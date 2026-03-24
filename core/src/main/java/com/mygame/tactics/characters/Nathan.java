package com.mygame.tactics.characters;

import java.util.Random;

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

public class Nathan extends Character {

    public int slainAllies = 0;
    public boolean speedHalved = false;

    public Nathan(Texture portrait) {
        super("Nathan", portrait, Enums.CharClass.COLLECTOR, Enums.CharType.FLORA, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.MYSTIC;
        this.originLocation = "Swamp";
        this.baseMaxHealth = 35;
        this.baseAtk = 0;
        this.baseMag = 14;
        this.baseArmor = 7;
        this.baseCloak = 7;
        this.baseSpeed = 1000;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 2;
        this.baseRange = 2;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.1;
        this.abilities[0] = new KissOfLove();
        this.abilities[1] = new ThornedRoots();
        this.abilities[2] = new Clutch();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class KissOfLove extends Ability {
        public KissOfLove() {
            super("Kiss of Love", "Deal MAG magic damage to a target.", 2, true);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team != user.team) {
                if (CombatUtils.rollDodge(target, events, tx, ty)) return AbilityResult.END_TURN;
                int finalMag = Math.max(0, user.getMag() - target.getCloak());
                finalMag = CombatUtils.applyCrit(user, finalMag, events, tx, ty);
                target.health = Math.max(0, target.health - finalMag);
                events.add(new EngineEvent.PopupEvent("KISS", finalMag, "MAG", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getMag(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class ThornedRoots extends Ability {
        public ThornedRoots() {
            super("Thorned Roots", "Click diagonal: root 2x2 in that corner. " +
                    "Click cardinal: root all 8 tiles around you.", 1, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            int ux = user.x, uy = user.y;
            int dx = tx - ux, dy = ty - uy;
            boolean diagonal = (dx != 0 && dy != 0);

            if (diagonal) {
                int[][] targets = {
                    {ux + dx, uy + dy},
                    {ux + dx, uy},
                    {ux,      uy + dy}
                };
                for (int[] t : targets) rootTile(t[0], t[1], state, events);
                events.add(new EngineEvent.PopupEvent("ROOTS", 0, "ROOT", ux, uy));
            } else {
                for (int ox = -1; ox <= 1; ox++) {
                    for (int oy = -1; oy <= 1; oy++) {
                        if (ox == 0 && oy == 0) continue;
                        rootTile(ux + ox, uy + oy, state, events);
                    }
                }
                events.add(new EngineEvent.PopupEvent("THORNED ROOTS", 0, "ROOT", ux, uy));
            }
            return AbilityResult.END_TURN;
        }

        private void rootTile(int x, int y, GameState state, Array<EngineEvent> events) {
            if (!state.board.isValid(x, y)) return;
            Tile t = state.board.getTile(x, y);
            if (t != null && !t.isCollapsed()) {
                t.setThorn(true);
                t.setStructureHP(10);
            }
            Character occupant = state.board.getCharacterAt(x, y);
            if (occupant != null) {
                occupant.isRooted = true;
                events.add(new EngineEvent.PopupEvent("ROOTED", 0, "ROOT", x, y));
            }
        }
    }

    public static class Clutch extends Ability {
        public Clutch() {
            super("Clutch", "Move Haven here. Spawn thorn wall (length = slain allies). " +
                    "If last alive: +1 wall and speed halved permanently.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            Nathan nathan = (Nathan) user;

            // 1. Move the Haven to Nathan's tile via event so GameEngine
            //    can strip the previous occupant's bonus through moveHaven().
            events.add(new EngineEvent.HavenMoveEvent(user.x, user.y));
            events.add(new EngineEvent.PopupEvent("HAVEN CLAIMED", 0, "BUFF", user.x, user.y));

            // 2. Spawn the first thorn wall.
            int wallLength = Math.max(1, nathan.slainAllies);
            spawnRandomThornWall(wallLength, state, events, user.x, user.y);
            events.add(new EngineEvent.PopupEvent("WALL L=" + wallLength, 0, "THORN", user.x, user.y));

            // 3. Check if Nathan is the last alive on his team.
            boolean lastAlive = true;
            for (int r = 0; r < state.board.getRows(); r++) {
                for (int c = 0; c < state.board.getCols(); c++) {
                    Character ally = state.board.getCharacterAt(r, c);
                    if (ally != null && ally != user && ally.team == user.team && !ally.isDead()) {
                        lastAlive = false;
                        break;
                    }
                }
                if (!lastAlive) break;
            }

            if (lastAlive) {
                spawnRandomThornWall(wallLength, state, events, user.x, user.y);
                events.add(new EngineEvent.PopupEvent("SECOND WALL!", 0, "THORN", user.x, user.y));

                if (!nathan.speedHalved) {
                    nathan.speedHalved = true;
                    nathan.speed = Math.max(1, nathan.speed / 2);
                    nathan.baseSpeed = Math.max(1, nathan.baseSpeed / 2);
                    events.add(new EngineEvent.PopupEvent("SPEED HALVED", 0, "DEBUFF", user.x, user.y));
                }
            }

            return AbilityResult.END_TURN;
        }

        private void spawnRandomThornWall(int length, GameState state,
                                           Array<EngineEvent> events, int nathanX, int nathanY) {
            Random rng = new Random();
            int rows = state.board.getRows();
            int cols = state.board.getCols();

            for (int attempt = 0; attempt < 20; attempt++) {
                boolean horizontal = rng.nextBoolean();
                int ax = horizontal ? rng.nextInt(Math.max(1, cols - length)) : rng.nextInt(cols);
                int ay = horizontal ? rng.nextInt(rows) : rng.nextInt(Math.max(1, rows - length));

                boolean valid = true;
                for (int i = 0; i < length; i++) {
                    int wx = horizontal ? ax + i : ax;
                    int wy = horizontal ? ay     : ay + i;
                    if (!state.board.isValid(wx, wy)) { valid = false; break; }
                    Tile t = state.board.getTile(wx, wy);
                    if (t == null || t.isCollapsed()) { valid = false; break; }
                    if (state.board.getCharacterAt(wx, wy) != null) { valid = false; break; }
                }

                if (valid) {
                    for (int i = 0; i < length; i++) {
                        int wx = horizontal ? ax + i : ax;
                        int wy = horizontal ? ay     : ay + i;
                        state.board.getTile(wx, wy).setThorn(true);
                        events.add(new EngineEvent.TileEffectEvent(
                                EngineEvent.TileEffectEvent.Effect.WALL_PLACED, wx, wy));
                        events.add(new EngineEvent.PopupEvent("THORN", 0, "WALL", wx, wy));
                    }
                    return;
                }
            }
        }
    }
}