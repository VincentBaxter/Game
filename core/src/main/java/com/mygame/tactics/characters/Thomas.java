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

public class Thomas extends Character {

    public Thomas(Texture portrait) {
        super("Thomas", portrait, Enums.CharClass.ENGINEER, Enums.CharType.FIRE, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.CHAMPION;
        this.originLocation = "Unknown";
        this.baseMaxHealth = 100;
        this.baseAtk = 15;
        this.baseMag = 8;
        this.baseArmor = 2;
        this.baseCloak = 2;
        this.baseSpeed = 890;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 2;
        this.baseRange = 2;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new NailGun();
        this.abilities[1] = new Thanksgiving();
        this.abilities[2] = new Drywall();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class NailGun extends Ability {
        public NailGun() {
            super("Nail Gun", "Basic attack. Deals ATK - ARM damage.", 2, true);
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
                events.add(new EngineEvent.PopupEvent("NAIL GUN", finalDamage, "ATK", tx, ty));
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getAtk(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class Thanksgiving extends Ability {
        public Thanksgiving() {
            super("Thanksgiving", "Heal all allies for 5 HP.", 0, false);
            this.isHeal = true;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            for (Character ally : state.allUnits) {
                if (ally.isDead() || ally.team != user.team) continue;
                int actualHeal = Math.min(5, ally.maxHealth - ally.health);
                ally.health += actualHeal;
                events.add(new EngineEvent.PopupEvent("THANKS", actualHeal, "HEAL", ally.x, ally.y));
            }
            events.add(new EngineEvent.PopupEvent("THANKSGIVING!", 0, "BUFF", user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }

    /**
     * Drywall — builds a breakable drywall structure (10 HP) on every surrounding
     * tile (8 neighbours). Skips tiles that are collapsed, already have a structure,
     * or are occupied by a character. Uses drywall_tile.png texture. Once per game.
     */
    public static class Drywall extends Ability {
        public Drywall() {
            super("Drywall",
                    "Build a 10 HP drywall structure on all empty surrounding tiles. Once per game.",
                    0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    if (ox == 0 && oy == 0) continue;
                    int wx = user.x + ox;
                    int wy = user.y + oy;
                    if (!state.board.isValid(wx, wy)) continue;
                    Tile t = state.board.getTile(wx, wy);
                    if (t == null || t.isCollapsed() || t.hasStructure()) continue;
                    if (state.board.getCharacterAt(wx, wy) != null) continue;
                    t.setStructureHP(10);
                    t.setDrywall(true);
                    events.add(new EngineEvent.TileEffectEvent(
                            EngineEvent.TileEffectEvent.Effect.WALL_PLACED, wx, wy));
                    events.add(new EngineEvent.PopupEvent("DRYWALL", 0, "WALL", wx, wy));
                }
            }
            events.add(new EngineEvent.PopupEvent("DRYWALL UP!", 0, "BUFF", user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }
}