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

public class GuardTower extends Character {

    public GuardTower(Texture portrait) {
        super("Guard Tower", portrait, Enums.CharClass.STATUE, Enums.CharType.WIND, Enums.Alliance.QUEEN);
        this.rarity = Enums.Rarity.COMMON;
        this.originLocation = "Queen Capital";
        this.baseMaxHealth = 20;
        this.baseAtk = 8;
        this.baseMag = 0;
        this.baseArmor = 15;
        this.baseCloak = 80;
        this.baseSpeed = 1200;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 0;
        this.baseRange = 4;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new Volley();
        this.abilities[1] = new WindPower();
        this.abilities[2] = new SummonTheWind();
        startBattle();
    }

    public static class Volley extends Ability {
        public Volley() {
            super("Volley", "Basic ranged attack. Deals ATK damage.", 3, true);
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
                events.add(new EngineEvent.PopupEvent("VOLLEY", finalDamage, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getAtk(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class WindPower extends Ability {
        public WindPower() {
            super("Wind Power", "Reduce all allies' current wait by 30%.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            for (Character ally : state.allUnits) {
                if (ally.isDead() || ally == user || ally.team != user.team) continue;
                ally.setCurrentWait(ally.getCurrentWait() * 0.70f);
                events.add(new EngineEvent.PopupEvent("WIND BOOST", 0, "BUFF", ally.x, ally.y));
            }
            events.add(new EngineEvent.PopupEvent("WIND POWER!", 0, "BUFF", user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Summon The Wind — click one of the 4 cardinal tiles adjacent to the tower
     * to choose a push direction. All enemies are pushed 2 tiles in that direction.
     * Direction tiles are shown by GameEngine.calculateTargetRange().
     * board.moveCharacter() handles void knockoffs and Gargoyle Unleashed checks.
     */
    public static class SummonTheWind extends Ability {
        public SummonTheWind() {
            super("Summon The Wind", "Push all enemies 2 tiles in a chosen direction.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            int dx = 0, dy = 0;
            if      (tx > user.x) dx =  1;
            else if (tx < user.x) dx = -1;
            else if (ty > user.y) dy =  1;
            else                  dy = -1;

            Array<Character> enemies = new Array<>();
            for (Character unit : state.allUnits) {
                if (!unit.isDead() && unit.team != user.team) enemies.add(unit);
            }

            for (Character enemy : enemies) {
                for (int step = 0; step < 2; step++) {
                    int nextX = enemy.x + dx;
                    int nextY = enemy.y + dy;

                    if (state.board.isValid(nextX, nextY)) {
                        Tile t = state.board.getTile(nextX, nextY);
                        if (t != null && t.hasStructure()) break;
                        if (state.board.getCharacterAt(nextX, nextY) != null) break;
                    }

                    boolean killed = state.engine.processBoardEvents(
                            state.board.moveCharacter(enemy, nextX, nextY), state, events);
                    if (killed) break;
                    if (state.isGameOver()) return AbilityResult.END_TURN;
                }
                events.add(new EngineEvent.PopupEvent("PUSHED!", 0, "STATUS", enemy.x, enemy.y));
            }

            events.add(new EngineEvent.PopupEvent("SUMMON THE WIND!", 0, "STATUS", user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }
}