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

public class Weirdguard extends Character {

    public Weirdguard(Texture portrait) {
        super("Weirdguard", portrait, Enums.CharClass.FIGHTER, Enums.CharType.FAUNA, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.COMMON;
        this.originLocation = "Weird City";
        this.baseMaxHealth = 80;
        this.baseAtk = 15;
        this.baseMag = 15;
        this.baseArmor = 7;
        this.baseCloak = 7;
        this.baseSpeed = 980;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 2;
        this.baseRange = 2;
        this.baseCritChance = 0.1;
        this.baseDodgeChance = 0.1;
        this.abilities[0] = new Ram();
        this.abilities[1] = new Smash();
        this.abilities[2] = new BullCharge();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class Ram extends Ability {
        public Ram() {
            super("Ram", "Basic attack. Deals ATK damage.", 1, true);
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
                events.add(new EngineEvent.PopupEvent("RAM", finalDamage, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getAtk(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class Smash extends Ability {
        public Smash() {
            super("Smash", "Deal MAG damage to all surrounding characters.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            for (int x = user.x - 1; x <= user.x + 1; x++) {
                for (int y = user.y - 1; y <= user.y + 1; y++) {
                    if (x == user.x && y == user.y) continue;
                    if (!state.board.isValid(x, y)) continue;
                    Character victim = state.board.getCharacterAt(x, y);
                    if (victim != null && victim.team != user.team) {
                        if (!CombatUtils.rollDodge(victim, events, x, y)) {
                            int finalMag = Math.max(0, user.getMag() - victim.getCloak());
                            finalMag = CombatUtils.applyCrit(user, finalMag, events, x, y);
                            victim.health = Math.max(0, victim.health - finalMag);
                            events.add(new EngineEvent.PopupEvent("SMASH", finalMag, "MAG", x, y));
                        }
                    } else if (victim == null) {
                        com.mygame.tactics.Tile t = state.board.getTile(x, y);
                        if (t != null && t.hasStructure()) {
                            state.engine.applyStructureDamageAtTile(
                                    state, x, y, user.getMag(), events);
                        }
                    }
                }
            }
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Bull Charge — click one of the 4 cardinal tiles to choose a direction.
     * Pushes every character in the 3-tile path one tile further, then moves
     * Weirdguard up to 3 tiles, stopping if blocked.
     * board.moveCharacter() handles void knockoffs and Gargoyle Unleashed.
     * Direction tiles are shown by GameEngine.calculateTargetRange().
     */
    public static class BullCharge extends Ability {
        public BullCharge() {
            super("Bull Charge", "Charge 3 tiles in a direction. Push all characters in the path.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            // Derive cardinal direction from the clicked tile relative to user.
            int dx = 0, dy = 0;
            if      (tx > user.x) dx =  1;
            else if (tx < user.x) dx = -1;
            else if (ty > user.y) dy =  1;
            else                  dy = -1;

            // Push every character in the 3-tile path before moving Weirdguard.
            // Iterate in reverse (step 3 → 1) so pushed units don't block each other.
            for (int step = 3; step >= 1; step--) {
                int checkX = user.x + (dx * step);
                int checkY = user.y + (dy * step);
                if (!state.board.isValid(checkX, checkY)) continue;

                Character inPath = state.board.getCharacterAt(checkX, checkY);
                if (inPath == null) continue;

                int pushX = checkX + dx, pushY = checkY + dy;
                Tile pushTile = state.board.isValid(pushX, pushY)
                        ? state.board.getTile(pushX, pushY) : null;
                boolean canPush = pushTile != null
                        && !pushTile.hasStructure()
                        && !pushTile.isCollapsed()
                        && state.board.getCharacterAt(pushX, pushY) == null;

                if (canPush) {
                    state.engine.processBoardEvents(
                            state.board.moveCharacter(inPath, pushX, pushY), state, events);
                    events.add(new EngineEvent.PopupEvent("PUSHED!", 0, "STATUS", inPath.x, inPath.y));
                } else {
                    // Blocked — deal impact damage instead
                    if (inPath.team != user.team) {
                        if (!CombatUtils.rollDodge(inPath, events, checkX, checkY)) {
                            int impactDamage = Math.max(0, user.getAtk() - inPath.getArmor());
                            impactDamage = CombatUtils.applyCrit(user, impactDamage, events, checkX, checkY);
                            inPath.health = Math.max(0, inPath.health - impactDamage);
                            events.add(new EngineEvent.PopupEvent("IMPACT!", impactDamage, "ATK", checkX, checkY));
                        }
                    }
                }
            }

            // Move Weirdguard up to 3 tiles, stopping if blocked.
            for (int step = 0; step < 3; step++) {
                int nextX = user.x + dx;
                int nextY = user.y + dy;
                if (!state.board.isValid(nextX, nextY)) break;
                Tile nextTile = state.board.getTile(nextX, nextY);
                if (nextTile == null || nextTile.hasStructure() || nextTile.isCollapsed()) break;
                if (state.board.getCharacterAt(nextX, nextY) != null) break;

                if (state.engine.processBoardEvents(
                        state.board.moveCharacter(user, nextX, nextY), state, events)) break;
            }

            events.add(new EngineEvent.PopupEvent("BULL CHARGE!", 0, "STATUS", user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }
}