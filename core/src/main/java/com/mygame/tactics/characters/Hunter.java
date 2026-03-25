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

public class Hunter extends Character {

    public boolean isDaggersCullActive = false;
    public int cullDamageMultiplier = 1; // Doubles per kill while Cull is active; resets at end of turn

    public Hunter(Texture portrait) {
        super("Hunter", portrait, Enums.CharClass.ASSASSIN, Enums.CharType.NIGHTMARE, Enums.Alliance.WITCH);
        this.rarity = Enums.Rarity.MYSTIC;
        this.originLocation = "Ice Town";
        this.baseMaxHealth = 30;
        this.baseAtk = 30;
        this.baseMag = 0;
        this.baseArmor = 0;
        this.baseCloak = 0;
        this.baseSpeed = 980;
        this.baseSpeedReduction = 0.9;
        this.baseMoveDist = 1;
        this.baseRange = 1;
        this.baseCritChance = 0.5;
        this.baseDodgeChance = 0.1;
        this.abilities[0] = new Shiv();
        this.abilities[1] = new Vanish();
        this.abilities[2] = new DaggersCull();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class Shiv extends Ability {
        public Shiv() {
            super("Shiv", "Deal ATK - ARM physical damage. (ATK: 20)", 1, true);
            this.showAtk = true;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team != user.team) {
                if (CombatUtils.rollDodge(target, events, tx, ty)) return AbilityResult.END_TURN;

                // Apply cull damage multiplier if Dagger's Cull is active
                int multiplier = (user instanceof Hunter) ? ((Hunter) user).cullDamageMultiplier : 1;
                int rawPhys = user.getAtk() * multiplier;

                int finalDamage = Math.max(0, rawPhys - target.getArmor());
                finalDamage = CombatUtils.applyCrit(user, finalDamage, events, tx, ty);
                target.health = Math.max(0, target.health - finalDamage);
                events.add(new EngineEvent.PopupEvent("SHIV", finalDamage, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                int multiplier = (user instanceof Hunter) ? ((Hunter) user).cullDamageMultiplier : 1;
                int rawPhys = user.getAtk() * multiplier;
                state.engine.applyStructureDamageAtTile(state, tx, ty, rawPhys, events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class Vanish extends Ability {
        public Vanish() {
            super("Vanish", "Turn invisible, then move. Attacking makes you visible again.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (user.isInvisible()) {
                events.add(new EngineEvent.PopupEvent("ALREADY HIDDEN", 0, "STATUS", user.x, user.y));
                return AbilityResult.END_TURN;
            }

            // Mark invisible BEFORE movement so opponent cannot see where Hunter moves
            user.setInvisible(true);
            events.add(new EngineEvent.PopupEvent("VANISH", 0, "STEALTH", user.x, user.y));
            return AbilityResult.GRANT_MOVEMENT;
        }
    }

    public static class DaggersCull extends Ability {
        public DaggersCull() {
            super("Dagger's Cull",
                    "Toggle: while active, Shiv kills grant a free move + attack. " +
                    "Damage doubles per kill this turn (1x → 2x → 4x...). " +
                    "Deactivates at end of turn. Once per game.",
                    0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (!(user instanceof Hunter)) return AbilityResult.END_TURN;
            Hunter hunter = (Hunter) user;

            hunter.isDaggersCullActive = true;
            hunter.cullDamageMultiplier = 1;
            hunter.setUltActive(true); // locks slot 3 for the rest of the match
            events.add(new EngineEvent.PopupEvent("CULL ACTIVE", 0, "ACTIVE", user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }
}