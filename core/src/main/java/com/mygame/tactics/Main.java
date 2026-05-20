package com.mygame.tactics;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class Main extends Game {
    public static final String VERSION = "1.0.0";

    /** Per-player story flags — persisted to disk, accessible from any screen. */
    public static PlayerFlags flags;

    /** Global music volume [0.0, 1.0] — persisted via LibGDX Preferences. */
    public static float musicVolume = 1.0f;

    private static Preferences prefs;
    private static Music menuMusic;

    public SpriteBatch batch;
    public BitmapFont font;

    public static void saveMusicVolume(float volume) {
        musicVolume = volume;
        if (prefs != null) { prefs.putFloat("musicVolume", volume); prefs.flush(); }
        if (menuMusic != null) menuMusic.setVolume(volume);
    }

    /** Call from any menu/settings screen show() to start the shared menu music. */
    public static void startMenuMusic() {
        if (menuMusic == null) return;
        menuMusic.setVolume(musicVolume);
        if (!menuMusic.isPlaying()) menuMusic.play();
    }

    /** Call from any screen that is NOT the menu/settings to stop menu music. */
    public static void stopMenuMusic() {
        if (menuMusic != null) menuMusic.stop();
    }

    @Override
    public void create() {
        prefs = Gdx.app.getPreferences("HavenGameSettings");
        musicVolume = prefs.getFloat("musicVolume", 1.0f);

        // Music disabled — uncomment to re-enable
        // try {
        //     menuMusic = Gdx.audio.newMusic(Gdx.files.internal("MainMenuMusic.mp3"));
        //     menuMusic.setLooping(true);
        // } catch (Exception ignored) {}

        flags = new PlayerFlags();
        batch = new SpriteBatch();
        font = new BitmapFont(Gdx.files.internal("font.fnt"));
        font.getRegion().getTexture().setFilter(
            Texture.TextureFilter.Linear,
            Texture.TextureFilter.Linear);
        // Base scale — increase this if text still feels small.
        font.getData().setScale(1.0f);

        this.setScreen(new MenuScreen(this));
    }

    @Override
    public void dispose() {
        if (menuMusic != null) menuMusic.dispose();
        batch.dispose();
        font.dispose();
    }
}