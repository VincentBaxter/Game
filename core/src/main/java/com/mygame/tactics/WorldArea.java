package com.mygame.tactics;

import com.badlogic.gdx.files.FileHandle;

/**
 * A rectangular grid of WorldTiles representing one map area.
 * tiles[x][y] — x=0 is left, y=0 is bottom.
 */
public class WorldArea {
    public String        areaId;
    public int           width, height;
    public WorldTile[][] tiles;   // [x][y]
    public int           spawnX = -1, spawnY = -1;  // -1 = use map centre

    /** Absolute path this area was loaded from; null for newly created areas. */
    public String sourceFile = null;

    /** Cutscene to play once on first area entry (gated by "{areaId}_entry_played" flag). */
    public String entryCutsceneId     = null;
    /** Cutscene to play after name entry following the entry cutscene. */
    public String postEntryCutsceneId = null;

    // -----------------------------------------------------------------------
    // Flag-based tile overrides
    // -----------------------------------------------------------------------

    /**
     * A conditional tile override: when PlayerFlags.check(flagKey, op, threshold)
     * is true, replace tile (x, y)'s bg or obj layer with tileId (null = remove).
     *
     * Stored in the map file as:
     *   override=flagKey,op,threshold,x,y,layer,tileId
     * where layer is "bg" or "obj" and tileId is "-" for null.
     *
     * Example — remove trees at (4,5) once tutorial_wins reaches 3:
     *   override=tutorial_wins,>=,3,4,5,obj,-
     */
    public static class FlagOverride {
        public String  flagKey;
        public String  op;          // >=  <=  ==  >  <
        public int     threshold;
        public int     x, y;
        public String  layer;       // "bg" or "obj"
        public String  tileId;      // null = remove
    }

    public final java.util.List<FlagOverride> overrides = new java.util.ArrayList<>();

    /**
     * A whole-map swap: when the flag condition is true, load targetFile instead of this area.
     * Evaluated after tile overrides so fine-grained changes still apply to the base map.
     *
     * Stored in the map file as:
     *   swapMap=flagKey,op,threshold,targetFile
     *
     * Example — replace this area with the post-quest version once cleared:
     *   swapMap=spider_cave_cleared,>=,1,area_forest_post.txt
     */
    public static class SwapMapOverride {
        public String flagKey;
        public String op;
        public int    threshold;
        public String targetFile;
    }

    public final java.util.List<SwapMapOverride> swapMaps = new java.util.ArrayList<>();

    /**
     * Returns the targetFile of the first SwapMapOverride whose condition is met, or null.
     */
    public String resolveSwapMap(PlayerFlags flags) {
        for (SwapMapOverride sm : swapMaps) {
            if (flags.check(sm.flagKey, sm.op, sm.threshold)) return sm.targetFile;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // NPCs
    // -----------------------------------------------------------------------

    /**
     * An NPC placed on the world map.
     * Stored in the map file as:
     *   npc=charName,x,y,interactable,triggerAreaId
     * where '-' represents null for triggerAreaId.
     */
    public static class WorldNpc {
        public String  charName;
        public int     x, y;
        public boolean interactable;
        public String  triggerAreaId;   // null = no cutscene trigger
        public String  combatFile;      // null = no combat; filename of combat board
        public String  winFlag;         // flag key set to 1 on win; null = no flag
        public String  winCutsceneId;   // cutscene to play on win; null = use {triggerAreaId}_post fallback
        public String  lossCutsceneId;  // cutscene to play on loss; null = just return to world
        public boolean followsPlayer;   // if true, NPC walks toward the player each frame
        public String  team1Preset;     // pipe-separated names for a fixed team1 (skips DraftScreen); null = use DraftScreen
        public String  showFlag;        // if set, NPC only appears when this flag >= 1
    }

    public final java.util.List<WorldNpc> npcs = new java.util.ArrayList<>();

    // -----------------------------------------------------------------------
    // Dropped items (transient — not saved to file)
    // -----------------------------------------------------------------------

    public static class WorldDrop {
        public int  x, y;
        public Item item;
        public WorldDrop(int x, int y, Item item) { this.x = x; this.y = y; this.item = item; }
    }

    public final java.util.List<WorldDrop> drops = new java.util.ArrayList<>();

    public WorldArea(String areaId, int width, int height) {
        this.areaId  = areaId;
        this.width   = width;
        this.height  = height;
        this.tiles   = new WorldTile[width][height];
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                tiles[x][y] = new WorldTile();
    }

    /**
     * Saves to a plain-text file.
     * One tile per line: backgroundId,objectId,walkable,interactable,triggerAreaId,isAnchor
     * Old 5-field format (no isAnchor) is supported on load — anchors are inferred.
     * '-' represents null.
     */
    public void save(FileHandle file) {
        StringBuilder sb = new StringBuilder();
        sb.append("areaId=").append(areaId).append('\n');
        sb.append("width=").append(width).append('\n');
        sb.append("height=").append(height).append('\n');
        sb.append("spawnX=").append(spawnX).append('\n');
        sb.append("spawnY=").append(spawnY).append('\n');
        if (entryCutsceneId     != null) sb.append("entryCutsceneId=").append(entryCutsceneId).append('\n');
        if (postEntryCutsceneId != null) sb.append("postEntryCutsceneId=").append(postEntryCutsceneId).append('\n');
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                WorldTile t = tiles[x][y];
                sb.append(t.backgroundId  == null ? "-" : t.backgroundId).append(',');
                sb.append(t.objectId      == null ? "-" : t.objectId).append(',');
                sb.append(t.walkable      ? '1' : '0').append(',');
                sb.append(t.interactable  ? '1' : '0').append(',');
                sb.append(t.triggerAreaId == null ? "-" : t.triggerAreaId).append(',');
                sb.append(t.isAnchor      ? '1' : '0').append('\n');
            }
        }
        for (FlagOverride ov : overrides) {
            sb.append("override=")
              .append(ov.flagKey).append(',')
              .append(ov.op).append(',')
              .append(ov.threshold).append(',')
              .append(ov.x).append(',')
              .append(ov.y).append(',')
              .append(ov.layer).append(',')
              .append(ov.tileId == null ? "-" : ov.tileId)
              .append('\n');
        }
        for (SwapMapOverride sm : swapMaps) {
            sb.append("swapMap=")
              .append(sm.flagKey).append(',')
              .append(sm.op).append(',')
              .append(sm.threshold).append(',')
              .append(sm.targetFile)
              .append('\n');
        }
        for (WorldNpc npc : npcs) {
            sb.append("npc=")
              .append(npc.charName).append(',')
              .append(npc.x).append(',')
              .append(npc.y).append(',')
              .append(npc.interactable ? '1' : '0').append(',')
              .append(npc.triggerAreaId  == null ? "-" : npc.triggerAreaId).append(',')
              .append(npc.combatFile    == null ? "-" : npc.combatFile).append(',')
              .append(npc.winFlag       == null ? "-" : npc.winFlag).append(',')
              .append(npc.winCutsceneId  == null ? "-" : npc.winCutsceneId).append(',')
              .append(npc.lossCutsceneId == null ? "-" : npc.lossCutsceneId).append(',')
              .append(npc.followsPlayer ? '1' : '0').append(',')
              .append(npc.team1Preset == null ? "-" : npc.team1Preset).append(',')
              .append(npc.showFlag    == null ? "-" : npc.showFlag)
              .append('\n');
        }
        file.writeString(sb.toString(), false);
    }

    public static WorldArea load(FileHandle file) {
        String[] lines = file.readString().split("\n");
        String areaId = lines[0].substring("areaId=".length()).trim();
        int    w      = Integer.parseInt(lines[1].substring("width=".length()).trim());
        int    h      = Integer.parseInt(lines[2].substring("height=".length()).trim());
        WorldArea area = new WorldArea(areaId, w, h);
        int line = 3;
        // Optional spawnX/spawnY lines (added later; skip if tile data follows)
        if (line < lines.length && lines[line].trim().startsWith("spawnX=")) {
            area.spawnX = Integer.parseInt(lines[line++].trim().substring("spawnX=".length()));
        }
        if (line < lines.length && lines[line].trim().startsWith("spawnY=")) {
            area.spawnY = Integer.parseInt(lines[line++].trim().substring("spawnY=".length()));
        }
        if (line < lines.length && lines[line].trim().startsWith("entryCutsceneId=")) {
            area.entryCutsceneId = lines[line++].trim().substring("entryCutsceneId=".length());
        }
        if (line < lines.length && lines[line].trim().startsWith("postEntryCutsceneId=")) {
            area.postEntryCutsceneId = lines[line++].trim().substring("postEntryCutsceneId=".length());
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                String[] p = lines[line++].trim().split(",");
                WorldTile t = area.tiles[x][y];
                t.backgroundId  = p[0].equals("-") ? null : p[0];
                t.objectId      = p[1].equals("-") ? null : p[1];
                t.walkable      = p[2].equals("1");
                t.interactable  = p[3].equals("1");
                t.triggerAreaId = p.length > 4 && !p[4].equals("-") ? p[4] : null;
                t.isAnchor      = p.length > 5 && p[5].equals("1");
            }
        }
        // Parse any trailing override= and npc= lines
        while (line < lines.length) {
            String l = lines[line++].trim();
            if (l.startsWith("override=")) {
                String[] p = l.substring("override=".length()).split(",", 7);
                if (p.length < 7) continue;
                try {
                    FlagOverride ov  = new FlagOverride();
                    ov.flagKey    = p[0];
                    ov.op         = p[1];
                    ov.threshold  = Integer.parseInt(p[2]);
                    ov.x          = Integer.parseInt(p[3]);
                    ov.y          = Integer.parseInt(p[4]);
                    ov.layer      = p[5];
                    ov.tileId     = p[6].equals("-") ? null : p[6];
                    area.overrides.add(ov);
                } catch (Exception ignored) {}
            } else if (l.startsWith("swapMap=")) {
                String[] p = l.substring("swapMap=".length()).split(",", 4);
                if (p.length < 4) continue;
                try {
                    SwapMapOverride sm = new SwapMapOverride();
                    sm.flagKey    = p[0];
                    sm.op         = p[1];
                    sm.threshold  = Integer.parseInt(p[2]);
                    sm.targetFile = p[3];
                    area.swapMaps.add(sm);
                } catch (Exception ignored) {}
            } else if (l.startsWith("npc=")) {
                String[] p = l.substring("npc=".length()).split(",", 12);
                if (p.length < 5) continue;
                try {
                    WorldNpc npc       = new WorldNpc();
                    npc.charName       = p[0];
                    npc.x              = Integer.parseInt(p[1]);
                    npc.y              = Integer.parseInt(p[2]);
                    npc.interactable   = p[3].equals("1");
                    npc.triggerAreaId  = p[4].equals("-") ? null : p[4];
                    npc.combatFile     = p.length > 5  && !p[5].equals("-")  ? p[5]  : null;
                    npc.winFlag        = p.length > 6  && !p[6].equals("-")  ? p[6]  : null;
                    npc.winCutsceneId  = p.length > 7  && !p[7].equals("-")  ? p[7]  : null;
                    npc.lossCutsceneId = p.length > 8  && !p[8].equals("-")  ? p[8]  : null;
                    npc.followsPlayer  = p.length > 9  && p[9].equals("1");
                    npc.team1Preset    = p.length > 10 && !p[10].equals("-") ? p[10] : null;
                    npc.showFlag       = p.length > 11 && !p[11].equals("-") ? p[11] : null;
                    area.npcs.add(npc);
                } catch (Exception ignored) {}
            }
        }

        // Always infer anchors as a repair pass: any tile whose objectId doesn't match
        // its left or bottom neighbour is the bottom-left corner of an object and must be
        // the anchor. Only sets isAnchor=true, never clears it, so correctly saved maps
        // are unaffected while any tile that was wrongly saved as non-anchor gets fixed.
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                WorldTile t = area.tiles[x][y];
                if (t.objectId == null) continue;
                boolean leftSame  = x > 0 && t.objectId.equals(area.tiles[x-1][y].objectId);
                boolean belowSame = y > 0 && t.objectId.equals(area.tiles[x][y-1].objectId);
                if (!leftSame && !belowSame) t.isAnchor = true;
            }
        }
        area.sourceFile = file.path();
        return area;
    }

    /**
     * Applies flag overrides using the provided PlayerFlags.
     * Call this after loading an area and whenever the player returns to it
     * so tile changes triggered by flags are reflected immediately.
     */
    public void applyOverrides(PlayerFlags flags) {
        for (FlagOverride ov : overrides) {
            if (!flags.check(ov.flagKey, ov.op, ov.threshold)) continue;
            if (ov.x < 0 || ov.x >= width || ov.y < 0 || ov.y >= height) continue;
            WorldTile t = tiles[ov.x][ov.y];
            if ("bg".equals(ov.layer)) {
                t.backgroundId = ov.tileId;
            } else {
                t.objectId = ov.tileId;
                if (ov.tileId == null) t.walkable = true;
            }
        }
        // Restore picked violetberry bushes based on position flags
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                WorldTile t = tiles[x][y];
                if ("map_violetberry_bush_full".equals(t.objectId)
                        && flags.is("vb_" + x + "_" + y)) {
                    t.objectId = "map_violetberry_bush";
                }
                if ("map_dead_tree1".equals(t.objectId)
                        && flags.is("tree_" + x + "_" + y + "_cut")) {
                    t.objectId = null;
                    t.walkable = true;
                }
            }
        }
    }
}
