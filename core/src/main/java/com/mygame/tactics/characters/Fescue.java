package com.mygame.tactics.characters;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.mygame.tactics.Ability;
import com.mygame.tactics.AbilityResult;
import com.mygame.tactics.Character;
import com.mygame.tactics.EngineEvent;
import com.mygame.tactics.Enums;
import com.mygame.tactics.GameState;
import com.mygame.tactics.Tile;

public class Fescue extends Character {

    public Fescue(Texture portrait) {
        super("Fescue", portrait, Enums.CharClass.SUPPORT, Enums.CharType.FLORA, Enums.Alliance.NONE);
        this.rarity             = Enums.Rarity.COMMON;
        this.originLocation     = "Forest";
        this.baseMaxHealth      = 50;
        this.baseAtk            = 0;
        this.baseMag            = 10;
        this.baseArmor          = 0;
        this.baseCloak          = 5;
        this.baseSpeed          = 901;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist       = 2;
        this.baseRange          = 3;
        this.baseCritChance     = 0.0;
        this.baseDodgeChance    = 0.0;
        this.abilities[0]       = new Fertilizer();
        this.abilities[1]       = new Pesticides();
        this.abilities[2]       = new Rescue();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    /** Fertilizer — heals a single ally within range 3 for MAG HP. */
    public static class Fertilizer extends Ability {
        public Fertilizer() {
            super("Fertilizer", "Heal an ally within range 3 for MAG HP.", 3, true);
            this.isHeal   = true;
            this.showHeal = true;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team == user.team && !target.isDead()) {
                state.engine.applyHeal(state, target, user.getMag(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    /** Pesticides — applies 1 stack of poison to a target enemy within range 3. */
    public static class Pesticides extends Ability {
        public Pesticides() {
            super("Pesticides", "Apply 1 poison to an enemy within range 3.", 3, true);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team != user.team && !target.isDead()) {
                target.applyPoison();
                events.add(new EngineEvent.PopupEvent("POISONED", 1, "POISON", tx, ty));
            }
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Rescue — self-cast. Pulls every living ally to the nearest empty tile
     * adjacent to Fescue. Allies already adjacent are left in place.
     * Up to 8 allies can be rescued (one per surrounding tile).
     */
    public static class Rescue extends Ability {
        public Rescue() {
            super("Rescue", "Pull all allies to an adjacent tile.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            int ux = user.x, uy = user.y;

            // All 8 surrounding tiles, checked in orthogonal-first order
            int[][] dirs = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};

            // Collect living allies (not the user)
            Array<Character> allies = new Array<>();
            for (Character c : state.allUnits) {
                if (c.team == user.team && c != user && !c.isDead()) allies.add(c);
            }

            int pulled = 0;
            for (Character ally : allies) {
                // Already adjacent — nothing to do
                boolean alreadyAdj = Math.abs(ally.x - ux) <= 1 && Math.abs(ally.y - uy) <= 1;
                if (alreadyAdj) continue;

                // Find the closest empty adjacent tile to Fescue
                int destX = -1, destY = -1, bestDist = Integer.MAX_VALUE;
                for (int[] d : dirs) {
                    int nx = ux + d[0], ny = uy + d[1];
                    if (!state.board.isValid(nx, ny)) continue;
                    if (state.board.getCharacterAt(nx, ny) != null) continue;
                    Tile t = state.board.getTile(nx, ny);
                    if (t == null || t.isCollapsed() || (t.hasStructure() && !t.isClothes())) continue;
                    int dist = Math.abs(ally.x - nx) + Math.abs(ally.y - ny);
                    if (dist < bestDist) { bestDist = dist; destX = nx; destY = ny; }
                }

                if (destX < 0) continue; // no free adjacent tile

                events.add(new EngineEvent.MoveAnimationEvent(ally, ally.x, ally.y, destX, destY));
                Array<EngineEvent> boardEvents = state.board.moveCharacter(ally, destX, destY);
                events.addAll(boardEvents);
                events.add(new EngineEvent.PopupEvent("RESCUED", 0, "MOVE", destX, destY));
                pulled++;
            }

            if (pulled > 0) {
                events.add(new EngineEvent.PopupEvent("RESCUE!", 0, "ACTIVE", ux, uy));
            }

            return AbilityResult.END_TURN;
        }
    }
}
