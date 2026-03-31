package com.mygame.tactics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.mygame.tactics.network.NetworkClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Playable world screen.
 *
 * Player moves tile-by-tile with WASD / arrow keys.
 * Press E or Space to interact with adjacent interactable tiles.
 *   building_fountain → ranked matchmaking (OnlineScreen)
 * Escape returns to the main menu.
 */
public class WorldScreen implements Screen {

    private static final float TILE_SZ  = 64f;
    private static final float MOVE_CD  = 0.13f; // seconds between steps

    // ---- Core ----
    private final Main               game;
    private final OrthographicCamera worldCam;
    private final OrthographicCamera hudCam;
    private final FitViewport        viewport;
    private final Texture            whitePixel;
    private final GlyphLayout        layout = new GlyphLayout();

    // ---- Textures ----
    private final Map<String, Texture> textures = new LinkedHashMap<>();

    // ---- World state ----
    private final WorldArea area;
    private int   playerX, playerY;
    private float moveCd = 0f;

    // Smoothed render position (lerps toward playerX/Y * TILE_SZ each frame)
    private float visualX, visualY;

    // Current interactable tile the player is adjacent to (or on), and its map position
    private WorldTile nearbyTile = null;
    private int       nearbyTileX = -1, nearbyTileY = -1;

    // Return-to-previous-area support
    private final WorldArea       returnArea;   // null if this is the top-level map
    private final int             returnX, returnY;

    // Player appearance (null = use defaults)
    private final PlayerAppearance appearance;

    // Online lobby: null for offline/single-player
    private final NetworkClient client;

    // Other players in the lobby: username → {x, y}
    private final Map<String, int[]> otherPlayers = new LinkedHashMap<>();

    // True once the player has sent JoinQueueAction (fountain → waiting for match)
    private boolean searchingForMatch = false;

    // NPC rendering
    private float npcBobTime = 0f;
    private final Map<String, Texture> npcPortraits = new LinkedHashMap<>();

    // Current nearby interactable NPC (set each frame by updateNearby)
    private WorldArea.WorldNpc nearbyNpc = null;

    // ---- Constructors ----

    public WorldScreen(Main game, WorldArea area) {
        this(game, area, null, -1, -1, area.spawnX, area.spawnY, null, null);
    }

    public WorldScreen(Main game, WorldArea area, PlayerAppearance appearance) {
        this(game, area, null, -1, -1, area.spawnX, area.spawnY, appearance, null);
    }

    /** Online lobby constructor — connects player to live world. */
    public WorldScreen(Main game, WorldArea area, PlayerAppearance appearance, NetworkClient client) {
        this(game, area, null, -1, -1, area.spawnX, area.spawnY, appearance, client);
    }

    /** Cave-entry — spawns near the top of the new area. */
    public WorldScreen(Main game, WorldArea area, WorldArea returnArea, int returnX, int returnY,
                       PlayerAppearance appearance, NetworkClient client) {
        this(game, area, returnArea, returnX, returnY, area.width / 2, area.height - 1, appearance, client);
    }

    /** Returns to a saved position in an area — used after cave combat. */
    public WorldScreen(Main game, WorldArea area, int spawnX, int spawnY,
                       PlayerAppearance appearance, NetworkClient client) {
        this(game, area, null, -1, -1, spawnX, spawnY, appearance, client);
    }

    /** Full constructor. spawnX/Y = -1 → auto-find nearest walkable to centre. */
    private WorldScreen(Main game, WorldArea area,
                        WorldArea returnArea, int returnX, int returnY,
                        int spawnX, int spawnY, PlayerAppearance appearance,
                        NetworkClient client) {
        this.game       = game;
        this.area       = area;
        this.returnArea = returnArea;
        this.returnX    = returnX;
        this.returnY    = returnY;
        this.appearance = appearance;
        this.client     = client;

        worldCam = new OrthographicCamera();
        hudCam   = new OrthographicCamera();
        viewport = new FitViewport(1280, 720, worldCam);
        hudCam.setToOrtho(false, 1280, 720);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE); pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();

        loadTextures();
        fixupAnchors();
        area.applyOverrides(Main.flags);
        spawnPlayer(spawnX >= 0 ? spawnX : area.width  / 2,
                    spawnY >= 0 ? spawnY : area.height / 2);
        visualX = playerX * TILE_SZ;
        visualY = playerY * TILE_SZ;
        snapCamera();

        if (client != null) {
            client.setListener(new NetworkClient.MessageListener() {
                @Override public void onConnected()    {}
                @Override public void onDisconnected() {}
                @Override
                public void onMessage(NetworkMessage msg) {
                    Gdx.app.postRunnable(() -> handleNetworkMessage(msg));
                }
            });
            // Send lobby join with full appearance so others can render us correctly
            PlayerAppearance ap = appearance != null ? appearance : new PlayerAppearance();
            client.sendLobbyJoin(ap.username, ap.modelType,
                    ap.skinColorIdx, ap.shirtColorIdx, ap.pantsColorIdx);
            // Send initial position so others see us at spawn, not server default
            client.sendPlayerMove(playerX, playerY);
        }
    }

    // ---- Texture loading ----

    private void loadTextures() {
        String[] dirs = {".", "assets", "../assets", "../../assets"};
        for (String d : dirs) {
            FileHandle dir = Gdx.files.local(d);
            if (!dir.exists() || !dir.isDirectory()) continue;
            for (FileHandle f : dir.list(".png")) {
                String id = f.nameWithoutExtension();
                if (!id.startsWith("tile_") && !id.startsWith("map_") && !id.startsWith("building_")) continue;
                if (!textures.containsKey(id)) {
                    try { textures.put(id, new Texture(f)); } catch (Exception ignored) {}
                }
            }
            if (!textures.isEmpty()) break;
        }
    }

    // ---- Anchor fixup ----

    /**
     * Fixes isAnchor for single-column objects (trees, rocks, single-tile walls, etc.)
     * by scanning each column for vertical runs of the same objectId and placing isAnchor=true
     * every tileH tiles within the run. This correctly separates adjacent stacked objects
     * of the same type even when the saved flag is wrong or missing.
     */
    private void fixupAnchors() {
        for (int tx = 0; tx < area.width; tx++) {
            int runStart = 0;
            String runId  = null;
            for (int ty = 0; ty <= area.height; ty++) {
                String id = (ty < area.height) ? area.tiles[tx][ty].objectId : null;
                if (id != null && id.equals(runId)) continue;
                if (runId != null) {
                    Texture tex = textures.get(runId);
                    if (tex != null && Math.max(1, tex.getWidth() / 64) == 1) {
                        int th = Math.max(1, tex.getHeight() / 64);
                        for (int r = runStart; r < ty; r += th)
                            area.tiles[tx][r].isAnchor = true;
                    }
                }
                runId    = id;
                runStart = ty;
            }
        }
    }

    // ---- Spawn ----

    /** Find a walkable tile nearest to (prefX, prefY). */
    private void spawnPlayer(int prefX, int prefY) {
        int cx = prefX, cy = prefY;
        int maxR = Math.max(area.width, area.height);
        for (int r = 0; r <= maxR; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -r; dy <= r; dy++) {
                    if (Math.abs(dx) != r && Math.abs(dy) != r) continue; // shell only
                    int x = cx + dx, y = cy + dy;
                    if (inBounds(x, y) && area.tiles[x][y].walkable) {
                        playerX = x; playerY = y; return;
                    }
                }
            }
        }
    }

    // ---- Camera ----

    private void snapCamera() {
        worldCam.position.set(visualX + TILE_SZ / 2f,
                              visualY + TILE_SZ / 2f, 0);
        clampCam();
        worldCam.update();
    }

    private void clampCam() {
        float mw = area.width  * TILE_SZ;
        float mh = area.height * TILE_SZ;
        worldCam.position.x = mw > 1280f ? Math.max(640f, Math.min(mw - 640f, worldCam.position.x)) : mw / 2f;
        worldCam.position.y = mh > 720f  ? Math.max(360f, Math.min(mh - 360f, worldCam.position.y)) : mh / 2f;
    }

    // ---- Screen lifecycle ----

    @Override public void show()   {}
    @Override public void hide()   {}
    @Override public void pause()  {}
    @Override public void resume() {}

    @Override
    public void resize(int w, int h) {
        viewport.update(w, h);
        hudCam.setToOrtho(false, 1280, 720);
    }

    // ---- Render ----

    @Override
    public void render(float delta) {
        moveCd    -= delta;
        npcBobTime += delta;
        handleInput();
        updateNearby();

        // Smooth player position
        float targetX = playerX * TILE_SZ;
        float targetY = playerY * TILE_SZ;
        float lerp = Math.min(1f, delta * 16f);
        visualX += (targetX - visualX) * lerp;
        visualY += (targetY - visualY) * lerp;

        // Smooth camera follow
        worldCam.position.x += (visualX + TILE_SZ / 2f - worldCam.position.x) * Math.min(1f, delta * 8f);
        worldCam.position.y += (visualY + TILE_SZ / 2f - worldCam.position.y) * Math.min(1f, delta * 8f);
        clampCam();
        worldCam.update();
        hudCam.update();

        ScreenUtils.clear(0.05f, 0.05f, 0.08f, 1f);

        // World layer
        game.batch.setProjectionMatrix(worldCam.combined);
        game.batch.begin();
        drawMap();
        drawNpcs();
        drawOtherPlayers();
        drawPlayer();
        game.batch.end();

        // HUD layer
        game.batch.setProjectionMatrix(hudCam.combined);
        game.batch.begin();
        drawHud();
        game.batch.end();
    }

    // ---- Input ----

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.setScreen(new MenuScreen(game));
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.E) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            interact();
            return;
        }

        if (moveCd > 0) return;

        int dx = 0, dy = 0;
        if      (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP))    dy =  1;
        else if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN))  dy = -1;
        else if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT))  dx = -1;
        else if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) dx =  1;

        if (dx != 0 || dy != 0) {
            int nx = playerX + dx, ny = playerY + dy;
            if (inBounds(nx, ny) && area.tiles[nx][ny].walkable) {
                playerX = nx;
                playerY = ny;
                moveCd  = MOVE_CD;
                if (client != null) client.sendPlayerMove(playerX, playerY);
            }
        }
    }

    // ---- Interaction ----

    private void updateNearby() {
        nearbyTile  = null;
        nearbyTileX = -1;
        nearbyTileY = -1;
        nearbyNpc   = null;

        // Check for interactable NPCs adjacent to or on the player's tile
        for (WorldArea.WorldNpc npc : area.npcs) {
            if (!npc.interactable) continue;
            if (npc.winFlag != null && Main.flags.is(npc.winFlag)) continue;
            int dx = Math.abs(npc.x - playerX), dy = Math.abs(npc.y - playerY);
            if (dx + dy <= 1) { nearbyNpc = npc; return; }
        }

        // Check for interactable tiles
        int[][] checks = {
            {playerX,     playerY},
            {playerX + 1, playerY},
            {playerX - 1, playerY},
            {playerX,     playerY + 1},
            {playerX,     playerY - 1}
        };
        for (int[] pos : checks) {
            if (!inBounds(pos[0], pos[1])) continue;
            WorldTile t = area.tiles[pos[0]][pos[1]];
            if (t.interactable) { nearbyTile = t; nearbyTileX = pos[0]; nearbyTileY = pos[1]; return; }
        }
    }

    private void interact() {
        if (nearbyNpc != null) {
            handleNpcInteract(nearbyNpc);
            return;
        }
        if (nearbyTile == null) return;

        // Special trigger: exit to online character creation
        if ("online_exit".equals(nearbyTile.triggerAreaId)) {
            game.setScreen(new CharacterCreationScreen(game));
            return;
        }

        // Bottom-row exit tile: return to the previous area, spawning at the stored position
        if (returnArea != null && nearbyTileY == 0) {
            game.setScreen(new WorldScreen(game, returnArea, null, -1, -1, returnX, returnY, appearance, client));
            return;
        }

        if (nearbyTile.objectId == null) return;
        String obj = nearbyTile.objectId;

        if (obj.startsWith("building_fountain")) {
            if (client != null) {
                // Online mode — join the ranked queue directly
                if (!searchingForMatch) {
                    searchingForMatch = true;
                    client.joinQueue();
                }
            } else {
                // Offline mode — open the online screen to connect
                game.setScreen(new OnlineScreen(game, new NetworkClient(), appearance != null ? appearance : new PlayerAppearance()));
            }
        }
        if (obj.equals("building_cave2")) {
            playCutsceneThen(nearbyTile, () -> enterCombatCave("combat_cave2.txt"));
            return;
        }
        if (obj.startsWith("building_cave")) {
            playCutsceneThen(nearbyTile, this::enterCave);
            return;
        }
        // Other buildings: placeholder for future content
    }

    /**
     * Searches common asset directories for a file by name.
     * Checks both the root and a "cutscenes/" subdirectory so files work
     * regardless of whether they're organised into a subfolder.
     * Returns the first FileHandle that exists, or null if not found.
     */
    private FileHandle findAssetFile(String filename) {
        String[] dirs = {".", "assets", "../assets", "../../assets"};
        for (String d : dirs) {
            FileHandle f = Gdx.files.local(d + "/" + filename);
            if (f.exists()) return f;
            f = Gdx.files.local(d + "/cutscenes/" + filename);
            if (f.exists()) return f;
        }
        return null;
    }

    /**
     * If the tile has a triggerAreaId and a matching cutscene script exists,
     * plays the cutscene first then calls action. Otherwise calls action immediately.
     */
    private void playCutsceneThen(WorldTile tile, Runnable action) {
        if (tile.triggerAreaId != null) {
            FileHandle f = findAssetFile(tile.triggerAreaId + ".txt");
            if (f != null) {
                try {
                    CutsceneData data = CutsceneData.load(f);
                    game.setScreen(new CutsceneScreen(game, data, Main.flags, action));
                    return;
                } catch (Exception e) {
                    Gdx.app.error("WorldScreen", "Cutscene load failed: " + e.getMessage());
                }
            }
        }
        action.run(); // no cutscene — go straight to the transition
    }

    private void handleNpcInteract(WorldArea.WorldNpc npc) {
        boolean hasCombat   = npc.combatFile != null && !npc.combatFile.isEmpty();
        boolean alreadyWon  = npc.winFlag != null && Main.flags.is(npc.winFlag);
        boolean hasCutscene = npc.triggerAreaId != null;

        if (hasCombat && alreadyWon) return;  // already beaten, nothing to do

        if (hasCutscene) {
            FileHandle cutsceneFile = findAssetFile(npc.triggerAreaId + ".txt");
            if (cutsceneFile != null) {
                try {
                    CutsceneData data = CutsceneData.load(cutsceneFile);
                    Runnable afterCutscene = hasCombat ? () -> launchNpcCombat(npc) : () -> {};
                    game.setScreen(new CutsceneScreen(game, data, Main.flags, afterCutscene));
                    return;
                } catch (Exception e) {
                    Gdx.app.error("WorldScreen", "NPC cutscene load failed: " + e.getMessage());
                }
            }
        }

        // No cutscene (or file not found) — go straight to combat if applicable
        if (hasCombat) launchNpcCombat(npc);
    }

    /**
     * Launches a DraftScreen combat against the board specified by npc.combatFile.
     * On win, sets npc.winFlag=1, checks if all combat NPCs in the area are beaten,
     * and returns to this area (reloading from disk so flag-based tile overrides apply).
     */
    private void launchNpcCombat(WorldArea.WorldNpc npc) {
        final WorldArea        savedArea   = area;
        final int              savedX      = playerX;
        final int              savedY      = playerY;
        final PlayerAppearance savedApp    = appearance;
        final NetworkClient    savedClient = client;
        final String           winFlagKey  = npc.winFlag;

        Runnable returnAction = () -> {
            if (winFlagKey != null && !winFlagKey.isEmpty()) {
                Main.flags.set(winFlagKey, 1);
            }
            checkAreaComplete();
            // Reload the area from disk so flag overrides (e.g. tree removal) take effect
            WorldArea freshArea = savedArea;
            if (savedArea.sourceFile != null) {
                try { freshArea = WorldArea.load(Gdx.files.absolute(savedArea.sourceFile)); }
                catch (Exception ignored) {}
            }
            freshArea.applyOverrides(Main.flags);
            game.setScreen(new WorldScreen(game, freshArea, savedX, savedY, savedApp, savedClient));
        };

        String[] dirs = {".", "assets", "../assets", "../../assets"};
        for (String d : dirs) {
            FileHandle f = Gdx.files.local(d + "/" + npc.combatFile);
            if (f.exists()) {
                CombatBoardLoader.Result result = CombatBoardLoader.load(f);
                if (result != null)
                    game.setScreen(new DraftScreen(game, result.team2, result.config, returnAction));
                return;
            }
        }
        Gdx.app.error("WorldScreen", "NPC combat file not found: " + npc.combatFile);
    }

    /**
     * Checks whether every combat NPC (one with a combatFile) in the current area
     * has had their winFlag set. If so, marks the area complete via
     * "<areaId>_complete = 1" in PlayerFlags.
     */
    private void checkAreaComplete() {
        boolean hasCombatNpc = false;
        for (WorldArea.WorldNpc npc : area.npcs) {
            if (npc.combatFile == null || npc.combatFile.isEmpty()) continue;
            if (npc.winFlag    == null || npc.winFlag.isEmpty())    continue;
            hasCombatNpc = true;
            if (!Main.flags.is(npc.winFlag)) return; // still at least one unbeaten
        }
        if (hasCombatNpc) {
            Main.flags.set(area.areaId + "_complete", 1);
        }
    }

    private void enterCave() {
        String[] dirs = {".", "assets", "../assets", "../../assets"};
        for (String d : dirs) {
            FileHandle dir = Gdx.files.local(d);
            if (!dir.exists() || !dir.isDirectory()) continue;
            FileHandle f = Gdx.files.local(d + "/Cave1.txt");
            if (f.exists()) {
                try {
                    WorldArea cave = WorldArea.load(f);
                    game.setScreen(new WorldScreen(game, cave, area, playerX, playerY, appearance, client));
                } catch (Exception ignored) {}
                return;
            }
        }
    }

    private void enterCombatCave(String filename) {
        final WorldArea        savedArea   = area;
        final int              savedX      = playerX;
        final int              savedY      = playerY;
        final PlayerAppearance savedApp    = appearance;
        final NetworkClient    savedClient = client;
        Runnable returnAction = () -> {
            // Reload the area from disk so flag-based tile overrides are re-evaluated
            WorldArea freshArea = savedArea;
            if (savedArea.sourceFile != null) {
                try { freshArea = WorldArea.load(Gdx.files.absolute(savedArea.sourceFile)); }
                catch (Exception ignored) {}
            }
            game.setScreen(new WorldScreen(game, freshArea, savedX, savedY, savedApp, savedClient));
        };

        String[] dirs = {".", "assets", "../assets", "../../assets"};
        for (String d : dirs) {
            FileHandle f = Gdx.files.local(d + "/" + filename);
            if (f.exists()) {
                CombatBoardLoader.Result result = CombatBoardLoader.load(f);
                if (result != null)
                    game.setScreen(new DraftScreen(game, result.team2, result.config, returnAction));
                return;
            }
        }
    }

    // ---- Network message handling ----

    private void handleNetworkMessage(NetworkMessage msg) {
        switch (msg.type) {
            case WORLD_STATE:
                otherPlayers.clear();
                if (msg.lobbyUsernames != null) {
                    String myName = appearance != null ? appearance.username : "";
                    for (int i = 0; i < msg.lobbyUsernames.length; i++) {
                        String name = msg.lobbyUsernames[i];
                        if (name != null && !name.equals(myName)) {
                            int model = msg.lobbyModelTypes  != null ? msg.lobbyModelTypes[i]  : 0;
                            int skin  = msg.lobbySkinIdxArr  != null ? msg.lobbySkinIdxArr[i]  : 0;
                            int shirt = msg.lobbyShirtIdxArr != null ? msg.lobbyShirtIdxArr[i] : 0;
                            int pant  = msg.lobbyPantsIdxArr != null ? msg.lobbyPantsIdxArr[i] : 0;
                            otherPlayers.put(name, new int[]{
                                msg.lobbyXArr[i], msg.lobbyYArr[i], model, skin, shirt, pant});
                        }
                    }
                }
                break;
            case PLAYER_JOINED:
                if (msg.lobbyUsername != null) {
                    otherPlayers.put(msg.lobbyUsername, new int[]{
                        msg.lobbyX, msg.lobbyY,
                        msg.lobbyModelType, msg.lobbySkinColorIdx,
                        msg.lobbyShirtColorIdx, msg.lobbyPantsColorIdx});
                }
                break;
            case PLAYER_MOVED:
                if (msg.lobbyUsername != null) {
                    int[] data = otherPlayers.get(msg.lobbyUsername);
                    if (data != null) { data[0] = msg.lobbyX; data[1] = msg.lobbyY; }
                    else {
                        // Received a move before PLAYER_JOINED — add with default appearance
                        otherPlayers.put(msg.lobbyUsername, new int[]{msg.lobbyX, msg.lobbyY, 0, 0, 0, 0});
                    }
                }
                break;
            case PLAYER_LEFT:
                if (msg.lobbyUsername != null) {
                    otherPlayers.remove(msg.lobbyUsername);
                }
                break;
            case ROOM_JOINED: {
                // Matched — go to DraftScreen
                final int     assignedTeam = msg.assignedTeam;
                final String  myName       = appearance != null ? appearance.username : "Player";
                final String[] names       = assignedTeam == 1
                        ? new String[]{myName, "Opponent"}
                        : new String[]{"Opponent", myName};
                game.setScreen(new DraftScreen(game, client, assignedTeam, true, 1, 0, 0, null, names));
                break;
            }
            default:
                break;
        }
    }

    // ---- Draw: map ----

    private void drawMap() {
        float camL = worldCam.position.x - 640f;
        float camB = worldCam.position.y - 360f;
        int x0 = Math.max(0, (int)(camL / TILE_SZ));
        int y0 = Math.max(0, (int)(camB / TILE_SZ));
        int x1 = Math.min(area.width,  x0 + (int)(1280f / TILE_SZ) + 2);
        int y1 = Math.min(area.height, y0 + (int)(720f  / TILE_SZ) + 2);

        // Pass 1: backgrounds
        for (int tx = x0; tx < x1; tx++) {
            for (int ty = y0; ty < y1; ty++) {
                float dx = tx * TILE_SZ, dy = ty * TILE_SZ;
                boolean even = (tx + ty) % 2 == 0;
                game.batch.setColor(even ? 0.14f : 0.18f, even ? 0.14f : 0.18f,
                                    even ? 0.18f : 0.22f, 1f);
                game.batch.draw(whitePixel, dx, dy, TILE_SZ, TILE_SZ);
                WorldTile tile = area.tiles[tx][ty];
                if (tile.backgroundId != null) {
                    Texture bg = textures.get(tile.backgroundId);
                    if (bg != null) {
                        game.batch.setColor(Color.WHITE);
                        game.batch.draw(bg, dx, dy, TILE_SZ, TILE_SZ);
                    }
                }
            }
        }

        // Pass 2: objects — expanded range so buildings near/past the edge still render.
        int buf = 10;
        int ox0 = Math.max(0, x0 - buf), oy0 = Math.max(0, y0 - buf);
        int ox1 = Math.min(area.width, x1 + buf), oy1 = Math.min(area.height, y1 + buf);
        for (int tx = ox0; tx < ox1; tx++) {
            for (int ty = oy0; ty < oy1; ty++) {
                WorldTile tile = area.tiles[tx][ty];
                if (tile.objectId == null) continue;
                Texture obj = textures.get(tile.objectId);
                if (obj == null) continue;
                // Scan DOWN first to find the anchor row, then LEFT to find the anchor column.
                // This prevents upper-row tiles from scanning past adjacent object boundaries.
                int ay = ty;
                while (ay > 0 && !area.tiles[tx][ay].isAnchor
                        && tile.objectId.equals(area.tiles[tx][ay - 1].objectId)) ay--;
                int ax = tx;
                while (ax > 0 && !area.tiles[ax][ay].isAnchor
                        && tile.objectId.equals(area.tiles[ax - 1][ay].objectId)) ax--;
                int tileH = Math.max(1, obj.getHeight() / 64);
                int srcX = (tx - ax) * 64;
                int srcY = (tileH - 1 - (ty - ay)) * 64;
                game.batch.setColor(Color.WHITE);
                game.batch.draw(obj,
                        tx * TILE_SZ, ty * TILE_SZ, TILE_SZ, TILE_SZ,
                        srcX, srcY, 64, 64, false, false);
            }
        }
    }

    // ---- Draw: NPCs ----

    private void drawNpcs() {
        // Slow bob: period ~2.5 s, amplitude ±5 px
        float bob = (float) Math.sin(npcBobTime * Math.PI * 0.8) * 5f;
        float ts  = TILE_SZ;

        for (WorldArea.WorldNpc npc : area.npcs) {
            if (npc.winFlag != null && Main.flags.is(npc.winFlag)) continue;
            float nx = npc.x * ts;
            float ny = npc.y * ts + bob;

            // Shadow at the tile base (doesn't bob)
            game.batch.setColor(0f, 0f, 0f, 0.28f);
            game.batch.draw(whitePixel, npc.x * ts + ts * 0.10f, npc.y * ts + ts * 0.02f, ts * 0.80f, ts * 0.06f);

            Texture portrait = getPortrait(npc.charName);
            if (portrait != null) {
                float pw = ts * 0.78f, ph = ts * 0.78f;
                game.batch.setColor(Color.WHITE);
                game.batch.draw(portrait, nx + (ts - pw) / 2f, ny + ts * 0.12f, pw, ph);
            } else {
                // Placeholder humanoid, color keyed to charName
                int   hash = npc.charName.hashCode();
                float hr   = ((hash & 0xFF) / 255f) * 0.5f + 0.25f;
                float hg   = (((hash >> 8)  & 0xFF) / 255f) * 0.5f + 0.25f;
                float hb   = (((hash >> 16) & 0xFF) / 255f) * 0.5f + 0.25f;
                // Legs
                game.batch.setColor(hr * 0.5f, hg * 0.5f, hb * 0.5f, 1f);
                game.batch.draw(whitePixel, nx + ts * 0.22f, ny + ts * 0.08f, ts * 0.18f, ts * 0.20f);
                game.batch.draw(whitePixel, nx + ts * 0.60f, ny + ts * 0.08f, ts * 0.18f, ts * 0.20f);
                // Body
                float bodyW = ts * 0.42f, bodyH = ts * 0.28f;
                float bodyX = nx + (ts - bodyW) / 2f, bodyY = ny + ts * 0.26f;
                game.batch.setColor(hr, hg, hb, 1f);
                game.batch.draw(whitePixel, bodyX, bodyY, bodyW, bodyH);
                // Head
                float headSz = ts * 0.30f;
                float headX  = nx + (ts - headSz) / 2f, headY = bodyY + bodyH + ts * 0.02f;
                game.batch.setColor(0.94f, 0.78f, 0.62f, 1f);
                game.batch.draw(whitePixel, headX, headY, headSz, headSz);
            }

            // Name label above the NPC
            float labelY = ny + ts * 0.90f + ts * 0.12f;
            game.font.getData().setScale(0.34f);
            layout.setText(game.font, npc.charName);
            float labelX = nx + ts / 2f - layout.width / 2f;
            game.font.setColor(0f, 0f, 0f, 0.75f);
            game.font.draw(game.batch, npc.charName, labelX + 1f, labelY - 1f);
            game.font.setColor(1f, 0.95f, 0.70f, 1f);
            game.font.draw(game.batch, npc.charName, labelX, labelY);

            // "!" indicator above the label for interactable NPCs
            if (npc.interactable) {
                String mark = "!";
                layout.setText(game.font, mark);
                float mx = nx + ts / 2f - layout.width / 2f;
                float my = labelY + layout.height + 2f;
                game.font.setColor(1f, 0.85f, 0f, 1f);
                game.font.draw(game.batch, mark, mx, my);
            }

            game.font.getData().setScale(1f);
            game.font.setColor(Color.WHITE);
            game.batch.setColor(Color.WHITE);
        }
    }

    /** Lazily loads and caches a character portrait by name (lower-cased .png filename). */
    private Texture getPortrait(String charName) {
        String key = charName.toLowerCase();
        if (npcPortraits.containsKey(key)) return npcPortraits.get(key);
        String[] dirs = {".", "assets", "../assets", "../../assets"};
        for (String d : dirs) {
            try {
                FileHandle f = Gdx.files.local(d + "/" + key + ".png");
                if (f.exists()) {
                    Texture t = new Texture(f);
                    npcPortraits.put(key, t);
                    return t;
                }
            } catch (Exception ignored) {}
        }
        npcPortraits.put(key, null); // cache the miss so we don't retry every frame
        return null;
    }

    // ---- Draw: other players ----

    private void drawOtherPlayers() {
        for (Map.Entry<String, int[]> entry : otherPlayers.entrySet()) {
            String name = entry.getKey();
            int[]  d    = entry.getValue(); // {x, y, model, skin, shirt, pants}
            float  px   = d[0] * TILE_SZ;
            float  py   = d[1] * TILE_SZ;
            float  ts   = TILE_SZ;
            int    model = d[2];
            Color  skin  = PlayerAppearance.SKIN_COLORS   [Math.max(0, Math.min(d[3], PlayerAppearance.SKIN_COLORS.length    - 1))];
            Color  shirt = PlayerAppearance.CLOTHES_COLORS[Math.max(0, Math.min(d[4], PlayerAppearance.CLOTHES_COLORS.length - 1))];
            Color  pants = PlayerAppearance.CLOTHES_COLORS[Math.max(0, Math.min(d[5], PlayerAppearance.CLOTHES_COLORS.length - 1))];

            float headTop;
            if (model == 0) {
                game.batch.setColor(0f, 0f, 0f, 0.25f);
                game.batch.draw(whitePixel, px + ts*0.12f, py + ts*0.02f, ts*0.76f, ts*0.06f);
                game.batch.setColor(pants);
                game.batch.draw(whitePixel, px + ts*0.22f, py + ts*0.08f, ts*0.18f, ts*0.20f);
                game.batch.draw(whitePixel, px + ts*0.60f, py + ts*0.08f, ts*0.18f, ts*0.20f);
                float bodyW = ts*0.42f, bodyH = ts*0.28f;
                float bodyX = px + (ts - bodyW)/2f, bodyY = py + ts*0.26f;
                game.batch.setColor(shirt);
                game.batch.draw(whitePixel, bodyX, bodyY, bodyW, bodyH);
                float headSz = ts*0.30f;
                float headX  = px + (ts - headSz)/2f, headY = bodyY + bodyH + ts*0.02f;
                game.batch.setColor(skin);
                game.batch.draw(whitePixel, headX, headY, headSz, headSz);
                game.batch.setColor(1f, 1f, 1f, 0.18f);
                game.batch.draw(whitePixel, headX, headY + headSz - ts*0.04f, headSz, ts*0.04f);
                headTop = headY + headSz;
            } else {
                game.batch.setColor(0f, 0f, 0f, 0.25f);
                game.batch.draw(whitePixel, px + ts*0.08f, py + ts*0.02f, ts*0.84f, ts*0.06f);
                game.batch.setColor(pants);
                game.batch.draw(whitePixel, px + ts*0.18f, py + ts*0.08f, ts*0.20f, ts*0.18f);
                game.batch.draw(whitePixel, px + ts*0.62f, py + ts*0.08f, ts*0.20f, ts*0.18f);
                float bodyW = ts*0.58f, bodyH = ts*0.24f;
                float bodyX = px + (ts - bodyW)/2f, bodyY = py + ts*0.24f;
                game.batch.setColor(shirt);
                game.batch.draw(whitePixel, bodyX, bodyY, bodyW, bodyH);
                game.batch.draw(whitePixel, bodyX - ts*0.05f, bodyY + bodyH - ts*0.06f, ts*0.10f, ts*0.08f);
                game.batch.draw(whitePixel, bodyX + bodyW - ts*0.05f, bodyY + bodyH - ts*0.06f, ts*0.10f, ts*0.08f);
                float headSz = ts*0.35f;
                float headX  = px + (ts - headSz)/2f, headY = bodyY + bodyH + ts*0.02f;
                game.batch.setColor(skin);
                game.batch.draw(whitePixel, headX, headY, headSz, headSz);
                game.batch.setColor(1f, 1f, 1f, 0.18f);
                game.batch.draw(whitePixel, headX, headY + headSz - ts*0.04f, headSz, ts*0.04f);
                headTop = headY + headSz;
            }

            game.font.getData().setScale(0.38f);
            layout.setText(game.font, name);
            float tx2 = px + ts/2f - layout.width/2f;
            float ty2 = headTop + ts*0.22f;
            game.font.setColor(0f, 0f, 0f, 0.75f);
            game.font.draw(game.batch, name, tx2 + 1f, ty2 - 1f);
            game.font.setColor(0.80f, 0.85f, 1.0f, 1f);
            game.font.draw(game.batch, name, tx2, ty2);
            game.font.getData().setScale(1f);
            game.font.setColor(Color.WHITE);
        }
        game.batch.setColor(Color.WHITE);
    }

    // ---- Draw: player ----

    private void drawPlayer() {
        float px = visualX;
        float py = visualY;
        float ts = TILE_SZ;

        Color skin  = appearance != null ? appearance.getSkinColor()  : new Color(0.94f, 0.78f, 0.62f, 1f);
        Color shirt = appearance != null ? appearance.getShirtColor() : new Color(0.20f, 0.52f, 0.90f, 1f);
        Color pants = appearance != null ? appearance.getPantsColor() : new Color(0.14f, 0.34f, 0.62f, 1f);
        int   model = appearance != null ? appearance.modelType : 0;

        float headTop;

        if (model == 0) {
            // Standard — tall, slim
            game.batch.setColor(0f, 0f, 0f, 0.25f);
            game.batch.draw(whitePixel, px + ts * 0.12f, py + ts * 0.02f, ts * 0.76f, ts * 0.06f);

            float legW = ts * 0.18f, legH = ts * 0.20f, legY = py + ts * 0.08f;
            game.batch.setColor(pants);
            game.batch.draw(whitePixel, px + ts * 0.22f, legY, legW, legH);
            game.batch.draw(whitePixel, px + ts * 0.60f, legY, legW, legH);

            float bodyW = ts * 0.42f, bodyH = ts * 0.28f;
            float bodyX = px + (ts - bodyW) / 2f, bodyY = py + ts * 0.26f;
            game.batch.setColor(shirt);
            game.batch.draw(whitePixel, bodyX, bodyY, bodyW, bodyH);

            float headSz = ts * 0.30f;
            float headX  = px + (ts - headSz) / 2f, headY = bodyY + bodyH + ts * 0.02f;
            game.batch.setColor(skin);
            game.batch.draw(whitePixel, headX, headY, headSz, headSz);
            game.batch.setColor(1f, 1f, 1f, 0.18f);
            game.batch.draw(whitePixel, headX, headY + headSz - ts * 0.04f, headSz, ts * 0.04f);
            headTop = headY + headSz;

        } else {
            // Stocky — wider, shorter
            game.batch.setColor(0f, 0f, 0f, 0.25f);
            game.batch.draw(whitePixel, px + ts * 0.08f, py + ts * 0.02f, ts * 0.84f, ts * 0.06f);

            float legW = ts * 0.20f, legH = ts * 0.18f, legY = py + ts * 0.08f;
            game.batch.setColor(pants);
            game.batch.draw(whitePixel, px + ts * 0.18f, legY, legW, legH);
            game.batch.draw(whitePixel, px + ts * 0.62f, legY, legW, legH);

            float bodyW = ts * 0.58f, bodyH = ts * 0.24f;
            float bodyX = px + (ts - bodyW) / 2f, bodyY = py + ts * 0.24f;
            game.batch.setColor(shirt);
            game.batch.draw(whitePixel, bodyX, bodyY, bodyW, bodyH);
            game.batch.draw(whitePixel, bodyX - ts * 0.05f, bodyY + bodyH - ts * 0.06f, ts * 0.10f, ts * 0.08f);
            game.batch.draw(whitePixel, bodyX + bodyW - ts * 0.05f, bodyY + bodyH - ts * 0.06f, ts * 0.10f, ts * 0.08f);

            float headSz = ts * 0.35f;
            float headX  = px + (ts - headSz) / 2f, headY = bodyY + bodyH + ts * 0.02f;
            game.batch.setColor(skin);
            game.batch.draw(whitePixel, headX, headY, headSz, headSz);
            game.batch.setColor(1f, 1f, 1f, 0.18f);
            game.batch.draw(whitePixel, headX, headY + headSz - ts * 0.04f, headSz, ts * 0.04f);
            headTop = headY + headSz;
        }

        // Username above head
        if (appearance != null && !appearance.username.isEmpty()) {
            game.batch.setColor(Color.WHITE);
            game.font.getData().setScale(0.38f);
            layout.setText(game.font, appearance.username);
            float tx2 = px + ts / 2f - layout.width / 2f;
            float ty2 = headTop + ts * 0.22f;
            // Dark shadow for readability
            game.font.setColor(0f, 0f, 0f, 0.75f);
            game.font.draw(game.batch, appearance.username, tx2 + 1f, ty2 - 1f);
            game.font.setColor(Color.WHITE);
            game.font.draw(game.batch, appearance.username, tx2, ty2);
            game.font.getData().setScale(1f);
            game.font.setColor(Color.WHITE);
        }

        game.batch.setColor(Color.WHITE);
    }

    // ---- Draw: HUD ----

    private void drawHud() {
        // Interaction prompt (NPCs take priority over tiles)
        String promptLabel = nearbyNpc != null ? "[E] Talk to " + nearbyNpc.charName
                           : nearbyTile != null ? interactionLabel(nearbyTile)
                           : null;
        if (promptLabel != null) {
            game.font.getData().setScale(0.65f);
            layout.setText(game.font, promptLabel);
            float bw = layout.width + 36f;
            float bh = layout.height + 22f;
            float bx = (1280f - bw) / 2f;
            float by = 36f;

            // Box
            game.batch.setColor(0f, 0f, 0f, 0.78f);
            game.batch.draw(whitePixel, bx, by, bw, bh);
            // Gold top bar
            game.batch.setColor(1f, 0.84f, 0f, 0.85f);
            game.batch.draw(whitePixel, bx, by + bh - 2, bw, 2);

            game.font.setColor(Color.WHITE);
            game.font.draw(game.batch, promptLabel, bx + 18f, by + bh - 11f);
            game.font.getData().setScale(1f);
            game.font.setColor(Color.WHITE);
        }

        // "Searching for match" overlay
        if (searchingForMatch) {
            String msg = "Searching for ranked match...";
            game.font.getData().setScale(0.65f);
            layout.setText(game.font, msg);
            float bw = layout.width + 36f;
            float bh = layout.height + 22f;
            float bx = (1280f - bw) / 2f;
            float by = 680f - bh;
            game.batch.setColor(0f, 0f, 0f, 0.80f);
            game.batch.draw(whitePixel, bx, by, bw, bh);
            game.batch.setColor(1f, 0.5f, 0.1f, 0.90f); // orange top bar
            game.batch.draw(whitePixel, bx, by + bh - 2, bw, 2);
            game.font.setColor(new Color(1f, 0.84f, 0f, 1f));
            game.font.draw(game.batch, msg, bx + 18f, by + bh - 11f);
            game.font.getData().setScale(1f);
            game.font.setColor(Color.WHITE);
            game.batch.setColor(Color.WHITE);
        }

        // Debug: lobby player count (remove once multiplayer is confirmed working)
        if (client != null) {
            game.font.getData().setScale(0.34f);
            game.font.setColor(new Color(0.6f, 1f, 0.6f, 1f));
            game.font.draw(game.batch, "Others in lobby: " + otherPlayers.size(), 10f, 36f);
        }

        // Controls hint
        game.font.getData().setScale(0.34f);
        game.font.setColor(new Color(0.35f, 0.35f, 0.45f, 1f));
        game.font.draw(game.batch, "WASD: move   E/Space: interact   Esc: menu", 10f, 18f);
        game.font.getData().setScale(1f);
        game.font.setColor(Color.WHITE);
        game.batch.setColor(Color.WHITE);
    }

    private String interactionLabel(WorldTile tile) {
        if ("online_exit".equals(tile.triggerAreaId))        return "[E] Enter Online World";
        if (returnArea != null && nearbyTileY == 0)          return "[E] Exit Cave";
        if (tile.objectId == null)                           return "[E] Interact";
        if (tile.objectId.startsWith("building_fountain"))   return "[E] Search for Ranked Match";
        if (tile.objectId.startsWith("building_bar"))        return "[E] Enter Bar";
        if (tile.objectId.startsWith("building_inn"))        return "[E] Enter Inn";
        if (tile.objectId.startsWith("building_shoppe"))     return "[E] Enter Shoppe";
        if (tile.objectId.startsWith("building_cave"))       return "[E] Enter Cave";
        return "[E] Interact";
    }

    // ---- Helpers ----

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < area.width && y >= 0 && y < area.height;
    }

    @Override
    public void dispose() {
        whitePixel.dispose();
        for (Texture t : textures.values()) t.dispose();
        for (Texture t : npcPortraits.values()) if (t != null) t.dispose();
    }
}
