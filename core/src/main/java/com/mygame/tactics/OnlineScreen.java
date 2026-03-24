package com.mygame.tactics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
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

    private enum State { ENTERING_IP, CONNECTING, WAITING, ERROR }

    // -----------------------------------------------------------------------
    // Layout
    // -----------------------------------------------------------------------
    private static final float FIELD_W  = 500f;
    private static final float FIELD_H  = 60f;
    private static final float FIELD_X  = (1280f - FIELD_W) / 2f;
    private static final float FIELD_Y  = 360f;
    private static final float BTN_W    = 300f;
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

    private State  state        = State.ENTERING_IP;
    private String serverIp     = "";
    private String errorMessage = "";
    private float  dotTimer     = 0f; // drives the "..." animation while waiting
    private int    dotCount     = 0;
    private boolean btnHovered  = false;
    private boolean backHovered = false;

    private final Rectangle connectBounds = new Rectangle(BTN_X, BTN_Y, BTN_W, BTN_H);
    private final Rectangle backBounds    = new Rectangle(40f, 40f, 160f, 50f);

    // -----------------------------------------------------------------------
    // Constructor
    // -----------------------------------------------------------------------
    public OnlineScreen(Main game, NetworkClient client) {
        this.game   = game;
        this.client = client;

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
                state = State.WAITING;
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
            	if (msg.type == NetworkMessage.Type.ROOM_JOINED) {
                    // Match found — transition to the NETWORKED DraftScreen,
                    // passing the client and our assigned team number.
                    final int assignedTeam = msg.assignedTeam;
                    Gdx.app.postRunnable(() ->
                        game.setScreen(new DraftScreen(game, client, assignedTeam))
                    );
                } else if (msg.type == NetworkMessage.Type.OPPONENT_DISCONNECTED) {
                    state        = State.ERROR;
                    errorMessage = "Opponent disconnected before the game started.";
                }
            }
        });
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
                    // Enter key — attempt connect
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
        // Animate the waiting dots
        if (state == State.WAITING || state == State.CONNECTING) {
            dotTimer += delta;
            if (dotTimer >= 0.5f) {
                dotTimer = 0f;
                dotCount = (dotCount + 1) % 4;
            }
        }

        handleInput();

        ScreenUtils.clear(0.06f, 0.06f, 0.10f, 1f);
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        drawBackground(game.batch);
        drawTitle(game.batch);

        switch (state) {
            case ENTERING_IP: drawIpEntry(game.batch);  break;
            case CONNECTING:  drawConnecting(game.batch); break;
            case WAITING:     drawWaiting(game.batch);  break;
            case ERROR:       drawError(game.batch);    break;
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
 
        btnHovered  = connectBounds.contains(world.x, world.y);
        backHovered = backBounds.contains(world.x, world.y);
 
        if (!Gdx.input.justTouched()) return;
 
        // Back button — return to menu
        if (backBounds.contains(world.x, world.y)) {
            client.disconnect();
            game.setScreen(new MenuScreen(game));
            return;
        }
 
        // Connect button
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
        // Connect on a background thread so the UI doesn't freeze
        new Thread(() -> {
            try {
                client.connect(serverIp.trim());
                // onConnected() callback will set state to WAITING
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

    private void drawTitle(SpriteBatch b) {
        b.setColor(Color.GOLD);
        b.draw(whitePixel, FIELD_X, FIELD_Y + FIELD_H + 80f, FIELD_W, 3f);
        b.setColor(Color.WHITE);

        String title = "ONLINE";
        game.font.getData().setScale(2.8f);
        game.font.setColor(Color.WHITE);
        game.font.draw(b, title, 640f - title.length() * 19f, FIELD_Y + FIELD_H + 72f);

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
    }

    private void drawIpEntry(SpriteBatch b) {
        // Label
        game.font.getData().setScale(0.65f);
        game.font.setColor(0.72f, 0.72f, 0.80f, 1f);
        game.font.draw(b, "ENTER SERVER IP ADDRESS", FIELD_X, FIELD_Y + FIELD_H + 20f);

        // Input field border
        b.setColor(Color.GOLD);
        b.draw(whitePixel, FIELD_X - 2, FIELD_Y - 2, FIELD_W + 4, FIELD_H + 4);
        b.setColor(0.10f, 0.10f, 0.16f, 1f);
        b.draw(whitePixel, FIELD_X, FIELD_Y, FIELD_W, FIELD_H);

        // IP text + cursor — centred vertically inside the field
        String display = serverIp + (System.currentTimeMillis() % 1000 < 500 ? "|" : " ");
        game.font.getData().setScale(0.85f);
        game.font.setColor(Color.WHITE);
        game.font.draw(b, display, FIELD_X + 12f, FIELD_Y + FIELD_H / 2f + 8f);

        // Connect button
        b.setColor(btnHovered
                ? new Color(0.22f, 0.22f, 0.38f, 1f)
                : new Color(0.12f, 0.12f, 0.20f, 1f));
        b.draw(whitePixel, BTN_X, BTN_Y, BTN_W, BTN_H);
        b.setColor(Color.GOLD);
        b.draw(whitePixel, BTN_X, BTN_Y, 4f, BTN_H);
        b.setColor(Color.WHITE);

        game.font.getData().setScale(0.80f);
        game.font.setColor(btnHovered ? Color.WHITE : new Color(0.80f, 0.80f, 0.85f, 1f));
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
        game.font.getData().setScale(0.85f);
        game.font.setColor(Color.GOLD);
        String msg = "CONNECTING" + dots;
        game.font.draw(b, msg, 640f - msg.length() * 5.5f, FIELD_Y + 20f);
        game.font.setColor(0.60f, 0.60f, 0.70f, 1f);
        game.font.getData().setScale(0.55f);
        game.font.draw(b, serverIp, 640f - serverIp.length() * 3.5f, FIELD_Y - 14f);
        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
    }

    private void drawWaiting(SpriteBatch b) {
        String dots = ".".repeat(dotCount);
        game.font.getData().setScale(0.85f);
        game.font.setColor(Color.GOLD);
        String msg = "SEARCHING FOR OPPONENT" + dots;
        game.font.draw(b, msg, 640f - msg.length() * 5.5f, FIELD_Y + 20f);
        game.font.setColor(0.60f, 0.60f, 0.70f, 1f);
        game.font.getData().setScale(0.55f);
        String sub = "Connected to " + serverIp + " — waiting for a match";
        game.font.draw(b, sub, 640f - sub.length() * 3.5f, FIELD_Y - 14f);
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

        // Retry button
        b.setColor(btnHovered
                ? new Color(0.22f, 0.22f, 0.38f, 1f)
                : new Color(0.12f, 0.12f, 0.20f, 1f));
        b.draw(whitePixel, BTN_X, BTN_Y, BTN_W, BTN_H);
        b.setColor(new Color(1f, 0.35f, 0.35f, 1f));
        b.draw(whitePixel, BTN_X, BTN_Y, 4f, BTN_H);
        b.setColor(Color.WHITE);

        game.font.getData().setScale(0.80f);
        game.font.setColor(btnHovered ? Color.WHITE : new Color(0.80f, 0.80f, 0.85f, 1f));
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