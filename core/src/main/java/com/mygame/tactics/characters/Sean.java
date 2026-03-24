package com.mygame.tactics.characters;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.mygame.tactics.Ability;
import com.mygame.tactics.AbilityResult;
import com.mygame.tactics.Character;
import com.mygame.tactics.CombatUtils;
import com.mygame.tactics.EngineEvent;
import com.mygame.tactics.Enums;
import com.mygame.tactics.GameState;
import com.mygame.tactics.Tile;

public class Sean extends Character {

    public Vector2 wallAnchor    = null;
    public Vector2 wallDirection = null;

    public Sean(Texture portrait) {
        super("Sean", portrait, Enums.CharClass.MAGE, Enums.CharType.FAUNA, Enums.Alliance.QUEEN);
        this.rarity = Enums.Rarity.CHAMPION;
        this.originLocation = "Weird City";
        this.baseMaxHealth = 45;
        this.baseAtk = 8;
        this.baseMag = 12;
        this.baseArmor = 1;
        this.baseCloak = 9;
        this.baseSpeed = 800;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 2;
        this.baseRange = 3;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new BrushWithDeath();
        this.abilities[1] = new PaintedWalls();
        this.abilities[2] = new PaintedFaces();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class BrushWithDeath extends Ability {
        public BrushWithDeath() {
            super("Brush with Death", "Standard magic strike.", 3, true);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team != user.team) {
                if (CombatUtils.rollDodge(target, events, tx, ty)) return AbilityResult.END_TURN;
                int finalMag = Math.max(1, user.getMag() - target.getCloak());
                finalMag = CombatUtils.applyCrit(user, finalMag, events, tx, ty);
                target.health = Math.max(0, target.health - finalMag);
                events.add(new EngineEvent.PopupEvent("BRUSH", finalMag, "MAG", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getMag(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Two-click ability — GameEngine sets wallAnchor on the first click,
     * then calls execute() on the second click with the direction tile as tx/ty.
     * wallAnchor is guaranteed non-null when execute() is called.
     */
    public static class PaintedWalls extends Ability {
        public PaintedWalls() {
            super("Painted Walls", "Place a 3-tile wall. Click anchor, then click a direction.", 4, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (!(user instanceof Sean)) return AbilityResult.END_TURN;
            Sean s = (Sean) user;
            if (s.wallAnchor == null || s.wallDirection == null) return AbilityResult.AWAIT_SECOND_CLICK;

            int ax = (int) s.wallAnchor.x;
            int ay = (int) s.wallAnchor.y;
            int dx = (int) s.wallDirection.x;

            placeWallSegment(ax, ay, state, events);

            if (dx == ax) {
                // Vertical — direction click is in same column as anchor
                placeWallSegment(ax, ay + 1, state, events);
                placeWallSegment(ax, ay - 1, state, events);
                events.add(new EngineEvent.PopupEvent("VERTICAL", 0, "WALL", ax, ay));
            } else {
                // Horizontal — direction click is in a different column
                placeWallSegment(ax + 1, ay, state, events);
                placeWallSegment(ax - 1, ay, state, events);
                events.add(new EngineEvent.PopupEvent("HORIZONTAL", 0, "WALL", ax, ay));
            }

            s.wallAnchor    = null;
            s.wallDirection = null;
            return AbilityResult.END_TURN;
        }

        private void placeWallSegment(int x, int y, GameState state, Array<EngineEvent> events) {
            if (state.board.isValid(x, y) && state.board.getCharacterAt(x, y) == null) {
                state.board.getTile(x, y).setStructureHP(30);
                events.add(new EngineEvent.TileEffectEvent(
                        EngineEvent.TileEffectEvent.Effect.WALL_PLACED, x, y));
            }
        }
    }

    public static class PaintedFaces extends Ability {
        public PaintedFaces() {
            super("Painted Faces", "Drip poison on EVERY tile currently occupied by a unit.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            for (int r = 0; r < state.board.getRows(); r++) {
                for (int c = 0; c < state.board.getCols(); c++) {
                    Character victim = state.board.getCharacterAt(r, c);
                    if (victim != null) {
                        Tile t = state.board.getTile(r, c);
                        if (t != null) {
                            t.setPoison(true);
                            events.add(new EngineEvent.PopupEvent("PAINTED", 0, "POISON", r, c));
                        }
                    }
                }
            }
            return AbilityResult.END_TURN;
        }
    }
}