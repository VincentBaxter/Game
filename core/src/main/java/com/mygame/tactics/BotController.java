package com.mygame.tactics;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.mygame.tactics.characters.Billy;

/**
 * Heuristic AI controller for bot-controlled teams.
 *
 * Called by CombatScreen each frame when it is a bot unit's turn.
 * Given the current GameState, returns the Action the bot should take.
 *
 * Decision flow:
 *   PRE_GAME  → deploy each unit based on class-based positional rules
 *   MOVEMENT  → score reachable tiles by enemy proximity + Haven weight
 *   ABILITY   → score ability+target combinations; prefer finishing off enemies
 *
 * Class behavior profiles:
 *   FIGHTER/TANK/CHAOS/COLLECTOR — advance toward enemies, anchor near Haven
 *   MAGE                         — mid-range, chase enemies but keep distance
 *   SUPPORT                      — stay near Haven, avoid front line
 *   ASSASSIN                     — close on isolated/low-HP enemies
 *   SNIPER                       — skip movement, hold distance
 *   STATUE/ENGINEER              — stationary, pick best ability target
 */
public class BotController {

    private static final int BOT_TEAM = 2;

    private final GameEngine engine;

    public BotController(GameEngine engine) {
        this.engine = engine;
    }

    // -----------------------------------------------------------------------
    // Main entry point
    // -----------------------------------------------------------------------

    public Action decide(GameState state) {
        Character unit = state.activeUnit;
        if (unit == null || unit.team != BOT_TEAM) return new Action.PassAction(BOT_TEAM);

        if (state.isPreGame())       return decidePreGame(state, unit);
        if (state.isMovementPhase()) return decideMovement(state, unit);
        return decideAbility(state, unit);
    }

    // -----------------------------------------------------------------------
    // Pre-game deployment
    // -----------------------------------------------------------------------

    private Action decidePreGame(GameState state, Character unit) {
        // Billy: choose a random enemy character to disguise as first
        if (unit instanceof Billy) {
            Billy billy = (Billy) unit;
            if (!billy.hasDeployed && billy.disguisedAs == null) {
                Array<Character> enemies = new Array<>();
                for (Character c : state.allUnits)
                    if (c.team != BOT_TEAM && !c.isDead()) enemies.add(c);
                if (enemies.size > 0) {
                    Character pick = enemies.get(MathUtils.random(enemies.size - 1));
                    return new Action.ChooseDisguiseAction(BOT_TEAM, pick.getName());
                }
                return new Action.PassAction(BOT_TEAM);
            }
        }

        engine.calculateMovementRange(state);
        if (state.reachableTiles.size == 0) return new Action.PassAction(BOT_TEAM);

        Vector2 tile = pickDeployTile(state.reachableTiles, unit);
        return new Action.DeployAction(BOT_TEAM, (int) tile.x, (int) tile.y);
    }

    private Vector2 pickDeployTile(Array<Vector2> tiles, Character unit) {
        Vector2 best      = tiles.first();
        float   bestScore = Float.NEGATIVE_INFINITY;
        for (Vector2 t : tiles) {
            float score = scoreDeployTile((int) t.x, (int) t.y, unit.getCharClass());
            if (score > bestScore) { bestScore = score; best = t; }
        }
        return best;
    }

    /**
     * Scores a deployment tile for team 2.
     * Row 8 = back row for team 2, row 4 = middle.
     */
    private float scoreDeployTile(int x, int y, Enums.CharClass cls) {
        // Small randomness so the bot doesn't always pick the exact same tile
        float score = MathUtils.random(-0.5f, 0.5f);
        switch (cls) {
            case SUPPORT:
            case ASSASSIN:
            case SNIPER:
                score += y * 2f;          // prefer high y (back row)
                break;
            case STATUE:
                score -= Math.abs(y - 5f) * 1.5f;
                score += MathUtils.random(-1.5f, 1.5f); // extra randomness for statues
                break;
            default:
                score -= Math.abs(y - 5.5f);  // prefer y≈5-6 (mid-front)
                score -= Math.abs(x - 4) * 0.4f; // prefer center column
                break;
        }
        return score;
    }

    // -----------------------------------------------------------------------
    // Movement
    // -----------------------------------------------------------------------

    private Action decideMovement(GameState state, Character unit) {
        // Immobile classes skip movement
        if (unit.getCharClass() == Enums.CharClass.STATUE || unit.baseMoveDist == 0)
            return new Action.PassAction(unit.team);
        if (unit.getCharClass() == Enums.CharClass.SNIPER)
            return new Action.PassAction(unit.team);

        engine.calculateMovementRange(state);
        if (state.reachableTiles.size == 0) return new Action.PassAction(unit.team);

        Array<Character> enemies = getVisibleEnemies(state);
        int havenX = (state.haven != null) ? state.haven.getX() : 4;
        int havenY = (state.haven != null) ? state.haven.getY() : 4;

        Vector2 best      = null;
        float   bestScore = Float.NEGATIVE_INFINITY;
        for (Vector2 t : state.reachableTiles) {
            float score = scoreMoveTarget((int) t.x, (int) t.y, unit, enemies, havenX, havenY);
            if (score > bestScore) { bestScore = score; best = t; }
        }

        if (best == null || ((int) best.x == unit.x && (int) best.y == unit.y))
            return new Action.PassAction(unit.team);
        return new Action.MoveAction(unit.team, (int) best.x, (int) best.y);
    }

    private float scoreMoveTarget(int x, int y, Character unit,
                                   Array<Character> enemies, int havenX, int havenY) {
        float score = MathUtils.random(-0.2f, 0.2f);

        // Haven proximity
        float havenDist = Math.abs(x - havenX) + Math.abs(y - havenY);
        score -= havenDist * getHavenWeight(unit.getCharClass());

        // Enemy proximity
        if (enemies.size > 0) {
            float nearestDist = Float.MAX_VALUE;
            for (Character e : enemies) {
                float d = Math.abs(x - e.x) + Math.abs(y - e.y);
                if (d < nearestDist) nearestDist = d;
            }
            float ew = getEnemyWeight(unit.getCharClass());
            // Positive weight = move toward; negative weight = move away
            if (ew >= 0) score -= nearestDist * ew;
            else         score += nearestDist * Math.abs(ew);
        }

        return score;
    }

    /** Weight applied to distance-from-Haven when scoring movement tiles. */
    private float getHavenWeight(Enums.CharClass cls) {
        switch (cls) {
            case TANK:    return 0.60f;
            case SUPPORT: return 0.70f;
            case SNIPER:  return 0.20f;
            default:      return 0.35f;
        }
    }

    /** Positive = move toward enemies; negative = move away. */
    private float getEnemyWeight(Enums.CharClass cls) {
        switch (cls) {
            case FIGHTER:
            case TANK:
            case CHAOS:
            case COLLECTOR: return 0.65f;
            case MAGE:      return 0.40f;
            case ASSASSIN:  return 0.70f;
            case SUPPORT:   return -0.20f;
            case SNIPER:    return -0.30f;
            default:        return 0.40f;
        }
    }

    // -----------------------------------------------------------------------
    // Ability
    // -----------------------------------------------------------------------

    private Action decideAbility(GameState state, Character unit) {
        Ability[] abilities = unit.getAbilities();
        if (abilities == null || abilities.length == 0) return new Action.PassAction(unit.team);

        int havenX = (state.haven != null) ? state.haven.getX() : 4;
        int havenY = (state.haven != null) ? state.haven.getY() : 4;

        Action bestAction = null;
        float  bestScore  = 0f; // must beat 0 to act; otherwise pass

        for (int i = 0; i < abilities.length; i++) {
            Ability ab = abilities[i];
            if (ab.isPassive) continue;

            // Self-cast (range 0, no target, not a direction-click ability)
            if (ab.getRange() == 0 && !ab.needsTarget() && !ab.isDirectionAbility) {
                float score = scoreSelfAbility(unit);
                if (score > bestScore) {
                    bestScore  = score;
                    bestAction = new Action.AbilityAction(unit.team, i, -1, -1);
                }
                continue;
            }

            // Two-step and direction-click abilities: skip for now
            if (ab.isDirectionAbility) continue;
            if (ab.getName().equals("Painted Walls") || ab.getName().equals("Wall of Fire")) continue;

            // Targeted ability
            engine.calculateTargetRange(state, ab);
            for (Vector2 t : state.targetableTiles) {
                int        tx         = (int) t.x, ty = (int) t.y;
                Character  targetChar = state.board.getCharacterAt(tx, ty);
                float      score      = scoreAbilityTarget(state, unit, ab, targetChar);
                if (score > bestScore) {
                    bestScore  = score;
                    bestAction = new Action.AbilityAction(unit.team, i, tx, ty);
                }
            }
        }

        return bestAction != null ? bestAction : new Action.PassAction(unit.team);
    }

    /** Score for self-cast abilities — prefer using when below 60% HP. */
    private float scoreSelfAbility(Character unit) {
        float hpRatio = (float) unit.getHealth() / Math.max(1, unit.getMaxHealth());
        return hpRatio < 0.6f ? 0.8f : 0.3f;
    }

    /** Score for using ability `ab` at a tile occupied by `targetChar`. */
    private float scoreAbilityTarget(GameState state, Character unit, Ability ab,
                                      Character targetChar) {
        if (ab.isHeal) {
            // Heal: target most-injured ally
            if (targetChar == null || targetChar.team != unit.team || targetChar.isDead()) return 0f;
            float hpRatio = (float) targetChar.getHealth() / Math.max(1, targetChar.getMaxHealth());
            return (1f - hpRatio) + 0.4f;
        } else {
            // Damage: target visible enemy — prefer finishing off low-HP targets
            if (targetChar == null || targetChar.team == unit.team
                    || targetChar.isDead() || targetChar.isInvisible()) return 0f;
            float hpRatio = (float) targetChar.getHealth() / Math.max(1, targetChar.getMaxHealth());
            float score   = (1f - hpRatio) + 0.3f;
            // Bonus for dislodging Haven occupant
            if (state.havenOccupant != null && state.havenOccupant == targetChar) score += 0.4f;
            return score;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** All living, visible enemies (not invisible) from the bot's perspective. */
    private Array<Character> getVisibleEnemies(GameState state) {
        Array<Character> result = new Array<>();
        for (Character c : state.allUnits)
            if (c.team != BOT_TEAM && !c.isDead() && !c.isInvisible()) result.add(c);
        return result;
    }
}
