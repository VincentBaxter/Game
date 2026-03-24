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

public class Stoneguard extends Character {

    public Stoneguard(Texture portrait) {
        super("Stoneguard", portrait, Enums.CharClass.TANK, Enums.CharType.FLORA, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.COMMON;
        this.originLocation = "Trade Hub";
        this.baseMaxHealth = 60;
        this.baseAtk = 5;
        this.baseMag = 0;
        this.baseArmor = 12;
        this.baseCloak = 15;
        this.baseSpeed = 1050;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 2;
        this.baseRange = 1;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new Tumble();
        this.abilities[1] = new Harden();
        this.abilities[2] = new Rockslide();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class Tumble extends Ability {
        public Tumble() {
            super("Tumble", "Basic attack. Deals ATK damage.", 1, true);
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
                events.add(new EngineEvent.PopupEvent("TUMBLE", finalDamage, "ATK", tx, ty));
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class Harden extends Ability {
        public Harden() {
            super("Harden", "Gain +5 Armor and +5 Cloak.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            user.armor += 5;
            user.cloak += 5;
            events.add(new EngineEvent.PopupEvent("+5 ARM / +5 CLK", 0, "BUFF", user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Rockslide — click one of the 4 cardinal tiles adjacent to the Stoneguard
     * to choose a direction. Deals heavy ATK damage to every enemy in that line.
     * Direction tiles are shown by GameEngine.calculateTargetRange().
     */
    public static class Rockslide extends Ability {
        public Rockslide() {
            super("Rockslide", "Deal heavy ATK damage to all tiles in a chosen direction.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            // Derive cardinal push direction from the clicked tile relative to user.
            int dx = 0, dy = 0;
            if      (tx > user.x) dx =  1;
            else if (tx < user.x) dx = -1;
            else if (ty > user.y) dy =  1;
            else                  dy = -1;

            int rawDamage = (int) (user.getAtk() * 2.5);
            int curX = user.x + dx;
            int curY = user.y + dy;

            while (state.board.isValid(curX, curY)) {
                Character victim = state.board.getCharacterAt(curX, curY);
                if (victim != null && victim.team != user.team) {
                    if (!CombatUtils.rollDodge(victim, events, curX, curY)) {
                        int finalDamage = Math.max(0, rawDamage - victim.getArmor());
                        finalDamage = CombatUtils.applyCrit(user, finalDamage, events, curX, curY);
                        victim.health = Math.max(0, victim.health - finalDamage);
                        events.add(new EngineEvent.PopupEvent("ROCKSLIDE", finalDamage, "ATK", curX, curY));
                    }
                }
                curX += dx;
                curY += dy;
            }

            events.add(new EngineEvent.PopupEvent("ROCKSLIDE!", 0, "STATUS", user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }
}