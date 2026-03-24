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

public class Ghia extends Character {

    /** Tracks the board positions of Ghia's clothes piles for Lick Wounds targeting. */
    public Array<Vector2> clothesTiles = new Array<>();

    public Ghia(Texture portrait) {
        super("Ghia", portrait, Enums.CharClass.ASSASSIN, Enums.CharType.FAUNA, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.MYSTIC;
        this.originLocation = "Unknown";
        this.baseMaxHealth = 20;
        this.baseAtk = 10;
        this.baseMag = 0;
        this.baseArmor = 0;
        this.baseCloak = 0;
        this.baseSpeed = 600;
        this.baseSpeedReduction = 1.1;
        this.baseMoveDist = 3;
        this.baseRange = 1;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.7;
        this.abilities[0] = new Scratch();
        this.abilities[1] = new LickWounds();
        this.abilities[2] = new ScaredyCat();
        // Scaredy Cat is passive (fires at battle start) — lock slot 2 permanently.
        this.ultUsed = true;
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class Scratch extends Ability {
        public Scratch() {
            super("Scratch", "Basic attack. Deals ATK - ARM damage.", 1, true);
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
                events.add(new EngineEvent.PopupEvent("SCRATCH", finalDamage, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getAtk(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Lick Wounds — player clicks a surviving clothes tile. Ghia teleports
     * there and heals 10 HP. Handled as a named special case in GameEngine.
     */
    public static class LickWounds extends Ability {
        public LickWounds() {
            super("Lick Wounds",
                    "Teleport to a pile of clothes and heal 10 HP.",
                    0, false);
            this.isHeal = true;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            // Teleport and heal handled by GameEngine.executeAbility().
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Scaredy Cat — passive. At battle start, GameEngine spawns 3 clothes piles
     * randomly on the board. Ghia becomes invisible while standing on one.
     * Slot 2 is permanently locked.
     */
    public static class ScaredyCat extends Ability {
        public ScaredyCat() {
            super("Scaredy Cat",
                    "PASSIVE: At the start of the game, spawn 3 piles of clothes anywhere " +
                    "on the board. Ghia becomes invisible while standing on a pile.",
                    0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            // Purely passive — spawned by GameEngine.finalizeBattleStart().
            return AbilityResult.END_TURN;
        }
    }
}