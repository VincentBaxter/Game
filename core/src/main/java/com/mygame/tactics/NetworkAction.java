package com.mygame.tactics;

/**
 * Wraps a plain Action with network routing metadata.
 *
 * Flow:
 *   1. CombatScreen creates an Action (e.g. MoveAction, AbilityAction)
 *   2. NetworkClient wraps it in a NetworkAction and sends it to the server
 *   3. Server validates playerId matches the acting team
 *   4. Server calls GameEngine.processNetwork(state, networkAction)
 *   5. Server broadcasts resulting GameState + EngineEvents back to both clients
 *
 * Both fields must be set before sending — gameId identifies which game session
 * this action belongs to, and playerId is used to reject out-of-turn actions
 * server-side (same guard that already exists in GameEngine.process()).
 */
public class NetworkAction {

    /** Identifies the game session this action belongs to. */
    public String gameId;

    /**
     * The team number (1 or 2) of the player sending this action.
     * Server rejects the action if this does not match state.activeUnit.team.
     */
    public int playerId;

    /** The actual game action to process. */
    public Action action;

    /** No-arg constructor required for Kryo serialization. */
    public NetworkAction() {}

    public NetworkAction(String gameId, int playerId, Action action) {
        this.gameId   = gameId;
        this.playerId = playerId;
        this.action   = action;
    }
}