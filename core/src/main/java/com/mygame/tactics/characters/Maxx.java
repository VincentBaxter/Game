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

public class Maxx extends Character {

    public boolean zombieTriggered = false; // true once Not Even Close has fired

    public Maxx(Texture portrait) {
        super("Maxx", portrait, Enums.CharClass.FIGHTER, Enums.CharType.NIGHTMARE, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.RARE;
        this.originLocation = "Unknown";
        this.baseMaxHealth = 90;
        this.baseAtk = 18;
        this.baseMag = 0;
        this.baseArmor = 7;
        this.baseCloak = 7;
        this.baseSpeed = 686;
        this.baseSpeedReduction = 1.1;
        this.baseMoveDist = 2;
        this.baseRange = 1;
        this.baseCritChance = 0.1;
        this.baseDodgeChance = 0.1;
        this.abilities[0] = new Trasher();
        this.abilities[1] = new OhhWaWaWa();
        this.abilities[2] = new NotEvenClose();
        // Passive — lock slot 2 from the start
        this.setUltActive(true);
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class Trasher extends Ability {
        public Trasher() {
            super("Trasher", "Basic attack. Deals ATK - ARM damage.", 1, true);
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
                events.add(new EngineEvent.PopupEvent("TRASHER", finalDamage, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getAtk(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class OhhWaWaWa extends Ability {
        public OhhWaWaWa() {
            super("Ohh Wa Wa Wa", "Reduce all enemies' ARM and CLK by 1.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            for (Character unit : state.allUnits) {
                if (unit.isDead() || unit.team == user.team) continue;
                unit.armor = Math.max(0, unit.armor - 1);
                unit.cloak = Math.max(0, unit.cloak - 1);
                events.add(new EngineEvent.PopupEvent("-1 ARM / CLK", 0, "DEBUFF", unit.x, unit.y));
            }
            events.add(new EngineEvent.PopupEvent("OHH WA WA WA!", 0, "STATUS", user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Not Even Close — passive, once per game.
     * When Maxx would die, he instead revives at 50% maxHealth in zombie form:
     *   - Portrait swaps to Maxx_Zombie.png
     *   - moveDist reduced by 1
     *   - baseSpeed halved (acts twice as often)
     *   - currentWait reset to new baseSpeed
     * Handled entirely in GameEngine.handleDeath().
     */
    public static class NotEvenClose extends Ability {
        public NotEvenClose() {
            super("Not Even Close",
                    "On death, revive as a zombie with 50% HP, -1 move, and 2x speed. Once per game.",
                    0, false);
            this.isPassive = true;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            // Purely passive — triggered by GameEngine.handleDeath().
            return AbilityResult.END_TURN;
        }
    }
}