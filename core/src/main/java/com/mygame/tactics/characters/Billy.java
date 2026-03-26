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

public class Billy extends Character {
    public Character disguisedAs       = null;
    public boolean isPoisonTrailActive = false; // true once triggered; lasts the whole game
    public int poisonMovesLeft         = 0;     // legacy field kept for compatibility
    public boolean isSnakeActive       = false; // true during the free invisible move from Snake In The Grass

    public Billy(Texture portrait) {
        super("Billy", portrait, Enums.CharClass.CHAOS, Enums.CharType.FAUNA, Enums.Alliance.WITCH);
        this.rarity = Enums.Rarity.MYSTIC;
        this.originLocation = "Weird City";
        this.baseMaxHealth = 120;
        this.baseAtk = 8;
        this.baseMag = 15;
        this.baseArmor = 2;
        this.baseCloak = 2;
        this.baseSpeed = 666;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 3;
        this.baseRange = 1;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.5;
        this.abilities[0] = new PoisonedFangs();
        this.abilities[1] = new SnakeInTheGrass();
        this.abilities[2] = new PoisonTrail();
        // Slot 3 is passive — mark it as used from the start so it's always greyed out
        this.ultUsed = true;
        startBattle();
    }

    @Override
    public void startBattle() {
        super.startBattle();
        // Poison Trail is inactive at game start — it triggers on first hit.
        this.isPoisonTrailActive = false;
    }

    /**
     * Reveals Billy's disguise and emits a popup event.
     */
    public void reveal(Array<EngineEvent> events) {
        if (this.disguisedAs != null) {
            this.disguisedAs = null;
            if (events != null) {
                events.add(new EngineEvent.PopupEvent("!! REVEALED !!", 0, "POOF", this.x, this.y));
            }
        }
    }


    // =========================================================
    // ABILITIES
    // =========================================================

    public static class PoisonedFangs extends Ability {
        public PoisonedFangs() {
            super("Poisoned Fangs",
                    "Deal (ATK - ARM) physical + (MAG - CLK) magic damage. " +
                    "3x both components if Invisible. Breaks stealth. (ATK: 5, MAG: 10)",
                    1, true);
            this.showAtk = true;
            this.showMag = true;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team != user.team) {
                if (CombatUtils.rollDodge(target, events, tx, ty)) return AbilityResult.END_TURN;

                boolean wasInvisible = user.isInvisible();
                int multiplier = wasInvisible ? 3 : 1;

                // Using Fangs breaks disguise and stealth
                if (user instanceof Billy) {
                    Billy b = (Billy) user;
                    b.reveal(events);
                    if (!b.isPoisonTrailActive) {
                        b.isPoisonTrailActive = true;
                        events.add(new EngineEvent.PopupEvent("POISON TRAIL!", 0, "POISON", b.x, b.y));
                    }
                    if (wasInvisible) {
                        user.setInvisible(false);
                        events.add(new EngineEvent.PopupEvent("REVEALED", 0, "STEALTH", user.x, user.y));
                    }
                }

                int rawPhys   = user.getAtk() * multiplier;
                int rawMag    = user.getMag()  * multiplier;
                int finalPhys = Math.max(0, rawPhys - target.getArmor());
                int finalMag  = Math.max(0, rawMag  - target.getCloak());
                int total     = finalPhys + finalMag;
                total = CombatUtils.applyCrit(user, total, events, tx, ty);

                events.add(new EngineEvent.PopupEvent("VENOM", total, "ATK", tx, ty));
                state.engine.applyDamage(state, target, 0, 0, total, events);
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                boolean wasInvisible = user.isInvisible();
                int multiplier = wasInvisible ? 3 : 1;
                if (user instanceof Billy) {
                    Billy b = (Billy) user;
                    b.reveal(events);
                    if (!b.isPoisonTrailActive) {
                        b.isPoisonTrailActive = true;
                        events.add(new EngineEvent.PopupEvent("POISON TRAIL!", 0, "POISON", b.x, b.y));
                    }
                    if (wasInvisible) {
                        user.setInvisible(false);
                        events.add(new EngineEvent.PopupEvent("REVEALED", 0, "STEALTH", user.x, user.y));
                    }
                }
                int total = (user.getAtk() + user.getMag()) * multiplier;
                state.engine.applyStructureDamageAtTile(state, tx, ty, total, events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class SnakeInTheGrass extends Ability {
        public SnakeInTheGrass() {
            super("Snake In The Grass",
                    "Turn invisible and move once. Stealth persists after the move.",
                    0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (user instanceof Billy) {
                ((Billy) user).reveal(events); // strip disguise if active
                ((Billy) user).isSnakeActive = true;
            }
            user.setInvisible(true);
            events.add(new EngineEvent.PopupEvent("SNAKE MODE", 0, "STEALTH", user.x, user.y));
            return AbilityResult.GRANT_MOVEMENT;
        }
    }

    /**
     * Poison Trail — passive ability, never clicked by the player.
     * Billy starts the game invisible with the trail active. It ends when he
     * uses Poisoned Fangs or takes damage.
     */
    public static class PoisonTrail extends Ability {
        public PoisonTrail() {
            super("Poison Trail",
                    "Triggers the first time Billy takes any damage. Every tile he " +
                    "moves over is poisoned for the rest of the game. Immune to poison while active.",
                    0, false);
            this.isPassive = true;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            // Purely passive — should never be called directly.
            return AbilityResult.END_TURN;
        }
    }
}