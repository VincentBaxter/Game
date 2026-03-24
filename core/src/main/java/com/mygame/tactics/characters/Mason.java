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

public class Mason extends Character {

    public boolean hasDeployed       = false;
    public boolean isTakeFlightActive = false;
    public boolean gargoyleTriggered  = false; // true once Gargoyle Unleashed has fired

    public Mason(Texture portrait) {
        super("Mason", portrait, Enums.CharClass.STATUE, Enums.CharType.WIND, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.CHAMPION;
        this.originLocation = "Director's Castle";
        this.baseMaxHealth = 44;
        this.baseAtk = 7;
        this.baseMag = 0;
        this.baseArmor = 10;
        this.baseCloak = 10;
        this.baseSpeed = 500;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 0;
        this.baseRange = 1;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new Claws();
        this.abilities[1] = new TakeFlight();
        this.abilities[2] = new GargoyleUnleashed();

        // Gargoyle Unleashed is passive — lock slot 2 from the start.
        this.setUltActive(true);
        this.setInvisible(true);

        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class Claws extends Ability {
        public Claws() {
            super("Claws", "Basic physical strike. (ATK: 7)", 1, true);
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
                events.add(new EngineEvent.PopupEvent("CLAW", finalPhys, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getAtk(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Take Flight — teleports Mason to any unoccupied, structure-free tile
     * within 3 Manhattan distance. Handled as a targetted ability in GameEngine.
     */
    public static class TakeFlight extends Ability {
        public TakeFlight() {
            super("Take Flight", "Teleport to any empty tile within 3 range.", 3, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            // Teleport is handled by GameEngine after this returns.
            events.add(new EngineEvent.PopupEvent("TAKE FLIGHT", 0, "MOVE", tx, ty));
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Gargoyle Unleashed — passive, once per game.
     * While Mason is invisible, any enemy that moves adjacent to him is
     * instantly killed. Mason is revealed. Handled entirely in GameEngine.
     */
    public static class GargoyleUnleashed extends Ability {
        public GargoyleUnleashed() {
            super("Gargoyle Unleashed",
                    "PASSIVE: While invisible, instantly kill the first enemy that moves " +
                    "adjacent to you. Revealed on trigger. Once per game.",
                    0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            // Purely passive — never called directly.
            return AbilityResult.END_TURN;
        }
    }
}