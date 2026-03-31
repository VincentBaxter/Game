package com.mygame.tactics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.mygame.tactics.network.NetworkClient;
import com.badlogic.gdx.InputAdapter;

/**
 * Online screen — shown when the player clicks Online from the menu.
 * Lets the player enter a server IP address and connect.
 * Once connected and matched, transitions to DraftScreen.
 *
 * States:
 *   ENTERING_IP   — player is typing the server IP
 *   CONNECTING    — attempting to connect, showing spinner
 *   WAITING       — connected, waiting for an opponent
 *   ERROR         — connection failed, showing error message
 */
public class OnlineScreen implements Screen {

    private enum State { ENTERING_IP, CONNECTING, ERROR }

    // -----------------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------------
    private static final float FIELD_W  = 500f;
    private static final float FIELD_H  = 60f;
    private static final float FIELD_X  = (1280f - FIELD_W) / 2f;
    private static final float FIELD_Y  = 310f;
    private static final float BTN_W    = 220f;
    private static final float BTN_H    = 60f;
    private static final float BTN_X    = (1280f - BTN_W) / 2f;
    private static final float BTN_Y    = FIELD_Y - BTN_H - 20f;

    // -----------------------------------------------------------------------
    // Fields
    // -----------------------------------------------------------------------
    private final Main               game;
    private final OrthographicCamera camera;
    private final FitViewport        viewport;
    private final Texture            whitePixel;
    private final NetworkClient      client;
    final         PlayerAppearance   appearance;

    private State  state        = State.ENTERING_IP;
    private String serverIp     = "";
    private String errorMessage = "";
    private float  dotTimer     = 0f;
    private int    dotCount     = 0;
    private boolean connectHovered = false;
    private boolean backHovered    = false;

    private final Rectangle connectBounds = new Rectangle(BTN_X, BTN_Y, BTN_W, BTN_H);
    private final Rectangle backBounds    = new Rectangle(40f, 40f, 160f, 50f);

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public OnlineScreen(Main game, NetworkClient client) {
        this(game, client, new PlayerAppearance());
    }

    public OnlineScreen(Main game, NetworkClient client, PlayerAppearance appearance) {
        this.game       = game;
        this.client     = client;
        this.appearance = appearance;

        camera   = new OrthographicCamera();
        viewport = new FitViewport(1280, 720, camera);
        camera.position.set(640, 360, 0);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();

        // Wire up the network listener
        client.setListener(new NetworkClient.MessageListener() {
            @Override
            public void onConnected() {
                // Connected — load the world map.
                // WorldScreen will send LobbyJoinAction after its listener is set up.
                Gdx.app.postRunnable(() -> enterWorld());
            }

            @Override
            public void onDisconnected() {
                if (state != State.ENTERING_IP) {
                    state        = State.ERROR;
                    errorMessage = "Disconnected from server.";
                }
            }

            @Override
            public void onMessage(NetworkMessage msg) {
                // WorldScreen handles all lobby/room messages once we're there
            }
        });
    }

    private void enterWorld() {
        String[] dirs = {".", "assets", "../assets", "../../assets"};
        WorldArea area = null;
        for (String d : dirs) {
            FileHandle f = Gdx.files.local(d + "/Test_Map.txt");
            if (f.exists()) {
                try { area = WorldArea.load(f); } catch (Exception ignored) {}
                if (area != null) break;
            }
        }
        // Fallback: internal (classpath) — works when running from a JAR
        if (area == null) {
            try {
                FileHandle f = Gdx.files.internal("Test_Map.txt");
                if (f.exists()) area = WorldArea.load(f);
            } catch (Exception ignored) {}
        }
        if (area == null) {
            state        = State.ERROR;
            errorMessage = "Could not load Test_Map.txt — make sure it is in the assets folder.";
            return;
        }
        game.setScreen(new WorldScreen(game, area, appearance, client));
    }

    // -----------------------------------------------------------------------
    // Screen lifecycle
    // -----------------------------------------------------------------------
    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
 
            @Override
            public boolean keyTyped(char c) {
                // Only capture typing when the IP entry field is active
                if (state != State.ENTERING_IP) return false;

                if (c == '\b') {
                    // Backspace
                    if (serverIp.length() > 0)
                        serverIp = serverIp.substring(0, serverIp.length() - 1);
                    return true;
                }

                if (c == '\r' || c == '\n') {
                    if (state == State.ENTERING_IP) attemptConnect();
                    return true;
                }

                // Allow digits, letters, dots and hyphens so both numeric IPs
                // (192.168.1.1) and hostnames (localhost, my-server) work.
                // Fully qualify to avoid ambiguity with the game's Character class.
                if (java.lang.Character.isLetterOrDigit(c) || c == '.' || c == '-') {
                    serverIp += c;
                    return true;
                }

                return false;
            }
        });
    }

    @Override
    public void render(float delta) {
        if (state == State.CONNECTING) {
            dotTimer += delta;
            if (dotTimer >= 0.5f) { dotTimer = 0f; dotCount = (dotCount + 1) % 4; }
        }

        handleInput();

        ScreenUtils.clear(0.06f, 0.06f, 0.10f, 1f);
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        drawBackground(game.batch);
        drawTitle(game.batch);

        switch (state) {
            case ENTERING_IP: drawIpEntry(game.batch);    break;
            case CONNECTING:  drawConnecting(game.batch); break;
            case ERROR:       drawError(game.batch);      break;
        }

        drawBackButton(game.batch);

        game.batch.end();
    }

    @Override public void resize(int w, int h) { viewport.update(w, h); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
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

        connectHovered = connectBounds.contains(world.x, world.y);
        backHovered    = backBounds.contains(world.x, world.y);

        if (!Gdx.input.justTouched()) return;

        // Back button — return to menu
        if (backBounds.contains(world.x, world.y)) {
            client.disconnect();
            game.setScreen(new MenuScreen(game));
            return;
        }

        if (state == State.ENTERING_IP && connectBounds.contains(world.x, world.y)) {
            attemptConnect();
            return;
        }

        // Retry on error
        if (state == State.ERROR && connectBounds.contains(world.x, world.y)) {
            state        = State.ENTERING_IP;
            errorMessage = "";
        }
    }

    private void attemptConnect() {
        if (serverIp.trim().isEmpty()) {
            errorMessage = "Please enter a server IP address.";
            state        = State.ERROR;
            return;
        }
        state = State.CONNECTING;
        new Thread(() -> {
            try {
                client.connect(serverIp.trim());
                // onConnected() callback calls enterWorld()
            } catch (Exception e) {
                errorMessage = "Could not connect to " + serverIp + "\n" + e.getMessage();
                state        = State.ERROR;
            }
        }, "network-connect").start();
    }

    // -----------------------------------------------------------------------
    // Drawing
    // -----------------------------------------------------------------------

    private void drawBackground(SpriteBatch b) {
        b.setColor(0.08f, 0.08f, 0.14f, 1f);
        b.draw(whitePixel, 0, 360, 1280, 360);
        b.setColor(0.04f, 0.04f, 0.08f, 1f);
        b.draw(whitePixel, 0, 0,   1280, 360);
        b.setColor(Color.WHITE);
    }

    private static final Color GOLD = new Color(1.00f, 0.84f, 0.00f, 1f);

    private void drawTitle(SpriteBatch b) {
        game.font.getData().setScale(2.8f);
        game.font.setColor(Color.WHITE);
        game.font.draw(b, "ONLINE", 0, FIELD_Y + FIELD_H + 180f, 1280, Align.center, false);

        drawOrnamentalRule(b, 640f, FIELD_Y + FIELD_H + 70f, 320f);

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
    }

    /** Ornamental rule matching the MenuScreen style:
     *  small-diamond — line — centre-diamond — line — small-diamond */
    private void drawOrnamentalRule(SpriteBatch b, float cx, float y, float totalW) {
        float lineH  = 1.5f;
        float bigR   = 5f;
        float smallR = 3.5f;
        float halfW  = totalW / 2f;

        b.setColor(GOLD.r, GOLD.g, GOLD.b, 0.55f);
        b.draw(whitePixel, cx - halfW + smallR * 2f, y - lineH / 2f,
                halfW - smallR * 2f - bigR * 2f, lineH);
        b.draw(whitePixel, cx + bigR * 2f, y - lineH / 2f,
                halfW - smallR * 2f - bigR * 2f, lineH);

        b.setColor(GOLD.r, GOLD.g, GOLD.b, 0.90f);
        b.draw(whitePixel, cx - bigR,  y - bigR * 0.5f, bigR * 2f, bigR);
        b.draw(whitePixel, cx - 1.5f,  y - bigR,        3f,        bigR * 2f);

        b.setColor(GOLD.r, GOLD.g, GOLD.b, 0.65f);
        float ldx = cx - halfW;
        b.draw(whitePixel, ldx,                  y - smallR * 0.5f, smallR * 2f, smallR);
        b.draw(whitePixel, ldx + smallR * 0.35f, y - smallR,        1.5f,        smallR * 2f);

        float rdx = cx + halfW - smallR * 2f;
        b.draw(whitePixel, rdx,                  y - smallR * 0.5f, smallR * 2f, smallR);
        b.draw(whitePixel, rdx + smallR * 0.35f, y - smallR,        1.5f,        smallR * 2f);

        b.setColor(Color.WHITE);
    }

    private void drawIpEntry(SpriteBatch b) {
        // Label
        game.font.getData().setScale(0.65f);
        game.font.setColor(0.72f, 0.72f, 0.80f, 1f);
        game.font.draw(b, "ENTER SERVER IP ADDRESS", FIELD_X, FIELD_Y + FIELD_H + 24f);

        // Input field border
        b.setColor(Color.GOLD);
        b.draw(whitePixel, FIELD_X - 2, FIELD_Y - 2, FIELD_W + 4, FIELD_H + 4);
        b.setColor(0.10f, 0.10f, 0.16f, 1f);
        b.draw(whitePixel, FIELD_X, FIELD_Y, FIELD_W, FIELD_H);

        // IP text + cursor
        String display = serverIp + (System.currentTimeMillis() % 1000 < 500 ? "|" : " ");
        game.font.getData().setScale(0.85f);
        game.font.setColor(Color.WHITE);
        game.font.draw(b, display, FIELD_X + 12f, FIELD_Y + FIELD_H / 2f + 8f);

        // CONNECT button
        b.setColor(connectHovered
                ? new Color(0.22f, 0.22f, 0.38f, 1f)
                : new Color(0.12f, 0.12f, 0.20f, 1f));
        b.draw(whitePixel, BTN_X, BTN_Y, BTN_W, BTN_H);
        b.setColor(Color.GOLD);
        b.draw(whitePixel, BTN_X, BTN_Y, 4f, BTN_H);
        b.setColor(Color.WHITE);
        game.font.getData().setScale(0.80f);
        game.font.setColor(connectHovered ? Color.WHITE : new Color(0.80f, 0.80f, 0.85f, 1f));
        game.font.draw(b, "CONNECT", BTN_X, BTN_Y + BTN_H / 2f + 12f, BTN_W, 1, true);

        game.font.getData().setScale(0.45f);
        game.font.setColor(0.55f, 0.55f, 0.65f, 1f);
        game.font.draw(b, "or press ENTER", BTN_X, BTN_Y - 10f, BTN_W, 1, true);

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    private void drawConnecting(SpriteBatch b) {
        String dots = ".".repeat(dotCount);
        GlyphLayout gl = new GlyphLayout();
        game.font.getData().setScale(0.85f);
        game.font.setColor(Color.GOLD);
        String msg = "CONNECTING" + dots;
        gl.setText(game.font, msg);
        game.font.draw(b, msg, 640f - gl.width / 2f, FIELD_Y + 20f);
        game.font.setColor(0.60f, 0.60f, 0.70f, 1f);
        game.font.getData().setScale(0.55f);
        gl.setText(game.font, serverIp);
        game.font.draw(b, serverIp, 640f - gl.width / 2f, FIELD_Y - 14f);
        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
    }

    private void drawError(SpriteBatch b) {
        game.font.getData().setScale(0.75f);
        game.font.setColor(new Color(1f, 0.35f, 0.35f, 1f));
        game.font.draw(b, "CONNECTION FAILED", 640f - 100f, FIELD_Y + 30f);

        game.font.getData().setScale(0.52f);
        game.font.setColor(0.75f, 0.75f, 0.75f, 1f);
        game.font.draw(b, errorMessage, FIELD_X, FIELD_Y - 10f, FIELD_W, -1, true);

        b.setColor(connectHovered
                ? new Color(0.22f, 0.22f, 0.38f, 1f)
                : new Color(0.12f, 0.12f, 0.20f, 1f));
        b.draw(whitePixel, BTN_X, BTN_Y, BTN_W, BTN_H);
        b.setColor(new Color(1f, 0.35f, 0.35f, 1f));
        b.draw(whitePixel, BTN_X, BTN_Y, 4f, BTN_H);
        b.setColor(Color.WHITE);

        game.font.getData().setScale(0.80f);
        game.font.setColor(connectHovered ? Color.WHITE : new Color(0.80f, 0.80f, 0.85f, 1f));
        game.font.draw(b, "TRY AGAIN", BTN_X, BTN_Y + BTN_H / 2f + 12f, BTN_W, 1, true);

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    private void drawBackButton(SpriteBatch b) {
        b.setColor(backHovered
                ? new Color(0.18f, 0.18f, 0.28f, 1f)
                : new Color(0.10f, 0.10f, 0.16f, 1f));
        b.draw(whitePixel, backBounds.x, backBounds.y, backBounds.width, backBounds.height);
        b.setColor(backHovered ? Color.GOLD : new Color(0.45f, 0.45f, 0.55f, 1f));
        b.draw(whitePixel, backBounds.x, backBounds.y, 3f, backBounds.height);
        b.setColor(Color.WHITE);

        game.font.getData().setScale(0.65f);
        game.font.setColor(backHovered ? Color.WHITE : new Color(0.60f, 0.60f, 0.70f, 1f));
        game.font.draw(b, "← BACK", backBounds.x + 12f,
                backBounds.y + backBounds.height / 2f + 10f);
        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }
}