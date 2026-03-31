package com.mygame.tactics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import java.util.HashMap;
import java.util.Map;
import com.mygame.tactics.characters.Aevan;
import com.mygame.tactics.characters.Aaron;
import com.mygame.tactics.characters.Anna;
import com.mygame.tactics.characters.Billy;
import com.mygame.tactics.characters.Brad;
import com.mygame.tactics.characters.Emily;
import com.mygame.tactics.characters.Evan;
import com.mygame.tactics.characters.Ghia;
import com.mygame.tactics.characters.GuardTower;
import com.mygame.tactics.characters.Hunter;
import com.mygame.tactics.characters.Jaxon;
import com.mygame.tactics.characters.Lark;
import com.mygame.tactics.characters.Luke;
import com.mygame.tactics.characters.Mason;
import com.mygame.tactics.characters.Maxx;
import com.mygame.tactics.characters.Nathan;
import com.mygame.tactics.characters.Sean;
import com.mygame.tactics.characters.Snowguard;
import com.mygame.tactics.characters.Speen;
import com.mygame.tactics.characters.Stoneguard;
import com.mygame.tactics.characters.Thomas;
import com.mygame.tactics.characters.Tyler;
import com.mygame.tactics.characters.Weirdguard;
import com.mygame.tactics.characters.Ben;
import com.mygame.tactics.characters.Fescue;
import com.mygame.tactics.network.NetworkClient;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;

/**
 * Draft screen — works in both local and online modes.
 *
 * LOCAL MODE  (client == null):
 *   Identical behaviour to the original DraftScreen. Both teams pick on the
 *   same machine, then the board is chosen and CombatScreen is launched.
 *
 * ONLINE MODE (client != null):
 *   - The server is the authority on pick order and pool state.
 *   - When it is MY turn (myTeam == server's pickingTeam) the UI is active and
 *     clicking a card sends a DraftPickAction to the server.
 *   - When it is NOT my turn the pool is greyed out and input is blocked.
 *   - After the server sends DRAFT_COMPLETE, only Team 1 sees the board
 *     selection screen. Team 2 sees a "Waiting for opponent..." overlay until
 *     the server broadcasts ACTION_RESULT with the initial battle state.
 *   - On GAME_OVER (opponent disconnected during draft) we return to the menu.
 */
public class DraftScreen implements Screen {

    // -----------------------------------------------------------------------
    // Constants
    // -----------------------------------------------------------------------
    private static final int PICKS_PER_TEAM = 4;
    private static final int TOTAL_PICKS    = PICKS_PER_TEAM * 2;

    private static final int   CARD_W   = 100;
    private static final int   CARD_H   = 130;
    private static final int   CARD_PAD = 10;
    private static final int   COLS     = 5;

    private static final float GRID_X     = 300f;
    private static final float GRID_W     = 660f;
    private static final float GRID_Y_TOP = 638f;
    private static final float GRID_Y_BOT = 110f;
    private static final float PANEL_W    = 290f;

    private static final float SCROLL_W     = 14f;
    private static final float SCROLL_X     = GRID_X + GRID_W - SCROLL_W - 4f;
    private static final float SCROLL_MIN_H = 40f;

    private static final float BOARD_CARD_W   = 300f;
    private static final float BOARD_CARD_H   = 400f;
    private static final float BOARD_CARD_PAD = 40f;

    private static final String[] BOARD_NAMES = { "FOREST", "WIND", "DESERT" };
    private static final String[] BOARD_DESCS = {
        "The classic arena. The board collapses ring by ring toward the Haven.",
        "The outer ring starts destroyed. Each cycle pushes all characters in a random direction.",
        "Danger lurks with every step. The tile farthest from the Haven collapses after each move."
    };

    // -----------------------------------------------------------------------
    // Core fields
    // -----------------------------------------------------------------------
    private final Main             game;
    private final NetworkClient    client;       // null in local mode
    private final int              myTeam;       // 0 in local mode; 1 or 2 online
    private final boolean          isRanked;     // true in ranked online mode
    private final int              roundNumber;  // current round (1–3) in ranked; 0 otherwise
    private       int              team1RoundWins;
    private       int              team2RoundWins;
    private final String[]         teamNames;    // {"Team 1", "Team 2"} by default

    private final OrthographicCamera camera;
    private final FitViewport        viewport;
    private final Texture            whitePixel;
    private final Map<Enums.CharType,  Texture> typeIcons  = new HashMap<>();
    private final Map<Enums.CharClass, Texture> classIcons = new HashMap<>();

    // -----------------------------------------------------------------------
    // Draft state
    // -----------------------------------------------------------------------
    /** Full character pool — rendered as cards. Shrinks as picks are made. */
    private final Array<Character> pool  = new Array<>();
    private final Array<Character> team1 = new Array<>();
    private final Array<Character> team2 = new Array<>();

    private int     pickingTeam  = 1;
    private int     picksMade    = 0;
    private int     hoveredIndex = -1;

    // Bot draft — used in local single-player; team 2 picks randomly after a short delay
    private boolean botDraft      = false;
    private float   botDraftTimer = 0f;

    // Combat cave mode — team2 and config come from a loaded combat board file
    private Array<Character> presetTeam2    = null;
    private BoardConfig      presetConfig   = null;
    private Runnable         onCombatComplete = null;

    // -----------------------------------------------------------------------
    // Scroll state
    // -----------------------------------------------------------------------
    private float   scrollOffset    = 0f;
    private float   maxScroll       = 0f;
    private float   scrollAmount    = 0f;
    private boolean isDraggingScroll = false;
    private float   dragStartY       = 0f;
    private float   dragStartOffset  = 0f;

    private final Rectangle       confirmBounds = new Rectangle(1280 - PANEL_W + 10, 20, PANEL_W - 20, 70);
    private final Array<Rectangle> cardBounds   = new Array<>();

    // -----------------------------------------------------------------------
    // Board selection state
    // -----------------------------------------------------------------------
    private boolean    boardSelectPhase = false;
    private int        hoveredBoard     = -1;
    private final BoardConfig[]   boardOptions    = {
        BoardConfig.forest(), BoardConfig.wind(), BoardConfig.desert()
    };
    private final Rectangle[] boardCardBounds = new Rectangle[3];

    // -----------------------------------------------------------------------
    // Online-only overlay state
    // -----------------------------------------------------------------------
    /** True while waiting for the opponent to pick (online mode only). */
    private boolean waitingForOpponent = false;

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------

    /** Local mode — no network. */
    public DraftScreen(Main game) {
        this(game, null, 0, false, 0, 0, 0, null, new String[]{"Team 1", "Team 2"});
    }

    /** Combat cave mode — player drafts team1; team2 and board config come from the combat file. */
    public DraftScreen(Main game, Array<Character> presetTeam2, BoardConfig presetConfig,
                       Runnable onCombatComplete) {
        this(game, null, 0, false, 0, 0, 0, null, new String[]{"Team 1", "Team 2"});
        this.presetTeam2       = presetTeam2;
        this.presetConfig      = presetConfig;
        this.botDraft          = false;
        this.onCombatComplete  = onCombatComplete;
    }

    /** Online casual mode — pass the connected NetworkClient and this player's team. */
    public DraftScreen(Main game, NetworkClient client, int myTeam) {
        this(game, client, myTeam, false, 0, 0, 0, null, new String[]{"Team 1", "Team 2"});
    }

    /** Online mode (casual or ranked, no pre-filtered pool). */
    public DraftScreen(Main game, NetworkClient client, int myTeam,
                       boolean isRanked, int roundNumber, int t1Wins, int t2Wins) {
        this(game, client, myTeam, isRanked, roundNumber, t1Wins, t2Wins, null, new String[]{"Team 1", "Team 2"});
    }

    /** Online mode — serverPool pre-filters the local pool for ranked round 2+. */
    public DraftScreen(Main game, NetworkClient client, int myTeam,
                       boolean isRanked, int roundNumber, int t1Wins, int t2Wins,
                       Array<String> serverPool) {
        this(game, client, myTeam, isRanked, roundNumber, t1Wins, t2Wins, serverPool, new String[]{"Team 1", "Team 2"});
    }

    /** Full constructor — accepts custom team display names. */
    public DraftScreen(Main game, NetworkClient client, int myTeam,
                       boolean isRanked, int roundNumber, int t1Wins, int t2Wins,
                       Array<String> serverPool, String[] teamNames) {
        this.game            = game;
        this.client          = client;
        this.myTeam          = myTeam;
        this.isRanked        = isRanked;
        this.roundNumber     = roundNumber;
        this.team1RoundWins  = t1Wins;
        this.team2RoundWins  = t2Wins;
        this.teamNames       = teamNames;
        this.botDraft        = (client == null); // local single-player: bot picks for team 2

        camera   = new OrthographicCamera();
        viewport = new FitViewport(1280, 720, camera);
        camera.position.set(640, 360, 0);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();

        for (Enums.CharType t : Enums.CharType.values()) {
            String path = t.name().toLowerCase() + "_icon.png";
            try { typeIcons.put(t, new Texture(path)); } catch (Exception ignored) {}
        }
        for (Enums.CharClass cl : Enums.CharClass.values()) {
            String path = cl.name().toLowerCase() + "_icon.png";
            try { classIcons.put(cl, new Texture(path)); } catch (Exception ignored) {}
        }

        for (int i = 0; i < 3; i++) boardCardBounds[i] = new Rectangle();

        buildPool();

        // Pre-filter the local pool using the server's authoritative available list.
        // This ensures locked characters from prior rounds are never shown, even
        // before the RequestDraftStateAction response arrives.
        if (serverPool != null && serverPool.size > 0) {
            for (int i = pool.size - 1; i >= 0; i--) {
                if (!serverPool.contains(pool.get(i).getName(), false)) {
                    pool.removeIndex(i);
                }
            }
        }

        recalcMaxScroll();

        // Wire up network listener if online
        if (client != null) {
            client.setListener(new NetworkClient.MessageListener() {
                @Override public void onConnected() {}
                @Override public void onDisconnected() {
                    Gdx.app.postRunnable(() -> game.setScreen(new MenuScreen(game)));
                }
                @Override public void onMessage(NetworkMessage msg) {
                    Gdx.app.postRunnable(() -> handleServerMessage(msg));
                }
            });
            if (isRanked) {
                // Both teams wait until the server confirms pick order.
                // The pool is already correctly filtered via serverPool above.
                waitingForOpponent = true;
                client.sendAction(new Action.RequestDraftStateAction(myTeam));
            } else {
                // Casual: team 2 waits for team 1's first pick
                waitingForOpponent = (myTeam == 2);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Pool
    // -----------------------------------------------------------------------
    private void buildPool() {
        pool.add(new Hunter    (new Texture("hunter.png")));
        pool.add(new Sean      (new Texture("sean.png")));
        pool.add(new Jaxon     (new Texture("jaxon.png")));
        pool.add(new Evan      (new Texture("evan.png")));
        pool.add(new Billy     (new Texture("billy.png")));
        pool.add(new Aaron     (new Texture("aaron.png")));
        pool.add(new Speen     (new Texture("speen.png")));
        pool.add(new Mason     (new Texture("mason.png")));
        pool.add(new Lark      (new Texture("lark.png")));
        pool.add(new Nathan    (new Texture("nathan.png")));
        pool.add(new Luke      (new Texture("luke.png")));
        pool.add(new Brad      (new Texture("brad.png")));
        pool.add(new GuardTower(new Texture("guardtower.png")));
        pool.add(new Weirdguard(new Texture("weirdguard.png")));
        pool.add(new Stoneguard(new Texture("stoneguard.png")));
        pool.add(new Snowguard (new Texture("snowguard.png")));
        pool.add(new Tyler     (new Texture("tyler.png")));
        pool.add(new Anna      (new Texture("anna.png")));
        pool.add(new Emily     (new Texture("emily.png")));
        pool.add(new Thomas    (new Texture("thomas.png")));
        pool.add(new Ghia      (new Texture("ghia.png")));
        pool.add(new Maxx      (new Texture("maxx.png")));
        pool.add(new Ben       (new Texture("ben.png")));
        pool.add(new Aevan       (new Texture("aevan.png")));
        pool.add(new Fescue      (new Texture("fescue.png")));
    }

    /** Looks up a Character in the local pool by name. */
    private Character findInPool(String name) {
        for (int i = 0; i < pool.size; i++)
            if (pool.get(i).getName().equals(name)) return pool.get(i);
        return null;
    }

    // -----------------------------------------------------------------------
    // Scroll helpers
    // -----------------------------------------------------------------------
    private void recalcMaxScroll() {
        int   rows     = (int) Math.ceil((double) pool.size / COLS);
        float totalH   = rows * (CARD_H + CARD_PAD) - CARD_PAD;
        float visibleH = GRID_Y_TOP - GRID_Y_BOT;
        maxScroll    = Math.max(0f, totalH - visibleH);
        scrollOffset = Math.min(scrollOffset, maxScroll);
    }

    private void rebuildCardBounds() {
        cardBounds.clear();
        float cellW     = CARD_W + CARD_PAD;
        float cellH     = CARD_H + CARD_PAD;
        float totalRowW = COLS * CARD_W + (COLS - 1) * CARD_PAD;
        float rowStartX = GRID_X + (GRID_W - totalRowW) / 2f;
        for (int i = 0; i < pool.size; i++) {
            int col = i % COLS, row = i / COLS;
            float x = rowStartX + col * cellW;
            float y = GRID_Y_TOP - row * cellH - CARD_H + scrollOffset;
            cardBounds.add(new Rectangle(x, y, CARD_W, CARD_H));
        }
    }

    // -----------------------------------------------------------------------
    // Screen lifecycle
    // -----------------------------------------------------------------------
    @Override
    public void show() {
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override public boolean scrolled(float amountX, float amountY) {
                scrollAmount += amountY * 30f;
                return true;
            }
        });
    }

    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   { Gdx.input.setInputProcessor(null); }

    @Override
    public void render(float delta) {
        ScreenUtils.clear(0.08f, 0.08f, 0.12f, 1f);
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        if (boardSelectPhase) {
            handleBoardInput();
            drawBoardSelection(game.batch);
            // Team 2 online: board select phase means waiting for team 1's choice
            if (isOnline() && myTeam == 2) drawWaitingOverlay(game.batch, "Waiting for opponent to choose board...");
        } else {
            // Only accept local input when it is my turn (or in local mode)
            if (!waitingForOpponent) {
                if (botDraft && pickingTeam == 2) tickBotDraft(delta);
                else handleInput();
            }
            scrollOffset = Math.max(0f, Math.min(maxScroll, scrollOffset));
            rebuildCardBounds();
            drawBackground(game.batch);
            drawTeamRosters(game.batch);
            drawRoundWins(game.batch);
            drawPoolGrid(game.batch);
            drawScrollbar(game.batch);
            drawAbilityPanel(game.batch);
            drawPickPrompt(game.batch);
            drawConfirmButton(game.batch);
            if (waitingForOpponent) {
                String waitMsg = (isRanked && picksMade == TOTAL_PICKS)
                        ? "Draft complete — waiting for battle to start..."
                        : "Waiting for opponent to pick...";
                drawWaitingOverlay(game.batch, waitMsg);
            }
        }

        game.batch.end();
    }

    @Override public void resize(int w, int h) { viewport.update(w, h); }

    @Override
    public void dispose() {
        whitePixel.dispose();
        for (Texture t : typeIcons.values())  t.dispose();
        for (Texture t : classIcons.values()) t.dispose();
        for (int i = 0; i < pool.size; i++) {
            Texture t = pool.get(i).getPortrait();
            if (t != null) t.dispose();
        }
    }

    // -----------------------------------------------------------------------
    // Network message handling (online mode)
    // -----------------------------------------------------------------------
    private void handleServerMessage(NetworkMessage msg) {
        switch (msg.type) {

            case DRAFT_UPDATE:
                // Sync pool and rosters to the server's authoritative state
                applyDraftUpdate(msg);
                // It's my turn if the server says pickingTeam == myTeam
                waitingForOpponent = (msg.pickingTeam != myTeam);
                break;

            case DRAFT_COMPLETE:
                // All picks done — apply final roster state
                applyDraftUpdate(msg);
                if (isRanked) {
                    // In ranked mode the server auto-picks the map — just wait for ACTION_RESULT
                    waitingForOpponent = true;
                    boardSelectPhase   = false;
                } else if (myTeam == 1) {
                    // Casual: only team 1 selects the board
                    boardSelectPhase   = true;
                    waitingForOpponent = false;
                } else {
                    waitingForOpponent = true;
                    boardSelectPhase   = true; // triggers "waiting for board" overlay
                }
                break;

            case ACTION_RESULT:
                // Server has started the battle — launch CombatScreen in online mode.
                // Pass the local team rosters (which have portrait textures loaded) so
                // CombatScreen can render characters correctly from the first frame.
                if (msg.gameState != null) {
                    game.setScreen(new CombatScreen(game, msg.gameState,
                            team1, team2, client, myTeam, isRanked,
                            roundNumber, team1RoundWins, team2RoundWins, teamNames));
                }
                break;

            case ACTION_REJECTED:
                // Our pick was rejected by the server (e.g. character no longer available).
                // Re-enable picking so the player can choose again.
                waitingForOpponent = (pickingTeam != myTeam);
                break;

            case GAME_OVER:
                // Opponent disconnected during draft
                game.setScreen(new MenuScreen(game));
                break;

            default:
                break;
        }
    }

    /**
     * Rebuilds the local pool and rosters from a DRAFT_UPDATE / DRAFT_COMPLETE message.
     * The server is authoritative — we match its state exactly.
     */
    private void applyDraftUpdate(NetworkMessage msg) {
        // Move picked characters from pool to rosters
        syncRoster(team1, msg.team1Picks, 1);
        syncRoster(team2, msg.team2Picks, 2);

        // Sync local pool to the server's authoritative remaining pool.
        // This removes already-picked characters AND characters locked from
        // prior ranked rounds (which are missing from msg.remainingPool).
        for (int i = pool.size - 1; i >= 0; i--) {
            if (!msg.remainingPool.contains(pool.get(i).getName(), false)) {
                pool.removeIndex(i);
            }
        }

        pickingTeam = msg.pickingTeam;
        picksMade   = msg.team1Picks.size + msg.team2Picks.size;
        recalcMaxScroll();
        hoveredIndex = -1;
    }

    /**
     * Ensures the local roster array matches the server's pick list.
     * Only adds characters that aren't already present.
     */
    private void syncRoster(Array<Character> roster, Array<String> serverPicks, int team) {
        for (String name : serverPicks) {
            boolean alreadyHave = false;
            for (Character c : roster)
                if (c.getName().equals(name)) { alreadyHave = true; break; }
            if (!alreadyHave) {
                // Find in pool and move to roster
                for (int i = pool.size - 1; i >= 0; i--) {
                    if (pool.get(i).getName().equals(name)) {
                        Character c = pool.removeIndex(i);
                        c.team = team;
                        roster.add(c);
                        break;
                    }
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    // Input — draft phase
    // -----------------------------------------------------------------------
    private void handleInput() {
        Vector3 world = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));

        // Scroll wheel
        if (scrollAmount != 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset + scrollAmount));
            scrollAmount = 0;
        }

        // Scrollbar drag
        if (maxScroll > 0) {
            float trackX = SCROLL_X, trackY = GRID_Y_BOT;
            float trackH = GRID_Y_TOP - GRID_Y_BOT;
            int   rows       = (int) Math.ceil((double) pool.size / COLS);
            float totalH     = rows * (CARD_H + CARD_PAD) - CARD_PAD;
            float thumbRatio = Math.min(1f, trackH / totalH);
            float thumbH     = Math.max(SCROLL_MIN_H, trackH * thumbRatio);
            float scrollable = trackH - thumbH;
            float scrollProgress = (maxScroll > 0) ? scrollOffset / maxScroll : 0f;
            float thumbY = trackY + (1f - scrollProgress) * scrollable;

            if (Gdx.input.isTouched()) {
                if (!isDraggingScroll && Gdx.input.justTouched()) {
                    if (world.x >= trackX && world.x <= trackX + SCROLL_W
                            && world.y >= trackY && world.y <= trackY + trackH) {
                        isDraggingScroll = true;
                        dragStartY       = world.y;
                        dragStartOffset  = scrollOffset;
                        if (world.y < thumbY || world.y > thumbY + thumbH) {
                            float progress = 1f - ((world.y - trackY - thumbH / 2f) / scrollable);
                            scrollOffset    = Math.max(0, Math.min(maxScroll, progress * maxScroll));
                            dragStartOffset = scrollOffset;
                        }
                    }
                } else if (isDraggingScroll) {
                    float d = dragStartY - world.y;
                    if (scrollable > 0)
                        scrollOffset = Math.max(0, Math.min(maxScroll,
                                dragStartOffset + d * (maxScroll / scrollable)));
                }
            } else {
                isDraggingScroll = false;
            }
        }

        if (!Gdx.input.justTouched()) return;
        if (isDraggingScroll) return;

        // Confirm button
        if (hoveredIndex >= 0 && confirmBounds.contains(world.x, world.y)) {
            confirmPick();
            return;
        }

        // Card selection
        hoveredIndex = -1;
        for (int i = 0; i < cardBounds.size; i++) {
            Rectangle r = cardBounds.get(i);
            if (r.contains(world.x, world.y)
                    && r.y >= GRID_Y_BOT && r.y + r.height <= GRID_Y_TOP) {
                hoveredIndex = i;
                break;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Input — board selection phase
    // -----------------------------------------------------------------------
    private void handleBoardInput() {
        // Online team 2 never drives board selection
        if (isOnline() && myTeam == 2) return;

        Vector3 world = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
        hoveredBoard = -1;
        for (int i = 0; i < 3; i++)
            if (boardCardBounds[i].contains(world.x, world.y)) { hoveredBoard = i; break; }

        if (!Gdx.input.justTouched()) return;
        if (hoveredBoard >= 0) launchCombat(boardOptions[hoveredBoard]);
    }

    // -----------------------------------------------------------------------
    // Pick logic
    // -----------------------------------------------------------------------
    private void confirmPick() {
        if (hoveredIndex < 0 || hoveredIndex >= pool.size) return;

        if (isOnline()) {
            // Send the pick to the server — don't mutate local state yet.
            // The server will echo back a DRAFT_UPDATE which we apply in
            // handleServerMessage(), keeping everything in sync.
            String name = pool.get(hoveredIndex).getName();
            client.sendAction(new Action.DraftPickAction(myTeam, name));
            hoveredIndex       = -1;
            waitingForOpponent = true; // block input until server confirms
        } else {
            // Local mode — mutate directly as before
            Character picked = pool.removeIndex(hoveredIndex);
            picked.team = pickingTeam;
            if (pickingTeam == 1) team1.add(picked);
            else                  team2.add(picked);
            picksMade++;
            hoveredIndex = -1;
            pickingTeam  = (presetTeam2 != null) ? 1 : ((pickingTeam == 1) ? 2 : 1);
            recalcMaxScroll();
            int totalPicks = (presetTeam2 != null) ? PICKS_PER_TEAM : TOTAL_PICKS;
            if (picksMade == totalPicks) {
                if (presetTeam2 != null) launchCombat(presetConfig);
                else boardSelectPhase = true;
            }
        }
    }

    /** Bot auto-pick: after a short delay, choose a random card from the pool. */
    private void tickBotDraft(float delta) {
        botDraftTimer += delta;
        if (botDraftTimer >= 0.6f) {
            botDraftTimer = 0f;
            if (pool.size > 0) {
                int idx = com.badlogic.gdx.math.MathUtils.random(pool.size - 1);
                Character picked = pool.removeIndex(idx);
                picked.team = 2;
                team2.add(picked);
                picksMade++;
                hoveredIndex = -1;
                pickingTeam  = 1;
                recalcMaxScroll();
                if (picksMade == TOTAL_PICKS) boardSelectPhase = true;
            }
        }
    }

    private void launchCombat(BoardConfig config) {
        if (isOnline()) {
            // Tell the server which board was chosen — it will start the battle
            // and send an ACTION_RESULT with the initial GameState to both clients.
            client.sendAction(new Action.BoardChoiceAction(myTeam, config.type));
            // Show "waiting" overlay while the server spins up the battle
            waitingForOpponent = true;
            boardSelectPhase   = false;
        } else {
            Array<Character> t2 = (presetTeam2 != null) ? presetTeam2 : team2;
            game.setScreen(new CombatScreen(game, team1, t2, config, teamNames, true, onCombatComplete));
        }
    }

    // -----------------------------------------------------------------------
    // Drawing — draft phase
    // -----------------------------------------------------------------------
    /**
     * Draws 2 small win-indicator boxes in each sidebar panel (ranked only).
     * Filled = won that round; unfilled = not yet won.
     */
    private void drawRoundWins(SpriteBatch b) {
        if (!isRanked) return;
        float boxSize = 16f, boxGap = 5f, boxY = 693f;

        // Team 1 (left panel) — right-aligned against the panel's inner edge
        for (int i = 0; i < 2; i++) {
            float bx = (PANEL_W - 12f - boxSize) - (1 - i) * (boxSize + boxGap);
            if (i < team1RoundWins) b.setColor(Color.CYAN);
            else                    b.setColor(0.05f, 0.22f, 0.22f, 1f);
            b.draw(whitePixel, bx, boxY, boxSize, boxSize);
        }

        // Team 2 (right panel) — right-aligned against the screen's right edge
        for (int i = 0; i < 2; i++) {
            float bx = (1280f - 12f - boxSize) - (1 - i) * (boxSize + boxGap);
            if (i < team2RoundWins) b.setColor(Color.SALMON);
            else                    b.setColor(0.25f, 0.08f, 0.06f, 1f);
            b.draw(whitePixel, bx, boxY, boxSize, boxSize);
        }

        b.setColor(Color.WHITE);
    }

    private void drawBackground(SpriteBatch b) {
        b.setColor(0.10f, 0.10f, 0.16f, 1f);
        b.draw(whitePixel, 0,              0, PANEL_W, 720);
        b.draw(whitePixel, 1280 - PANEL_W, 0, PANEL_W, 720);
        b.setColor(0.07f, 0.07f, 0.10f, 1f);
        b.draw(whitePixel, GRID_X, 0, GRID_W, 720);
        b.setColor(Color.WHITE);
    }

    private void drawTeamRosters(SpriteBatch b) {
        game.font.getData().setScale(0.8f);
        game.font.setColor(Color.CYAN);
        game.font.draw(b, teamNames[0].toUpperCase(), 15, 705);

        float t1Y = 658f;
        for (Character c : team1)                          { drawRosterEntry(b, c, 8,           t1Y, Color.CYAN);   t1Y -= 152f; }
        for (int i = team1.size; i < PICKS_PER_TEAM; i++) { drawEmptySlot  (b,    8,           t1Y, Color.CYAN);   t1Y -= 152f; }

        float px = 1280 - PANEL_W;
        game.font.setColor(Color.SALMON);
        game.font.draw(b, teamNames[1].toUpperCase(), px + 15, 705);

        float t2Y = 658f;
        for (Character c : team2)                          { drawRosterEntry(b, c, px + 5, t2Y, Color.SALMON); t2Y -= 152f; }
        for (int i = team2.size; i < PICKS_PER_TEAM; i++) { drawEmptySlot  (b,    px + 5, t2Y, Color.SALMON); t2Y -= 152f; }

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
    }

    private void drawRosterEntry(SpriteBatch b, Character c, float x, float y, Color tc) {
        float w = PANEL_W - 18f, h = 140f;
        b.setColor(tc.r * 0.18f, tc.g * 0.18f, tc.b * 0.18f, 1f);
        b.draw(whitePixel, x, y - h, w, h);
        b.setColor(tc);
        b.draw(whitePixel, x, y - h, 4f, h);
        b.setColor(Color.WHITE);
        if (c.getPortrait() != null) b.draw(c.getPortrait(), x + 10, y - h + 18, 68, 68);
        game.font.setColor(tc);
        game.font.getData().setScale(0.65f);
        game.font.draw(b, c.getName().toUpperCase(), x + 88, y - 8f);
        game.font.setColor(classColor(c));
        game.font.getData().setScale(0.52f);
        game.font.draw(b, c.getCharClass().name(), x + 88, y - 28f);
        game.font.setColor(Color.WHITE);
        game.font.getData().setScale(0.50f);
        game.font.draw(b, "HP "  + c.getMaxHealth() + "  ATK " + c.getAtk(),   x + 88, y - 52f);
        game.font.draw(b, "MAG " + c.getMag()       + "  ARM " + c.getArmor(), x + 88, y - 70f);
        game.font.draw(b, "CLK " + c.getCloak()     + "  SPD " + c.getSpeed(), x + 88, y - 88f);

        // Class + Type icons — right-aligned in the name row
        float iconSize  = 20f;
        float iconRight = x + w - 4f;
        float iconY     = y - 22f;
        Texture classIcon = classIcons.get(c.getCharClass());
        Texture typeIcon  = typeIcons.get(c.getCharType());
        b.setColor(Color.WHITE);
        if (classIcon != null) {
            b.draw(classIcon, iconRight - iconSize, iconY, iconSize, iconSize);
            iconRight -= iconSize + 3f;
        }
        if (typeIcon != null) {
            b.draw(typeIcon, iconRight - iconSize, iconY, iconSize, iconSize);
        }
    }

    private void drawEmptySlot(SpriteBatch b, float x, float y, Color tc) {
        float w = PANEL_W - 18f, h = 140f;
        b.setColor(tc.r * 0.06f, tc.g * 0.06f, tc.b * 0.06f, 1f);
        b.draw(whitePixel, x, y - h, w, h);
        b.setColor(tc.r, tc.g, tc.b, 0.18f);
        b.draw(whitePixel, x,         y - h,      w,  2f);
        b.draw(whitePixel, x,         y - 2f,     w,  2f);
        b.draw(whitePixel, x,         y - h,      2f, h);
        b.draw(whitePixel, x + w - 2, y - h,      2f, h);
        game.font.setColor(tc.r, tc.g, tc.b, 0.28f);
        game.font.getData().setScale(0.55f);
        game.font.draw(b, "— EMPTY —", x + w / 2f - 36f, y - h / 2f + 8f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    private void drawPoolGrid(SpriteBatch b) {
        // Dim the grid when it is not this player's turn (online mode)
        boolean myTurn = !isOnline() || (pickingTeam == myTeam);
        Color tc = myTurn
                ? ((pickingTeam == 1) ? Color.CYAN : Color.SALMON)
                : Color.GRAY;

        b.setColor(tc.r, tc.g, tc.b, 0.30f);
        b.draw(whitePixel, GRID_X, GRID_Y_TOP + 2, GRID_W, 2f);
        b.setColor(0.07f, 0.07f, 0.10f, 1f);
        b.draw(whitePixel, GRID_X, GRID_Y_TOP, GRID_W, 720 - GRID_Y_TOP);
        b.draw(whitePixel, GRID_X, 0,           GRID_W, GRID_Y_BOT);
        b.setColor(Color.WHITE);

        for (int i = 0; i < pool.size; i++) {
            Character c      = pool.get(i);
            Rectangle bounds = cardBounds.get(i);
            boolean   sel    = (i == hoveredIndex) && myTurn;

            if (bounds.y + bounds.height < GRID_Y_BOT || bounds.y > GRID_Y_TOP) continue;

            if (sel) {
                b.setColor(tc);
                b.draw(whitePixel, bounds.x - 3, bounds.y - 3, bounds.width + 6, bounds.height + 6);
            }

            b.setColor(myTurn
                    ? (sel ? new Color(0.22f, 0.22f, 0.42f, 1f) : new Color(0.13f, 0.13f, 0.20f, 1f))
                    : new Color(0.09f, 0.09f, 0.12f, 1f));
            b.draw(whitePixel, bounds.x, bounds.y, bounds.width, bounds.height);

            float ps = bounds.width - 12f;
            b.setColor(myTurn ? Color.WHITE : new Color(0.5f, 0.5f, 0.5f, 1f));
            if (c.getPortrait() != null)
                b.draw(c.getPortrait(), bounds.x + 6, bounds.y + bounds.height - ps - 6, ps, ps);

            String name = c.getName().toUpperCase();
            float nameScale = name.length() > 8 ? 0.36f : 0.45f;
            game.font.getData().setScale(nameScale);
            game.font.setColor(sel ? Color.WHITE : new Color(0.85f, 0.85f, 0.85f, myTurn ? 1f : 0.4f));
            game.font.draw(b, name, bounds.x, bounds.y + 28f, bounds.width, 1, true);

            game.font.getData().setScale(0.38f);
            game.font.setColor(myTurn ? classColor(c) : Color.DARK_GRAY);
            game.font.draw(b, c.getCharClass().name(), bounds.x, bounds.y + 14f, bounds.width, 1, true);

            game.font.getData().setScale(1.0f);
            game.font.setColor(Color.WHITE);
        }
    }

    private void drawPickPrompt(SpriteBatch b) {
        boolean myTurn = !isOnline() || (pickingTeam == myTeam);
        Color tc = myTurn
                ? ((pickingTeam == 1) ? Color.CYAN : Color.SALMON)
                : Color.GRAY;

        float panelX = GRID_X + 10f, panelW = GRID_W - 20f;
        float panelH = 60f, panelY = GRID_Y_TOP + 8f;

        b.setColor(Color.GOLD);
        b.draw(whitePixel, panelX - 2, panelY - 2, panelW + 4, panelH + 4);
        b.setColor(0.08f, 0.08f, 0.12f, 1f);
        b.draw(whitePixel, panelX, panelY, panelW, panelH);
        b.setColor(Color.WHITE);

        game.font.getData().setScale(0.9f);
        float textY = panelY + panelH - 10f;
        GlyphLayout gl = new GlyphLayout();

        String roundPrefix = (isRanked && roundNumber > 0) ? "ROUND " + roundNumber + "/3  |  " : "";
        String mainLabel;
        String teamChunk = null;
        Color  teamColor = null;
        String pickSuffix = null;
        if (isOnline() && !myTurn) {
            mainLabel = roundPrefix + "OPPONENT'S TURN  —  PICK " + (picksMade + 1) + " OF " + TOTAL_PICKS;
        } else {
            int teamPicks = (pickingTeam == 1) ? team1.size : team2.size;
            teamChunk  = teamNames[pickingTeam - 1].toUpperCase();
            teamColor  = (pickingTeam == 1) ? Color.CYAN : Color.SALMON;
            pickSuffix = "  \u2014  PICK " + (teamPicks + 1) + " OF " + PICKS_PER_TEAM;
            mainLabel  = roundPrefix + teamChunk + pickSuffix;
        }

        // Centre the full label, then draw each coloured segment at its exact position
        gl.setText(game.font, mainLabel);
        float x = 640 - gl.width / 2f;

        if (teamChunk == null) {
            game.font.setColor(Color.GOLD);
            game.font.draw(b, mainLabel, x, textY);
        } else {
            if (!roundPrefix.isEmpty()) {
                game.font.setColor(Color.GOLD);
                game.font.draw(b, roundPrefix, x, textY);
                gl.setText(game.font, roundPrefix);
                x += gl.width;
            }
            game.font.setColor(teamColor);
            game.font.draw(b, teamChunk, x, textY);
            gl.setText(game.font, teamChunk);
            x += gl.width;
            game.font.setColor(Color.GOLD);
            game.font.draw(b, pickSuffix, x, textY);
        }

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
    }

    private void drawConfirmButton(SpriteBatch b) {
        boolean myTurn = !isOnline() || (pickingTeam == myTeam);
        boolean active = hoveredIndex >= 0 && myTurn;
        Color   tc     = myTurn
                ? ((pickingTeam == 1) ? Color.CYAN : Color.SALMON)
                : Color.GRAY;

        b.setColor(active ? new Color(tc.r * 0.35f, tc.g * 0.35f, tc.b * 0.35f, 1f)
                          : new Color(0.08f, 0.08f, 0.10f, 1f));
        b.draw(whitePixel, confirmBounds.x, confirmBounds.y,
                confirmBounds.width, confirmBounds.height);

        b.setColor(active ? tc : Color.DARK_GRAY);
        float bx = confirmBounds.x, by = confirmBounds.y,
              bw = confirmBounds.width, bh = confirmBounds.height;
        b.draw(whitePixel, bx,          by,          bw, 2f);
        b.draw(whitePixel, bx,          by + bh - 2, bw, 2f);
        b.draw(whitePixel, bx,          by,          2f, bh);
        b.draw(whitePixel, bx + bw - 2, by,          2f, bh);

        game.font.getData().setScale(0.75f);
        game.font.setColor(active ? Color.WHITE : Color.DARK_GRAY);
        game.font.draw(b, "CONFIRM PICK", bx, by + bh / 2f + 9f, bw, 1, true);
        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    private void drawAbilityPanel(SpriteBatch b) {
        boolean myTurn = !isOnline() || (pickingTeam == myTeam);
        if (hoveredIndex < 0 || hoveredIndex >= pool.size || !myTurn) return;

        Character c  = pool.get(hoveredIndex);
        Color     tc = (pickingTeam == 1) ? Color.CYAN : Color.SALMON;

        float stripY = 5f, stripH = 100f;

        // ---- Bottom left: character portrait + name ----
        float infoX = 5f, infoW = PANEL_W - 10f;
        b.setColor(tc.r * 0.12f, tc.g * 0.12f, tc.b * 0.12f, 1f);
        b.draw(whitePixel, infoX, stripY, infoW, stripH);
        b.setColor(tc.r * 0.5f, tc.g * 0.5f, tc.b * 0.5f, 1f);
        b.draw(whitePixel, infoX, stripY + stripH - 2f, infoW, 2f);
        b.setColor(Color.WHITE);

        float portraitSize = 76f;
        if (c.getPortrait() != null)
            b.draw(c.getPortrait(), infoX + 8f, stripY + (stripH - portraitSize) / 2f, portraitSize, portraitSize);

        float txX = infoX + 8f + portraitSize + 8f;
        float txY = stripY + stripH - 14f;
        game.font.getData().setScale(0.62f);
        game.font.setColor(tc);
        game.font.draw(b, c.getName().toUpperCase(), txX, txY);
        game.font.getData().setScale(0.46f);
        game.font.setColor(classColor(c));
        game.font.draw(b, c.getCharClass().name(), txX, txY - 22f);
        game.font.getData().setScale(0.40f);
        game.font.setColor(0.75f, 0.75f, 0.75f, 1f);
        game.font.draw(b, "HP "  + c.getMaxHealth() + "  ATK " + c.getAtk(),   txX, txY - 42f);
        game.font.draw(b, "MAG " + c.getMag()       + "  ARM " + c.getArmor(), txX, txY - 56f);

        // ---- Bottom center: one box per ability ----
        Ability[] abilities = c.getAbilities();
        if (abilities != null && abilities.length > 0) {
            int   n       = abilities.length;
            float areaX   = GRID_X + 4f;
            float areaW   = GRID_W - 8f;
            float boxGap  = 6f;
            float boxW    = (areaW - (n - 1) * boxGap) / n;

            for (int i = 0; i < n; i++) {
                Ability ab = abilities[i];
                float bx = areaX + i * (boxW + boxGap);

                b.setColor(tc.r * 0.10f, tc.g * 0.10f, tc.b * 0.10f, 1f);
                b.draw(whitePixel, bx, stripY, boxW, stripH);
                b.setColor(tc.r * 0.55f, tc.g * 0.55f, tc.b * 0.55f, 1f);
                b.draw(whitePixel, bx, stripY + stripH - 2f, boxW, 2f);
                b.setColor(Color.WHITE);

                game.font.getData().setScale(0.48f);
                game.font.setColor(tc);
                game.font.draw(b, ab.getName().toUpperCase(), bx + 5f, stripY + stripH - 7f, boxW - 10f, 1, true);

                String desc = ab.getDescription();
                if (desc != null && !desc.isEmpty()) {
                    game.font.getData().setScale(0.40f);
                    game.font.setColor(0.80f, 0.80f, 0.80f, 1f);
                    game.font.draw(b, desc, bx + 5f, stripY + stripH - 28f, boxW - 10f, -1, true);
                }
            }
        }

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
        b.setColor(Color.WHITE);
    }

    private void drawScrollbar(SpriteBatch b) {
        if (maxScroll <= 0) return;
        float trackX = SCROLL_X, trackY = GRID_Y_BOT;
        float trackH = GRID_Y_TOP - GRID_Y_BOT;
        b.setColor(0.12f, 0.12f, 0.18f, 1f);
        b.draw(whitePixel, trackX, trackY, SCROLL_W, trackH);

        int   rows       = (int) Math.ceil((double) pool.size / COLS);
        float totalH     = rows * (CARD_H + CARD_PAD) - CARD_PAD;
        float thumbRatio = Math.min(1f, trackH / totalH);
        float thumbH     = Math.max(SCROLL_MIN_H, trackH * thumbRatio);
        float scrollable = trackH - thumbH;
        float progress   = (maxScroll > 0) ? scrollOffset / maxScroll : 0f;
        float thumbY     = trackY + (1f - progress) * scrollable;

        Color tc = (pickingTeam == 1) ? Color.CYAN : Color.SALMON;
        float brightness = isDraggingScroll ? 1.0f : 0.65f;
        b.setColor(tc.r * brightness, tc.g * brightness, tc.b * brightness, 1f);
        b.draw(whitePixel, trackX, thumbY, SCROLL_W, thumbH);
        b.setColor(0f, 0f, 0f, 0.4f);
        b.draw(whitePixel, trackX, thumbY,              SCROLL_W, 1f);
        b.draw(whitePixel, trackX, thumbY + thumbH - 1, SCROLL_W, 1f);
        b.setColor(Color.WHITE);
    }

    // -----------------------------------------------------------------------
    // Drawing — board selection phase
    // -----------------------------------------------------------------------
    private void drawBoardSelection(SpriteBatch b) {
        b.setColor(0.06f, 0.06f, 0.10f, 1f);
        b.draw(whitePixel, 0, 0, 1280, 720);
        b.setColor(Color.WHITE);

        game.font.getData().setScale(1.1f);
        game.font.setColor(Color.WHITE);
        String title = "CHOOSE YOUR BATTLEFIELD";
        game.font.draw(b, title, 640 - title.length() * 7.5f, 685f);
        game.font.getData().setScale(1.0f);

        float totalW = 3 * BOARD_CARD_W + 2 * BOARD_CARD_PAD;
        float startX = (1280f - totalW) / 2f;
        float cardY  = 120f;

        for (int i = 0; i < 3; i++) {
            float cx = startX + i * (BOARD_CARD_W + BOARD_CARD_PAD);
            boardCardBounds[i].set(cx, cardY, BOARD_CARD_W, BOARD_CARD_H);

            boolean hovered = (i == hoveredBoard) && (!isOnline() || myTeam == 1);
            BoardConfig cfg = boardOptions[i];

            if (hovered) { b.setColor(Color.WHITE); b.draw(whitePixel, cx - 3, cardY - 3, BOARD_CARD_W + 6, BOARD_CARD_H + 6); }
            b.setColor(cfg.backgroundColor);
            b.draw(whitePixel, cx, cardY, BOARD_CARD_W, BOARD_CARD_H);

            float previewSize = 140f, tileSize = previewSize / 4f;
            float previewX = cx + (BOARD_CARD_W - previewSize) / 2f;
            float previewY = cardY + BOARD_CARD_H - previewSize - 18f;
            for (int tx = 0; tx < 4; tx++)
                for (int ty = 0; ty < 4; ty++) {
                    b.setColor((tx + ty) % 2 == 0 ? cfg.tileColorA : cfg.tileColorB);
                    b.draw(whitePixel, previewX + tx * tileSize, previewY + ty * tileSize, tileSize - 1, tileSize - 1);
                }

            float nameY    = previewY - 14f;
            float dividerY = nameY - 28f;
            game.font.getData().setScale(0.80f);
            game.font.setColor(hovered ? Color.WHITE : new Color(0.85f, 0.85f, 0.85f, 1f));
            game.font.draw(b, BOARD_NAMES[i], cx, nameY, BOARD_CARD_W, 1, true);

            b.setColor(hovered ? Color.WHITE : new Color(0.35f, 0.35f, 0.45f, 1f));
            b.draw(whitePixel, cx + 10, dividerY, BOARD_CARD_W - 20, 1);

            game.font.getData().setScale(0.52f);
            game.font.setColor(cfg.type == BoardConfig.BoardType.WIND
                    ? new Color(0.15f, 0.15f, 0.15f, 1f)
                    : new Color(0.72f, 0.72f, 0.72f, 1f));
            game.font.draw(b, BOARD_DESCS[i], cx + 14f, dividerY - 16f, BOARD_CARD_W - 28f, -1, true);

            if (hovered) {
                b.setColor(new Color(1f, 1f, 1f, 0.18f));
                b.draw(whitePixel, cx, cardY, BOARD_CARD_W, 36f);
                game.font.getData().setScale(0.52f);
                game.font.setColor(Color.WHITE);
                game.font.draw(b, "CLICK TO SELECT", cx, cardY + 24f, BOARD_CARD_W, 1, true);
            }

            b.setColor(Color.WHITE);
            game.font.getData().setScale(1.0f);
            game.font.setColor(Color.WHITE);
        }
    }

    // -----------------------------------------------------------------------
    // Waiting overlay (online mode only)
    // -----------------------------------------------------------------------
    private void drawWaitingOverlay(SpriteBatch b, String message) {
        b.setColor(0f, 0f, 0f, 0.55f);
        b.draw(whitePixel, 0, 0, 1280, 720);
        b.setColor(Color.WHITE);
        game.font.getData().setScale(0.85f);
        game.font.setColor(Color.GOLD);
        game.font.draw(b, message, 640 - message.length() * 5.5f, 370f);
        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private boolean isOnline() { return client != null; }

    private Color classColor(Character c) {
        switch (c.getCharClass()) {
            case ASSASSIN: return new Color(0.8f, 0.3f, 1.0f, 1f);
            case FIGHTER:  return new Color(1.0f, 0.5f, 0.2f, 1f);
            case MAGE:     return new Color(0.3f, 0.6f, 1.0f, 1f);
            case SUPPORT:  return new Color(0.3f, 1.0f, 0.5f, 1f);
            case STATUE:   return new Color(0.7f, 0.7f, 0.7f, 1f);
            case CHAOS:    return new Color(1.0f, 0.8f, 0.2f, 1f);
            case SNIPER:   return new Color(0.4f, 0.9f, 0.4f, 1f);
            default:       return Color.LIGHT_GRAY;
        }
    }
}