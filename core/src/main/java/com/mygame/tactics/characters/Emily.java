package com.mygame.tactics.characters;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.mygame.tactics.Ability;
import com.mygame.tactics.AbilityResult;
import com.mygame.tactics.Character;
import com.mygame.tactics.EngineEvent;
import com.mygame.tactics.Enums;
import com.mygame.tactics.GameState;

public class Emily extends Character {

    public Emily(Texture portrait) {
        super("Emily", portrait, Enums.CharClass.SUPPORT, Enums.CharType.ANGELIC, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.SPECIAL;
        this.originLocation = "Unknown";
        this.baseMaxHealth = 33;
        this.baseAtk = 0;
        this.baseMag = 13;
        this.baseArmor = 3;
        this.baseCloak = 3;
        this.baseSpeed = 733;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 2;
        this.baseRange = 1;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new CouriersGrace();
        this.abilities[1] = new Tumble();
        this.abilities[2] = new Embrace();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class CouriersGrace extends Ability {
        public CouriersGrace() {
            super("Courier's Grace", "Heal a single ally within 1 range for MAG HP.", 1, true);
            this.isHeal = true;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team == user.team && !target.isDead()) {
                int actualHeal = Math.min(user.getMag(), target.maxHealth - target.health);
                target.health += actualHeal;
                events.add(new EngineEvent.PopupEvent("GRACE", actualHeal, "HEAL", tx, ty));
            }
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Tumble — pushes the Haven one tile directly away from Emily.
     * Haven movement is handled by GameEngine.executeAbility() via the
     * named "Tumble" special case, mirroring Speen's Pass.
     */
    public static class Tumble extends Ability {
        public Tumble() {
            super("Tumble", "Push the Haven 1 tile directly away from you.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            // Haven push is handled by GameEngine.executeAbility().
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Embrace — pulls the Haven one tile directly toward Emily.
     * Haven movement is handled by GameEngine.executeAbility() via the
     * named "Embrace" special case.
     */
    public static class Embrace extends Ability {
        public Embrace() {
            super("Embrace", "Pull the Haven 1 tile directly toward you. Once per game.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            // Haven pull is handled by GameEngine.executeAbility().
            return AbilityResult.END_TURN;
        }
    }
}