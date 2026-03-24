package com.mygame.tactics;

/**
 * Sealed action hierarchy.
 * An Action is a plain data object capturing player intent — no game logic.
 *
 * Flow:
 *   1. CombatScreen reads input → creates an Action
 *   2. Action passed to GameEngine.process(state, action)
 *   3. GameEngine validates + mutates GameState, appends EngineEvents
 *   4. CombatScreen reads updated GameState + events, re-renders
 *
 * For multiplayer: serialize Action → send to server → server runs
 * GameEngine.process() → broadcasts new GameState to both clients.
 */
public abstract class Action {

    /** Which team submitted this action. Used for server-side auth. */
    public final int actingTeam;

    protected Action(int actingTeam) {
        this.actingTeam = actingTeam;
    }

    // --- Battle actions ---

    public static class MoveAction extends Action {
        public final int targetX, targetY;
        public MoveAction(int actingTeam, int targetX, int targetY) {
            super(actingTeam);
            this.targetX = targetX;
            this.targetY = targetY;
        }
    }

    public static class AbilityAction extends Action {
        public final int abilityIndex;
        public final int targetX, targetY; // -1,-1 for range-0 abilities
        public AbilityAction(int actingTeam, int abilityIndex, int targetX, int targetY) {
            super(actingTeam);
            this.abilityIndex = abilityIndex;
            this.targetX = targetX;
            this.targetY = targetY;
        }
    }

    /** "Stay here" in MOVEMENT phase, or "Pass turn" in ABILITY phase. */
    public static class PassAction extends Action {
        public PassAction(int actingTeam) { super(actingTeam); }
    }

    // --- Pre-game actions ---

    public static class ChooseDisguiseAction extends Action {
        public final String targetName;
        public ChooseDisguiseAction(int actingTeam, String targetName) {
            super(actingTeam);
            this.targetName = targetName;
        }
    }

    public static class DeployAction extends Action {
        public final int targetX, targetY;
        public DeployAction(int actingTeam, int targetX, int targetY) {
            super(actingTeam);
            this.targetX = targetX;
            this.targetY = targetY;
        }
    }

    /** Sean's Painted Walls and any other 2-click abilities. */
    public static class TwoStepAbilityAction extends Action {
        public final int abilityIndex;
        public final int anchorX, anchorY;
        public final int directionX, directionY;
        public TwoStepAbilityAction(int actingTeam, int abilityIndex,
                                    int anchorX, int anchorY,
                                    int directionX, int directionY) {
            super(actingTeam);
            this.abilityIndex = abilityIndex;
            this.anchorX  = anchorX;  this.anchorY  = anchorY;
            this.directionX = directionX; this.directionY = directionY;
        }
    }

    // --- Online draft actions ---

    /**
     * Sent by a client during the draft phase to claim a character.
     * The server validates it is this team's turn before accepting.
     */
    public static class DraftPickAction extends Action {
        /** The exact name string as it appears in GameServer.buildPool(). */
        public String characterName; // non-final for Kryo deserialization
        public DraftPickAction() { super(0); } // no-arg for Kryo
        public DraftPickAction(int actingTeam, String characterName) {
            super(actingTeam);
            this.characterName = characterName;
        }
    }

    /**
     * Sent by Team 1 after the draft is complete to choose the battle board.
     * The server creates a BoardConfig from the chosen type and starts the battle.
     */
    public static class BoardChoiceAction extends Action {
        public BoardConfig.BoardType boardType; // non-final for Kryo deserialization
        public BoardChoiceAction() { super(0); } // no-arg for Kryo
        public BoardChoiceAction(int actingTeam, BoardConfig.BoardType boardType) {
            super(actingTeam);
            this.boardType = boardType;
        }
    }
}