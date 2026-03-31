package com.mygame.tactics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.awt.FileDialog;
import java.awt.Frame;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JOptionPane;

/**
 * Dev-only map editor screen.
 *
 * Tile naming convention (assets/tiles/):
 *   tile_*      — ground/background textures (painted on the background layer)
 *   map_*       — obstacle objects (painted on object layer, auto-sets walkable=false)
 *   building_*  — interactable buildings (object layer, walkable=false, interactable=true)
 *
 * Controls:
 *   Left-click palette  — select brush
 *   Left-click map      — paint selected brush onto the tile
 *   Right-click map     — erase object layer first; if none, erase background
 *   Middle-drag / WASD  — pan camera
 *   Mouse wheel         — scroll palette
 *   Ctrl+S              — save
 *   Escape              — back to menu
 *
 * Overlay legend (on map tiles):
 *   Red tint       — not walkable
 *   Yellow dot TR  — interactable
 *   Cyan dot TL    — has trigger area ID
 */
public class MapEditorScreen implements Screen, InputProcessor {

    // ---- Layout ----
    private static final float TILE_SZ   = 48f;
    private static final float PALETTE_W = 200f;
    private static final float PROPS_W   = 200f;
    private static final float TOP_H     = 40f;
    private static final float MAP_X     = PALETTE_W;
    private static final float MAP_Y     = 0f;
    private static final float MAP_W     = 1280f - PALETTE_W - PROPS_W;  // 880
    private static final float MAP_H     = 720f  - TOP_H;                 // 680
    private static final float PROPS_X   = 1280f - PROPS_W;               // 1080

    // Palette tile grid
    private static final int   PAL_COLS  = 3;
    private static final float PAL_TSZ   = 56f;
    private static final float PAL_GAP   = 4f;
    private static final float PAL_MRG   = (PALETTE_W - PAL_COLS * PAL_TSZ - (PAL_COLS - 1) * PAL_GAP) / 2f;
    private static final float PAL_HDR_H = 24f;   // top header height

    // Palette section element heights
    private static final float SEC_HDR_H  = 18f;  // section label
    private static final float ERASE_H    = 28f;  // erase button
    private static final float TILE_ROW_H = PAL_TSZ + PAL_GAP;

    // Terrain fill buttons
    private static final float TERRAIN_BTN_W = 91f;
    private static final float TERRAIN_BTN_H = 26f;
    private static final float TERRAIN_MRG   = 6f;
    private static final float TERRAIN_GAP   = 6f;

    // Properties panel fixed Y positions (LibGDX Y=0 at bottom)
    private static final float PROP_TOP      = MAP_H - PAL_HDR_H;   // 656
    private static final float PROP_COORDS_Y = PROP_TOP  - 16f;     // 640
    private static final float PROP_BG_Y     = PROP_COORDS_Y - 16f; // 624
    private static final float PROP_OBJ_Y    = PROP_BG_Y - 16f;     // 608
    private static final float PROP_WALK_Y   = PROP_OBJ_Y - 30f;    // 578 (box bottom)
    private static final float PROP_INTER_Y  = PROP_WALK_Y - 28f;   // 550
    private static final float PROP_TRIG_LY  = PROP_INTER_Y - 32f;  // 518 (label baseline)
    private static final float PROP_TRIG_BY  = PROP_TRIG_LY - 22f;  // 496 (box bottom)
    private static final float PROP_TRIG_BH  = 20f;

    // Top-bar buttons
    private static final float BTN_BY = 720f - TOP_H + 6f;
    private static final float BTN_BH = 28f;
    private final Rectangle btnNew       = new Rectangle(8,    BTN_BY, 76, BTN_BH);
    private final Rectangle btnSave      = new Rectangle(90,   BTN_BY, 76, BTN_BH);
    private final Rectangle btnLoad      = new Rectangle(172,  BTN_BY, 76, BTN_BH);
    private final Rectangle btnResize    = new Rectangle(254,  BTN_BY, 76, BTN_BH);
    private final Rectangle btnModeWorld = new Rectangle(346,  BTN_BY, 96, BTN_BH);
    private final Rectangle btnModeCombat= new Rectangle(448,  BTN_BY, 114, BTN_BH);
    private final Rectangle btnBack      = new Rectangle(1196, BTN_BY, 76, BTN_BH);

    // Combat board grid constants
    private static final int   COMBAT_COLS    = 9;
    private static final int   COMBAT_ROWS    = 9;
    private static final float COMBAT_TILE_SZ = 70f;
    private static final float COMBAT_GRID_W  = COMBAT_COLS * COMBAT_TILE_SZ;
    private static final float COMBAT_GRID_H  = COMBAT_ROWS * COMBAT_TILE_SZ;
    private static final float COMBAT_GRID_X  = MAP_X + (MAP_W - COMBAT_GRID_W) / 2f;
    private static final float COMBAT_GRID_Y  = MAP_Y + (MAP_H - COMBAT_GRID_H) / 2f;

    // All character names (mirrors DraftScreen pool order)
    private static final String[] ALL_CHARS = {
        "Hunter","Sean","Jaxon","Evan","Billy","Aaron","Speen","Mason","Lark","Nathan",
        "Luke","Brad","GuardTower","Weirdguard","Stoneguard","Snowguard","Tyler",
        "Anna","Emily","Thomas","Ghia","Maxx","Ben","Aevan","Fescue"
    };

    // ---- Brush sentinels ----
    private static final String ERASE_BG    = "\0erase_bg";
    private static final String ERASE_OBJ   = "\0erase_obj";
    private static final String BRUSH_WALK  = "\0walk";
    private static final String BRUSH_INTER = "\0inter";
    private static final String BRUSH_SPAWN = "\0spawn";
    private static final String BRUSH_NPC   = "\0npc";

    private static final float NPC_ROW_H = 22f;

    // ---- Fields ----
    private final Main               game;
    private final OrthographicCamera camera;
    private final FitViewport        viewport;
    private final Texture            whitePixel;
    private final Texture            havenTileTexture;
    private final GlyphLayout        layout = new GlyphLayout();

    private final Map<String, Texture>   tileTextures = new LinkedHashMap<>();
    private final Array<String>          bgTileIds    = new Array<>();  // tile_*
    private final Array<String>          objTileIds   = new Array<>();  // map_* + building_*
    /** terrain name → { tileId1, tileId2 } used for alternating fill. */
    private final Map<String, String[]>  terrainPairs = new LinkedHashMap<>();

    private WorldArea area;
    private String    brush        = null;  // null=no action, ERASE_BG, ERASE_OBJ, BRUSH_WALK, BRUSH_INTER, BRUSH_NPC, or texture id
    private String    npcBrushChar = ALL_CHARS[0]; // selected character for NPC placement
    private boolean   paintWalkValue  = true;  // target value when dragging with BRUSH_WALK
    private boolean   paintInterValue = true;  // target value when dragging with BRUSH_INTER
    private String    pendingFillTerrain = null; // terrain name awaiting confirm-click
    private float     camX = 0f, camY = 0f;
    private float     palScrollY = 0f;

    // Middle-mouse drag
    private boolean midDragging = false;
    private float   midDragWX, midDragWY, midDragCamX, midDragCamY;

    // Hover and selected map tile
    private int hovX = -1, hovY = -1;
    private int selX = -1, selY = -1;

    // ---- Editor mode ----
    private enum EditorMode { WORLD, COMBAT, DIALOGUE }
    private EditorMode editorMode = EditorMode.WORLD;

    // Combat board state
    private BoardConfig.BoardType   combatBoardType  = BoardConfig.BoardType.FOREST;
    private final Array<int[]>      botSpawns        = new Array<>(); // {tileX, tileY, charIndex}
    private int                     selectedCharIndex = 0;
    private float                   combatPalScrollY  = 0f;
    private int                     combatHovX = -1, combatHovY = -1;

    // ---- Dialogue editor state ----
    private static final float DLG_ROW_H      = 38f;
    private static final float DLG_SPEAKER_W  = 140f;
    private static final float DLG_LIST_HDR_H = 30f;
    private static final float DLG_ADD_BTN_H  = 36f;  // fixed ADD BEAT bar at bottom

    private final Array<CutsceneData.DialogueBeat> dlgBeats  = new Array<>();
    private String  dlgBgPath      = null;  // full filename, e.g. "storybg_forest.png"
    private int     dlgSelIdx      = -1;    // selected beat index
    private String  dlgSpeaker     = null;  // null = NARRATOR
    private float   dlgPalScrollY  = 0f;
    private float   dlgBeatScrollY = 0f;

    private final Map<String, Texture> storybgTextures = new LinkedHashMap<>();
    private final Array<String>        storybgIds      = new Array<>();

    // Mode toggle buttons (top bar) — DIALOGUE added alongside WORLD/COMBAT
    private final Rectangle btnModeDialogue = new Rectangle(568, BTN_BY, 114, BTN_BH);

    // ---- Constructor ----

    public MapEditorScreen(Main game) {
        this.game = game;
        camera   = new OrthographicCamera();
        viewport = new FitViewport(1280, 720, camera);
        camera.position.set(640, 360, 0);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE); pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();
        havenTileTexture = new Texture("tile_haven.png");

        loadTileTextures();
        loadStorybgTextures();
        area = new WorldArea("new_area", 20, 20);
    }

    private void loadTileTextures() {
        tileTextures.clear();
        bgTileIds.clear();
        objTileIds.clear();

        // Try several candidate directories to support both Gradle run (workingDir=assets/)
        // and Eclipse launch (workingDir = project root or module root).
        String[] candidates = {".", "assets", "../assets", "../../assets"};
        for (String cand : candidates) {
            try {
                FileHandle dir = Gdx.files.local(cand);
                if (!dir.exists() || !dir.isDirectory()) continue;
                for (FileHandle f : dir.list(".png")) {
                    String id = f.nameWithoutExtension();
                    if (!id.startsWith("tile_") && !id.startsWith("map_") && !id.startsWith("building_")) continue;
                    if (tileTextures.containsKey(id)) continue; // already loaded
                    try {
                        Texture tex = new Texture(f); // load from exact path, not classpath
                        tileTextures.put(id, tex);
                        if (id.startsWith("tile_"))                                    bgTileIds.add(id);
                        else if (id.startsWith("map_") || id.startsWith("building_")) objTileIds.add(id);
                    } catch (Exception ignored) {}
                }
                if (!tileTextures.isEmpty()) break; // found tiles, stop searching
            } catch (Exception ignored) {}
        }
        buildTerrainPairs();
    }

    private void buildTerrainPairs() {
        terrainPairs.clear();
        // Dynamically detect fillable terrain names: tile IDs that end with one or more digits
        // e.g. tile_grass1 → "grass",  tile_water23 → "water".
        // Tiles without trailing digits (e.g. tile_coastleft) appear in the palette but not as fill buttons.
        LinkedHashMap<String, Array<String>> groups = new LinkedHashMap<>();
        for (String id : bgTileIds) {
            String name = id.substring("tile_".length()); // strip "tile_"
            int i = name.length() - 1;
            while (i >= 0 && name.charAt(i) >= '0' && name.charAt(i) <= '9') i--;
            if (i < 0 || i == name.length() - 1) continue; // all-digit or no trailing digit
            String terrain = name.substring(0, i + 1);
            if (!groups.containsKey(terrain)) groups.put(terrain, new Array<>());
            groups.get(terrain).add(id);
        }
        for (Map.Entry<String, Array<String>> e : groups.entrySet()) {
            Array<String> matches = e.getValue();
            terrainPairs.put(e.getKey(), new String[]{
                    matches.get(0),
                    matches.size > 1 ? matches.get(1) : matches.get(0)
            });
        }
    }

    /** Height of the TILE PROPS (walkable/interactable paint) section. */
    private static float propsSectionHeight() {
        return SEC_HDR_H + PAL_GAP + ERASE_H + PAL_GAP + ERASE_H + PAL_GAP + ERASE_H + 8f;
    }

    private float terrainSectionHeight() {
        if (terrainPairs.isEmpty()) return 6f;
        int rows = (terrainPairs.size() + 1) / 2;
        return 6f + SEC_HDR_H + PAL_GAP + rows * (TERRAIN_BTN_H + PAL_GAP) + 8f;
    }

    private void fillBackground(String terrain) {
        String[] pair = terrainPairs.get(terrain);
        if (pair == null) return;
        for (int x = 0; x < area.width; x++)
            for (int y = 0; y < area.height; y++)
                area.tiles[x][y].backgroundId = ((x + y) % 2 == 0) ? pair[0] : pair[1];
    }

    private WorldArea resizeArea(WorldArea old, int newW, int newH) {
        WorldArea next = new WorldArea(old.areaId, newW, newH);
        for (int x = 0; x < Math.min(old.width, newW); x++)
            for (int y = 0; y < Math.min(old.height, newH); y++)
                next.tiles[x][y] = old.tiles[x][y].copy();
        return next;
    }

    // ---- Screen lifecycle ----

    @Override public void show()   { Gdx.input.setInputProcessor(this); }
    @Override public void hide()   { Gdx.input.setInputProcessor(null); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void resize(int w, int h) { viewport.update(w, h); }

    // ---- Render ----

    @Override
    public void render(float delta) {
        // WASD / arrow key pan
        float spd = 300f * delta;
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT))  camX -= spd;
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) camX += spd;
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN))  camY -= spd;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP))    camY += spd;
        clampCamera();

        Vector3 mp = unproject(Gdx.input.getX(), Gdx.input.getY());

        if (editorMode == EditorMode.WORLD) {
            // Update hover tile
            hovX = hovY = -1;
            if (inMapArea(mp.x, mp.y)) {
                int tx = toTileX(mp.x), ty = toTileY(mp.y);
                if (inBounds(tx, ty)) { hovX = tx; hovY = ty; }
            }
            // Held left-click: paint
            if (!midDragging && Gdx.input.isButtonPressed(Input.Buttons.LEFT) && hovX >= 0)
                paintAt(hovX, hovY);
            // Held right-click: erase
            if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT) && hovX >= 0)
                eraseAt(hovX, hovY);
        } else if (editorMode == EditorMode.COMBAT) {
            // Update combat grid hover
            combatHovX = combatHovY = -1;
            int cgx = combatTileX(mp.x), cgy = combatTileY(mp.y);
            if (cgx >= 0 && cgx < COMBAT_COLS && cgy >= 0 && cgy < COMBAT_ROWS) {
                combatHovX = cgx; combatHovY = cgy;
            }
        }
        // DIALOGUE mode needs no per-frame hover tracking

        ScreenUtils.clear(0.07f, 0.07f, 0.10f, 1f);
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();
        if (editorMode == EditorMode.WORLD) {
            drawMap(game.batch);
            drawPalette(game.batch);
            drawProperties(game.batch);
        } else if (editorMode == EditorMode.COMBAT) {
            drawCombatGrid(game.batch);
            drawCombatPalette(game.batch);
            drawCombatProperties(game.batch);
        } else {
            drawDialogueBeatList(game.batch);
            drawDialoguePalette(game.batch);
            drawDialogueProperties(game.batch);
        }
        drawTopBar(game.batch);
        game.batch.end();
    }

    // ---- Paint / Erase ----

    private void paintAt(int tx, int ty) {
        if (brush == null) return;
        WorldTile t = area.tiles[tx][ty];
        if (brush == BRUSH_WALK)  { t.walkable     = paintWalkValue;  return; }
        if (brush == BRUSH_INTER) { t.interactable = paintInterValue; return; }
        if (brush == BRUSH_SPAWN) { area.spawnX = tx; area.spawnY = ty; return; }
        if (brush == BRUSH_NPC) {
            // Replace any existing NPC at this tile
            for (int i = area.npcs.size() - 1; i >= 0; i--) {
                WorldArea.WorldNpc n = area.npcs.get(i);
                if (n.x == tx && n.y == ty) area.npcs.remove(i);
            }
            WorldArea.WorldNpc npc = new WorldArea.WorldNpc();
            npc.charName      = npcBrushChar;
            npc.x             = tx;
            npc.y             = ty;
            npc.interactable  = true;
            npc.triggerAreaId = null;
            area.npcs.add(npc);
            return;
        }
        if (brush == ERASE_BG) {
            t.backgroundId = null;
        } else if (brush == ERASE_OBJ) {
            t.objectId    = null;
            t.walkable    = true;
            t.interactable = false;
        } else if (brush.startsWith("tile_")) {
            t.backgroundId = brush;
        } else if (brush.startsWith("map_")) {
            int tw = buildingTilesW(brush);
            int th = buildingTilesH(brush);
            for (int bx = tx; bx < tx + tw && bx < area.width; bx++) {
                for (int by = ty; by < ty + th && by < area.height; by++) {
                    WorldTile bt = area.tiles[bx][by];
                    bt.objectId    = brush;
                    bt.walkable    = false;
                    bt.interactable = false;
                    bt.isAnchor    = (bx == tx && by == ty);
                }
            }
        } else if (brush.startsWith("building_")) {
            int tw = buildingTilesW(brush);
            int th = buildingTilesH(brush);
            for (int bx = tx; bx < tx + tw && bx < area.width; bx++) {
                for (int by = ty; by < ty + th && by < area.height; by++) {
                    WorldTile bt = area.tiles[bx][by];
                    bt.objectId    = brush;
                    bt.walkable    = false;
                    bt.interactable = true;
                    bt.isAnchor    = (bx == tx && by == ty);
                }
            }
        }
    }

    /** Right-click: removes object layer first; if none, removes background. */
    private void eraseAt(int tx, int ty) {
        WorldTile t = area.tiles[tx][ty];
        if (t.objectId != null) {
            // Clear the entire footprint for any multi-tile object
            String bid = t.objectId;
            int[] anchor = buildingAnchor(bid, tx, ty);
            int ax = anchor[0], ay = anchor[1];
            int tw = buildingTilesW(bid), th = buildingTilesH(bid);
            for (int bx = ax; bx < ax + tw && bx < area.width; bx++) {
                for (int by = ay; by < ay + th && by < area.height; by++) {
                    if (bid.equals(area.tiles[bx][by].objectId)) {
                        area.tiles[bx][by].objectId    = null;
                        area.tiles[bx][by].walkable    = true;
                        area.tiles[bx][by].interactable = false;
                        area.tiles[bx][by].isAnchor    = false;
                    }
                }
            }
        } else {
            t.backgroundId = null;
        }
    }

    // ---- Draw: Map ----

    private void drawMap(SpriteBatch b) {
        b.setColor(0.08f, 0.08f, 0.08f, 1f);
        b.draw(whitePixel, MAP_X, MAP_Y, MAP_W, MAP_H);

        int x0 = Math.max(0, (int)(camX / TILE_SZ));
        int y0 = Math.max(0, (int)(camY / TILE_SZ));
        int x1 = Math.min(area.width,  x0 + (int)(MAP_W / TILE_SZ) + 2);
        int y1 = Math.min(area.height, y0 + (int)(MAP_H / TILE_SZ) + 2);

        // ---- Pass 1: checkerboard + background tile textures ----
        for (int tx = x0; tx < x1; tx++) {
            for (int ty = y0; ty < y1; ty++) {
                float dx = MAP_X + tx * TILE_SZ - camX;
                float dy = MAP_Y + ty * TILE_SZ - camY;
                boolean even = (tx + ty) % 2 == 0;
                b.setColor(even ? 0.14f : 0.18f, even ? 0.14f : 0.18f, even ? 0.18f : 0.22f, 1f);
                b.draw(whitePixel, dx, dy, TILE_SZ, TILE_SZ);
                WorldTile tile = area.tiles[tx][ty];
                if (tile.backgroundId != null) {
                    Texture bg = tileTextures.get(tile.backgroundId);
                    if (bg != null) {
                        b.setColor(Color.WHITE);
                        b.draw(bg, dx, dy, TILE_SZ, TILE_SZ);
                    }
                }
            }
        }

        // ---- Pass 2: objects — expanded range so buildings near/past the edge still render ----
        int buf = 10;
        int ox0 = Math.max(0, x0 - buf), oy0 = Math.max(0, y0 - buf);
        int ox1 = Math.min(area.width, x1 + buf), oy1 = Math.min(area.height, y1 + buf);
        for (int tx = ox0; tx < ox1; tx++) {
            for (int ty = oy0; ty < oy1; ty++) {
                WorldTile tile = area.tiles[tx][ty];
                if (tile.objectId == null) continue;
                Texture obj = tileTextures.get(tile.objectId);
                float adx = MAP_X + tx * TILE_SZ - camX;
                float ady = MAP_Y + ty * TILE_SZ - camY;
                if (obj == null) {
                    b.setColor(0.8f, 0f, 0.8f, 0.5f);
                    b.draw(whitePixel, adx, ady, TILE_SZ, TILE_SZ);
                    continue;
                }
                // Scan DOWN first to find the anchor row, then LEFT to find the anchor column.
                int ay = ty;
                while (ay > 0 && !area.tiles[tx][ay].isAnchor
                        && tile.objectId.equals(area.tiles[tx][ay - 1].objectId)) ay--;
                int ax = tx;
                while (ax > 0 && !area.tiles[ax][ay].isAnchor
                        && tile.objectId.equals(area.tiles[ax - 1][ay].objectId)) ax--;
                int tileH = Math.max(1, obj.getHeight() / 64);
                int srcX = (tx - ax) * 64;
                int srcY = (tileH - 1 - (ty - ay)) * 64;
                b.setColor(Color.WHITE);
                b.draw(obj, adx, ady, TILE_SZ, TILE_SZ, srcX, srcY, 64, 64, false, false);
            }
        }

        // ---- Pass 3: overlays, grid lines, hover, selection ----
        for (int tx = x0; tx < x1; tx++) {
            for (int ty = y0; ty < y1; ty++) {
                float dx = MAP_X + tx * TILE_SZ - camX;
                float dy = MAP_Y + ty * TILE_SZ - camY;
                WorldTile tile = area.tiles[tx][ty];

                // Blocked overlay (red tint)
                if (!tile.walkable) {
                    b.setColor(0.9f, 0.1f, 0.1f, 0.20f);
                    b.draw(whitePixel, dx, dy, TILE_SZ, TILE_SZ);
                }
                // Interactable dot — yellow, top-right
                if (tile.interactable) {
                    b.setColor(1f, 0.9f, 0f, 1f);
                    b.draw(whitePixel, dx + TILE_SZ - 10f, dy + TILE_SZ - 10f, 8f, 8f);
                }
                // Trigger dot — cyan, top-left
                if (tile.triggerAreaId != null) {
                    b.setColor(0f, 0.9f, 1f, 1f);
                    b.draw(whitePixel, dx + 2f, dy + TILE_SZ - 10f, 8f, 8f);
                }

                // Grid lines (right and top edges)
                b.setColor(0.28f, 0.28f, 0.32f, 1f);
                b.draw(whitePixel, dx + TILE_SZ - 1, dy,               1,       TILE_SZ);
                b.draw(whitePixel, dx,               dy + TILE_SZ - 1, TILE_SZ, 1);

                // Hover: brush preview
                if (tx == hovX && ty == hovY) {
                    if (brush != null && brush != ERASE_BG && brush != ERASE_OBJ) {
                        Texture bt = tileTextures.get(brush);
                        if (bt != null) {
                            b.setColor(1f, 1f, 1f, 0.55f);
                            if (brush.startsWith("building_")) {
                                b.draw(bt, dx, dy, buildingTilesW(brush) * TILE_SZ, buildingTilesH(brush) * TILE_SZ);
                            } else {
                                b.draw(bt, dx, dy, TILE_SZ, TILE_SZ);
                            }
                        }
                    }
                    b.setColor(1f, 1f, 1f, 0.18f);
                    b.draw(whitePixel, dx, dy, TILE_SZ, TILE_SZ);
                }

                // Selection outline
                if (tx == selX && ty == selY) {
                    b.setColor(Color.YELLOW);
                    b.draw(whitePixel, dx,               dy,               TILE_SZ, 2);
                    b.draw(whitePixel, dx,               dy + TILE_SZ - 2, TILE_SZ, 2);
                    b.draw(whitePixel, dx,               dy,               2, TILE_SZ);
                    b.draw(whitePixel, dx + TILE_SZ - 2, dy,               2, TILE_SZ);
                }
            }
        }

        // Spawn point marker
        if (area.spawnX >= 0 && area.spawnY >= 0) {
            float sx = MAP_X + area.spawnX * TILE_SZ - camX;
            float sy = MAP_Y + area.spawnY * TILE_SZ - camY;
            b.setColor(0.2f, 0.6f, 1f, 0.55f);
            b.draw(whitePixel, sx, sy, TILE_SZ, TILE_SZ);
            b.setColor(0.2f, 0.6f, 1f, 1f);
            b.draw(whitePixel, sx,               sy,               TILE_SZ, 2);
            b.draw(whitePixel, sx,               sy + TILE_SZ - 2, TILE_SZ, 2);
            b.draw(whitePixel, sx,               sy,               2, TILE_SZ);
            b.draw(whitePixel, sx + TILE_SZ - 2, sy,               2, TILE_SZ);
            game.font.getData().setScale(0.55f);
            game.font.setColor(Color.WHITE);
            game.font.draw(b, "S", sx + TILE_SZ / 2f - 4f, sy + TILE_SZ / 2f + 6f);
        }

        // NPC markers
        for (WorldArea.WorldNpc npc : area.npcs) {
            float nx = MAP_X + npc.x * TILE_SZ - camX;
            float ny = MAP_Y + npc.y * TILE_SZ - camY;
            // Purple tint overlay
            b.setColor(0.50f, 0.10f, 0.68f, 0.45f);
            b.draw(whitePixel, nx, ny, TILE_SZ, TILE_SZ);
            // Purple border
            b.setColor(0.70f, 0.25f, 0.90f, 1f);
            b.draw(whitePixel, nx,               ny,               TILE_SZ, 2);
            b.draw(whitePixel, nx,               ny + TILE_SZ - 2, TILE_SZ, 2);
            b.draw(whitePixel, nx,               ny,               2, TILE_SZ);
            b.draw(whitePixel, nx + TILE_SZ - 2, ny,               2, TILE_SZ);
            // Character name (first 6 chars truncated to fit)
            game.font.getData().setScale(0.40f);
            game.font.setColor(Color.WHITE);
            String label = npc.charName.length() > 7 ? npc.charName.substring(0, 7) : npc.charName;
            game.font.draw(b, label, nx + 3f, ny + TILE_SZ / 2f + 6f, TILE_SZ - 6f, -1, true);
            // Yellow dot (top-right) for interactable
            if (npc.interactable) {
                b.setColor(1f, 0.90f, 0f, 1f);
                b.draw(whitePixel, nx + TILE_SZ - 10f, ny + TILE_SZ - 10f, 8f, 8f);
            }
        }

        // Map border
        b.setColor(0.35f, 0.35f, 0.45f, 1f);
        float bx = MAP_X - camX, by2 = MAP_Y - camY;
        float bw = area.width * TILE_SZ, bh = area.height * TILE_SZ;
        b.draw(whitePixel, bx,      by2,      bw,  1);
        b.draw(whitePixel, bx,      by2 + bh, bw,  1);
        b.draw(whitePixel, bx,      by2,      1,   bh);
        b.draw(whitePixel, bx + bw, by2,      1,   bh + 1);
    }

    // ---- Draw: Palette ----
    //
    // Palette layout uses a "position from top" system.
    // palY(p, h) converts a content position to world Y (bottom of element).
    //   p = distance from palette content top, in pixels (increases downward)
    //   h = element height

    private float palY(float p, float h) {
        return (MAP_H - PAL_HDR_H) - p - h + palScrollY;
    }

    /** Computes the content-top position of the objects section. */
    private float objSectionPos() {
        int bgRows = (bgTileIds.size + PAL_COLS - 1) / PAL_COLS;
        return propsSectionHeight() + terrainSectionHeight() + SEC_HDR_H + PAL_GAP + ERASE_H + PAL_GAP + bgRows * TILE_ROW_H + 10f;
    }

    private static float npcSectionHeight() {
        return 6f + SEC_HDR_H + PAL_GAP + ALL_CHARS.length * (NPC_ROW_H + PAL_GAP) + 8f;
    }

    /** Total palette content height for scroll clamping. */
    private float palContentHeight() {
        int objRows = (objTileIds.size + PAL_COLS - 1) / PAL_COLS;
        return objSectionPos() + SEC_HDR_H + PAL_GAP + ERASE_H + PAL_GAP + objRows * TILE_ROW_H + 8f + npcSectionHeight();
    }

    private void drawPalette(SpriteBatch b) {
        b.setColor(0.10f, 0.10f, 0.14f, 1f);
        b.draw(whitePixel, 0, 0, PALETTE_W, MAP_H);

        // Top header
        b.setColor(0.16f, 0.16f, 0.22f, 1f);
        b.draw(whitePixel, 0, MAP_H - PAL_HDR_H, PALETTE_W, PAL_HDR_H);
        game.font.getData().setScale(0.45f);
        game.font.setColor(Color.LIGHT_GRAY);
        game.font.draw(b, "TILES", 10, MAP_H - 6f);

        // ---- TILE PROPS section (walkable / interactable paint brushes) ----
        float p = 0f;
        drawPalSectionHeader(b, "TILE PROPS", p);
        p += SEC_HDR_H + PAL_GAP;
        drawPalEraseBtn(b, p, brush == BRUSH_WALK,  "PAINT WALKABLE",     new Color(0.15f, 0.55f, 0.15f, 1f));
        p += ERASE_H + PAL_GAP;
        drawPalEraseBtn(b, p, brush == BRUSH_INTER, "PAINT INTERACTABLE", new Color(0.55f, 0.50f, 0.05f, 1f));
        p += ERASE_H + PAL_GAP;
        drawPalEraseBtn(b, p, brush == BRUSH_SPAWN, "SET SPAWN POINT",    new Color(0.10f, 0.35f, 0.65f, 1f));
        p += ERASE_H + 8f;

        // ---- TERRAIN FILL section ----
        if (!terrainPairs.isEmpty()) {
            drawPalSectionHeader(b, "TERRAIN FILL", p);
            p += SEC_HDR_H + PAL_GAP;
            int ti = 0;
            for (Map.Entry<String, String[]> e : terrainPairs.entrySet()) {
                int col = ti % 2, row = ti / 2;
                float bx = TERRAIN_MRG + col * (TERRAIN_BTN_W + TERRAIN_GAP);
                float by = palY(p + row * (TERRAIN_BTN_H + PAL_GAP), TERRAIN_BTN_H);
                if (by + TERRAIN_BTN_H >= 0 && by <= MAP_H - PAL_HDR_H)
                    drawTerrainButton(b, e.getKey(), e.getValue()[0], bx, by);
                ti++;
            }
            int tRows = (terrainPairs.size() + 1) / 2;
            p += tRows * (TERRAIN_BTN_H + PAL_GAP) + 8f;
        }

        // ---- BACKGROUNDS section ----
        drawPalSectionHeader(b, "BACKGROUNDS", p);
        p += SEC_HDR_H + PAL_GAP;

        // Erase BG
        drawPalEraseBtn(b, p, brush == ERASE_BG, "ERASE BG");
        p += ERASE_H + PAL_GAP;

        // tile_ textures
        for (int i = 0; i < bgTileIds.size; i++) {
            int col = i % PAL_COLS;
            int row = i / PAL_COLS;
            float tx = PAL_MRG + col * (PAL_TSZ + PAL_GAP);
            float ty = palY(p + row * TILE_ROW_H, PAL_TSZ);
            if (ty + PAL_TSZ >= 0 && ty <= MAP_H - PAL_HDR_H)
                drawPalTile(b, bgTileIds.get(i), tx, ty);
        }
        int bgRows = (bgTileIds.size + PAL_COLS - 1) / PAL_COLS;
        p += bgRows * TILE_ROW_H + 10f;

        // ---- OBJECTS section ----
        drawPalSectionHeader(b, "OBJECTS", p);
        p += SEC_HDR_H + PAL_GAP;

        // Erase OBJ
        drawPalEraseBtn(b, p, brush == ERASE_OBJ, "ERASE OBJ");
        p += ERASE_H + PAL_GAP;

        // map_ + building_ textures
        for (int i = 0; i < objTileIds.size; i++) {
            int col = i % PAL_COLS;
            int row = i / PAL_COLS;
            float tx = PAL_MRG + col * (PAL_TSZ + PAL_GAP);
            float ty = palY(p + row * TILE_ROW_H, PAL_TSZ);
            if (ty + PAL_TSZ >= 0 && ty <= MAP_H - PAL_HDR_H)
                drawPalTile(b, objTileIds.get(i), tx, ty);
        }

        if (bgTileIds.size == 0 && objTileIds.size == 0) {
            game.font.getData().setScale(0.38f);
            game.font.setColor(Color.DARK_GRAY);
            game.font.draw(b, "No tiles found.\nAdd PNGs to\nassets/ with\ntile_ / map_ /\nbuilding_ prefix",
                    8f, MAP_H - PAL_HDR_H - 30f, PALETTE_W - 16f, -1, true);
        }

        // Advance p past the objects section
        int objRows = (objTileIds.size + PAL_COLS - 1) / PAL_COLS;
        p = objSectionPos() + SEC_HDR_H + PAL_GAP + ERASE_H + PAL_GAP + objRows * TILE_ROW_H + 8f;

        // ---- NPCS section ----
        p += 6f;
        drawPalSectionHeader(b, "NPCS", p);
        p += SEC_HDR_H + PAL_GAP;
        for (int i = 0; i < ALL_CHARS.length; i++) {
            float ry = palY(p + i * (NPC_ROW_H + PAL_GAP), NPC_ROW_H);
            if (ry + NPC_ROW_H < 0 || ry > MAP_H - PAL_HDR_H) continue;
            boolean sel = brush == BRUSH_NPC && ALL_CHARS[i].equals(npcBrushChar);
            b.setColor(sel ? new Color(0.42f, 0.12f, 0.52f, 1f) : new Color(0.20f, 0.20f, 0.28f, 1f));
            b.draw(whitePixel, PAL_MRG, ry, PALETTE_W - PAL_MRG * 2, NPC_ROW_H);
            // Border
            b.setColor(sel ? new Color(0.70f, 0.30f, 0.85f, 1f) : new Color(0.32f, 0.32f, 0.42f, 1f));
            b.draw(whitePixel, PAL_MRG, ry, PALETTE_W - PAL_MRG * 2, 1);
            game.font.getData().setScale(0.40f);
            game.font.setColor(sel ? Color.WHITE : Color.GRAY);
            game.font.draw(b, ALL_CHARS[i], PAL_MRG + 6f, ry + NPC_ROW_H / 2f + 6f);
        }

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    private void drawPalSectionHeader(SpriteBatch b, String label, float p) {
        float y = palY(p, SEC_HDR_H);
        if (y + SEC_HDR_H < 0 || y > MAP_H - PAL_HDR_H) return;
        b.setColor(0.18f, 0.18f, 0.26f, 1f);
        b.draw(whitePixel, 2, y, PALETTE_W - 4, SEC_HDR_H);
        game.font.getData().setScale(0.38f);
        game.font.setColor(new Color(0.7f, 0.7f, 0.85f, 1f));
        game.font.draw(b, label, 8f, y + SEC_HDR_H - 3f);
    }

    private void drawPalEraseBtn(SpriteBatch b, float p, boolean selected, String label) {
        drawPalEraseBtn(b, p, selected, label, new Color(0.6f, 0.15f, 0.15f, 1f));
    }

    private void drawPalEraseBtn(SpriteBatch b, float p, boolean selected, String label, Color activeColor) {
        float y = palY(p, ERASE_H);
        if (y + ERASE_H < 0 || y > MAP_H - PAL_HDR_H) return;
        Color offColor = new Color(activeColor.r * 0.3f, activeColor.g * 0.3f, activeColor.b * 0.3f, 1f);
        b.setColor(selected ? activeColor : offColor);
        b.draw(whitePixel, PAL_MRG, y, PALETTE_W - PAL_MRG * 2, ERASE_H);
        game.font.getData().setScale(0.38f);
        game.font.setColor(selected ? Color.WHITE : new Color(0.55f, 0.55f, 0.55f, 1f));
        layout.setText(game.font, label);
        game.font.draw(b, label,
                PAL_MRG + (PALETTE_W - PAL_MRG * 2 - layout.width) / 2f,
                y + ERASE_H / 2f + layout.height / 2f);
    }

    private void drawPalTile(SpriteBatch b, String id, float tx, float ty) {
        boolean sel = id.equals(brush);
        // Border
        b.setColor(sel ? Color.GOLD
                : id.startsWith("building_") ? new Color(0.3f, 0.6f, 0.3f, 1f)
                : new Color(0.25f, 0.25f, 0.32f, 1f));
        b.draw(whitePixel, tx - 1, ty - 1, PAL_TSZ + 2, PAL_TSZ + 2);
        // Background
        b.setColor(0.18f, 0.18f, 0.22f, 1f);
        b.draw(whitePixel, tx, ty, PAL_TSZ, PAL_TSZ);
        // Texture
        Texture tex = tileTextures.get(id);
        if (tex != null) {
            b.setColor(Color.WHITE);
            b.draw(tex, tx, ty, PAL_TSZ, PAL_TSZ);
        }
        // Building indicator: green tint
        if (id.startsWith("building_")) {
            b.setColor(0f, 1f, 0.3f, 0.15f);
            b.draw(whitePixel, tx, ty, PAL_TSZ, PAL_TSZ);
        }
        // Short name label
        game.font.getData().setScale(0.28f);
        game.font.setColor(sel ? Color.GOLD : Color.GRAY);
        String shortName = id.contains("_") ? id.substring(id.indexOf('_') + 1) : id;
        game.font.draw(b, shortName, tx + 2, ty + 10, PAL_TSZ - 4, -1, true);
    }

    private void drawTerrainButton(SpriteBatch b, String name, String sampleId, float bx, float by) {
        boolean pending = name.equals(pendingFillTerrain);
        b.setColor(pending ? new Color(0.40f, 0.10f, 0.10f, 1f) : new Color(0.18f, 0.18f, 0.26f, 1f));
        b.draw(whitePixel, bx, by, TERRAIN_BTN_W, TERRAIN_BTN_H);
        // Texture swatch on the left
        Texture sample = tileTextures.get(sampleId);
        float swatchSz = TERRAIN_BTN_H - 4f;
        if (sample != null) {
            b.setColor(Color.WHITE);
            b.draw(sample, bx + 2, by + 2, swatchSz, swatchSz);
        }
        // Border
        b.setColor(pending ? new Color(1f, 0.3f, 0.3f, 1f) : new Color(0.35f, 0.35f, 0.50f, 1f));
        b.draw(whitePixel, bx,                     by,                     TERRAIN_BTN_W, 1);
        b.draw(whitePixel, bx,                     by + TERRAIN_BTN_H - 1, TERRAIN_BTN_W, 1);
        b.draw(whitePixel, bx,                     by,                     1, TERRAIN_BTN_H);
        b.draw(whitePixel, bx + TERRAIN_BTN_W - 1, by,                     1, TERRAIN_BTN_H);
        // Name or confirm prompt
        game.font.getData().setScale(0.38f);
        game.font.setColor(pending ? Color.RED : Color.LIGHT_GRAY);
        String label = pending ? "CONFIRM?" : name;
        game.font.draw(b, label, bx + swatchSz + 5f, by + TERRAIN_BTN_H / 2f + 5f);
    }

    // ---- Draw: Properties ----

    private void drawProperties(SpriteBatch b) {
        b.setColor(0.10f, 0.10f, 0.14f, 1f);
        b.draw(whitePixel, PROPS_X, 0, PROPS_W, MAP_H);
        b.setColor(0.16f, 0.16f, 0.22f, 1f);
        b.draw(whitePixel, PROPS_X, MAP_H - PAL_HDR_H, PROPS_W, PAL_HDR_H);
        game.font.getData().setScale(0.45f);
        game.font.setColor(Color.LIGHT_GRAY);
        game.font.draw(b, "PROPERTIES", PROPS_X + 8, MAP_H - 6f);

        if (selX < 0 || selY < 0) {
            game.font.getData().setScale(0.40f);
            game.font.setColor(Color.DARK_GRAY);
            game.font.draw(b, "Select a tile\non the map",
                    PROPS_X + 10, PROP_TOP - 14f, PROPS_W - 20f, -1, true);
            game.font.getData().setScale(1.0f);
            game.font.setColor(Color.WHITE);
            return;
        }

        WorldTile tile = area.tiles[selX][selY];

        game.font.getData().setScale(0.45f);
        game.font.setColor(Color.CYAN);
        game.font.draw(b, "Tile (" + selX + ", " + selY + ")", PROPS_X + 10, PROP_COORDS_Y);

        game.font.getData().setScale(0.38f);
        game.font.setColor(new Color(0.6f, 0.8f, 0.6f, 1f));
        game.font.draw(b, "BG: " + (tile.backgroundId == null ? "(none)" : tile.backgroundId),
                PROPS_X + 10, PROP_BG_Y, PROPS_W - 20f, -1, true);

        game.font.setColor(tile.objectId != null && tile.objectId.startsWith("building_")
                ? new Color(0.5f, 1f, 0.5f, 1f) : new Color(0.8f, 0.6f, 0.6f, 1f));
        game.font.draw(b, "OBJ: " + (tile.objectId == null ? "(none)" : tile.objectId),
                PROPS_X + 10, PROP_OBJ_Y, PROPS_W - 20f, -1, true);

        drawToggle(b, PROPS_X + 10, PROP_WALK_Y,  "Walkable",     tile.walkable,     new Color(0.2f, 0.8f, 0.2f, 1f));
        drawToggle(b, PROPS_X + 10, PROP_INTER_Y, "Interactable", tile.interactable, new Color(1f, 0.85f, 0f, 1f));

        game.font.getData().setScale(0.40f);
        game.font.setColor(Color.LIGHT_GRAY);
        game.font.draw(b, "Trigger area:", PROPS_X + 10, PROP_TRIG_LY);

        b.setColor(0.18f, 0.18f, 0.26f, 1f);
        b.draw(whitePixel, PROPS_X + 10, PROP_TRIG_BY, PROPS_W - 20f, PROP_TRIG_BH);
        b.setColor(0.35f, 0.35f, 0.50f, 1f);
        b.draw(whitePixel, PROPS_X + 10, PROP_TRIG_BY,                  PROPS_W - 20f, 1);
        b.draw(whitePixel, PROPS_X + 10, PROP_TRIG_BY + PROP_TRIG_BH - 1, PROPS_W - 20f, 1);

        game.font.getData().setScale(0.36f);
        game.font.setColor(tile.triggerAreaId == null
                ? new Color(0.4f, 0.4f, 0.4f, 1f) : Color.CYAN);
        game.font.draw(b,
                tile.triggerAreaId == null ? "none  (click to set)" : tile.triggerAreaId,
                PROPS_X + 14, PROP_TRIG_BY + PROP_TRIG_BH - 4f, PROPS_W - 24f, -1, true);

        // Legend
        float ly = PROP_TRIG_BY - 28f;
        game.font.getData().setScale(0.34f);
        game.font.setColor(Color.DARK_GRAY);
        game.font.draw(b, "Legend:", PROPS_X + 10, ly); ly -= 16f;
        b.setColor(0.9f, 0.1f, 0.1f, 0.45f);
        b.draw(whitePixel, PROPS_X + 10, ly - 9, 11, 11);
        game.font.setColor(Color.GRAY);
        game.font.draw(b, " not walkable", PROPS_X + 23, ly); ly -= 14f;
        b.setColor(1f, 0.9f, 0f, 1f);
        b.draw(whitePixel, PROPS_X + 10, ly - 9, 8, 8);
        game.font.draw(b, " interactable", PROPS_X + 23, ly); ly -= 14f;
        b.setColor(0f, 0.9f, 1f, 1f);
        b.draw(whitePixel, PROPS_X + 10, ly - 9, 8, 8);
        game.font.draw(b, " trigger", PROPS_X + 23, ly); ly -= 14f;
        b.setColor(0f, 1f, 0.3f, 0.3f);
        b.draw(whitePixel, PROPS_X + 10, ly - 9, 11, 11);
        game.font.draw(b, " building", PROPS_X + 23, ly);

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    private void drawToggle(SpriteBatch b, float x, float y, String label, boolean on, Color onColor) {
        b.setColor(on ? onColor : new Color(0.22f, 0.22f, 0.28f, 1f));
        b.draw(whitePixel, x, y, 16, 16);
        if (on) {
            b.setColor(Color.WHITE);
            b.draw(whitePixel, x + 3, y + 6, 4, 3);
            b.draw(whitePixel, x + 5, y + 3, 3, 6);
        }
        game.font.getData().setScale(0.42f);
        game.font.setColor(on ? Color.WHITE : Color.GRAY);
        game.font.draw(b, label, x + 20, y + 13);
    }

    // ---- Draw: Top bar ----

    private void drawTopBar(SpriteBatch b) {
        b.setColor(0.08f, 0.08f, 0.12f, 1f);
        b.draw(whitePixel, 0, 720f - TOP_H, 1280, TOP_H);
        b.setColor(0.25f, 0.25f, 0.35f, 1f);
        b.draw(whitePixel, 0, 720f - TOP_H, 1280, 1f);

        drawSmallButton(b, btnNew,  "NEW");
        drawSmallButton(b, btnSave, "SAVE");
        drawSmallButton(b, btnLoad, "LOAD");
        if (editorMode == EditorMode.WORLD) drawSmallButton(b, btnResize, "RESIZE");
        drawSmallButton(b, btnBack, "BACK");

        // Mode toggle buttons
        boolean worldActive    = editorMode == EditorMode.WORLD;
        boolean combatActive   = editorMode == EditorMode.COMBAT;
        boolean dialogueActive = editorMode == EditorMode.DIALOGUE;
        b.setColor(worldActive  ? new Color(0.10f, 0.35f, 0.10f, 1f) : new Color(0.12f, 0.12f, 0.18f, 1f));
        b.draw(whitePixel, btnModeWorld.x, btnModeWorld.y, btnModeWorld.width, btnModeWorld.height);
        b.setColor(worldActive  ? new Color(0.2f, 0.8f, 0.2f, 1f) : new Color(0.35f, 0.35f, 0.50f, 1f));
        b.draw(whitePixel, btnModeWorld.x, btnModeWorld.y, btnModeWorld.width, 1);
        b.draw(whitePixel, btnModeWorld.x, btnModeWorld.y + btnModeWorld.height - 1, btnModeWorld.width, 1);
        b.draw(whitePixel, btnModeWorld.x, btnModeWorld.y, 1, btnModeWorld.height);
        b.draw(whitePixel, btnModeWorld.x + btnModeWorld.width - 1, btnModeWorld.y, 1, btnModeWorld.height);
        game.font.getData().setScale(0.42f);
        game.font.setColor(worldActive ? Color.WHITE : Color.GRAY);
        layout.setText(game.font, "WORLD");
        game.font.draw(b, "WORLD",
                btnModeWorld.x + (btnModeWorld.width - layout.width) / 2f,
                btnModeWorld.y + (btnModeWorld.height + layout.height) / 2f);

        b.setColor(combatActive ? new Color(0.30f, 0.15f, 0.05f, 1f) : new Color(0.12f, 0.12f, 0.18f, 1f));
        b.draw(whitePixel, btnModeCombat.x, btnModeCombat.y, btnModeCombat.width, btnModeCombat.height);
        b.setColor(combatActive ? new Color(1f, 0.6f, 0.1f, 1f) : new Color(0.35f, 0.35f, 0.50f, 1f));
        b.draw(whitePixel, btnModeCombat.x, btnModeCombat.y, btnModeCombat.width, 1);
        b.draw(whitePixel, btnModeCombat.x, btnModeCombat.y + btnModeCombat.height - 1, btnModeCombat.width, 1);
        b.draw(whitePixel, btnModeCombat.x, btnModeCombat.y, 1, btnModeCombat.height);
        b.draw(whitePixel, btnModeCombat.x + btnModeCombat.width - 1, btnModeCombat.y, 1, btnModeCombat.height);
        game.font.setColor(combatActive ? Color.WHITE : Color.GRAY);
        layout.setText(game.font, "COMBAT");
        game.font.draw(b, "COMBAT",
                btnModeCombat.x + (btnModeCombat.width - layout.width) / 2f,
                btnModeCombat.y + (btnModeCombat.height + layout.height) / 2f);

        b.setColor(dialogueActive ? new Color(0.10f, 0.10f, 0.40f, 1f) : new Color(0.12f, 0.12f, 0.18f, 1f));
        b.draw(whitePixel, btnModeDialogue.x, btnModeDialogue.y, btnModeDialogue.width, btnModeDialogue.height);
        b.setColor(dialogueActive ? new Color(0.4f, 0.4f, 1f, 1f) : new Color(0.35f, 0.35f, 0.50f, 1f));
        b.draw(whitePixel, btnModeDialogue.x, btnModeDialogue.y, btnModeDialogue.width, 1);
        b.draw(whitePixel, btnModeDialogue.x, btnModeDialogue.y + btnModeDialogue.height - 1, btnModeDialogue.width, 1);
        b.draw(whitePixel, btnModeDialogue.x, btnModeDialogue.y, 1, btnModeDialogue.height);
        b.draw(whitePixel, btnModeDialogue.x + btnModeDialogue.width - 1, btnModeDialogue.y, 1, btnModeDialogue.height);
        game.font.setColor(dialogueActive ? Color.WHITE : Color.GRAY);
        layout.setText(game.font, "DIALOGUE");
        game.font.draw(b, "DIALOGUE",
                btnModeDialogue.x + (btnModeDialogue.width - layout.width) / 2f,
                btnModeDialogue.y + (btnModeDialogue.height + layout.height) / 2f);

        game.font.getData().setScale(0.44f);
        game.font.setColor(Color.LIGHT_GRAY);
        if (editorMode == EditorMode.WORLD) {
            game.font.draw(b, area.areaId + "   (" + area.width + " x " + area.height + ")",
                    700, BTN_BY + BTN_BH - 4f);
            game.font.getData().setScale(0.34f);
            game.font.setColor(new Color(0.4f, 0.4f, 0.5f, 1f));
            game.font.draw(b, "WASD: pan   LClick: paint   RClick: erase   Ctrl+S: save",
                    700, BTN_BY + 10f);
        } else if (editorMode == EditorMode.COMBAT) {
            game.font.draw(b, "COMBAT BOARD  —  " + combatBoardType.name() + "  —  " + botSpawns.size + " spawn(s)",
                    700, BTN_BY + BTN_BH - 4f);
            game.font.getData().setScale(0.34f);
            game.font.setColor(new Color(0.4f, 0.4f, 0.5f, 1f));
            game.font.draw(b, "LClick grid: place spawn   RClick: remove   Select char in palette first",
                    700, BTN_BY + 10f);
        } else {
            game.font.draw(b, "DIALOGUE  —  " + dlgBeats.size + " beat(s)",
                    700, BTN_BY + BTN_BH - 4f);
            game.font.getData().setScale(0.34f);
            game.font.setColor(new Color(0.4f, 0.4f, 0.5f, 1f));
            game.font.draw(b, "Left: pick speaker/BG   Center: select/add beats   Right: edit   Ctrl+S: save",
                    700, BTN_BY + 10f);
        }

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    private void drawSmallButton(SpriteBatch b, Rectangle r, String label) {
        b.setColor(0.18f, 0.18f, 0.26f, 1f);
        b.draw(whitePixel, r.x, r.y, r.width, r.height);
        b.setColor(0.35f, 0.35f, 0.50f, 1f);
        b.draw(whitePixel, r.x,               r.y,             r.width, 1);
        b.draw(whitePixel, r.x,               r.y + r.height - 1, r.width, 1);
        b.draw(whitePixel, r.x,               r.y,             1, r.height);
        b.draw(whitePixel, r.x + r.width - 1, r.y,             1, r.height);
        game.font.getData().setScale(0.48f);
        game.font.setColor(Color.LIGHT_GRAY);
        layout.setText(game.font, label);
        game.font.draw(b, label,
                r.x + (r.width  - layout.width)  / 2f,
                r.y + (r.height + layout.height) / 2f);
        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    // ---- InputProcessor ----

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        Vector3 w = unproject(screenX, screenY);

        if (button == Input.Buttons.MIDDLE) {
            midDragging = true;
            midDragWX = w.x; midDragWY = w.y;
            midDragCamX = camX; midDragCamY = camY;
            return true;
        }

        if (button == Input.Buttons.LEFT) {
            if (btnNew   .contains(w.x, w.y)) {
                if (editorMode == EditorMode.WORLD) doNew();
                else if (editorMode == EditorMode.COMBAT) { botSpawns.clear(); combatBoardType = BoardConfig.BoardType.FOREST; }
                else { dlgBeats.clear(); dlgBgPath = null; dlgSelIdx = -1; }
                return true;
            }
            if (btnSave  .contains(w.x, w.y)) {
                if (editorMode == EditorMode.WORLD) doSave();
                else if (editorMode == EditorMode.COMBAT) doSaveCombat();
                else saveDialogue();
                return true;
            }
            if (btnLoad  .contains(w.x, w.y)) {
                if (editorMode == EditorMode.WORLD) doLoad();
                else if (editorMode == EditorMode.COMBAT) doLoadCombat();
                else loadDialogue();
                return true;
            }
            if (btnResize.contains(w.x, w.y) && editorMode == EditorMode.WORLD) { doResize(); return true; }
            if (btnModeWorld   .contains(w.x, w.y)) { editorMode = EditorMode.WORLD;    return true; }
            if (btnModeCombat  .contains(w.x, w.y)) { editorMode = EditorMode.COMBAT;   return true; }
            if (btnModeDialogue.contains(w.x, w.y)) { editorMode = EditorMode.DIALOGUE; return true; }
            if (btnBack  .contains(w.x, w.y)) { game.setScreen(new MenuScreen(game)); return true; }

            if (editorMode == EditorMode.WORLD) {
                if (w.x < PALETTE_W && w.y < MAP_H) {
                    handlePaletteClick(w.x, w.y);
                    return true;
                }
                if (w.x >= PROPS_X && w.y < MAP_H && selX >= 0) {
                    handlePropertiesClick(w.x, w.y);
                    return true;
                }
                if (inMapArea(w.x, w.y)) {
                    int tx = toTileX(w.x), ty = toTileY(w.y);
                    if (inBounds(tx, ty)) {
                        if (brush == BRUSH_WALK) {
                            paintWalkValue = !area.tiles[tx][ty].walkable;
                            paintAt(tx, ty);
                        } else if (brush == BRUSH_INTER) {
                            paintInterValue = !area.tiles[tx][ty].interactable;
                            paintAt(tx, ty);
                        } else if (tx == selX && ty == selY) {
                            selX = selY = -1;
                        } else {
                            selX = tx; selY = ty; paintAt(tx, ty);
                        }
                    }
                    return true;
                }
            } else if (editorMode == EditorMode.COMBAT) {
                // Combat mode palette click
                if (w.x < PALETTE_W && w.y < MAP_H) {
                    handleCombatPaletteClick(w.x, w.y);
                    return true;
                }
                // Combat grid click — place/remove spawn
                int cgx = combatTileX(w.x), cgy = combatTileY(w.y);
                if (cgx >= 0 && cgx < COMBAT_COLS && cgy >= 0 && cgy < COMBAT_ROWS) {
                    int[] existing = getSpawnAt(cgx, cgy);
                    if (existing != null) {
                        botSpawns.removeValue(existing, true);
                    } else {
                        botSpawns.add(new int[]{cgx, cgy, selectedCharIndex});
                    }
                    return true;
                }
            } else {
                // Dialogue mode
                if (w.x < PALETTE_W && w.y < MAP_H) {
                    handleDialoguePaletteClick(w.x, w.y);
                    return true;
                }
                if (w.x >= PROPS_X && w.y < MAP_H) {
                    handleDialoguePropertiesClick(w.x, w.y);
                    return true;
                }
                if (inMapArea(w.x, w.y)) {
                    handleDialogueBeatListClick(w.x, w.y);
                    return true;
                }
            }
        }

        if (button == Input.Buttons.RIGHT) {
            if (editorMode == EditorMode.WORLD && inMapArea(w.x, w.y)) {
                int tx = toTileX(w.x), ty = toTileY(w.y);
                if (inBounds(tx, ty)) {
                    if (brush == BRUSH_NPC) {
                        for (int i = area.npcs.size() - 1; i >= 0; i--) {
                            WorldArea.WorldNpc n = area.npcs.get(i);
                            if (n.x == tx && n.y == ty) { area.npcs.remove(i); break; }
                        }
                    } else {
                        eraseAt(tx, ty);
                    }
                }
                return true;
            }
            if (editorMode == EditorMode.COMBAT) {
                int cgx = combatTileX(w.x), cgy = combatTileY(w.y);
                int[] existing = getSpawnAt(cgx, cgy);
                if (existing != null) { botSpawns.removeValue(existing, true); return true; }
            }
        }

        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        if (midDragging) {
            Vector3 w = unproject(screenX, screenY);
            camX = midDragCamX - (w.x - midDragWX);
            camY = midDragCamY - (w.y - midDragWY);
            clampCamera();
            return true;
        }
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button == Input.Buttons.MIDDLE) midDragging = false;
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        Vector3 w = unproject(Gdx.input.getX(), Gdx.input.getY());
        if (w.x < PALETTE_W) {
            if (editorMode == EditorMode.WORLD) {
                palScrollY += amountY * TILE_ROW_H;
                float maxScroll = Math.max(0, palContentHeight() - (MAP_H - PAL_HDR_H));
                palScrollY = MathUtils.clamp(palScrollY, 0, maxScroll);
            } else if (editorMode == EditorMode.COMBAT) {
                combatPalScrollY += amountY * 22f;
                float maxScroll = Math.max(0, ALL_CHARS.length * 22f - (MAP_H - PAL_HDR_H - 90f));
                combatPalScrollY = MathUtils.clamp(combatPalScrollY, 0, maxScroll);
            } else {
                dlgPalScrollY += amountY * NPC_ROW_H;
                float maxScroll = Math.max(0, dlgPalContentHeight() - (MAP_H - PAL_HDR_H));
                dlgPalScrollY = MathUtils.clamp(dlgPalScrollY, 0, maxScroll);
            }
        } else if (inMapArea(w.x, w.y) && editorMode == EditorMode.DIALOGUE) {
            dlgBeatScrollY += amountY * (DLG_ROW_H + 2f);
            float listH = MAP_H - PAL_HDR_H - (DLG_ADD_BTN_H + 8f);
            float maxScroll = Math.max(0, dlgBeats.size * (DLG_ROW_H + 2f) - listH);
            dlgBeatScrollY = MathUtils.clamp(dlgBeatScrollY, 0, maxScroll);
        }
        return true;
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            if (selX >= 0) { selX = selY = -1; return true; } // first Escape: deselect
            game.setScreen(new MenuScreen(game)); return true;  // second Escape: back to menu
        }
        if (keycode == Input.Keys.S && Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)) {
            if (editorMode == EditorMode.WORLD) doSave();
            else if (editorMode == EditorMode.COMBAT) doSaveCombat();
            else saveDialogue();
            return true;
        }
        return false;
    }

    @Override public boolean keyUp(int k)            { return false; }
    @Override public boolean keyTyped(char c)        { return false; }
    @Override public boolean touchCancelled(int sx, int sy, int p, int btn) { return false; }
    @Override public boolean mouseMoved(int sx, int sy) { return false; }

    // ---- Palette click ----

    private void handlePaletteClick(float wx, float wy) {
        // TILE PROPS section
        float p = 0f;
        p += SEC_HDR_H + PAL_GAP;
        float walkY = palY(p, ERASE_H);
        if (wy >= walkY && wy < walkY + ERASE_H) { brush = (brush == BRUSH_WALK ? null : BRUSH_WALK); return; }
        p += ERASE_H + PAL_GAP;
        float interY = palY(p, ERASE_H);
        if (wy >= interY && wy < interY + ERASE_H) { brush = (brush == BRUSH_INTER ? null : BRUSH_INTER); return; }
        p += ERASE_H + PAL_GAP;
        float spawnBtnY = palY(p, ERASE_H);
        if (wy >= spawnBtnY && wy < spawnBtnY + ERASE_H) { brush = (brush == BRUSH_SPAWN ? null : BRUSH_SPAWN); return; }
        p += ERASE_H + 8f;

        // TERRAIN FILL section
        if (!terrainPairs.isEmpty()) {
            p += SEC_HDR_H + PAL_GAP;
            int ti = 0;
            for (Map.Entry<String, String[]> e : terrainPairs.entrySet()) {
                int col = ti % 2, row = ti / 2;
                float bx = TERRAIN_MRG + col * (TERRAIN_BTN_W + TERRAIN_GAP);
                float by = palY(p + row * (TERRAIN_BTN_H + PAL_GAP), TERRAIN_BTN_H);
                if (wx >= bx && wx < bx + TERRAIN_BTN_W && wy >= by && wy < by + TERRAIN_BTN_H) {
                    if (e.getKey().equals(pendingFillTerrain)) {
                        fillBackground(e.getKey());
                        pendingFillTerrain = null;
                    } else {
                        pendingFillTerrain = e.getKey();
                    }
                    return;
                }
                ti++;
            }
            int tRows = (terrainPairs.size() + 1) / 2;
            p += tRows * (TERRAIN_BTN_H + PAL_GAP) + 8f;
        }
        // Clicking outside terrain buttons clears any pending fill
        pendingFillTerrain = null;

        // BACKGROUNDS section
        p += SEC_HDR_H + PAL_GAP;
        // Erase BG button
        float eraseBgBottom = palY(p, ERASE_H);
        if (wy >= eraseBgBottom && wy < eraseBgBottom + ERASE_H) { brush = ERASE_BG; return; }
        p += ERASE_H + PAL_GAP;
        // BG tiles
        for (int i = 0; i < bgTileIds.size; i++) {
            int col = i % PAL_COLS, row = i / PAL_COLS;
            float tx = PAL_MRG + col * (PAL_TSZ + PAL_GAP);
            float ty = palY(p + row * TILE_ROW_H, PAL_TSZ);
            if (wx >= tx && wx < tx + PAL_TSZ && wy >= ty && wy < ty + PAL_TSZ) { brush = bgTileIds.get(i); return; }
        }
        int bgRows = (bgTileIds.size + PAL_COLS - 1) / PAL_COLS;
        p += bgRows * TILE_ROW_H + 10f;

        // OBJECTS section
        p += SEC_HDR_H + PAL_GAP;
        // Erase OBJ button
        float eraseObjBottom = palY(p, ERASE_H);
        if (wy >= eraseObjBottom && wy < eraseObjBottom + ERASE_H) { brush = ERASE_OBJ; return; }
        p += ERASE_H + PAL_GAP;
        // OBJ tiles
        for (int i = 0; i < objTileIds.size; i++) {
            int col = i % PAL_COLS, row = i / PAL_COLS;
            float tx = PAL_MRG + col * (PAL_TSZ + PAL_GAP);
            float ty = palY(p + row * TILE_ROW_H, PAL_TSZ);
            if (wx >= tx && wx < tx + PAL_TSZ && wy >= ty && wy < ty + PAL_TSZ) { brush = objTileIds.get(i); return; }
        }

        // NPCS section
        int objRowsC = (objTileIds.size + PAL_COLS - 1) / PAL_COLS;
        p = objSectionPos() + SEC_HDR_H + PAL_GAP + ERASE_H + PAL_GAP + objRowsC * TILE_ROW_H + 8f + 6f;
        p += SEC_HDR_H + PAL_GAP;
        for (int i = 0; i < ALL_CHARS.length; i++) {
            float ry = palY(p + i * (NPC_ROW_H + PAL_GAP), NPC_ROW_H);
            if (wy >= ry && wy < ry + NPC_ROW_H) {
                npcBrushChar = ALL_CHARS[i];
                brush = BRUSH_NPC;
                return;
            }
        }
    }

    // ---- Properties click ----

    private void handlePropertiesClick(float wx, float wy) {
        WorldTile tile = area.tiles[selX][selY];

        if (wx >= PROPS_X + 10 && wx < PROPS_X + 26 && wy >= PROP_WALK_Y && wy < PROP_WALK_Y + 16) {
            tile.walkable = !tile.walkable; return;
        }
        if (wx >= PROPS_X + 10 && wx < PROPS_X + 26 && wy >= PROP_INTER_Y && wy < PROP_INTER_Y + 16) {
            tile.interactable = !tile.interactable; return;
        }
        if (wx >= PROPS_X + 10 && wx < PROPS_X + PROPS_W - 10
                && wy >= PROP_TRIG_BY && wy < PROP_TRIG_BY + PROP_TRIG_BH) {
            Gdx.input.getTextInput(new Input.TextInputListener() {
                @Override public void input(String text) {
                    tile.triggerAreaId = text.trim().isEmpty() ? null : text.trim();
                }
                @Override public void canceled() {}
            }, "Trigger Area ID", tile.triggerAreaId == null ? "" : tile.triggerAreaId,
                    "area name, or blank to clear");
        }
    }

    // ---- Actions ----

    private void doNew() {
        String name = JOptionPane.showInputDialog(null, "Area name:", area.areaId);
        if (name == null) return;
        String dims = JOptionPane.showInputDialog(null, "Dimensions (W x H):", area.width + "x" + area.height);
        if (dims == null) return;
        try {
            String[] p = dims.toLowerCase().split("x");
            int w = MathUtils.clamp(Integer.parseInt(p[0].trim()), 5, 100);
            int h = MathUtils.clamp(Integer.parseInt(p[1].trim()), 5, 100);
            area = new WorldArea(name.trim().replace(" ", "_"), w, h);
            camX = camY = 0; selX = selY = -1;
        } catch (Exception ignored) {}
    }

    private void doSave() {
        FileDialog fd = new FileDialog((Frame) null, "Save Area", FileDialog.SAVE);
        fd.setFile(area.areaId + ".txt");
        fd.setFilenameFilter((dir, name) -> name.endsWith(".txt"));
        fd.setVisible(true);
        String dir = fd.getDirectory();
        String file = fd.getFile();
        if (dir == null || file == null) return;
        if (!file.endsWith(".txt")) file += ".txt";
        try {
            area.save(Gdx.files.absolute(dir + file));
            Gdx.app.log("MapEditor", "Saved: " + dir + file);
        } catch (Exception e) {
            Gdx.app.error("MapEditor", "Save failed: " + e.getMessage());
        }
    }

    private void doLoad() {
        FileDialog fd = new FileDialog((Frame) null, "Load Area", FileDialog.LOAD);
        fd.setFilenameFilter((dir, name) -> name.endsWith(".txt"));
        fd.setVisible(true);
        String dir = fd.getDirectory();
        String file = fd.getFile();
        if (dir == null || file == null) return;
        try {
            area = WorldArea.load(Gdx.files.absolute(dir + file));
            camX = camY = 0; selX = selY = -1;
        } catch (Exception e) {
            Gdx.app.error("MapEditor", "Load failed: " + e.getMessage());
        }
    }

    private void doResize() {
        String dims = JOptionPane.showInputDialog(null, "New dimensions (W x H):", area.width + "x" + area.height);
        if (dims == null) return;
        try {
            String[] p = dims.toLowerCase().split("x");
            int newW = MathUtils.clamp(Integer.parseInt(p[0].trim()), 5, 100);
            int newH = MathUtils.clamp(Integer.parseInt(p[1].trim()), 5, 100);
            area = resizeArea(area, newW, newH);
            clampCamera();
            selX = selY = -1;
        } catch (Exception ignored) {}
    }

    // ---- Helpers ----

    /** Number of tiles wide this building occupies (texture.width / 64). */
    private int buildingTilesW(String id) {
        Texture tex = tileTextures.get(id);
        return tex != null ? Math.max(1, tex.getWidth() / 64) : 1;
    }

    /** Number of tiles tall this building occupies (texture.height / 64). */
    private int buildingTilesH(String id) {
        Texture tex = tileTextures.get(id);
        return tex != null ? Math.max(1, tex.getHeight() / 64) : 1;
    }

    /**
     * Given any tile that is part of a building footprint, returns the anchor
     * (bottom-left) tile of that building by scanning left then down.
     * Result: {anchorX, anchorY}
     */
    private int[] buildingAnchor(String bid, int tx, int ty) {
        int ax = tx, ay = ty;
        while (ax > 0 && inBounds(ax - 1, ay) && bid.equals(area.tiles[ax - 1][ay].objectId)) ax--;
        while (ay > 0 && inBounds(ax, ay - 1) && bid.equals(area.tiles[ax][ay - 1].objectId)) ay--;
        return new int[]{ax, ay};
    }

    private Vector3 unproject(int sx, int sy) {
        return camera.unproject(new Vector3(sx, sy, 0));
    }

    private boolean inMapArea(float wx, float wy) {
        return wx >= MAP_X && wx < MAP_X + MAP_W && wy >= MAP_Y && wy < MAP_Y + MAP_H;
    }

    private int toTileX(float wx) { return (int)((wx - MAP_X + camX) / TILE_SZ); }
    private int toTileY(float wy) { return (int)((wy - MAP_Y + camY) / TILE_SZ); }

    private boolean inBounds(int tx, int ty) {
        return tx >= 0 && tx < area.width && ty >= 0 && ty < area.height;
    }

    private void clampCamera() {
        camX = MathUtils.clamp(camX, 0, Math.max(0, area.width  * TILE_SZ - MAP_W));
        camY = MathUtils.clamp(camY, 0, Math.max(0, area.height * TILE_SZ - MAP_H));
    }

    // ---- Combat mode: draw ----

    private void drawCombatGrid(SpriteBatch b) {
        // Background fill for map area
        b.setColor(0.08f, 0.08f, 0.08f, 1f);
        b.draw(whitePixel, MAP_X, MAP_Y, MAP_W, MAP_H);

        String[] groundTextures = getCombatGroundTextures(combatBoardType);
        Texture texA = tileTextures.get(groundTextures[0]);
        Texture texB = tileTextures.get(groundTextures[1]);
        BoardConfig cfg = boardConfigForType(combatBoardType);

        for (int gx = 0; gx < COMBAT_COLS; gx++) {
            for (int gy = 0; gy < COMBAT_ROWS; gy++) {
                float dx = COMBAT_GRID_X + gx * COMBAT_TILE_SZ;
                float dy = COMBAT_GRID_Y + gy * COMBAT_TILE_SZ;

                // Checkerboard: texture if available, else fall back to board color
                boolean even = (gx + gy) % 2 == 0;
                Texture ground = even ? texA : texB;
                if (ground != null) {
                    b.setColor(Color.WHITE);
                    b.draw(ground, dx, dy, COMBAT_TILE_SZ, COMBAT_TILE_SZ);
                } else {
                    b.setColor(even ? cfg.tileColorA : cfg.tileColorB);
                    b.draw(whitePixel, dx, dy, COMBAT_TILE_SZ, COMBAT_TILE_SZ);
                }

                // Deployment zone overlay: rows 7-8 = bot regular zone (orange tint)
                if (gy >= 7) {
                    b.setColor(1f, 0.5f, 0.1f, 0.12f);
                    b.draw(whitePixel, dx, dy, COMBAT_TILE_SZ, COMBAT_TILE_SZ);
                } else if (gy >= 4) {
                    b.setColor(1f, 0.5f, 0.1f, 0.06f);
                    b.draw(whitePixel, dx, dy, COMBAT_TILE_SZ, COMBAT_TILE_SZ);
                }

                // Haven tile (center = 4,4)
                if (gx == 4 && gy == 4) {
                    b.setColor(Color.WHITE);
                    b.draw(havenTileTexture, dx, dy, COMBAT_TILE_SZ, COMBAT_TILE_SZ);
                }

                // Grid lines
                b.setColor(0.22f, 0.22f, 0.28f, 1f);
                b.draw(whitePixel, dx + COMBAT_TILE_SZ - 1, dy, 1, COMBAT_TILE_SZ);
                b.draw(whitePixel, dx, dy + COMBAT_TILE_SZ - 1, COMBAT_TILE_SZ, 1);
            }
        }

        // Draw spawn markers
        for (int[] sp : botSpawns) {
            int gx = sp[0], gy = sp[1];
            String name = ALL_CHARS[sp[2]];
            float dx = COMBAT_GRID_X + gx * COMBAT_TILE_SZ;
            float dy = COMBAT_GRID_Y + gy * COMBAT_TILE_SZ;
            b.setColor(0.8f, 0.25f, 0.1f, 0.85f);
            b.draw(whitePixel, dx + 3, dy + 3, COMBAT_TILE_SZ - 6, COMBAT_TILE_SZ - 6);
            b.setColor(1f, 0.5f, 0.2f, 1f);
            b.draw(whitePixel, dx + 3, dy + 3, COMBAT_TILE_SZ - 6, 2);
            b.draw(whitePixel, dx + 3, dy + COMBAT_TILE_SZ - 5, COMBAT_TILE_SZ - 6, 2);
            b.draw(whitePixel, dx + 3, dy + 3, 2, COMBAT_TILE_SZ - 6);
            b.draw(whitePixel, dx + COMBAT_TILE_SZ - 5, dy + 3, 2, COMBAT_TILE_SZ - 6);
            game.font.getData().setScale(0.36f);
            game.font.setColor(Color.WHITE);
            game.font.draw(b, name, dx + 4, dy + COMBAT_TILE_SZ - 8f, COMBAT_TILE_SZ - 8f, -1, true);
        }

        // Hover highlight
        if (combatHovX >= 0) {
            float dx = COMBAT_GRID_X + combatHovX * COMBAT_TILE_SZ;
            float dy = COMBAT_GRID_Y + combatHovY * COMBAT_TILE_SZ;
            b.setColor(1f, 1f, 1f, 0.15f);
            b.draw(whitePixel, dx, dy, COMBAT_TILE_SZ, COMBAT_TILE_SZ);
            b.setColor(1f, 1f, 1f, 0.55f);
            b.draw(whitePixel, dx, dy, COMBAT_TILE_SZ, 1);
            b.draw(whitePixel, dx, dy + COMBAT_TILE_SZ - 1, COMBAT_TILE_SZ, 1);
            b.draw(whitePixel, dx, dy, 1, COMBAT_TILE_SZ);
            b.draw(whitePixel, dx + COMBAT_TILE_SZ - 1, dy, 1, COMBAT_TILE_SZ);
        }

        // Border
        b.setColor(0.45f, 0.45f, 0.55f, 1f);
        b.draw(whitePixel, COMBAT_GRID_X - 1, COMBAT_GRID_Y - 1, COMBAT_GRID_W + 2, 1);
        b.draw(whitePixel, COMBAT_GRID_X - 1, COMBAT_GRID_Y + COMBAT_GRID_H, COMBAT_GRID_W + 2, 1);
        b.draw(whitePixel, COMBAT_GRID_X - 1, COMBAT_GRID_Y - 1, 1, COMBAT_GRID_H + 2);
        b.draw(whitePixel, COMBAT_GRID_X + COMBAT_GRID_W, COMBAT_GRID_Y - 1, 1, COMBAT_GRID_H + 2);

        // Legend
        game.font.getData().setScale(0.36f);
        game.font.setColor(new Color(1f, 0.5f, 0.1f, 0.7f));
        game.font.draw(b, "Bot spawn zone (rows 4-8)", COMBAT_GRID_X, COMBAT_GRID_Y - 8f);

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    private void drawCombatPalette(SpriteBatch b) {
        b.setColor(0.10f, 0.10f, 0.14f, 1f);
        b.draw(whitePixel, 0, 0, PALETTE_W, MAP_H);
        b.setColor(0.16f, 0.16f, 0.22f, 1f);
        b.draw(whitePixel, 0, MAP_H - PAL_HDR_H, PALETTE_W, PAL_HDR_H);
        game.font.getData().setScale(0.45f);
        game.font.setColor(Color.LIGHT_GRAY);
        game.font.draw(b, "COMBAT PALETTE", 8, MAP_H - 6f);

        // Board type section
        float secY = MAP_H - PAL_HDR_H - SEC_HDR_H;
        b.setColor(0.18f, 0.18f, 0.26f, 1f);
        b.draw(whitePixel, 2, secY, PALETTE_W - 4, SEC_HDR_H);
        game.font.getData().setScale(0.38f);
        game.font.setColor(new Color(0.7f, 0.7f, 0.85f, 1f));
        game.font.draw(b, "BOARD TYPE", 8f, secY + SEC_HDR_H - 3f);

        BoardConfig.BoardType[] types = BoardConfig.BoardType.values();
        float btnW = (PALETTE_W - 12f) / types.length;
        float btnY = secY - PAL_GAP - TERRAIN_BTN_H;
        for (int i = 0; i < types.length; i++) {
            float bx = 6f + i * (btnW + 2f);
            boolean sel = types[i] == combatBoardType;
            b.setColor(sel ? new Color(0.25f, 0.45f, 0.25f, 1f) : new Color(0.14f, 0.14f, 0.20f, 1f));
            b.draw(whitePixel, bx, btnY, btnW, TERRAIN_BTN_H);
            b.setColor(sel ? new Color(0.4f, 0.9f, 0.4f, 1f) : new Color(0.30f, 0.30f, 0.45f, 1f));
            b.draw(whitePixel, bx, btnY, btnW, 1);
            b.draw(whitePixel, bx, btnY + TERRAIN_BTN_H - 1, btnW, 1);
            game.font.getData().setScale(0.36f);
            game.font.setColor(sel ? Color.WHITE : Color.GRAY);
            layout.setText(game.font, types[i].name());
            game.font.draw(b, types[i].name(), bx + (btnW - layout.width) / 2f, btnY + TERRAIN_BTN_H / 2f + layout.height / 2f);
        }

        // Characters section
        float charSecY = btnY - PAL_GAP - SEC_HDR_H;
        b.setColor(0.18f, 0.18f, 0.26f, 1f);
        b.draw(whitePixel, 2, charSecY, PALETTE_W - 4, SEC_HDR_H);
        game.font.getData().setScale(0.38f);
        game.font.setColor(new Color(0.7f, 0.7f, 0.85f, 1f));
        game.font.draw(b, "BOT CHARACTERS", 8f, charSecY + SEC_HDR_H - 3f);

        float listTop = charSecY;
        float rowH = 22f;
        for (int i = 0; i < ALL_CHARS.length; i++) {
            float ry = listTop - PAL_GAP - (i + 1) * rowH + combatPalScrollY;
            if (ry + rowH < 0 || ry > listTop) continue;
            boolean sel = (i == selectedCharIndex);
            b.setColor(sel ? new Color(0.25f, 0.12f, 0.05f, 1f) : new Color(0.14f, 0.14f, 0.20f, 1f));
            b.draw(whitePixel, 4, ry, PALETTE_W - 8, rowH - 2);
            if (sel) {
                b.setColor(1f, 0.5f, 0.2f, 1f);
                b.draw(whitePixel, 4, ry, 3, rowH - 2);
            }
            game.font.getData().setScale(0.40f);
            game.font.setColor(sel ? Color.WHITE : Color.LIGHT_GRAY);
            game.font.draw(b, ALL_CHARS[i], 12, ry + rowH - 5f);
        }

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    private void drawCombatProperties(SpriteBatch b) {
        b.setColor(0.10f, 0.10f, 0.14f, 1f);
        b.draw(whitePixel, PROPS_X, 0, PROPS_W, MAP_H);
        b.setColor(0.16f, 0.16f, 0.22f, 1f);
        b.draw(whitePixel, PROPS_X, MAP_H - PAL_HDR_H, PROPS_W, PAL_HDR_H);
        game.font.getData().setScale(0.45f);
        game.font.setColor(Color.LIGHT_GRAY);
        game.font.draw(b, "SPAWNS", PROPS_X + 8, MAP_H - 6f);

        float y = MAP_H - PAL_HDR_H - 20f;
        game.font.getData().setScale(0.40f);
        game.font.setColor(Color.CYAN);
        game.font.draw(b, botSpawns.size + " bot spawn(s)", PROPS_X + 8, y);
        y -= 20f;

        for (int[] sp : botSpawns) {
            game.font.getData().setScale(0.38f);
            game.font.setColor(new Color(1f, 0.6f, 0.3f, 1f));
            game.font.draw(b, ALL_CHARS[sp[2]] + " (" + sp[0] + "," + sp[1] + ")",
                    PROPS_X + 8, y, PROPS_W - 16f, -1, true);
            y -= 18f;
            if (y < 20f) break;
        }

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    private void handleCombatPaletteClick(float wx, float wy) {
        // Board type buttons
        BoardConfig.BoardType[] types = BoardConfig.BoardType.values();
        float secY   = MAP_H - PAL_HDR_H - SEC_HDR_H;
        float btnW   = (PALETTE_W - 12f) / types.length;
        float btnY   = secY - PAL_GAP - TERRAIN_BTN_H;
        for (int i = 0; i < types.length; i++) {
            float bx = 6f + i * (btnW + 2f);
            if (wx >= bx && wx < bx + btnW && wy >= btnY && wy < btnY + TERRAIN_BTN_H) {
                combatBoardType = types[i];
                return;
            }
        }
        // Character list
        float listTop = btnY - PAL_GAP - SEC_HDR_H;
        float rowH    = 22f;
        for (int i = 0; i < ALL_CHARS.length; i++) {
            float ry = listTop - PAL_GAP - (i + 1) * rowH + combatPalScrollY;
            if (wy >= ry && wy < ry + rowH - 2) {
                selectedCharIndex = i;
                return;
            }
        }
    }

    // ---- Combat mode: save / load ----

    private void doSaveCombat() {
        FileDialog fd = new FileDialog((Frame) null, "Save Combat Board", FileDialog.SAVE);
        fd.setFile("combat_board.txt");
        fd.setFilenameFilter((dir, name) -> name.endsWith(".txt"));
        fd.setVisible(true);
        if (fd.getDirectory() == null || fd.getFile() == null) return;
        String path = fd.getDirectory() + fd.getFile();
        if (!path.endsWith(".txt")) path += ".txt";
        StringBuilder sb = new StringBuilder();
        sb.append("COMBAT_BOARD\n");
        sb.append("boardType=").append(combatBoardType.name()).append("\n");
        for (int[] sp : botSpawns)
            sb.append("spawn=").append(ALL_CHARS[sp[2]]).append(",").append(sp[0]).append(",").append(sp[1]).append("\n");
        try {
            Gdx.files.absolute(path).writeString(sb.toString(), false);
        } catch (Exception e) {
            Gdx.app.error("MapEditor", "Combat save failed: " + e.getMessage());
        }
    }

    private void doLoadCombat() {
        FileDialog fd = new FileDialog((Frame) null, "Load Combat Board", FileDialog.LOAD);
        fd.setFilenameFilter((dir, name) -> name.endsWith(".txt"));
        fd.setVisible(true);
        if (fd.getDirectory() == null || fd.getFile() == null) return;
        try {
            String[] lines = Gdx.files.absolute(fd.getDirectory() + fd.getFile()).readString().split("\n");
            if (lines.length == 0 || !lines[0].trim().equals("COMBAT_BOARD")) return;
            botSpawns.clear();
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("boardType=")) {
                    try { combatBoardType = BoardConfig.BoardType.valueOf(line.substring(10)); } catch (Exception ignored) {}
                } else if (line.startsWith("spawn=")) {
                    String[] parts = line.substring(6).split(",");
                    if (parts.length == 3) {
                        String charName = parts[0].trim();
                        int cx = Integer.parseInt(parts[1].trim());
                        int cy = Integer.parseInt(parts[2].trim());
                        for (int i = 0; i < ALL_CHARS.length; i++) {
                            if (ALL_CHARS[i].equals(charName)) { botSpawns.add(new int[]{cx, cy, i}); break; }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Gdx.app.error("MapEditor", "Combat load failed: " + e.getMessage());
        }
    }

    // ---- Dialogue mode: load storybg textures ----

    private void loadStorybgTextures() {
        storybgTextures.clear();
        storybgIds.clear();
        String[] candidates = {".", "assets", "../assets", "../../assets"};
        for (String cand : candidates) {
            try {
                FileHandle dir = Gdx.files.local(cand);
                if (!dir.exists() || !dir.isDirectory()) continue;
                for (FileHandle f : dir.list(".png")) {
                    String name = f.nameWithoutExtension();
                    if (!name.startsWith("storybg_")) continue;
                    if (storybgTextures.containsKey(name)) continue;
                    try {
                        storybgTextures.put(name, new Texture(f));
                        storybgIds.add(name);
                    } catch (Exception ignored) {}
                }
                if (!storybgTextures.isEmpty()) break;
            } catch (Exception ignored) {}
        }
    }

    private float dlgPalContentHeight() {
        int bgRows = storybgIds.isEmpty() ? 0 : (storybgIds.size + PAL_COLS - 1) / PAL_COLS;
        float bgSection  = SEC_HDR_H + PAL_GAP + (storybgIds.isEmpty() ? 40f : bgRows * TILE_ROW_H + 6f);
        float speSection = SEC_HDR_H + PAL_GAP + (NPC_ROW_H + PAL_GAP) + ALL_CHARS.length * (NPC_ROW_H + PAL_GAP) + 8f;
        return bgSection + speSection;
    }

    private float dlgPalY(float p, float h) {
        return (MAP_H - PAL_HDR_H) - p - h + dlgPalScrollY;
    }

    // ---- Dialogue mode: draw ----

    private void drawDialoguePalette(SpriteBatch b) {
        b.setColor(0.10f, 0.10f, 0.14f, 1f);
        b.draw(whitePixel, 0, 0, PALETTE_W, MAP_H);
        b.setColor(0.16f, 0.16f, 0.22f, 1f);
        b.draw(whitePixel, 0, MAP_H - PAL_HDR_H, PALETTE_W, PAL_HDR_H);
        game.font.getData().setScale(0.45f);
        game.font.setColor(Color.LIGHT_GRAY);
        game.font.draw(b, "DIALOGUE", 10, MAP_H - 6f);

        float p = 0f;

        // BACKGROUND section
        drawDlgPalSectionHeader(b, "BACKGROUND", p);
        p += SEC_HDR_H + PAL_GAP;

        if (storybgIds.isEmpty()) {
            float ey = dlgPalY(p, 20f);
            game.font.getData().setScale(0.36f);
            game.font.setColor(Color.DARK_GRAY);
            game.font.draw(b, "No storybg_*.png\nfound in assets/",
                    PAL_MRG, ey + 36f, PALETTE_W - PAL_MRG * 2, -1, true);
            p += 40f;
        } else {
            for (int i = 0; i < storybgIds.size; i++) {
                int col = i % PAL_COLS, row = i / PAL_COLS;
                float tx = PAL_MRG + col * (PAL_TSZ + PAL_GAP);
                float ty = dlgPalY(p + row * TILE_ROW_H, PAL_TSZ);
                if (ty + PAL_TSZ >= 0 && ty <= MAP_H - PAL_HDR_H)
                    drawDlgBgThumbnail(b, storybgIds.get(i), tx, ty);
            }
            int bgRows = (storybgIds.size + PAL_COLS - 1) / PAL_COLS;
            p += bgRows * TILE_ROW_H + 6f;
        }

        // SPEAKER section
        drawDlgPalSectionHeader(b, "SPEAKER", p);
        p += SEC_HDR_H + PAL_GAP;

        // NARRATOR row
        float narratorY = dlgPalY(p, NPC_ROW_H);
        if (narratorY + NPC_ROW_H >= 0 && narratorY <= MAP_H - PAL_HDR_H) {
            boolean sel = dlgSpeaker == null;
            b.setColor(sel ? new Color(0.20f, 0.20f, 0.45f, 1f) : new Color(0.16f, 0.16f, 0.24f, 1f));
            b.draw(whitePixel, PAL_MRG, narratorY, PALETTE_W - PAL_MRG * 2, NPC_ROW_H);
            b.setColor(sel ? new Color(0.5f, 0.5f, 1f, 1f) : new Color(0.28f, 0.28f, 0.38f, 1f));
            b.draw(whitePixel, PAL_MRG, narratorY, PALETTE_W - PAL_MRG * 2, 1);
            game.font.getData().setScale(0.40f);
            game.font.setColor(sel ? Color.WHITE : Color.GRAY);
            game.font.draw(b, "NARRATOR", PAL_MRG + 6f, narratorY + NPC_ROW_H / 2f + 6f);
        }
        p += NPC_ROW_H + PAL_GAP;

        // Character rows
        for (int i = 0; i < ALL_CHARS.length; i++) {
            float ry = dlgPalY(p + i * (NPC_ROW_H + PAL_GAP), NPC_ROW_H);
            if (ry + NPC_ROW_H < 0 || ry > MAP_H - PAL_HDR_H) continue;
            boolean sel = ALL_CHARS[i].equals(dlgSpeaker);
            b.setColor(sel ? new Color(0.20f, 0.20f, 0.45f, 1f) : new Color(0.16f, 0.16f, 0.24f, 1f));
            b.draw(whitePixel, PAL_MRG, ry, PALETTE_W - PAL_MRG * 2, NPC_ROW_H);
            b.setColor(sel ? new Color(0.5f, 0.5f, 1f, 1f) : new Color(0.28f, 0.28f, 0.38f, 1f));
            b.draw(whitePixel, PAL_MRG, ry, PALETTE_W - PAL_MRG * 2, 1);
            game.font.getData().setScale(0.40f);
            game.font.setColor(sel ? Color.WHITE : Color.GRAY);
            game.font.draw(b, ALL_CHARS[i], PAL_MRG + 6f, ry + NPC_ROW_H / 2f + 6f);
        }

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    private void drawDlgPalSectionHeader(SpriteBatch b, String label, float p) {
        float y = dlgPalY(p, SEC_HDR_H);
        if (y + SEC_HDR_H < 0 || y > MAP_H - PAL_HDR_H) return;
        b.setColor(0.18f, 0.18f, 0.26f, 1f);
        b.draw(whitePixel, 2, y, PALETTE_W - 4, SEC_HDR_H);
        game.font.getData().setScale(0.38f);
        game.font.setColor(new Color(0.7f, 0.7f, 0.85f, 1f));
        game.font.draw(b, label, 8f, y + SEC_HDR_H - 3f);
    }

    private void drawDlgBgThumbnail(SpriteBatch b, String id, float tx, float ty) {
        boolean sel = (id + ".png").equals(dlgBgPath);
        b.setColor(sel ? Color.GOLD : new Color(0.25f, 0.25f, 0.32f, 1f));
        b.draw(whitePixel, tx - 1, ty - 1, PAL_TSZ + 2, PAL_TSZ + 2);
        b.setColor(0.18f, 0.18f, 0.22f, 1f);
        b.draw(whitePixel, tx, ty, PAL_TSZ, PAL_TSZ);
        Texture tex = storybgTextures.get(id);
        if (tex != null) {
            b.setColor(Color.WHITE);
            b.draw(tex, tx, ty, PAL_TSZ, PAL_TSZ);
        }
        game.font.getData().setScale(0.28f);
        game.font.setColor(sel ? Color.GOLD : Color.GRAY);
        String shortName = id.length() > "storybg_".length() ? id.substring("storybg_".length()) : id;
        game.font.draw(b, shortName, tx + 2, ty + 10, PAL_TSZ - 4, -1, true);
    }

    private void drawDialogueBeatList(SpriteBatch b) {
        b.setColor(0.08f, 0.08f, 0.10f, 1f);
        b.draw(whitePixel, MAP_X, MAP_Y, MAP_W, MAP_H);

        // Top header
        b.setColor(0.12f, 0.12f, 0.18f, 1f);
        b.draw(whitePixel, MAP_X, MAP_H - PAL_HDR_H, MAP_W, PAL_HDR_H);
        game.font.getData().setScale(0.45f);
        game.font.setColor(Color.LIGHT_GRAY);
        String title = "BEATS" + (dlgBeats.size > 0 ? "  (" + dlgBeats.size + ")" : "  — empty");
        game.font.draw(b, title, MAP_X + 10, MAP_H - 6f);

        // Fixed "+ ADD BEAT" button at the bottom
        float addBtnY = MAP_Y + 4;
        b.setColor(0.08f, 0.28f, 0.08f, 1f);
        b.draw(whitePixel, MAP_X + 4, addBtnY, MAP_W - 8, DLG_ADD_BTN_H);
        b.setColor(0.2f, 0.7f, 0.2f, 1f);
        b.draw(whitePixel, MAP_X + 4, addBtnY, MAP_W - 8, 1);
        b.draw(whitePixel, MAP_X + 4, addBtnY + DLG_ADD_BTN_H - 1, MAP_W - 8, 1);
        b.draw(whitePixel, MAP_X + 4, addBtnY, 1, DLG_ADD_BTN_H);
        b.draw(whitePixel, MAP_X + MAP_W - 5, addBtnY, 1, DLG_ADD_BTN_H);
        game.font.getData().setScale(0.45f);
        game.font.setColor(new Color(0.45f, 1f, 0.45f, 1f));
        layout.setText(game.font, "+ ADD BEAT");
        game.font.draw(b, "+ ADD BEAT",
                MAP_X + 4 + (MAP_W - 8 - layout.width) / 2f,
                addBtnY + (DLG_ADD_BTN_H + layout.height) / 2f);

        // Scrollable beat list above the button
        float listTop = MAP_H - PAL_HDR_H;
        float listBot = MAP_Y + DLG_ADD_BTN_H + 8f;

        for (int i = 0; i < dlgBeats.size; i++) {
            CutsceneData.DialogueBeat beat = dlgBeats.get(i);
            float ry = listTop - (i + 1) * (DLG_ROW_H + 2f) + dlgBeatScrollY;
            if (ry + DLG_ROW_H < listBot || ry > listTop) continue;

            boolean sel = (i == dlgSelIdx);
            b.setColor(sel ? new Color(0.18f, 0.18f, 0.42f, 1f) : new Color(0.12f, 0.12f, 0.18f, 1f));
            b.draw(whitePixel, MAP_X + 4, ry, MAP_W - 8, DLG_ROW_H);

            if (sel) {
                b.setColor(0.4f, 0.4f, 0.9f, 1f);
                b.draw(whitePixel, MAP_X + 4, ry, MAP_W - 8, 1);
                b.draw(whitePixel, MAP_X + 4, ry + DLG_ROW_H - 1, MAP_W - 8, 1);
                b.draw(whitePixel, MAP_X + 4, ry, 1, DLG_ROW_H);
                b.draw(whitePixel, MAP_X + MAP_W - 5, ry, 1, DLG_ROW_H);
            } else {
                b.setColor(0.22f, 0.22f, 0.30f, 1f);
                b.draw(whitePixel, MAP_X + 4, ry, MAP_W - 8, 1);
            }

            // Index number
            game.font.getData().setScale(0.36f);
            game.font.setColor(new Color(0.45f, 0.45f, 0.55f, 1f));
            game.font.draw(b, "#" + (i + 1), MAP_X + 10, ry + DLG_ROW_H - 7f);

            // Speaker name
            String speaker = beat.speaker == null ? "NARRATOR" : beat.speaker;
            game.font.getData().setScale(0.42f);
            game.font.setColor(beat.speaker == null
                    ? new Color(0.65f, 0.65f, 1f, 1f) : new Color(1f, 0.82f, 0.4f, 1f));
            layout.setText(game.font, speaker + ": ");
            game.font.draw(b, speaker + ": ", MAP_X + 36, ry + DLG_ROW_H - 7f);

            // Text preview (single line)
            float textX = MAP_X + 36 + layout.width + 2f;
            game.font.getData().setScale(0.38f);
            game.font.setColor(sel ? Color.WHITE : new Color(0.72f, 0.72f, 0.72f, 1f));
            float maxW = MAP_X + MAP_W - 12 - textX;
            game.font.draw(b, beat.text, textX, ry + DLG_ROW_H - 7f, maxW, -1, false);
        }

        if (dlgBeats.isEmpty()) {
            game.font.getData().setScale(0.40f);
            game.font.setColor(new Color(0.3f, 0.3f, 0.4f, 1f));
            game.font.draw(b, "Pick a speaker in the left panel,\nthen click + ADD BEAT below",
                    MAP_X + MAP_W / 2f - 120f, MAP_H / 2f + 20f, 240f, -1, true);
        }

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    private void drawDialogueProperties(SpriteBatch b) {
        b.setColor(0.10f, 0.10f, 0.14f, 1f);
        b.draw(whitePixel, PROPS_X, 0, PROPS_W, MAP_H);
        b.setColor(0.16f, 0.16f, 0.22f, 1f);
        b.draw(whitePixel, PROPS_X, MAP_H - PAL_HDR_H, PROPS_W, PAL_HDR_H);
        game.font.getData().setScale(0.45f);
        game.font.setColor(Color.LIGHT_GRAY);
        game.font.draw(b, "BEAT", PROPS_X + 8, MAP_H - 6f);

        float y = MAP_H - PAL_HDR_H - 20f;

        // BG path
        game.font.getData().setScale(0.36f);
        game.font.setColor(new Color(0.5f, 0.5f, 0.6f, 1f));
        game.font.draw(b, "BG:", PROPS_X + 8, y);
        y -= 14f;
        game.font.setColor(dlgBgPath == null ? Color.DARK_GRAY : Color.CYAN);
        game.font.draw(b, dlgBgPath == null ? "(none)" : dlgBgPath,
                PROPS_X + 8, y, PROPS_W - 16f, -1, true);
        y -= 20f;

        if (dlgSelIdx < 0 || dlgSelIdx >= dlgBeats.size) {
            game.font.getData().setScale(0.38f);
            game.font.setColor(Color.DARK_GRAY);
            game.font.draw(b, "Select a beat\nor add one",
                    PROPS_X + 10, y - 10f, PROPS_W - 20f, -1, true);
            game.font.getData().setScale(1.0f);
            game.font.setColor(Color.WHITE);
            b.setColor(Color.WHITE);
            return;
        }

        CutsceneData.DialogueBeat beat = dlgBeats.get(dlgSelIdx);

        // Beat index
        game.font.getData().setScale(0.45f);
        game.font.setColor(Color.CYAN);
        game.font.draw(b, "Beat #" + (dlgSelIdx + 1), PROPS_X + 8, y);
        y -= 18f;

        // Speaker
        game.font.getData().setScale(0.36f);
        game.font.setColor(Color.LIGHT_GRAY);
        game.font.draw(b, "Speaker:", PROPS_X + 8, y);
        y -= 14f;
        game.font.setColor(beat.speaker == null
                ? new Color(0.65f, 0.65f, 1f, 1f) : new Color(1f, 0.82f, 0.4f, 1f));
        game.font.getData().setScale(0.42f);
        game.font.draw(b, beat.speaker == null ? "NARRATOR" : beat.speaker,
                PROPS_X + 8, y, PROPS_W - 16f, -1, true);
        y -= 18f;

        // Text box
        game.font.getData().setScale(0.36f);
        game.font.setColor(Color.LIGHT_GRAY);
        game.font.draw(b, "Text:", PROPS_X + 8, y);
        y -= 8f;
        float textBoxH = 62f;
        b.setColor(0.14f, 0.14f, 0.20f, 1f);
        b.draw(whitePixel, PROPS_X + 8, y - textBoxH, PROPS_W - 16f, textBoxH);
        b.setColor(0.30f, 0.30f, 0.46f, 1f);
        b.draw(whitePixel, PROPS_X + 8,              y - textBoxH,      PROPS_W - 16f, 1);
        b.draw(whitePixel, PROPS_X + 8,              y - 1,             PROPS_W - 16f, 1);
        b.draw(whitePixel, PROPS_X + 8,              y - textBoxH,      1, textBoxH);
        b.draw(whitePixel, PROPS_X + PROPS_W - 9,    y - textBoxH,      1, textBoxH);
        game.font.getData().setScale(0.36f);
        game.font.setColor(Color.WHITE);
        game.font.draw(b, beat.text, PROPS_X + 12, y - 4f, PROPS_W - 24f, -1, true);
        y -= textBoxH + 6f;

        // Edit text button
        drawDlgButton(b, PROPS_X + 8, y - 22f, PROPS_W - 16f, 22f, "EDIT TEXT",
                new Color(0.12f, 0.30f, 0.52f, 1f));
        y -= 30f;

        // Move up / down
        float halfW = (PROPS_W - 20f) / 2f;
        drawDlgButton(b, PROPS_X + 8, y - 22f, halfW, 22f, "UP",
                dlgSelIdx > 0 ? new Color(0.28f, 0.22f, 0.08f, 1f) : new Color(0.16f, 0.16f, 0.20f, 1f));
        drawDlgButton(b, PROPS_X + 10 + halfW, y - 22f, halfW, 22f, "DOWN",
                dlgSelIdx < dlgBeats.size - 1 ? new Color(0.28f, 0.22f, 0.08f, 1f) : new Color(0.16f, 0.16f, 0.20f, 1f));
        y -= 30f;

        // Delete button
        drawDlgButton(b, PROPS_X + 8, y - 22f, PROPS_W - 16f, 22f, "DELETE BEAT",
                new Color(0.48f, 0.08f, 0.08f, 1f));

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    private void drawDlgButton(SpriteBatch b, float x, float y, float w, float h, String label, Color color) {
        b.setColor(color);
        b.draw(whitePixel, x, y, w, h);
        b.setColor(Math.min(1f, color.r * 1.6f), Math.min(1f, color.g * 1.6f), Math.min(1f, color.b * 1.6f), 1f);
        b.draw(whitePixel, x, y, w, 1);
        b.draw(whitePixel, x, y + h - 1, w, 1);
        b.draw(whitePixel, x, y, 1, h);
        b.draw(whitePixel, x + w - 1, y, 1, h);
        game.font.getData().setScale(0.40f);
        game.font.setColor(Color.WHITE);
        layout.setText(game.font, label);
        game.font.draw(b, label, x + (w - layout.width) / 2f, y + (h + layout.height) / 2f);
    }

    // ---- Dialogue mode: input ----

    private void handleDialoguePaletteClick(float wx, float wy) {
        float p = 0f;

        // BACKGROUND section
        p += SEC_HDR_H + PAL_GAP;
        if (!storybgIds.isEmpty()) {
            for (int i = 0; i < storybgIds.size; i++) {
                int col = i % PAL_COLS, row = i / PAL_COLS;
                float tx = PAL_MRG + col * (PAL_TSZ + PAL_GAP);
                float ty = dlgPalY(p + row * TILE_ROW_H, PAL_TSZ);
                if (wx >= tx && wx < tx + PAL_TSZ && wy >= ty && wy < ty + PAL_TSZ) {
                    String id = storybgIds.get(i);
                    dlgBgPath = (id + ".png").equals(dlgBgPath) ? null : id + ".png";
                    return;
                }
            }
            int bgRows = (storybgIds.size + PAL_COLS - 1) / PAL_COLS;
            p += bgRows * TILE_ROW_H + 6f;
        } else {
            p += 40f;
        }

        // SPEAKER section
        p += SEC_HDR_H + PAL_GAP;
        float narratorY = dlgPalY(p, NPC_ROW_H);
        if (wy >= narratorY && wy < narratorY + NPC_ROW_H) {
            dlgSpeaker = null;
            replaceBeat(null);
            return;
        }
        p += NPC_ROW_H + PAL_GAP;
        for (int i = 0; i < ALL_CHARS.length; i++) {
            float ry = dlgPalY(p + i * (NPC_ROW_H + PAL_GAP), NPC_ROW_H);
            if (wy >= ry && wy < ry + NPC_ROW_H) {
                dlgSpeaker = ALL_CHARS[i];
                replaceBeat(ALL_CHARS[i]);
                return;
            }
        }
    }

    private void handleDialogueBeatListClick(float wx, float wy) {
        // Fixed ADD BEAT button at the bottom
        float addBtnY = MAP_Y + 4;
        if (wy >= addBtnY && wy < addBtnY + DLG_ADD_BTN_H) {
            addBeat();
            return;
        }

        float listTop = MAP_H - PAL_HDR_H;
        float listBot = MAP_Y + DLG_ADD_BTN_H + 8f;

        for (int i = 0; i < dlgBeats.size; i++) {
            float ry = listTop - (i + 1) * (DLG_ROW_H + 2f) + dlgBeatScrollY;
            if (ry + DLG_ROW_H < listBot || ry > listTop) continue;
            if (wy >= ry && wy < ry + DLG_ROW_H) {
                if (dlgSelIdx == i) {
                    dlgSelIdx = -1;
                } else {
                    dlgSelIdx = i;
                    dlgSpeaker = dlgBeats.get(i).speaker;
                }
                return;
            }
        }
    }

    private void handleDialoguePropertiesClick(float wx, float wy) {
        float y = MAP_H - PAL_HDR_H - 20f;
        y -= 14f;  // BG label
        y -= 20f;  // BG value

        if (dlgSelIdx < 0 || dlgSelIdx >= dlgBeats.size) return;

        y -= 18f;  // "Beat #N"
        y -= 14f;  // "Speaker:"
        y -= 18f;  // speaker name
        y -= 8f;   // "Text:"
        float textBoxH = 62f;
        y -= textBoxH + 6f;

        // Edit text button: y-22 to y
        if (wy >= y - 22f && wy < y) {
            CutsceneData.DialogueBeat cur = dlgBeats.get(dlgSelIdx);
            String newText = JOptionPane.showInputDialog(null, "Edit dialogue text:", cur.text);
            if (newText != null && !newText.trim().isEmpty())
                dlgBeats.set(dlgSelIdx, new CutsceneData.DialogueBeat(cur.speaker, newText.trim()));
            return;
        }
        y -= 30f;

        float halfW = (PROPS_W - 20f) / 2f;
        // Move up
        if (wx < PROPS_X + 8 + halfW && wy >= y - 22f && wy < y && dlgSelIdx > 0) {
            CutsceneData.DialogueBeat moved = dlgBeats.get(dlgSelIdx);
            dlgBeats.set(dlgSelIdx, dlgBeats.get(dlgSelIdx - 1));
            dlgBeats.set(dlgSelIdx - 1, moved);
            dlgSelIdx--;
            return;
        }
        // Move down
        if (wx >= PROPS_X + 10 + halfW && wy >= y - 22f && wy < y && dlgSelIdx < dlgBeats.size - 1) {
            CutsceneData.DialogueBeat moved = dlgBeats.get(dlgSelIdx);
            dlgBeats.set(dlgSelIdx, dlgBeats.get(dlgSelIdx + 1));
            dlgBeats.set(dlgSelIdx + 1, moved);
            dlgSelIdx++;
            return;
        }
        y -= 30f;

        // Delete
        if (wy >= y - 22f && wy < y) {
            dlgBeats.removeIndex(dlgSelIdx);
            if (dlgSelIdx >= dlgBeats.size) dlgSelIdx = dlgBeats.size - 1;
        }
    }

    // ---- Dialogue mode: actions ----

    private void addBeat() {
        String prompt = "Dialogue text (" + (dlgSpeaker == null ? "NARRATOR" : dlgSpeaker) + "):";
        String text = JOptionPane.showInputDialog(null, prompt, "");
        if (text != null && !text.trim().isEmpty()) {
            dlgBeats.add(new CutsceneData.DialogueBeat(dlgSpeaker, text.trim()));
            dlgSelIdx = dlgBeats.size - 1;
        }
    }

    private void replaceBeat(String newSpeaker) {
        if (dlgSelIdx < 0 || dlgSelIdx >= dlgBeats.size) return;
        CutsceneData.DialogueBeat cur = dlgBeats.get(dlgSelIdx);
        dlgBeats.set(dlgSelIdx, new CutsceneData.DialogueBeat(newSpeaker, cur.text));
    }

    private void saveDialogue() {
        FileDialog fd = new FileDialog((Frame) null, "Save Dialogue", FileDialog.SAVE);
        fd.setFile("cutscene.txt");
        fd.setFilenameFilter((dir, name) -> name.endsWith(".txt"));
        fd.setVisible(true);
        if (fd.getDirectory() == null || fd.getFile() == null) return;
        String path = fd.getDirectory() + fd.getFile();
        if (!path.endsWith(".txt")) path += ".txt";
        StringBuilder sb = new StringBuilder();
        if (dlgBgPath != null) sb.append("BACKGROUND ").append(dlgBgPath).append('\n');
        for (CutsceneData.DialogueBeat beat : dlgBeats) {
            if (beat.speaker == null) {
                sb.append("NARRATOR ").append(beat.text).append('\n');
            } else {
                sb.append("SAY ").append(beat.speaker).append(": ").append(beat.text).append('\n');
            }
        }
        try {
            Gdx.files.absolute(path).writeString(sb.toString(), false);
            Gdx.app.log("MapEditor", "Dialogue saved: " + path);
        } catch (Exception e) {
            Gdx.app.error("MapEditor", "Dialogue save failed: " + e.getMessage());
        }
    }

    private void loadDialogue() {
        FileDialog fd = new FileDialog((Frame) null, "Load Dialogue", FileDialog.LOAD);
        fd.setFilenameFilter((dir, name) -> name.endsWith(".txt"));
        fd.setVisible(true);
        if (fd.getDirectory() == null || fd.getFile() == null) return;
        try {
            String[] lines = Gdx.files.absolute(fd.getDirectory() + fd.getFile())
                    .readString().split("\n");
            dlgBeats.clear();
            dlgBgPath  = null;
            dlgSelIdx  = -1;
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                if (line.startsWith("BACKGROUND ")) {
                    dlgBgPath = line.substring("BACKGROUND ".length()).trim();
                } else if (line.startsWith("SAY ")) {
                    String rest = line.substring(4).trim();
                    int colon = rest.indexOf(':');
                    if (colon >= 0) {
                        String speaker = rest.substring(0, colon).trim();
                        String text    = rest.substring(colon + 1).trim();
                        dlgBeats.add(new CutsceneData.DialogueBeat(speaker, text));
                    }
                } else if (line.startsWith("NARRATOR ")) {
                    dlgBeats.add(new CutsceneData.DialogueBeat(null, line.substring(9).trim()));
                }
            }
        } catch (Exception e) {
            Gdx.app.error("MapEditor", "Dialogue load failed: " + e.getMessage());
        }
    }

    // ---- Combat mode: helpers ----

    private int combatTileX(float wx) {
        return (int)((wx - COMBAT_GRID_X) / COMBAT_TILE_SZ);
    }

    private int combatTileY(float wy) {
        return (int)((wy - COMBAT_GRID_Y) / COMBAT_TILE_SZ);
    }

    private int[] getSpawnAt(int gx, int gy) {
        for (int[] sp : botSpawns)
            if (sp[0] == gx && sp[1] == gy) return sp;
        return null;
    }

    private String[] getCombatGroundTextures(BoardConfig.BoardType type) {
        switch (type) {
            case WIND:   return new String[]{"tile_stone1", "tile_stone2"};
            case DESERT: return new String[]{"tile_sand1",  "tile_sand2"};
            default:     return new String[]{"tile_grass1", "tile_grass2"};
        }
    }

    private BoardConfig boardConfigForType(BoardConfig.BoardType type) {
        switch (type) {
            case WIND:   return BoardConfig.wind();
            case DESERT: return BoardConfig.desert();
            default:     return BoardConfig.forest();
        }
    }

    @Override
    public void dispose() {
        whitePixel.dispose();
        havenTileTexture.dispose();
        for (Texture t : tileTextures.values()) t.dispose();
        for (Texture t : storybgTextures.values()) t.dispose();
    }
}
