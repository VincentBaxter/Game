package com.mygame.tactics;

import com.badlogic.gdx.utils.Array;
import com.mygame.tactics.characters.Mason;

public class CombatBoard {
    private Character[][] characterGrid;
    private Tile[][]      tiles;
    private int rows, cols;

    public CombatBoard(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.characterGrid = new Character[rows][cols];
        this.tiles = new Tile[rows][cols];
        for (int i = 0; i < rows; i++)
            for (int j = 0; j < cols; j++)
                tiles[i][j] = new Tile(i, j);
    }

    // --- TILE EFFECTS ---
    public void applyPoison(int x, int y) {
        if (isValid(x, y)) tiles[x][y].setPoison(true);
    }

    public void applyFire(int x, int y, int duration) {
        if (isValid(x, y)) tiles[x][y].applyFire(duration);
    }

    public void placeWall(int x, int y) {
        if (isValid(x, y)) tiles[x][y].setStructureHP(30);
    }

    // --- CHARACTER MANAGEMENT ---
    public void addCharacter(Character c, int x, int y) {
        if (isValid(x, y)) { characterGrid[x][y] = c; c.x = x; c.y = y; }
    }

    public void removeCharacter(int x, int y) {
        if (isValid(x, y)) characterGrid[x][y] = null;
    }

    /**
     * Moves a character on the board.
     * Returns any events generated (ambush popup, etc.) instead of calling back into the screen.
     *
     * If newX/newY is off the board the character is knocked into the void:
     * they are removed from the grid, their health is zeroed, and a
     * "INTO THE VOID" popup event is returned so GameEngine can run handleDeath.
     */
    public Array<EngineEvent> moveCharacter(Character mover, int newX, int newY) {
        Array<EngineEvent> events = new Array<>();

        boolean isTakeFlightActive =
                (mover instanceof Mason && ((Mason) mover).isTakeFlightActive);
        if (mover.getCharClass() == Enums.CharClass.STATUE
                && !mover.isInvisible() && !isTakeFlightActive) {
            return events;
        }

        // Remove from current position first
        if (isValid(mover.x, mover.y)) removeCharacter(mover.x, mover.y);

        // Off-board = knocked into the void
        if (!isValid(newX, newY)) {
            mover.setHealth(0);
            events.add(new EngineEvent.PopupEvent("INTO THE VOID", 0, "VOID",
                    Math.max(0, Math.min(newX, rows - 1)),
                    Math.max(0, Math.min(newY, cols - 1))));
            events.add(new EngineEvent.CharacterKilledEvent(mover, "VOID"));
            return events;
        }

        // Collapsed tile = knocked into the void
        if (isCollapsedAt(newX, newY)) {
            mover.setHealth(0);
            events.add(new EngineEvent.PopupEvent("INTO THE VOID", 0, "VOID", newX, newY));
            events.add(new EngineEvent.CharacterKilledEvent(mover, "VOID"));
            return events;
        }

        addCharacter(mover, newX, newY);

        // Gargoyle Unleashed: if an enemy moves adjacent to an invisible Mason
        // who hasn't triggered yet, instantly kill the mover.
        for (int x = newX - 1; x <= newX + 1; x++) {
            for (int y = newY - 1; y <= newY + 1; y++) {
                if (x == newX && y == newY) continue;
                Character neighbor = getCharacterAt(x, y);
                if (neighbor instanceof Mason
                        && neighbor.isInvisible()
                        && !((Mason) neighbor).gargoyleTriggered
                        && neighbor.team != mover.team) {
                    Mason mason = (Mason) neighbor;
                    mason.gargoyleTriggered = true;
                    mason.setInvisible(false);
                    mason.setUltActive(false);
                    // Remove the victim from the target tile, then move Mason onto it
                    removeCharacter(newX, newY);
                    removeCharacter(mason.x, mason.y);
                    addCharacter(mason, newX, newY);
                    events.add(new EngineEvent.PopupEvent("GARGOYLE!", 0, "ACTIVE", newX, newY));
                    events.add(new EngineEvent.PopupEvent("REVEALED", 0, "STATUS", newX, newY));
                    events.add(new EngineEvent.CharacterKilledEvent(mover, "GARGOYLE"));
                    return events;
                }
            }
        }
        return events;
    }


    // --- GETTERS & UTILS ---
    public Character getCharacterAt(int x, int y) {
        return isValid(x, y) ? characterGrid[x][y] : null;
    }

    public Tile getTile(int x, int y) {
        return isValid(x, y) ? tiles[x][y] : null;
    }

    public boolean isValid(int x, int y) {
        return x >= 0 && x < rows && y >= 0 && y < cols;
    }

    public boolean isCollapsedAt(int x, int y) {
        if (!isValid(x, y)) return true;
        return tiles[x][y].isCollapsed();
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
}