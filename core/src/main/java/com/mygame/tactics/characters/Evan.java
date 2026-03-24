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
import com.mygame.tactics.Tile;

public class Evan extends Character {

    public int currentPushDistance = 0;

    public Evan(Texture portrait) {
        super("Evan", portrait, Enums.CharClass.FIGHTER, Enums.CharType.AQUA, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.MYSTIC;
        this.originLocation = "Trade Hub";
        this.baseMaxHealth = 50;
        this.baseAtk = 12;
        this.baseMag = 12;
        this.baseArmor = 8;
        this.baseCloak = 5;
        this.baseSpeed = 750;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 3;
        this.baseRange = 1;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.1;
        this.abilities[0] = new TridentRush();
        this.abilities[1] = new Marathon();
        this.abilities[2] = new Tsunami();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class TridentRush extends Ability {
        public TridentRush() { super("Trident Rush", "Basic physical strike.", 1, true); }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team != user.team) {
                if (CombatUtils.rollDodge(target, events, tx, ty)) return AbilityResult.END_TURN;
                int finalPhys = Math.max(0, user.getAtk() - target.getArmor());
                finalPhys = CombatUtils.applyCrit(user, finalPhys, events, tx, ty);
                target.health = Math.max(0, target.health - finalPhys);
                events.add(new EngineEvent.PopupEvent("RUSH", finalPhys, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getAtk(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class Marathon extends Ability {
        public Marathon() {
            super("Marathon", "Move again immediately.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (user instanceof Evan) ((Evan) user).currentPushDistance += 1;
            user.setCurrentWait(0);
            events.add(new EngineEvent.PopupEvent("SPRINT", 0, "FREE", user.x, user.y));
            return AbilityResult.GRANT_MOVEMENT;
        }
    }

    public static class Tsunami extends Ability {
        public Tsunami() {
            super("Tsunami", "Push enemies 1 tile in chosen direction.", 1, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            int dx   = Integer.compare(tx, user.x);
            int dy   = Integer.compare(ty, user.y);
            int dist = ((Evan) user).currentPushDistance;

            for (int r = 0; r < state.board.getRows(); r++) {
                for (int c = 0; c < state.board.getCols(); c++) {
                    Character victim = state.board.getCharacterAt(r, c);
                    if (victim != null && victim.team != user.team) {
                        processMultiPush(victim, dx, dy, dist, state, events);
                    }
                }
            }
            return AbilityResult.END_TURN;
        }

        private void processMultiPush(Character victim, int dx, int dy, int maxDist,
                                      GameState state, Array<EngineEvent> events) {
            if (victim.getCharClass() == Enums.CharClass.STATUE) {
                events.add(new EngineEvent.PopupEvent("IMMOBILE", 0, "STATUS", victim.x, victim.y));
                return;
            }

            for (int i = 0; i < maxDist; i++) {
                int nextX = victim.x + dx;
                int nextY = victim.y + dy;

                // Check for wall before moving — walls block the push and take damage.
                if (state.board.isValid(nextX, nextY)) {
                    Tile nextTile = state.board.getTile(nextX, nextY);
                    if (nextTile != null && nextTile.hasStructure()) {
                        nextTile.applyStructureDamage(30);
                        events.add(new EngineEvent.PopupEvent("CRASH!", 30, "OBJ", nextX, nextY));
                        if (nextTile.hasStructure()) break; // Wall survived — stop push.
                        // Wall destroyed — fall through and move onto the now-clear tile.
                    }

                    // Check for occupant — STATUEs block and deal damage, others block.
                    Character occupant = state.board.getCharacterAt(nextX, nextY);
                    if (occupant != null) {
                        if (occupant.getCharClass() == Enums.CharClass.STATUE) {
                            victim.health = Math.max(0, victim.health - 5);
                            events.add(new EngineEvent.PopupEvent("BLOCKED", 5, "ATK", nextX, nextY));
                        }
                        break;
                    }
                }

                // Delegate the actual move to board.moveCharacter().
                // processBoardEvents resolves void kills and Gargoyle kills immediately.
                boolean killed = state.engine.processBoardEvents(
                        state.board.moveCharacter(victim, nextX, nextY), state, events);
                if (killed) break;
            }
        }
    }
}