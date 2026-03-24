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

public class Aaron extends Character {
    public Aaron(Texture portrait) {
        super("Aaron", portrait, Enums.CharClass.MAGE, Enums.CharType.ANGELIC, Enums.Alliance.QUEEN);
        this.rarity         = Enums.Rarity.MYSTIC;
        this.originLocation = "Queen Resort";
        this.baseMaxHealth = 40;
        this.baseAtk = 0;
        this.baseMag = 20;
        this.baseArmor = 3;
        this.baseCloak = 7;
        this.baseSpeed = 900;
        this.baseSpeedReduction = 1.0;
        this.baseMoveDist = 2;
        this.baseRange = 3;
        this.baseCritChance = 0.0;
        this.baseDodgeChance = 0.0;
        this.abilities[0]   = new TouchOfGrace();
        this.abilities[1]   = new HolyLight();
        this.abilities[2]   = new KillSecured();
        startBattle();
    }

    public static class TouchOfGrace extends Ability {
        public TouchOfGrace() {
            super("Touch of Grace", "Heals an ally based on Magic stat.", 1, true);
            this.isHeal = true;
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            if (target != null && target.team == user.team) {
                int missing = target.maxHealth - target.health;
                int heal    = Math.min(user.getMag(), missing);
                target.health += heal;
                events.add(new EngineEvent.PopupEvent("HEAL", heal, "HEAL", tx, ty));
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class HolyLight extends Ability {
        public HolyLight() {
            super("Holy Light", "Freeze enemies within 2 tiles for 300 wait.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            for (int x = user.x - 2; x <= user.x + 2; x++) {
                for (int y = user.y - 2; y <= user.y + 2; y++) {
                    if (state.board.isValid(x, y)
                            && Math.abs(x - user.x) + Math.abs(y - user.y) <= 2) {
                        Character victim = state.board.getCharacterAt(x, y);
                        if (victim != null && victim.team != user.team) {
                            victim.setCurrentWait(victim.getCurrentWait() + 300);
                            state.board.getTile(x, y).setFrozen(true);
                            events.add(new EngineEvent.PopupEvent("FROZEN", 300, "WAIT", x, y));
                            events.add(new EngineEvent.TileEffectEvent(
                                    EngineEvent.TileEffectEvent.Effect.FREEZE, x, y));
                        }
                    }
                }
            }
            return AbilityResult.END_TURN;
        }
    }

    public static class KillSecured extends Ability {
        public KillSecured() {
            super("Kill Secured", "Magic damage to enemies around ALL allies.", 0, false);
        }

        @Override
        public AbilityResult execute(Character user, Character target,
                                     GameState state, Array<EngineEvent> events,
                                     int tx, int ty) {
            for (int r = 0; r < state.board.getRows(); r++) {
                for (int c = 0; c < state.board.getCols(); c++) {
                    Character ally = state.board.getCharacterAt(r, c);
                    if (ally != null && ally.team == user.team) {
                        for (int x = r - 1; x <= r + 1; x++) {
                            for (int y = c - 1; y <= c + 1; y++) {
                                if (state.board.isValid(x, y)) {
                                    Character victim = state.board.getCharacterAt(x, y);
                                    if (victim != null && victim.team != user.team) {
                                        if (!CombatUtils.rollDodge(victim, events, x, y)) {
                                            int dmg = Math.max(0, user.getMag() - victim.getCloak());
                                            dmg = CombatUtils.applyCrit(user, dmg, events, x, y);
                                            victim.health = Math.max(0, victim.health - dmg);
                                            events.add(new EngineEvent.PopupEvent(
                                                    "SECURED", dmg, "MAG", x, y));
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
                        }
                    }
                }
            }
            return AbilityResult.END_TURN;
        }
    }
}