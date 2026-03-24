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

public class Jaxon extends Character {

    public boolean grandEntrancePending = true;

    public Jaxon(Texture portrait) {
        super("Jaxon", portrait, Enums.CharClass.FIGHTER, Enums.CharType.WIND, Enums.Alliance.QUEEN);
        this.rarity = Enums.Rarity.MYSTIC;
        this.originLocation = "Capital";
        this.baseMaxHealth = 66;
        this.baseAtk = 16;
        this.baseMag = 0;
        this.baseArmor = 10;
        this.baseCloak = 10;
        this.baseSpeed = 720;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 2;
        this.baseRange = 2;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new Smash();
        this.abilities[1] = new Block();
        this.abilities[2] = new GrandEntrance();
        startBattle();
    }

    @Override
    public void startBattle() {
        super.startBattle();
        // Grand Entrance: Jaxon always acts first at the start of the game.
        if (grandEntrancePending) this.currentWait = 0;
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class Smash extends Ability {
        public Smash() {
            super("Smash", "Basic attack. Deals ATK damage.", 1, true);
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
                events.add(new EngineEvent.PopupEvent("SMASH", finalPhys, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getAtk(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class Block extends Ability {
        public Block() {
            super("Block", "Gain +2 Armor and +2 Cloak.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            user.armor += 2;
            user.cloak += 2;
            events.add(new EngineEvent.PopupEvent("+2 ARM / +2 CLK", 0, "BUFF", user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Grand Entrance fires automatically on Jaxon's first turn via GameEngine.startTurn().
     * Jaxon always acts first (currentWait = 0 at battle start).
     * He gets two movement phases before using an ability this turn.
     * The ult slot is locked after it fires so it can never be re-cast.
     * execute() emits the popup; GameEngine owns the movement-phase logic via grandEntranceMovesLeft.
     */
    public static class GrandEntrance extends Ability {
        public GrandEntrance() {
            super("Grand Entrance", "Always goes first. Move twice before attacking on the opening turn.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            events.add(new EngineEvent.PopupEvent("GRAND ENTRANCE!", 0, "MOVE", user.x, user.y));
            return AbilityResult.GRANT_MOVEMENT;
        }
    }
}