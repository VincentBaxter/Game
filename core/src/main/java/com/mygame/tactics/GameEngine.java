package com.mygame.tactics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.mygame.tactics.characters.Billy;
import com.mygame.tactics.characters.Ghia;
import com.mygame.tactics.characters.GuardTower;
import com.mygame.tactics.characters.Hunter;
import com.mygame.tactics.characters.Jaxon;
import com.mygame.tactics.characters.Lark;
import com.mygame.tactics.characters.Luke;
import com.mygame.tactics.characters.Mason;
import com.mygame.tactics.characters.Maxx;
import com.mygame.tactics.characters.Sean;
import com.mygame.tactics.characters.Speen;
import com.mygame.tactics.characters.Stoneguard;
import com.mygame.tactics.characters.Weirdguard;

import java.util.Random;

/**
 * Pure game logic. No LibGDX rendering types.
 *
 * GameEngine receives a GameState and an Action, validates the action,
 * mutates the state, and returns a list of EngineEvents for the renderer.
 *
 * This class can be instantiated on a headless server with no display context.
 */
public class GameEngine {

    /**
     * Entry point. Process one player action.
     * @return List of EngineEvents for the renderer to consume.
     */
    public Array<EngineEvent> process(GameState state, Action action) {
        Array<EngineEvent> events = new Array<>();

        // --- Validate: only the active team may act ---
        if (state.activeUnit != null && action.actingTeam != state.activeUnit.team) {
            return events; // Silently reject out-of-turn actions (server guards this too)
        }

        if (state.isPreGame()) {
            processPreGame(state, action, events);
        } else if (state.isBattle()) {
            processBattle(state, action, events);
        }
        // GAME_OVER: no actions accepted

        return events;
    }
    
    public Array<EngineEvent> processNetwork(GameState state, NetworkAction na) {
        if (na == null || na.action == null) return new Array<>();
        if (state == null) return new Array<>();
        if (state.activeUnit != null && na.playerId != state.activeUnit.team) return new Array<>();
        return process(state, na.action);
    }

    // =========================================================================
    // PRE-GAME
    // =========================================================================

    private void processPreGame(GameState state, Action action, Array<EngineEvent> events) {
        if (state.activeUnit instanceof Billy && action instanceof Action.ChooseDisguiseAction) {
            // Set the disguise but stay on Billy's slot — he still needs to deploy.
            Action.ChooseDisguiseAction da = (Action.ChooseDisguiseAction) action;
            Array<Character> teammates = (state.activeUnit.team == 1) ? state.team1 : state.team2;
            for (Character ally : teammates) {
                if (ally != state.activeUnit && ally.getName().equals(da.targetName)) {
                    ((Billy) state.activeUnit).disguisedAs = ally;
                    break;
                }
            }
            calculateMovementRange(state); // refresh deployment tile highlights

        } else if (state.activeUnit instanceof Billy && action instanceof Action.DeployAction) {
            // Require disguise to be chosen before deploying.
            if (((Billy) state.activeUnit).disguisedAs == null) return;
            Action.DeployAction da = (Action.DeployAction) action;
            boolean isTeam1  = (state.activeUnit.team == 1);
            boolean validRow = isTeam1 ? (da.targetY <= 1) : (da.targetY >= 7);
            boolean notOuterRing = !isWindBoard(state)
                    || (da.targetX > 0 && da.targetX < 8 && da.targetY > 0 && da.targetY < 8);
            boolean validCell = state.board.isValid(da.targetX, da.targetY)
                                && validRow && notOuterRing
                                && state.board.getCharacterAt(da.targetX, da.targetY) == null;
            if (validCell) {
                state.board.addCharacter(state.activeUnit, da.targetX, da.targetY);
                state.activeUnit.hasDeployed = true;
                processNextSetupCharacter(state, events);
            }

        } else if (state.activeUnit.getCharClass() == Enums.CharClass.STATUE
                   && action instanceof Action.DeployAction) {
            Action.DeployAction da = (Action.DeployAction) action;
            boolean isTeam1   = (state.activeUnit.team == 1);
            boolean validRow  = isTeam1 ? (da.targetY <= 4) : (da.targetY >= 4);
            boolean notOuterRing = !isWindBoard(state)
                    || (da.targetX > 0 && da.targetX < 8 && da.targetY > 0 && da.targetY < 8);
            boolean validCell = state.board.isValid(da.targetX, da.targetY)
                                && validRow && notOuterRing
                                && state.board.getCharacterAt(da.targetX, da.targetY) == null;
            if (validCell) {
                state.board.addCharacter(state.activeUnit, da.targetX, da.targetY);
                state.activeUnit.hasDeployed = true;
                processNextSetupCharacter(state, events);
            }
        } else if (state.activeUnit.getCharClass() != Enums.CharClass.STATUE
                && !(state.activeUnit instanceof Billy)
                && action instanceof Action.DeployAction) {
            Action.DeployAction da = (Action.DeployAction) action;
            boolean isTeam1  = (state.activeUnit.team == 1);
            // Team 1 limited to rows 0-1, Team 2 limited to rows 7-8
            boolean validRow = isTeam1 ? (da.targetY <= 1) : (da.targetY >= 7);
            boolean notOuterRing = !isWindBoard(state)
                    || (da.targetX > 0 && da.targetX < 8 && da.targetY > 0 && da.targetY < 8);
            boolean validCell = state.board.isValid(da.targetX, da.targetY)
                                && validRow && notOuterRing
                                && state.board.getCharacterAt(da.targetX, da.targetY) == null;
            if (validCell) {
                state.board.addCharacter(state.activeUnit, da.targetX, da.targetY);
                state.activeUnit.hasDeployed = true;
                processNextSetupCharacter(state, events);
            }
        }
    }

    private void processNextSetupCharacter(GameState state, Array<EngineEvent> events) {
        if (state.setupQueue.size > 0) {
            state.activeUnit = state.setupQueue.removeIndex(0);
            calculateMovementRange(state);
        } else {
            finalizeBattleStart(state, events);
        }
    }

    /**
     * Called once by CombatScreen after construction.
     * Handles both cases: pre-game setup queue exists, or battle starts immediately.
     */
    public Array<EngineEvent> initialize(GameState state) {
        Array<EngineEvent> events = new Array<>();
        if (state.isPreGame() && state.setupQueue.size > 0) {
            state.activeUnit = state.setupQueue.removeIndex(0);
            calculateMovementRange(state);
        } else {
            finalizeBattleStart(state, events);
        }
        return events;
    }

    private void finalizeBattleStart(GameState state, Array<EngineEvent> events) {
        state.phase = GameState.Phase.BATTLE;
        state.haven = new Haven(state.boardConfig.havenStartX, state.boardConfig.havenStartY);

        // Pre-collapse outer ring for Wind Arena
        if (state.boardConfig.preCollapseOuterRing) {
            for (int x = 0; x < state.board.getRows(); x++) {
                for (int y = 0; y < state.board.getCols(); y++) {
                    if (x == 0 || x == state.board.getRows() - 1
                            || y == 0 || y == state.board.getCols() - 1) {
                        state.board.getTile(x, y).setCollapsed(true);
                    }
                }
            }
        }

        for (int i = 0; i < state.team1.size; i++) {
            Character c = state.team1.get(i);
            if (c.hasDeployed) continue;
            state.board.addCharacter(c, 3 + i, 0);
        }
        for (int i = 0; i < state.team2.size; i++) {
            Character c = state.team2.get(i);
            if (c.hasDeployed) continue;
            state.board.addCharacter(c, 3 + i, 8);
        }

        for (Character c : state.allUnits) {
            if (c instanceof Ghia) spawnClothesPiles((Ghia) c, state);
        }

        startTurn(state, events);
    }

    // =========================================================================
    // BATTLE
    // =========================================================================

    private void processBattle(GameState state, Action action, Array<EngineEvent> events) {
        if (action instanceof Action.MoveAction) {
            Action.MoveAction ma = (Action.MoveAction) action;
            // "Stay here" = moving to your own tile
            if (ma.targetX == state.activeUnit.x && ma.targetY == state.activeUnit.y) {
                handleStay(state, events);
            } else {
                handleMove(state, ma.targetX, ma.targetY, events);
            }

        } else if (action instanceof Action.PassAction) {
            handlePass(state, events);

        } else if (action instanceof Action.AbilityAction) {
            Action.AbilityAction aa = (Action.AbilityAction) action;
            state.selectedAbility = state.activeUnit.getAbility(aa.abilityIndex);
            state.selectedTargetTile = (aa.targetX >= 0)
                    ? new Vector2(aa.targetX, aa.targetY) : null;
            executeAbility(state, events);

        } else if (action instanceof Action.TwoStepAbilityAction) {
            Action.TwoStepAbilityAction tsa = (Action.TwoStepAbilityAction) action;
            state.selectedAbility = state.activeUnit.getAbility(tsa.abilityIndex);
            state.pendingAnchor   = new Vector2(tsa.anchorX, tsa.anchorY);
            state.selectedTargetTile = new Vector2(tsa.directionX, tsa.directionY);
            executeAbility(state, events);
        }
    }

    // -----------------------------------------------------------------------
    // Move
    // -----------------------------------------------------------------------

    private void handleStay(GameState state, Array<EngineEvent> events) {
        if (state.isGrandEntranceMove) {
            state.isGrandEntranceMove = false;
            state.turnPhase = GameState.TurnPhase.MOVEMENT;
            calculateMovementRange(state);
        } else if (state.activeUnit instanceof Billy && ((Billy) state.activeUnit).isSnakeActive) {
            // Snake In The Grass granted this move — staying still ends the turn.
            ((Billy) state.activeUnit).isSnakeActive = false;
            endTurn(state, events);
        } else if (state.isFighterBonusMove
                || state.activeUnit.getCharClass() == Enums.CharClass.SNIPER) {
            state.isFighterBonusMove = false;
            endTurn(state, events);
        } else {
            state.turnPhase = GameState.TurnPhase.ABILITY;
        }
    }

    private void handlePass(GameState state, Array<EngineEvent> events) {
        if (state.turnPhase == GameState.TurnPhase.ABILITY
                && state.activeUnit.getCharClass() == Enums.CharClass.SNIPER) {
            state.turnPhase = GameState.TurnPhase.MOVEMENT;
            calculateMovementRange(state);
        } else {
            endTurn(state, events);
        }
    }

    private void handleMove(GameState state, int targetX, int targetY,
                            Array<EngineEvent> events) {
        // Validate the tile is in reachable set
        boolean valid = false;
        for (Vector2 v : state.reachableTiles) {
            if ((int) v.x == targetX && (int) v.y == targetY) { valid = true; break; }
        }
        if (!valid) return;

        Character occupant = state.board.getCharacterAt(targetX, targetY);

        // Invisible enemy collision — reveal + bounce
        if (occupant != null && occupant != state.activeUnit
                && occupant.isInvisible() && occupant.team != state.activeUnit.team) {
            occupant.setInvisible(false);
            if (occupant instanceof Billy) ((Billy) occupant).disguisedAs = null;
            events.add(new EngineEvent.PopupEvent("AMBUSHED!", 0, "REVEAL", targetX, targetY));
            events.add(new EngineEvent.PopupEvent("BOUNCED BACK", 0, "STATUS",
                    state.activeUnit.x, state.activeUnit.y));
            state.selectedMoveTile = null;
            return;
        }

        if (occupant != null && occupant != state.activeUnit) return; // Safety guard

        // Billy stealth on move:
        // - Snake In The Grass grants one free invisible move (isSnakeActive flag).
        // - Any other invisible move breaks stealth.
        if (state.activeUnit instanceof Billy && state.activeUnit.isInvisible()) {
            Billy _b = (Billy) state.activeUnit;
            if (_b.isSnakeActive) {
                // One free invisible move consumed — stealth persists.
                // isSnakeActive is cleared in postMoveTransition so it can trigger end-turn.
            } else {
                state.activeUnit.setInvisible(false);
                events.add(new EngineEvent.PopupEvent("REVEALED", 0, "STEALTH",
                        state.activeUnit.x, state.activeUnit.y));
            }
        }

        // Billy poison trail
        if (state.activeUnit instanceof Billy && ((Billy) state.activeUnit).isPoisonTrailActive) {
            int cx = state.activeUnit.x, cy = state.activeUnit.y;
            while (cx != targetX) {
                state.board.applyPoison(cx, cy);
                events.add(new EngineEvent.TileEffectEvent(
                        EngineEvent.TileEffectEvent.Effect.POISON, cx, cy));
                cx += (targetX > cx) ? 1 : -1;
            }
            while (cy != targetY) {
                state.board.applyPoison(cx, cy);
                events.add(new EngineEvent.TileEffectEvent(
                        EngineEvent.TileEffectEvent.Effect.POISON, cx, cy));
                cy += (targetY > cy) ? 1 : -1;
            }
            state.board.applyPoison(targetX, targetY);
            events.add(new EngineEvent.TileEffectEvent(
                    EngineEvent.TileEffectEvent.Effect.POISON, targetX, targetY));
        }

        // Emit move animation event BEFORE updating grid position
        events.add(new EngineEvent.MoveAnimationEvent(
                state.activeUnit, state.activeUnit.x, state.activeUnit.y, targetX, targetY));

        // Move on the board (may trigger void knockout, returns events)
        Array<EngineEvent> boardEvents = state.board.moveCharacter(state.activeUnit, targetX, targetY);
        if (processBoardEvents(boardEvents, state, events) && state.isGameOver()) return;


        state.reachableTiles.clear();
        state.selectedMoveTile = null;
        
        // Post-move phase transition
        postMoveTransition(state, events);
    }

    private void postMoveTransition(GameState state, Array<EngineEvent> events) {
        // Safety: active unit may have died mid-move
        if (state.activeUnit.getHealth() <= 0) {
            endTurn(state, events);
            return;
        }

        if (state.activeUnit instanceof Billy && ((Billy) state.activeUnit).isSnakeActive) {
            // Snake In The Grass granted this move — end the turn now.
            ((Billy) state.activeUnit).isSnakeActive = false;
            endTurn(state, events);
        } else if (state.activeUnit instanceof Billy && ((Billy) state.activeUnit).isPoisonTrailActive) {
            // Trail active — move to ability phase so Billy can still use Fangs or Snake.
            state.turnPhase = GameState.TurnPhase.ABILITY;
        } else if (state.isFighterBonusMove
                || state.activeUnit.getCharClass() == Enums.CharClass.SNIPER) {
            state.isFighterBonusMove = false;
            endTurn(state, events);
        } else if (state.isMarathonMove) {
            state.isMarathonMove = false;
            endTurn(state, events);
        } else if (state.isGrandEntranceMove) {
            state.isGrandEntranceMove = false;
            state.turnPhase = GameState.TurnPhase.MOVEMENT;
            calculateMovementRange(state);
            events.add(new EngineEvent.PopupEvent("NOW ATTACK!", 0, "MOVE",
                    state.activeUnit.x, state.activeUnit.y));
        } else {
            state.turnPhase = GameState.TurnPhase.ABILITY;
        }
    }

    // -----------------------------------------------------------------------
    // Ability execution
    // -----------------------------------------------------------------------

    private void executeAbility(GameState state, Array<EngineEvent> events) {
        if (state.selectedAbility == null) return;

        state.wasBillyRevealedThisAction = false;

        int tx = (state.selectedTargetTile != null)
                ? (int) state.selectedTargetTile.x : state.activeUnit.x;
        int ty = (state.selectedTargetTile != null)
                ? (int) state.selectedTargetTile.y : state.activeUnit.y;
        Character target = state.board.getCharacterAt(tx, ty);

        String abilityName = state.selectedAbility.getName();

        // Lock slot 3 (index 2) for the rest of the game once it is used.
        // This is checked here — before execution — so every code path below
        // (named special cases and the general case) benefits automatically.
        // Characters that manage their own ult state (Hunter, Jaxon, Speen)
        // already call setUltActive(true) themselves; calling it again is harmless.
        if (state.selectedAbility == state.activeUnit.getAbility(2)) {
            state.activeUnit.setUltActive(true);
        }

        // Billy stealth break on ability use.
        // Snake In The Grass is exempt (it grants stealth).
        // Poisoned Fangs is exempt (it handles its own trail deactivation and reveal inside execute()).
        if (state.activeUnit instanceof Billy
                && state.activeUnit.isInvisible()
                && !abilityName.equals("Snake In The Grass")
                && !abilityName.equals("Poisoned Fangs")) {
            state.activeUnit.setInvisible(false);
            events.add(new EngineEvent.PopupEvent("REVEALED", 0, "STEALTH",
                    state.activeUnit.x, state.activeUnit.y));
        }

        // --- Vanish ---
        if (abilityName.equals("Vanish")) {
            state.selectedAbility.execute(state.activeUnit, target, state, events, tx, ty);
            clearAbilitySelection(state);
            state.turnPhase = GameState.TurnPhase.MOVEMENT;
            calculateMovementRange(state);
            return;
        }

        // --- Snake In The Grass ---
        if (abilityName.equals("Snake In The Grass")) {
            state.selectedAbility.execute(state.activeUnit, target, state, events, tx, ty);
            clearAbilitySelection(state);
            state.turnPhase = GameState.TurnPhase.MOVEMENT;
            calculateMovementRange(state);
            return;
        }

        // --- Painted Walls (two-step, already resolved via TwoStepAbilityAction) ---
        if (abilityName.equals("Painted Walls")) {
            if (state.activeUnit instanceof Sean && state.pendingAnchor != null) {
                Sean sean = (Sean) state.activeUnit;
                sean.wallAnchor    = state.pendingAnchor;
                sean.wallDirection = new Vector2(tx, ty); // tx/ty = directionX/directionY
            }
            state.selectedAbility.execute(state.activeUnit, target, state, events, tx, ty);
            state.pendingAnchor = null;
            clearAbilitySelection(state);
            transitionAfterAbility(state, events);
            return;
        }

        // --- Wall of Fire (two-step, same pattern as Painted Walls) ---
        if (abilityName.equals("Wall of Fire")) {
            if (state.activeUnit instanceof Lark && state.pendingAnchor != null) {
                Lark lark = (Lark) state.activeUnit;
                lark.wallOfFireAnchor    = state.pendingAnchor;
                lark.wallOfFireDirection = new Vector2(tx, ty); // tx/ty = directionX/directionY
            }
            state.selectedAbility.execute(state.activeUnit, target, state, events, tx, ty);
            state.pendingAnchor = null;
            clearAbilitySelection(state);
            transitionAfterAbility(state, events);
            return;
        }

        // --- Pergolatory (two-step: anchor corner, then confirm) ---
        if (abilityName.equals("Pergolatory")) {
            if (state.activeUnit instanceof Luke && state.pendingAnchor != null) {
                ((Luke) state.activeUnit).pergolaAnchor = state.pendingAnchor;
            }
            state.selectedAbility.execute(state.activeUnit, target, state, events, tx, ty);
            state.pendingAnchor = null;
            clearAbilitySelection(state);
            transitionAfterAbility(state, events);
            return;
        }

        // --- Take Flight (Mason teleport within 3 range) ---
        if (abilityName.equals("Take Flight")) {
            if (state.selectedTargetTile != null) {
                Tile dest = state.board.getTile(tx, ty);
                Character occ = state.board.getCharacterAt(tx, ty);
                if (dest != null && !dest.hasStructure() && !dest.isCollapsed() && occ == null) {
                    ((Mason) state.activeUnit).isTakeFlightActive = true;
                    state.board.removeCharacter(state.activeUnit.x, state.activeUnit.y);
                    state.board.addCharacter(state.activeUnit, tx, ty);
                    events.add(new EngineEvent.MoveAnimationEvent(
                            state.activeUnit, state.activeUnit.x, state.activeUnit.y, tx, ty));
                }
            }
            clearAbilitySelection(state);
            transitionAfterAbility(state, events);
            return;
        }

        // --- Marathon ---
        if (abilityName.equals("Marathon")) {
            state.selectedAbility.execute(state.activeUnit, null, state, events,
                    state.activeUnit.x, state.activeUnit.y);
            clearAbilitySelection(state);
            state.isMarathonMove = true;
            state.turnPhase = GameState.TurnPhase.MOVEMENT;
            calculateMovementRange(state);
            return;
        }

        // --- Pass (Speen's Haven push) ---
        if (abilityName.equals("Pass")) {
            state.selectedAbility.execute(state.activeUnit, null, state, events,
                    state.activeUnit.x, state.activeUnit.y);
            pushHaven(state, events);
            clearAbilitySelection(state);
            transitionAfterAbility(state, events);
            return;
        }

        // --- Tumble (Emily — push Haven away) ---
        if (abilityName.equals("Tumble")) {
            state.selectedAbility.execute(state.activeUnit, null, state, events,
                    state.activeUnit.x, state.activeUnit.y);
            pushHaven(state, events);
            clearAbilitySelection(state);
            transitionAfterAbility(state, events);
            return;
        }

        // --- Embrace (Emily — pull Haven toward her, once per game) ---
        if (abilityName.equals("Embrace")) {
            state.selectedAbility.execute(state.activeUnit, null, state, events,
                    state.activeUnit.x, state.activeUnit.y);
            pullHaven(state, events);
            clearAbilitySelection(state);
            transitionAfterAbility(state, events);
            return;
        }

        // --- Par 3 (Aevan — pull Haven one tile closer) ---
        if (abilityName.equals("Par 3")) {
            state.selectedAbility.execute(state.activeUnit, null, state, events,
                    state.activeUnit.x, state.activeUnit.y);
            pullHaven(state, events);
            clearAbilitySelection(state);
            transitionAfterAbility(state, events);
            return;
        }

        // --- Lick Wounds (Ghia — teleport to a clothes tile and heal 10) ---
        if (abilityName.equals("Lick Wounds")) {
            if (state.selectedTargetTile == null) return;
            Tile dest = state.board.getTile(tx, ty);
            // Validate the chosen tile is actually a living clothes pile
            if (dest == null || !dest.isClothes() || !dest.hasStructure()) {
                clearAbilitySelection(state);
                transitionAfterAbility(state, events);
                return;
            }
            // Teleport Ghia onto the clothes pile
            state.board.removeCharacter(state.activeUnit.x, state.activeUnit.y);
            state.board.addCharacter(state.activeUnit, tx, ty);
            events.add(new EngineEvent.MoveAnimationEvent(
                    state.activeUnit, state.activeUnit.x, state.activeUnit.y, tx, ty));
            // Heal 10 HP (capped at max)
            int actualHeal = Math.min(10, state.activeUnit.maxHealth - state.activeUnit.health);
            state.activeUnit.health += actualHeal;
            events.add(new EngineEvent.PopupEvent("LICK WOUNDS", actualHeal, "HEAL", tx, ty));
            // Apply invisibility immediately since she's now on clothes
            if (state.activeUnit instanceof Ghia) {
                updateGhiaClothesInvisibility((Ghia) state.activeUnit, state, events);
            }
            clearAbilitySelection(state);
            transitionAfterAbility(state, events);
            return;
        }

        // --- Away Game (Speen teleport) ---
        if (abilityName.equals("Away Game")) {
            if (state.selectedTargetTile == null) return;
            Character occ = state.board.getCharacterAt(tx, ty);
            if (occ != null && occ.isInvisible() && occ.team != state.activeUnit.team) {
                occ.setInvisible(false);
                events.add(new EngineEvent.PopupEvent("AMBUSHED!", 0, "REVEAL", tx, ty));
                clearAbilitySelection(state);
                transitionAfterAbility(state, events);
                return;
            }
            state.selectedAbility.execute(state.activeUnit, null, state, events, tx, ty);
            state.board.removeCharacter(state.activeUnit.x, state.activeUnit.y);
            state.board.addCharacter(state.activeUnit, tx, ty);
            events.add(new EngineEvent.PopupEvent("TELEPORTED", 0, "MOVE", tx, ty));
            state.activeUnit.setUltActive(true);
            clearAbilitySelection(state);
            transitionAfterAbility(state, events);
            return;
        }

        // --- Dagger's Cull: toggle active then go straight to ability phase ---
        if (abilityName.equals("Dagger's Cull")) {
            state.selectedAbility.execute(state.activeUnit, target, state, events, tx, ty);
            clearAbilitySelection(state);
            state.turnPhase = GameState.TurnPhase.ABILITY;
            calculateTargetRange(state, state.activeUnit.getAbility(0));
            return;
        }
        
     // --- Lockdown (Ben — lock the Haven permanently) ---
        if (abilityName.equals("Lockdown")) {
            state.havenLocked = true;
            Tile havenTile = state.board.getTile(state.haven.getX(), state.haven.getY());
            if (havenTile != null) havenTile.setLockdown(true);
            events.add(new EngineEvent.PopupEvent("HAVEN LOCKED!", 0, "STATUS",
                    state.haven.getX(), state.haven.getY()));
            clearAbilitySelection(state);
            transitionAfterAbility(state, events);
            return;
        }

        // --- General case ---
        state.selectedAbility.execute(state.activeUnit, target, state, events, tx, ty);

        // Process any deaths CombatBoard signalled via CharacterKilledEvent.
        // Abilities that call board.moveCharacter() internally (e.g. Evan's Tsunami,
        // Brad's Hook) use processBoardEvents() at their call sites. This fallback
        // catches any remaining unprocessed kills in the master event list.
        for (EngineEvent e : new Array<>(events)) {
            if (e instanceof EngineEvent.CharacterKilledEvent) {
                EngineEvent.CharacterKilledEvent ke = (EngineEvent.CharacterKilledEvent) e;
                if (!ke.victim.isDead()) ke.victim.setHealth(0);
                handleDeath(ke.victim, state, events);
                if (state.isGameOver()) return;
            }
        }

        // Post-ability death processing for the primary target
        if (target != null && target.getHealth() <= 0) {
            handleDeath(target, state, events);
            if (state.isGameOver()) return;
        }

        // Dagger's Cull: Shiv kill while cull is active -> double multiplier, grant move + attack
        if (abilityName.equals("Shiv")
                && state.activeUnit instanceof Hunter
                && ((Hunter) state.activeUnit).isDaggersCullActive
                && target != null && target.isDead()) {
            Hunter h = (Hunter) state.activeUnit;
            h.cullDamageMultiplier *= 2;
            clearAbilitySelection(state);
            state.turnPhase = GameState.TurnPhase.MOVEMENT;
            calculateMovementRange(state);
            events.add(new EngineEvent.PopupEvent("CULL! x" + h.cullDamageMultiplier, 0, "ACTIVE",
                    state.activeUnit.x, state.activeUnit.y));
            return;
        }

        clearAbilitySelection(state);

        // Process any HavenMoveEvent emitted by abilities (e.g. Nathan's Clutch).
        // Handled here so moveHaven() correctly strips the old occupant's bonus stats.
        for (EngineEvent e : new Array<>(events)) {
            if (e instanceof EngineEvent.HavenMoveEvent) {
                EngineEvent.HavenMoveEvent hme = (EngineEvent.HavenMoveEvent) e;
                moveHaven(state, hme.newX, hme.newY, events);
            }
        }

        transitionAfterAbility(state, events);
    }

    // NOTE: Wall of Fire is handled as a named special case early in executeAbility()
    // and always returns before reaching the general case. The duplicate block that
    // previously appeared here has been removed.

    private void transitionAfterAbility(GameState state, Array<EngineEvent> events) {
        if (state.activeUnit.getCharClass() == Enums.CharClass.SNIPER) {
            state.turnPhase = GameState.TurnPhase.MOVEMENT;
            calculateMovementRange(state);
        } else if (state.activeUnit.getCharClass() == Enums.CharClass.FIGHTER) {
            state.isFighterBonusMove = true;
            state.turnPhase = GameState.TurnPhase.MOVEMENT;
            calculateMovementRange(state);
            events.add(new EngineEvent.PopupEvent("BONUS MOVE", 0, "MOVE",
                    state.activeUnit.x, state.activeUnit.y));
        } else {
            endTurn(state, events);
        }
    }

    // -----------------------------------------------------------------------
    // Turn management
    // -----------------------------------------------------------------------

    private void endTurn(GameState state, Array<EngineEvent> events) {
        state.isFighterBonusMove  = false;
        state.isGrandEntranceMove = false;
        state.isMarathonMove      = false;

        if (state.activeUnit != null) {
            // Hunter end-of-turn: deactivate Cull and reset damage multiplier
            if (state.activeUnit instanceof Hunter) {
                Hunter h = (Hunter) state.activeUnit;
                if (h.isDaggersCullActive) {
                    h.isDaggersCullActive = false;
                    events.add(new EngineEvent.PopupEvent("CULL ENDED", 0, "STATUS",
                            state.activeUnit.x, state.activeUnit.y));
                }
                h.cullDamageMultiplier = 1;
            }
            if (state.activeUnit instanceof Billy) {
                Billy b = (Billy) state.activeUnit;
                b.isSnakeActive = false; // clear the one-free-move flag at turn end
            }
            if (state.activeUnit instanceof Mason) {
                ((Mason) state.activeUnit).isTakeFlightActive = false;
            }
            // Poison escalation: if the unit ends their turn poisoned, +1 poison level
            if (state.activeUnit.poisonLevel > 0) {
                state.activeUnit.poisonLevel++;
                events.add(new EngineEvent.PopupEvent("POISON +" + state.activeUnit.poisonLevel,
                        0, "POISON", state.activeUnit.x, state.activeUnit.y));
            }

            // Advance timers
            float timePassed = state.activeUnit.getCurrentWait();
            for (Character c : state.allUnits)
                if (!c.isDead()) c.setCurrentWait(Math.max(0, c.getCurrentWait() - timePassed));
            state.activeUnit.resetWaitAfterTurn();

            // Desert — collapse the furthest tile from Haven at the end of every turn
            if (state.isBattle() && state.boardConfig.collapseStyle == BoardConfig.CollapseStyle.DESERT_TILE) {
                triggerDesertCollapse(state, events);
                if (state.isGameOver()) return;
            }

         // Advance collapse timer
            if (state.isBattle() && state.haven != null) {
                state.collapseWait -= timePassed;

                if (!state.warningShownThisCycle && state.collapseWait <= 200f
                        && state.boardConfig.collapseStyle != BoardConfig.CollapseStyle.DESERT_TILE) {
                    state.warningAlpha = 1.0f;
                    state.warningShownThisCycle = true;
                }

                if (state.collapseWait <= 0f) {
                    if (state.boardConfig.collapseStyle == BoardConfig.CollapseStyle.WIND_PUSH) {
                        triggerWindPush(state, events);
                    } else if (state.boardConfig.collapseStyle == BoardConfig.CollapseStyle.RING) {
                        triggerRingCollapse(state, events);
                    }
                    if (state.isGameOver()) return;
                    state.collapseWait = state.boardConfig.initialCollapseWait;
                    state.warningShownThisCycle = false;
                    state.pendingCollapseCols.clear();
                    state.pendingCollapseRows.clear();
                }
            }
        }
        startTurn(state, events);
    }

    private void startTurn(GameState state, Array<EngineEvent> events) {
        state.wasBillyRevealedThisAction = false;

        // Find the living unit with the lowest wait
        Character next = null;
        float low = Float.MAX_VALUE;
        for (Character c : state.allUnits) {
            if (!c.isDead() && c.getCurrentWait() < low) { low = c.getCurrentWait(); next = c; }
        }
        state.activeUnit = next;

        // Mason's Gargoyle Unleashed: skip his turn while invisible
        if (state.activeUnit instanceof Mason && state.activeUnit.isInvisible()) {
            float timePassed = state.activeUnit.getCurrentWait();
            for (Character c : state.allUnits)
                if (!c.isDead()) c.setCurrentWait(Math.max(0, c.getCurrentWait() - timePassed));
            state.activeUnit.resetWaitAfterTurn();
            startTurn(state, events);
            return;
        }

        if (state.activeUnit == null) return;

        state.selectedMoveTile   = null;
        state.selectedTargetTile = null;
        state.selectedAbility    = null;
        state.targetableTiles.clear();

        // Grand Entrance
        if (state.activeUnit instanceof Jaxon && ((Jaxon) state.activeUnit).grandEntrancePending) {
            ((Jaxon) state.activeUnit).grandEntrancePending = false;
            state.activeUnit.setUltActive(true);
            state.isGrandEntranceMove = true;
            state.activeUnit.getAbility(2).execute(state.activeUnit, null, state, events,
                    state.activeUnit.x, state.activeUnit.y);
        }

        // Haven occupant bonus — disabled on Desert map
        boolean isDesert = state.boardConfig != null
                && state.boardConfig.collapseStyle == BoardConfig.CollapseStyle.DESERT_TILE;
        if (state.haven != null && !isDesert) {
            Character currentOcc = state.board.getCharacterAt(state.haven.getX(), state.haven.getY());
            if (currentOcc != state.havenOccupant) {
                int bonus = state.collapseCount + 1;
                if (state.havenOccupant != null && !state.havenOccupant.isDead()) {
                    state.havenOccupant.atk   -= bonus;
                    state.havenOccupant.mag   -= bonus;
                    state.havenOccupant.armor -= bonus;
                    state.havenOccupant.cloak -= bonus;
                    events.add(new EngineEvent.PopupEvent("HAVEN -" + bonus, 0, "DEBUFF",
                            state.haven.getX(), state.haven.getY()));
                }
                state.havenOccupant = currentOcc;
                if (state.havenOccupant != null && !state.havenOccupant.isDead()) {
                    state.havenOccupant.atk   += bonus;
                    state.havenOccupant.mag   += bonus;
                    state.havenOccupant.armor += bonus;
                    state.havenOccupant.cloak += bonus;
                    events.add(new EngineEvent.PopupEvent("HAVEN +" + bonus, 0, "BUFF",
                            state.haven.getX(), state.haven.getY()));
                }
            }
        }

        state.timeline.projectFutureEvents(state.allUnits);
        updateTileStates(state);

        // --- Pergola range buff ---
        // If the active unit is standing on a pergola tile, grant +1 range.
        // The buff is applied fresh each turn so it naturally falls off when
        // the character moves off the tile or the structure is destroyed.
        boolean onPergola = false;
        Tile standingTile = state.board.getTile(state.activeUnit.x, state.activeUnit.y);
        if (standingTile != null && standingTile.isPergola() && standingTile.hasStructure()) {
            onPergola = true;
        }
        if (onPergola && !state.activeUnit.pergolaRangeActive) {
            state.activeUnit.range++;
            state.activeUnit.pergolaRangeActive = true;
            events.add(new EngineEvent.PopupEvent("+1 RANGE", 0, "BUFF",
                    state.activeUnit.x, state.activeUnit.y));
        } else if (!onPergola && state.activeUnit.pergolaRangeActive) {
            state.activeUnit.range--;
            state.activeUnit.pergolaRangeActive = false;
        }

        // --- Ghia clothes invisibility ---
        // Applied at turn start so it also fires when other characters' turns
        // begin, keeping Ghia's invisibility in sync if the clothes pile is
        // destroyed while it's not her turn.
        for (Character c : state.allUnits) {
            if (c instanceof Ghia && !c.isDead()) {
                updateGhiaClothesInvisibility((Ghia) c, state, events);
            }
        }

        // --- Fire damage on turn start ---
        // Every burning tile deals fireTurnsActive true damage to any character
        // or structure on it, then ticks age and duration forward.
        for (int fx = 0; fx < 9; fx++) {
            for (int fy = 0; fy < 9; fy++) {
                Tile fireTile = state.board.getTile(fx, fy);
                if (fireTile == null || !fireTile.isOnFire()) continue;

                int fireDmg = fireTile.getFireTurnsActive(); // 1 on first turn, +1 each tick

                // Damage any character standing on this tile
                Character onFire = state.board.getCharacterAt(fx, fy);
                if (onFire != null && !onFire.isDead()) {
                    checkAndTriggerBillyTrail(state, onFire, events);
                    onFire.health = Math.max(0, onFire.health - fireDmg);
                    events.add(new EngineEvent.PopupEvent("FIRE", fireDmg, "TRUE", fx, fy));
                    if (onFire.isDead()) {
                        handleDeath(onFire, state, events);
                        if (state.isGameOver()) return;
                    }
                }

                // Damage any structure on this tile
                if (fireTile.hasStructure()) {
                    fireTile.applyStructureDamage(fireDmg);
                    events.add(new EngineEvent.PopupEvent("FIRE", fireDmg, "OBJ", fx, fy));
                }

                // Advance fire age and count down duration
                fireTile.tickFire();
            }
        }

        // --- Poison infection and damage on turn start ---
        // Billy is immune to poison while Poison Trail is active.
        // If the active unit is standing on a poisoned tile, infect them (+1 poison level).
        // Then, if they have any poison, deal poisonLevel true damage.
        boolean billyTrailImmune = (state.activeUnit instanceof Billy
                && ((Billy) state.activeUnit).isPoisonTrailActive);
        if (!billyTrailImmune) {
            Tile currentTile = state.board.getTile(state.activeUnit.x, state.activeUnit.y);
            if (currentTile != null && currentTile.isPoisoned()) {
                state.activeUnit.applyPoison();
                events.add(new EngineEvent.PopupEvent("INFECTED", state.activeUnit.poisonLevel,
                        "POISON", state.activeUnit.x, state.activeUnit.y));
            }
            if (state.activeUnit.poisonLevel > 0) {
                int poisonDmg = state.activeUnit.poisonLevel;
                checkAndTriggerBillyTrail(state, state.activeUnit, events);
                state.activeUnit.health = Math.max(0, state.activeUnit.health - poisonDmg);
                events.add(new EngineEvent.PopupEvent("POISON", poisonDmg, "TRUE",
                        state.activeUnit.x, state.activeUnit.y));
                if (state.activeUnit.isDead()) { endTurn(state, events); return; }
            }
        }

        state.turnPhase = (state.activeUnit.getCharClass() == Enums.CharClass.SNIPER)
                ? GameState.TurnPhase.ABILITY : GameState.TurnPhase.MOVEMENT;
        if (state.activeUnit.baseMoveDist == 0 && state.isMovementPhase()) {
            state.turnPhase = GameState.TurnPhase.ABILITY;
        }
        calculateMovementRange(state);
    }

    // -----------------------------------------------------------------------
    // Damage / heal / death
    // -----------------------------------------------------------------------

    /**
     * If Billy takes damage while Poison Trail is active, deactivate it and reveal him.
     * Call this before every raw health reduction, regardless of damage source.
     */
    private void checkAndTriggerBillyTrail(GameState state, Character target,
                                           Array<EngineEvent> events) {
        if (target instanceof Billy) {
            Billy b = (Billy) target;
            if (!b.isPoisonTrailActive) {
                b.isPoisonTrailActive = true;
                events.add(new EngineEvent.PopupEvent("POISON TRAIL!", 0, "POISON", b.x, b.y));
            }
        }
    }

    /**
     * Central damage application. Handles Billy reveal, mitigation, death.
     */
    public void applyDamage(GameState state, Character target,
                            int rawPhys, int rawMag, int rawTrue,
                            Array<EngineEvent> events) {
        checkAndTriggerBillyTrail(state, target, events);
        if (target instanceof Mason && target.isInvisible()) {
            target.setUltActive(false);
            target.setInvisible(false);
            events.add(new EngineEvent.PopupEvent("REVEALED!", 0, "STATUS", target.x, target.y));
        }

        int finalPhys = Math.max(0, rawPhys - target.getArmor());
        int finalMag  = Math.max(0, rawMag  - target.getCloak());
        int total     = finalPhys + finalMag + rawTrue;

        if (total > 0) {
            target.health = Math.max(0, target.health - total);
            if (target.isDead()) handleDeath(target, state, events);
        }
    }

    public void applyHeal(GameState state, Character target, int amount,
                          Array<EngineEvent> events) {
        if (target == null) return;
        int actualHeal = Math.min(amount, target.maxHealth - target.health);
        target.health += actualHeal;
        events.add(new EngineEvent.PopupEvent("HEAL", actualHeal, "HEAL", target.x, target.y));
    }

    public void applyStructureDamageAtTile(GameState state, int tx, int ty, int dmg, Array<EngineEvent> events) {
        Tile t = state.board.getTile(tx, ty);
        if (t != null && t.hasStructure()) {
            t.applyStructureDamage(dmg);
            events.add(new EngineEvent.PopupEvent("STRUCTURE", dmg, "OBJ", tx, ty));
        }
    }

    private void handleDeath(Character dead, GameState state, Array<EngineEvent> events) {
        // Not Even Close — Maxx revives as a zombie on first death.
        if (dead instanceof Maxx && !((Maxx) dead).zombieTriggered) {
            Maxx maxx = (Maxx) dead;
            maxx.zombieTriggered = true;
            maxx.health = Math.max(1, maxx.maxHealth / 2);
            maxx.moveDist = Math.max(0, maxx.moveDist - 1);
            maxx.baseSpeed = Math.max(1, maxx.baseSpeed / 2);
            maxx.setCurrentWait(maxx.baseSpeed); // reset wait to new faster cadence
            events.add(new EngineEvent.PopupEvent("NOT EVEN CLOSE!", 0, "STATUS", dead.x, dead.y));
            events.add(new EngineEvent.PortraitChangeEvent(maxx, "maxx_zombie.png"));
            return; // abort death — Maxx lives on as a zombie
        }

        // Only remove if this character is still the one occupying that tile.
        // (e.g. Mason's Gargoyle kill moves Mason onto the victim's tile before
        // handleDeath runs, so dead.x/dead.y now points to Mason — don't erase him.)
        if (state.board.getCharacterAt(dead.x, dead.y) == dead) {
            state.board.removeCharacter(dead.x, dead.y);
        }
        if (!state.bothTeamsAlive()) {
            boolean t1Alive = false;
            for (Character c : state.allUnits) if (!c.isDead() && c.team == 1) { t1Alive = true; break; }
            state.winnerTeam = t1Alive ? 1 : 2;
            state.phase = GameState.Phase.GAME_OVER;
            events.add(new EngineEvent.GameOverEvent(state.winnerTeam));
        }
    }

    // -----------------------------------------------------------------------
    // Board-event processing helper
    // -----------------------------------------------------------------------

    /**
     * Scans events returned by CombatBoard.moveCharacter() and resolves any
     * CharacterKilledEvents immediately via handleDeath().
     *
     * Call this after every board.moveCharacter() call instead of duplicating
     * the CharacterKilledEvent loop at each call site.
     *
     * @param boardEvents  Events returned by board.moveCharacter().
     * @param state        Current game state.
     * @param events       Master event list — boardEvents are added here, plus
     *                     any new events generated by handleDeath().
     * @return true if at least one character was killed (caller should stop pushing).
     */
    public boolean processBoardEvents(Array<EngineEvent> boardEvents,
                                      GameState state,
                                      Array<EngineEvent> events) {
        events.addAll(boardEvents);
        boolean killed = false;
        for (EngineEvent e : boardEvents) {
            if (e instanceof EngineEvent.CharacterKilledEvent) {
                EngineEvent.CharacterKilledEvent ke = (EngineEvent.CharacterKilledEvent) e;
                if (!ke.victim.isDead()) ke.victim.setHealth(0);
                handleDeath(ke.victim, state, events);
                killed = true;
            }
        }
        return killed;
    }

    // -----------------------------------------------------------------------
    // Ring collapse
    // -----------------------------------------------------------------------

    private void triggerRingCollapse(GameState state, Array<EngineEvent> events) {
        int hx = state.haven.getX();
        int hy = state.haven.getY();

        // Stop collapsing once only the Haven tile remains
        boolean onlyHavenLeft = true;
        for (int x = 0; x < state.board.getRows(); x++) {
            for (int y = 0; y < state.board.getCols(); y++) {
                if (!state.board.isCollapsedAt(x, y)
                        && !(x == hx && y == hy)) {
                    onlyHavenLeft = false;
                    break;
                }
            }
            if (!onlyHavenLeft) break;
        }
        if (onlyHavenLeft) return;

        events.add(new EngineEvent.PopupEvent("RING " + (state.collapseCount + 1) + " FALLEN!",
                0, "VOID", hx, hy));

        // Find outermost surviving column on each side.
        int leftCol = -1, rightCol = -1;
        outer:
        for (int x = 0; x < state.board.getRows(); x++) {
            for (int y = 0; y < state.board.getCols(); y++) {
                if (!state.board.isCollapsedAt(x, y)) { leftCol = x; break outer; }
            }
        }
        outer2:
        for (int x = state.board.getRows() - 1; x >= 0; x--) {
            for (int y = 0; y < state.board.getCols(); y++) {
                if (!state.board.isCollapsedAt(x, y)) { rightCol = x; break outer2; }
            }
        }

        // Find outermost surviving row on each side.
        int bottomRow = -1, topRow = -1;
        outer3:
        for (int y = 0; y < state.board.getCols(); y++) {
            for (int x = 0; x < state.board.getRows(); x++) {
                if (!state.board.isCollapsedAt(x, y)) { bottomRow = y; break outer3; }
            }
        }
        outer4:
        for (int y = state.board.getCols() - 1; y >= 0; y--) {
            for (int x = 0; x < state.board.getRows(); x++) {
                if (!state.board.isCollapsedAt(x, y)) { topRow = y; break outer4; }
            }
        }

        // Distance from Haven to each surviving edge.
        int distLeft  = (leftCol   >= 0) ? hx - leftCol   : -1;
        int distRight = (rightCol  >= 0) ? rightCol - hx   : -1;
        int distBot   = (bottomRow >= 0) ? hy - bottomRow  : -1;
        int distTop   = (topRow    >= 0) ? topRow - hy     : -1;

        // Collapse all sides that tie for the maximum distance from the Haven.
        // When Haven is centered all four sides are equal so the full outer ring falls.
        // When Haven is off-center only the farthest side(s) collapse.
        int maxDist = Math.max(Math.max(distLeft, distRight), Math.max(distBot, distTop));

        boolean collapseLeft  = (distLeft  >= 0 && distLeft  == maxDist);
        boolean collapseRight = (distRight >= 0 && distRight == maxDist);
        boolean collapseBot   = (distBot   >= 0 && distBot   == maxDist);
        boolean collapseTop   = (distTop   >= 0 && distTop   == maxDist);

        Array<Integer> colsToCollapse = new Array<>();
        Array<Integer> rowsToCollapse = new Array<>();
        if (collapseLeft  && leftCol   >= 0) colsToCollapse.add(leftCol);
        if (collapseRight && rightCol  >= 0 && rightCol  != leftCol)  colsToCollapse.add(rightCol);
        if (collapseBot   && bottomRow >= 0) rowsToCollapse.add(bottomRow);
        if (collapseTop   && topRow    >= 0 && topRow != bottomRow) rowsToCollapse.add(topRow);

        // ── Apply collapse ────────────────────────────────────────────────────
        state.pendingCollapseCols.clear();
        state.pendingCollapseRows.clear();

        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 9; y++) {
                if (state.board.isCollapsedAt(x, y)) continue;
                if (x == hx && y == hy) continue; // Haven tile always protected

                boolean inCollapsingCol = colsToCollapse.contains(x, false);
                boolean inCollapsingRow = rowsToCollapse.contains(y, false);
                if (!inCollapsingCol && !inCollapsingRow) continue;

                state.board.getTile(x, y).setCollapsed(true);
                events.add(new EngineEvent.TileEffectEvent(
                        EngineEvent.TileEffectEvent.Effect.COLLAPSE, x, y));

                Character victim = state.board.getCharacterAt(x, y);
                if (victim != null && !victim.isDead()) {
                    events.add(new EngineEvent.PopupEvent("INTO THE VOID", 0, "VOID", x, y));
                    victim.setHealth(0);
                    handleDeath(victim, state, events);
                    if (state.isGameOver()) return;
                }
            }
        }

        // Collapse done — clear pending lists so crack overlay stops rendering.
        state.pendingCollapseCols.clear();
        state.pendingCollapseRows.clear();

        state.collapseCount++;

        // All living characters gain a 1.2x ATK and MAG bonus after each ring collapse
        for (Character c : state.allUnits) {
            if (!c.isDead()) {
                c.atk = (int)(c.atk * 1.2f);
                c.mag = (int)(c.mag * 1.2f);
            }
        }
        events.add(new EngineEvent.PopupEvent("POWER SURGE!", 0, "BUFF", hx, hy));

        if (state.havenOccupant != null && !state.havenOccupant.isDead()) {
            state.havenOccupant.atk++;
            state.havenOccupant.mag++;
            state.havenOccupant.armor++;
            state.havenOccupant.cloak++;
            events.add(new EngineEvent.PopupEvent("HAVEN +" + (state.collapseCount + 1),
                    0, "BUFF", hx, hy));
        }
    }

    // -----------------------------------------------------------------------
    // Haven
    // -----------------------------------------------------------------------

    private void pushHaven(GameState state, Array<EngineEvent> events) {
    	if (state.havenLocked) {
    	    events.add(new EngineEvent.PopupEvent("HAVEN LOCKED!", 0, "STATUS",
    	            state.haven.getX(), state.haven.getY()));
    	    return;
    	}
        if (state.haven == null) return;
        int dx = state.haven.getX() - state.activeUnit.x;
        int dy = state.haven.getY() - state.activeUnit.y;
        int stepX = (dx == 0) ? 0 : (dx > 0 ? 1 : -1);
        int stepY = (dy == 0) ? 0 : (dy > 0 ? 1 : -1);
        int newHX = state.haven.getX() + stepX;
        int newHY = state.haven.getY() + stepY;
        if (state.board.isValid(newHX, newHY) && !state.board.isCollapsedAt(newHX, newHY)) {
            moveHaven(state, newHX, newHY, events);
            events.add(new EngineEvent.PopupEvent("HAVEN MOVED", 0, "MOVE", newHX, newHY));
        } else {
            events.add(new EngineEvent.PopupEvent("BLOCKED!", 0, "STATUS",
                    state.activeUnit.x, state.activeUnit.y));
        }
    }
    
 // -----------------------------------------------------------------------
 // Wind push
 // -----------------------------------------------------------------------

	 private void triggerWindPush(GameState state, Array<EngineEvent> events) {
	     // Pick a random cardinal direction
	     int[][] directions = { {1, 0}, {-1, 0}, {0, 1}, {0, -1} };
	     int[] dir = directions[new Random().nextInt(4)];
	     int dx = dir[0], dy = dir[1];
	
	     String dirName = (dx == 1) ? "EAST" : (dx == -1) ? "WEST"
	                    : (dy == 1) ? "NORTH" : "SOUTH";
	     events.add(new EngineEvent.PopupEvent("WIND PUSHES " + dirName + "!",
	             0, "VOID", state.haven.getX(), state.haven.getY()));
	
	     int dist = state.windPushDistance;
	
	     // Collect all living units — snapshot so mid-push kills don't
	     // affect the iteration order
	     Array<Character> toProcess = new Array<>();
	     for (Character c : state.allUnits) {
	         if (!c.isDead()) toProcess.add(c);
	     }
	
	     // Sort so units furthest in the push direction are moved first,
	     // preventing units from blocking each other mid-push
	     toProcess.sort((a, b) -> {
	         int posA = (dx != 0) ? a.x * dx : a.y * dy;
	         int posB = (dx != 0) ? b.x * dx : b.y * dy;
	         return posB - posA; // descending: furthest in push dir first
	     });
	
	     for (Character c : toProcess) {
	         if (c.isDead()) continue;
	         for (int step = 0; step < dist; step++) {
	             int nextX = c.x + dx;
	             int nextY = c.y + dy;
	
	             // Blocked by a structure
	             if (state.board.isValid(nextX, nextY)) {
	                 Tile t = state.board.getTile(nextX, nextY);
	                 if (t != null && t.hasStructure()) {
	                     t.applyStructureDamage(30);
	                     events.add(new EngineEvent.PopupEvent("CRASH!", 30, "OBJ", nextX, nextY));
	                     if (t.hasStructure()) break;
	                 }
	             }
	
	             // Blocked by another character
	             if (state.board.isValid(nextX, nextY)
	                     && state.board.getCharacterAt(nextX, nextY) != null) break;
	
	             // Move — board.moveCharacter handles void kills and Gargoyle
	             boolean killed = processBoardEvents(
	                     state.board.moveCharacter(c, nextX, nextY), state, events);
	             if (state.isGameOver()) return;
	             if (killed) break;
	
	             // If pushed onto a collapsed tile, kill via handleDeath
	             if (!state.board.isValid(c.x, c.y)
	                     || state.board.isCollapsedAt(c.x, c.y)) {
	                 events.add(new EngineEvent.PopupEvent("INTO THE VOID", 0, "VOID", c.x, c.y));
	                 c.setHealth(0);
	                 handleDeath(c, state, events);
	                 if (state.isGameOver()) return;
	                 break;
	             }
	         }
	     }
	
	     // Grow push distance for next event
	     state.windPushDistance++;
	     state.collapseCount++; // keeps Haven bonus escalating same as other boards

	     // All living characters gain a 1.2x ATK and MAG bonus after each wind cycle
	     for (Character c : state.allUnits) {
	         if (!c.isDead()) {
	             c.atk = (int)(c.atk * 1.2f);
	             c.mag = (int)(c.mag * 1.2f);
	         }
	     }
	     events.add(new EngineEvent.PopupEvent("POWER SURGE!", 0, "BUFF",
	             state.haven.getX(), state.haven.getY()));

	     if (state.havenOccupant != null && !state.havenOccupant.isDead()) {
	         state.havenOccupant.atk++;
	         state.havenOccupant.mag++;
	         state.havenOccupant.armor++;
	         state.havenOccupant.cloak++;
	         events.add(new EngineEvent.PopupEvent("HAVEN +" + state.collapseCount,
	                 0, "BUFF", state.haven.getX(), state.haven.getY()));
	     }
	 }
	 
	// -----------------------------------------------------------------------
	// Desert collapse
	// -----------------------------------------------------------------------

	private void triggerDesertCollapse(GameState state, Array<EngineEvent> events) {
	    if (state.haven == null) return;

	    int hx = state.haven.getX();
	    int hy = state.haven.getY();

	    // Find the non-collapsed, non-Haven tile furthest from the Haven
	    // by Manhattan distance. Break ties by picking the highest x then y.
	    int bestX = -1, bestY = -1, bestDist = -1;
	    for (int x = 0; x < state.board.getRows(); x++) {
	        for (int y = 0; y < state.board.getCols(); y++) {
	            if (state.board.isCollapsedAt(x, y)) continue;
	            if (x == hx && y == hy) continue; // Haven always protected
	            int dist = Math.abs(x - hx) + Math.abs(y - hy);
	            if (dist > bestDist
	                    || (dist == bestDist && (x > bestX
	                    || (x == bestX && y > bestY)))) {
	                bestDist = dist;
	                bestX = x;
	                bestY = y;
	            }
	        }
	    }

	    if (bestX == -1) return; // No tile to collapse — only Haven remains

	    // Collapse the tile
	    state.board.getTile(bestX, bestY).setCollapsed(true);
	    events.add(new EngineEvent.TileEffectEvent(
	            EngineEvent.TileEffectEvent.Effect.COLLAPSE, bestX, bestY));
	    events.add(new EngineEvent.PopupEvent("SANDS SHIFT!", 0, "VOID", bestX, bestY));

	    // Kill any character standing on the collapsed tile
	    Character victim = state.board.getCharacterAt(bestX, bestY);
	    if (victim != null && !victim.isDead()) {
	        events.add(new EngineEvent.PopupEvent("INTO THE VOID", 0, "VOID", bestX, bestY));
	        victim.setHealth(0);
	        handleDeath(victim, state, events);
	        if (state.isGameOver()) return;
	    }

	    // Increment collapseCount so Haven bonus escalates same as other boards
	    state.collapseCount++;
	    if (state.havenOccupant != null && !state.havenOccupant.isDead()) {
	        state.havenOccupant.atk++;
	        state.havenOccupant.mag++;
	        state.havenOccupant.armor++;
	        state.havenOccupant.cloak++;
	        events.add(new EngineEvent.PopupEvent("HAVEN +" + state.collapseCount,
	                0, "BUFF", hx, hy));
	    }
	}

    /**
     * Pulls the Haven one tile directly toward the active unit.
     * The step direction is the inverse of pushHaven — toward the unit rather than away.
     * If the destination is invalid or collapsed, the pull is blocked.
     */
    private void pullHaven(GameState state, Array<EngineEvent> events) {
    	if (state.havenLocked) {
    	    events.add(new EngineEvent.PopupEvent("HAVEN LOCKED!", 0, "STATUS",
    	            state.haven.getX(), state.haven.getY()));
    	    return;
    	}
        if (state.haven == null) return;
        int dx = state.activeUnit.x - state.haven.getX();
        int dy = state.activeUnit.y - state.haven.getY();
        int stepX = (dx == 0) ? 0 : (dx > 0 ? 1 : -1);
        int stepY = (dy == 0) ? 0 : (dy > 0 ? 1 : -1);
        int newHX = state.haven.getX() + stepX;
        int newHY = state.haven.getY() + stepY;
        if (state.board.isValid(newHX, newHY) && !state.board.isCollapsedAt(newHX, newHY)) {
            moveHaven(state, newHX, newHY, events);
            events.add(new EngineEvent.PopupEvent("HAVEN PULLED", 0, "MOVE", newHX, newHY));
        } else {
            events.add(new EngineEvent.PopupEvent("BLOCKED!", 0, "STATUS",
                    state.activeUnit.x, state.activeUnit.y));
        }
    }

    public void moveHaven(GameState state, int newX, int newY, Array<EngineEvent> events) {
    	if (state.havenLocked) {
    	    events.add(new EngineEvent.PopupEvent("HAVEN LOCKED!", 0, "STATUS",
    	            state.haven.getX(), state.haven.getY()));
    	    return;
    	}
        if (state.haven == null) return;
        if (!state.board.isValid(newX, newY) || state.board.isCollapsedAt(newX, newY)) return;
        if (state.havenOccupant != null && !state.havenOccupant.isDead()) {
            int bonus = state.collapseCount + 1;
            state.havenOccupant.atk   -= bonus;
            state.havenOccupant.mag   -= bonus;
            state.havenOccupant.armor -= bonus;
            state.havenOccupant.cloak -= bonus;
            state.havenOccupant = null;
        }
        state.haven.moveTo(newX, newY);
    }

    // -----------------------------------------------------------------------
    // Range calculations
    // -----------------------------------------------------------------------

    public void calculateMovementRange(GameState state) {
        state.reachableTiles.clear();
        Character unit = state.activeUnit;
        if (unit == null) return;

        if (unit.getCharClass() == Enums.CharClass.STATUE && !unit.hasDeployed) {
            boolean isTeam1 = (unit.team == 1);
            for (int x = 0; x < 9; x++)
                for (int y = 0; y < 9; y++) {
                    boolean validRow = isTeam1 ? (y <= 4) : (y >= 4);
                    boolean notOuter = !isWindBoard(state)
                            || (x > 0 && x < 8 && y > 0 && y < 8);
                    if (validRow && notOuter && state.board.getCharacterAt(x, y) == null)
                        state.reachableTiles.add(new Vector2(x, y));
                }
            return;
        }

        if (!unit.hasDeployed && unit.getCharClass() != Enums.CharClass.STATUE
                && !(unit instanceof Billy)) {
            boolean isTeam1 = (unit.team == 1);
            for (int x = 0; x < 9; x++)
                for (int y = 0; y < 9; y++) {
                    boolean validRow = isTeam1 ? (y <= 1) : (y >= 7);
                    boolean notOuter = !isWindBoard(state)
                            || (x > 0 && x < 8 && y > 0 && y < 8);
                    if (validRow && notOuter && state.board.getCharacterAt(x, y) == null)
                        state.reachableTiles.add(new Vector2(x, y));
                }
            return;
        }

        // Billy's deployment turn — same rows as regular characters
        if (unit instanceof Billy && !unit.hasDeployed) {
            boolean isTeam1 = (unit.team == 1);
            for (int x = 0; x < 9; x++)
                for (int y = 0; y < 9; y++) {
                    boolean validRow = isTeam1 ? (y <= 1) : (y >= 7);
                    boolean notOuter = !isWindBoard(state)
                            || (x > 0 && x < 8 && y > 0 && y < 8);
                    if (validRow && notOuter && state.board.getCharacterAt(x, y) == null)
                        state.reachableTiles.add(new Vector2(x, y));
                }
            return;
        }

        int d;
        if (unit instanceof Mason && ((Mason) unit).isTakeFlightActive) d = 7;
        else if (state.isFighterBonusMove) d = 1;
        else d = unit.baseMoveDist;

        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 9; y++) {
                if (Math.abs(x - unit.x) + Math.abs(y - unit.y) <= d) {
                    Character occ = state.board.getCharacterAt(x, y);
                    Tile t = state.board.getTile(x, y);
                    // Clothes piles are walkable by everyone — only Ghia gets invisibility.
                    boolean passable = t != null && !t.isCollapsed()
                            && (!t.hasStructure() || t.isClothes());
                    if (passable) {
                        if (occ == null || occ == unit) {
                            state.reachableTiles.add(new Vector2(x, y));
                        } else if (occ.isInvisible() && occ.team != unit.team) {
                            state.reachableTiles.add(new Vector2(x, y));
                        }
                    }
                }
            }
        }

        // Assassin bonus: tiles adjacent to visible enemies
        if (unit.getCharClass() == Enums.CharClass.ASSASSIN) {
            for (int ex = 0; ex < 9; ex++) {
                for (int ey = 0; ey < 9; ey++) {
                    Character enemy = state.board.getCharacterAt(ex, ey);
                    if (enemy == null || enemy.team == unit.team || enemy.isInvisible()) continue;
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dy = -1; dy <= 1; dy++) {
                            if (dx == 0 && dy == 0) continue;
                            int nx = ex + dx, ny = ey + dy;
                            if (!state.board.isValid(nx, ny)) continue;
                            Tile t = state.board.getTile(nx, ny);
                            Character occ = state.board.getCharacterAt(nx, ny);
                            if (t == null || (t.hasStructure() && !t.isClothes()) || t.isCollapsed()) continue;
                            if (occ != null && occ != unit && !occ.isInvisible()) continue;
                            boolean already = false;
                            for (Vector2 v : state.reachableTiles)
                                if ((int)v.x == nx && (int)v.y == ny) { already = true; break; }
                            if (!already) state.reachableTiles.add(new Vector2(nx, ny));
                        }
                    }
                }
            }
        }
    }

    public void calculateTargetRange(GameState state, Ability ab) {
        state.targetableTiles.clear();
        Character unit = state.activeUnit;

        // Sean's direction step (after anchor set)
        if (unit instanceof Sean && ((Sean) unit).wallAnchor != null) {
            Vector2 anchor = ((Sean) unit).wallAnchor;
            state.targetableTiles.add(new Vector2(anchor.x, anchor.y + 1));
            state.targetableTiles.add(new Vector2(anchor.x, anchor.y - 1));
            state.targetableTiles.add(new Vector2(anchor.x + 1, anchor.y));
            state.targetableTiles.add(new Vector2(anchor.x - 1, anchor.y));
            return;
        }

        // Lark's direction step (after anchor set)
        if (unit instanceof Lark && ((Lark) unit).wallOfFireAnchor != null) {
            Vector2 anchor = ((Lark) unit).wallOfFireAnchor;
            state.targetableTiles.add(new Vector2(anchor.x, anchor.y + 1));
            state.targetableTiles.add(new Vector2(anchor.x, anchor.y - 1));
            state.targetableTiles.add(new Vector2(anchor.x + 1, anchor.y));
            state.targetableTiles.add(new Vector2(anchor.x - 1, anchor.y));
            return;
        }

        // Luke's Pergolatory — after anchor is set, show the 3 other diagonal corners
        // that would form a valid 2x2 with the anchor.
        if (unit instanceof Luke && ((Luke) unit).pergolaAnchor != null) {
            int ax = (int) ((Luke) unit).pergolaAnchor.x;
            int ay = (int) ((Luke) unit).pergolaAnchor.y;
            int[][] corners = { {ax+1, ay+1}, {ax-1, ay+1}, {ax+1, ay-1}, {ax-1, ay-1} };
            for (int[] corner : corners) {
                int cx = corner[0], cy = corner[1];
                if (!state.board.isValid(cx, cy)) continue;
                // Check all 4 tiles of the 2x2 formed by anchor + this corner are valid
                int minX = Math.min(ax, cx), minY = Math.min(ay, cy);
                boolean valid = true;
                for (int dx = 0; dx <= 1 && valid; dx++)
                    for (int dy = 0; dy <= 1 && valid; dy++) {
                        int tx2 = minX + dx, ty2 = minY + dy;
                        if (!state.board.isValid(tx2, ty2)) { valid = false; break; }
                        Tile t = state.board.getTile(tx2, ty2);
                        if (t == null || t.isCollapsed()) { valid = false; break; }
                        if (state.board.getCharacterAt(tx2, ty2) != null) { valid = false; break; }
                    }
                if (valid) state.targetableTiles.add(new Vector2(cx, cy));
            }
            return;
        }

        // Take Flight — empty non-collapsed non-structure tiles within 3
        if (unit instanceof Mason && ab.getName().equals("Take Flight")) {
            for (int x = 0; x < 9; x++) {
                for (int y = 0; y < 9; y++) {
                    if (Math.abs(x - unit.x) + Math.abs(y - unit.y) > 3) continue;
                    Tile t = state.board.getTile(x, y);
                    if (t == null || t.isCollapsed() || t.hasStructure()) continue;
                    if (state.board.getCharacterAt(x, y) != null) continue;
                    state.targetableTiles.add(new Vector2(x, y));
                }
            }
            return;
        }

        // Summon The Wind — show the 4 cardinal tiles adjacent to the tower as direction choices
        if (unit instanceof GuardTower && ab.getName().equals("Summon The Wind")) {
            int[][] cardinals = { {unit.x+1, unit.y}, {unit.x-1, unit.y},
                                  {unit.x, unit.y+1}, {unit.x, unit.y-1} };
            for (int[] c : cardinals) {
                if (state.board.isValid(c[0], c[1]))
                    state.targetableTiles.add(new Vector2(c[0], c[1]));
            }
            return;
        }

        // Rockslide — show the 4 cardinal tiles adjacent to the Stoneguard as direction choices
        if (unit instanceof Stoneguard && ab.getName().equals("Rockslide")) {
            int[][] cardinals = { {unit.x+1, unit.y}, {unit.x-1, unit.y},
                                  {unit.x, unit.y+1}, {unit.x, unit.y-1} };
            for (int[] c : cardinals) {
                if (state.board.isValid(c[0], c[1]))
                    state.targetableTiles.add(new Vector2(c[0], c[1]));
            }
            return;
        }

        // Bull Charge — show the 4 cardinal tiles adjacent to Weirdguard as direction choices
        if (unit instanceof Weirdguard && ab.getName().equals("Bull Charge")) {
            int[][] cardinals = { {unit.x+1, unit.y}, {unit.x-1, unit.y},
                                  {unit.x, unit.y+1}, {unit.x, unit.y-1} };
            for (int[] c : cardinals) {
                if (state.board.isValid(c[0], c[1]))
                    state.targetableTiles.add(new Vector2(c[0], c[1]));
            }
            return;
        }

        // Lick Wounds — show all surviving clothes tiles Ghia can teleport to
        if (unit instanceof Ghia && ab.getName().equals("Lick Wounds")) {
            for (Vector2 v : ((Ghia) unit).clothesTiles) {
                int cx = (int) v.x, cy = (int) v.y;
                Tile t = state.board.getTile(cx, cy);
                if (t == null || !t.isClothes() || !t.hasStructure()) continue;
                if (state.board.getCharacterAt(cx, cy) != null) continue;
                state.targetableTiles.add(new Vector2(cx, cy));
            }
            return;
        }

        // Away Game
        if (unit instanceof Speen && ab.getName().equals("Away Game")) {
            for (int x = 0; x < 9; x++) {
                for (int y = 0; y < 9; y++) {
                    Tile t = state.board.getTile(x, y);
                    if (t == null || t.isCollapsed() || t.hasStructure()) continue;
                    Character occ = state.board.getCharacterAt(x, y);
                    if (occ == null || occ == unit) state.targetableTiles.add(new Vector2(x, y));
                }
            }
            return;
        }

        int range = ab.getRange();
        for (int x = 0; x < 9; x++)
            for (int y = 0; y < 9; y++)
                if (Math.abs(x - unit.x) + Math.abs(y - unit.y) <= range)
                    state.targetableTiles.add(new Vector2(x, y));
    }

    // -----------------------------------------------------------------------
    // Tile state updates
    // -----------------------------------------------------------------------

    private void updateTileStates(GameState state) {
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 9; y++) {
                Tile t = state.board.getTile(x, y);
                if (t.isFrozen()) {
                    Character occ = state.board.getCharacterAt(x, y);
                    if (occ == null || occ.isDead() || occ.getCurrentWait() <= 0)
                        t.setFrozen(false);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private boolean isWindBoard(GameState state) {
        return state.boardConfig != null
                && state.boardConfig.type == BoardConfig.BoardType.WIND;
    }

    private void clearAbilitySelection(GameState state) {
        state.selectedAbility    = null;
        state.targetableTiles.clear();
        state.selectedTargetTile = null;
    }

    /**
     * Spawns 3 clothes piles randomly on the board for the given Ghia.
     * Skips collapsed tiles, tiles with structures, and occupied tiles.
     * Stores tile coordinates on the Ghia instance for Lick Wounds targeting.
     */
    private void spawnClothesPiles(Ghia ghia, GameState state) {
        Random rng = new Random();
        int placed = 0;
        int attempts = 0;
        while (placed < 3 && attempts < 100) {
            attempts++;
            int x = rng.nextInt(9);
            int y = rng.nextInt(9);
            Tile t = state.board.getTile(x, y);
            if (t == null || t.isCollapsed() || t.hasStructure()) continue;
            if (state.board.getCharacterAt(x, y) != null) continue;
            // Avoid placing two piles on the same tile
            boolean alreadyUsed = false;
            for (Vector2 v : ghia.clothesTiles) {
                if ((int) v.x == x && (int) v.y == y) { alreadyUsed = true; break; }
            }
            if (alreadyUsed) continue;
            t.setStructureHP(10);
            t.setClothes(true);
            ghia.clothesTiles.add(new Vector2(x, y));
            placed++;
        }
    }

    /**
     * Checks if Ghia is standing on a clothes tile and applies or removes
     * her invisibility accordingly. Called at the start of each turn.
     */
    private void updateGhiaClothesInvisibility(Ghia ghia, GameState state, Array<EngineEvent> events) {
        Tile standing = state.board.getTile(ghia.x, ghia.y);
        boolean onClothes = standing != null && standing.isClothes() && standing.hasStructure();
        if (onClothes && !ghia.isInvisible()) {
            ghia.setInvisible(true);
            events.add(new EngineEvent.PopupEvent("HIDING", 0, "STEALTH", ghia.x, ghia.y));
        } else if (!onClothes && ghia.isInvisible()) {
            ghia.setInvisible(false);
            events.add(new EngineEvent.PopupEvent("REVEALED", 0, "STEALTH", ghia.x, ghia.y));
        }
    }
}