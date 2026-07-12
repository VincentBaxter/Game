package com.mygame.tactics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

public class SellScreen implements Screen {

    private static final float W = 1280f, H = 720f;
    private static final int   SELL_PRICE = 10;

    // Panel
    private static final float PANEL_X = 80f,  PANEL_Y = 40f;
    private static final float PANEL_W = 1120f, PANEL_H = 640f;

    // Tyler portrait
    private static final float PORT_SZ = 160f;
    private static final float PORT_X  = PANEL_X + 24f;
    private static final float PORT_Y  = PANEL_Y + PANEL_H - PORT_SZ - 20f;

    // Dialogue bubble
    private static final float DLG_X = PORT_X + PORT_SZ + 16f;
    private static final float DLG_Y = PORT_Y;
    private static final float DLG_W = PANEL_W - PORT_SZ - 64f;
    private static final float DLG_H = 80f;

    // Bag grid  (2 rows × 8 cols)
    private static final float SLOT_SZ     = 80f;
    private static final float SLOT_GAP    = 8f;
    private static final float GRID_W      = 8 * SLOT_SZ + 7 * SLOT_GAP;
    private static final float GRID_X      = PANEL_X + (PANEL_W - GRID_W) / 2f;
    private static final float GRID_ROW2_Y = PANEL_Y + 20f;
    private static final float GRID_ROW1_Y = GRID_ROW2_Y + SLOT_SZ + SLOT_GAP;

    // Leave button
    private static final float BTN_W = 160f, BTN_H = 44f;
    private static final float BTN_X = PANEL_X + PANEL_W - BTN_W - 16f;
    private static final float BTN_Y = PANEL_Y + PANEL_H - BTN_H - 16f;

    // Colors
    private static final Color C_PANEL    = new Color(0.08f, 0.08f, 0.13f, 0.97f);
    private static final Color C_SLOT     = new Color(0.14f, 0.14f, 0.22f, 1f);
    private static final Color C_SLOT_HOV = new Color(0.28f, 0.22f, 0.10f, 1f);
    private static final Color C_OUTLINE  = new Color(0.30f, 0.30f, 0.50f, 1f);
    private static final Color C_GOLD     = new Color(1.00f, 0.84f, 0.00f, 1f);
    private static final Color C_BTN      = new Color(0.20f, 0.45f, 0.20f, 1f);
    private static final Color C_BTN_HOV  = new Color(0.28f, 0.60f, 0.28f, 1f);
    private static final Color C_DLG_BG   = new Color(0.12f, 0.12f, 0.18f, 1f);

    private final Main             game;
    private final Screen           returnScreen;
    private final PlayerInventory  inv;
    private final PlayerFlags      flags;
    private final Texture          bgTex;
    private final Texture          whitePx;
    private final Texture          tylerPortrait;
    private final OrthographicCamera camera;
    private final FitViewport        viewport;
    private final GlyphLayout        layout = new GlyphLayout();

    private int   hoveredSlot = -1;   // -1 = none, 0-15 = bag slots
    private String feedback   = null;
    private float  feedbackTimer = 0f;

    public SellScreen(Main game, Screen returnScreen, PlayerInventory inv,
                      PlayerFlags flags, Texture bgTex) {
        this.game         = game;
        this.returnScreen = returnScreen;
        this.inv          = inv;
        this.flags        = flags;
        this.bgTex        = bgTex;

        camera   = new OrthographicCamera();
        viewport = new FitViewport(W, H, camera);
        camera.setToOrtho(false, W, H);

        com.badlogic.gdx.graphics.Pixmap px = new com.badlogic.gdx.graphics.Pixmap(1, 1,
                com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        px.setColor(Color.WHITE); px.fill();
        whitePx = new Texture(px);
        px.dispose();

        Texture t = null;
        try { t = new Texture(Gdx.files.internal("tyler.png")); } catch (Exception ignored) {}
        tylerPortrait = t;
    }

    @Override
    public void render(float delta) {
        if (feedbackTimer > 0f) feedbackTimer -= delta;

        // Mouse position in virtual coords
        Vector3 mouse = new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouse, viewport.getScreenX(), viewport.getScreenY(),
                viewport.getScreenWidth(), viewport.getScreenHeight());
        float mx = mouse.x, my = mouse.y;

        hoveredSlot = slotAt(mx, my);

        // Input
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            close(); return;
        }
        if (Gdx.input.justTouched()) {
            if (hit(mx, my, BTN_X, BTN_Y, BTN_W, BTN_H)) { close(); return; }
            if (hoveredSlot >= 0 && inv.bag[hoveredSlot] != null) {
                sellItem(hoveredSlot);
            }
        }

        // Draw
        ScreenUtils.clear(0f, 0f, 0f, 1f);
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        // World background
        if (bgTex != null) {
            game.batch.setColor(0.4f, 0.4f, 0.4f, 1f);
            game.batch.draw(bgTex, 0, 0, W, H);
            game.batch.setColor(Color.WHITE);
        }

        // Panel
        fillRect(C_PANEL, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);
        outlineRect(C_OUTLINE, PANEL_X, PANEL_Y, PANEL_W, PANEL_H);

        // Tyler portrait
        if (tylerPortrait != null) {
            game.batch.setColor(Color.WHITE);
            game.batch.draw(tylerPortrait, PORT_X, PORT_Y, PORT_SZ, PORT_SZ);
        } else {
            fillRect(new Color(0.3f, 0.3f, 0.4f, 1f), PORT_X, PORT_Y, PORT_SZ, PORT_SZ);
        }
        outlineRect(C_OUTLINE, PORT_X, PORT_Y, PORT_SZ, PORT_SZ);

        // Dialogue bubble
        fillRect(C_DLG_BG, DLG_X, DLG_Y, DLG_W, DLG_H);
        outlineRect(C_OUTLINE, DLG_X, DLG_Y, DLG_W, DLG_H);
        game.font.getData().setScale(0.65f);
        game.font.setColor(Color.WHITE);
        game.font.draw(game.batch, "Tyler: Got anything to sell?",
                DLG_X + 12f, DLG_Y + DLG_H - 14f);

        // Gold
        game.font.getData().setScale(0.60f);
        game.font.setColor(C_GOLD);
        game.font.draw(game.batch, "GOLD: " + inv.gold,
                DLG_X + DLG_W - 200f, DLG_Y + DLG_H - 14f);

        // Feedback
        if (feedback != null && feedbackTimer > 0f) {
            game.font.getData().setScale(0.55f);
            game.font.setColor(C_GOLD);
            layout.setText(game.font, feedback);
            game.font.draw(game.batch, feedback,
                    PANEL_X + (PANEL_W - layout.width) / 2f,
                    DLG_Y + DLG_H * 0.38f);
        }

        // Section label
        game.font.getData().setScale(0.50f);
        game.font.setColor(new Color(0.65f, 0.65f, 0.85f, 1f));
        game.font.draw(game.batch, "YOUR ITEMS  (click to sell for " + SELL_PRICE + "g each)",
                GRID_X, GRID_ROW1_Y + SLOT_SZ + 22f);

        // Bag slots
        for (int i = 0; i < 16; i++) {
            float sx = slotX(i), sy = slotY(i);
            boolean hov   = (i == hoveredSlot && inv.bag[i] != null);
            fillRect(hov ? C_SLOT_HOV : C_SLOT, sx, sy, SLOT_SZ, SLOT_SZ);
            outlineRect(C_OUTLINE, sx, sy, SLOT_SZ, SLOT_SZ);

            Item item = inv.bag[i];
            if (item != null) {
                Texture icon = loadIcon(item.iconName);
                if (icon != null) {
                    float pad = 8f;
                    game.batch.setColor(Color.WHITE);
                    game.batch.draw(icon, sx + pad, sy + pad, SLOT_SZ - pad*2f, SLOT_SZ - pad*2f);
                }
                // Name label under icon
                game.font.getData().setScale(0.30f);
                game.font.setColor(hov ? C_GOLD : Color.LIGHT_GRAY);
                layout.setText(game.font, item.name);
                game.font.draw(game.batch, item.name,
                        sx + (SLOT_SZ - layout.width) / 2f, sy + 14f);
            }
        }

        // Hovered item tooltip
        if (hoveredSlot >= 0 && inv.bag[hoveredSlot] != null) {
            Item it = inv.bag[hoveredSlot];
            game.font.getData().setScale(0.45f);
            game.font.setColor(C_GOLD);
            game.font.draw(game.batch, "Sell \"" + it.name + "\" for " + SELL_PRICE + "g?",
                    PANEL_X + 12f, PANEL_Y + PANEL_H - PORT_SZ - 36f);
        }

        // Leave button
        boolean btnHov = hit(mx, my, BTN_X, BTN_Y, BTN_W, BTN_H);
        fillRect(btnHov ? C_BTN_HOV : C_BTN, BTN_X, BTN_Y, BTN_W, BTN_H);
        outlineRect(C_OUTLINE, BTN_X, BTN_Y, BTN_W, BTN_H);
        game.font.getData().setScale(0.55f);
        game.font.setColor(Color.WHITE);
        layout.setText(game.font, "Leave");
        game.font.draw(game.batch, "Leave",
                BTN_X + (BTN_W - layout.width) / 2f,
                BTN_Y + BTN_H / 2f + layout.height / 2f);

        game.font.getData().setScale(1f);
        game.font.setColor(Color.WHITE);
        game.batch.end();
    }

    private void sellItem(int slot) {
        Item item = inv.bag[slot];
        if (item == null) return;
        inv.bag[slot] = null;
        inv.gold += SELL_PRICE;
        inv.save(flags);
        feedback     = "Sold " + item.name + " for " + SELL_PRICE + "g!";
        feedbackTimer = 2f;
        // Dispose icon if loaded
        Texture t = iconCache.remove(item.iconName);
        if (t != null) t.dispose();
    }

    private void close() {
        game.setScreen(returnScreen);
        dispose();
    }

    // ---- Slot layout helpers ----

    private float slotX(int i) { return GRID_X + (i % 8) * (SLOT_SZ + SLOT_GAP); }
    private float slotY(int i) { return i < 8 ? GRID_ROW1_Y : GRID_ROW2_Y; }

    private int slotAt(float mx, float my) {
        for (int i = 0; i < 16; i++) {
            if (hit(mx, my, slotX(i), slotY(i), SLOT_SZ, SLOT_SZ)) return i;
        }
        return -1;
    }

    // ---- Icon cache ----

    private final java.util.HashMap<String, Texture> iconCache = new java.util.HashMap<>();

    private Texture loadIcon(String name) {
        if (name == null) return null;
        if (iconCache.containsKey(name)) return iconCache.get(name);
        try {
            Texture t = new Texture(Gdx.files.internal(name + ".png"));
            iconCache.put(name, t);
            return t;
        } catch (Exception e) {
            iconCache.put(name, null);
            return null;
        }
    }

    // ---- Drawing helpers ----

    private void fillRect(Color c, float x, float y, float w, float h) {
        game.batch.setColor(c);
        game.batch.draw(whitePx, x, y, w, h);
    }

    private void outlineRect(Color c, float x, float y, float w, float h) {
        game.batch.setColor(c);
        game.batch.draw(whitePx, x,         y,         w,  1);
        game.batch.draw(whitePx, x,         y + h - 1, w,  1);
        game.batch.draw(whitePx, x,         y,         1,  h);
        game.batch.draw(whitePx, x + w - 1, y,         1,  h);
    }

    private boolean hit(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    // ---- Screen lifecycle ----

    @Override public void show()   {}
    @Override public void resize(int w, int h) { viewport.update(w, h); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}

    @Override
    public void dispose() {
        if (bgTex != null) bgTex.dispose();
        if (tylerPortrait != null) tylerPortrait.dispose();
        whitePx.dispose();
        for (Texture t : iconCache.values()) if (t != null) t.dispose();
    }
}
