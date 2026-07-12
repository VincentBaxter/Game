package com.mygame.tactics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

public class InventoryScreen implements Screen {

    // ---- Viewport ----
    private static final float W = 1280f, H = 720f;

    // ---- Popup panel (world visible in the border strips around it) ----
    private static final float POPUP_X = 50f,  POPUP_Y = 25f;
    private static final float POPUP_W = 1180f, POPUP_H = 695f; // top of popup == screen top

    // ---- Top bar ----
    private static final float BAR_H = 36f;
    private static final float BAR_Y = H - BAR_H; // y=684

    // ---- Equipment area sits between bag (y=0-210) and bar (y=684-720) ----
    private static final float EQ_BOTTOM = 210f;

    // ---- Equipment slot size ----
    private static final float EQ_SZ = 88f;

    // ---- Left column (Helm / Body / Shoes) ----
    private static final float EQ_LEFT_X = 80f;
    private static final float HELM_Y    = 565f;
    private static final float BODY_Y    = 455f;
    private static final float SHOES_Y   = 345f;

    // ---- Right column (Weapon / Special) ----
    private static final float EQ_RIGHT_X  = 545f;
    private static final float WEAPON_Y    = 555f;
    private static final float SPECIAL_Y   = 435f;

    // ---- Portrait (drawn with a virtual tile size) ----
    // Horizontally centered between left col (right edge ~168) and right col (left edge 545)
    // Vertically centered in the equipment area (EQ_BOTTOM=210 to BAR_Y=684, center=447)
    private static final float PORT_TS = 240f;
    private static final float PORT_X  = 237f;
    private static final float PORT_Y  = 340f;

    // ---- Stats panel ----
    private static final float STAT_X    = 720f;
    private static final float STAT_TOP  = 655f;
    private static final float STAT_STEP = 30f;

    // ---- Abilities panel (right side, below stats) ----
    private static final float ABIL_X   = STAT_X;
    private static final float ABIL_W   = 148f;
    private static final float ABIL_H   = 54f;
    private static final float ABIL_GAP = 10f;
    private static final float ABIL_Y   = 222f;

    // ---- Bag area ----
    private static final float SLOT_SZ     = 72f;
    private static final float SLOT_GAP    = 6f;
    private static final float BAG_TOTAL_W = 8 * SLOT_SZ + 7 * SLOT_GAP; // 618
    private static final float BAG_START_X = (W - BAG_TOTAL_W) / 2f;     // 331
    private static final float BAG_ROW1_Y  = 113f; // upper bag row (higher on screen)
    private static final float BAG_ROW2_Y  = 25f;  // lower bag row

    // ---- Slot indices ----
    private static final int SLOT_HELM    = 0;
    private static final int SLOT_BODY    = 1;
    private static final int SLOT_SHOES   = 2;
    private static final int SLOT_WEAPON  = 3;
    private static final int SLOT_SPECIAL = 4;
    private static final int SLOT_BAG_0   = 5; // bag slots 5–20

    // ---- Colors ----
    private static final Color C_BG_DARK  = new Color(0.06f, 0.06f, 0.10f, 1f);
    private static final Color C_BG_MID   = new Color(0.10f, 0.10f, 0.16f, 1f);
    private static final Color C_SLOT     = new Color(0.14f, 0.14f, 0.22f, 1f);
    private static final Color C_SLOT_HOV = new Color(0.22f, 0.22f, 0.36f, 1f);
    private static final Color C_SLOT_SEL = new Color(0.25f, 0.50f, 0.88f, 1f);
    private static final Color C_OUTLINE  = new Color(0.30f, 0.30f, 0.50f, 1f);
    private static final Color C_GOLD     = new Color(1.00f, 0.84f, 0.00f, 1f);
    private static final Color C_LABEL    = new Color(0.65f, 0.65f, 0.85f, 1f);

    // ---- State ----
    private final Main             game;
    private final Screen           returnScreen;
    private final PlayerAppearance appearance;
    private final PlayerInventory  inv;

    private final OrthographicCamera camera;
    private final FitViewport        viewport;
    private final Texture            white;
    private final Texture            soulTex;
    private final Texture            classIconTex;
    private final Texture            typeIconTex;
    private final Texture            bgTex;     // world snapshot (may be null)
    private final TextureRegion      bgRegion;  // Y-flipped region for bgTex
    private final java.util.Map<String, Texture> itemIconCache = new java.util.HashMap<>();
    private final GlyphLayout        layout = new GlyphLayout();

    private int   hoveredSlot  = -1;
    private int   selectedSlot = -1;
    private float mouseX, mouseY;

    // =========================================================

    public InventoryScreen(Main game, Screen returnScreen, PlayerAppearance appearance) {
        this(game, returnScreen, appearance, null);
    }

    public InventoryScreen(Main game, Screen returnScreen, PlayerAppearance appearance, Texture bgTex) {
        this.game         = game;
        this.returnScreen = returnScreen;
        this.appearance   = appearance != null ? appearance : new PlayerAppearance();
        this.inv          = Main.inventory;
        this.bgTex        = bgTex;

        if (bgTex != null) {
            bgRegion = new TextureRegion(bgTex);
            bgRegion.flip(false, true); // OpenGL framebuffer is Y-flipped
        } else {
            bgRegion = null;
        }

        camera   = new OrthographicCamera();
        viewport = new FitViewport(W, H, camera);
        camera.setToOrtho(false, W, H);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE); pm.fill();
        white = new Texture(pm);
        pm.dispose();
        Texture loadedSoul = null;
        try {
            FileHandle fh = Gdx.files.internal("item_soul.png");
            if (fh.exists()) loadedSoul = new Texture(fh);
        } catch (Exception ignored) {}
        soulTex      = loadedSoul;
        classIconTex = loadTex(Main.inventory.charClass.name().toLowerCase() + "_icon.png");
        typeIconTex  = loadTex(Main.inventory.charType.name().toLowerCase()  + "_icon.png");
    }

    private static Texture loadTex(String path) {
        try {
            FileHandle fh = Gdx.files.internal(path);
            if (fh.exists()) return new Texture(fh);
        } catch (Exception ignored) {}
        return null;
    }

    // ---- Lifecycle ----

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int ptr, int btn) {
                Vector3 v = viewport.unproject(new Vector3(sx, sy, 0));
                if (btn == 0) handleClick(v.x, v.y);
                return true;
            }
        });
    }

    @Override public void hide()   { Gdx.input.setInputProcessor(null); }
    @Override public void pause()  {}
    @Override public void resume() {}

    @Override
    public void resize(int w, int h) {
        viewport.update(w, h, true);
    }

    @Override
    public void dispose() {
        white.dispose();
        if (soulTex      != null) soulTex.dispose();
        if (classIconTex != null) classIconTex.dispose();
        if (typeIconTex  != null) typeIconTex.dispose();
        if (bgTex        != null) bgTex.dispose();
        for (Texture t : itemIconCache.values()) if (t != null) t.dispose();
        itemIconCache.clear();
    }

    // ---- Render ----

    @Override
    public void render(float delta) {
        Vector3 mv = viewport.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        mouseX = mv.x;
        mouseY = mv.y;
        hoveredSlot = slotAt(mouseX, mouseY);

        if (Gdx.input.isKeyJustPressed(Input.Keys.I) || Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            inv.save(Main.flags);
            game.setScreen(returnScreen);
            return;
        }

        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        // World snapshot (dimmed) fills the full screen; popup panel sits on top
        if (bgRegion != null) {
            game.batch.setColor(0.40f, 0.40f, 0.40f, 1f);
            game.batch.draw(bgRegion, 0f, 0f, W, H);
            game.batch.setColor(Color.WHITE);
        } else {
            fill(0f, 0f, W, H, C_BG_DARK);
        }
        fill(0f, 0f, W, H, new Color(0f, 0f, 0f, 0.30f));
        // Popup drop-shadow
        fill(POPUP_X + 6f, POPUP_Y - 6f, POPUP_W, POPUP_H, new Color(0f, 0f, 0f, 0.55f));
        // Popup panel
        fill(POPUP_X, POPUP_Y, POPUP_W, POPUP_H, C_BG_DARK);
        outline(POPUP_X, POPUP_Y, POPUP_W, POPUP_H, new Color(0.35f, 0.35f, 0.55f, 0.8f));

        drawBagArea();
        drawEquipmentArea();
        drawPortrait();
        drawStatsPanel();
        drawAbilitiesSection();
        drawTopBar();
        drawTooltip();

        game.batch.end();
    }

    // =========================================================
    // TOP BAR
    // =========================================================

    private void drawTopBar() {
        fill(POPUP_X, BAR_Y, POPUP_W, BAR_H, new Color(0.04f, 0.04f, 0.08f, 1f));

        game.batch.setColor(Color.WHITE);
        game.font.draw(game.batch, appearance.username, POPUP_X + 16f, BAR_Y + 26f);

        float iconSz = 28f;
        float iconY  = BAR_Y + (BAR_H - iconSz) / 2f;
        float clsX   = 438f;
        float typX   = clsX + iconSz + 14f;

        if (classIconTex != null) {
            fill(clsX - 3f, iconY - 3f, iconSz + 6f, iconSz + 6f, colorForClass(inv.charClass));
            game.batch.setColor(Color.WHITE);
            game.batch.draw(classIconTex, clsX, iconY, iconSz, iconSz);
        } else {
            game.font.getData().setScale(0.78f);
            drawTag(inv.charClass.name(), colorForClass(inv.charClass), 430f, BAR_Y + 7f, 120f, 22f);
            game.font.getData().setScale(1f);
        }

        if (typeIconTex != null) {
            fill(typX - 3f, iconY - 3f, iconSz + 6f, iconSz + 6f, colorForType(inv.charType));
            game.batch.setColor(Color.WHITE);
            game.batch.draw(typeIconTex, typX, iconY, iconSz, iconSz);
        } else {
            game.font.getData().setScale(0.78f);
            drawTag(inv.charType.name(), colorForType(inv.charType), 560f, BAR_Y + 7f, 120f, 22f);
            game.font.getData().setScale(1f);
        }

        game.batch.setColor(C_GOLD);
        game.font.draw(game.batch, "GOLD: " + inv.gold, POPUP_X + POPUP_W - 230f, BAR_Y + 26f);
        game.batch.setColor(Color.WHITE);
    }

    // =========================================================
    // EQUIPMENT AREA
    // =========================================================

    private void drawEquipmentArea() {
        fill(POPUP_X, EQ_BOTTOM, POPUP_W, BAR_Y - EQ_BOTTOM, C_BG_MID);

        drawEquipSlot(SLOT_HELM,    EQ_LEFT_X,  HELM_Y,    "HELMET",  inv.helmet);
        drawEquipSlot(SLOT_BODY,    EQ_LEFT_X,  BODY_Y,    "BODY",    inv.body);
        drawEquipSlot(SLOT_SHOES,   EQ_LEFT_X,  SHOES_Y,   "SHOES",   inv.shoes);
        drawEquipSlot(SLOT_WEAPON,  EQ_RIGHT_X, WEAPON_Y,  "WEAPON",  inv.weapon);
        drawEquipSlot(SLOT_SPECIAL, EQ_RIGHT_X, SPECIAL_Y, "SPECIAL", inv.special);
    }

    private void drawEquipSlot(int idx, float x, float y, String label, Item item) {
        boolean sel = selectedSlot == idx;
        boolean hov = hoveredSlot  == idx;
        fill(x, y, EQ_SZ, EQ_SZ, sel ? C_SLOT_SEL : hov ? C_SLOT_HOV : C_SLOT);
        outline(x, y, EQ_SZ, EQ_SZ, C_OUTLINE);

        game.font.getData().setScale(0.75f);
        layout.setText(game.font, label);
        game.batch.setColor(C_LABEL);
        game.font.draw(game.batch, label, x + (EQ_SZ - layout.width) / 2f, y + EQ_SZ + 16f);
        game.font.getData().setScale(1f);
        game.batch.setColor(Color.WHITE);

        if (item != null) drawItemInSlot(item, x, y, EQ_SZ);
    }

    private void drawItemInSlot(Item item, float x, float y, float sz) {
        Texture icon = iconFor(item);
        if (icon != null) {
            float pad = sz * 0.10f;
            game.batch.setColor(Color.WHITE);
            game.batch.draw(icon, x + pad, y + pad, sz - pad * 2f, sz - pad * 2f);
        } else {
            String s = item.name.length() > 8 ? item.name.substring(0, 7) + "." : item.name;
            layout.setText(game.font, s);
            game.batch.setColor(Color.WHITE);
            game.font.draw(game.batch, s,
                    x + (sz - layout.width) / 2f,
                    y + sz / 2f + layout.height / 2f);
        }
    }

    private Texture iconFor(Item item) {
        if (item.iconName == null || item.iconName.isEmpty()) return null;
        if (itemIconCache.containsKey(item.iconName)) return itemIconCache.get(item.iconName);
        Texture t = loadTex(item.iconName + ".png");
        itemIconCache.put(item.iconName, t); // null is cached too (avoids repeated failed lookups)
        return t;
    }

    // =========================================================
    // PORTRAIT
    // =========================================================

    private void drawPortrait() {
        float ts = PORT_TS, px = PORT_X, py = PORT_Y;
        Color skin  = appearance.getSkinColor();
        Color shirt = appearance.getShirtColor();
        Color pants = appearance.getPantsColor();

        if (appearance.modelType == 0) {
            // Standard — tall, slim
            game.batch.setColor(0f, 0f, 0f, 0.25f);
            game.batch.draw(white, px + ts*0.12f, py + ts*0.02f, ts*0.76f, ts*0.06f);

            game.batch.setColor(pants);
            game.batch.draw(white, px + ts*0.22f, py + ts*0.08f, ts*0.18f, ts*0.20f);
            game.batch.draw(white, px + ts*0.60f, py + ts*0.08f, ts*0.18f, ts*0.20f);

            float bw = ts*0.42f, bh = ts*0.28f;
            float bx = px + (ts - bw)/2f, by = py + ts*0.26f;
            game.batch.setColor(shirt);
            game.batch.draw(white, bx, by, bw, bh);

            float hs = ts*0.30f;
            float hx = px + (ts - hs)/2f, hy = by + bh + ts*0.02f;
            game.batch.setColor(skin);
            game.batch.draw(white, hx, hy, hs, hs);
            game.batch.setColor(1f, 1f, 1f, 0.18f);
            game.batch.draw(white, hx, hy + hs - ts*0.04f, hs, ts*0.04f);
        } else {
            // Stocky — wider, shorter
            game.batch.setColor(0f, 0f, 0f, 0.25f);
            game.batch.draw(white, px + ts*0.08f, py + ts*0.02f, ts*0.84f, ts*0.06f);

            game.batch.setColor(pants);
            game.batch.draw(white, px + ts*0.18f, py + ts*0.08f, ts*0.20f, ts*0.18f);
            game.batch.draw(white, px + ts*0.62f, py + ts*0.08f, ts*0.20f, ts*0.18f);

            float bw = ts*0.58f, bh = ts*0.24f;
            float bx = px + (ts - bw)/2f, by = py + ts*0.24f;
            game.batch.setColor(shirt);
            game.batch.draw(white, bx, by, bw, bh);
            game.batch.draw(white, bx - ts*0.05f,       by + bh - ts*0.06f, ts*0.10f, ts*0.08f);
            game.batch.draw(white, bx + bw - ts*0.05f,  by + bh - ts*0.06f, ts*0.10f, ts*0.08f);

            float hs = ts*0.35f;
            float hx = px + (ts - hs)/2f, hy = by + bh + ts*0.02f;
            game.batch.setColor(skin);
            game.batch.draw(white, hx, hy, hs, hs);
            game.batch.setColor(1f, 1f, 1f, 0.18f);
            game.batch.draw(white, hx, hy + hs - ts*0.04f, hs, ts*0.04f);
        }
        game.batch.setColor(Color.WHITE);
    }

    // =========================================================
    // STATS PANEL
    // =========================================================

    private void drawStatsPanel() {
        float x = STAT_X, y = STAT_TOP;
        game.font.getData().setScale(0.80f);
        stat("Health",        inv.currentHealth + " / " + inv.getMaxHealth(),       x, y); y -= STAT_STEP;
        stat("Attack",        "" + inv.getAtk(),                                    x, y); y -= STAT_STEP;
        stat("Magic",         "" + inv.getMag(),                                    x, y); y -= STAT_STEP;
        stat("Armor",         "" + inv.getArmor(),                                  x, y); y -= STAT_STEP;
        stat("Cloak",         "" + inv.getCloak(),                                  x, y); y -= STAT_STEP;
        stat("Speed",         "" + inv.getSpeed(),                                  x, y); y -= STAT_STEP;
        stat("Spd. Reduction",String.format("%.1f", inv.getSpeedReduction()),       x, y); y -= STAT_STEP;
        stat("Movement",      "" + inv.getMoveDist(),                               x, y); y -= STAT_STEP;
        stat("Range",         "" + inv.getRange(),                                  x, y); y -= STAT_STEP;
        stat("Critical",      String.format("%.0f%%", inv.getCritChance()  * 100),  x, y); y -= STAT_STEP;
        stat("Dodge",         String.format("%.0f%%", inv.getDodgeChance() * 100),  x, y);
        game.font.getData().setScale(1f);
    }

    private void stat(String label, String value, float x, float y) {
        game.batch.setColor(C_LABEL);
        game.font.draw(game.batch, label + ":", x, y);
        game.batch.setColor(Color.WHITE);
        game.font.draw(game.batch, value, x + 200f, y);
    }

    // =========================================================
    // ABILITIES
    // =========================================================

    private void drawAbilitiesSection() {
        String[] refs = { inv.abilitySlot1, inv.abilitySlot2, inv.abilitySlot3 };

        game.font.getData().setScale(0.78f);
        game.batch.setColor(C_LABEL);
        game.font.draw(game.batch, "ABILITIES", ABIL_X, ABIL_Y + ABIL_H + 22f);

        for (int i = 0; i < 3; i++) {
            float  sx   = ABIL_X + i * (ABIL_W + ABIL_GAP);
            String name = resolveAbilityName(refs[i]);
            boolean filled = name != null;

            fill(sx, ABIL_Y, ABIL_W, ABIL_H,
                    filled ? new Color(0.15f, 0.12f, 0.28f, 1f) : C_SLOT);
            outline(sx, ABIL_Y, ABIL_W, ABIL_H,
                    filled ? new Color(0.50f, 0.35f, 0.80f, 1f) : C_OUTLINE);

            game.font.getData().setScale(0.60f);
            game.batch.setColor(C_LABEL);
            game.font.draw(game.batch, String.valueOf(i + 1), sx + 5f, ABIL_Y + ABIL_H - 3f);

            String label = filled ? name : "Empty";
            game.font.getData().setScale(0.70f);
            layout.setText(game.font, label);
            float tx = sx + (ABIL_W - layout.width) / 2f;
            float ty = ABIL_Y + ABIL_H / 2f + layout.height / 2f;
            game.batch.setColor(filled ? Color.WHITE : new Color(0.40f, 0.40f, 0.50f, 1f));
            game.font.draw(game.batch, label, tx, ty);
        }

        game.font.getData().setScale(1f);
        game.batch.setColor(Color.WHITE);
    }

    private String resolveAbilityName(String ref) {
        if (ref == null || ref.isEmpty()) return null;
        String[] parts = ref.split(":");
        if (parts.length != 2) return ref;
        try {
            Character chr = CharacterRoster.build(parts[0]);
            if (chr == null) return parts[0];
            Ability ab = chr.getAbility(Integer.parseInt(parts[1]));
            return ab != null ? ab.getName() : parts[0];
        } catch (Exception ignored) { return parts[0]; }
    }

    // =========================================================
    // BAG
    // =========================================================

    private void drawBagArea() {
        fill(POPUP_X, POPUP_Y, POPUP_W, EQ_BOTTOM - POPUP_Y, C_BG_DARK);

        game.batch.setColor(C_LABEL);
        game.font.draw(game.batch, "INVENTORY", BAG_START_X, EQ_BOTTOM - 8f);
        game.batch.setColor(Color.WHITE);

        for (int i = 0; i < 16; i++) {
            int   row = i / 8, col = i % 8;
            float sx  = BAG_START_X + col * (SLOT_SZ + SLOT_GAP);
            float sy  = (row == 0) ? BAG_ROW1_Y : BAG_ROW2_Y;
            int   idx = SLOT_BAG_0 + i;
            boolean sel = selectedSlot == idx;
            boolean hov = hoveredSlot  == idx;

            fill(sx, sy, SLOT_SZ, SLOT_SZ, sel ? C_SLOT_SEL : hov ? C_SLOT_HOV : C_SLOT);
            outline(sx, sy, SLOT_SZ, SLOT_SZ, C_OUTLINE);

            if (inv.bag[i] != null) drawItemInSlot(inv.bag[i], sx, sy, SLOT_SZ);
        }
    }

    // =========================================================
    // TOOLTIP
    // =========================================================

    private void drawTooltip() {
        Item item = selectedSlot >= 0 ? getItemAt(selectedSlot) : null;
        if (item == null && hoveredSlot >= 0) item = getItemAt(hoveredSlot);
        if (item == null) return;

        float tx = EQ_RIGHT_X, ty = EQ_BOTTOM + 10f, tw = 210f, th = 120f;
        fill(tx, ty, tw, th, new Color(0.08f, 0.08f, 0.18f, 0.96f));
        outline(tx, ty, tw, th, C_OUTLINE);

        game.batch.setColor(C_GOLD);
        game.font.draw(game.batch, item.name, tx + 8f, ty + th - 7f);

        float ly = ty + th - 29f;
        if (!item.description.isEmpty()) {
            game.batch.setColor(C_LABEL);
            game.font.draw(game.batch, item.description, tx + 8f, ly);
            ly -= 22f;
        }

        // Stat mod lines
        if (item.atkMod   != 0) { modLine("ATK",  item.atkMod,   tx+8f, ly); ly -= 20f; }
        if (item.magMod   != 0) { modLine("MAG",  item.magMod,   tx+8f, ly); ly -= 20f; }
        if (item.armorMod != 0) { modLine("ARM",  item.armorMod, tx+8f, ly); ly -= 20f; }
        if (item.cloakMod != 0) { modLine("CLK",  item.cloakMod, tx+8f, ly); }
        game.batch.setColor(Color.WHITE);
    }

    private void modLine(String label, int val, float x, float y) {
        String s = label + ": " + (val > 0 ? "+" : "") + val;
        game.batch.setColor(val > 0 ? new Color(0.4f, 1f, 0.4f, 1f) : new Color(1f, 0.4f, 0.4f, 1f));
        game.font.draw(game.batch, s, x, y);
    }

    // =========================================================
    // CLICK HANDLING
    // =========================================================

    private void handleClick(float wx, float wy) {
        int clicked = slotAt(wx, wy);

        if (clicked < 0) {
            selectedSlot = -1;
            return;
        }

        if (selectedSlot == clicked) {
            selectedSlot = -1;
            return;
        }

        Item clickedItem = getItemAt(clicked);

        if (selectedSlot < 0) {
            if (clickedItem != null) selectedSlot = clicked;
            return;
        }

        // Move/equip the selected item
        Item moving = getItemAt(selectedSlot);
        if (moving == null) { selectedSlot = clicked; return; }

        if (isEquipSlot(clicked)) {
            if (itemFitsSlot(moving, clicked)) {
                setItemAt(clicked, moving);
                setItemAt(selectedSlot, clickedItem);
                selectedSlot = -1;
                inv.save(Main.flags);
            }
        } else {
            // Bag-to-bag or equip-to-bag swap
            setItemAt(clicked, moving);
            setItemAt(selectedSlot, clickedItem);
            selectedSlot = -1;
            inv.save(Main.flags);
        }
    }

    // =========================================================
    // SLOT HELPERS
    // =========================================================

    private int slotAt(float wx, float wy) {
        if (hit(wx, wy, EQ_LEFT_X,  HELM_Y,    EQ_SZ, EQ_SZ)) return SLOT_HELM;
        if (hit(wx, wy, EQ_LEFT_X,  BODY_Y,    EQ_SZ, EQ_SZ)) return SLOT_BODY;
        if (hit(wx, wy, EQ_LEFT_X,  SHOES_Y,   EQ_SZ, EQ_SZ)) return SLOT_SHOES;
        if (hit(wx, wy, EQ_RIGHT_X, WEAPON_Y,  EQ_SZ, EQ_SZ)) return SLOT_WEAPON;
        if (hit(wx, wy, EQ_RIGHT_X, SPECIAL_Y, EQ_SZ, EQ_SZ)) return SLOT_SPECIAL;
        for (int i = 0; i < 16; i++) {
            int   row = i / 8, col = i % 8;
            float sx  = BAG_START_X + col * (SLOT_SZ + SLOT_GAP);
            float sy  = (row == 0) ? BAG_ROW1_Y : BAG_ROW2_Y;
            if (hit(wx, wy, sx, sy, SLOT_SZ, SLOT_SZ)) return SLOT_BAG_0 + i;
        }
        return -1;
    }

    private boolean isEquipSlot(int idx) { return idx >= 0 && idx < SLOT_BAG_0; }

    private boolean itemFitsSlot(Item item, int slotIdx) {
        if (item.slot == null) return false;
        switch (slotIdx) {
            case SLOT_HELM:    return item.slot == Item.ItemSlot.HELMET;
            case SLOT_BODY:    return item.slot == Item.ItemSlot.BODY;
            case SLOT_SHOES:   return item.slot == Item.ItemSlot.SHOES;
            case SLOT_WEAPON:  return item.slot == Item.ItemSlot.WEAPON;
            case SLOT_SPECIAL: return item.slot == Item.ItemSlot.SPECIAL;
            default:           return false;
        }
    }

    private Item getItemAt(int idx) {
        switch (idx) {
            case SLOT_HELM:    return inv.helmet;
            case SLOT_BODY:    return inv.body;
            case SLOT_SHOES:   return inv.shoes;
            case SLOT_WEAPON:  return inv.weapon;
            case SLOT_SPECIAL: return inv.special;
            default:
                int b = idx - SLOT_BAG_0;
                return (b >= 0 && b < inv.bag.length) ? inv.bag[b] : null;
        }
    }

    private void setItemAt(int idx, Item item) {
        switch (idx) {
            case SLOT_HELM:    inv.helmet  = item; return;
            case SLOT_BODY:    inv.body    = item; return;
            case SLOT_SHOES:   inv.shoes   = item; return;
            case SLOT_WEAPON:  inv.weapon  = item; return;
            case SLOT_SPECIAL: inv.special = item; return;
            default:
                int b = idx - SLOT_BAG_0;
                if (b >= 0 && b < inv.bag.length) inv.bag[b] = item;
        }
    }

    // =========================================================
    // DRAW UTILITIES
    // =========================================================

    private void fill(float x, float y, float w, float h, Color c) {
        game.batch.setColor(c);
        game.batch.draw(white, x, y, w, h);
        game.batch.setColor(Color.WHITE);
    }

    private void outline(float x, float y, float w, float h, Color c) {
        float t = 1.5f;
        game.batch.setColor(c);
        game.batch.draw(white, x,       y,       w, t);
        game.batch.draw(white, x,       y+h-t,   w, t);
        game.batch.draw(white, x,       y,       t, h);
        game.batch.draw(white, x+w-t,   y,       t, h);
        game.batch.setColor(Color.WHITE);
    }

    private void drawTag(String text, Color bg, float x, float y, float w, float h) {
        fill(x, y, w, h, bg);
        layout.setText(game.font, text);
        game.batch.setColor(Color.WHITE);
        game.font.draw(game.batch, text, x + (w - layout.width) / 2f, y + h - 4f);
    }

    private static boolean hit(float px, float py, float rx, float ry, float rw, float rh) {
        return px >= rx && px < rx+rw && py >= ry && py < ry+rh;
    }

    // =========================================================
    // CLASS / TYPE COLORS
    // =========================================================

    private static Color colorForClass(Enums.CharClass c) {
        switch (c) {
            case FIGHTER:  return new Color(0.80f, 0.25f, 0.20f, 1f);
            case MAGE:     return new Color(0.30f, 0.20f, 0.82f, 1f);
            case TANK:     return new Color(0.40f, 0.40f, 0.42f, 1f);
            case SUPPORT:  return new Color(0.18f, 0.68f, 0.33f, 1f);
            case ASSASSIN: return new Color(0.16f, 0.16f, 0.22f, 1f);
            case SNIPER:   return new Color(0.60f, 0.50f, 0.14f, 1f);
            default:       return new Color(0.35f, 0.35f, 0.55f, 1f);
        }
    }

    private static Color colorForType(Enums.CharType t) {
        switch (t) {
            case FLORA:     return new Color(0.18f, 0.65f, 0.22f, 1f);
            case FAUNA:     return new Color(0.62f, 0.38f, 0.16f, 1f);
            case AQUA:      return new Color(0.14f, 0.48f, 0.84f, 1f);
            case WIND:      return new Color(0.60f, 0.78f, 0.60f, 1f);
            case FIRE:      return new Color(0.90f, 0.33f, 0.10f, 1f);
            case ANGELIC:   return new Color(0.88f, 0.88f, 0.60f, 1f);
            case NIGHTMARE: return new Color(0.40f, 0.10f, 0.55f, 1f);
            default:        return new Color(0.35f, 0.35f, 0.55f, 1f);
        }
    }
}
