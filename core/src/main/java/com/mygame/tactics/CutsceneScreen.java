package com.mygame.tactics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plays a CutsceneData — background image, dialogue boxes, and branching choices.
 *
 * Layout (Y = 0 at bottom):
 *   ┌────────────────────────────────────────────────┐ ← 720
 *   │            background image                   │
 *   │                                               │
 *   │                              ╱‾‾‾‾‾‾‾‾‾‾‾‾‾‾ │ ← LEDGE_TOP (240)
 *   │                            ╱  [portrait peeks]│
 *   ├──────────────────────────╱────────────────────┤ ← BOX_H (170)
 *   │ text text text text                    [Next] │
 *   └────────────────────────────────────────────────┘ ← 0
 *                          ↑                   ↑
 *                    LEDGE_RAMP_X          LEDGE_FLAT_X
 *
 * Portrait is drawn before the ledge so the ledge covers its lower portion,
 * making the character appear to peek/bob up from behind it.
 *
 * Controls: click anywhere, Space, or Enter advances dialogue. Choices require a click.
 */
public class CutsceneScreen implements Screen {

    // ---- Screen ----
    private static final float W = 1280f;
    private static final float H = 720f;

    // ---- Dialogue box ----
    private static final float BOX_H   = 170f;  // bottom band height
    private static final float BOX_PAD = 28f;

    // ---- Ledge (right-side character platform) ----
    private static final float LEDGE_TOP    = 242f;  // top of the flat ledge surface
    private static final float LEDGE_FLAT_X = 1020f; // where the flat portion starts
    private static final float LEDGE_RAMP_X = 900f;  // where the diagonal ramp begins (at y=BOX_H)

    // ---- Portrait ----
    private static final float PORTRAIT_W    = 220f;  // draw width
    private static final float PORTRAIT_H    = 250f;  // draw height
    private static final float PORTRAIT_PEEK = 160f;  // px visible above the ledge
    // centre X of portrait sits in the middle of the flat ledge area
    private static final float PORTRAIT_CX   = (LEDGE_FLAT_X + W) / 2f; // ≈ 1150

    // ---- Bob ----
    private static final double BOB_SPEED     = Math.PI * 1.1; // radians/s  (period ≈ 1.8 s)
    private static final float  BOB_AMPLITUDE = 5f;            // px up/down

    // ---- Next button ----
    private static final float NEXT_W = 180f;
    private static final float NEXT_H = 42f;
    private static final float NEXT_X = W - NEXT_W - 16f;  // 1084
    private static final float NEXT_Y = 14f;

    // ---- Choice overlay ----
    private static final float CHOICE_BTN_W = 640f;
    private static final float CHOICE_BTN_H = 46f;
    private static final float CHOICE_GAP   = 12f;

    // ---- Font scales ----
    private static final float SPEAKER_SCALE = 0.58f;
    private static final float TEXT_SCALE    = 0.72f;

    // =========================================================
    // State
    // =========================================================

    private final Main        game;
    private final PlayerFlags flags;
    private final Runnable    onComplete;
    private final GlyphLayout layout = new GlyphLayout();

    private final Array<CutsceneData.Beat> queue = new Array<>();
    private CutsceneData.Beat activeBeat = null;

    private final OrthographicCamera camera;
    private final FitViewport        viewport;
    private final Texture            whitePixel;

    private Texture background = null;

    /** Lazily loaded portrait textures keyed by lower-cased speaker name. */
    private final Map<String, Texture> portraitCache = new LinkedHashMap<>();

    private float   stateTime   = 0f;
    private boolean prevTouched = false;
    private boolean prevSpace   = false;

    // =========================================================
    // Constructor
    // =========================================================

    public CutsceneScreen(Main game, CutsceneData data, PlayerFlags flags, Runnable onComplete) {
        this.game       = game;
        this.flags      = flags;
        this.onComplete = onComplete;

        camera   = new OrthographicCamera();
        viewport = new FitViewport(W, H, camera);
        camera.position.set(W / 2f, H / 2f, 0);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE); pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();

        if (data.backgroundPath != null) background = loadTexture(data.backgroundPath);
        queue.addAll(data.beats);
        advance();
    }

    // =========================================================
    // Screen lifecycle
    // =========================================================

    @Override
    public void render(float delta) {
        stateTime += delta;

        ScreenUtils.clear(0f, 0f, 0f, 1f);
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        drawBackground();

        if (activeBeat instanceof CutsceneData.DialogueBeat) {
            drawDialogueBox((CutsceneData.DialogueBeat) activeBeat);
            handleAdvanceInput();
        } else if (activeBeat instanceof CutsceneData.ChoiceBeat) {
            drawChoiceOverlay((CutsceneData.ChoiceBeat) activeBeat);
        }

        game.batch.end();
    }

    @Override public void show()   {}
    @Override public void resize(int w, int h) { viewport.update(w, h); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void dispose() {
        whitePixel.dispose();
        if (background != null) background.dispose();
        for (Texture t : portraitCache.values()) if (t != null) t.dispose();
    }

    // =========================================================
    // Beat queue
    // =========================================================

    private void advance() {
        while (!queue.isEmpty()) {
            CutsceneData.Beat next = queue.removeIndex(0);

            if (next instanceof CutsceneData.FlagBeat) {
                CutsceneData.FlagBeat fb = (CutsceneData.FlagBeat) next;
                if (fb.increment) flags.increment(fb.key);
                else              flags.set(fb.key, fb.value);
                continue;
            } else if (next instanceof CutsceneData.BackgroundChangeBeat) {
                if (background != null) background.dispose();
                background = loadTexture(((CutsceneData.BackgroundChangeBeat) next).path);
                continue;
            }

            activeBeat = next;
            return;
        }
        activeBeat = null;
        onComplete.run();
    }

    private void pickOption(CutsceneData.Option option) {
        for (int i = option.beats.size - 1; i >= 0; i--) queue.insert(0, option.beats.get(i));
        advance();
    }

    // =========================================================
    // Input
    // =========================================================

    private void handleAdvanceInput() {
        boolean touched = Gdx.input.isTouched();
        boolean space   = Gdx.input.isKeyPressed(Input.Keys.SPACE)
                       || Gdx.input.isKeyPressed(Input.Keys.ENTER);

        boolean justClicked = touched && !prevTouched;
        boolean justKey     = space   && !prevSpace;

        prevTouched = touched;
        prevSpace   = space;

        if (justClicked || justKey) advance();
    }

    // =========================================================
    // Drawing — background
    // =========================================================

    private void drawBackground() {
        if (background != null) {
            game.batch.setColor(Color.WHITE);
            game.batch.draw(background, 0, 0, W, H);
        } else {
            game.batch.setColor(0.05f, 0.05f, 0.10f, 1f);
            game.batch.draw(whitePixel, 0, 0, W, H);
        }
    }

    // =========================================================
    // Drawing — dialogue box with ledge + portrait
    // =========================================================

    private void drawDialogueBox(CutsceneData.DialogueBeat beat) {
        SpriteBatch b           = game.batch;
        boolean     hasPortrait = beat.speaker != null;
        float       bob         = (float) Math.sin(stateTime * BOB_SPEED) * BOB_AMPLITUDE;

        // ---- 1. Portrait (drawn before ledge so ledge covers its bottom) ----
        if (hasPortrait) {
            float px = PORTRAIT_CX - PORTRAIT_W / 2f;
            float py = LEDGE_TOP - (PORTRAIT_H - PORTRAIT_PEEK) + bob;
            Texture portrait = getPortrait(beat.speaker);
            if (portrait != null) {
                b.setColor(Color.WHITE);
                b.draw(portrait, px, py, PORTRAIT_W, PORTRAIT_H);
            } else {
                drawFallbackFigure(b, px, py);
            }
        }

        // ---- 2. Dialogue box (semi-transparent dark band, full width) ----
        b.setColor(0.10f, 0.10f, 0.15f, 0.93f);
        b.draw(whitePixel, 0, 0, W, BOX_H);

        // Top edge of dialogue box
        b.setColor(0.28f, 0.28f, 0.42f, 1f);
        b.draw(whitePixel, 0, BOX_H, W, 1f);

        // ---- 3. Ledge (covers portrait's lower portion) ----
        if (hasPortrait) {
            float rampH = LEDGE_TOP - BOX_H;  // height of the ramp/flat section

            // Ledge body color
            b.setColor(0.30f, 0.30f, 0.34f, 1f);

            // Flat rectangle (LEDGE_FLAT_X → W, BOX_H → LEDGE_TOP)
            b.draw(whitePixel, LEDGE_FLAT_X, BOX_H, W - LEDGE_FLAT_X, rampH);

            // Ramp: thin horizontal strips forming the diagonal
            for (float dy = 0; dy < rampH; dy += 2f) {
                float t  = dy / rampH;
                float lx = LEDGE_RAMP_X + (LEDGE_FLAT_X - LEDGE_RAMP_X) * t;
                b.draw(whitePixel, lx, BOX_H + dy, LEDGE_FLAT_X - lx, 2f);
            }

            // Top-edge highlight — flat portion
            b.setColor(0.55f, 0.55f, 0.62f, 1f);
            b.draw(whitePixel, LEDGE_FLAT_X, LEDGE_TOP, W - LEDGE_FLAT_X, 2f);

            // Top-edge highlight — ramp (dots along the diagonal)
            for (float dy = 0; dy <= rampH; dy += 1.5f) {
                float t  = dy / rampH;
                float ex = LEDGE_RAMP_X + (LEDGE_FLAT_X - LEDGE_RAMP_X) * t;
                b.draw(whitePixel, ex, BOX_H + dy, 2f, 1.5f);
            }

            // ---- 4. Speaker name centred inside the flat ledge box ----
            game.font.getData().setScale(SPEAKER_SCALE);
            layout.setText(game.font, beat.speaker);
            float nameX = LEDGE_FLAT_X + (W - LEDGE_FLAT_X - layout.width) / 2f;
            float nameY = (BOX_H + LEDGE_TOP) / 2f + layout.height / 2f;
            // drop shadow
            game.font.setColor(0f, 0f, 0f, 0.70f);
            game.font.draw(b, beat.speaker, nameX + 1f, nameY - 1f);
            // white label
            game.font.setColor(0.95f, 0.95f, 1.00f, 1f);
            game.font.draw(b, beat.speaker, nameX, nameY);
        }

        // ---- 5. Dialogue text ----
        // Constrain text width to the left of the ramp when a portrait is visible
        float textMaxW = hasPortrait ? LEDGE_RAMP_X - BOX_PAD * 2f
                                     : W - BOX_PAD * 2f;
        float textTopY = BOX_H - BOX_PAD;

        game.font.getData().setScale(TEXT_SCALE);
        game.font.setColor(Color.WHITE);
        game.font.draw(b, beat.text, BOX_PAD, textTopY, textMaxW, -1, true);

        // ---- 6. Next button (lime green, bottom-right inside the box) ----
        Vector3 mp  = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        boolean hov = new Rectangle(NEXT_X, NEXT_Y, NEXT_W, NEXT_H).contains(mp.x, mp.y);

        b.setColor(hov ? new Color(0.62f, 0.87f, 0.10f, 1f)
                       : new Color(0.42f, 0.62f, 0.07f, 1f));
        b.draw(whitePixel, NEXT_X, NEXT_Y, NEXT_W, NEXT_H);

        b.setColor(hov ? new Color(0.82f, 1.00f, 0.32f, 1f)
                       : new Color(0.58f, 0.78f, 0.18f, 1f));
        b.draw(whitePixel, NEXT_X,            NEXT_Y,            NEXT_W, 1);
        b.draw(whitePixel, NEXT_X,            NEXT_Y + NEXT_H - 1, NEXT_W, 1);
        b.draw(whitePixel, NEXT_X,            NEXT_Y,            1, NEXT_H);
        b.draw(whitePixel, NEXT_X + NEXT_W - 1, NEXT_Y,          1, NEXT_H);

        game.font.getData().setScale(0.50f);
        game.font.setColor(hov ? Color.WHITE : new Color(0.88f, 0.97f, 0.72f, 1f));
        layout.setText(game.font, "Next");
        game.font.draw(b, "Next",
                NEXT_X + (NEXT_W - layout.width)  / 2f,
                NEXT_Y + (NEXT_H + layout.height) / 2f);

        game.font.getData().setScale(1f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    /**
     * Fallback humanoid figure drawn when no portrait PNG is found for the speaker.
     * x/y are the bottom-left of the draw area (PORTRAIT_W × PORTRAIT_H).
     */
    private void drawFallbackFigure(SpriteBatch b, float x, float y) {
        float w = PORTRAIT_W, h = PORTRAIT_H;
        // Legs
        b.setColor(0.26f, 0.26f, 0.34f, 1f);
        b.draw(whitePixel, x + w * 0.22f, y + h * 0.06f, w * 0.20f, h * 0.24f);
        b.draw(whitePixel, x + w * 0.58f, y + h * 0.06f, w * 0.20f, h * 0.24f);
        // Body
        float bw = w * 0.46f, bh = h * 0.30f;
        float bx = x + (w - bw) / 2f, by2 = y + h * 0.28f;
        b.setColor(0.40f, 0.40f, 0.52f, 1f);
        b.draw(whitePixel, bx, by2, bw, bh);
        // Head
        float hs = w * 0.34f;
        b.setColor(0.80f, 0.66f, 0.53f, 1f);
        b.draw(whitePixel, x + (w - hs) / 2f, by2 + bh + h * 0.02f, hs, hs);
        b.setColor(Color.WHITE);
    }

    // =========================================================
    // Drawing — choice overlay
    // =========================================================

    private void drawChoiceOverlay(CutsceneData.ChoiceBeat beat) {
        SpriteBatch b    = game.batch;
        Vector3     mp   = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        boolean     just = Gdx.input.justTouched();

        // Full-screen dim over the background
        b.setColor(0f, 0f, 0f, 0.62f);
        b.draw(whitePixel, 0, 0, W, H);

        // Prompt
        game.font.getData().setScale(SPEAKER_SCALE);
        game.font.setColor(1.0f, 0.84f, 0.20f, 1f);
        layout.setText(game.font, beat.prompt);
        float totalBtnH = beat.options.size * (CHOICE_BTN_H + CHOICE_GAP) - CHOICE_GAP;
        float promptY   = H / 2f + totalBtnH / 2f + 44f;
        game.font.draw(b, beat.prompt, W / 2f - layout.width / 2f, promptY);

        // Choice buttons
        float startY = H / 2f + totalBtnH / 2f;
        for (int i = 0; i < beat.options.size; i++) {
            CutsceneData.Option opt  = beat.options.get(i);
            float btnX = W / 2f - CHOICE_BTN_W / 2f;
            float btnY = startY - i * (CHOICE_BTN_H + CHOICE_GAP) - CHOICE_BTN_H;
            boolean hov = new Rectangle(btnX, btnY, CHOICE_BTN_W, CHOICE_BTN_H).contains(mp.x, mp.y);

            b.setColor(hov ? new Color(0.08f, 0.22f, 0.40f, 1f)
                           : new Color(0.06f, 0.06f, 0.14f, 0.96f));
            b.draw(whitePixel, btnX, btnY, CHOICE_BTN_W, CHOICE_BTN_H);

            Color bdr = hov ? new Color(0.30f, 0.60f, 1.0f, 1f)
                            : new Color(0.28f, 0.28f, 0.38f, 1f);
            b.setColor(bdr);
            b.draw(whitePixel, btnX,                     btnY,                     CHOICE_BTN_W, 1);
            b.draw(whitePixel, btnX,                     btnY + CHOICE_BTN_H - 1,  CHOICE_BTN_W, 1);
            b.draw(whitePixel, btnX,                     btnY,                     1, CHOICE_BTN_H);
            b.draw(whitePixel, btnX + CHOICE_BTN_W - 1,  btnY,                     1, CHOICE_BTN_H);

            game.font.getData().setScale(0.46f);
            game.font.setColor(hov ? Color.WHITE : new Color(0.82f, 0.82f, 0.90f, 1f));
            layout.setText(game.font, opt.label);
            game.font.draw(b, opt.label,
                    btnX + (CHOICE_BTN_W - layout.width)  / 2f,
                    btnY + (CHOICE_BTN_H + layout.height) / 2f);

            if (hov && just) {
                game.font.getData().setScale(1f);
                game.font.setColor(Color.WHITE);
                b.setColor(Color.WHITE);
                pickOption(opt);
                return;
            }
        }

        game.font.getData().setScale(1f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    // =========================================================
    // Helpers
    // =========================================================

    /**
     * Lazily loads and caches a character portrait.
     * Looks for <speaker_lower>.png in the standard asset directories.
     * Returns null (and caches the miss) if not found.
     */
    private Texture getPortrait(String speaker) {
        String key = speaker.toLowerCase();
        if (portraitCache.containsKey(key)) return portraitCache.get(key);
        String[] dirs = {".", "assets", "../assets", "../../assets"};
        for (String d : dirs) {
            try {
                com.badlogic.gdx.files.FileHandle fh = Gdx.files.local(d + "/" + key + ".png");
                if (fh.exists()) {
                    Texture t = new Texture(fh);
                    portraitCache.put(key, t);
                    return t;
                }
            } catch (Exception ignored) {}
        }
        portraitCache.put(key, null);
        return null;
    }

    private Texture loadTexture(String path) {
        String[] dirs = {".", "assets", "../assets", "../../assets"};
        for (String d : dirs) {
            try {
                com.badlogic.gdx.files.FileHandle fh = Gdx.files.local(d + "/" + path);
                if (fh.exists()) return new Texture(fh);
            } catch (Exception ignored) {}
        }
        Gdx.app.error("CutsceneScreen", "Texture not found: " + path);
        return null;
    }
}
