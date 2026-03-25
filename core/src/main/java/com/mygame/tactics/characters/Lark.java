package com.mygame.tactics.characters;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.mygame.tactics.Ability;
import com.mygame.tactics.AbilityResult;
import com.mygame.tactics.Character;
import com.mygame.tactics.CombatUtils;
import com.mygame.tactics.EngineEvent;
import com.mygame.tactics.Enums;
import com.mygame.tactics.GameState;

public class Lark extends Character {

    public int fireCount = 1; // Starts at 1 so first Wall of Fire has a meaningful duration
    public Vector2 wallOfFireAnchor    = null;
    public Vector2 wallOfFireDirection = null;

    public Lark(Texture portrait) {
        super("Lark", portrait, Enums.CharClass.CHAOS, Enums.CharType.FIRE, Enums.Alliance.WITCH);
        this.rarity = Enums.Rarity.MYSTIC;
        this.originLocation = "Mountain";
        this.baseMaxHealth = 98;
        this.baseAtk = 150;
        this.baseMag = 12;
        this.baseArmor = 4;
        this.baseCloak = 4;
        this.baseSpeed = 685;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 2;
        this.baseRange = 3;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.1;
        this.abilities[0] = new Fireball();
        this.abilities[1] = new WallOfFire();
        this.abilities[2] = new Hell();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    /**
     * Fireball — AoE magic attack hitting all enemies within Manhattan distance 2
     * of the target tile. Deals MAG + fireCount magic damage to each.
     * Increments fireCount by 1 after firing.
     */
    public static class Fireball extends Ability {
        public Fireball() {
            super("Fireball", "Deal MAG + fireCount magic damage to all enemies within 2 tiles of target. +1 fireCount.", 3, true);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            int fireCount = (user instanceof Lark) ? ((Lark) user).fireCount : 0;
            int rawMag = user.getMag() + fireCount;
            boolean hitAnyone = false;

            // Hit all enemies within Manhattan distance 2 of the target tile
            for (int x = 0; x < state.board.getRows(); x++) {
                for (int y = 0; y < state.board.getCols(); y++) {
                    if (Math.abs(x - tx) + Math.abs(y - ty) > 2) continue;
                    Character hit = state.board.getCharacterAt(x, y);
                    if (hit != null && hit.team != user.team && !hit.isDead()) {
                        if (CombatUtils.rollDodge(hit, events, x, y)) continue;
                        int finalMag = Math.max(0, rawMag - hit.getCloak());
                        finalMag = CombatUtils.applyCrit(user, finalMag, events, x, y);
                        hit.health = Math.max(0, hit.health - finalMag);
                        events.add(new EngineEvent.PopupEvent("FIREBALL", finalMag, "MAG", x, y));
                        hitAnyone = true;
                    } else if (hit == null) {
                        // Damage any structure in the AoE
                        com.mygame.tactics.Tile t = state.board.getTile(x, y);
                        if (t != null && t.hasStructure()) {
                            state.engine.applyStructureDamageAtTile(state, x, y, rawMag, events);
                            hitAnyone = true;
                        }
                    }
                }
            }

            if (!hitAnyone) {
                events.add(new EngineEvent.PopupEvent("MISS", 0, "STATUS", tx, ty));
            }

            // Increment fireCount after use
            if (user instanceof Lark) {
                ((Lark) user).fireCount++;
                events.add(new EngineEvent.PopupEvent("FIRE COUNT " + ((Lark) user).fireCount,
                        0, "BUFF", user.x, user.y));
            }

            events.add(new EngineEvent.AbilityResolveEvent(0.8f));
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Wall of Fire — two-step ability. First click sets the anchor tile (via
     * TwoStepAbilityAction / wallOfFireAnchor). Second click picks a direction
     * tile adjacent to the anchor; the wall extends 2 tiles in both directions
     * along that axis (5 tiles total). Burns for fireCount turns. +1 fireCount.
     *
     * GameEngine handles setting wallOfFireAnchor and routing the
     * TwoStepAbilityAction, mirroring the "Painted Walls" pattern.
     */
    public static class WallOfFire extends Ability {
        public WallOfFire() {
            super("Wall of Fire",
                    "Place a 1×5 line of fire centred on a target tile. " +
                    "Burns for fireCount turns. +1 fireCount.", 4, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (!(user instanceof Lark)) return AbilityResult.END_TURN;
            Lark lark = (Lark) user;
            if (lark.wallOfFireAnchor == null || lark.wallOfFireDirection == null)
                return AbilityResult.AWAIT_SECOND_CLICK;

            int ax  = (int) lark.wallOfFireAnchor.x;
            int ay  = (int) lark.wallOfFireAnchor.y;
            int dx  = (int) lark.wallOfFireDirection.x;
            int dy  = (int) lark.wallOfFireDirection.y;
            int duration = Math.max(1, lark.fireCount);

            // Vertical if direction click shares the anchor's X column, else horizontal
            boolean vertical = (dx == ax);

            for (int i = -2; i <= 2; i++) {
                int fx = vertical ? ax     : ax + i;
                int fy = vertical ? ay + i : ay;
                if (state.board.isValid(fx, fy) && !state.board.isCollapsedAt(fx, fy)) {
                    state.board.applyFire(fx, fy, user, duration);
                    events.add(new EngineEvent.PopupEvent("FIRE", 0, "WALL", fx, fy));
                }
            }

            lark.fireCount++;
            lark.wallOfFireAnchor    = null;
            lark.wallOfFireDirection = null;
            events.add(new EngineEvent.PopupEvent("WALL OF FIRE", 0, "FIRE", ax, ay));
            events.add(new EngineEvent.PopupEvent("FIRE COUNT " + lark.fireCount,
                    0, "BUFF", user.x, user.y));

            events.add(new EngineEvent.AbilityResolveEvent(0.8f));
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Hell — sets every non-collapsed tile on the board on fire for
     * fireCount + 1 turns. Once per game. +1 fireCount.
     */
    public static class Hell extends Ability {
        public Hell() {
            super("Hell",
                    "Set every tile on fire for fireCount+1 turns. Once per game. +1 fireCount.",
                    0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            int fireCount = (user instanceof Lark) ? ((Lark) user).fireCount : 0;
            int duration  = fireCount + 1;

            for (int x = 0; x < state.board.getRows(); x++) {
                for (int y = 0; y < state.board.getCols(); y++) {
                    if (!state.board.isCollapsedAt(x, y)) {
                        state.board.applyFire(x, y, user, duration);
                        events.add(new EngineEvent.PopupEvent("FIRE", 0, "HELL", x, y));
                    }
                }
            }

            // Increment fireCount after use
            if (user instanceof Lark) {
                ((Lark) user).fireCount++;
                events.add(new EngineEvent.PopupEvent("HELL UNLEASHED", 0, "FIRE", user.x, user.y));
                events.add(new EngineEvent.PopupEvent("FIRE COUNT " + ((Lark) user).fireCount,
                        0, "BUFF", user.x, user.y));
            }

            events.add(new EngineEvent.AbilityResolveEvent(1.2f));
            return AbilityResult.END_TURN;
        }
    }
}