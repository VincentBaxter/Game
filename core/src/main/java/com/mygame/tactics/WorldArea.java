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
        file.writeString(sb.toString(), false);
    }

    public static WorldArea load(FileHandle file) {
        String[] lines = file.readString().split("\n");
        String areaId = lines[0].substring("areaId=".length()).trim();
        int    w      = Integer.parseInt(lines[1].substring("width=".length()).trim());
        int    h      = Integer.parseInt(lines[2].substring("height=".length()).trim());
        WorldArea area = new WorldArea(areaId, w, h);
        int line = 3;
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
        return area;
    }
}
