package com.mygame.tactics.server;


import com.mygame.tactics.network.KryoRegistrar;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
import com.esotericsoftware.kryonet.Connection;
import com.mygame.tactics.Action;
import com.mygame.tactics.BoardConfig;
import com.mygame.tactics.Character;
import com.mygame.tactics.EngineEvent;
import com.mygame.tactics.GameEngine;
import com.mygame.tactics.GameState;
import com.mygame.tactics.NetworkAction;
import com.mygame.tactics.NetworkMessage;
import com.mygame.tactics.characters.Aevan;
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

    private static final BoardConfig.BoardType[] BOARD_TYPES = BoardConfig.BoardType.values();
    private static final Random RANDOM = new Random();

    private final Server     kryoServer;
    private final GameEngine engine;

    // Maps a connection ID to the room it belongs to
    private final Map<Integer, GameRoom> connectionToRoom = new HashMap<>();

    // Maps a gameId to its room
    private final Map<String, GameRoom> rooms = new HashMap<>();

    // Waiting player and their ranked preference (set when JoinQueueAction arrives)
    private Connection waitingPlayer      = null;
    private boolean    waitingPlayerRanked = false;

    public GameServer() {
        kryoServer = new Server(65536, 65536);
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
            waitingPlayer = connection;
            System.out.println("Player " + connection.getID() + " is waiting for an opponent.");
        } else {
            // Second player — create a room using the waiting player's ranked preference
            String gameId = UUID.randomUUID().toString();
            GameRoom room = new GameRoom(gameId, waitingPlayer, connection, waitingPlayerRanked);
            rooms.put(gameId, room);
            connectionToRoom.put(waitingPlayer.getID(), room);
            connectionToRoom.put(connection.getID(), room);

            waitingPlayer       = null;
            waitingPlayerRanked = false;

            System.out.println("Game room created: " + gameId + " (ranked=" + room.isRanked + ")");

            // Notify both players which team they are
            room.player1.sendTCP(NetworkMessage.roomJoined(gameId, 1, room.isRanked));
            room.player2.sendTCP(NetworkMessage.roomJoined(gameId, 2, room.isRanked));

            // Send initial draft state so both clients have the authoritative pool
            NetworkMessage draftStart = NetworkMessage.draftUpdate(
                    gameId, room.pickingTeam, room.draftPool,
                    room.team1Picks, room.team2Picks);
            room.player1.sendTCP(draftStart);
            room.player2.sendTCP(draftStart);
        }
    }

    private synchronized void onPlayerDisconnected(Connection connection) {
        System.out.println("Player disconnected: " + connection.getID());

        if (waitingPlayer != null && waitingPlayer.getID() == connection.getID()) {
            waitingPlayer       = null;
            waitingPlayerRanked = false;
        }

        GameRoom room = connectionToRoom.remove(connection.getID());
        if (room != null) {
            Connection other = (room.player1.getID() == connection.getID())
                    ? room.player2 : room.player1;
            if (other.isConnected()) {
                other.sendTCP(NetworkMessage.opponentDisconnected(room.gameId));
            }
            connectionToRoom.remove(other.getID());
            rooms.remove(room.gameId);
            System.out.println("Room " + room.gameId + " closed.");
        }
    }

    // -----------------------------------------------------------------------
    // Action processing
    // -----------------------------------------------------------------------

    private synchronized void onActionReceived(Connection connection, NetworkAction na) {
        GameRoom room = connectionToRoom.get(connection.getID());

        // Pre-room: capture the ranked preference sent with JoinQueueAction
        if (room == null) {
            if (na.action instanceof Action.JoinQueueAction) {
                waitingPlayerRanked = ((Action.JoinQueueAction) na.action).ranked;
                System.out.println("Player " + connection.getID()
                        + " queued as " + (waitingPlayerRanked ? "RANKED" : "CASUAL"));
            }
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

        Action action = na.action;

        // --- Draft phase actions (handled before GameState is initialized) ---

        if (action instanceof Action.DraftPickAction) {
            handleDraftPick(connection, room, (Action.DraftPickAction) action);
            return;
        }

        if (action instanceof Action.BoardChoiceAction) {
            handleBoardChoice(connection, room, (Action.BoardChoiceAction) action);
            return;
        }

        if (action instanceof Action.RequestDraftStateAction) {
            handleRequestDraftState(connection, room);
            return;
        }

        if (action instanceof Action.JoinQueueAction) {
            return; // Already matched; nothing more to do
        }

        // --- Battle/pregame phase actions — require initialized GameState ---

        if (room.state == null) {
            connection.sendTCP(NetworkMessage.rejected(na.gameId, "Game not started yet."));
            return;
        }

        // Validate turn order BEFORE processing — activeUnit changes after a deploy,
        // so checking it afterward would produce false rejections for pre-game deploys.
        if (room.state.activeUnit != null && na.playerId != room.state.activeUnit.team) {
            connection.sendTCP(NetworkMessage.rejected(na.gameId, "Not your turn."));
            return;
        }

        Array<EngineEvent> events = engine.processNetwork(room.state, na);

        if (room.state.isGameOver()) {
            handleGameOver(room);
        } else {
            NetworkMessage result = NetworkMessage.actionResult(room.gameId, room.state, events);
            room.player1.sendTCP(result);
            room.player2.sendTCP(result);
        }

        System.out.println("Action processed in room " + room.gameId
                + " by player " + na.playerId);
    }

    // -----------------------------------------------------------------------
    // Draft handling
    // -----------------------------------------------------------------------

    private void handleDraftPick(Connection connection, GameRoom room,
                                  Action.DraftPickAction action) {
        int playerId = (connection.getID() == room.player1.getID()) ? 1 : 2;

        if (playerId != room.pickingTeam) {
            connection.sendTCP(NetworkMessage.rejected(room.gameId, "Not your draft turn."));
            return;
        }

        if (!room.draftPool.contains(action.characterName, false)) {
            connection.sendTCP(NetworkMessage.rejected(room.gameId, "Character not available."));
            return;
        }

        room.draftPool.removeValue(action.characterName, false);
        if (room.pickingTeam == 1) room.team1Picks.add(action.characterName);
        else                       room.team2Picks.add(action.characterName);
        room.picksMade++;
        room.pickingTeam = (room.pickingTeam == 1) ? 2 : 1;

        boolean draftComplete = (room.picksMade == GameRoom.PICKS_PER_TEAM * 2);

        System.out.println("Draft pick in room " + room.gameId
                + ": " + action.characterName + " → team " + playerId
                + (draftComplete ? " (DRAFT COMPLETE)" : ""));

        if (draftComplete) {
            // Broadcast DRAFT_COMPLETE so clients can display the final rosters
            NetworkMessage completeMsg = NetworkMessage.draftComplete(
                    room.gameId, room.team1Picks, room.team2Picks);
            room.player1.sendTCP(completeMsg);
            room.player2.sendTCP(completeMsg);

            if (room.isRanked) {
                // Ranked: server auto-picks a random board — clients are waiting for ACTION_RESULT
                startBattleWithRandomBoard(room);
            }
            // Casual: wait for BoardChoiceAction from team 1
        } else {
            NetworkMessage update = NetworkMessage.draftUpdate(room.gameId, room.pickingTeam,
                    room.draftPool, room.team1Picks, room.team2Picks);
            room.player1.sendTCP(update);
            room.player2.sendTCP(update);
        }
    }

    private void handleBoardChoice(Connection connection, GameRoom room,
                                    Action.BoardChoiceAction action) {
        int playerId = (connection.getID() == room.player1.getID()) ? 1 : 2;

        if (playerId != 1) {
            connection.sendTCP(NetworkMessage.rejected(room.gameId, "Only Team 1 chooses the board."));
            return;
        }

        if (room.state != null) {
            connection.sendTCP(NetworkMessage.rejected(room.gameId, "Battle already started."));
            return;
        }

        startBattle(room, boardConfigFor(action.boardType));
        System.out.println("Battle started in room " + room.gameId + " on board " + action.boardType);
    }

    private void handleRequestDraftState(Connection connection, GameRoom room) {
        NetworkMessage msg = NetworkMessage.draftUpdate(
                room.gameId, room.pickingTeam, room.draftPool,
                room.team1Picks, room.team2Picks);
        connection.sendTCP(msg);
    }

    // -----------------------------------------------------------------------
    // Battle startup
    // -----------------------------------------------------------------------

    /** Picks a random board and starts the battle. Used for ranked mode. */
    private void startBattleWithRandomBoard(GameRoom room) {
        BoardConfig.BoardType chosen = BOARD_TYPES[RANDOM.nextInt(BOARD_TYPES.length)];
        System.out.println("Ranked auto-selected board: " + chosen + " in room " + room.gameId);
        startBattle(room, boardConfigFor(chosen));
    }

    /** Builds GameState from the room's picks and the given config, broadcasts ACTION_RESULT. */
    private void startBattle(GameRoom room, BoardConfig config) {
        Array<Character> team1 = new Array<>();
        Array<Character> team2 = new Array<>();
        for (String name : room.team1Picks) {
            Character c = buildCharacter(name);
            if (c != null) team1.add(c);
        }
        for (String name : room.team2Picks) {
            Character c = buildCharacter(name);
            if (c != null) team2.add(c);
        }

        room.state = new GameState(team1, team2, config);
        room.state.engine = engine;

        Array<EngineEvent> events = engine.initialize(room.state);

        NetworkMessage result = NetworkMessage.actionResult(room.gameId, room.state, events);
        room.player1.sendTCP(result);
        room.player2.sendTCP(result);
    }

    // -----------------------------------------------------------------------
    // Game-over handling
    // -----------------------------------------------------------------------

    private void handleGameOver(GameRoom room) {
        if (!room.isRanked) {
            room.player1.sendTCP(NetworkMessage.gameOver(room.gameId, room.state));
            room.player2.sendTCP(NetworkMessage.gameOver(room.gameId, room.state));
            return;
        }

        // Ranked: track round wins
        int winner = room.state.winnerTeam;
        if (winner == 1) room.team1RoundWins++;
        else if (winner == 2) room.team2RoundWins++;

        int completedRound = room.roundNumber;
        boolean matchOver = room.team1RoundWins >= 2
                || room.team2RoundWins >= 2
                || room.roundNumber >= 3;

        System.out.println("Ranked round " + completedRound + " ended in room " + room.gameId
                + ". Winner: team " + winner
                + ". Score: " + room.team1RoundWins + "-" + room.team2RoundWins
                + (matchOver ? " — MATCH OVER" : ""));

        if (matchOver) {
            room.player1.sendTCP(NetworkMessage.rankedMatchOver(
                    room.gameId, room.team1RoundWins, room.team2RoundWins));
            room.player2.sendTCP(NetworkMessage.rankedMatchOver(
                    room.gameId, room.team1RoundWins, room.team2RoundWins));
        } else {
            // Record all characters used this round so they're excluded next round
            for (String name : room.team1Picks) room.usedCharacters.add(name);
            for (String name : room.team2Picks) room.usedCharacters.add(name);

            // Build next round's pool (full pool minus all previously used characters)
            Array<String> nextPool = new Array<>();
            for (String name : GameRoom.ALL_CHARACTER_NAMES) {
                if (!room.usedCharacters.contains(name, false)) nextPool.add(name);
            }

            // Reset draft state for next round
            room.roundNumber++;
            room.team1Picks.clear();
            room.team2Picks.clear();
            room.pickingTeam = 1;
            room.picksMade   = 0;
            room.draftPool.clear();
            room.draftPool.addAll(nextPool);
            room.state = null;

            NetworkMessage roundOver = NetworkMessage.rankedRoundOver(
                    room.gameId, completedRound, winner,
                    room.team1RoundWins, room.team2RoundWins, nextPool);
            room.player1.sendTCP(roundOver);
            room.player2.sendTCP(roundOver);
        }
    }

    // -----------------------------------------------------------------------
    // Character factory (headless — no texture)
    // -----------------------------------------------------------------------

    private static Character buildCharacter(String name) {
        switch (name) {
            case "Aaron":      return new Aaron(null);
            case "Aevan":      return new Aevan(null);
            case "Anna":       return new Anna(null);
            case "Ben":        return new Ben(null);
            case "Billy":      return new Billy(null);
            case "Brad":       return new Brad(null);
            case "Emily":      return new Emily(null);
            case "Evan":       return new Evan(null);
            case "Ghia":       return new Ghia(null);
            case "GuardTower": return new GuardTower(null);
            case "Hunter":     return new Hunter(null);
            case "Jaxon":      return new Jaxon(null);
            case "Lark":       return new Lark(null);
            case "Luke":       return new Luke(null);
            case "Mason":      return new Mason(null);
            case "Maxx":       return new Maxx(null);
            case "Nathan":     return new Nathan(null);
            case "Sean":       return new Sean(null);
            case "Snowguard":  return new Snowguard(null);
            case "Speen":      return new Speen(null);
            case "Stoneguard": return new Stoneguard(null);
            case "Thomas":     return new Thomas(null);
            case "Tyler":      return new Tyler(null);
            case "Weirdguard": return new Weirdguard(null);
            default:
                System.out.println("WARNING: unknown character name: " + name);
                return null;
        }
    }

    private static BoardConfig boardConfigFor(BoardConfig.BoardType type) {
        switch (type) {
            case WIND:   return BoardConfig.wind();
            case DESERT: return BoardConfig.desert();
            default:     return BoardConfig.forest();
        }
    }

    // -----------------------------------------------------------------------
    // Inner class — GameRoom
    // -----------------------------------------------------------------------

    public static class GameRoom {
        public static final int      PICKS_PER_TEAM      = 4;
        public static final String[] ALL_CHARACTER_NAMES = {
            "Hunter", "Sean", "Jaxon", "Evan", "Billy", "Aaron", "Speen", "Mason",
            "Lark", "Nathan", "Luke", "Brad", "GuardTower", "Weirdguard", "Stoneguard",
            "Snowguard", "Tyler", "Anna", "Emily", "Thomas", "Ghia", "Maxx", "Ben", "Aevan"
        };

        public final String     gameId;
        public final Connection player1;
        public final Connection player2;
        public final boolean    isRanked;

        // Draft state
        public final Array<String> draftPool      = new Array<>();
        public final Array<String> team1Picks     = new Array<>();
        public final Array<String> team2Picks     = new Array<>();
        public final Array<String> usedCharacters = new Array<>(); // locked out in future rounds
        public int pickingTeam = 1;
        public int picksMade   = 0;

        // Ranked round tracking
        public int roundNumber    = 1;
        public int team1RoundWins = 0;
        public int team2RoundWins = 0;

        // Battle state — null until battle starts, reset to null between rounds
        public GameState state;

        public GameRoom(String gameId, Connection player1, Connection player2, boolean isRanked) {
            this.gameId   = gameId;
            this.player1  = player1;
            this.player2  = player2;
            this.isRanked = isRanked;
            for (String name : ALL_CHARACTER_NAMES) draftPool.add(name);
        }
    }
}
