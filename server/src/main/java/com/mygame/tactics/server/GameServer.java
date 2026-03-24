package com.mygame.tactics.server;


import com.mygame.tactics.network.KryoRegistrar;
import java.io.IOException;
import com.esotericsoftware.kryonet.Listener;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.Connection;
import com.mygame.tactics.Action;
import com.mygame.tactics.EngineEvent;
import com.mygame.tactics.GameEngine;
import com.mygame.tactics.GameState;
import com.mygame.tactics.NetworkAction;
import com.mygame.tactics.NetworkMessage;

/**
 * Headless game server.
 *
 * Manages game rooms — each room holds two player connections and one
 * authoritative GameState. When a player sends a NetworkAction, the server
 * validates it, runs GameEngine.processNetwork(), and broadcasts the result
 * to both players in the room.
 *
 * Port layout:
 *   TCP 54555 — reliable ordered messages (actions, state snapshots)
 *   UDP 54777 — reserved for future low-latency updates (pings, etc.)
 */
public class GameServer {

    private static final int TCP_PORT = 54555;
    private static final int UDP_PORT = 54777;

    private final Server     kryoServer;
    private final GameEngine engine;

    // Maps a connection ID to the room it belongs to
    private final Map<Integer, GameRoom> connectionToRoom = new HashMap<>();

    // Maps a gameId to its room
    private final Map<String, GameRoom> rooms = new HashMap<>();

    // Waiting player — the first player to connect waits here until a second joins
    private Connection waitingPlayer = null;

    public GameServer() {
        kryoServer = new Server(16384, 2048);
        engine     = new GameEngine();
        KryoRegistrar.register(kryoServer.getKryo());
    }

    public void start() throws IOException {
        kryoServer.start();
        kryoServer.bind(TCP_PORT, UDP_PORT);

        kryoServer.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                onPlayerConnected(connection);
            }

            @Override
            public void disconnected(Connection connection) {
                onPlayerDisconnected(connection);
            }

            @Override
            public void received(Connection connection, Object object) {
                if (object instanceof NetworkAction) {
                    onActionReceived(connection, (NetworkAction) object);
                }
            }
        });

        System.out.println("Server started on TCP " + TCP_PORT + " / UDP " + UDP_PORT);
    }

    // -----------------------------------------------------------------------
    // Connection handling
    // -----------------------------------------------------------------------

    private synchronized void onPlayerConnected(Connection connection) {
        System.out.println("Player connected: " + connection.getID());

        if (waitingPlayer == null) {
            // First player — put them in the waiting room
            waitingPlayer = connection;
            System.out.println("Player " + connection.getID() + " is waiting for an opponent.");
        } else {
            // Second player — create a room and start the game
            String gameId = UUID.randomUUID().toString();
            GameRoom room = new GameRoom(gameId, waitingPlayer, connection);
            rooms.put(gameId, room);
            connectionToRoom.put(waitingPlayer.getID(), room);
            connectionToRoom.put(connection.getID(), room);

            waitingPlayer = null;

            System.out.println("Game room created: " + gameId);

            // Notify both players which team they are
            waitingPlayer = null;
            NetworkMessage p1Msg = NetworkMessage.roomJoined(gameId, 1);
            NetworkMessage p2Msg = NetworkMessage.roomJoined(gameId, 2);
            room.player1.sendTCP(p1Msg);
            room.player2.sendTCP(p2Msg);
        }
    }

    private synchronized void onPlayerDisconnected(Connection connection) {
        System.out.println("Player disconnected: " + connection.getID());

        GameRoom room = connectionToRoom.remove(connection.getID());
        if (room != null) {
            // Notify the other player
            Connection other = (room.player1.getID() == connection.getID())
                    ? room.player2 : room.player1;
            if (other.isConnected()) {
                other.sendTCP(NetworkMessage.opponentDisconnected(room.gameId));
            }
            connectionToRoom.remove(other.getID());
            rooms.remove(room.gameId);
            System.out.println("Room " + room.gameId + " closed.");
        }

        // Clear the waiting slot if this was the waiting player
        if (waitingPlayer != null && waitingPlayer.getID() == connection.getID()) {
            waitingPlayer = null;
        }
    }

    // -----------------------------------------------------------------------
    // Action processing
    // -----------------------------------------------------------------------

    private synchronized void onActionReceived(Connection connection, NetworkAction na) {
        GameRoom room = connectionToRoom.get(connection.getID());
        if (room == null) {
            System.out.println("Action received from player with no room: " + connection.getID());
            return;
        }

        if (!room.gameId.equals(na.gameId)) {
            connection.sendTCP(NetworkMessage.rejected(na.gameId, "Game ID mismatch."));
            return;
        }

        if (na.action == null) {
            connection.sendTCP(NetworkMessage.rejected(na.gameId, "Null action."));
            return;
        }

        // Run the action through the authoritative game engine
        com.badlogic.gdx.utils.Array<EngineEvent> events =
                engine.processNetwork(room.state, na);

        if (events == null || events.size == 0 && room.state.activeUnit != null
                && na.playerId != room.state.activeUnit.team) {
            connection.sendTCP(NetworkMessage.rejected(na.gameId, "Not your turn."));
            return;
        }

        // Broadcast result to both players
        NetworkMessage result = room.state.isGameOver()
                ? NetworkMessage.gameOver(room.gameId, room.state)
                : NetworkMessage.actionResult(room.gameId, room.state, events);

        room.player1.sendTCP(result);
        room.player2.sendTCP(result);

        System.out.println("Action processed in room " + room.gameId
                + " by player " + na.playerId);
    }

    // -----------------------------------------------------------------------
    // Kryo registration
    // -----------------------------------------------------------------------

    /**
     * Every class that crosses the network must be registered with Kryo.
     * Order does not matter but every class must be listed — missing
     * registrations cause serialization errors at runtime.
     */
    private void registerClasses(Kryo kryo) {
        // Network wrapper classes
        kryo.register(NetworkAction.class);
        kryo.register(NetworkMessage.class);
        kryo.register(NetworkMessage.Type.class);

        // Action subclasses
        kryo.register(Action.class);
        kryo.register(Action.MoveAction.class);
        kryo.register(Action.AbilityAction.class);
        kryo.register(Action.PassAction.class);
        kryo.register(Action.DeployAction.class);
        kryo.register(Action.ChooseDisguiseAction.class);
        kryo.register(Action.TwoStepAbilityAction.class);

        // EngineEvent subclasses
        kryo.register(EngineEvent.class);
        kryo.register(EngineEvent.PopupEvent.class);
        kryo.register(EngineEvent.MoveAnimationEvent.class);
        kryo.register(EngineEvent.TileEffectEvent.class);
        kryo.register(EngineEvent.TileEffectEvent.Effect.class);
        kryo.register(EngineEvent.GameOverEvent.class);
        kryo.register(EngineEvent.AbilityResolveEvent.class);
        kryo.register(EngineEvent.CharacterKilledEvent.class);
        kryo.register(EngineEvent.PortraitChangeEvent.class);
        kryo.register(EngineEvent.HavenMoveEvent.class);

        // Collections
        kryo.register(com.badlogic.gdx.utils.Array.class);
        kryo.register(java.util.ArrayList.class);
        kryo.register(String.class);
        kryo.register(int[].class);

        System.out.println("Kryo classes registered.");
    }

    // -----------------------------------------------------------------------
    // Inner class — GameRoom
    // -----------------------------------------------------------------------

    /**
     * Holds the two player connections and the shared authoritative GameState
     * for one game session. GameState is null until the draft is complete and
     * both players have confirmed their teams.
     *
     * TODO: GameState will be initialized here once the draft flow is wired
     * up over the network. For now it is set externally after room creation.
     */
    public static class GameRoom {
        public final String     gameId;
        public final Connection player1;
        public final Connection player2;
        public GameState        state; // set after draft is complete

        public GameRoom(String gameId, Connection player1, Connection player2) {
            this.gameId  = gameId;
            this.player1 = player1;
            this.player2 = player2;
        }
    }
}