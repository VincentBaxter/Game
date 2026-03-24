package com.mygame.tactics.characters;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.mygame.tactics.Ability;
import com.mygame.tactics.AbilityResult;
import com.mygame.tactics.Character;
import com.mygame.tactics.CombatUtils;
import com.mygame.tactics.EngineEvent;
import com.mygame.tactics.Enums;
import com.mygame.tactics.GameState;

public class Brad extends Character {

    public Brad(Texture portrait) {
        super("Brad", portrait, Enums.CharClass.SNIPER, Enums.CharType.AQUA, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.SPECIAL;
        this.originLocation = "Trade Hub";
        this.baseMaxHealth = 30;
        this.baseAtk = 10;
        this.baseMag = 0;
        this.baseArmor = 10;
        this.baseCloak = 0;
        this.baseSpeed = 762;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 2;
        this.baseRange = 15;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new Stab();
        this.abilities[1] = new Att4570();
        this.abilities[2] = new Hook();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class Stab extends Ability {
        public Stab() {
            super("Stab", "Basic attack. Deals ATK damage.", 1, true);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team != user.team) {
                if (CombatUtils.rollDodge(target, events, tx, ty)) return AbilityResult.END_TURN;
                int finalDamage = Math.max(0, user.getAtk() - target.getArmor());
                finalDamage = CombatUtils.applyCrit(user, finalDamage, events, tx, ty);
                target.health = Math.max(0, target.health - finalDamage);
                events.add(new EngineEvent.PopupEvent("STAB", finalDamage, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getAtk(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class Att4570 extends Ability {
        public Att4570() {
            super("ATT .45-70", "Shoot an enemy for heavy ATK damage.", 4, true);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team != user.team) {
                if (CombatUtils.rollDodge(target, events, tx, ty)) return AbilityResult.END_TURN;
                int rawDamage   = (int) (user.getAtk() * 2.5);
                int finalDamage = Math.max(0, rawDamage - target.getArmor());
                finalDamage = CombatUtils.applyCrit(user, finalDamage, events, tx, ty);
                target.health = Math.max(0, target.health - finalDamage);
                events.add(new EngineEvent.PopupEvent(".45-70", finalDamage, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty,
                        (int)(user.getAtk() * 2.5), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class Hook extends Ability {
        public Hook() {
            super("Hook", "Pull an enemy to the tile next to you.", 4, true);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target == null || target.team == user.team) return AbilityResult.END_TURN;

            // Find the closest empty tile adjacent to Brad
            int bestX = -1, bestY = -1, bestDist = Integer.MAX_VALUE;
            int[][] neighbors = {
                {user.x - 1, user.y},
                {user.x + 1, user.y},
                {user.x, user.y - 1},
                {user.x, user.y + 1}
            };

            for (int[] n : neighbors) {
                int nx = n[0], ny = n[1];
                if (state.board.isValid(nx, ny)
                        && state.board.getCharacterAt(nx, ny) == null) {
                    int dist = Math.abs(nx - tx) + Math.abs(ny - ty);
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestX = nx;
                        bestY = ny;
                    }
                }
            }

            if (bestX != -1) {
                state.engine.processBoardEvents(
                        state.board.moveCharacter(target, bestX, bestY), state, events);
                events.add(new EngineEvent.PopupEvent("HOOKED!", 0, "STATUS", bestX, bestY));
            }

            return AbilityResult.END_TURN;
        }
    }
}