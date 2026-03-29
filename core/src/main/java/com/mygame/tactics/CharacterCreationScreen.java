package com.mygame.tactics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.mygame.tactics.network.NetworkClient;

/**
 * Character creation screen shown before entering online play.
 * Player chooses a username, character model, skin colour, shirt colour, and pants colour.
 * Pressing CONTINUE proceeds to OnlineScreen (server IP entry).
 */
public class CharacterCreationScreen implements Screen {

    // -----------------------------------------------------------------------
    // Layout constants
    // -----------------------------------------------------------------------
    private static final float CX            = 640f;  // horizontal centre
    private static final Color BG            = new Color(0.04f, 0.04f, 0.08f, 1f);
    private static final Color GOLD          = new Color(1.00f, 0.84f, 0.00f, 1f);

    // Title
    private static final float TITLE_Y       = 669f;  // font baseline; text extends up ~29px to y≈698

    // Character preview — bottom-left corner of the draw area.
    // Preview occupies PREVIEW_Y … PREVIEW_Y+PREVIEW_TS (≈ 507–619).
    private static final float PREVIEW_TS    = 112f;
    private static final float PREVIEW_X     = CX - PREVIEW_TS / 2f;
    private static final float PREVIEW_Y     = 507f;

    // Model select buttons — ~30px gap below preview bottom (527).
    private static final float MODEL_BTN_W   = 130f;
    private static final float MODEL_BTN_H   = 30f;
    private static final float MODEL_BTN_GAP = 14f;
    private static final float MODEL_BTN_Y   = 467f;  // bottom of buttons; top = 497
    private static final float MODEL_A_X     = CX - MODEL_BTN_W - MODEL_BTN_GAP / 2f;
    private static final float MODEL_B_X     = CX + MODEL_BTN_GAP / 2f;

    // Username — ~38px gap below model buttons (467).
    private static final float UN_BOX_H      = 32f;
    private static final float UN_BOX_W      = 340f;
    private static final float UN_BOX_X      = CX - UN_BOX_W / 2f;
    private static final float UN_BOX_Y      = 381f;  // bottom of box; top = 413
    private static final float UN_LBL_Y      = 433f;  // 20px above box top

    // Skin swatches — ~38px gap below username box (381).
    private static final int   SKIN_N        = 4;
    private static final float SKIN_SZ       = 34f;
    private static final float SKIN_GAP      = 10f;
    private static final float SKIN_ROW_W    = SKIN_N * SKIN_SZ + (SKIN_N - 1) * SKIN_GAP;
    private static final float SKIN_ROW_X    = CX - SKIN_ROW_W / 2f;
    private static final float SKIN_ROW_Y    = 293f;  // bottom of swatches; top = 327
    private static final float SKIN_LBL_Y    = 347f;  // 20px above swatch top

    // Shirt swatches — ~38px gap below skin swatches (293).
    private static final int   CLR_N         = 8;
    private static final float CLR_SZ        = 30f;
    private static final float CLR_GAP       = 7f;
    private static final float CLR_ROW_W     = CLR_N * CLR_SZ + (CLR_N - 1) * CLR_GAP;
    private static final float CLR_ROW_X     = CX - CLR_ROW_W / 2f;
    private static final float SHIRT_ROW_Y   = 209f;  // bottom; top = 239
    private static final float SHIRT_LBL_Y   = 259f;  // 20px above swatch top

    // Pants swatches — ~38px gap below shirt swatches (209).
    private static final float PANTS_ROW_Y   = 125f;  // bottom; top = 155
    private static final float PANTS_LBL_Y   = 175f;  // 20px above swatch top

    // Continue button — ~30px gap below pants swatches (125).
    private static final float CONT_W        = 260f;
    private static final float CONT_H        = 55f;
    private static final float CONT_X        = CX - CONT_W / 2f;
    private static final float CONT_Y        = 40f;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------
    private final Main               game;
    private final OrthographicCamera camera;
    private final FitViewport        viewport;
    private final Texture            whitePixel;
    private final GlyphLayout        layout = new GlyphLayout();

    private final PlayerAppearance   appearance = new PlayerAppearance();

    private boolean usernameActive  = false;
    private float   cursorBlink     = 0f;
    private boolean contHovered     = false;

    // -----------------------------------------------------------------------
    // Input adapter
    // -----------------------------------------------------------------------
    private final InputAdapter inputAdapter = new InputAdapter() {

        @Override
        public boolean keyTyped(char c) {
            if (!usernameActive) return false;
            if (c == '\b') {
                if (appearance.username.length() > 0)
                    appearance.username = appearance.username.substring(0, appearance.username.length() - 1);
            } else if (c >= 32 && c < 127 && appearance.username.length() < 16) {
                appearance.username += c;
            }
            return true;
        }

        @Override
        public boolean touchDown(int sx, int sy, int pointer, int button) {
            Vector3 w = camera.unproject(new Vector3(sx, sy, 0));
            float wx = w.x, wy = w.y;

            // Username field
            usernameActive = (wx >= UN_BOX_X && wx < UN_BOX_X + UN_BOX_W
                           && wy >= UN_BOX_Y  && wy < UN_BOX_Y + UN_BOX_H);

            // Model A
            if (wx >= MODEL_A_X && wx < MODEL_A_X + MODEL_BTN_W
             && wy >= MODEL_BTN_Y && wy < MODEL_BTN_Y + MODEL_BTN_H)
                appearance.modelType = 0;

            // Model B
            if (wx >= MODEL_B_X && wx < MODEL_B_X + MODEL_BTN_W
             && wy >= MODEL_BTN_Y && wy < MODEL_BTN_Y + MODEL_BTN_H)
                appearance.modelType = 1;

            // Skin swatches
            for (int i = 0; i < SKIN_N; i++) {
                float sx2 = SKIN_ROW_X + i * (SKIN_SZ + SKIN_GAP);
                if (wx >= sx2 && wx < sx2 + SKIN_SZ && wy >= SKIN_ROW_Y && wy < SKIN_ROW_Y + SKIN_SZ)
                    appearance.skinColorIdx = i;
            }

            // Shirt swatches
            for (int i = 0; i < CLR_N; i++) {
                float bx = CLR_ROW_X + i * (CLR_SZ + CLR_GAP);
                if (wx >= bx && wx < bx + CLR_SZ && wy >= SHIRT_ROW_Y && wy < SHIRT_ROW_Y + CLR_SZ)
                    appearance.shirtColorIdx = i;
            }

            // Pants swatches
            for (int i = 0; i < CLR_N; i++) {
                float bx = CLR_ROW_X + i * (CLR_SZ + CLR_GAP);
                if (wx >= bx && wx < bx + CLR_SZ && wy >= PANTS_ROW_Y && wy < PANTS_ROW_Y + CLR_SZ)
                    appearance.pantsColorIdx = i;
            }

            // Continue button
            if (wx >= CONT_X && wx < CONT_X + CONT_W && wy >= CONT_Y && wy < CONT_Y + CONT_H) {
                if (appearance.username.trim().isEmpty()) appearance.username = "Player";
                game.setScreen(new OnlineScreen(game, new NetworkClient(), appearance));
            }

            return true;
        }
    };

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public CharacterCreationScreen(Main game) {
        this.game = game;
        camera   = new OrthographicCamera();
        viewport = new FitViewport(1280, 720, camera);
        camera.position.set(640, 360, 0);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE); pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();
    }

    // -----------------------------------------------------------------------
    // Screen lifecycle
    // -----------------------------------------------------------------------
    @Override public void show()   { Gdx.input.setInputProcessor(inputAdapter); }
    @Override public void hide()   { Gdx.input.setInputProcessor(null); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void resize(int w, int h) { viewport.update(w, h); }

    @Override
    public void render(float delta) {
        cursorBlink += delta;

        // Hover detection for continue button
        Vector3 w = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        contHovered = w.x >= CONT_X && w.x < CONT_X + CONT_W
                   && w.y >= CONT_Y && w.y < CONT_Y + CONT_H;

        ScreenUtils.clear(BG.r, BG.g, BG.b, 1f);
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        drawTitle();
        drawPreview();
        drawModelButtons();
        drawUsernameField();
        drawSwatchRow("SKIN COLOR",  SKIN_LBL_Y, SKIN_ROW_Y, SKIN_SZ, SKIN_GAP,
                      SKIN_N, PlayerAppearance.SKIN_COLORS, appearance.skinColorIdx, SKIN_ROW_X);
        drawSwatchRow("SHIRT COLOR", SHIRT_LBL_Y, SHIRT_ROW_Y, CLR_SZ, CLR_GAP,
                      CLR_N, PlayerAppearance.CLOTHES_COLORS, appearance.shirtColorIdx, CLR_ROW_X);
        drawSwatchRow("PANTS COLOR", PANTS_LBL_Y, PANTS_ROW_Y, CLR_SZ, CLR_GAP,
                      CLR_N, PlayerAppearance.CLOTHES_COLORS, appearance.pantsColorIdx, CLR_ROW_X);
        drawContinueButton();

        game.batch.end();
    }

    @Override
    public void dispose() {
        whitePixel.dispose();
    }

    // -----------------------------------------------------------------------
    // Draw helpers
    // -----------------------------------------------------------------------

    private void drawTitle() {
        game.font.getData().setScale(1.8f);
        layout.setText(game.font, "CREATE CHARACTER");
        game.font.setColor(GOLD);
        game.font.draw(game.batch, "CREATE CHARACTER", CX - layout.width / 2f, TITLE_Y);
        game.font.getData().setScale(1f);
        game.font.setColor(Color.WHITE);
    }

    private void drawPreview() {
        drawCharacter(game.batch, PREVIEW_X, PREVIEW_Y, PREVIEW_TS, appearance);
    }

    /**
     * Draws the character using whitePixel quads, scaled to tileSz.
     * Two models:
     *   0 = standard  — tall, slim
     *   1 = stocky    — shorter body, wider shoulders, bigger head
     */
    private void drawCharacter(SpriteBatch b, float ox, float oy, float ts, PlayerAppearance ap) {
        Color skin  = ap.getSkinColor();
        Color shirt = ap.getShirtColor();
        Color pants = ap.getPantsColor();

        if (ap.modelType == 0) {
            // --- Standard ---
            // Shadow
            b.setColor(0f, 0f, 0f, 0.25f);
            b.draw(whitePixel, ox + ts * 0.12f, oy + ts * 0.02f, ts * 0.76f, ts * 0.06f);

            // Legs / pants (lower body)
            float legW = ts * 0.18f, legH = ts * 0.20f;
            float legY = oy + ts * 0.08f;
            b.setColor(pants);
            b.draw(whitePixel, ox + ts * 0.22f, legY, legW, legH);
            b.draw(whitePixel, ox + ts * 0.60f, legY, legW, legH);

            // Body / shirt
            float bodyW = ts * 0.42f, bodyH = ts * 0.28f;
            float bodyX = ox + (ts - bodyW) / 2f, bodyY = oy + ts * 0.26f;
            b.setColor(shirt);
            b.draw(whitePixel, bodyX, bodyY, bodyW, bodyH);

            // Head
            float headSz = ts * 0.30f;
            float headX  = ox + (ts - headSz) / 2f;
            float headY  = bodyY + bodyH + ts * 0.02f;
            b.setColor(skin);
            b.draw(whitePixel, headX, headY, headSz, headSz);

            // Highlight
            b.setColor(1f, 1f, 1f, 0.18f);
            b.draw(whitePixel, headX, headY + headSz - ts * 0.04f, headSz, ts * 0.04f);

        } else {
            // --- Stocky ---
            // Shadow
            b.setColor(0f, 0f, 0f, 0.25f);
            b.draw(whitePixel, ox + ts * 0.08f, oy + ts * 0.02f, ts * 0.84f, ts * 0.06f);

            // Legs / pants
            float legW = ts * 0.20f, legH = ts * 0.18f;
            float legY = oy + ts * 0.08f;
            b.setColor(pants);
            b.draw(whitePixel, ox + ts * 0.18f, legY, legW, legH);
            b.draw(whitePixel, ox + ts * 0.62f, legY, legW, legH);

            // Body / shirt (wide, short)
            float bodyW = ts * 0.58f, bodyH = ts * 0.24f;
            float bodyX = ox + (ts - bodyW) / 2f, bodyY = oy + ts * 0.24f;
            b.setColor(shirt);
            b.draw(whitePixel, bodyX, bodyY, bodyW, bodyH);

            // Shoulders (two small rectangles overhanging the body)
            b.draw(whitePixel, bodyX - ts * 0.05f, bodyY + bodyH - ts * 0.06f, ts * 0.10f, ts * 0.08f);
            b.draw(whitePixel, bodyX + bodyW - ts * 0.05f, bodyY + bodyH - ts * 0.06f, ts * 0.10f, ts * 0.08f);

            // Head (bigger, rounder feel — just a larger square)
            float headSz = ts * 0.35f;
            float headX  = ox + (ts - headSz) / 2f;
            float headY  = bodyY + bodyH + ts * 0.02f;
            b.setColor(skin);
            b.draw(whitePixel, headX, headY, headSz, headSz);

            // Highlight
            b.setColor(1f, 1f, 1f, 0.18f);
            b.draw(whitePixel, headX, headY + headSz - ts * 0.04f, headSz, ts * 0.04f);
        }

        b.setColor(Color.WHITE);
    }

    private void drawModelButtons() {
        drawModelBtn(MODEL_A_X, MODEL_BTN_Y, "Standard", appearance.modelType == 0);
        drawModelBtn(MODEL_B_X, MODEL_BTN_Y, "Stocky",   appearance.modelType == 1);
    }

    private void drawModelBtn(float x, float y, String label, boolean selected) {
        // Background
        b().setColor(selected ? new Color(0.08f, 0.20f, 0.38f, 1f)
                              : new Color(0.10f, 0.10f, 0.16f, 1f));
        b().draw(whitePixel, x, y, MODEL_BTN_W, MODEL_BTN_H);
        // Border
        b().setColor(selected ? GOLD : new Color(0.30f, 0.30f, 0.44f, 1f));
        b().draw(whitePixel, x,                       y,                        MODEL_BTN_W, 1);
        b().draw(whitePixel, x,                       y + MODEL_BTN_H - 1,      MODEL_BTN_W, 1);
        b().draw(whitePixel, x,                       y,                        1, MODEL_BTN_H);
        b().draw(whitePixel, x + MODEL_BTN_W - 1,     y,                        1, MODEL_BTN_H);
        // Label
        game.font.getData().setScale(0.46f);
        game.font.setColor(selected ? Color.WHITE : new Color(0.60f, 0.60f, 0.70f, 1f));
        layout.setText(game.font, label);
        game.font.draw(game.batch, label,
                x + (MODEL_BTN_W - layout.width)  / 2f,
                y + (MODEL_BTN_H + layout.height) / 2f);
        game.font.getData().setScale(1f);
        game.font.setColor(Color.WHITE);
        b().setColor(Color.WHITE);
    }

    private void drawUsernameField() {
        // Label
        game.font.getData().setScale(0.46f);
        game.font.setColor(new Color(0.70f, 0.70f, 0.80f, 1f));
        layout.setText(game.font, "USERNAME");
        game.font.draw(game.batch, "USERNAME",
                CX - layout.width / 2f, UN_LBL_Y);

        // Box background
        b().setColor(usernameActive ? new Color(0.08f, 0.10f, 0.18f, 1f)
                                    : new Color(0.06f, 0.06f, 0.10f, 1f));
        b().draw(whitePixel, UN_BOX_X, UN_BOX_Y, UN_BOX_W, UN_BOX_H);
        // Box border
        b().setColor(usernameActive ? GOLD : new Color(0.28f, 0.28f, 0.40f, 1f));
        b().draw(whitePixel, UN_BOX_X,                  UN_BOX_Y,                  UN_BOX_W, 1);
        b().draw(whitePixel, UN_BOX_X,                  UN_BOX_Y + UN_BOX_H - 1,  UN_BOX_W, 1);
        b().draw(whitePixel, UN_BOX_X,                  UN_BOX_Y,                  1, UN_BOX_H);
        b().draw(whitePixel, UN_BOX_X + UN_BOX_W - 1,  UN_BOX_Y,                  1, UN_BOX_H);

        // Text + cursor
        game.font.getData().setScale(0.55f);
        game.font.setColor(Color.WHITE);
        String display = appearance.username;
        boolean showCursor = usernameActive && (int)(cursorBlink * 2) % 2 == 0;
        if (showCursor) display += "|";
        layout.setText(game.font, display);
        game.font.draw(game.batch, display,
                UN_BOX_X + 10f,
                UN_BOX_Y + (UN_BOX_H + layout.height) / 2f);

        game.font.getData().setScale(1f);
        game.font.setColor(Color.WHITE);
        b().setColor(Color.WHITE);
    }

    private void drawSwatchRow(String label, float lblY, float rowY, float sz, float gap,
                               int count, Color[] palette, int selectedIdx, float rowX) {
        // Section label
        game.font.getData().setScale(0.44f);
        game.font.setColor(new Color(0.70f, 0.70f, 0.80f, 1f));
        layout.setText(game.font, label);
        game.font.draw(game.batch, label, CX - layout.width / 2f, lblY);
        game.font.getData().setScale(1f);
        game.font.setColor(Color.WHITE);

        for (int i = 0; i < count; i++) {
            float sx = rowX + i * (sz + gap);
            boolean sel = (i == selectedIdx);

            // Selection outline
            if (sel) {
                b().setColor(Color.WHITE);
                b().draw(whitePixel, sx - 3, rowY - 3, sz + 6, sz + 6);
            } else {
                b().setColor(0.25f, 0.25f, 0.30f, 1f);
                b().draw(whitePixel, sx - 1, rowY - 1, sz + 2, sz + 2);
            }

            // Swatch colour
            b().setColor(palette[i]);
            b().draw(whitePixel, sx, rowY, sz, sz);
        }
        b().setColor(Color.WHITE);
    }

    private void drawContinueButton() {
        // Glow / border
        if (contHovered) {
            b().setColor(GOLD.r, GOLD.g, GOLD.b, 0.70f);
            b().draw(whitePixel, CONT_X - 2, CONT_Y - 2, CONT_W + 4, CONT_H + 4);
        } else {
            b().setColor(GOLD.r, GOLD.g, GOLD.b, 0.30f);
            b().draw(whitePixel, CONT_X - 1, CONT_Y - 1, CONT_W + 2, CONT_H + 2);
        }
        // Body
        b().setColor(contHovered ? new Color(0.05f, 0.16f, 0.28f, 1f)
                                 : new Color(0.08f, 0.08f, 0.15f, 1f));
        b().draw(whitePixel, CONT_X, CONT_Y, CONT_W, CONT_H);

        // Label
        game.font.getData().setScale(0.78f);
        game.font.setColor(contHovered ? Color.WHITE : new Color(0.80f, 0.80f, 0.88f, 1f));
        layout.setText(game.font, "CONTINUE");
        game.font.draw(game.batch, "CONTINUE",
                CONT_X + (CONT_W - layout.width)  / 2f,
                CONT_Y + (CONT_H + layout.height) / 2f);
        game.font.getData().setScale(1f);
        game.font.setColor(Color.WHITE);
        b().setColor(Color.WHITE);
    }

    /** Shorthand to avoid repeating game.batch everywhere. */
    private SpriteBatch b() { return game.batch; }
}
