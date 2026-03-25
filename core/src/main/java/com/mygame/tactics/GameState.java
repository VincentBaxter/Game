package com.mygame.tactics;

import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.mygame.tactics.characters.Billy;

/**
 * Pure data container for all game-logic state.
 * No LibGDX rendering types (Texture, SpriteBatch, Camera) live here.
 * CombatScreen holds one GameState and reads from it for rendering.
 * GameEngine mutates it in response to Actions.
 */
public class GameState {

    // -----------------------------------------------------------------------
    // Phase enums
    // -----------------------------------------------------------------------
    public enum Phase { PRE_GAME, BATTLE, GAME_OVER }
    public enum TurnPhase { MOVEMENT, ABILITY }

    // -----------------------------------------------------------------------
    // Game phase
    // -----------------------------------------------------------------------
    public Phase phase = Phase.PRE_GAME;
    public TurnPhase turnPhase = TurnPhase.MOVEMENT;

    // -----------------------------------------------------------------------
    // Board & units
    // -----------------------------------------------------------------------
    public CombatBoard board;
    public Array<Character> team1    = new Array<>();
    public Array<Character> team2    = new Array<>();
    public Array<Character> allUnits = new Array<>();
    public Array<Character> setupQueue = new Array<>();
    public Character activeUnit = null;
    public BoardConfig boardConfig;

    // -----------------------------------------------------------------------
    // Turn flags
    // -----------------------------------------------------------------------
    public boolean isFighterBonusMove      = false;
    public boolean isGrandEntranceMove     = false;
    public boolean isMarathonMove          = false;
    public boolean wasBillyRevealedThisAction = false;

    // -----------------------------------------------------------------------
    // Win condition
    // -----------------------------------------------------------------------
    public int winnerTeam = 0; // 0 = no winner yet

    // -----------------------------------------------------------------------
    // Haven & board collapse
    // -----------------------------------------------------------------------
    public Haven  haven               = null;
    public float  collapseWait        = 1000f;
    public int    collapseCount       = 0;
    public Character havenOccupant    = null;
    public boolean warningShownThisCycle = false;
    public float  warningAlpha        = 0f; // visual fade; kept here so logic + rendering share one value
    public int windPushDistance = 1;
    public boolean havenLocked = false; // true once Ben's Lockdown fires

    // Columns and rows that will collapse on the next ring trigger.
    // Populated by GameEngine.triggerRingCollapse() so CombatScreen can
    // render the crack-overlay warning before tiles actually fall.
    public Array<Integer> pendingCollapseCols = new Array<>();
    public Array<Integer> pendingCollapseRows = new Array<>();

    // -----------------------------------------------------------------------
    // Timeline
    // -----------------------------------------------------------------------
    public Timeline timeline = new Timeline();

    // -----------------------------------------------------------------------
    // UI selection state  (needed by renderer; mutated by GameEngine)
    // -----------------------------------------------------------------------
    public Array<Vector2> reachableTiles  = new Array<>();
    public Array<Vector2> targetableTiles = new Array<>();
    public Vector2 selectedMoveTile   = null;
    public Vector2 selectedTargetTile = null;
    public Ability selectedAbility    = null;

    // Pending anchor for two-step abilities (e.g. Sean's Painted Walls)
    public Vector2 pendingAnchor = null;

    // Reference to the GameEngine so abilities can call processBoardEvents()
    // after board.moveCharacter() to resolve void kills immediately.
    public GameEngine engine;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------
    public GameState() {
        this.board = new CombatBoard(9, 9);
    }

    public GameState(Array<Character> team1, Array<Character> team2, BoardConfig config) {
        this();
        this.team1 = team1;
        this.team2 = team2;
        for (Character c : team1) c.team = 1;
        for (Character c : team2) c.team = 2;
        allUnits.addAll(team1);
        allUnits.addAll(team2);
        for (Character c : allUnits) c.startBattle();

        // Billy gets one slot: he selects his disguise then immediately deploys in the same turn.
        for (Character c : allUnits) if (c instanceof Billy) setupQueue.add(c);
        for (Character c : allUnits) if (c.getCharClass() == Enums.CharClass.STATUE) setupQueue.add(c);
        for (Character c : allUnits) {
            if (c.getCharClass() != Enums.CharClass.STATUE && !(c instanceof Billy))
                setupQueue.add(c);
        }
        phase = setupQueue.size > 0 ? Phase.PRE_GAME : Phase.BATTLE;
        
        this.boardConfig = config;
    }

    // -----------------------------------------------------------------------
    // Convenience queries
    // -----------------------------------------------------------------------
    public boolean isPreGame()  { return phase == Phase.PRE_GAME;  }
    public boolean isBattle()   { return phase == Phase.BATTLE;    }
    public boolean isGameOver() { return phase == Phase.GAME_OVER; }
    public boolean isMovementPhase() { return turnPhase == TurnPhase.MOVEMENT; }
    public boolean isAbilityPhase()  { return turnPhase == TurnPhase.ABILITY;  }

    public boolean bothTeamsAlive() {
        boolean t1 = false, t2 = false;
        for (Character c : allUnits) {
            if (!c.isDead()) { if (c.team == 1) t1 = true; else t2 = true; }
        }
        return t1 && t2;
    }
}