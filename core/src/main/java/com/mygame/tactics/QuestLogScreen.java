package com.mygame.tactics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;

import java.util.ArrayList;
import java.util.List;

public class QuestLogScreen implements Screen {

    private static final float W = 1280f, H = 720f;

    // Panel
    private static final float PANEL_X = 60f,  PANEL_Y = 40f;
    private static final float PANEL_W = 1160f, PANEL_H = 640f;

    // Top bar inside panel
    private static final float BAR_H = 36f;
    private static final float BAR_Y = PANEL_Y + PANEL_H - BAR_H;

    // List column (left side)
    private static final float LIST_X  = PANEL_X + 12f;
    private static final float LIST_W  = 320f;
    private static final float ROW_H   = 42f;
    private static final float LIST_TOP = BAR_Y - 10f;

    // Detail panel (right side)
    private static final float DETAIL_X = LIST_X + LIST_W + 20f;
    private static final float DETAIL_W = PANEL_W - LIST_W - 44f;

    // Colors
    private static final Color C_BG       = new Color(0.05f, 0.05f, 0.09f, 1f);
    private static final Color C_PANEL    = new Color(0.08f, 0.08f, 0.13f, 1f);
    private static final Color C_OUTLINE  = new Color(0.28f, 0.28f, 0.48f, 1f);
    private static final Color C_LABEL    = new Color(0.60f, 0.60f, 0.80f, 1f);
    private static final Color C_GOLD     = new Color(1.00f, 0.84f, 0.00f, 1f);
    private static final Color C_DONE     = new Color(0.30f, 0.85f, 0.40f, 1f);
    private static final Color C_HOV      = new Color(0.14f, 0.14f, 0.24f, 1f);
    private static final Color C_SEL      = new Color(0.18f, 0.28f, 0.55f, 1f);

    // ---- Quest definitions ----

    private static class Quest {
        final String name;
        final String completionFlag; // PlayerFlag key; null = never complete
        final String description;
        final String reward;

        Quest(String name, String completionFlag, String description, String reward) {
            this.name           = name;
            this.completionFlag = completionFlag;
            this.description    = description;
            this.reward         = reward;
        }

        boolean isComplete() {
            return completionFlag != null && Main.flags.is(completionFlag);
        }
    }

    private static final List<Quest> QUESTS = new ArrayList<>();
    static {
        QUESTS.add(new Quest(
            "Tutorial",
            "area_tutorial_complete",
            "Learn the basics of combat by fighting alongside Evan against Fescue, Willow, and Stoneguard. "
          + "Then visit the Runic Stone to choose your abilities and cross the bridge into the Forest.",
            "Fescue, Willow, Stoneguard"
        ));
        QUESTS.add(new Quest(
            "Fix the Stairs",
            "fix_stairs_complete",
            "The inn's stairs are broken. Talk to Thomas, who has moved into the inn to work on them. "
          + "He needs three violetberries from the forest to stain the wood. "
          + "Return to Thomas with the berries, then deliver his axe to Luke near the river.",
            "Access to inn second floor, Axe"
        ));
        QUESTS.add(new Quest(
            "A Man in the Woods",
            "man_in_woods_complete",
            "Luke needs help clearing dead trees blocking Haven's main entrance. "
          + "Equip the axe and cut down all four trees near the river. "
          + "Talk to Luke when they're all cleared.",
            "Axe, Logs"
        ));
    }

    // ---- State ----
    private final Main     game;
    private final Screen   returnScreen;
    private final Texture  white;
    private final GlyphLayout layout = new GlyphLayout();

    private final OrthographicCamera camera;
    private final FitViewport        viewport;

    private int   selectedIdx = 0;
    private float mouseX, mouseY;

    // =========================================================

    public QuestLogScreen(Main game, Screen returnScreen) {
        this.game         = game;
        this.returnScreen = returnScreen;

        camera   = new OrthographicCamera();
        viewport = new FitViewport(W, H, camera);
        camera.setToOrtho(false, W, H);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE); pm.fill();
        white = new Texture(pm);
        pm.dispose();
    }

    // ---- Lifecycle ----

    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean touchDown(int sx, int sy, int p, int btn) {
                if (btn == 0) handleClick(unproject(sx, sy));
                return true;
            }
        });
    }

    @Override public void hide()   { Gdx.input.setInputProcessor(null); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void resize(int w, int h) { viewport.update(w, h, true); }

    @Override
    public void dispose() {
        white.dispose();
    }

    // ---- Render ----

    @Override
    public void render(float delta) {
        Vector3 mv = unproject(Gdx.input.getX(), Gdx.input.getY());
        mouseX = mv.x; mouseY = mv.y;

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
                || Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            game.setScreen(returnScreen);
            return;
        }

        ScreenUtils.clear(0.03f, 0.03f, 0.06f, 1f);
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        drawPanel();
        drawTopBar();
        drawQuestList();
        drawDetail();

        game.batch.end();
    }

    // ---- Drawing ----

    private void drawPanel() {
        // Drop shadow
        fill(PANEL_X + 6f, PANEL_Y - 6f, PANEL_W, PANEL_H, new Color(0f, 0f, 0f, 0.55f));
        fill(PANEL_X, PANEL_Y, PANEL_W, PANEL_H, C_PANEL);
        outline(PANEL_X, PANEL_Y, PANEL_W, PANEL_H, C_OUTLINE);

        // Divider between list and detail
        float divX = LIST_X + LIST_W + 10f;
        fill(divX, PANEL_Y + 4f, 1.5f, PANEL_H - BAR_H - 8f, C_OUTLINE);
    }

    private void drawTopBar() {
        fill(PANEL_X, BAR_Y, PANEL_W, BAR_H, C_BG);
        outline(PANEL_X, BAR_Y, PANEL_W, BAR_H, C_OUTLINE);

        game.font.getData().setScale(0.85f);
        layout.setText(game.font, "QUEST LOG");
        game.batch.setColor(C_GOLD);
        game.font.draw(game.batch, "QUEST LOG", PANEL_X + (PANEL_W - layout.width) / 2f, BAR_Y + BAR_H - 7f);

        layout.setText(game.font, "[Q] CLOSE");
        game.batch.setColor(C_LABEL);
        game.font.draw(game.batch, "[Q] CLOSE", PANEL_X + PANEL_W - layout.width - 14f, BAR_Y + BAR_H - 7f);

        game.font.getData().setScale(1f);
        game.batch.setColor(Color.WHITE);
    }

    private void drawQuestList() {
        int completed = 0;
        for (Quest q : QUESTS) if (q.isComplete()) completed++;

        game.font.getData().setScale(0.62f);
        layout.setText(game.font, completed + " / " + QUESTS.size() + " completed");
        game.batch.setColor(C_LABEL);
        game.font.draw(game.batch, completed + " / " + QUESTS.size() + " completed",
                LIST_X, LIST_TOP - 4f);
        game.font.getData().setScale(1f);
        game.batch.setColor(Color.WHITE);

        float ry = LIST_TOP - 28f;
        for (int i = 0; i < QUESTS.size(); i++) {
            Quest q   = QUESTS.get(i);
            boolean sel = selectedIdx == i;
            boolean hov = hit(mouseX, mouseY, LIST_X, ry - ROW_H + 4f, LIST_W, ROW_H - 2f);

            fill(LIST_X, ry - ROW_H + 4f, LIST_W, ROW_H - 2f,
                    sel ? C_SEL : hov ? C_HOV : new Color(0.10f, 0.10f, 0.17f, 1f));
            outline(LIST_X, ry - ROW_H + 4f, LIST_W, ROW_H - 2f,
                    sel ? new Color(0.40f, 0.55f, 0.90f, 1f) : C_OUTLINE);

            // Status dot
            Color dotColor = q.isComplete() ? C_DONE : new Color(0.55f, 0.55f, 0.65f, 1f);
            fill(LIST_X + 8f, ry - ROW_H / 2f - 5f, 10f, 10f, dotColor);

            // Quest name
            game.font.getData().setScale(0.72f);
            game.batch.setColor(q.isComplete() ? C_DONE : Color.WHITE);
            game.font.draw(game.batch, q.name, LIST_X + 26f, ry - 10f);

            // Completed label
            if (q.isComplete()) {
                game.font.getData().setScale(0.55f);
                layout.setText(game.font, "Completed");
                game.batch.setColor(C_DONE);
                game.font.draw(game.batch, "Completed", LIST_X + 26f, ry - 26f);
            }

            game.font.getData().setScale(1f);
            game.batch.setColor(Color.WHITE);
            ry -= ROW_H;
        }
    }

    private void drawDetail() {
        if (selectedIdx < 0 || selectedIdx >= QUESTS.size()) return;
        Quest q = QUESTS.get(selectedIdx);

        float ty = LIST_TOP - 4f;

        // Title
        game.font.getData().setScale(0.90f);
        game.batch.setColor(q.isComplete() ? C_DONE : C_GOLD);
        game.font.draw(game.batch, q.name, DETAIL_X, ty);
        ty -= 28f;

        // Status badge
        String statusText = q.isComplete() ? "COMPLETED" : "IN PROGRESS";
        Color  statusColor = q.isComplete() ? C_DONE : new Color(0.85f, 0.70f, 0.20f, 1f);
        game.font.getData().setScale(0.65f);
        layout.setText(game.font, statusText);
        fill(DETAIL_X, ty - layout.height - 2f, layout.width + 14f, layout.height + 8f, statusColor);
        game.batch.setColor(Color.WHITE);
        game.font.draw(game.batch, statusText, DETAIL_X + 7f, ty);
        ty -= layout.height + 18f;

        // Description
        game.font.getData().setScale(0.70f);
        game.batch.setColor(C_LABEL);
        ty = drawWrapped(q.description, DETAIL_X, ty, DETAIL_W);
        ty -= 22f;

        // Reward
        if (q.reward != null && !q.reward.isEmpty()) {
            game.font.getData().setScale(0.68f);
            game.batch.setColor(C_LABEL);
            game.font.draw(game.batch, "Unlocks:", DETAIL_X, ty);
            ty -= 22f;
            game.batch.setColor(Color.WHITE);
            game.font.draw(game.batch, q.reward, DETAIL_X + 10f, ty);
        }

        game.font.getData().setScale(1f);
        game.batch.setColor(Color.WHITE);
    }

    /** Draws word-wrapped text, returns the y position after the last line. */
    private float drawWrapped(String text, float x, float startY, float maxW) {
        game.font.getData().setScale(0.70f);
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        float y = startY;
        float lineH = 22f;

        for (String word : words) {
            String test = line.length() == 0 ? word : line + " " + word;
            layout.setText(game.font, test);
            if (layout.width > maxW && line.length() > 0) {
                game.font.draw(game.batch, line.toString(), x, y);
                y -= lineH;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) {
            game.font.draw(game.batch, line.toString(), x, y);
            y -= lineH;
        }
        return y;
    }

    // ---- Input ----

    private void handleClick(Vector3 v) {
        float ry = LIST_TOP - 28f;
        for (int i = 0; i < QUESTS.size(); i++) {
            if (hit(v.x, v.y, LIST_X, ry - ROW_H + 4f, LIST_W, ROW_H - 2f)) {
                selectedIdx = i;
                return;
            }
            ry -= ROW_H;
        }
    }

    // ---- Helpers ----

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

    private static boolean hit(float px, float py, float rx, float ry, float rw, float rh) {
        return px >= rx && px < rx+rw && py >= ry && py < ry+rh;
    }
}
