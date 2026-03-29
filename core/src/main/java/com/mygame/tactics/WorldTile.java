package com.mygame.tactics;

/** Data for a single tile in a WorldArea. Rendered in two layers: background then object. */
public class WorldTile {
    /** tile_ texture drawn as the ground layer. Null = empty (checkerboard in editor). */
    public String  backgroundId  = null;
    /**
     * map_ or building_ texture drawn on top of the background.
     * map_      = blocks movement (walkable auto-set false).
     * building_ = blocks movement, interactable, triggers area entry.
     */
    public String  objectId      = null;
    public boolean walkable      = true;
    public boolean interactable  = false;
    /** Area ID to load when the player interacts with this tile (building_ objects). */
    public String  triggerAreaId = null;
    /**
     * True only on the bottom-left (anchor) tile of a multi-tile object.
     * Rendering draws the full texture from whichever tile has isAnchor=true,
     * avoiding false merges between adjacent same-type objects.
     */
    public boolean isAnchor      = false;

    public WorldTile() {}

    public WorldTile copy() {
        WorldTile t = new WorldTile();
        t.backgroundId  = this.backgroundId;
        t.objectId      = this.objectId;
        t.walkable      = this.walkable;
        t.interactable  = this.interactable;
        t.triggerAreaId = this.triggerAreaId;
        t.isAnchor      = this.isAnchor;
        return t;
    }
}
