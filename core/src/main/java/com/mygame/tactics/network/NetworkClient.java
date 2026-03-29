package com.mygame.tactics.network;

import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.mygame.tactics.Action;
import com.mygame.tactics.NetworkAction;
import com.mygame.tactics.NetworkMessage;

import java.io.IOException;

/**
 * Client-side networking layer.
 *
 * Handles connecting to the game server, sending NetworkAction objects,
 * and receiving NetworkMessage objects back. Notifies a listener when
 * messages arrive so the game screens can react.
 *
 * Usage:
 *   NetworkClient client = new NetworkClient();
 *   client.setListener(msg -> { ... handle message ... });
 *   client.connect("192.168.1.100");
 *   client.sendAction(gameId, playerId, action);
 *   client.disconnect();
 */
public class NetworkClient {

    private static final int TCP_PORT    = 54555;
    private static final int UDP_PORT    = 54777;
    private static final int TIMEOUT_MS  = 5000;

    public interface MessageListener {
        void onMessage(NetworkMessage message);
        void onDisconnected();
        void onConnected();
    }

    private final Client kryoClient;
    private MessageListener listener;
    private String gameId;
    private int    assignedTeam = -1;
    private boolean connected   = false;

    public NetworkClient() {
        kryoClient = new Client(65536, 65536);
        KryoRegistrar.register(kryoClient.getKryo());

        kryoClient.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                connected = true;
                System.out.println("Connected to server.");
                if (listener != null) listener.onConnected();
            }

            @Override
            public void disconnected(Connection connection) {
                connected = false;
                System.out.println("Disconnected from server.");
                if (listener != null) listener.onDisconnected();
            }

            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof NetworkMessage) {
                    NetworkMessage msg = (NetworkMessage) object;
                    handleMessage(msg);
                }
            }
        });
    }

    // -----------------------------------------------------------------------
    // Connection
    // -----------------------------------------------------------------------

    /**
     * Connects to the server at the given IP address.
     * Blocks for up to TIMEOUT_MS milliseconds.
     *
     * @param serverIp The IP address of the server.
     * @throws IOException if the connection fails or times out.
     */
    public void connect(String serverIp) throws IOException {
        kryoClient.start();
        kryoClient.connect(TIMEOUT_MS, serverIp, TCP_PORT, UDP_PORT);
    }

    public void disconnect() {
        kryoClient.stop();
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    // -----------------------------------------------------------------------
    // Sending
    // -----------------------------------------------------------------------

    /**
     * Wraps the given Action in a NetworkAction and sends it to the server.
     *
     * @param action The game action to send.
     */
    /**
     * Registers the player in the lobby with their chosen username and appearance.
     * Must be called once after connect() succeeds.
     */
    public void sendLobbyJoin(String username, int modelType,
                              int skinColorIdx, int shirtColorIdx, int pantsColorIdx) {
        if (!connected) return;
        kryoClient.sendTCP(new NetworkAction(null, 0,
                new Action.LobbyJoinAction(username, modelType, skinColorIdx, shirtColorIdx, pantsColorIdx)));
    }

    /**
     * Notifies the server that this player moved to a new tile.
     */
    public void sendPlayerMove(int x, int y) {
        if (!connected) return;
        kryoClient.sendTCP(new NetworkAction(null, 0, new Action.PlayerMoveAction(x, y)));
    }

    /**
     * Tells the server to enter the ranked matchmaking queue.
     */
    public void joinQueue() {
        if (!connected) return;
        kryoClient.sendTCP(new NetworkAction(null, 0, new Action.JoinQueueAction(true)));
    }

    /** @deprecated Use joinQueue() — ranked is always true now. */
    public void joinQueue(boolean ranked) {
        joinQueue();
    }

    public void sendAction(Action action) {
        if (!connected) {
            System.err.println("Cannot send action — not connected.");
            return;
        }
        NetworkAction na = new NetworkAction(gameId, assignedTeam, action);
        kryoClient.sendTCP(na);
    }

    // -----------------------------------------------------------------------
    // Receiving
    // -----------------------------------------------------------------------

    private void handleMessage(NetworkMessage msg) {
        switch (msg.type) {
            case ROOM_JOINED:
                gameId       = msg.gameId;
                assignedTeam = msg.assignedTeam;
                System.out.println("Joined room " + gameId + " as team " + assignedTeam);
                break;
            case OPPONENT_DISCONNECTED:
                System.out.println("Opponent disconnected.");
                break;
            case GAME_OVER:
                System.out.println("Game over. Winner: team " + 
                    (msg.gameState != null ? msg.gameState.winnerTeam : "?"));
                break;
            default:
                break;
        }
        // Always forward to the screen-level listener
        if (listener != null) listener.onMessage(msg);
    }

    // -----------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------

    public void setListener(MessageListener listener) {
        this.listener = listener;
    }

    public int getAssignedTeam()  { return assignedTeam; }
    public String getGameId()     { return gameId; }
}