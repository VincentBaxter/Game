package com.mygame.tactics;

/**
 * Events emitted by GameEngine after processing an Action.
 * CombatScreen reads these to spawn popups and trigger animations.
 * Contains NO game logic — pure data describing what happened.
 */
public abstract class EngineEvent {

    public static class PopupEvent extends EngineEvent {
        public final String text;
        public final int value;
        public final String type;
        public final int tileX, tileY;
        public final float delay;
        public PopupEvent(String text, int value, String type, int tileX, int tileY, float delay) {
            this.text = text; this.value = value; this.type = type;
            this.tileX = tileX; this.tileY = tileY; this.delay = delay;
        }
        public PopupEvent(String text, int value, String type, int tileX, int tileY) {
            this(text, value, type, tileX, tileY, 0f);
        }
    }

    public static class MoveAnimationEvent extends EngineEvent {
        public final Character unit;
        public final int fromX, fromY;
        public final int toX, toY;
        public MoveAnimationEvent(Character unit, int fromX, int fromY, int toX, int toY) {
            this.unit = unit;
            this.fromX = fromX; this.fromY = fromY;
            this.toX = toX;     this.toY = toY;
        }
    }

    public static class TileEffectEvent extends EngineEvent {
        public enum Effect { FREEZE, POISON, COLLAPSE, WALL_PLACED }
        public final Effect effect;
        public final int tileX, tileY;
        public TileEffectEvent(Effect effect, int tileX, int tileY) {
            this.effect = effect;
            this.tileX = tileX; this.tileY = tileY;
        }
    }

    public static class GameOverEvent extends EngineEvent {
        public final int winnerTeam;
        public GameOverEvent(int winnerTeam) { this.winnerTeam = winnerTeam; }
    }

    /**
     * Emitted by abilities that need a visual pause before the turn ends.
     * CombatScreen blocks input for the given duration so the player can
     * see the effect before the next turn begins.
     */
    public static class AbilityResolveEvent extends EngineEvent {
        public final float duration;
        public AbilityResolveEvent(float duration) { this.duration = duration; }
    }

    /**
     * Emitted by CombatBoard when a character's health should be set to zero
     * and handleDeath should be called.  CombatBoard owns only grid geometry;
     * GameEngine owns all game-state consequences (win condition, allUnits, etc.).
     *
     * CombatScreen ignores this event — it is consumed entirely by GameEngine.
     */
    public static class CharacterKilledEvent extends EngineEvent {
        public final Character victim;
        /** Human-readable reason, e.g. "VOID" or "AMBUSH" — used for logging/debug. */
        public final String reason;
        public CharacterKilledEvent(Character victim, String reason) {
            this.victim = victim;
            this.reason = reason;
        }
    }

    /**
     * Emitted by GameEngine when a character's portrait should change.
     * CombatScreen resolves the new texture by name — GameEngine stays headless.
     */
    public static class PortraitChangeEvent extends EngineEvent {
        public final Character character;
        public final String newPortraitPath;
        public PortraitChangeEvent(Character character, String newPortraitPath) {
            this.character = character;
            this.newPortraitPath = newPortraitPath;
        }
    }

    /**
     * Emitted by abilities that want to relocate the Haven.
     * GameEngine consumes this in executeAbility() and calls moveHaven() so
     * the occupant bonus is correctly stripped before the Haven tile changes.
     * CombatScreen ignores this event.
     */
    public static class HavenMoveEvent extends EngineEvent {
        public final int newX, newY;
        public HavenMoveEvent(int newX, int newY) {
            this.newX = newX;
            this.newY = newY;
        }
    }
}