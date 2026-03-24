package com.mygame.tactics.network;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.mygame.tactics.Action;
import com.mygame.tactics.BoardConfig;
import com.mygame.tactics.Character;
import com.mygame.tactics.EngineEvent;
import com.mygame.tactics.GameEngine;
import com.mygame.tactics.GameState;
import com.mygame.tactics.NetworkAction;
import com.mygame.tactics.NetworkMessage;
import com.mygame.tactics.characters.Aaron;
import com.mygame.tactics.characters.Anna;
import com.mygame.tactics.characters.Ben;
import com.mygame.tactics.characters.Billy;
import com.mygame.tactics.characters.Brad;
import com.mygame.tactics.characters.Emily;
import com.mygame.tactics.characters.Evan;
import com.mygame.tactics.characters.Ghia;
import com.mygame.tactics.characters.GuardTower;
import com.mygame.tactics.characters.Hunter;
import com.mygame.tactics.characters.Jaxon;
import com.mygame.tactics.characters.Lark;
import com.mygame.tactics.characters.Luke;
import com.mygame.tactics.characters.Mason;
import com.mygame.tactics.characters.Maxx;
import com.mygame.tactics.characters.Nathan;
import com.mygame.tactics.characters.Sean;
import com.mygame.tactics.characters.Snowguard;
import com.mygame.tactics.characters.Speen;
import com.mygame.tactics.characters.Stoneguard;
import com.mygame.tactics.characters.Thomas;
import com.mygame.tactics.characters.Tyler;
import com.mygame.tactics.characters.Weirdguard;
import com.badlogic.gdx.utils.Array;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Headless game server supporting multiple concurrent games.
 *
 * Architecture:
 *   - One Server (Kryonet) listens on TCP 54555 / UDP 54777.
 *   - Incoming connections are held in a matchmaking queue.
 *   - Every two waiting connections are paired into a GameRoom.
 *   - Each GameRoom owns a GameState + GameEngine and processes
 *     actions authoritatively via GameEngine.processNetwork().
 *   - Results are broadcast to both clients as NetworkMessages.
 *
 * Draft phase:
 *   - The server maintains the authoritative pick pool and pick order.
 *   - Clients send DraftPickAction (a String character name) when they pick.
 *   - After each pick the server broadcasts a DRAFT_UPDATE to both clients
 *     so each screen stays in sync.
 *   - Once all 8 picks are done, the server waits for the board choice
 *     (a BoardChoiceAction) from Team 1, then starts the battle.
 *
 * Disconnect handling:
 *   - If either player disconnects at any point, the opponent is sent
 *     a GAME_OVER message with winnerTeam set to the opponent's team.
 *
 * Running:
 *   java -cp <classpath> com.mygame.tactics.network.GameServer
 *
 * No LibGDX display context is needed — the server is fully headless.
 * Textures are never loaded; Character subclasses receive a null portrait
 * because the server never renders anything.
 */
public class GameServer {

    // -----------------------------------------------------------------------
    // Network constants — must match NetworkClient
    // -----------------------------------------------------------------------
    private static final int TCP_PORT   = 54555;
    private static final int UDP_PORT   = 54777;
    private static final int PICKS_PER_TEAM = 4;
    private static final int TOTAL_PICKS    = PICKS_PER_TEAM * 2;

    // -----------------------------------------------------------------------
    // Server state
    // -----------------------------------------------------------------------
    private final Server kryoServer;

    /** Connections waiting for a second player. At most one at a time. */
    private Connection waitingConnection = null;

    /** Maps connectionId -> GameRoom so disconnects can be routed. */
    private final Map<Integer, GameRoom> roomByConnection = new ConcurrentHashMap<>();

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------
    public static void main(String[] args) throws IOException {
        new GameServer().start();
    }

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public GameServer() {
        kryoServer = new Server(262144, 65536);
        KryoRegistrar.register(kryoServer.getKryo());
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------
    public void start() throws IOException {
        kryoServer.start();
        kryoServer.bind(TCP_PORT, UDP_PORT);

        kryoServer.addListener(new Listener() {
            @Override
            public void connected(Connection conn) {
                log("Client connected: " + conn.getID());
                handleNewConnection(conn);
            }

            @Override
            public void disconnected(Connection conn) {
                log("Client disconnected: " + conn.getID());
                handleDisconnect(conn);
            }

            @Override
            public void received(Connection conn, Object obj) {
                if (obj instanceof NetworkAction) {
                    handleNetworkAction(conn, (NetworkAction) obj);
                }
            }
        });

        log("GameServer started on TCP:" + TCP_PORT + " / UDP:" + UDP_PORT);
        log("Waiting for players...");

        // Keep the main thread alive
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // -----------------------------------------------------------------------
    // Connection / matchmaking
    // -----------------------------------------------------------------------
    private synchronized void handleNewConnection(Connection conn) {
        if (waitingConnection == null) {
            // First player — put them in the queue
            waitingConnection = conn;
            log("Player queued, waiting for opponent...");
        } else {
            // Second player — create a room
            Connection team1Conn = waitingConnection;
            Connection team2Conn = conn;
            waitingConnection = null;

            String gameId = UUID.randomUUID().toString().substring(0, 8);
            GameRoom room = new GameRoom(gameId, team1Conn, team2Conn);
            roomByConnection.put(team1Conn.getID(), room);
            roomByConnection.put(team2Conn.getID(), room);

            // Tell both clients which team they are
            team1Conn.sendTCP(NetworkMessage.roomJoined(gameId, 1));
            team2Conn.sendTCP(NetworkMessage.roomJoined(gameId, 2));

            log("[" + gameId + "] Room created — starting draft.");
            room.startDraft();
        }
    }

    private synchronized void handleDisconnect(Connection conn) {
        // Remove from matchmaking queue if they were waiting
        if (waitingConnection != null && waitingConnection.getID() == conn.getID()) {
            waitingConnection = null;
            log("Queued player disconnected before match was found.");
            return;
        }

        GameRoom room = roomByConnection.remove(conn.getID());
        if (room == null) return;

        // Remove the other player's room entry too
        Connection other = room.getOtherConnection(conn);
        if (other != null) {
            roomByConnection.remove(other.getID());
            int winnerTeam = room.getTeamFor(other);
            NetworkMessage gameOver = NetworkMessage.gameOver(room.gameId, room.buildGameOverState(winnerTeam));
            other.sendTCP(gameOver);
            log("[" + room.gameId + "] Player disconnected — team " + winnerTeam + " wins.");
        }
    }

    // -----------------------------------------------------------------------
    // Action routing
    // -----------------------------------------------------------------------
    private void handleNetworkAction(Connection conn, NetworkAction na) {
        GameRoom room = roomByConnection.get(conn.getID());
        if (room == null) {
            conn.sendTCP(NetworkMessage.rejected(na.gameId, "Not in a game room."));
            return;
        }
        room.processAction(conn, na);
    }

    // -----------------------------------------------------------------------
    // GameRoom — owns one complete game (draft + battle)
    // -----------------------------------------------------------------------
    private class GameRoom {

        final String gameId;
        final Connection conn1; // team 1
        final Connection conn2; // team 2

        // Draft state
        final Array<String> pool         = new Array<>(); // remaining character names
        final Array<String> team1Picks   = new Array<>();
        final Array<String> team2Picks   = new Array<>();
        int pickingTeam  = 1;
        int picksMade    = 0;
        boolean boardChosen = false;

        // Battle state (null until draft + board selection complete)
        GameState  state  = null;
        GameEngine engine = null;

        GameRoom(String gameId, Connection conn1, Connection conn2) {
            this.gameId = gameId;
            this.conn1  = conn1;
            this.conn2  = conn2;
        }

        // -------------------------------------------------------------------
        // Draft
        // -------------------------------------------------------------------
        void startDraft() {
            buildPool();
            broadcastDraftState();
        }

        /** Build the canonical character pool (names only — no textures on server). */
        private void buildPool() {
            // Must stay in sync with DraftScreen.buildPool()
            pool.add("Hunter");
            pool.add("Sean");
            pool.add("Jaxon");
            pool.add("Evan");
            pool.add("Billy");
            pool.add("Aaron");
            pool.add("Speen");
            pool.add("Mason");
            pool.add("Lark");
            pool.add("Nathan");
            pool.add("Luke");
            pool.add("Brad");
            pool.add("Guard Tower");
            pool.add("Weirdguard");
            pool.add("Stoneguard");
            pool.add("Snowguard");
            pool.add("Tyler");
            pool.add("Anna");
            pool.add("Emily");
            pool.add("Thomas");
            pool.add("Ghia");
            pool.add("Maxx");
            pool.add("Ben");
        }

        /**
         * Sends the current draft state to both clients as a DRAFT_UPDATE message.
         * Clients use this to render the pool, rosters, and whose turn it is.
         */
        private void broadcastDraftState() {
            NetworkMessage msg = buildDraftUpdateMessage();
            conn1.sendTCP(msg);
            conn2.sendTCP(msg);
        }

        private NetworkMessage buildDraftUpdateMessage() {
            NetworkMessage msg = new NetworkMessage();
            msg.type           = NetworkMessage.Type.DRAFT_UPDATE;
            msg.gameId         = gameId;
            msg.success        = true;
            msg.pickingTeam    = pickingTeam;
            msg.remainingPool  = new Array<>(pool);
            msg.team1Picks     = new Array<>(team1Picks);
            msg.team2Picks     = new Array<>(team2Picks);
            return msg;
        }

        /** Handle a draft pick sent by a client. */
        private synchronized void handleDraftPick(Connection conn, NetworkAction na) {
            // Validate it is this team's turn to pick
            int senderTeam = getTeamFor(conn);
            if (senderTeam != pickingTeam) {
                conn.sendTCP(NetworkMessage.rejected(gameId, "Not your turn to pick."));
                return;
            }
            if (!(na.action instanceof Action.DraftPickAction)) {
                conn.sendTCP(NetworkMessage.rejected(gameId, "Expected a DraftPickAction."));
                return;
            }

            String chosen = ((Action.DraftPickAction) na.action).characterName;
            if (!pool.contains(chosen, false)) {
                conn.sendTCP(NetworkMessage.rejected(gameId, "Character not available: " + chosen));
                return;
            }

            pool.removeValue(chosen, false);
            if (pickingTeam == 1) team1Picks.add(chosen);
            else                  team2Picks.add(chosen);

            picksMade++;
            pickingTeam = (pickingTeam == 1) ? 2 : 1;

            log("[" + gameId + "] Team " + senderTeam + " picked " + chosen
                    + " (" + picksMade + "/" + TOTAL_PICKS + ")");

            if (picksMade == TOTAL_PICKS) {
                // Draft complete — ask team 1 to choose the board
                NetworkMessage doneMsg = buildDraftUpdateMessage();
                doneMsg.type = NetworkMessage.Type.DRAFT_COMPLETE;
                conn1.sendTCP(doneMsg);
                conn2.sendTCP(doneMsg);
                log("[" + gameId + "] Draft complete. Waiting for Team 1 to choose board.");
            } else {
                broadcastDraftState();
            }
        }

        /** Handle a board choice sent by team 1 after the draft. */
        private synchronized void handleBoardChoice(Connection conn, NetworkAction na) {
            if (getTeamFor(conn) != 1) {
                conn.sendTCP(NetworkMessage.rejected(gameId, "Only Team 1 selects the board."));
                return;
            }
            if (!(na.action instanceof Action.BoardChoiceAction)) {
                conn.sendTCP(NetworkMessage.rejected(gameId, "Expected a BoardChoiceAction."));
                return;
            }
            if (boardChosen) {
                conn.sendTCP(NetworkMessage.rejected(gameId, "Board already chosen."));
                return;
            }

            boardChosen = true;
            BoardConfig.BoardType boardType = ((Action.BoardChoiceAction) na.action).boardType;
            BoardConfig config = boardConfigFor(boardType);

            log("[" + gameId + "] Board chosen: " + boardType);
            startBattle(config);
        }

        private BoardConfig boardConfigFor(BoardConfig.BoardType type) {
            switch (type) {
                case WIND:   return BoardConfig.wind();
                case DESERT: return BoardConfig.desert();
                default:     return BoardConfig.forest();
            }
        }

        // -------------------------------------------------------------------
        // Battle
        // -------------------------------------------------------------------
        private void startBattle(BoardConfig config) {
            Array<Character> t1 = buildTeam(team1Picks, 1);
            Array<Character> t2 = buildTeam(team2Picks, 2);

            engine = new GameEngine();
            state  = new GameState(t1, t2, config);
            state.engine = engine;

            Array<EngineEvent> initEvents = engine.initialize(state);

            NetworkMessage msg = NetworkMessage.actionResult(gameId, state, initEvents);
            conn1.sendTCP(msg);
            conn2.sendTCP(msg);
            log("[" + gameId + "] Battle started.");
        }

        /**
         * Instantiate Character objects from names for the server.
         * Portraits are null — the server never renders.
         */
        private Array<Character> buildTeam(Array<String> names, int team) {
            Array<Character> result = new Array<>();
            for (String name : names) {
                Character c = instantiateCharacter(name);
                if (c != null) {
                    c.team = team;
                    result.add(c);
                }
            }
            return result;
        }

        /** Maps a character name to a new instance (null portrait — headless). */
        private Character instantiateCharacter(String name) {
            switch (name) {
                case "Hunter":      return new Hunter(null);
                case "Sean":        return new Sean(null);
                case "Jaxon":       return new Jaxon(null);
                case "Evan":        return new Evan(null);
                case "Billy":       return new Billy(null);
                case "Aaron":       return new Aaron(null);
                case "Speen":       return new Speen(null);
                case "Mason":       return new Mason(null);
                case "Lark":        return new Lark(null);
                case "Nathan":      return new Nathan(null);
                case "Luke":        return new Luke(null);
                case "Brad":        return new Brad(null);
                case "Guard Tower": return new GuardTower(null);
                case "Weirdguard":  return new Weirdguard(null);
                case "Stoneguard":  return new Stoneguard(null);
                case "Snowguard":   return new Snowguard(null);
                case "Tyler":       return new Tyler(null);
                case "Anna":        return new Anna(null);
                case "Emily":       return new Emily(null);
                case "Thomas":      return new Thomas(null);
                case "Ghia":        return new Ghia(null);
                case "Maxx":        return new Maxx(null);
                case "Ben":         return new Ben(null);
                default:
                    log("WARNING: Unknown character name '" + name + "' — skipping.");
                    return null;
            }
        }

        // -------------------------------------------------------------------
        // Action dispatch
        // -------------------------------------------------------------------
        synchronized void processAction(Connection conn, NetworkAction na) {
            // Route based on game phase
            if (state == null) {
                // Still in draft / board selection
                if (na.action instanceof Action.DraftPickAction) {
                    handleDraftPick(conn, na);
                } else if (na.action instanceof Action.BoardChoiceAction) {
                    handleBoardChoice(conn, na);
                } else {
                    conn.sendTCP(NetworkMessage.rejected(na.gameId,
                            "Game has not started yet."));
                }
                return;
            }

            if (state.isGameOver()) {
                conn.sendTCP(NetworkMessage.rejected(na.gameId, "Game is already over."));
                return;
            }

            // Validate acting team
            if (state.activeUnit == null || na.playerId != state.activeUnit.team) {
                conn.sendTCP(NetworkMessage.rejected(na.gameId, "Not your turn."));
                return;
            }

            // Process through GameEngine
            Array<EngineEvent> events = engine.processNetwork(state, na);
            NetworkMessage result = NetworkMessage.actionResult(gameId, state, events);

            conn1.sendTCP(result);
            conn2.sendTCP(result);

            if (state.isGameOver()) {
                log("[" + gameId + "] Game over — team " + state.winnerTeam + " wins.");
                roomByConnection.remove(conn1.getID());
                roomByConnection.remove(conn2.getID());
            }
        }

        // -------------------------------------------------------------------
        // Disconnect helper
        // -------------------------------------------------------------------
        GameState buildGameOverState(int winnerTeam) {
            if (state != null) {
                state.winnerTeam = winnerTeam;
                state.phase = GameState.Phase.GAME_OVER;
                return state;
            }
            // Disconnect happened during draft — return a minimal state
            GameState s = new GameState();
            s.winnerTeam = winnerTeam;
            s.phase = GameState.Phase.GAME_OVER;
            return s;
        }

        // -------------------------------------------------------------------
        // Utilities
        // -------------------------------------------------------------------
        Connection getOtherConnection(Connection conn) {
            if (conn.getID() == conn1.getID()) return conn2;
            if (conn.getID() == conn2.getID()) return conn1;
            return null;
        }

        int getTeamFor(Connection conn) {
            if (conn.getID() == conn1.getID()) return 1;
            if (conn.getID() == conn2.getID()) return 2;
            return -1;
        }
    }

    // -----------------------------------------------------------------------
    // Logging
    // -----------------------------------------------------------------------
    private static void log(String msg) {
        System.out.println("[GameServer] " + msg);
    }
}