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

public class Willow extends Character {

    public Willow(Texture portrait) {
        super("Willow", portrait, Enums.CharClass.MAGE, Enums.CharType.FLORA, Enums.Alliance.NONE);
        this.rarity = Enums.Rarity.COMMON;
        this.originLocation = "Forest";
        this.baseMaxHealth = 60;
        this.baseAtk = 0;
        this.baseMag = 20;
        this.baseArmor = 10;
        this.baseCloak = 10;
        this.baseSpeed = 975;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 1;
        this.baseRange = 2;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.0;
        this.abilities[0] = new FallingBranches();
        this.abilities[1] = new Hardwood();
        this.abilities[2] = new GrowRoots();
        startBattle();
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    public static class FallingBranches extends Ability {
        public FallingBranches() {
            super("Falling Branches", "Basic magic attack. Deals MAG - CLK damage.", 2, true);
            this.showMag = true;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team != user.team) {
                if (CombatUtils.rollDodge(target, events, tx, ty)) return AbilityResult.END_TURN;
                int finalMag = Math.max(0, user.getMag() - target.getCloak());
                finalMag = CombatUtils.applyCrit(user, finalMag, events, tx, ty);
                events.add(new EngineEvent.PopupEvent("FALLING BRANCHES", finalMag, "MAG", tx, ty));
                state.engine.applyDamage(state, user, target, 0, 0, finalMag, events);
            } else if (target == null && state.board.getTile(tx, ty) != null
                    && state.board.getTile(tx, ty).hasStructure()) {
                state.engine.applyStructureDamageAtTile(state, tx, ty, user.getMag(), events);
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class Hardwood extends Ability {
        public Hardwood() {
            super("Hardwood", "Gain +5 Armor and +5 Cloak.", 0, false);
            this.armorBuff = 5;
            this.cloakBuff = 5;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            user.armor += 5;
            user.cloak += 5;
            events.add(new EngineEvent.PopupEvent("+5 ARM / +5 CLK", 0, "BUFF", user.x, user.y));
            return AbilityResult.END_TURN;
        }
    }

    public static class GrowRoots extends Ability {
        public GrowRoots() {
            super("Grow Roots", "Root all adjacent tiles (including corners). Occupants are rooted and their wait increases by 1500.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    if (ox == 0 && oy == 0) continue;
                    rootTile(user.x + ox, user.y + oy, state, events);
                }
            }
            events.add(new EngineEvent.PopupEvent("GROW ROOTS", 0, "ROOT", user.x, user.y));
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
                occupant.setCurrentWait(occupant.getCurrentWait() + 1500);
                events.add(new EngineEvent.PopupEvent("ROOTED", 0, "ROOT", x, y));
            }
        }
    }
}
