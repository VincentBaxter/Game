package com.mygame.tactics;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class Main extends Game {
    public static final String VERSION = "1.0.0";

    /** Per-player story flags — persisted to disk, accessible from any screen. */
    public static PlayerFlags flags;

    public SpriteBatch batch;
    public BitmapFont font;

    @Override
    public void create() {
        flags = new PlayerFlags();
        batch = new SpriteBatch();
        font = new BitmapFont(Gdx.files.internal("font.fnt"));
        font.getRegion().getTexture().setFilter(
            Texture.TextureFilter.Linear,
            Texture.TextureFilter.Linear);
        // Base scale — increase this if text still feels small.
        font.getData().setScale(1.0f);

        // Open the draft screen first; it will transition to CombatScreen
        // once both teams have finished picking.
        this.setScreen(new MenuScreen(this));
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}