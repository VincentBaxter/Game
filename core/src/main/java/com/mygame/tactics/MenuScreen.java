package com.mygame.tactics;

import java.awt.FileDialog;
import java.awt.Frame;
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

/**
 * Main menu screen.
 *
 * Coordinate system: Y=0 bottom, Y=720 top.
 *
 * Layout (working upward from the bottom of the screen):
 *
 *   BTN_SET bottom = 60    (bottom button sits 60px from screen bottom)
 *   BTN_SET top    = 132
 *   BTN_ONL bottom = 150   (gap of 18px between buttons)
 *   BTN_ONL top    = 222
 *   BTN_SP  bottom = 240
 *   BTN_SP  top    = 312
 *
 *   RULE_Y  = 370           (58px above the top of the first button)
 *
 *   TITLE_BASELINE = 530    (160px above the rule — title cap top ≈ 680)
 */
public class MenuScreen implements Screen {

    // -----------------------------------------------------------------------
    // Palette
    // -----------------------------------------------------------------------
    private static final Color GOLD = new Color(1.00f, 0.84f, 0.00f, 1f);
    private static final Color BLUE = new Color(0.00f, 0.64f, 0.91f, 1f);
    private static final Color BG   = new Color(0.04f, 0.04f, 0.08f, 1f);

    // -----------------------------------------------------------------------
    // Title
    // -----------------------------------------------------------------------
    private static final float TITLE_SCALE    = 3.4f;
    private static final float TITLE_BASELINE = 572f;

    // -----------------------------------------------------------------------
    // Ornamental rule — sits between title and buttons
    // -----------------------------------------------------------------------
    private static final float RULE_Y = 502f;
    private static final float RULE_W = 420f;

    // -----------------------------------------------------------------------
    // Buttons — anchored from the bottom of the screen upward
    // -----------------------------------------------------------------------
    private static final float BTN_W   = 360f;
    private static final float BTN_H   = 68f;
    private static final float BTN_GAP = 12f;
    private static final float BTN_X   = 640f - BTN_W / 2f;

    // Bottom edges, working upward (5 buttons)
    private static final float BTN_SET_BOTTOM = 38f;
    private static final float BTN_SET_Y      = BTN_SET_BOTTOM;
    private static final float BTN_ONL_Y      = BTN_SET_Y   + BTN_H + BTN_GAP;
    private static final float BTN_WORLD_Y    = BTN_ONL_Y   + BTN_H + BTN_GAP;
    private static final float BTN_ME_Y       = BTN_WORLD_Y + BTN_H + BTN_GAP;
    private static final float BTN_SP_Y       = BTN_ME_Y    + BTN_H + BTN_GAP;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------
    private final Main               game;
    private final OrthographicCamera camera;
    private final FitViewport        viewport;
    private final Texture            whitePixel;
    private final GlyphLayout        layout;

    private final Rectangle btnSinglePlayer = new Rectangle(BTN_X, BTN_SP_Y,    BTN_W, BTN_H);
    private final Rectangle btnMapEditor    = new Rectangle(BTN_X, BTN_ME_Y,    BTN_W, BTN_H);
    private final Rectangle btnWorld        = new Rectangle(BTN_X, BTN_WORLD_Y, BTN_W, BTN_H);
    private final Rectangle btnOnline       = new Rectangle(BTN_X, BTN_ONL_Y,   BTN_W, BTN_H);
    private final Rectangle btnSettings     = new Rectangle(BTN_X, BTN_SET_Y,   BTN_W, BTN_H);

    private int   hoveredBtn = -1;
    private float stateTime  = 0f;

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public MenuScreen(Main game) {
        this.game = game;
        camera   = new OrthographicCamera();
        viewport = new FitViewport(1280, 720, camera);
        camera.position.set(640, 360, 0);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE); pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();

        layout = new GlyphLayout();
    }

    // -----------------------------------------------------------------------
    // Screen lifecycle
    // -----------------------------------------------------------------------
    @Override public void show()   {}
    @Override public void resize(int w, int h) { viewport.update(w, h); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void render(float delta) {
        stateTime += delta;
        handleInput();

        ScreenUtils.clear(BG.r, BG.g, BG.b, 1f);
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        drawTitle(game.batch);
        drawOrnamentalRule(game.batch, 640f, RULE_Y, RULE_W);
        drawButton(game.batch, btnSinglePlayer, "SINGLE PLAYER", 0);
        drawButton(game.batch, btnMapEditor,    "MAP EDITOR",    1);
        drawButton(game.batch, btnWorld,        "WORLD",         2);
        drawButton(game.batch, btnOnline,       "ONLINE",        3);
        drawButton(game.batch, btnSettings,     "SETTINGS",      4);

        game.batch.end();
    }

    @Override
    public void dispose() {
        whitePixel.dispose();
    }

    // -----------------------------------------------------------------------
    // Input
    // -----------------------------------------------------------------------
    private void handleInput() {
        Vector3 world = camera.unproject(
                new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));

        if      (btnSinglePlayer.contains(world.x, world.y)) hoveredBtn = 0;
        else if (btnMapEditor   .contains(world.x, world.y)) hoveredBtn = 1;
        else if (btnWorld       .contains(world.x, world.y)) hoveredBtn = 2;
        else if (btnOnline      .contains(world.x, world.y)) hoveredBtn = 3;
        else if (btnSettings    .contains(world.x, world.y)) hoveredBtn = 4;
        else                                                  hoveredBtn = -1;

        if (!Gdx.input.justTouched()) return;

        if (btnSinglePlayer.contains(world.x, world.y)) {
            game.setScreen(new DraftScreen(game));
        } else if (btnMapEditor.contains(world.x, world.y)) {
            game.setScreen(new MapEditorScreen(game));
        } else if (btnWorld.contains(world.x, world.y)) {
            FileDialog fd = new FileDialog((Frame) null, "Open World Area", FileDialog.LOAD);
            fd.setFilenameFilter((dir, name) -> name.endsWith(".txt"));
            fd.setVisible(true);
            if (fd.getFile() != null) {
                try {
                    WorldArea area = WorldArea.load(Gdx.files.absolute(fd.getDirectory() + fd.getFile()));
                    game.setScreen(new WorldScreen(game, area));
                } catch (Exception ignored) {}
            }
        } else if (btnOnline.contains(world.x, world.y)) {
            game.setScreen(new CharacterCreationScreen(game));
        }
        // Settings: TODO
    }

    // -----------------------------------------------------------------------
    // Title
    // -----------------------------------------------------------------------
    private void drawTitle(SpriteBatch b) {
        String title = "TACTICS";
        game.font.getData().setScale(TITLE_SCALE);

        layout.setText(game.font, title);
        float titleX = 640f - layout.width / 2f;

        // Drop shadow
        game.font.setColor(0f, 0f, 0f, 0.50f);
        game.font.draw(b, title, titleX + 3f, TITLE_BASELINE - 3f);

        // Gold shimmer
        float s = 0.88f + (float) Math.sin(stateTime * 1.1f) * 0.06f;
        game.font.setColor(s, s * 0.84f, 0.00f, 1f);
        game.font.draw(b, title, titleX, TITLE_BASELINE);

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
    }

    // -----------------------------------------------------------------------
    // Buttons
    // -----------------------------------------------------------------------
    private void drawButton(SpriteBatch b, Rectangle r, String label, int idx) {
        boolean hovered = (hoveredBtn == idx);
        boolean dimmed  = (idx == 3 || idx == 4);
        float   aw      = 6f;
        Color   ac      = dimmed ? BLUE : GOLD;
        float   acA     = dimmed ? 0.30f : (hovered ? 1.0f : 0.72f);

        // Outer glow / hairline border
        if (hovered && !dimmed) {
            b.setColor(GOLD.r, GOLD.g, GOLD.b, 0.70f);
            b.draw(whitePixel, r.x - 2, r.y - 2, r.width + 4, r.height + 4);
        } else {
            b.setColor(ac.r, ac.g, ac.b, 0.14f);
            b.draw(whitePixel, r.x - 1, r.y - 1, r.width + 2, r.height + 2);
        }

        // Body
        if (hovered && !dimmed) {
            b.setColor(0.05f, 0.16f, 0.28f, 1f);
        } else if (dimmed) {
            b.setColor(0.05f, 0.05f, 0.09f, 1f);
        } else {
            b.setColor(0.08f, 0.08f, 0.15f, 1f);
        }
        b.draw(whitePixel, r.x, r.y, r.width, r.height);

        // Inner top highlight
        if (!dimmed) {
            b.setColor(1f, 1f, 1f, hovered ? 0.07f : 0.03f);
            b.draw(whitePixel, r.x, r.y + r.height - 2f, r.width, 2f);
        }

        // Left accent bar
        b.setColor(ac.r, ac.g, ac.b, acA);
        b.draw(whitePixel, r.x, r.y, aw, r.height);

        // Right accent bar
        b.setColor(ac.r, ac.g, ac.b, acA);
        b.draw(whitePixel, r.x + r.width - aw, r.y, aw, r.height);

        // Corner rivets
        float ds = 4f;
        b.setColor(ac.r, ac.g, ac.b, dimmed ? 0.28f : (hovered ? 0.90f : 0.55f));
        b.draw(whitePixel, r.x + aw,                r.y,                 ds, ds);
        b.draw(whitePixel, r.x + aw,                r.y + r.height - ds, ds, ds);
        b.draw(whitePixel, r.x + r.width - aw - ds, r.y,                 ds, ds);
        b.draw(whitePixel, r.x + r.width - aw - ds, r.y + r.height - ds, ds, ds);

        // Text — dimmed buttons show label + "COMING SOON" as a two-line stack,
        // measured and centred so they never overlap.
        if (dimmed) {
            game.font.getData().setScale(0.72f);
            layout.setText(game.font, label);
            float labelH = layout.height;
            float labelW = layout.width;

            game.font.getData().setScale(0.36f);
            layout.setText(game.font, "COMING SOON");
            float csH = layout.height;
            float csW = layout.width;

            float lineGap  = 6f;
            float totalH   = labelH + lineGap + csH;
            float blockTop = r.y + (r.height + totalH) / 2f;

            game.font.getData().setScale(0.72f);
            game.font.setColor(0.32f, 0.32f, 0.40f, 1f);
            game.font.draw(b, label, r.x + (r.width - labelW) / 2f, blockTop);

            game.font.getData().setScale(0.36f);
            game.font.setColor(BLUE.r, BLUE.g, BLUE.b, 0.55f);
            game.font.draw(b, "COMING SOON",
                    r.x + (r.width - csW) / 2f,
                    blockTop - labelH - lineGap);

        } else {
            game.font.getData().setScale(0.78f);
            game.font.setColor(hovered ? Color.WHITE : new Color(0.80f, 0.80f, 0.88f, 1f));
            layout.setText(game.font, label);
            float labelX = r.x + (r.width  - layout.width)  / 2f;
            float labelY = r.y + (r.height + layout.height) / 2f;
            game.font.draw(b, label, labelX, labelY);
        }

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    // -----------------------------------------------------------------------
    // Ornamental rule
    // small-diamond — line — centre-diamond — line — small-diamond
    // -----------------------------------------------------------------------
    private void drawOrnamentalRule(SpriteBatch b, float cx, float y, float totalW) {
        float lineH  = 1.5f;
        float bigR   = 5f;
        float smallR = 3.5f;
        float halfW  = totalW / 2f;

        // Left line
        b.setColor(GOLD.r, GOLD.g, GOLD.b, 0.55f);
        b.draw(whitePixel, cx - halfW + smallR * 2f, y - lineH / 2f,
                halfW - smallR * 2f - bigR * 2f, lineH);

        // Right line
        b.draw(whitePixel, cx + bigR * 2f, y - lineH / 2f,
                halfW - smallR * 2f - bigR * 2f, lineH);

        // Centre diamond
        b.setColor(GOLD.r, GOLD.g, GOLD.b, 0.90f);
        b.draw(whitePixel, cx - bigR,  y - bigR * 0.5f, bigR * 2f, bigR);
        b.draw(whitePixel, cx - 1.5f,  y - bigR,        3f,        bigR * 2f);

        // Left end diamond
        b.setColor(GOLD.r, GOLD.g, GOLD.b, 0.65f);
        float ldx = cx - halfW;
        b.draw(whitePixel, ldx,                  y - smallR * 0.5f, smallR * 2f, smallR);
        b.draw(whitePixel, ldx + smallR * 0.35f, y - smallR,        1.5f,        smallR * 2f);

        // Right end diamond
        float rdx = cx + halfW - smallR * 2f;
        b.draw(whitePixel, rdx,                  y - smallR * 0.5f, smallR * 2f, smallR);
        b.draw(whitePixel, rdx + smallR * 0.35f, y - smallR,        1.5f,        smallR * 2f);

        b.setColor(Color.WHITE);
    }
}