package com.mygame.tactics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
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
    private WorldArea area;
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

    // Deep-return: the return info of our returnArea (for stacked area transitions like inn → inn_2)
    private WorldArea deepReturnArea = null;
    private int       deepReturnX    = -1;
    private int       deepReturnY    = -1;

    // Player appearance (null = use defaults)
    private final PlayerAppearance appearance;

    // Online lobby: null for offline/single-player
    private final NetworkClient client;

    // Other players in the lobby: username → {x, y}
    private final Map<String, int[]> otherPlayers = new LinkedHashMap<>();

    // True once the player has sent JoinQueueAction (fountain/coliseum → waiting for match)
    private boolean searchingForMatch  = false;
    private boolean searchingRanked    = false;

    // Escape-key "leave to menu?" confirmation dialog
    private boolean showLeaveConfirm = false;
    private final Rectangle leaveConfirmYesBounds =
            new Rectangle(1280f / 2f - 230f, 720f / 2f - 60f, 200f, 60f);
    private final Rectangle leaveConfirmNoBounds =
            new Rectangle(1280f / 2f + 30f,  720f / 2f - 60f, 200f, 60f);

    // Set right before we voluntarily disconnect, so the network listener's
    // onDisconnected() doesn't also try to navigate us into a reconnect screen.
    private volatile boolean voluntaryDisconnect = false;

    // Forest ambient music — cycles between two tracks
    private Music[] forestTracks = new Music[2];
    private int     forestTrackIdx = 0;

    // Repair the Bridge quest state
    private boolean carryingBigLog = false;
    private boolean bigLogFloating = false;
    private float   bigLogFloatX   = 0f;
    private float   bigLogFloatY   = 0f;
    private static final float LOG_FLOAT_SPEED = 192f; // px/sec northward

    // NPC rendering
    private float npcBobTime = 0f;
    private final Map<String, Texture> npcPortraits = new LinkedHashMap<>();

    // Current nearby interactable NPC (set each frame by updateNearby)
    private WorldArea.WorldNpc nearbyNpc = null;

    // Last NPC that was auto-triggered; cleared when player leaves range to prevent re-firing
    private WorldArea.WorldNpc lastAutoNpc = null;

    // Run on the very first render() call — used to trigger entry cutscene chains
    private Runnable pendingFirstFrameAction = null;

    // Timed hint message shown at the top of the screen (e.g. blocked interaction feedback)
    private String hintText  = null;
    private float  hintTimer = 0f;

    // Follower NPC movement cooldown
    private float followerMoveCd = 0f;

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
    public WorldScreen(Main game, WorldArea area,
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
        this.area.applyOverrides(Main.flags);
        this.area = resolveSwap(this.area);
        spawnPlayer(spawnX >= 0 ? spawnX : area.width  / 2,
                    spawnY >= 0 ? spawnY : area.height / 2);
        visualX = playerX * TILE_SZ;
        visualY = playerY * TILE_SZ;
        snapCamera();

        if (client != null) {
            client.setListener(new NetworkClient.MessageListener() {
                @Override public void onConnected()    {}
                @Override public void onDisconnected() {
                    if (voluntaryDisconnect) return; // we're already navigating away ourselves
                    Gdx.app.postRunnable(() -> {
                        Main.flags.set("online_x", playerX);
                        Main.flags.set("online_y", playerY);
                        Main.flags.set("online_pos_saved", 1);
                        WorldArea cur = WorldScreen.this.area;
                        if (cur != null && cur.sourceFile != null)
                            Main.flags.setString("online_area", cur.sourceFile);
                        game.setScreen(new OnlineScreen(game, client, appearance));
                    });
                }
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

        // Restore bridge-quest carry state and Thomas-following state
        if (area.sourceFile != null && area.sourceFile.contains("area_forest")) {
            boolean treeCut   = Main.flags.is("repair_bridge_big_tree_cut");
            boolean logPlaced = Main.flags.is("repair_bridge_log_placed");
            boolean logFloated= Main.flags.is("repair_bridge_log_floated");
            if (treeCut && !logPlaced && !logFloated) carryingBigLog = true;
            // Thomas follows player during beam search (choice=1, tree not yet cut)
            if (Main.flags.get("repair_bridge_choice") == 1 && !treeCut) {
                for (WorldArea.WorldNpc npc : area.npcs) {
                    if ("Thomas".equals(npc.charName)) { npc.followsPlayer = true; break; }
                }
            }
        }

        // Forest ambient music
        if (area.sourceFile != null && area.sourceFile.contains("area_forest")) {
            try {
                forestTracks[0] = Gdx.audio.newMusic(Gdx.files.internal("music_forest1.mp3"));
                forestTracks[1] = Gdx.audio.newMusic(Gdx.files.internal("music_forest2.mp3"));
                forestTracks[0].setLooping(false);
                forestTracks[1].setLooping(false);
                forestTrackIdx = 0;
                forestTracks[0].setVolume(Main.musicVolume);
                forestTracks[0].play();
            } catch (Exception ignored) {}
        }

        // Area entry cutscene — plays once on first visit, gated by a PlayerFlag.
        // For first-time players (no username yet) the flag is set AFTER char creation
        // completes, so a crash/quit before finishing doesn't lock them out permanently.
        if (area.entryCutsceneId != null) {
            String entryFlag = area.areaId + "_entry_played";
            boolean firstTime = Main.flags.getString("ap_username", "").isEmpty();
            if (!Main.flags.is(entryFlag) || firstTime) {
                if (!firstTime) Main.flags.set(entryFlag, 1);
                pendingFirstFrameAction = buildEntryFlow();
            }
        }
    }

    // ---- Texture loading ----

    private void loadTextures() {
        // Load all tile/map/building textures from tile_manifest.txt.
        // Supports both plain-id lines ("tile_grass1") and tab-prefixed lines ("1\ttile_grass1").
        // Works in both Eclipse (classpath on disk) and JAR (embedded classpath).
        try {
            FileHandle manifest = Gdx.files.internal("tile_manifest.txt");
            if (manifest.exists()) {
                for (String line : manifest.readString().split("\\r?\\n")) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    int tab = line.indexOf('\t');
                    String id = (tab >= 0) ? line.substring(tab + 1).trim() : line;
                    if (id.isEmpty() || textures.containsKey(id)) continue;
                    try {
                        FileHandle fh = Gdx.files.internal(id + ".png");
                        if (!fh.exists()) fh = Gdx.files.internal(id + ".jpg");
                        if (fh.exists()) textures.put(id, new Texture(fh));
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        // Drop-item textures (not in the tile manifest)
        for (String icon : new String[]{
                "item_soul","item_log","item_rocks","item_violetberries",
                "item_axe","item_sword","item_staff","item_pickaxe",
                "item_boots","item_chestarmor","item_helmet"}) {
            try { textures.put(icon, new Texture(Gdx.files.internal(icon + ".png"))); } catch (Exception ignored) {}
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
                        for (int r = runStart; r < ty; r++) area.tiles[tx][r].isAnchor = false;
                        for (int r = runStart; r < ty; r += th) area.tiles[tx][r].isAnchor = true;
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
                    if (canWalkTo(x, y)) {
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

    @Override public void show()   { area.applyOverrides(Main.flags); }
    @Override public void hide()   { stopForestMusic(); }
    @Override public void pause()  { stopForestMusic(); }
    @Override public void resume() {
        if (forestTracks[forestTrackIdx] != null && !forestTracks[forestTrackIdx].isPlaying()) {
            forestTracks[forestTrackIdx].setVolume(Main.musicVolume);
            forestTracks[forestTrackIdx].play();
        }
    }

    @Override
    public void resize(int w, int h) {
        viewport.update(w, h);
        hudCam.setToOrtho(false, 1280, 720);
    }

    // ---- Render ----

    @Override
    public void render(float delta) {
        if (pendingFirstFrameAction != null) {
            Runnable action = pendingFirstFrameAction;
            pendingFirstFrameAction = null;
            action.run();
            return;
        }
        if (hintTimer > 0f) hintTimer -= delta;
        moveCd    -= delta;
        npcBobTime += delta;
        updateForestMusic();
        updateBridgeQuest(delta);
        handleInput();
        updateFollowers(delta);
        updateNearby();

        // Auto-trigger NPC interaction when entering proximity (once per approach)
        if (!showLeaveConfirm && nearbyNpc != null && nearbyNpc != lastAutoNpc) {
            lastAutoNpc = nearbyNpc;
            handleNpcInteract(nearbyNpc);
            return;
        }
        if (nearbyNpc == null) lastAutoNpc = null;

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
        drawDrops();
        drawOtherPlayers();
        drawPlayer();
        drawBigLog();
        game.batch.end();

        // HUD layer
        game.batch.setProjectionMatrix(hudCam.combined);
        game.batch.begin();
        drawHud();
        if (showLeaveConfirm) drawLeaveConfirm();
        game.batch.end();
    }

    // ---- Input ----

    private void handleInput() {
        if (showLeaveConfirm) {
            handleLeaveConfirmInput();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            showLeaveConfirm = true;
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.I)) {
            Pixmap snap = Pixmap.createFromFrameBuffer(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            Texture bgTex = new Texture(snap);
            snap.dispose();
            game.setScreen(new InventoryScreen(game, this, appearance, bgTex));
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            game.setScreen(new QuestLogScreen(game, this));
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
            if (canWalkTo(nx, ny)) {
                playerX = nx;
                playerY = ny;
                moveCd  = MOVE_CD;
                if (client != null) client.sendPlayerMove(playerX, playerY);
                checkDropPickup();
            }
        }
    }

    private void handleLeaveConfirmInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            showLeaveConfirm = false;
            return;
        }
        if (!Gdx.input.justTouched()) return;
        Vector3 world = hudCam.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        if (leaveConfirmYesBounds.contains(world.x, world.y)) {
            leaveToMenu();
        } else if (leaveConfirmNoBounds.contains(world.x, world.y)) {
            showLeaveConfirm = false;
        }
    }

    private void leaveToMenu() {
        if (client != null) {
            Main.flags.set("online_x", playerX);
            Main.flags.set("online_y", playerY);
            Main.flags.set("online_pos_saved", 1);
            if (area.sourceFile != null) Main.flags.setString("online_area", area.sourceFile);
            voluntaryDisconnect = true;
            client.disconnect();
        }
        game.setScreen(new MenuScreen(game));
    }

    private void checkDropPickup() {
        java.util.Iterator<WorldArea.WorldDrop> it = area.drops.iterator();
        while (it.hasNext()) {
            WorldArea.WorldDrop drop = it.next();
            if (drop.x == playerX && drop.y == playerY) {
                Main.inventory.addToBag(drop.item);
                Main.inventory.save(Main.flags);
                hintText  = "Picked up " + drop.item.name + "!";
                hintTimer = 3f;
                it.remove();
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
            if (npc.winFlag  != null && Main.flags.is(npc.winFlag))  continue;
            if (npc.showFlag != null && !Main.flags.is(npc.showFlag)) continue;
            int dx = Math.abs(npc.x - playerX), dy = Math.abs(npc.y - playerY);
            if (dx + dy <= 1) { nearbyNpc = npc; return; }
        }

        // Check for interactable tiles — only count a tile if it has something to interact with
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
            if (!t.interactable) continue;
            boolean isExit = returnArea != null && (pos[1] == 0 || pos[1] == area.height - 1);
            // Allow interactable water tiles when carrying the big log
            if (carryingBigLog && isWaterTile(pos[0], pos[1])) {
                nearbyTile = t; nearbyTileX = pos[0]; nearbyTileY = pos[1]; return;
            }
            if (t.objectId != null || t.triggerAreaId != null || isExit) {
                nearbyTile = t; nearbyTileX = pos[0]; nearbyTileY = pos[1]; return;
            }
        }
    }

    private void interact() {
        if (nearbyNpc != null) {
            if ("Thomas".equals(nearbyNpc.charName)
                    && area.sourceFile != null && area.sourceFile.contains("area_forest")
                    && Main.flags.is("repair_bridge_started")) {
                handleThomasBridgeInteract(nearbyNpc);
                return;
            }
            if ("Tyler".equals(nearbyNpc.charName)) {
                com.badlogic.gdx.graphics.Pixmap snap =
                        com.badlogic.gdx.graphics.Pixmap.createFromFrameBuffer(
                                0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
                Texture bgTex = new Texture(snap);
                snap.dispose();
                game.setScreen(new SellScreen(game, this, Main.inventory, Main.flags, bgTex));
                return;
            }
            handleNpcInteract(nearbyNpc);
            return;
        }
        if (nearbyTile == null) return;

        // Special trigger: exit to online character creation
        if ("online_exit".equals(nearbyTile.triggerAreaId)) {
            game.setScreen(new CharacterCreationScreen(game));
            return;
        }

        // Edge-row exit tile: return to the previous area, spawning at the stored position
        if (returnArea != null && (nearbyTileY == 0 || nearbyTileY == area.height - 1)) {
            game.setScreen(new WorldScreen(game, returnArea, null, -1, -1, returnX, returnY, appearance, client));
            return;
        }

        // Guard Tower combat trigger
        if ("guard_tower_combat".equals(nearbyTile.triggerAreaId)) {
            if (Main.flags.is("guard_tower_won")) {
                hintText = "The arena is empty now."; hintTimer = 3f;
            } else {
                WorldArea.WorldNpc gtNpc = new WorldArea.WorldNpc();
                gtNpc.charName      = "GuardTower";
                gtNpc.x             = nearbyTileX; gtNpc.y = nearbyTileY;
                gtNpc.combatFile    = "combat_board_guard_tower.txt";
                gtNpc.winFlag       = "guard_tower_won";
                gtNpc.team1Preset   = null;
                launchNpcCombat(gtNpc);
            }
            return;
        }

        // Generic area transition: if triggerAreaId points to a WorldArea file, load and enter it
        if (nearbyTile.triggerAreaId != null) {
            FileHandle areaFile = findAssetFile(nearbyTile.triggerAreaId + ".txt");
            if (areaFile != null) {
                try {
                    WorldArea dest = WorldArea.load(areaFile);
                    game.setScreen(new WorldScreen(game, dest, area, playerX, playerY,
                            dest.spawnX, dest.spawnY, appearance, client));
                    return;
                } catch (Exception ignored) {}
                // File found but not a WorldArea — fall through to cutscene/object handling
            }
        }

        // No objectId and no specific trigger — if we're in a sub-area, return to previous area
        if (nearbyTile.objectId == null) {
            if (returnArea != null) {
                game.setScreen(new WorldScreen(game, returnArea, null, -1, -1, returnX, returnY, appearance, client));
            }
            return;
        }
        String obj = nearbyTile.objectId;

        if (obj.equals("map_violetberry_bush_full") || obj.equals("map_violetberry_bush")) {
            String pickFlag = "vb_" + nearbyTileX + "_" + nearbyTileY;
            if (obj.equals("map_violetberry_bush") || Main.flags.is(pickFlag)) {
                hintText  = "This bush has already been picked.";
                hintTimer = 2f;
            } else {
                Item berries = new Item("Violetberries", "Freshly picked violetberries.", Item.ItemSlot.MISC);
                berries.iconName = "item_violetberries";
                if (Main.inventory.addToBag(berries)) {
                    Main.inventory.save(Main.flags);
                    Main.flags.set(pickFlag, 1);
                    Main.flags.increment("violetberries_count");
                    area.tiles[nearbyTileX][nearbyTileY].objectId = "map_violetberry_bush";
                    hintText  = "Picked up Violetberries!";
                    hintTimer = 3f;
                } else {
                    hintText  = "Your bag is full.";
                    hintTimer = 3f;
                }
            }
            return;
        }
        if (obj.equals("map_dead_tree1")) {
            boolean hasAxe = Main.inventory.weapon != null && Main.inventory.weapon.name.equals("Axe");
            if (!hasAxe) {
                hintText  = "You need an axe to cut this down.";
                hintTimer = 2f;
            } else if (Main.flags.get("repair_bridge_choice") == 1
                    && !Main.flags.is("repair_bridge_big_tree_cut")) {
                // Quest: cut the big beam tree
                area.tiles[nearbyTileX][nearbyTileY].objectId = null;
                area.tiles[nearbyTileX][nearbyTileY].walkable = true;
                launchCutsceneThen("scene_thomas_big_tree_cut", () -> {
                    carryingBigLog = true;
                    for (WorldArea.WorldNpc n : area.npcs)
                        if ("Thomas".equals(n.charName)) { n.followsPlayer = false; break; }
                });
            } else {
                Main.flags.set("tree_" + nearbyTileX + "_" + nearbyTileY + "_cut", 1);
                area.tiles[nearbyTileX][nearbyTileY].objectId = null;
                area.tiles[nearbyTileX][nearbyTileY].walkable = true;
                Item log = new Item("Log", "A freshly cut log.", Item.ItemSlot.MISC);
                log.iconName = "item_log";
                area.drops.add(new WorldArea.WorldDrop(nearbyTileX, nearbyTileY, log));
                if (Main.flags.is("man_in_woods_started") && !Main.flags.is("man_in_woods_complete")) {
                    Main.flags.increment("man_in_woods_trees_cut");
                }
                hintText  = "You cut down the tree!";
                hintTimer = 3f;
            }
            return;
        }
        // River interaction while carrying the big log
        if (carryingBigLog && nearbyTile != null && isWaterTile(nearbyTileX, nearbyTileY)) {
            launchCutsceneThen("scene_thomas_log_float", () -> {
                carryingBigLog = false;
                bigLogFloating = true;
                bigLogFloatX   = nearbyTileX * TILE_SZ;
                bigLogFloatY   = nearbyTileY * TILE_SZ;
                for (WorldArea.WorldNpc n : area.npcs)
                    if ("Thomas".equals(n.charName)) { n.followsPlayer = false; break; }
            });
            return;
        }
        if (obj.equals("map_chest_full")) {
            String chestFlag = "chest_" + nearbyTileX + "_" + nearbyTileY;
            if (Main.flags.is(chestFlag)) {
                hintText = "This chest is empty."; hintTimer = 2f;
            } else {
                Item loot = createChestItem(nearbyTile.triggerAreaId);
                if (loot != null) {
                    if (Main.inventory.addToBag(loot)) {
                        Main.inventory.save(Main.flags);
                        Main.flags.set(chestFlag, 1);
                        area.tiles[nearbyTileX][nearbyTileY].objectId = "map_chest_empty";
                        hintText  = "Found " + loot.name + "!";
                        hintTimer = 3f;
                    } else {
                        hintText = "Your bag is full."; hintTimer = 2f;
                    }
                } else {
                    hintText = "The chest is empty."; hintTimer = 2f;
                }
            }
            return;
        }
        if (obj.equals("map_stone1")) {
            boolean hasPickaxe = Main.inventory.weapon != null && Main.inventory.weapon.name.equals("Pickaxe");
            if (!hasPickaxe) {
                hintText  = "You need a pickaxe to break this.";
                hintTimer = 2f;
            } else {
                area.tiles[nearbyTileX][nearbyTileY].objectId = null;
                area.tiles[nearbyTileX][nearbyTileY].walkable = true;
                Item rocks = new Item("Rocks", "A pile of rocks.", Item.ItemSlot.MISC);
                rocks.iconName = "item_rocks";
                area.drops.add(new WorldArea.WorldDrop(nearbyTileX, nearbyTileY, rocks));
                hintText  = "You broke the rock!";
                hintTimer = 3f;
            }
            return;
        }
        if (obj.startsWith("map_stairs")) {
            if (!Main.flags.is("fix_stairs_complete")) {
                hintText  = "The stairs are broken.";
                hintTimer = 3f;
            } else if ("area_inn_2".equals(area.areaId)) {
                // Going back downstairs to the inn
                FileHandle innFile = findAssetFile("area_inn.txt");
                if (innFile != null) {
                    try {
                        WorldArea inn = WorldArea.load(innFile);
                        inn.applyOverrides(Main.flags);
                        WorldScreen innScreen = new WorldScreen(game, inn, deepReturnArea,
                                deepReturnX, deepReturnY, 12, 27, appearance, client);
                        game.setScreen(innScreen);
                    } catch (Exception e) {
                        Gdx.app.error("WorldScreen", "Failed to load area_inn: " + e.getMessage());
                    }
                }
            } else {
                // Going upstairs to inn_2
                FileHandle stairsFile = findAssetFile("area_inn_2.txt");
                if (stairsFile != null) {
                    try {
                        WorldArea inn2 = WorldArea.load(stairsFile);
                        inn2.applyOverrides(Main.flags);
                        WorldScreen inn2Screen = new WorldScreen(game, inn2, area, playerX, playerY,
                                inn2.spawnX, inn2.spawnY, appearance, client);
                        // Preserve this screen's return info so downstairs can restore it
                        inn2Screen.deepReturnArea = returnArea;
                        inn2Screen.deepReturnX    = returnX;
                        inn2Screen.deepReturnY    = returnY;
                        game.setScreen(inn2Screen);
                    } catch (Exception e) {
                        Gdx.app.error("WorldScreen", "Failed to load area_inn_2: " + e.getMessage());
                    }
                }
            }
            return;
        }
        if (obj.startsWith("building_fountain")) {
            if (client != null) {
                int unlocked = Main.inventory.unlockedCharacters.size();
                if (unlocked < 8) {
                    hintText  = "You need at least 8 unlocked characters for casual PvP.";
                    hintTimer = 3f;
                } else if (!searchingForMatch) {
                    searchingForMatch = true;
                    searchingRanked   = false;
                    client.joinQueue(false);
                }
            } else {
                game.setScreen(new OnlineScreen(game, new NetworkClient(), appearance != null ? appearance : new PlayerAppearance()));
            }
        }
        if (obj.startsWith("building_coliseum")) {
            if (client != null) {
                int unlocked = Main.inventory.unlockedCharacters.size();
                if (unlocked < 24) {
                    hintText  = "You need at least 24 unlocked characters for ranked PvP.";
                    hintTimer = 3f;
                } else if (!searchingForMatch) {
                    searchingForMatch = true;
                    searchingRanked   = true;
                    client.joinQueue(true);
                }
            } else {
                game.setScreen(new OnlineScreen(game, new NetworkClient(), appearance != null ? appearance : new PlayerAppearance()));
            }
        }
        if (obj.equals("building_runic_stone")) {
            final WorldArea       savedArea   = area;
            final int             savedX      = playerX,  savedY      = playerY;
            final PlayerAppearance savedApp   = appearance;
            final NetworkClient   savedClient = client;
            final WorldArea       savedReturn = returnArea;
            final int             savedRX     = returnX,  savedRY     = returnY;
            Runnable onClose = () -> {
                WorldArea fresh = savedArea;
                if (savedArea.sourceFile != null) {
                    try { fresh = WorldArea.load(Gdx.files.internal(savedArea.sourceFile)); }
                    catch (Exception ignored) {}
                }
                fresh.applyOverrides(Main.flags);
                game.setScreen(new WorldScreen(game, fresh, savedReturn, savedRX, savedRY,
                        savedX, savedY, savedApp, savedClient));
            };
            game.setScreen(new RunicStoneScreen(game, onClose, appearance));
            return;
        }
        if (obj.equals("building_cave1")) {
            String caveChar = findCaveFighter(nearbyTileX, nearbyTileY);
            if (caveChar != null) {
                String winFlagKey = "cave_" + caveChar + "_won";
                WorldArea.WorldNpc caveNpc = new WorldArea.WorldNpc();
                caveNpc.charName   = caveChar;
                caveNpc.x          = nearbyTileX;
                caveNpc.y          = nearbyTileY;
                caveNpc.winFlag    = winFlagKey;
                String[] caveBoards = { "combat_board_cave_forest.txt",
                                        "combat_board_cave_wind.txt",
                                        "combat_board_cave_desert.txt" };
                caveNpc.combatFile = caveBoards[com.badlogic.gdx.math.MathUtils.random(caveBoards.length - 1)];
                launchNpcCombat(caveNpc);
            }
            return;
        }
        if (obj.equals("building_cave2")) {
            playCutsceneThen(nearbyTile, () -> enterCombatCave("combat_cave2.txt"));
            return;
        }
        if (obj.startsWith("building_cave")) {
            playCutsceneThen(nearbyTile, this::enterCave);
            return;
        }
        if (obj.equals("building_Bridge_Fixed") && "tutorial_island".equals(area.areaId)) {
            int filled = (Main.inventory.abilitySlot1.isEmpty() ? 0 : 1)
                       + (Main.inventory.abilitySlot2.isEmpty() ? 0 : 1)
                       + (Main.inventory.abilitySlot3.isEmpty() ? 0 : 1);
            if (filled < 3) {
                hintText  = "Select 3 abilities at the Runic Stone before crossing.";
                hintTimer = 3f;
                return;
            }
            Main.flags.set("area_tutorial_complete", 1);
            FileHandle forestFile = findAssetFile("area_forest.txt");
            if (forestFile == null) return;
            try {
                WorldArea            forest      = WorldArea.load(forestFile);
                final PlayerAppearance savedApp   = appearance;
                final NetworkClient    savedClient = client;
                Runnable enterForest = () ->
                    game.setScreen(new WorldScreen(game, forest, null, -1, -1,
                            forest.spawnX, forest.spawnY, savedApp, savedClient));
                FileHandle scene9 = findAssetFile("scene_tutorial_9.txt");
                if (scene9 != null) {
                    CutsceneData data = CutsceneData.load(scene9);
                    game.setScreen(new CutsceneScreen(game, data, Main.flags, enterForest));
                } else {
                    enterForest.run();
                }
            } catch (Exception e) {
                Gdx.app.error("WorldScreen", "Bridge to forest failed: " + e.getMessage());
            }
            return;
        }
        if (obj.equals("building_lukes_house")) {
            if (!Main.flags.is("man_in_woods_complete")) {
                hintText  = "Luke doesn't seem to be home.";
                hintTimer = 3f;
            } else if (Main.flags.is("luke_won")) {
                hintText  = "Luke isn't taking any more challengers.";
                hintTimer = 3f;
            } else {
                WorldArea.WorldNpc lukeNpc = new WorldArea.WorldNpc();
                lukeNpc.charName       = "Luke";
                lukeNpc.x              = nearbyTileX;
                lukeNpc.y              = nearbyTileY;
                lukeNpc.combatFile     = "combat_board_luke.txt";
                lukeNpc.winFlag        = "luke_won";
                lukeNpc.winCutsceneId  = "scene_luke_house_win";
                lukeNpc.lossCutsceneId = "scene_luke_house_loss";
                lukeNpc.triggerAreaId  = "scene_luke_house";
                lukeNpc.team1Preset    = null;
                handleNpcInteract(lukeNpc);
            }
            return;
        }
        // Generic building → area transition: building_bar → area_bar.txt, etc.
        if (obj.startsWith("building_")) {
            String areaName = "area_" + obj.substring("building_".length());
            FileHandle areaFile = findAssetFile(areaName + ".txt");
            if (areaFile != null) {
                try {
                    WorldArea dest = WorldArea.load(areaFile);
                    game.setScreen(new WorldScreen(game, dest, area, playerX, playerY,
                            dest.spawnX, dest.spawnY, appearance, client));
                } catch (Exception e) {
                    Gdx.app.error("WorldScreen", "Failed to load area: " + areaName + " — " + e.getMessage());
                }
            }
        }
    }

    /**
     * Searches common asset directories for a file by name.
     * Checks both the root and a "cutscenes/" subdirectory so files work
     * regardless of whether they're organised into a subfolder.
     * Returns the first FileHandle that exists, or null if not found.
     */
    private FileHandle findAssetFile(String filename) {
        for (String prefix : new String[]{"", "cutscenes/"}) {
            FileHandle f = Gdx.files.internal(prefix + filename);
            if (f.exists()) return f;
            // exists() can return false for classpath resources in fat JARs; try reading directly
            try { f.read().close(); return f; } catch (Exception ignored) {}
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
                    Runnable afterCutscene = hasCombat ? () -> launchNpcCombat(npc) : () -> game.setScreen(WorldScreen.this);
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
     * On win: sets winFlag, drops soul, plays winCutsceneId (or triggerAreaId_post fallback).
     * On loss: plays lossCutsceneId if set, otherwise just returns to world.
     */
    private void launchNpcCombat(WorldArea.WorldNpc npc) {
        // Persist follower positions so they survive the area reload on return
        for (WorldArea.WorldNpc n : area.npcs) {
            if (!n.followsPlayer) continue;
            Main.flags.set("npc_" + area.areaId + "_" + n.charName + "_x", n.x);
            Main.flags.set("npc_" + area.areaId + "_" + n.charName + "_y", n.y);
        }

        final WorldArea        savedArea       = area;
        final int              savedX          = playerX;
        final int              savedY          = playerY;
        final PlayerAppearance savedApp        = appearance;
        final NetworkClient    savedClient     = client;
        final WorldArea        savedReturnArea = returnArea;
        final int              savedReturnX    = returnX;
        final int              savedReturnY    = returnY;
        final String           winFlagKey      = npc.winFlag;
        final String           triggerAreaId   = npc.triggerAreaId;
        final String           winCutsceneId   = npc.winCutsceneId;
        final String           lossCutsceneId  = npc.lossCutsceneId;
        final String           npcCharName     = npc.charName;
        final int              npcDropX        = npc.x;
        final int              npcDropY        = npc.y;

        java.util.function.Consumer<Boolean> returnCallback = won -> {
            // Reload area so flag-based tile overrides take effect
            WorldArea freshArea = savedArea;
            if (savedArea.sourceFile != null) {
                try { freshArea = WorldArea.load(Gdx.files.internal(savedArea.sourceFile)); }
                catch (Exception ignored) {}
            }
            if (won) {
                if (winFlagKey != null && !winFlagKey.isEmpty()) {
                    Main.flags.set(winFlagKey, 1);
                    if (npcCharName != null
                            && !Main.inventory.unlockedCharacters.contains(npcCharName)
                            && !Main.inventory.hasSoulInBag(npcCharName)) {
                        Item soul = new Item(npcCharName + " Soul",
                                "The soul of " + npcCharName + ".", Item.ItemSlot.MISC);
                        soul.iconName = "item_soul";
                        freshArea.drops.add(new WorldArea.WorldDrop(npcDropX, npcDropY, soul));
                    }
                }
                checkAreaComplete();
            }
            freshArea.applyOverrides(Main.flags);
            freshArea = resolveSwap(freshArea);
            // Restore follower positions saved before combat
            for (WorldArea.WorldNpc n : freshArea.npcs) {
                if (!n.followsPlayer) continue;
                int sx = Main.flags.get("npc_" + freshArea.areaId + "_" + n.charName + "_x");
                int sy = Main.flags.get("npc_" + freshArea.areaId + "_" + n.charName + "_y");
                if (sx != 0 || sy != 0) { n.x = sx; n.y = sy; }
            }
            final WorldArea areaToShow = freshArea;
            Runnable goToWorld = () -> game.setScreen(
                    new WorldScreen(game, areaToShow, savedReturnArea, savedReturnX, savedReturnY,
                            savedX, savedY, savedApp, savedClient));

            // Determine which post-combat cutscene to play
            String postId = won ? winCutsceneId : lossCutsceneId;
            if (postId == null && won && triggerAreaId != null) {
                // Fall back to the {triggerAreaId}_post convention for win-only
                FileHandle fallback = findAssetFile(triggerAreaId + "_post.txt");
                if (fallback != null) postId = triggerAreaId + "_post";
            }
            if (postId != null) {
                FileHandle postFile = findAssetFile(postId + ".txt");
                if (postFile != null) {
                    try {
                        CutsceneData data = CutsceneData.load(postFile);
                        game.setScreen(new CutsceneScreen(game, data, Main.flags, goToWorld));
                        return;
                    } catch (Exception e) {
                        Gdx.app.error("WorldScreen", "Post-combat cutscene failed: " + e.getMessage());
                    }
                }
            }
            goToWorld.run();
        };

        final String team1Preset = npc.team1Preset;
        FileHandle f = Gdx.files.internal(npc.combatFile);
        if (f.exists()) {
            CombatBoardLoader.Result result = CombatBoardLoader.load(f);
            if (result != null && result.team2.size == 0 && npcCharName != null) {
                Character injectChar = CombatBoardLoader.createCharacter(npcCharName);
                if (injectChar != null) { injectChar.team = 2; result.team2.add(injectChar); }
            }
            if (result != null) {
                if (team1Preset != null) {
                    // Skip draft — use the preset team directly
                    com.badlogic.gdx.utils.Array<Character> team1 = CombatBoardLoader.buildTeam(team1Preset);
                    String playerName = Main.flags.getString("ap_username", "Player");
                    String[] teamNames = {playerName, npc.charName != null ? npc.charName : "Enemy"};
                    game.setScreen(new CombatScreen(game, team1, result.team2, result.config,
                            teamNames, true, returnCallback));
                } else {
                    game.setScreen(new DraftScreen(game, result.team2, result.config, returnCallback));
                }
            }
        } else {
            Gdx.app.error("WorldScreen", "NPC combat file not found: " + npc.combatFile);
        }
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

    /**
     * Builds the Runnable chain triggered on first area entry:
     *   entryCutscene → (CharacterCreationScreen if no name) → postEntryCutscene → new WorldScreen
     */
    private Runnable buildEntryFlow() {
        final WorldArea        savedArea       = this.area;
        final int              savedSpawnX     = this.playerX;
        final int              savedSpawnY     = this.playerY;
        final WorldArea        savedReturnArea = this.returnArea;
        final int              savedReturnX    = this.returnX;
        final int              savedReturnY    = this.returnY;
        final NetworkClient    savedClient     = this.client;

        // Final step: re-create the WorldScreen with the (possibly new) appearance
        Runnable showWorld = () -> {
            PlayerAppearance ap = new PlayerAppearance();
            ap.username      = Main.flags.getString("ap_username",      "Player");
            ap.modelType     = Main.flags.get("ap_modelType");
            ap.skinColorIdx  = Main.flags.get("ap_skinColorIdx");
            ap.shirtColorIdx = Main.flags.get("ap_shirtColorIdx");
            ap.pantsColorIdx = Main.flags.get("ap_pantsColorIdx");
            WorldArea fresh = savedArea;
            if (savedArea.sourceFile != null) {
                try { fresh = WorldArea.load(Gdx.files.internal(savedArea.sourceFile)); }
                catch (Exception ignored) {}
            }
            fresh.applyOverrides(Main.flags);
            fresh = resolveSwap(fresh);
            game.setScreen(new WorldScreen(game, fresh, savedReturnArea, savedReturnX, savedReturnY,
                    savedSpawnX, savedSpawnY, ap, savedClient));
        };

        // Optional post-entry cutscene (e.g. scene_tutorial_2 after name entry)
        final Runnable afterNameEntry;
        if (savedArea.postEntryCutsceneId != null) {
            final String postId = savedArea.postEntryCutsceneId;
            afterNameEntry = () -> {
                FileHandle pcf = findAssetFile(postId + ".txt");
                if (pcf != null) {
                    try {
                        CutsceneData d = CutsceneData.load(pcf);
                        game.setScreen(new CutsceneScreen(game, d, Main.flags, showWorld));
                        return;
                    } catch (Exception e) {
                        Gdx.app.error("WorldScreen", "Post-entry cutscene failed: " + e.getMessage());
                    }
                }
                showWorld.run();
            };
        } else {
            afterNameEntry = showWorld;
        }

        // If no name has been set yet, show CharacterCreationScreen between the two cutscenes.
        // Set the entry-played flag only after char creation so quitting mid-flow doesn't lock it out.
        final String entryFlag = savedArea.areaId + "_entry_played";
        final Runnable afterEntryCutscene;
        if (Main.flags.getString("ap_username", "").isEmpty()) {
            afterEntryCutscene = () -> game.setScreen(new CharacterCreationScreen(game, () -> {
                Main.flags.set(entryFlag, 1);
                afterNameEntry.run();
            }));
        } else {
            afterEntryCutscene = afterNameEntry;
        }

        // Wrap in the entry cutscene itself
        final String entryId = savedArea.entryCutsceneId;
        return () -> {
            FileHandle cf = findAssetFile(entryId + ".txt");
            if (cf != null) {
                try {
                    CutsceneData d = CutsceneData.load(cf);
                    game.setScreen(new CutsceneScreen(game, d, Main.flags, afterEntryCutscene));
                    return;
                } catch (Exception e) {
                    Gdx.app.error("WorldScreen", "Entry cutscene failed: " + e.getMessage());
                }
            }
            afterEntryCutscene.run();
        };
    }

    private static final float FOLLOWER_MOVE_CD = 0.18f;

    private void updateFollowers(float delta) {
        followerMoveCd -= delta;
        if (followerMoveCd > 0f) return;
        followerMoveCd = FOLLOWER_MOVE_CD;
        for (WorldArea.WorldNpc npc : area.npcs) {
            if (npc.followsPlayer) stepFollowerNpc(npc);
        }
    }

    private void stepFollowerNpc(WorldArea.WorldNpc npc) {
        int dx = playerX - npc.x;
        int dy = playerY - npc.y;
        if (Math.abs(dx) + Math.abs(dy) <= 1) return; // already adjacent, stay put

        // Try primary axis (the one with larger gap), then secondary
        int[] primary   = Math.abs(dx) >= Math.abs(dy)
                ? new int[]{dx > 0 ? 1 : -1, 0}
                : new int[]{0, dy > 0 ? 1 : -1};
        int[] secondary = primary[0] != 0
                ? new int[]{0, dy > 0 ? 1 : -1}
                : new int[]{dx > 0 ? 1 : -1, 0};

        for (int[] step : new int[][]{primary, secondary}) {
            int nx = npc.x + step[0], ny = npc.y + step[1];
            if (canWalkTo(nx, ny) && !(nx == playerX && ny == playerY)) {
                npc.x = nx; npc.y = ny;
                return;
            }
        }
    }

    private void enterCave() {
        try {
            FileHandle f = Gdx.files.internal("Cave1.txt");
            if (f.exists()) {
                WorldArea cave = WorldArea.load(f);
                game.setScreen(new WorldScreen(game, cave, area, playerX, playerY, appearance, client));
            }
        } catch (Exception ignored) {}
    }

    private void enterCombatCave(String filename) {
        final WorldArea        savedArea   = area;
        final int              savedX      = playerX;
        final int              savedY      = playerY;
        final PlayerAppearance savedApp    = appearance;
        final NetworkClient    savedClient = client;
        java.util.function.Consumer<Boolean> returnCallback = won -> {
            WorldArea freshArea = savedArea;
            if (savedArea.sourceFile != null) {
                try { freshArea = WorldArea.load(Gdx.files.absolute(savedArea.sourceFile)); }
                catch (Exception ignored) {}
            }
            game.setScreen(new WorldScreen(game, freshArea, savedX, savedY, savedApp, savedClient));
        };

        try {
            FileHandle f = Gdx.files.internal(filename);
            if (f.exists()) {
                CombatBoardLoader.Result result = CombatBoardLoader.load(f);
                if (result != null)
                    game.setScreen(new DraftScreen(game, result.team2, result.config, returnCallback));
            }
        } catch (Exception ignored) {}
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
            if (npc.winFlag  != null && Main.flags.is(npc.winFlag))  continue;
            if (npc.showFlag != null && !Main.flags.is(npc.showFlag)) continue;
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
        try {
            FileHandle f = Gdx.files.internal(key + ".png");
            if (f.exists()) {
                Texture t = new Texture(f);
                npcPortraits.put(key, t);
                return t;
            }
        } catch (Exception ignored) {}
        npcPortraits.put(key, null); // cache the miss so we don't retry every frame
        return null;
    }

    // ---- Draw: dropped items ----

    private void drawDrops() {
        if (area.drops.isEmpty()) return;
        Texture fallback = textures.get("item_soul");
        if (fallback == null) return;
        float bob = (float) Math.sin(npcBobTime * Math.PI * 1.6f) * 4f;
        float ts  = TILE_SZ;
        for (WorldArea.WorldDrop drop : area.drops) {
            String  iconName = drop.item != null ? drop.item.iconName : null;
            Texture tex      = iconName != null ? textures.getOrDefault(iconName, fallback) : fallback;
            float iconSz = ts * 0.55f;
            float dx = drop.x * ts + (ts - iconSz) / 2f;
            float dy = drop.y * ts + (ts - iconSz) / 2f + bob;
            game.batch.setColor(0.6f, 0.4f, 1f, 0.25f);
            game.batch.draw(whitePixel, drop.x * ts + ts * 0.15f, drop.y * ts + ts * 0.05f,
                    ts * 0.70f, ts * 0.10f);
            game.batch.setColor(Color.WHITE);
            game.batch.draw(tex, dx, dy, iconSz, iconSz);
        }
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
            String msg = searchingRanked ? "Searching for ranked match..." : "Searching for casual match...";
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

        // Timed hint message (e.g. blocked bridge crossing)
        if (hintText != null && hintTimer > 0f) {
            game.font.getData().setScale(0.55f);
            layout.setText(game.font, hintText);
            float bw = layout.width + 36f;
            float bh = layout.height + 22f;
            float bx = (1280f - bw) / 2f;
            float by = 680f - bh;
            float alpha = Math.min(1f, hintTimer);
            game.batch.setColor(0f, 0f, 0f, 0.78f * alpha);
            game.batch.draw(whitePixel, bx, by, bw, bh);
            game.batch.setColor(1f, 0.4f, 0.4f, 0.9f * alpha);
            game.batch.draw(whitePixel, bx, by + bh - 2, bw, 2);
            game.font.setColor(1f, 1f, 1f, alpha);
            game.font.draw(game.batch, hintText, bx + 18f, by + bh - 11f);
            game.font.getData().setScale(1f);
            game.font.setColor(Color.WHITE);
            game.batch.setColor(Color.WHITE);
        }

        // Controls hint
        game.font.getData().setScale(0.34f);
        game.font.setColor(new Color(0.35f, 0.35f, 0.45f, 1f));
        game.font.draw(game.batch, "WASD: move   E/Space: interact   I: inventory   Q: quests   Esc: menu", 10f, 18f);
        game.font.getData().setScale(1f);
        game.font.setColor(Color.WHITE);
        game.batch.setColor(Color.WHITE);
    }

    private void drawLeaveConfirm() {
        // Dim the world behind the dialog
        game.batch.setColor(0f, 0f, 0f, 0.55f);
        game.batch.draw(whitePixel, 0, 0, 1280, 720);

        float pw = 460f, ph = 170f;
        float px = (1280f - pw) / 2f, py = (720f - ph) / 2f;

        game.batch.setColor(0.10f, 0.10f, 0.16f, 0.96f);
        game.batch.draw(whitePixel, px, py, pw, ph);
        game.batch.setColor(1f, 0.84f, 0f, 0.85f);
        game.batch.draw(whitePixel, px, py + ph - 3f, pw, 3f);

        game.font.getData().setScale(0.7f);
        game.font.setColor(Color.WHITE);
        String msg = "Leave to the main menu?";
        layout.setText(game.font, msg);
        game.font.draw(game.batch, msg, px + (pw - layout.width) / 2f, py + ph - 34f);
        game.font.getData().setScale(1f);

        drawConfirmButton(leaveConfirmYesBounds, "Leave", true);
        drawConfirmButton(leaveConfirmNoBounds,  "Cancel", false);

        game.font.setColor(Color.WHITE);
        game.batch.setColor(Color.WHITE);
    }

    private void drawConfirmButton(Rectangle bounds, String label, boolean warn) {
        game.batch.setColor(warn ? new Color(0.35f, 0.12f, 0.12f, 1f) : new Color(0.16f, 0.16f, 0.22f, 1f));
        game.batch.draw(whitePixel, bounds.x, bounds.y, bounds.width, bounds.height);
        game.batch.setColor(warn ? new Color(0.85f, 0.25f, 0.25f, 1f) : new Color(0.4f, 0.4f, 0.5f, 1f));
        game.batch.draw(whitePixel, bounds.x, bounds.y + bounds.height - 3f, bounds.width, 3f);

        game.font.getData().setScale(0.6f);
        game.font.setColor(Color.WHITE);
        layout.setText(game.font, label);
        game.font.draw(game.batch, label, bounds.x + (bounds.width - layout.width) / 2f,
                bounds.y + bounds.height / 2f + layout.height / 2f);
        game.font.getData().setScale(1f);
    }

    private String interactionLabel(WorldTile tile) {
        if ("online_exit".equals(tile.triggerAreaId))        return "[E] Enter Online World";
        if (returnArea != null && (nearbyTileY == 0 || nearbyTileY == area.height - 1)) return "[E] Exit";
        if (tile.objectId == null)                           return "[E] Interact";
        if (tile.objectId.equals("building_lukes_house"))    return "[E] Enter";
        if (tile.objectId.startsWith("building_fountain"))   return "[E] Search for Casual Match";
        if (tile.objectId.startsWith("building_coliseum"))   return "[E] Search for Ranked Match";
        if (tile.objectId.startsWith("building_bar"))        return "[E] Enter Bar";
        if (tile.objectId.startsWith("building_inn"))        return "[E] Enter Inn";
        if (tile.objectId.startsWith("building_shoppe"))     return "[E] Enter Shoppe";
        if (tile.objectId.startsWith("building_cave"))       return "[E] Enter Cave";
        if (tile.objectId.equals("building_Bridge_Fixed"))        return "[E] Cross Bridge";
        if (tile.objectId.equals("map_violetberry_bush_full"))     return "[E] Pick Violetberries";
        if (tile.objectId.equals("map_violetberry_bush"))          return "[E] Examine Bush";
        if (tile.objectId != null && tile.objectId.equals("map_dead_tree1")) {
            boolean hasAxe = Main.inventory.weapon != null && Main.inventory.weapon.name.equals("Axe");
            return hasAxe ? "[E] Cut Down Tree" : "[E] Examine Tree";
        }
        if (tile.objectId != null && tile.objectId.equals("map_stone1")) {
            boolean hasPickaxe = Main.inventory.weapon != null && Main.inventory.weapon.name.equals("Pickaxe");
            return hasPickaxe ? "[E] Break Rock" : "[E] Examine Rock";
        }
        if (tile.objectId != null && tile.objectId.equals("map_chest_full")) return "[E] Search Chest";
        if (carryingBigLog && isWaterTile(nearbyTileX, nearbyTileY)) return "[E] Drop Log in River";
        if (nearbyNpc != null && "Thomas".equals(nearbyNpc.charName) && carryingBigLog) return "[E] Deliver Log";
        return "[E] Interact";
    }

    // ---- Helpers ----

    /**
     * If the area has a swapMap condition that is now met, loads and returns the target
     * area (with overrides applied). Otherwise returns the original area unchanged.
     */
    private WorldArea resolveSwap(WorldArea src) {
        String swapFile = src.resolveSwapMap(Main.flags);
        if (swapFile == null) return src;
        FileHandle f = findAssetFile(swapFile);
        if (f == null) f = Gdx.files.internal(swapFile);
        if (!f.exists()) return src;
        try {
            WorldArea swapped = WorldArea.load(f);
            swapped.applyOverrides(Main.flags);
            return swapped;
        } catch (Exception e) {
            Gdx.app.error("WorldScreen", "swapMap load failed: " + swapFile + " — " + e.getMessage());
            return src;
        }
    }

    // ---- Repair the Bridge quest ----

    private void updateBridgeQuest(float delta) {
        if (!Main.flags.is("repair_bridge_started")) return;
        if (area.sourceFile == null || !area.sourceFile.contains("area_forest")) return;

        // Floating log animation
        if (bigLogFloating) {
            bigLogFloatY += LOG_FLOAT_SPEED * delta;
            if (bigLogFloatY > area.height * TILE_SZ + TILE_SZ * 4) {
                bigLogFloating = false;
                Main.flags.set("repair_bridge_log_placed", 1);
            }
            return;
        }

        // Tree approach dialogue: fires once when adjacent to any dead_tree1 during beam search
        if (Main.flags.get("repair_bridge_choice") == 1
                && !Main.flags.is("repair_bridge_big_tree_cut")
                && !Main.flags.is("repair_bridge_tree_approached")) {
            int[][] adj = {{playerX,playerY},{playerX+1,playerY},{playerX-1,playerY},
                           {playerX,playerY+1},{playerX,playerY-1}};
            for (int[] p : adj) {
                if (inBounds(p[0], p[1]) && "map_dead_tree1".equals(area.tiles[p[0]][p[1]].objectId)) {
                    Main.flags.set("repair_bridge_tree_approached", 1);
                    launchCutsceneThen("scene_thomas_tree_approach", null);
                    return;
                }
            }
        }
    }

    private void handleThomasBridgeInteract(WorldArea.WorldNpc npc) {
        // Carrying log to Thomas → deliver it
        if (carryingBigLog) {
            carryingBigLog = false;
            launchCutsceneThen("scene_thomas_log_carry", () -> {
                checkBridgeCompleteOrStones(npc);
            });
            return;
        }

        // After log placed via float — Thomas tells you about extra damage
        if (Main.flags.is("repair_bridge_log_floated")
                && Main.flags.is("repair_bridge_log_placed")
                && Main.flags.get("repair_bridge_choice") != 2
                && !Main.flags.is("repair_bridge_float_return_played")) {
            Main.flags.set("repair_bridge_float_return_played", 1);
            Main.flags.set("repair_bridge_stones_target", 25);
            launchCutsceneThen("scene_thomas_float_return", () -> launchStonesPath(npc));
            return;
        }

        boolean logsDone   = Main.flags.is("repair_bridge_logs_done");
        boolean choiceMade = Main.flags.get("repair_bridge_choice") != 0;
        boolean stonesDone = Main.flags.is("repair_bridge_stones_done");
        boolean logPlaced  = Main.flags.is("repair_bridge_log_placed");

        // All conditions met → end scene
        if (logsDone && logPlaced && stonesDone) {
            completeBridgeQuest();
            return;
        }

        // Logs delivery phase
        if (!logsDone) {
            int logsHeld = Main.inventory.countInBag("Log");
            if (logsHeld == 0) {
                hintText  = "Thomas: I still need those logs. Bring me 12 total.";
                hintTimer = 3f;
                return;
            }
            int delivered = Main.flags.get("repair_bridge_logs_count");
            int take      = Math.min(logsHeld, 12 - delivered);
            Main.inventory.removeFromBag("Log", take);
            Main.inventory.save(Main.flags);
            delivered += take;
            Main.flags.set("repair_bridge_logs_count", delivered);
            if (delivered >= 12) {
                Main.flags.set("repair_bridge_logs_done", 1);
                launchCutsceneThen("scene_thomas_bridge_choice", null);
            } else {
                int remaining = 12 - delivered;
                hintText  = "Thomas: Thank you. We only need " + remaining + " more.";
                hintTimer = 3f;
            }
            return;
        }

        // Choice not yet made → show choice scene again
        if (!choiceMade) {
            launchCutsceneThen("scene_thomas_bridge_choice", null);
            return;
        }

        int choice = Main.flags.get("repair_bridge_choice");

        // Beam path: log not yet placed
        if (choice == 1 && !logPlaced) {
            if (!Main.flags.is("repair_bridge_big_tree_cut")) {
                hintText  = "Thomas: Follow me, let's find a big tree.";
                hintTimer = 2f;
            } else {
                hintText  = "Thomas: Bring the log back here when you're ready.";
                hintTimer = 2f;
            }
            return;
        }

        // Stones path (or after beam+carry needs stones too)
        if (!stonesDone) {
            if (!Main.flags.is("repair_bridge_stones_path_started")) {
                launchStonesPath(npc);
                return;
            }
            deliverStones();
            return;
        }

        // Log not placed yet (choice=stones path, need to go beam after?)
        if (!logPlaced) {
            launchCutsceneThen("scene_thomas_bridge_choice", null);
        }
    }

    private void launchStonesPath(WorldArea.WorldNpc npc) {
        Main.flags.set("repair_bridge_stones_path_started", 1);
        if (Main.flags.get("repair_bridge_stones_target") == 0) {
            Main.flags.set("repair_bridge_stones_target", Main.flags.is("repair_bridge_log_floated") ? 25 : 20);
        }
        launchCutsceneThen("scene_thomas_bridge_stones", null);
    }

    private void deliverStones() {
        int stonesHeld   = Main.inventory.countInBag("Rocks");
        int target       = Main.flags.get("repair_bridge_stones_target");
        if (target == 0) target = Main.flags.is("repair_bridge_log_floated") ? 25 : 20;
        int delivered    = Main.flags.get("repair_bridge_stones_count");
        int remaining    = target - delivered;
        if (stonesHeld == 0) {
            hintText  = "Thomas: I still need " + remaining + " more stones.";
            hintTimer = 3f;
            return;
        }
        int take = Math.min(stonesHeld, remaining);
        Main.inventory.removeFromBag("Rocks", take);
        Main.inventory.save(Main.flags);
        delivered += take;
        Main.flags.set("repair_bridge_stones_count", delivered);
        remaining = target - delivered;
        if (remaining <= 0) {
            Main.flags.set("repair_bridge_stones_done", 1);
            // Check if log is also placed; if beam not chosen yet, prompt for it
            if (Main.flags.get("repair_bridge_choice") == 2 && !Main.flags.is("repair_bridge_log_placed")) {
                hintText  = "Thomas: Stones are in. Now we need that beam.";
                hintTimer = 3f;
                Main.flags.set("repair_bridge_choice", 0); // reset choice so beam path opens
            } else if (Main.flags.is("repair_bridge_log_placed")) {
                completeBridgeQuest();
            }
        } else {
            hintText  = "Thomas: Thank you! We only need " + remaining + " more stones.";
            hintTimer = 3f;
        }
    }

    private void checkBridgeCompleteOrStones(WorldArea.WorldNpc npc) {
        if (Main.flags.is("repair_bridge_stones_done")) {
            completeBridgeQuest();
        } else if (!Main.flags.is("repair_bridge_stones_path_started")) {
            launchStonesPath(npc);
        } else {
            deliverStones();
        }
    }

    private void completeBridgeQuest() {
        // Replace broken bridge with fixed bridge and make those tiles walkable
        for (int tx = 0; tx < area.width; tx++) {
            for (int ty = 0; ty < area.height; ty++) {
                if ("building_Bridge_Broken".equals(area.tiles[tx][ty].objectId)) {
                    area.tiles[tx][ty].objectId = "building_Bridge_Fixed";
                    area.tiles[tx][ty].walkable = true;
                }
            }
        }
        launchCutsceneThen("scene_thomas_bridge_complete", null);
    }

    private void drawBigLog() {
        Texture logTex = textures.get("map_dead_tree1");
        if (logTex == null) return;
        game.batch.setColor(Color.WHITE);
        if (carryingBigLog) {
            // Draw tree draped over the player (offset upward so it's visible above the sprite)
            float px = visualX, py = visualY + TILE_SZ * 0.5f;
            game.batch.draw(logTex, px - TILE_SZ * 0.5f, py, TILE_SZ * 2f, TILE_SZ * 2f);
        } else if (bigLogFloating) {
            game.batch.draw(logTex, bigLogFloatX, bigLogFloatY, TILE_SZ * 2f, TILE_SZ * 2f);
        }
    }

    /** Launch a named cutscene and return to this WorldScreen after, optionally running a pre-return action. */
    private void launchCutsceneThen(String sceneId, Runnable preReturn) {
        FileHandle f = findAssetFile(sceneId + ".txt");
        if (f == null) {
            if (preReturn != null) preReturn.run();
            return;
        }
        try {
            CutsceneData data = CutsceneData.load(f);
            WorldScreen  self = this;
            game.setScreen(new CutsceneScreen(game, data, Main.flags, () -> {
                if (preReturn != null) preReturn.run();
                game.setScreen(self);
            }));
        } catch (Exception e) {
            Gdx.app.error("WorldScreen", "Cutscene load failed: " + sceneId);
            if (preReturn != null) preReturn.run();
        }
    }

    private void updateForestMusic() {
        Music current = forestTracks[forestTrackIdx];
        if (current == null) return;
        current.setVolume(Main.musicVolume);
        if (!current.isPlaying()) {
            forestTrackIdx = (forestTrackIdx + 1) % 2;
            Music next = forestTracks[forestTrackIdx];
            if (next != null) { next.setVolume(Main.musicVolume); next.play(); }
        }
    }

    private void stopForestMusic() {
        for (Music m : forestTracks) if (m != null) m.stop();
    }

    // Cave anchor positions → fighter name (anchors from area_forest.txt)
    private static final int[][] CAVE_ANCHORS = {
        {559,237}, {585,491}, {628,511}, {632,676}, {649,470}, {656,372},
        {671,609}, {691,174}, {713,313}, {725,712}, {742,490}, {745,295},
        {777,280}, {777,289}, {784,208}, {794,66},  {805,107}, {932,565}
    };
    private static final String[] CAVE_FIGHTERS = {
        "Hunter","Billy","Speen","Lark","Nathan","Evan",
        "Jaxon","Aaron","Sean","Brad","Weirdguard","Snowguard",
        "Tyler","Maxx","Anna","Emily","Ben","Aevan"
    };

    private String findCaveFighter(int tileX, int tileY) {
        int bestIdx  = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < CAVE_ANCHORS.length; i++) {
            int dx = tileX - CAVE_ANCHORS[i][0];
            int dy = tileY - CAVE_ANCHORS[i][1];
            int dist = dx * dx + dy * dy;
            if (dist < bestDist) { bestDist = dist; bestIdx = i; }
        }
        return bestIdx >= 0 ? CAVE_FIGHTERS[bestIdx] : null;
    }

    private Item createChestItem(String name) {
        if (name == null) return null;
        switch (name) {
            case "Sword":       { Item i = new Item("Sword",       "A sharp sword.",           Item.ItemSlot.WEAPON); i.iconName = "item_sword";       CutsceneScreen.applyKnownItemMods(i); return i; }
            case "Staff":       { Item i = new Item("Staff",       "A magical staff.",          Item.ItemSlot.WEAPON); i.iconName = "item_staff";       CutsceneScreen.applyKnownItemMods(i); return i; }
            case "Chest Armor": { Item i = new Item("Chest Armor", "Sturdy chest armor.",       Item.ItemSlot.BODY);   i.iconName = "item_chestarmor"; CutsceneScreen.applyKnownItemMods(i); return i; }
            case "Boots":       { Item i = new Item("Boots",       "Lightweight boots.",        Item.ItemSlot.SHOES);  i.iconName = "item_boots";      CutsceneScreen.applyKnownItemMods(i); return i; }
            case "Helmet":      { Item i = new Item("Helmet",      "A solid helmet.",           Item.ItemSlot.HELMET); i.iconName = "item_helmet";     CutsceneScreen.applyKnownItemMods(i); return i; }
            case "Pickaxe":     { Item i = new Item("Pickaxe",     "A sturdy pickaxe.",         Item.ItemSlot.WEAPON); i.iconName = "item_pickaxe";    CutsceneScreen.applyKnownItemMods(i); return i; }
            case "Axe":         { Item i = new Item("Axe",         "An axe for chopping wood.", Item.ItemSlot.WEAPON); i.iconName = "item_axe";        CutsceneScreen.applyKnownItemMods(i); return i; }
            default: return null;
        }
    }

    private boolean inBounds(int x, int y) {
        return x >= 0 && x < area.width && y >= 0 && y < area.height;
    }

    private boolean isWaterTile(int x, int y) {
        if (!inBounds(x, y)) return false;
        String bg = area.tiles[x][y].backgroundId;
        if (bg == null) return false;
        return bg.startsWith("tile_water") || bg.startsWith("tile_ocean");
    }

    private boolean canWalkTo(int x, int y) {
        if (!inBounds(x, y)) return false;
        if (!area.tiles[x][y].walkable) return false;
        if (isWaterTile(x, y)) return false; // requires water-walking item (not yet in game)
        return true;
    }

    @Override
    public void dispose() {
        whitePixel.dispose();
        for (Texture t : textures.values()) t.dispose();
        for (Texture t : npcPortraits.values()) if (t != null) t.dispose();
        for (Music m : forestTracks) if (m != null) m.dispose();
    }
}
