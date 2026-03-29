package com.mygame.tactics;

import com.badlogic.gdx.graphics.Color;

/**
 * Holds the player's chosen username and visual customisation.
 * Passed through screens and eventually used for rendering.
 */
public class PlayerAppearance {

    public String username     = "Player";
    public int    modelType    = 0;   // 0 = standard, 1 = stocky
    public int    skinColorIdx = 0;
    public int    shirtColorIdx = 0;
    public int    pantsColorIdx = 0;

    // -----------------------------------------------------------------------
    // Palettes
    // -----------------------------------------------------------------------

    public static final Color[] SKIN_COLORS = {
        new Color(0.94f, 0.78f, 0.62f, 1f),   // light
        new Color(0.80f, 0.60f, 0.40f, 1f),   // medium
        new Color(0.58f, 0.38f, 0.22f, 1f),   // tan
        new Color(0.30f, 0.18f, 0.10f, 1f),   // dark
    };

    public static final Color[] CLOTHES_COLORS = {
        new Color(0.20f, 0.45f, 0.88f, 1f),   // blue
        new Color(0.82f, 0.18f, 0.18f, 1f),   // red
        new Color(0.18f, 0.65f, 0.22f, 1f),   // green
        new Color(0.88f, 0.72f, 0.08f, 1f),   // yellow
        new Color(0.60f, 0.18f, 0.78f, 1f),   // purple
        new Color(0.88f, 0.48f, 0.08f, 1f),   // orange
        new Color(0.82f, 0.82f, 0.82f, 1f),   // white
        new Color(0.16f, 0.16f, 0.20f, 1f),   // black
    };

    public Color getSkinColor()  { return SKIN_COLORS[skinColorIdx]; }
    public Color getShirtColor() { return CLOTHES_COLORS[shirtColorIdx]; }
    public Color getPantsColor() { return CLOTHES_COLORS[pantsColorIdx]; }
}
