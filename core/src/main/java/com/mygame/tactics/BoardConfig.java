package com.mygame.tactics;

import com.badlogic.gdx.graphics.Color;

public class BoardConfig {

    public enum BoardType { FOREST, WIND, DESERT }

    public enum CollapseStyle {
        RING,         // Forest: standard ring collapse relative to Haven
        WIND_PUSH,    // Wind:   push all characters, grow distance each event
        DESERT_TILE   // Desert: collapse furthest tile from Haven on each move
    }

    public final BoardType     type;
    public final int           rows, cols;
    public final CollapseStyle collapseStyle;
    public final float         initialCollapseWait;
    public final int           havenStartX, havenStartY;
    public final boolean       onMoveCollapse;
    public final boolean       preCollapseOuterRing;

    // Rendering
    public final Color tileColorA;
    public final Color tileColorB;
    public final Color backgroundColor;

    private BoardConfig(Builder b) {
        this.type                 = b.type;
        this.rows                 = b.rows;
        this.cols                 = b.cols;
        this.collapseStyle        = b.collapseStyle;
        this.initialCollapseWait  = b.initialCollapseWait;
        this.havenStartX          = b.havenStartX;
        this.havenStartY          = b.havenStartY;
        this.onMoveCollapse       = b.onMoveCollapse;
        this.preCollapseOuterRing = b.preCollapseOuterRing;
        this.tileColorA           = b.tileColorA;
        this.tileColorB           = b.tileColorB;
        this.backgroundColor      = b.backgroundColor;
    }

    // --- Preset factories ---

    public static BoardConfig forest() {
        return new Builder()
            .type(BoardType.FOREST)
            .size(9, 9)
            .collapseStyle(CollapseStyle.RING)
            .collapseWait(1000f)
            .havenStart(4, 4)
            .onMoveCollapse(false)
            .preCollapseOuterRing(false)
            .tileColors(
                new Color(0.15f, 0.30f, 0.15f, 1f),
                new Color(0.25f, 0.45f, 0.25f, 1f))
            .backgroundColor(new Color(0.1f, 0.1f, 0.12f, 1f))
            .build();
    }

    public static BoardConfig wind() {
        return new Builder()
            .type(BoardType.WIND)
            .size(9, 9)
            .collapseStyle(CollapseStyle.WIND_PUSH)
            .collapseWait(1000f)
            .havenStart(4, 4)
            .onMoveCollapse(false)
            .preCollapseOuterRing(true)
            .tileColors(
            	    new Color(0.82f, 0.84f, 0.86f, 1f),  // light gray
            	    new Color(0.68f, 0.70f, 0.72f, 1f))   // slightly darker gray
            	.backgroundColor(new Color(0.60f, 0.62f, 0.64f, 1f))
            .build();
    }

    public static BoardConfig desert() {
        return new Builder()
            .type(BoardType.DESERT)
            .size(9, 9)
            .collapseStyle(CollapseStyle.DESERT_TILE)
            .collapseWait(1000f)
            .havenStart(4, 4)
            .onMoveCollapse(true)
            .preCollapseOuterRing(false)
            .tileColors(
                new Color(0.76f, 0.60f, 0.30f, 1f),
                new Color(0.85f, 0.72f, 0.45f, 1f))
            .backgroundColor(new Color(0.18f, 0.14f, 0.08f, 1f))
            .build();
    }

    // -----------------------------------------------------------------------
    // Builder
    // -----------------------------------------------------------------------

    public static class Builder {
        private BoardType     type                 = BoardType.FOREST;
        private int           rows                 = 9;
        private int           cols                 = 9;
        private CollapseStyle collapseStyle        = CollapseStyle.RING;
        private float         initialCollapseWait  = 1000f;
        private int           havenStartX          = 4;
        private int           havenStartY          = 4;
        private boolean       onMoveCollapse       = false;
        private boolean       preCollapseOuterRing = false;
        private Color         tileColorA           = new Color(0.15f, 0.30f, 0.15f, 1f);
        private Color         tileColorB           = new Color(0.25f, 0.45f, 0.25f, 1f);
        private Color         backgroundColor      = new Color(0.1f, 0.1f, 0.12f, 1f);

        public Builder() {}

        public Builder type(BoardType v)                 { this.type = v;                 return this; }
        public Builder size(int rows, int cols)          { this.rows = rows;
                                                           this.cols = cols;               return this; }
        public Builder collapseStyle(CollapseStyle v)    { this.collapseStyle = v;         return this; }
        public Builder collapseWait(float v)             { this.initialCollapseWait = v;   return this; }
        public Builder havenStart(int x, int y)          { this.havenStartX = x;
                                                           this.havenStartY = y;           return this; }
        public Builder onMoveCollapse(boolean v)         { this.onMoveCollapse = v;        return this; }
        public Builder preCollapseOuterRing(boolean v)   { this.preCollapseOuterRing = v;  return this; }
        public Builder tileColors(Color a, Color b)      { this.tileColorA = a;
                                                           this.tileColorB = b;            return this; }
        public Builder backgroundColor(Color v)          { this.backgroundColor = v;       return this; }

        public BoardConfig build()                       { return new BoardConfig(this);   }
    }
}