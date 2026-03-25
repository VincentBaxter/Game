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
import com.mygame.tactics.Tile;

public class Luke extends Character {

    public Vector2 pergolaAnchor = null;

    public Luke(Texture portrait) {
        super("Luke", portrait, Enums.CharClass.ENGINEER, Enums.CharType.FLORA, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.RARE;
        this.originLocation = "Act 2";
        this.baseMaxHealth = 80;
        this.baseAtk = 20;
        this.baseMag = 8;
        this.baseArmor = 2;
        this.baseCloak = 7;
        this.baseSpeed = 800;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 3;
        this.baseRange = 1;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new Hack();
        this.abilities[1] = new HerbalMedicine();
        this.abilities[2] = new Pergolatory();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class Hack extends Ability {
        public Hack() {
            super("Hack", "Basic attack. Deal ATK - ARM damage.", 1, true);
            this.showAtk = true;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team != user.team) {
                if (CombatUtils.rollDodge(target, events, tx, ty)) return AbilityResult.END_TURN;
                int finalPhys = Math.max(0, user.getAtk() - target.getArmor());
                finalPhys = CombatUtils.applyCrit(user, finalPhys, events, tx, ty);
                target.health = Math.max(0, target.health - finalPhys);
                events.add(new EngineEvent.PopupEvent("HACK", finalPhys, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getAtk(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class HerbalMedicine extends Ability {
        public HerbalMedicine() {
            super("Herbal Medicine", "Heal an ally within 3 tiles for MAG HP.", 3, true);
            this.isHeal   = true;
            this.showHeal = true;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team == user.team && !target.isDead()) {
                int actualHeal = Math.min(user.getMag(), target.maxHealth - target.health);
                target.health += actualHeal;
                events.add(new EngineEvent.PopupEvent("HEAL", actualHeal, "HEAL", target.x, target.y));
            }
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Two-click ability — GameEngine sets pergolaAnchor on the first click,
     * then calls execute() on the second click with the confirmed anchor in place.
     * Mirrors the same pattern as Sean's Painted Walls and Lark's Wall of Fire.
     *
     * TODO: Characters standing on a pergola tile should receive +1 range.
     * This buff needs to be applied/removed in GameEngine.startTurn() by checking
     * whether the active unit is standing on a tile with hasStructure() == true
     * and the structure was placed by Luke (pergola). A Luke reference or a tile
     * flag (e.g. isPergola) would be needed to distinguish pergola tiles from
     * Sean's walls or other structures.
     */
    public static class Pergolatory extends Ability {
        public Pergolatory() {
            super("Pergolatory", "Place a 2x2 breakable structure. Characters on tiles gain +1 range.", 4, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (!(user instanceof Luke)) return AbilityResult.END_TURN;
            Luke luke = (Luke) user;
            if (luke.pergolaAnchor == null) return AbilityResult.END_TURN;

            int ax = (int) luke.pergolaAnchor.x;
            int ay = (int) luke.pergolaAnchor.y;
            // tx/ty is the chosen diagonal corner — derive the 2x2 from the two corners
            int minX = Math.min(ax, tx);
            int minY = Math.min(ay, ty);

            placeSegment(minX,     minY,     state, events);
            placeSegment(minX + 1, minY,     state, events);
            placeSegment(minX,     minY + 1, state, events);
            placeSegment(minX + 1, minY + 1, state, events);

            luke.pergolaAnchor = null;
            events.add(new EngineEvent.PopupEvent("PERGOLA BUILT", 0, "WALL", minX, minY));
            return AbilityResult.END_TURN;
        }

        private void placeSegment(int x, int y, GameState state, Array<EngineEvent> events) {
            if (state.board.isValid(x, y) && state.board.getCharacterAt(x, y) == null) {
                Tile t = state.board.getTile(x, y);
                t.setStructureHP(30);
                t.setPergola(true);
                events.add(new EngineEvent.TileEffectEvent(
                        EngineEvent.TileEffectEvent.Effect.WALL_PLACED, x, y));
            }
        }
    }
}