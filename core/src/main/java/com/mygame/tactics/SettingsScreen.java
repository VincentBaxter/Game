package com.mygame.tactics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

public class SettingsScreen implements Screen {

    private static final Color GOLD = new Color(1.00f, 0.84f, 0.00f, 1f);
    private static final Color BG   = new Color(0.04f, 0.04f, 0.08f, 1f);

    // Slider geometry
    private static final float TRACK_X     = 440f;
    private static final float TRACK_Y     = 360f;
    private static final float TRACK_W     = 400f;
    private static final float TRACK_H     =   6f;
    private static final float KNOB_R      =  14f;

    // Back button
    private static final float BTN_W = 200f;
    private static final float BTN_H =  56f;
    private static final float BTN_X = 640f - BTN_W / 2f;
    private static final float BTN_Y = 200f;

    private final Main               game;
    private final OrthographicCamera camera;
    private final FitViewport        viewport;
    private final Texture            whitePixel;
    private final GlyphLayout        layout;
    private final Rectangle          backBtn;
    private final Rectangle          trackHitArea; // wider than the visual track for easy grab

    private float volume;       // current slider value [0,1]
    private boolean dragging = false;
    private boolean backHovered = false;

    public SettingsScreen(Main game) {
        this.game = game;
        camera   = new OrthographicCamera();
        viewport = new FitViewport(1280, 720, camera);
        camera.position.set(640, 360, 0);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE); pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();

        layout   = new GlyphLayout();
        backBtn  = new Rectangle(BTN_X, BTN_Y, BTN_W, BTN_H);
        // Hit area: full track width + knob diameter on each side, tall enough for easy click
        trackHitArea = new Rectangle(TRACK_X - KNOB_R, TRACK_Y - KNOB_R * 2,
                                     TRACK_W + KNOB_R * 2, KNOB_R * 4);

        volume = Main.musicVolume;
    }

    @Override public void show()   { Main.startMenuMusic(); }
    @Override public void resize(int w, int h) { viewport.update(w, h); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}
    @Override public void dispose() { whitePixel.dispose(); }

    @Override
    public void render(float delta) {
        handleInput();

        ScreenUtils.clear(BG.r, BG.g, BG.b, 1f);
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        drawTitle(game.batch);
        drawSlider(game.batch);
        drawBackButton(game.batch);

        game.batch.end();
    }

    // -----------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------
    private void handleInput() {
        Vector3 world = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        backHovered = backBtn.contains(world.x, world.y);

        boolean touched = Gdx.input.isTouched();

        if (touched && trackHitArea.contains(world.x, world.y)) {
            dragging = true;
        }
        if (!touched) {
            dragging = false;
        }

        if (dragging) {
            float raw = (world.x - TRACK_X) / TRACK_W;
            volume = Math.max(0f, Math.min(1f, raw));
            Main.saveMusicVolume(volume);
        }

        if (Gdx.input.justTouched() && backBtn.contains(world.x, world.y)) {
            game.setScreen(new MenuScreen(game));
        }
    }

    // -----------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------
    private void drawTitle(SpriteBatch b) {
        game.font.getData().setScale(2.2f);
        game.font.setColor(GOLD.r, GOLD.g, GOLD.b, 1f);
        layout.setText(game.font, "SETTINGS");
        game.font.draw(b, "SETTINGS", 640f - layout.width / 2f, 580f);
        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
    }

    private void drawSlider(SpriteBatch b) {
        float knobX = TRACK_X + volume * TRACK_W;
        float knobCY = TRACK_Y + TRACK_H / 2f;

        // Section label
        game.font.getData().setScale(0.60f);
        game.font.setColor(0.70f, 0.70f, 0.80f, 1f);
        game.font.draw(b, "MUSIC VOLUME", TRACK_X, TRACK_Y + 48f);

        // Percentage
        String pct = Math.round(volume * 100) + "%";
        layout.setText(game.font, pct);
        game.font.setColor(Color.WHITE);
        game.font.draw(b, pct, TRACK_X + TRACK_W + 20f, TRACK_Y + TRACK_H + layout.height / 2f + 2f);

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);

        // Track background
        b.setColor(0.20f, 0.20f, 0.28f, 1f);
        b.draw(whitePixel, TRACK_X, TRACK_Y, TRACK_W, TRACK_H);

        // Filled portion
        b.setColor(GOLD.r, GOLD.g, GOLD.b, 1f);
        b.draw(whitePixel, TRACK_X, TRACK_Y, volume * TRACK_W, TRACK_H);

        // Knob shadow
        b.setColor(0f, 0f, 0f, 0.45f);
        b.draw(whitePixel, knobX - KNOB_R + 2f, knobCY - KNOB_R + 2f, KNOB_R * 2f, KNOB_R * 2f);

        // Knob body
        b.setColor(dragging ? Color.WHITE : new Color(0.90f, 0.90f, 0.95f, 1f));
        b.draw(whitePixel, knobX - KNOB_R, knobCY - KNOB_R, KNOB_R * 2f, KNOB_R * 2f);

        // Knob gold border
        b.setColor(GOLD.r, GOLD.g, GOLD.b, dragging ? 1f : 0.75f);
        b.draw(whitePixel, knobX - KNOB_R,      knobCY - KNOB_R,      KNOB_R * 2f, 2f);
        b.draw(whitePixel, knobX - KNOB_R,      knobCY + KNOB_R - 2f, KNOB_R * 2f, 2f);
        b.draw(whitePixel, knobX - KNOB_R,      knobCY - KNOB_R,      2f, KNOB_R * 2f);
        b.draw(whitePixel, knobX + KNOB_R - 2f, knobCY - KNOB_R,      2f, KNOB_R * 2f);

        b.setColor(Color.WHITE);
    }

    private void drawBackButton(SpriteBatch b) {
        // Border
        b.setColor(GOLD.r, GOLD.g, GOLD.b, backHovered ? 0.85f : 0.35f);
        b.draw(whitePixel, backBtn.x - 1, backBtn.y - 1, backBtn.width + 2, backBtn.height + 2);

        // Body
        b.setColor(backHovered ? 0.05f : 0.08f, backHovered ? 0.16f : 0.08f,
                   backHovered ? 0.28f : 0.15f, 1f);
        b.draw(whitePixel, backBtn.x, backBtn.y, backBtn.width, backBtn.height);

        // Label
        game.font.getData().setScale(0.72f);
        game.font.setColor(backHovered ? Color.WHITE : new Color(0.75f, 0.75f, 0.85f, 1f));
        layout.setText(game.font, "BACK");
        game.font.draw(b, "BACK",
                backBtn.x + (backBtn.width  - layout.width)  / 2f,
                backBtn.y + (backBtn.height + layout.height) / 2f);
        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }
}
