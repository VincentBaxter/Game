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

public class Aevan extends Character {

    public Aevan(Texture portrait) {
        super("Aevan", portrait, Enums.CharClass.SNIPER, Enums.CharType.FAUNA, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.SPECIAL;
        this.originLocation = "Weird City";
        this.baseMaxHealth = 80;
        this.baseAtk = 0;
        this.baseMag = 23;
        this.baseArmor = 5;
        this.baseCloak = 5;
        this.baseSpeed = 898;
        this.baseSpeedReduction = 0.9;
        this.baseMoveDist = 2;
        this.baseRange = 4;
        this.baseCritChance = 0.1;
        this.baseDodgeChance = 0.1;
        this.abilities[0] = new Disc();
        this.abilities[1] = new Par3();
        this.abilities[2] = new HoleInOne();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class Disc extends Ability {
        public Disc() {
            super("Disc", "Basic magic attack. Deals MAG - CLK damage. (MAG: 15)", 4, true);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team != user.team) {
                if (CombatUtils.rollDodge(target, events, tx, ty)) return AbilityResult.END_TURN;
                int finalMag = Math.max(0, user.getMag() - target.getCloak());
                finalMag = CombatUtils.applyCrit(user, finalMag, events, tx, ty);
                events.add(new EngineEvent.PopupEvent("DISC", finalMag, "MAG", tx, ty));
                state.engine.applyDamage(state, target, 0, 0, finalMag, events);
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getMag(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Par 3 — pulls the Haven one tile toward Aevan.
     * Haven movement is handled by GameEngine.executeAbility() after this returns.
     */
    public static class Par3 extends Ability {
        public Par3() {
            super("Par 3", "Pull the Haven one tile closer to you.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            // Haven pull is handled by GameEngine.executeAbility() named case.
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Hole in One — reduces a target enemy's health to 1. Once per game.
     */
    public static class HoleInOne extends Ability {
        public HoleInOne() {
            super("Hole in One",
                    "Reduce an enemy's health to 1. Once per game.",
                    4, true);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team != user.team) {
                int dmg = target.health - 1;
                if (dmg > 0) {
                    events.add(new EngineEvent.PopupEvent("HOLE IN ONE", dmg, "TRUE", tx, ty));
                    state.engine.applyDamage(state, target, 0, 0, dmg, events);
                }
            }
            return AbilityResult.END_TURN;
        }
    }
}
