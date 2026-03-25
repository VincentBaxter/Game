package com.mygame.tactics.characters;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.mygame.tactics.Ability;
import com.mygame.tactics.AbilityResult;
import com.mygame.tactics.Character;
import com.mygame.tactics.EngineEvent;
import com.mygame.tactics.Enums;
import com.mygame.tactics.GameState;

public class Speen extends Character {

    public Speen(Texture portrait) {
        super("Speen", portrait, Enums.CharClass.CHAOS, Enums.CharType.AQUA, Enums.Alliance.QUEEN);
        this.rarity = Enums.Rarity.CHAMPION;
        this.originLocation = "Ice Town";
        this.baseMaxHealth = 46;
        this.baseAtk = 15;
        this.baseMag = 0;
        this.baseArmor = 12;
        this.baseCloak = 12;
        this.baseSpeed = 600;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 3;
        this.baseRange = 1;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new Pads();
        this.abilities[1] = new Pass();
        this.abilities[2] = new AwayGame();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class Pads extends Ability {
        public Pads() {
            super("Pads", "Gain +1 Armor and +1 Cloak. Stacks on every use.", 0, false);
            this.armorBuff = 1;
            this.cloakBuff = 1;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            user.armor++;
            user.cloak++;
            events.add(new EngineEvent.PopupEvent("+1 ARM / +1 CLK", 0, "BUFF", user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Pass — pushes the Haven one tile directly away from Speen.
     * The push direction and Haven movement are handled by GameEngine.executeAbility(),
     * which calls pushHaven() after this returns. execute() just signals completion.
     */
    public static class Pass extends Ability {
        public Pass() {
            super("Pass", "Push the Haven one tile directly away from you.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            // Haven push is handled by GameEngine.executeAbility() after this returns.
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Away Game — teleports Speen to any non-collapsed tile on the board.
     * GameEngine.executeAbility() handles the removeCharacter / addCharacter
     * for a clean teleport and marks the ult as used.
     */
    public static class AwayGame extends Ability {
        public AwayGame() {
            super("Away Game", "Teleport to any tile on the board.", 9, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            // Teleport is handled by GameEngine.executeAbility() after this returns.
            return AbilityResult.END_TURN;
        }
    }
}