package com.mygame.tactics.characters;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import com.mygame.tactics.Ability;
import com.mygame.tactics.AbilityResult;
import com.mygame.tactics.Character;
import com.mygame.tactics.CombatUtils;
import com.mygame.tactics.EngineEvent;
import com.mygame.tactics.GameState;
import com.mygame.tactics.Enums;

public class Ben extends Character {

    public Ben(Texture portrait) {
        super("Ben", portrait, Enums.CharClass.SNIPER, Enums.CharType.FAUNA, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.RARE;
        this.originLocation = "Unknown";
        this.baseMaxHealth = 50;
        this.baseAtk = 15;
        this.baseMag = 0;
        this.baseArmor = 10;
        this.baseCloak = 0;
        this.baseSpeed = 930;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 1;
        this.baseRange = 2;
        this.baseCritChance = 0.3;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new ServiceWeapon();
        this.abilities[1] = new MassSurveillance();
        this.abilities[2] = new Lockdown();
        startBattle();
    }

    public static class ServiceWeapon extends Ability {
        public ServiceWeapon() {
            super("Service Weapon", "Basic attack. Deals ATK - ARM damage.", 2, true);
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
                events.add(new EngineEvent.PopupEvent("SERVICE WEAPON", finalDamage, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getAtk(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class MassSurveillance extends Ability {
        public MassSurveillance() {
            super("Mass Surveillance", "Reveal all invisible enemies.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            for (Character c : state.allUnits) {
                if (c.isDead() || c.team == user.team) continue;
                if (c.isInvisible()) {
                    c.setInvisible(false);
                    if (c instanceof Billy) ((Billy) c).disguisedAs = null;
                    events.add(new EngineEvent.PopupEvent("REVEALED", 0, "STATUS", c.x, c.y));
                }
            }
            events.add(new EngineEvent.PopupEvent("MASS SURVEILLANCE", 0, "STATUS",
                    user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }

    public static class Lockdown extends Ability {
        public Lockdown() {
            super("Lockdown",
                    "The Haven can no longer be moved for the rest of the game. Once per game.",
                    0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            // Handled by GameEngine.executeAbility() named special case.
            return AbilityResult.END_TURN;
        }
    }
}