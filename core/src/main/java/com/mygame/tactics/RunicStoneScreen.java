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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RunicStoneScreen implements Screen {

    private static final float W = 1280f, H = 720f;

    // ---- Vertical layout (values computed top-to-bottom) ----
    private static final float TITLE_H    = 28f;
    private static final float TYPE_H     = 40f;
    private static final float CLASS_H    = 62f;
    private static final float SOUL_H     = 56f;
    private static final float PORT_H     = 138f;
    private static final float SLOT_H     = 64f;   // ability slots bar at y=0

    private static final float TITLE_Y    = H - TITLE_H;               // 692
    private static final float TYPE_Y     = TITLE_Y - TYPE_H;           // 652
    private static final float CLASS_Y    = TYPE_Y  - CLASS_H;          // 590
    private static final float SOUL_Y     = CLASS_Y - SOUL_H;           // 534
    private static final float PORT_BOT   = SOUL_Y  - PORT_H;           // 396
    // picker overlay occupies SLOT_H..PORT_BOT (64..396 = 332px) when active

    // ---- Type row ----
    private static final Enums.CharType[]  TYPES   = Enums.CharType.values();
    private static final Enums.CharClass[] CLASSES = Enums.CharClass.values();

    // ---- Type icon row ----
    private static final float TYPE_ICON_SZ  = 30f;
    private static final float TYPE_ICON_GAP = 10f;

    // ---- Class icon row ----
    private static final float CLS_SZ  = 50f;
    private static final float CLS_GAP = 10f;

    // ---- Portrait scroller ----
    private static final float PORT_IMG_SZ  = 88f;
    private static final float PORT_IMG_GAP = 10f;
    private static final float PORT_MARGIN  = 52f; // left/right margin (space for arrows)

    // ---- Soul strip ----
    private static final float SOUL_BTN_W  = 155f;
    private static final float SOUL_BTN_H  = 26f;

    // ---- Ability slots bar ----
    private static final float SLOT_BTN_W = W / 3f;

    // ---- Ability picker ----
    private static final float PICK_CHAR_SZ  = 82f;
    private static final float PICK_CHAR_GAP = 10f;
    private static final float PICK_CHAR_H   = 112f;  // character row height in picker
    private static final float PICK_AB_H     = 42f;   // per ability row

    // ---- Colors ----
    private static final Color C_BG        = new Color(0.05f, 0.05f, 0.09f, 1f);
    private static final Color C_PANEL     = new Color(0.09f, 0.09f, 0.14f, 1f);
    private static final Color C_OUTLINE   = new Color(0.30f, 0.30f, 0.50f, 1f);
    private static final Color C_LABEL     = new Color(0.65f, 0.65f, 0.85f, 1f);
    private static final Color C_GOLD      = new Color(1.00f, 0.84f, 0.00f, 1f);
    private static final Color C_HOV       = new Color(0.20f, 0.20f, 0.34f, 1f);
    private static final Color C_SEL       = new Color(0.25f, 0.50f, 0.90f, 1f);
    private static final Color C_SOUL_BTN  = new Color(0.45f, 0.15f, 0.70f, 1f);
    private static final Color C_SOUL_HOV  = new Color(0.55f, 0.25f, 0.80f, 1f);
    private static final Color C_LIST_HOV  = new Color(0.20f, 0.22f, 0.38f, 1f);
    private static final Color C_GRAY      = new Color(0.28f, 0.28f, 0.33f, 1f);

    // ---- Ability entry ----
    private static class AbilityEntry {
        final String charName; final int idx; final String name; final String desc;
        AbilityEntry(String cn, int i, Ability ab) {
            charName = cn; idx = i; name = ab.getName(); desc = ab.getDescription();
        }
        String ref() { return charName + ":" + idx; }
    }

    // ---- State ----
    private final Main             game;
    private final Runnable         onClose;
    private final PlayerInventory  inv;

    private final OrthographicCamera camera;
    private final FitViewport        viewport;
    private final Texture            white;
    private       Texture            soulTex;
    private final GlyphLayout        layout = new GlyphLayout();

    private final Map<String, Texture>          portraitTexs    = new LinkedHashMap<>();
    private final Map<Enums.CharClass, Texture> classIconTexs  = new LinkedHashMap<>();
    private final Map<Enums.CharType,  Texture> typeIconTexs   = new LinkedHashMap<>();
    private final List<Enums.CharClass>         unlockedClasses = new ArrayList<>();
    private final List<Enums.CharType>          unlockedTypes   = new ArrayList<>();
    private       List<AbilityEntry>            abilities       = new ArrayList<>();

    private float mouseX, mouseY;
    private int   portScroll = 0;  // portrait scroller offset
    private int   activeSlot = -1; // 0/1/2 = ability slot being assigned
    private String pickerChar = null;
    private int   pickerScroll = 0;

    // ---- First-open tutorial tooltips ----
    private int stoneTutStep = -1;
    private static final String[] STONE_TUT = {
        "Each character has a type and a class. Each type deals bonus damage to another type and each class has their own unique benefits.",
        "Select a type and class at the top.",
        "You will also select 3 abilities.",
        "The types, classes, and abilities available to you are from the characters you have unlocked."
    };

    // =========================================================

    public RunicStoneScreen(Main game, Runnable onClose, PlayerAppearance appearance) {
        this.game    = game;
        this.onClose = onClose;
        this.inv     = Main.inventory;

        camera   = new OrthographicCamera();
        viewport = new FitViewport(W, H, camera);
        camera.setToOrtho(false, W, H);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE); pm.fill();
        white = new Texture(pm);
        pm.dispose();

        soulTex = loadTex("item_soul.png");
        rebuildAll();
    }

    private void rebuildAll() {
        // Abilities
        abilities.clear();
        for (String cn : inv.unlockedCharacters) {
            Character chr = CharacterRoster.build(cn);
            if (chr == null) continue;
            for (int i = 0; i < 3; i++) {
                Ability ab = chr.getAbility(i);
                if (ab != null) abilities.add(new AbilityEntry(cn, i, ab));
            }
        }
        // Portraits
        for (Texture t : portraitTexs.values()) t.dispose();
        portraitTexs.clear();
        for (String cn : inv.unlockedCharacters) {
            Texture t = loadTex(cn.toLowerCase() + ".png");
            if (t == null) t = loadTex(cn + ".png");
            if (t != null) portraitTexs.put(cn, t);
        }
        // Unlocked classes + types + icons
        unlockedClasses.clear();
        unlockedTypes.clear();
        for (String cn : inv.unlockedCharacters) {
            Character chr = CharacterRoster.build(cn);
            if (chr == null) continue;
            if (!unlockedClasses.contains(chr.charClass)) unlockedClasses.add(chr.charClass);
            if (!unlockedTypes.contains(chr.getCharType())) unlockedTypes.add(chr.getCharType());
        }
        for (Enums.CharClass c : CLASSES) {
            if (!classIconTexs.containsKey(c)) {
                Texture t = loadTex(c.name().toLowerCase() + "_icon.png");
                if (t != null) classIconTexs.put(c, t);
            }
        }
        for (Enums.CharType t : TYPES) {
            if (!typeIconTexs.containsKey(t)) {
                Texture tx = loadTex(t.name().toLowerCase() + "_icon.png");
                if (tx != null) typeIconTexs.put(t, tx);
            }
        }
    }

    private Texture loadTex(String path) {
        try {
            FileHandle fh = Gdx.files.internal(path);
            if (fh.exists()) return new Texture(fh);
        } catch (Exception ignored) {}
        return null;
    }

    // ---- Lifecycle ----

    @Override
    public void show() {
        if (!Main.flags.is("runic_stone_tutorial_shown")) stoneTutStep = 0;

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean touchDown(int sx, int sy, int p, int btn) {
                if (btn == 0) handleClick(unproject(sx, sy));
                return true;
            }
            public boolean scrolled(int a)           { handleScroll(a);                    return true; }
            public boolean scrolled(float ax, float ay) { handleScroll((int)Math.signum(ay)); return true; }
        });
    }

    @Override public void hide()   { Gdx.input.setInputProcessor(null); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void resize(int w, int h) { viewport.update(w, h, true); }

    @Override
    public void dispose() {
        white.dispose();
        if (soulTex != null) soulTex.dispose();
        for (Texture t : portraitTexs.values())  t.dispose();
        for (Texture t : classIconTexs.values()) t.dispose();
        for (Texture t : typeIconTexs.values())  t.dispose();
    }

    // ---- Render ----

    @Override
    public void render(float delta) {
        Vector3 mv = unproject(Gdx.input.getX(), Gdx.input.getY());
        mouseX = mv.x; mouseY = mv.y;

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (activeSlot >= 0) { activeSlot = -1; pickerChar = null; }
            else close();
            return;
        }

        ScreenUtils.clear(0.05f, 0.05f, 0.09f, 1f);
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        drawTopBar();
        drawTypeRow();
        drawClassRow();
        drawSoulStrip();
        drawPortraitScroller();
        drawAbilitySlots();
        if (activeSlot >= 0) drawPicker();
        if (stoneTutStep >= 0) drawStoneTutorial(game.batch);

        game.batch.end();
    }

    // =========================================================
    // TOP BAR
    // =========================================================

    private void drawTopBar() {
        fill(0f, TITLE_Y, W, TITLE_H, new Color(0.07f, 0.07f, 0.12f, 1f));
        game.font.getData().setScale(0.85f);
        layout.setText(game.font, "RUNIC STONE");
        game.batch.setColor(C_GOLD);
        game.font.draw(game.batch, "RUNIC STONE", (W - layout.width) / 2f, H - 5f);
        layout.setText(game.font, "[ESC] CLOSE");
        game.batch.setColor(C_LABEL);
        game.font.draw(game.batch, "[ESC] CLOSE", W - layout.width - 12f, H - 5f);
        game.font.getData().setScale(1f);
        game.batch.setColor(Color.WHITE);
    }

    // =========================================================
    // TYPE ROW
    // =========================================================

    private void drawTypeRow() {
        fill(0f, TYPE_Y, W, TYPE_H, new Color(0.08f, 0.08f, 0.13f, 1f));

        game.font.getData().setScale(0.72f);
        layout.setText(game.font, "Type:");
        float labelW = layout.width;
        float labelH = layout.height;
        game.font.getData().setScale(1f);

        float iconY = TYPE_Y + (TYPE_H - TYPE_ICON_SZ) / 2f;

        int   count  = unlockedTypes.size();
        float iconsW = count > 0 ? count * TYPE_ICON_SZ + (count - 1) * TYPE_ICON_GAP : 0f;
        float totalW = labelW + (count > 0 ? 14f + iconsW : 0f);
        float startX = (W - totalW) / 2f;
        float labelY = TYPE_Y + (TYPE_H + labelH) / 2f;

        game.font.getData().setScale(0.72f);
        game.batch.setColor(C_LABEL);
        game.font.draw(game.batch, "Type:", startX, labelY);
        game.font.getData().setScale(1f);
        game.batch.setColor(Color.WHITE);

        float iconStartX = startX + labelW + 14f;
        for (int i = 0; i < count; i++) {
            Enums.CharType t = unlockedTypes.get(i);
            float ix  = iconStartX + i * (TYPE_ICON_SZ + TYPE_ICON_GAP);
            boolean sel = inv.charType == t;
            boolean hov = hit(mouseX, mouseY, ix, iconY, TYPE_ICON_SZ, TYPE_ICON_SZ);
            Texture icon = typeIconTexs.get(t);
            if (sel) fill(ix - 3f, iconY - 3f, TYPE_ICON_SZ + 6f, TYPE_ICON_SZ + 6f, typeColor(t));
            if (icon != null) {
                game.batch.setColor(sel ? Color.WHITE
                        : (hov ? new Color(0.72f, 0.72f, 0.72f, 1f)
                               : new Color(0.38f, 0.38f, 0.42f, 1f)));
                game.batch.draw(icon, ix, iconY, TYPE_ICON_SZ, TYPE_ICON_SZ);
                game.batch.setColor(Color.WHITE);
            } else {
                fill(ix, iconY, TYPE_ICON_SZ, TYPE_ICON_SZ, sel ? typeColor(t) : (hov ? C_HOV : C_GRAY));
                outline(ix, iconY, TYPE_ICON_SZ, TYPE_ICON_SZ, sel ? Color.WHITE : C_OUTLINE);
            }
        }
    }

    // =========================================================
    // CLASS ICON ROW
    // =========================================================

    private void drawClassRow() {
        fill(0f, CLASS_Y, W, CLASS_H, C_BG);

        game.font.getData().setScale(0.72f);
        layout.setText(game.font, "Class:");
        float labelW = layout.width;
        float labelH = layout.height;
        game.font.getData().setScale(1f);

        float iy     = CLASS_Y + (CLASS_H - CLS_SZ) / 2f;
        float labelY = CLASS_Y + (CLASS_H + labelH) / 2f;

        if (unlockedClasses.isEmpty()) {
            game.font.getData().setScale(0.72f);
            game.batch.setColor(C_LABEL);
            game.font.draw(game.batch, "Class:", (W - labelW) / 2f, labelY);
            game.font.getData().setScale(1f);
            game.batch.setColor(Color.WHITE);
            return;
        }

        int   count  = unlockedClasses.size();
        float iconsW = count * CLS_SZ + (count - 1) * CLS_GAP;
        float totalW = labelW + 14f + iconsW;
        float startX = (W - totalW) / 2f;

        game.font.getData().setScale(0.72f);
        game.batch.setColor(C_LABEL);
        game.font.draw(game.batch, "Class:", startX, labelY);
        game.font.getData().setScale(1f);
        game.batch.setColor(Color.WHITE);

        float iconStartX = startX + labelW + 14f;
        for (int i = 0; i < count; i++) {
            Enums.CharClass cls = unlockedClasses.get(i);
            float ix  = iconStartX + i * (CLS_SZ + CLS_GAP);
            boolean sel = inv.charClass == cls;
            boolean hov = hit(mouseX, mouseY, ix, iy, CLS_SZ, CLS_SZ);
            Texture icon = classIconTexs.get(cls);

            if (icon != null) {
                if (sel) fill(ix - 3f, iy - 3f, CLS_SZ + 6f, CLS_SZ + 6f, classColor(cls));
                game.batch.setColor(sel ? Color.WHITE
                        : (hov ? new Color(0.72f, 0.72f, 0.72f, 1f)
                               : new Color(0.38f, 0.38f, 0.42f, 1f)));
                game.batch.draw(icon, ix, iy, CLS_SZ, CLS_SZ);
                game.batch.setColor(Color.WHITE);
            } else {
                fill(ix, iy, CLS_SZ, CLS_SZ, sel ? classColor(cls) : (hov ? C_HOV : C_GRAY));
                outline(ix, iy, CLS_SZ, CLS_SZ, sel ? Color.WHITE : C_OUTLINE);
                game.font.getData().setScale(0.70f);
                drawCentered(cls.name().substring(0, 1), ix, iy, CLS_SZ, CLS_SZ, Color.WHITE);
                game.font.getData().setScale(1f);
            }
        }
    }

    // =========================================================
    // SOUL STRIP
    // =========================================================

    private void drawSoulStrip() {
        fill(0f, SOUL_Y, W, SOUL_H, new Color(0.08f, 0.08f, 0.14f, 1f));
        outline(0f, SOUL_Y, W, SOUL_H, C_OUTLINE);

        game.font.getData().setScale(0.75f);
        layout.setText(game.font, "DEPOSIT:");
        float centerY = SOUL_Y + (SOUL_H + layout.height) / 2f;
        float btnY    = SOUL_Y + (SOUL_H - SOUL_BTN_H) / 2f;

        game.batch.setColor(C_LABEL);
        game.font.draw(game.batch, "DEPOSIT:", 12f, centerY);
        game.batch.setColor(Color.WHITE);

        float sx = 110f;
        boolean any = false;
        for (int i = 0; i < inv.bag.length; i++) {
            Item item = inv.bag[i];
            if (item == null || !item.name.endsWith(" Soul")) continue;
            String cn = item.name.substring(0, item.name.length() - 5);
            if (inv.unlockedCharacters.contains(cn)) continue;
            if (sx + SOUL_BTN_W > W - 8f) break;
            boolean hov = hit(mouseX, mouseY, sx, btnY, SOUL_BTN_W, SOUL_BTN_H);
            fill(sx, btnY, SOUL_BTN_W, SOUL_BTN_H, hov ? C_SOUL_HOV : C_SOUL_BTN);
            outline(sx, btnY, SOUL_BTN_W, SOUL_BTN_H, C_OUTLINE);
            if (soulTex != null) {
                float isz = SOUL_BTN_H - 4f;
                game.batch.setColor(Color.WHITE);
                game.batch.draw(soulTex, sx + 3f, btnY + 2f, isz, isz);
            }
            layout.setText(game.font, cn);
            game.font.draw(game.batch, cn, sx + SOUL_BTN_H + 1f, centerY);
            sx += SOUL_BTN_W + 6f;
            any = true;
        }
        if (!any) {
            game.batch.setColor(C_LABEL);
            game.font.draw(game.batch, "(no souls in bag)", 110f, centerY);
            game.batch.setColor(Color.WHITE);
        }
        game.font.getData().setScale(1f);
    }

    // =========================================================
    // PORTRAIT SCROLLER
    // =========================================================

    private void drawPortraitScroller() {
        fill(0f, PORT_BOT, W, PORT_H, new Color(0.07f, 0.07f, 0.12f, 1f));

        List<String> chars = inv.unlockedCharacters;
        if (chars.isEmpty()) {
            game.font.getData().setScale(0.75f);
            game.batch.setColor(C_LABEL);
            layout.setText(game.font, "No characters unlocked — deposit a soul above");
            game.font.draw(game.batch, "No characters unlocked — deposit a soul above",
                    (W - layout.width) / 2f, PORT_BOT + PORT_H / 2f + 8f);
            game.font.getData().setScale(1f);
            game.batch.setColor(Color.WHITE);
            return;
        }

        float nameH   = 16f;
        float imgBot  = PORT_BOT + (PORT_H - PORT_IMG_SZ - nameH - 6f) / 2f + nameH + 4f;
        float visW    = W - PORT_MARGIN * 2f;
        int   maxVis  = Math.max(1, (int)((visW + PORT_IMG_GAP) / (PORT_IMG_SZ + PORT_IMG_GAP)));
        int   maxScrl = Math.max(0, chars.size() - maxVis);
        if (portScroll > maxScrl) portScroll = maxScrl;

        for (int i = portScroll; i < chars.size(); i++) {
            float px = PORT_MARGIN + (i - portScroll) * (PORT_IMG_SZ + PORT_IMG_GAP);
            if (px + PORT_IMG_SZ > W - PORT_MARGIN) break;
            String cn = chars.get(i);
            Texture t  = portraitTexs.get(cn);
            if (t != null) {
                game.batch.setColor(Color.WHITE);
                game.batch.draw(t, px, imgBot, PORT_IMG_SZ, PORT_IMG_SZ);
            } else {
                fill(px, imgBot, PORT_IMG_SZ, PORT_IMG_SZ, new Color(0.15f, 0.15f, 0.22f, 1f));
                outline(px, imgBot, PORT_IMG_SZ, PORT_IMG_SZ, C_OUTLINE);
            }
            game.font.getData().setScale(0.62f);
            layout.setText(game.font, cn);
            game.batch.setColor(C_LABEL);
            game.font.draw(game.batch, cn, px + (PORT_IMG_SZ - layout.width) / 2f, imgBot - 2f);
            game.font.getData().setScale(1f);
            game.batch.setColor(Color.WHITE);
        }

        // Scroll arrows
        if (portScroll > 0) {
            game.batch.setColor(C_LABEL);
            game.font.draw(game.batch, "◄", PORT_MARGIN - 28f, PORT_BOT + PORT_H / 2f + 8f);
            game.batch.setColor(Color.WHITE);
        }
        if (portScroll < maxScrl) {
            game.batch.setColor(C_LABEL);
            game.font.draw(game.batch, "►", W - PORT_MARGIN + 6f, PORT_BOT + PORT_H / 2f + 8f);
            game.batch.setColor(Color.WHITE);
        }
    }

    // =========================================================
    // ABILITY SLOTS BAR
    // =========================================================

    private void drawAbilitySlots() {
        fill(0f, 0f, W, SLOT_H, new Color(0.06f, 0.06f, 0.10f, 1f));
        String[] refs = { inv.abilitySlot1, inv.abilitySlot2, inv.abilitySlot3 };
        for (int col = 0; col < 3; col++) {
            float cx  = col * SLOT_BTN_W;
            boolean active = activeSlot == col;
            boolean hov    = hit(mouseX, mouseY, cx, 0f, SLOT_BTN_W, SLOT_H);
            Color bg = active ? C_SEL : (hov ? C_HOV : new Color(0.10f, 0.10f, 0.18f, 1f));
            fill(cx + 2f, 2f, SLOT_BTN_W - 4f, SLOT_H - 4f, bg);
            outline(cx + 2f, 2f, SLOT_BTN_W - 4f, SLOT_H - 4f, active ? Color.WHITE : C_OUTLINE);

            AbilityEntry e = findEntry(refs[col]);
            game.font.getData().setScale(0.68f);
            String hdr = "ABILITY " + (col + 1);
            layout.setText(game.font, hdr);
            game.batch.setColor(active ? Color.WHITE : C_LABEL);
            game.font.draw(game.batch, hdr, cx + (SLOT_BTN_W - layout.width) / 2f, SLOT_H - 3f);
            String val = e != null ? e.name + "  (" + e.charName + ")" : "— none —";
            layout.setText(game.font, val);
            game.batch.setColor(Color.WHITE);
            game.font.draw(game.batch, val, cx + (SLOT_BTN_W - layout.width) / 2f, SLOT_H - 22f);
            game.font.getData().setScale(1f);
            game.batch.setColor(Color.WHITE);
            if (col > 0) fill(cx, 0f, 1.5f, SLOT_H, C_OUTLINE);
        }
    }

    // =========================================================
    // ABILITY PICKER OVERLAY
    // =========================================================

    private void drawPicker() {
        // Dim the whole picker area (SLOT_H..PORT_BOT)
        fill(0f, SLOT_H, W, PORT_BOT - SLOT_H, new Color(0f, 0f, 0f, 0.78f));

        // Character row: top of picker area
        float charBot = PORT_BOT - 6f;
        float charTop = charBot - PICK_CHAR_H;
        fill(0f, charTop, W, PICK_CHAR_H, new Color(0.07f, 0.07f, 0.13f, 0.97f));
        outline(0f, charTop, W, PICK_CHAR_H, C_OUTLINE);

        List<String> chars = inv.unlockedCharacters;
        if (!chars.isEmpty()) {
            int   maxVis   = Math.max(1, (int)((W + PICK_CHAR_GAP) / (PICK_CHAR_SZ + PICK_CHAR_GAP)));
            int   maxScrl  = Math.max(0, chars.size() - maxVis);
            if (pickerScroll > maxScrl) pickerScroll = maxScrl;
            float rowW     = Math.min(chars.size(), maxVis) * (PICK_CHAR_SZ + PICK_CHAR_GAP) - PICK_CHAR_GAP;
            float startX   = (W - rowW) / 2f;
            float imgBot   = charTop + (PICK_CHAR_H - PICK_CHAR_SZ - 14f) / 2f + 14f;

            for (int i = pickerScroll; i < chars.size(); i++) {
                float px = startX + (i - pickerScroll) * (PICK_CHAR_SZ + PICK_CHAR_GAP);
                if (px + PICK_CHAR_SZ > W) break;
                String cn  = chars.get(i);
                boolean sel = cn.equals(pickerChar);
                boolean hov = hit(mouseX, mouseY, px, imgBot, PICK_CHAR_SZ, PICK_CHAR_SZ);
                Texture t  = portraitTexs.get(cn);
                if (t != null) {
                    if (sel) fill(px - 3f, imgBot - 3f, PICK_CHAR_SZ + 6f, PICK_CHAR_SZ + 6f, C_SEL);
                    game.batch.setColor(sel ? Color.WHITE : (hov ? new Color(0.80f,0.80f,0.80f,1f) : new Color(0.45f,0.45f,0.45f,1f)));
                    game.batch.draw(t, px, imgBot, PICK_CHAR_SZ, PICK_CHAR_SZ);
                    game.batch.setColor(Color.WHITE);
                } else {
                    fill(px, imgBot, PICK_CHAR_SZ, PICK_CHAR_SZ, sel ? C_SEL : (hov ? C_HOV : new Color(0.15f,0.15f,0.22f,1f)));
                    outline(px, imgBot, PICK_CHAR_SZ, PICK_CHAR_SZ, C_OUTLINE);
                }
                game.font.getData().setScale(0.60f);
                layout.setText(game.font, cn);
                game.batch.setColor(sel ? C_GOLD : C_LABEL);
                game.font.draw(game.batch, cn, px + (PICK_CHAR_SZ - layout.width) / 2f, imgBot - 2f);
                game.font.getData().setScale(1f);
                game.batch.setColor(Color.WHITE);
            }
        }

        // Ability list for selected character
        float listTop = charTop - 4f;
        float listBot = SLOT_H + 4f;
        fill(0f, listBot, W, listTop - listBot, new Color(0.06f, 0.06f, 0.10f, 0.97f));

        if (pickerChar != null) {
            String slotRef = slotRef(activeSlot);
            float  iw      = W * 0.58f;
            float  ix      = (W - iw) / 2f;
            float  iy      = listTop;
            boolean drew = false;
            for (AbilityEntry e : abilities) {
                if (!e.charName.equals(pickerChar)) continue;
                if (e.idx != activeSlot) continue;
                if (iy - PICK_AB_H < listBot) break;
                iy -= PICK_AB_H;
                boolean sel = e.ref().equals(slotRef);
                boolean hov = hit(mouseX, mouseY, ix, iy, iw, PICK_AB_H - 2f);
                fill(ix, iy, iw, PICK_AB_H - 2f, sel ? C_SEL : (hov ? C_LIST_HOV : new Color(0.11f,0.11f,0.18f,1f)));
                outline(ix, iy, iw, PICK_AB_H - 2f, sel ? Color.WHITE : C_OUTLINE);
                game.font.getData().setScale(0.78f);
                game.batch.setColor(Color.WHITE);
                game.font.draw(game.batch, e.name, ix + 10f, iy + PICK_AB_H - 7f);
                game.font.getData().setScale(0.62f);
                game.batch.setColor(C_LABEL);
                game.font.draw(game.batch, e.desc, ix + 10f, iy + PICK_AB_H - 22f);
                game.font.getData().setScale(1f);
                game.batch.setColor(Color.WHITE);
                drew = true;
            }
            if (!drew) {
                game.font.getData().setScale(0.76f);
                game.batch.setColor(C_LABEL);
                layout.setText(game.font, "No abilities available for " + pickerChar);
                game.font.draw(game.batch, "No abilities available for " + pickerChar,
                        (W - layout.width) / 2f, (listBot + listTop) / 2f + 8f);
                game.font.getData().setScale(1f);
                game.batch.setColor(Color.WHITE);
            }
        } else {
            game.font.getData().setScale(0.76f);
            game.batch.setColor(C_LABEL);
            String hint = inv.unlockedCharacters.isEmpty()
                    ? "Deposit a soul to unlock characters"
                    : "Select a character above to choose an ability";
            layout.setText(game.font, hint);
            game.font.draw(game.batch, hint, (W - layout.width) / 2f, (listBot + listTop) / 2f + 8f);
            game.font.getData().setScale(1f);
            game.batch.setColor(Color.WHITE);
        }
    }

    // =========================================================
    // STONE TUTORIAL OVERLAY
    // =========================================================

    private void drawStoneTutorial(com.badlogic.gdx.graphics.g2d.SpriteBatch b) {
        // Dim entire screen
        b.setColor(0f, 0f, 0f, 0.72f);
        b.draw(white, 0, 0, W, H);
        b.setColor(Color.WHITE);

        // Card
        float cw = 720f, ch = 200f, cx = (W - cw) / 2f, cy = (H - ch) / 2f;
        b.setColor(0.07f, 0.07f, 0.13f, 0.97f);
        b.draw(white, cx, cy, cw, ch);
        b.setColor(1f, 0.84f, 0f, 1f);
        b.draw(white, cx,          cy,          cw, 2f);
        b.draw(white, cx,          cy + ch - 2f, cw, 2f);
        b.draw(white, cx,          cy,          3f, ch);
        b.draw(white, cx + cw - 2f, cy,          2f, ch);
        b.setColor(Color.WHITE);

        // Step counter
        game.font.getData().setScale(0.45f);
        game.font.setColor(C_LABEL);
        String counter = (stoneTutStep + 1) + " / " + STONE_TUT.length;
        game.font.draw(b, counter, cx + 14f, cy + ch - 10f);

        // Body text
        game.font.getData().setScale(0.58f);
        game.font.setColor(0.93f, 0.93f, 0.96f, 1f);
        game.font.draw(b, STONE_TUT[stoneTutStep], cx + 14f, cy + ch - 30f, cw - 28f, -1, true);

        // Continue button
        float bw = 130f, bh = 35f, bx = cx + cw - bw - 10f, by = cy + 10f;
        b.setColor(0.16f, 0.16f, 0.28f, 1f);
        b.draw(white, bx, by, bw, bh);
        b.setColor(1f, 0.84f, 0f, 1f);
        b.draw(white, bx, by, 3f, bh);
        b.setColor(Color.WHITE);
        game.font.getData().setScale(0.48f);
        boolean last = (stoneTutStep == STONE_TUT.length - 1);
        game.font.draw(b, last ? "GOT IT!" : "CONTINUE »", bx, by + bh - 8f, bw, 1, true);
        game.font.getData().setScale(1f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    // =========================================================
    // INPUT
    // =========================================================

    private void handleClick(Vector3 v) {
        float wx = v.x, wy = v.y;

        // Tutorial tooltip — any click advances; last step dismisses
        if (stoneTutStep >= 0) {
            stoneTutStep++;
            if (stoneTutStep >= STONE_TUT.length) {
                stoneTutStep = -1;
                Main.flags.set("runic_stone_tutorial_shown", 1);
            }
            return;
        }

        // Ability picker (consumes all clicks when active)
        if (activeSlot >= 0) {
            float charBot = PORT_BOT - 6f;
            float charTop = charBot - PICK_CHAR_H;

            // Character row hit
            List<String> chars = inv.unlockedCharacters;
            int   maxVis  = Math.max(1, (int)((W + PICK_CHAR_GAP) / (PICK_CHAR_SZ + PICK_CHAR_GAP)));
            int   maxScrl = Math.max(0, chars.size() - maxVis);
            float rowW    = Math.min(chars.size(), maxVis) * (PICK_CHAR_SZ + PICK_CHAR_GAP) - PICK_CHAR_GAP;
            float startX  = (W - rowW) / 2f;
            float imgBot  = charTop + (PICK_CHAR_H - PICK_CHAR_SZ - 14f) / 2f + 14f;
            for (int i = pickerScroll; i < chars.size(); i++) {
                float px = startX + (i - pickerScroll) * (PICK_CHAR_SZ + PICK_CHAR_GAP);
                if (px + PICK_CHAR_SZ > W) break;
                if (hit(wx, wy, px, imgBot, PICK_CHAR_SZ, PICK_CHAR_SZ)) {
                    pickerChar = chars.get(i);
                    return;
                }
            }

            // Ability list hit
            if (pickerChar != null) {
                float listTop = charTop - 4f;
                float listBot = SLOT_H + 4f;
                float iw = W * 0.58f, ix = (W - iw) / 2f, iy = listTop;
                for (AbilityEntry e : abilities) {
                    if (!e.charName.equals(pickerChar)) continue;
                    if (e.idx != activeSlot) continue;
                    if (iy - PICK_AB_H < listBot) break;
                    iy -= PICK_AB_H;
                    if (hit(wx, wy, ix, iy, iw, PICK_AB_H - 2f)) {
                        setSlot(activeSlot, e.ref());
                        inv.save(Main.flags);
                        activeSlot = -1; pickerChar = null;
                        return;
                    }
                }
            }

            // Click outside picker area → dismiss
            if (wy >= PORT_BOT || wy < SLOT_H) { activeSlot = -1; pickerChar = null; }
            return;
        }

        // Ability slots bar
        for (int col = 0; col < 3; col++) {
            if (hit(wx, wy, col * SLOT_BTN_W, 0f, SLOT_BTN_W, SLOT_H)) {
                if (activeSlot == col) { activeSlot = -1; pickerChar = null; }
                else {
                    activeSlot  = col;
                    pickerChar  = inv.unlockedCharacters.isEmpty() ? null : inv.unlockedCharacters.get(0);
                    pickerScroll = 0;
                }
                return;
            }
        }

        // Portrait scroller arrows
        if (wy >= PORT_BOT && wy < SOUL_Y) {
            if (wx < PORT_MARGIN && portScroll > 0)   { portScroll--; return; }
            if (wx > W - PORT_MARGIN)                  { portScroll++; return; }
        }

        // Type icon buttons
        if (!unlockedTypes.isEmpty()) {
            game.font.getData().setScale(0.72f);
            layout.setText(game.font, "Type:");
            float lw = layout.width;
            game.font.getData().setScale(1f);
            int   count      = unlockedTypes.size();
            float iconsW     = count * TYPE_ICON_SZ + (count - 1) * TYPE_ICON_GAP;
            float iconStartX = (W - (lw + 14f + iconsW)) / 2f + lw + 14f;
            float iconY      = TYPE_Y + (TYPE_H - TYPE_ICON_SZ) / 2f;
            for (int i = 0; i < count; i++) {
                float ix = iconStartX + i * (TYPE_ICON_SZ + TYPE_ICON_GAP);
                if (hit(wx, wy, ix, iconY, TYPE_ICON_SZ, TYPE_ICON_SZ)) {
                    inv.charType = unlockedTypes.get(i); inv.save(Main.flags); return;
                }
            }
        }

        // Class icon buttons
        if (!unlockedClasses.isEmpty()) {
            game.font.getData().setScale(0.72f);
            layout.setText(game.font, "Class:");
            float lw = layout.width;
            game.font.getData().setScale(1f);
            int   count      = unlockedClasses.size();
            float iconsW     = count * CLS_SZ + (count - 1) * CLS_GAP;
            float iconStartX = (W - (lw + 14f + iconsW)) / 2f + lw + 14f;
            float iy         = CLASS_Y + (CLASS_H - CLS_SZ) / 2f;
            for (int i = 0; i < count; i++) {
                float ix = iconStartX + i * (CLS_SZ + CLS_GAP);
                if (hit(wx, wy, ix, iy, CLS_SZ, CLS_SZ)) {
                    inv.charClass = unlockedClasses.get(i); inv.save(Main.flags); return;
                }
            }
        }

        // Soul deposit buttons
        {
            float sx = 110f, sy = SOUL_Y + (SOUL_H - SOUL_BTN_H) / 2f;
            for (int i = 0; i < inv.bag.length; i++) {
                Item item = inv.bag[i];
                if (item == null || !item.name.endsWith(" Soul")) continue;
                String cn = item.name.substring(0, item.name.length() - 5);
                if (inv.unlockedCharacters.contains(cn)) continue;
                if (sx + SOUL_BTN_W > W - 8f) break;
                if (hit(wx, wy, sx, sy, SOUL_BTN_W, SOUL_BTN_H)) {
                    depositSoul(i, cn); return;
                }
                sx += SOUL_BTN_W + 6f;
            }
        }
    }

    private void handleScroll(int amount) {
        if (mouseY >= PORT_BOT && mouseY < SOUL_Y && activeSlot < 0)
            portScroll = Math.max(0, portScroll + amount);
        if (activeSlot >= 0) {
            float charTop = PORT_BOT - 6f - PICK_CHAR_H;
            float charBot = PORT_BOT - 6f;
            if (mouseY >= charTop && mouseY <= charBot)
                pickerScroll = Math.max(0, pickerScroll + amount);
        }
    }

    private void depositSoul(int bagIdx, String cn) {
        inv.bag[bagIdx] = null;
        if (!inv.unlockedCharacters.contains(cn)) inv.unlockedCharacters.add(cn);
        Main.flags.set("souls_deposited", inv.unlockedCharacters.size());
        inv.save(Main.flags);
        rebuildAll();
    }

    private void setSlot(int col, String ref) {
        switch (col) {
            case 0: inv.abilitySlot1 = ref; break;
            case 1: inv.abilitySlot2 = ref; break;
            case 2: inv.abilitySlot3 = ref; break;
        }
    }

    private String slotRef(int col) {
        switch (col) {
            case 0: return inv.abilitySlot1;
            case 1: return inv.abilitySlot2;
            case 2: return inv.abilitySlot3;
            default: return "";
        }
    }

    private void close() { inv.save(Main.flags); onClose.run(); }

    // =========================================================
    // HELPERS
    // =========================================================

    private AbilityEntry findEntry(String ref) {
        if (ref == null || ref.isEmpty()) return null;
        for (AbilityEntry e : abilities) if (e.ref().equals(ref)) return e;
        return null;
    }

    private Vector3 unproject(int sx, int sy) {
        return viewport.unproject(new Vector3(sx, sy, 0));
    }

    private void fill(float x, float y, float w, float h, Color c) {
        game.batch.setColor(c); game.batch.draw(white, x, y, w, h); game.batch.setColor(Color.WHITE);
    }

    private void outline(float x, float y, float w, float h, Color c) {
        float t = 1.5f;
        game.batch.setColor(c);
        game.batch.draw(white, x,     y,     w, t);
        game.batch.draw(white, x,     y+h-t, w, t);
        game.batch.draw(white, x,     y,     t, h);
        game.batch.draw(white, x+w-t, y,     t, h);
        game.batch.setColor(Color.WHITE);
    }

    private void drawCentered(String text, float x, float y, float w, float h, Color c) {
        layout.setText(game.font, text);
        game.batch.setColor(c);
        game.font.draw(game.batch, text, x + (w - layout.width) / 2f, y + (h + layout.height) / 2f);
        game.batch.setColor(Color.WHITE);
    }

    private static boolean hit(float px, float py, float rx, float ry, float rw, float rh) {
        return px >= rx && px < rx+rw && py >= ry && py < ry+rh;
    }

    private static Color typeColor(Enums.CharType t) {
        switch (t) {
            case FLORA:     return new Color(0.18f, 0.65f, 0.22f, 1f);
            case FAUNA:     return new Color(0.62f, 0.38f, 0.16f, 1f);
            case AQUA:      return new Color(0.14f, 0.48f, 0.84f, 1f);
            case WIND:      return new Color(0.55f, 0.75f, 0.55f, 1f);
            case FIRE:      return new Color(0.88f, 0.33f, 0.10f, 1f);
            case ANGELIC:   return new Color(0.85f, 0.85f, 0.55f, 1f);
            case NIGHTMARE: return new Color(0.42f, 0.10f, 0.58f, 1f);
            default:        return new Color(0.35f, 0.35f, 0.55f, 1f);
        }
    }

    private static Color classColor(Enums.CharClass c) {
        switch (c) {
            case FIGHTER:   return new Color(0.78f, 0.24f, 0.20f, 1f);
            case MAGE:      return new Color(0.28f, 0.20f, 0.80f, 1f);
            case TANK:      return new Color(0.38f, 0.38f, 0.42f, 1f);
            case SUPPORT:   return new Color(0.18f, 0.68f, 0.33f, 1f);
            case ASSASSIN:  return new Color(0.16f, 0.16f, 0.24f, 1f);
            case SNIPER:    return new Color(0.58f, 0.48f, 0.14f, 1f);
            case ENGINEER:  return new Color(0.55f, 0.35f, 0.10f, 1f);
            case COLLECTOR: return new Color(0.20f, 0.55f, 0.60f, 1f);
            case CHAOS:     return new Color(0.70f, 0.15f, 0.70f, 1f);
            default:        return new Color(0.35f, 0.35f, 0.55f, 1f);
        }
    }
}
