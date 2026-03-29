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

    /**
     * Sent immediately after connecting to tell the server which matchmaking
     * queue to enter: casual (isRanked=false) or ranked (isRanked=true).
     */
    public static class JoinQueueAction extends Action {
        public boolean ranked; // non-final for Kryo deserialization
        public JoinQueueAction() { super(0); } // no-arg for Kryo
        public JoinQueueAction(boolean ranked) {
            super(0);
            this.ranked = ranked;
        }
    }

    /**
     * Sent by DraftScreen when it first becomes active in a ranked match.
     * The server replies with a fresh DRAFT_UPDATE so the client gets the
     * correct filtered pool (locked chars excluded) regardless of timing.
     */
    public static class RequestDraftStateAction extends Action {
        public RequestDraftStateAction() { super(0); }
        public RequestDraftStateAction(int actingTeam) { super(actingTeam); }
    }

    /** Sent once after connecting — registers the player in the lobby with their username and appearance. */
    public static class LobbyJoinAction extends Action {
        public String username;
        public int modelType    = 0;
        public int skinColorIdx = 0;
        public int shirtColorIdx = 0;
        public int pantsColorIdx = 0;
        public LobbyJoinAction() { super(0); }
        public LobbyJoinAction(String username) { super(0); this.username = username; }
        public LobbyJoinAction(String username, int modelType, int skinColorIdx,
                               int shirtColorIdx, int pantsColorIdx) {
            super(0);
            this.username      = username;
            this.modelType     = modelType;
            this.skinColorIdx  = skinColorIdx;
            this.shirtColorIdx = shirtColorIdx;
            this.pantsColorIdx = pantsColorIdx;
        }
    }

    /** Sent every time the player moves to a new tile in the world map. */
    public static class PlayerMoveAction extends Action {
        public int x, y;
        public PlayerMoveAction() { super(0); }
        public PlayerMoveAction(int x, int y) { super(0); this.x = x; this.y = y; }
    }
}