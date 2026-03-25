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

public class Tyler extends Character {

    public Tyler(Texture portrait) {
        super("Tyler", portrait, Enums.CharClass.FIGHTER, Enums.CharType.ANGELIC, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.RARE;
        this.originLocation = "Unknown";
        this.baseMaxHealth = 60;
        this.baseAtk = 15;
        this.baseMag = 15;
        this.baseArmor = 7;
        this.baseCloak = 7;
        this.baseSpeed = 888;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 1;
        this.baseRange = 2;
        this.baseCritChance = 0.1;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new Fighter();
        this.abilities[1] = new Lover();
        this.abilities[2] = new Supercharged();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class Fighter extends Ability {
        public Fighter() {
            super("Fighter", "Basic attack. Deals ATK - ARM damage.", 2, true);
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
                events.add(new EngineEvent.PopupEvent("FIGHTER", finalDamage, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getAtk(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class Lover extends Ability {
        public Lover() {
            super("Lover", "Heal an ally for MAG HP.", 2, true);
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
                events.add(new EngineEvent.PopupEvent("HEAL", actualHeal, "HEAL", tx, ty));
            }
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Supercharged — doubles ATK, MAG, ARM, CLK, and maxHealth.
     * Also heals Tyler up to the new maxHealth cap.
     * Speed is intentionally unaffected. Once per game.
     */
    public static class Supercharged extends Ability {
        public Supercharged() {
            super("Supercharged",
                    "Double ATK, MAG, ARM, CLK, and max HP. Once per game.",
                    0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            user.atk        *= 2;
            user.mag        *= 2;
            user.armor      *= 2;
            user.cloak      *= 2;
            user.maxHealth  *= 2;
            user.baseMaxHealth *= 2;
            // Heal up to the new cap so the boost feels meaningful
            user.health = Math.min(user.health * 2, user.maxHealth);
            events.add(new EngineEvent.PopupEvent("SUPERCHARGED!", 0, "BUFF", user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }
}