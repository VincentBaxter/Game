package com.mygame.tactics;

public class Tile {
    public int x, y;
    private boolean isPoisoned = false;
    private boolean isWalkable = true;
    private boolean isFrozen = false;
    private boolean isCollapsed = false; // True when this tile has fallen into the void.
    private boolean isLockdown = false;

    public void setLockdown(boolean lockdown) { this.isLockdown = lockdown; }
    public boolean isLockdown() { return isLockdown; }
    private int structureHealth = 0;

    public Tile(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // --- POISON LOGIC (Billy) ---
    public void setPoison(boolean poisoned) {
        this.isPoisoned = poisoned;
    }

    public boolean isPoisoned() { 
        return isPoisoned; 
    }

    // --- MOVEMENT & STATUS LOGIC ---
    public void setWalkable(boolean walkable) {
        this.isWalkable = walkable;
    }

    public boolean isWalkable() { 
        // A tile is only walkable if it's marked walkable AND has no structure
        return isWalkable && structureHealth <= 0; 
    }
    
    public void setFrozen(boolean frozen) {
        this.isFrozen = frozen;
    }

    public boolean isFrozen() {
        return isFrozen;
    }

    // --- COLLAPSE LOGIC ---
    public void setCollapsed(boolean collapsed) {
        this.isCollapsed = collapsed;
    }

    public boolean isCollapsed() {
        return isCollapsed;
    }

    // --- STRUCTURE LOGIC (Sean & Obstacles) ---
    
    public void setStructure(boolean hasStructure) {
        if (!hasStructure) {
            this.structureHealth = 0;
        } else if (this.structureHealth <= 0) {
            this.structureHealth = 1; 
        }
    }

    public void setStructureHP(int hp) {
        this.structureHealth = hp;
    }

    public boolean hasStructure() { 
        return structureHealth > 0; 
    }

    public int getStructureHealth() {
        return structureHealth;
    }

    // --- THORN LOGIC (Nathan) ---
    private boolean isThorn = false;

    public void setThorn(boolean thorn) {
        this.isThorn = thorn;
    }

    public boolean isThorn() {
        return isThorn;
    }

    // --- PERGOLA LOGIC (Luke) ---
    private boolean isPergola = false;

    public void setPergola(boolean pergola) {
        this.isPergola = pergola;
    }

    public boolean isPergola() {
        return isPergola;
    }

    // --- DRYWALL LOGIC (Thomas) ---
    private boolean isDrywall = false;

    public void setDrywall(boolean drywall) {
        this.isDrywall = drywall;
    }

    public boolean isDrywall() {
        return isDrywall;
    }

    // --- CLOTHES LOGIC (Ghia) ---
    private boolean isClothes = false;

    public void setClothes(boolean clothes) {
        this.isClothes = clothes;
    }

    public boolean isClothes() {
        return isClothes;
    }

    // --- FIRE LOGIC (Lark) ---
    //
    // fireTurnsActive — how many turns this tile has been burning.
    //                   Drives damage: deals fireTurnsActive true damage each turn start.
    //                   Starts at 1 on first ignition and increments every tick.
    //                   Used as the isOnFire() sentinel: fire is active while > 0.
    //
    // fireDuration    — turns remaining before the fire goes out.
    //                   Stacks additively when fire is applied to an already-burning tile.
    //
    // Lifecycle for duration=1:
    //   applyFire(src, 1)   -> fireDuration=1, fireTurnsActive=1  [fire visible]
    //   startTurn damage    -> deals 1 true damage
    //   endTurn  tickFire() -> fireDuration=0 -> extinguished      [fire gone]
    //
    // Lifecycle for duration=2:
    //   applyFire(src, 2)   -> fireDuration=2, fireTurnsActive=1
    //   startTurn damage    -> deals 1 true damage
    //   endTurn  tickFire() -> fireDuration=1, fireTurnsActive=2
    //   startTurn damage    -> deals 2 true damage
    //   endTurn  tickFire() -> fireDuration=0 -> extinguished
    //
    private int fireTurnsActive = 0;
    private int fireDuration    = 0;
    private Character fireSource = null;

    /**
     * Apply (or stack) fire on this tile.
     * Duration adds on top of any existing duration so re-igniting always
     * extends rather than resets the fire.
     */
    public void applyFire(Character source, int duration) {
        this.fireSource = source;
        this.fireDuration += duration;
        if (this.fireTurnsActive == 0) {
            this.fireTurnsActive = 1;
        }
    }

    /**
     * True while this tile is burning.
     * Driven by fireTurnsActive so the renderer and damage loop agree.
     */
    public boolean isOnFire() {
        return fireTurnsActive > 0;
    }

    /** How many turns this tile has been on fire — used as the damage value. */
    public int getFireTurnsActive() {
        return fireTurnsActive;
    }

    public Character getFireSource() {
        return fireSource;
    }

    /**
     * Called at the end of each character's turn (GameEngine.endTurn).
     *
     * Decrements remaining duration. Extinguishes only when duration hits zero,
     * which happens AFTER startTurn() has dealt damage for that final turn.
     * fireTurnsActive is incremented here (not in startTurn) so damage escalates
     * correctly on subsequent turns.
     */
    public void tickFire() {
        if (fireTurnsActive == 0) return; // nothing to tick

        fireDuration--;
        if (fireDuration <= 0) {
            // Duration exhausted — extinguish completely.
            fireDuration    = 0;
            fireTurnsActive = 0;
            fireSource      = null;
        } else {
            // Still has turns remaining — age the fire so damage escalates next turn.
            fireTurnsActive++;
        }
    }

    /**
     * Called when a structure's HP drops to 0.
     * Clears all structure-type flags so the tile reverts to a normal passable tile.
     */
    public void clearStructureFlags() {
        isPergola = false;
        isDrywall = false;
        isClothes = false;
        isThorn   = false;
    }

    /**
     * Called when units attack the wall or Evan slams someone into it.
     */
    public void applyStructureDamage(int dmg) {
        if (structureHealth > 0) {
            structureHealth -= dmg;
            if (structureHealth <= 0) {
                structureHealth = 0;
                clearStructureFlags();
            }
        }
    }
}