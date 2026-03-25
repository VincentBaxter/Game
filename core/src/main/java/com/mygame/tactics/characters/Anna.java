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

public class Anna extends Character {

    public Anna(Texture portrait) {
        super("Anna", portrait, Enums.CharClass.FIGHTER, Enums.CharType.ANGELIC, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.COMMON;
        this.originLocation = "Unknown";
        this.baseMaxHealth = 80;
        this.baseAtk = 23;
        this.baseMag = 0;
        this.baseArmor = 0;
        this.baseCloak = 1;
        this.baseSpeed = 815;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 2;
        this.baseRange = 1;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new Punch();
        this.abilities[1] = new Muster();
        this.abilities[2] = new FullStrength();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class Punch extends Ability {
        public Punch() {
            super("Punch", "Basic attack. Deals ATK - ARM damage.", 1, true);
            this.showAtk = true;
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
                events.add(new EngineEvent.PopupEvent("PUNCH", finalDamage, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getAtk(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class Muster extends Ability {
        public Muster() {
            super("Muster", "Gain +5 ATK.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            user.atk += 5;
            events.add(new EngineEvent.PopupEvent("+5 ATK", 0, "BUFF", user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Full Strength — deals 3x ATK damage to a single enemy within range 1.
     * Respects dodge and crit. Once per game.
     */
    public static class FullStrength extends Ability {
        public FullStrength() {
            super("Full Strength",
                    "Deal 3x ATK damage to an enemy within 1 range. Once per game.",
                    1, true);
            this.showAtk = true;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team != user.team) {
                if (CombatUtils.rollDodge(target, events, tx, ty)) return AbilityResult.END_TURN;
                int finalDamage = Math.max(0, (user.getAtk() * 3) - target.getArmor());
                finalDamage = CombatUtils.applyCrit(user, finalDamage, events, tx, ty);
                target.health = Math.max(0, target.health - finalDamage);
                events.add(new EngineEvent.PopupEvent("FULL STRENGTH", finalDamage, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getAtk() * 3, events);
            }
            return AbilityResult.END_TURN;
        }
    }
}