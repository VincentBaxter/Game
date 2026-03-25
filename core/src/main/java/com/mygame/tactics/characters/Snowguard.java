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

public class Snowguard extends Character {

    public Snowguard(Texture portrait) {
        super("Snowguard", portrait, Enums.CharClass.TANK, Enums.CharType.AQUA, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.COMMON;
        this.originLocation = "Trade Hub";
        this.baseMaxHealth = 60;
        this.baseAtk = 0;
        this.baseMag = 12;
        this.baseArmor = 0;
        this.baseCloak = 9;
        this.baseSpeed = 950;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 2;
        this.baseRange = 2;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new Snowball();
        this.abilities[1] = new Iceblast();
        this.abilities[2] = new Blizzard();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class Snowball extends Ability {
        public Snowball() {
            super("Snowball", "Basic magic attack. Deals MAG - CLK damage.", 2, true);
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
                events.add(new EngineEvent.PopupEvent("SNOWBALL", finalMag, "MAG", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getMag(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class Iceblast extends Ability {
        public Iceblast() {
            super("Iceblast", "Freeze a target enemy for 200 wait.", 2, true);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team != user.team) {
                target.setCurrentWait(target.getCurrentWait() + 200);
                state.board.getTile(tx, ty).setFrozen(true);
                events.add(new EngineEvent.PopupEvent("FROZEN", 200, "WAIT", tx, ty));
                events.add(new EngineEvent.TileEffectEvent(
                        EngineEvent.TileEffectEvent.Effect.FREEZE, tx, ty));
            }
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Blizzard — deals 10 true magic damage to all enemies on the board and
     * freezes ALL characters (including allies) for 200 wait. Once per game.
     */
    public static class Blizzard extends Ability {
        public Blizzard() {
            super("Blizzard",
                    "Deal 10 magic damage to all enemies. Freeze all characters for 200 wait. Once per game.",
                    0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            for (Character unit : state.allUnits) {
                if (unit.isDead()) continue;

                // Damage enemies
                if (unit.team != user.team) {
                    int dmg = Math.max(0, 10 - unit.getCloak());
                    unit.health = Math.max(0, unit.health - dmg);
                    events.add(new EngineEvent.PopupEvent("BLIZZARD", dmg, "MAG", unit.x, unit.y));
                }

                // Freeze everyone (including user's team, excluding the Snowguard itself)
                if (unit != user) {
                    unit.setCurrentWait(unit.getCurrentWait() + 200);
                    state.board.getTile(unit.x, unit.y).setFrozen(true);
                    events.add(new EngineEvent.PopupEvent("FROZEN", 200, "WAIT", unit.x, unit.y));
                    events.add(new EngineEvent.TileEffectEvent(
                            EngineEvent.TileEffectEvent.Effect.FREEZE, unit.x, unit.y));
                }
            }

            // Damage all structures on the board
            for (int x = 0; x < state.board.getRows(); x++) {
                for (int y = 0; y < state.board.getCols(); y++) {
                    if (state.board.getCharacterAt(x, y) != null) continue;
                    com.mygame.tactics.Tile t = state.board.getTile(x, y);
                    if (t != null && t.hasStructure()) {
                        state.engine.applyStructureDamageAtTile(state, x, y, 10, events);
                    }
                }
            }

            events.add(new EngineEvent.PopupEvent("BLIZZARD!", 0, "STATUS", user.x, user.y));
            events.add(new EngineEvent.AbilityResolveEvent(1.0f));
            return AbilityResult.END_TURN;
        }
    }
}