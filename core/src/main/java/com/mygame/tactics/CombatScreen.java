package com.mygame.tactics;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import java.util.HashMap;
import java.util.Map;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.mygame.tactics.characters.Billy;
import com.mygame.tactics.characters.Hunter;
import com.mygame.tactics.characters.Lark;
import com.mygame.tactics.characters.Luke;
import com.mygame.tactics.characters.Mason;
import com.mygame.tactics.characters.Sean;
import com.mygame.tactics.network.NetworkClient;

public class CombatScreen implements Screen {

    // -----------------------------------------------------------------------
    // Core
    // -----------------------------------------------------------------------
    private final Main       game;
    private final GameState  state;
    private final GameEngine engine;

    // Online mode — null in local play
    private final NetworkClient client;
    private final int           myTeam;      // 1 or 2 online; 0 in local play
    private       boolean       isRanked;    // true in ranked online matches
    private       int           currentRound;
    private       int           team1RoundWins;
    private       int           team2RoundWins;
    private final String[]      teamNames;   // display names for each team
    // Non-null when a ranked round/match has just ended (drives overlay rendering)
    private       NetworkMessage rankedEndMsg = null;

    // -----------------------------------------------------------------------
    // Rendering-only
    // -----------------------------------------------------------------------
    private OrthographicCamera camera;
    private FitViewport        viewport;
    private Texture whitePixel, wallTexture, poisonTileTexture, fireTileTexture,
                    pergolaTileTexture, vinesTileTexture, drywallTileTexture, clothesTileTexture, cloudTileTexture, lockdownTileTexture;
    private Map<Enums.CharType,  Texture> typeIcons  = new HashMap<>();
    private Map<Enums.CharClass, Texture> classIcons = new HashMap<>();
    private Array<DamagePopup> damagePopups = new Array<>();

    // Move animation (rendering concern only)
    private boolean isAnimatingMove = false;
    private float   moveLerp        = 0f;
    private Vector2 moveStart       = new Vector2();
    private Vector2 moveTarget      = new Vector2();
    private Character animatingUnit = null;
    private float stateTime         = 0f;

    // Post-ability pause — blocks input briefly so the player can see the effect
    private float postAbilityDelay = 0f;

    // Delay before showing the game-over overlay so the final action can settle
    private float gameOverDisplayDelay = -1f; // -1 = not yet triggered

    // Delay before auto-returning to menu after a ranked match ends
    private float matchOverReturnDelay = -1f; // -1 = not active

    // "YOUR TURN" flash — set when the active unit switches to myTeam in online play
    private float     yourTurnFlash      = 0f;
    private Character lastSeenActiveUnit = null;

    // -----------------------------------------------------------------------
    // Layout constants — single source of truth for all vertical zones
    // -----------------------------------------------------------------------
    private static final float TIMELINE_Y      = 660f; // bottom of timeline band
    private static final float TIMELINE_H      = 50f;  // height of timeline portraits
    private static final float BOARD_BOTTOM_Y  = 120f; // bottom of board area
    private static final float BUTTON_Y        = 10f;  // bottom of ability/action buttons
    private static final float BUTTON_H        = 90f;  // height of ability/action buttons
    private static final float SIDEBAR_W       = 370f;
    private static final float BOARD_START_X   = 380f;
    private static final float HP_BAR_H        = 18f;  // taller health bar
    private static final Color STAT_ATK_COLOR   = new Color(0.95f, 0.25f, 0.25f, 1f);
    private static final Color STAT_MAG_COLOR   = new Color(0.75f, 0.25f, 0.95f, 1f);
    private static final Color STAT_HEAL_COLOR  = new Color(0.20f, 0.85f, 0.30f, 1f);
    private static final Color STAT_ARMOR_COLOR = new Color(0.85f, 0.72f, 0.45f, 1f);
    private static final Color STAT_CLOAK_COLOR = new Color(0.35f, 0.55f, 0.95f, 1f);

    // -----------------------------------------------------------------------
    // Pre-game UI state (rendering-only)
    // -----------------------------------------------------------------------
    private Array<Rectangle> disguiseOptionBounds = new Array<>();

    // -----------------------------------------------------------------------
    // Constructors
    // -----------------------------------------------------------------------
    public CombatScreen(Main game, Array<Character> team1, Array<Character> team2, BoardConfig config) {
        this(game, team1, team2, config, new String[]{"Team 1", "Team 2"});
    }

    public CombatScreen(Main game, Array<Character> team1, Array<Character> team2, BoardConfig config,
                        String[] teamNames) {
        this.game      = game;
        this.state     = new GameState(team1, team2, config);
        this.engine    = new GameEngine();
        this.state.engine = this.engine;
        this.client    = null;
        this.myTeam    = 0;
        this.teamNames = teamNames;
        initRendering();
        consumeEvents(engine.initialize(state));
    }

    /**
     * Online constructor — called by DraftScreen when the server sends the
     * initial ACTION_RESULT with the battle GameState already built.
     *
     * @param localTeam1  Team 1 characters built by DraftScreen — have portrait textures.
     * @param localTeam2  Team 2 characters built by DraftScreen — have portrait textures.
     */
    public CombatScreen(Main game, GameState serverState,
                        Array<Character> localTeam1, Array<Character> localTeam2,
                        NetworkClient client, int myTeam) {
        this(game, serverState, localTeam1, localTeam2, client, myTeam, false, 0, 0, 0,
             new String[]{"Team 1", "Team 2"});
    }

    public CombatScreen(Main game, GameState serverState,
                        Array<Character> localTeam1, Array<Character> localTeam2,
                        NetworkClient client, int myTeam,
                        boolean isRanked, int roundNumber, int t1Wins, int t2Wins) {
        this(game, serverState, localTeam1, localTeam2, client, myTeam,
             isRanked, roundNumber, t1Wins, t2Wins, new String[]{"Team 1", "Team 2"});
    }

    public CombatScreen(Main game, GameState serverState,
                        Array<Character> localTeam1, Array<Character> localTeam2,
                        NetworkClient client, int myTeam,
                        boolean isRanked, int roundNumber, int t1Wins, int t2Wins,
                        String[] teamNames) {
        this.game            = game;
        this.engine          = new GameEngine();
        this.client          = client;
        this.myTeam          = myTeam;
        this.isRanked        = isRanked;
        this.currentRound    = roundNumber;
        this.team1RoundWins  = t1Wins;
        this.team2RoundWins  = t2Wins;
        this.teamNames       = teamNames;

        // Build a fresh GameState using the local (portrait-bearing) character objects.
        // Then sync all battle fields from the server state so positions, health, etc. match.
        this.state  = new GameState(localTeam1, localTeam2, serverState.boardConfig);
        this.state.engine = this.engine;

        // Copy server state fields into our local state
        this.state.phase            = serverState.phase;
        this.state.turnPhase        = serverState.turnPhase;
        this.state.haven            = serverState.haven;
        this.state.collapseWait     = serverState.collapseWait;
        this.state.collapseCount    = serverState.collapseCount;
        this.state.windPushDistance = serverState.windPushDistance;
        this.state.havenLocked      = serverState.havenLocked;
        this.state.isFighterBonusMove  = serverState.isFighterBonusMove;
        this.state.isGrandEntranceMove = serverState.isGrandEntranceMove;
        this.state.isMarathonMove      = serverState.isMarathonMove;

        // Sync each local unit's position and stats from the server state
        for (Character serverUnit : serverState.allUnits) {
            for (Character localUnit : this.state.allUnits) {
                if (localUnit.getName().equals(serverUnit.getName())) {
                    localUnit.x          = serverUnit.x;
                    localUnit.y          = serverUnit.y;
                    localUnit.health     = serverUnit.health;
                    localUnit.atk        = serverUnit.atk;
                    localUnit.mag        = serverUnit.mag;
                    localUnit.armor      = serverUnit.armor;
                    localUnit.cloak      = serverUnit.cloak;
                    localUnit.speed      = serverUnit.speed;
                    localUnit.maxHealth  = serverUnit.maxHealth;
                    localUnit.moveDist   = serverUnit.moveDist;
                    localUnit.range      = serverUnit.range;
                    localUnit.hasDeployed = serverUnit.hasDeployed;
                    localUnit.isInvisible = serverUnit.isInvisible;
                    localUnit.setCurrentWait(serverUnit.getCurrentWait());
                    break;
                }
            }
        }

        // Resolve activeUnit to local object
        this.state.activeUnit = null;
        if (serverState.activeUnit != null) {
            for (Character c : this.state.allUnits) {
                if (c.getName().equals(serverState.activeUnit.getName())) {
                    this.state.activeUnit = c;
                    break;
                }
            }
        }

        initRendering();
        rebuildBoardFromLocalUnits();

        client.setListener(new NetworkClient.MessageListener() {
            @Override public void onConnected() {}

            @Override
            public void onDisconnected() {
                Gdx.app.postRunnable(() -> game.setScreen(new MenuScreen(game)));
            }

            @Override
            public void onMessage(NetworkMessage msg) {
                Gdx.app.postRunnable(() -> handleServerMessage(msg));
            }
        });
    }

    private void handleServerMessage(NetworkMessage msg) {
        switch (msg.type) {
            case ACTION_RESULT:
                if (msg.gameState != null) applyServerState(msg.gameState);
                consumeEvents(msg.events);
                break;
            case GAME_OVER:
                if (msg.gameState != null) applyServerState(msg.gameState);
                break;
            case RANKED_ROUND_OVER:
                rankedEndMsg      = msg;
                team1RoundWins    = msg.team1RoundWins;
                team2RoundWins    = msg.team2RoundWins;
                // Only start a new draft if the match is still ongoing
                boolean matchOver = msg.team1RoundWins >= 2
                        || msg.team2RoundWins >= 2
                        || msg.roundNumber >= 3;
                if (!matchOver) {
                    Gdx.app.postRunnable(() -> {
                        int nextRound = msg.roundNumber + 1;
                        game.setScreen(new DraftScreen(game, client, myTeam, true,
                                nextRound, msg.team1RoundWins, msg.team2RoundWins,
                                msg.remainingPool, teamNames));
                    });
                }
                break;
            case RANKED_MATCH_OVER:
                rankedEndMsg = msg;
                // Auto-return to menu after showing the result for 5 seconds
                matchOverReturnDelay = 5f;
                break;
            case OPPONENT_DISCONNECTED:
                damagePopups.add(new DamagePopup("OPPONENT LEFT", 640, 360));
                break;
            default:
                break;
        }
    }

    /**
     * Re-places local Character objects (which have portrait Textures) onto
     * the board using their current x/y positions. Called once after the
     * online constructor receives the initial GameState from the server,
     * and also at the end of every applyServerState() call.
     */
    private void rebuildBoardFromLocalUnits() {
        for (int x = 0; x < 9; x++)
            for (int y = 0; y < 9; y++)
                state.board.removeCharacter(x, y);
        for (Character c : state.allUnits) {
            if (!c.isDead() && c.hasDeployed) {
                state.board.addCharacter(c, c.x, c.y);
            }
        }
    }

    private void applyServerState(GameState incoming) {
        // Clear all transient selection/highlight state from the previous action
        state.selectedAbility    = null;
        state.selectedMoveTile   = null;
        state.selectedTargetTile = null;
        state.targetableTiles.clear();
        state.reachableTiles.clear();

        state.phase             = incoming.phase;
        state.turnPhase         = incoming.turnPhase;
        state.winnerTeam        = incoming.winnerTeam;
        state.collapseWait      = incoming.collapseWait;
        state.collapseCount     = incoming.collapseCount;
        state.windPushDistance  = incoming.windPushDistance;
        state.havenLocked       = incoming.havenLocked;
        state.haven             = incoming.haven;
        state.isFighterBonusMove   = incoming.isFighterBonusMove;
        state.isGrandEntranceMove  = incoming.isGrandEntranceMove;
        state.isMarathonMove       = incoming.isMarathonMove;
        state.pendingCollapseCols  = incoming.pendingCollapseCols;
        state.pendingCollapseRows  = incoming.pendingCollapseRows;

        // Sync each unit's stats by name — preserves local portrait textures
        for (Character inUnit : incoming.allUnits) {
            for (Character myUnit : state.allUnits) {
                if (myUnit.getName().equals(inUnit.getName())) {
                    myUnit.health       = inUnit.health;
                    myUnit.atk          = inUnit.atk;
                    myUnit.mag          = inUnit.mag;
                    myUnit.armor        = inUnit.armor;
                    myUnit.cloak        = inUnit.cloak;
                    myUnit.speed        = inUnit.speed;
                    myUnit.maxHealth    = inUnit.maxHealth;
                    myUnit.poisonLevel  = inUnit.poisonLevel;
                    myUnit.moveDist     = inUnit.moveDist;
                    myUnit.range        = inUnit.range;
                    myUnit.x            = inUnit.x;
                    myUnit.y            = inUnit.y;
                    myUnit.isInvisible  = inUnit.isInvisible;
                    myUnit.isRooted     = inUnit.isRooted;
                    myUnit.isFrozen     = inUnit.isFrozen;
                    myUnit.hasDeployed  = inUnit.hasDeployed;
                    myUnit.setCurrentWait(inUnit.getCurrentWait());
                    myUnit.setUltUsed(inUnit.isUltUsed());
                    myUnit.setUltActive(inUnit.isUltActive());

                    // Billy: sync disguise choice (server is authoritative)
                    if (myUnit instanceof Billy && inUnit instanceof Billy) {
                        Billy myBilly = (Billy) myUnit;
                        Billy inBilly = (Billy) inUnit;
                        if (inBilly.disguisedAs == null) {
                            myBilly.disguisedAs = null;
                        } else {
                            // Find the local Character object matching the server's disguise target
                            for (Character c : state.allUnits) {
                                if (c.getName().equals(inBilly.disguisedAs.getName())) {
                                    myBilly.disguisedAs = c;
                                    break;
                                }
                            }
                        }
                    }
                    break;
                }
            }
        }

        // After syncing all units, refresh deployment highlights if still in pre-game
        if (state.isPreGame() && state.activeUnit != null) {
            engine.calculateMovementRange(state);
        }

        // Resolve activeUnit to local object (which has portrait)
        state.activeUnit = null;
        if (incoming.activeUnit != null) {
            for (Character c : state.allUnits) {
                if (c.getName().equals(incoming.activeUnit.getName())) {
                    state.activeUnit = c;
                    break;
                }
            }
        }

        // Resolve havenOccupant
        state.havenOccupant = null;
        if (incoming.havenOccupant != null) {
            for (Character c : state.allUnits) {
                if (c.getName().equals(incoming.havenOccupant.getName())) {
                    state.havenOccupant = c;
                    break;
                }
            }
        }

        // Sync board tile states
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 9; y++) {
                Tile src  = incoming.board.getTile(x, y);
                Tile dest = state.board.getTile(x, y);
                if (src == null || dest == null) continue;
                dest.setCollapsed(src.isCollapsed());
                dest.setPoison(src.isPoisoned());
                dest.setFrozen(src.isFrozen());
                dest.setThorn(src.isThorn());
                dest.setPergola(src.isPergola());
                dest.setDrywall(src.isDrywall());
                dest.setClothes(src.isClothes());
                dest.setLockdown(src.isLockdown());
                if (src.hasStructure()) dest.setStructureHP(src.getStructureHealth());
                else dest.setStructureHP(0);
                dest.syncFire(src.getFireTurnsActive(), src.getFireDuration());
            }
        }

        // Re-place local character objects (which have portraits) onto the board.
        rebuildBoardFromLocalUnits();

        // Start the game-over display delay when we first see the game over state.
        if (state.isGameOver() && gameOverDisplayDelay < 0f)
            gameOverDisplayDelay = 3.0f;

        state.timeline.projectFutureEvents(state.allUnits);
        engine.calculateMovementRange(state);
    }

    private void initRendering() {
        camera   = new OrthographicCamera();
        viewport = new FitViewport(1280, 720, camera);
        camera.position.set(640, 360, 0);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE); pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();

        poisonTileTexture = new Texture("poison_tile.png");
        fireTileTexture   = new Texture("fire_tile.png");
        wallTexture       = new Texture("painted_walls.png");
        pergolaTileTexture = new Texture("pergola_tile.png");
        vinesTileTexture   = new Texture("vines_tile.png");
        drywallTileTexture = new Texture("drywall_tile.png");
        clothesTileTexture = new Texture("clothes_tile.png");
        cloudTileTexture = new Texture("cloud_tile.png");
        lockdownTileTexture = new Texture("lockdown_tile.png");

        // Type icons
        for (Enums.CharType t : Enums.CharType.values()) {
            String path = t.name().toLowerCase() + "_icon.png";
            try { typeIcons.put(t, new Texture(path)); } catch (Exception ignored) {}
        }
        // Class icons
        for (Enums.CharClass cl : Enums.CharClass.values()) {
            String path = cl.name().toLowerCase() + "_icon.png";
            try { classIcons.put(cl, new Texture(path)); } catch (Exception ignored) {}
        }
    }

    // -----------------------------------------------------------------------
    // Screen lifecycle
    // -----------------------------------------------------------------------
    @Override
    public void render(float delta) {
        stateTime += delta;

        if (state.warningAlpha > 0)
            state.warningAlpha = Math.max(0f, state.warningAlpha - delta * 0.4f);

        if (isAnimatingMove) {
            moveLerp += delta * 4.0f;
            if (moveLerp >= 1.0f) {
                moveLerp = 1.0f;
                isAnimatingMove = false;
                animatingUnit   = null;
            }
        }

        // Tick down the post-ability delay; block input while either it or a
        // move animation is active.
        if (postAbilityDelay > 0f) postAbilityDelay -= delta;
        if (!isAnimatingMove && postAbilityDelay <= 0f) handleInput();

        // Detect when it becomes my turn in online play and trigger the flash
        if (client != null && state.activeUnit != lastSeenActiveUnit) {
            if (state.activeUnit != null && state.activeUnit.team == myTeam) {
                yourTurnFlash = 1.8f;
            }
            lastSeenActiveUnit = state.activeUnit;
        }
        if (yourTurnFlash > 0f) yourTurnFlash -= delta;

        if (gameOverDisplayDelay > 0f) gameOverDisplayDelay -= delta;

        if (matchOverReturnDelay > 0f) {
            matchOverReturnDelay -= delta;
            if (matchOverReturnDelay <= 0f) {
                game.setScreen(new MenuScreen(game));
                return;
            }
        }

        ScreenUtils.clear(0.1f, 0.1f, 0.12f, 1);
        camera.update();
        game.batch.setProjectionMatrix(camera.combined);
        game.batch.begin();

        drawBoard(game.batch);
        drawSidebars(game.batch);
        drawTimeline(game.batch);
        drawAbilityButtons(game.batch);
        drawActionTriggerButton(game.batch);

        if (state.isPreGame() && state.activeUnit instanceof Billy
                && !state.activeUnit.hasDeployed
                && ((Billy) state.activeUnit).disguisedAs == null)
            drawBillySelectionWindow(game.batch);

        if (state.warningAlpha > 0) {
            game.batch.setColor(1f, 0.2f, 0.1f, state.warningAlpha * 0.45f);
            game.batch.draw(whitePixel, 0, 0, 1280, 720);
            game.batch.setColor(Color.WHITE);
            game.font.getData().setScale(3.0f);
            game.font.setColor(1f, 0.3f, 0.1f, state.warningAlpha);
            String warn = "RING COLLAPSING SOON!";
            game.font.draw(game.batch, warn, 640 - warn.length() * 16, 420);
            game.font.getData().setScale(1.0f);
            game.font.setColor(Color.WHITE);
        }

        if (state.isGameOver() && gameOverDisplayDelay <= 0f) drawGameOverScreen(game.batch);
        if (rankedEndMsg != null && rankedEndMsg.type == NetworkMessage.Type.RANKED_MATCH_OVER)
            drawRankedMatchOverOverlay(game.batch);

        // "YOUR TURN" flash overlay
        if (yourTurnFlash > 0f) {
            float alpha = Math.min(1f, yourTurnFlash * 1.5f); // quick fade-in, then fade out
            game.font.getData().setScale(3.2f);
            game.font.setColor(Color.CYAN.r, Color.CYAN.g, Color.CYAN.b, alpha);
            String yt = "YOUR TURN";
            GlyphLayout ytLayout = new GlyphLayout(game.font, yt);
            game.font.draw(game.batch, yt, 640f - ytLayout.width / 2f, 420f);
            game.font.getData().setScale(1.0f);
            game.font.setColor(Color.WHITE);
        }

        for (int i = damagePopups.size - 1; i >= 0; i--) {
            DamagePopup p = damagePopups.get(i);
            p.update(delta);
            if (p.delay <= 0) {
                if (p.text.contains("HEAL"))       game.font.setColor(Color.GREEN);
                else if (p.text.contains("MOVE")
                        || p.text.contains("FREE")) game.font.setColor(Color.CYAN);
                else                               game.font.setColor(1, 1, 1, p.alpha);
                game.font.getData().setScale(0.7f);
                game.font.draw(game.batch, p.text, p.x, p.y);
            }
            if (p.lifetime <= 0) damagePopups.removeIndex(i);
        }

        game.font.setColor(Color.WHITE);
        game.font.getData().setScale(1.0f);
        game.batch.end();
    }

    // -----------------------------------------------------------------------
    // Input → Action dispatch
    // -----------------------------------------------------------------------
    private void handleInput() {
        if (rankedEndMsg != null) return; // overlay handles its own input
        if (state.isGameOver()) return;
        if (state.activeUnit == null) return;
        // Online — only accept input on this player's turn
        if (client != null && state.activeUnit.team != myTeam) return;
        if (!Gdx.input.justTouched()) return;

        Vector3 world = camera.unproject(
                new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));

        // PRE-GAME: STATUE deployment
        if (state.isPreGame() && state.activeUnit.getCharClass() == Enums.CharClass.STATUE
                && !state.activeUnit.hasDeployed) {
            int gx = (int) ((world.x - BOARD_START_X) / 58f);
            int gy = (int) ((world.y - BOARD_BOTTOM_Y) / 58f);
            if (state.board.isValid(gx, gy)) {
                dispatch(new Action.DeployAction(state.activeUnit.team, gx, gy));
            }
            return;
        }
        
     // PRE-GAME: regular character deployment
        if (state.isPreGame() && !state.activeUnit.hasDeployed
                && state.activeUnit.getCharClass() != Enums.CharClass.STATUE
                && !(state.activeUnit instanceof Billy)) {
            int gx = (int) ((world.x - BOARD_START_X) / 58f);
            int gy = (int) ((world.y - BOARD_BOTTOM_Y) / 58f);
            if (state.board.isValid(gx, gy)) {
                dispatch(new Action.DeployAction(state.activeUnit.team, gx, gy));
            }
            return;
        }

        // PRE-GAME: Billy disguise selection — click a portrait to choose immediately
        if (state.isPreGame() && state.activeUnit instanceof Billy && !state.activeUnit.hasDeployed
                && ((Billy) state.activeUnit).disguisedAs == null) {
            Array<Character> teammates = (state.activeUnit.team == 1) ? state.team1 : state.team2;
            int idx = 0;
            for (Character ally : teammates) {
                if (ally == state.activeUnit) continue;
                if (idx < disguiseOptionBounds.size
                        && disguiseOptionBounds.get(idx).contains(world.x, world.y)) {
                    dispatch(new Action.ChooseDisguiseAction(
                            state.activeUnit.team, ally.getName()));
                    return;
                }
                idx++;
            }
            return;
        }

        // PRE-GAME: Billy deployment (after disguise has been chosen)
        if (state.isPreGame() && state.activeUnit instanceof Billy && !state.activeUnit.hasDeployed) {
            int gx = (int) ((world.x - BOARD_START_X) / 58f);
            int gy = (int) ((world.y - BOARD_BOTTOM_Y) / 58f);
            if (state.board.isValid(gx, gy)) {
                dispatch(new Action.DeployAction(state.activeUnit.team, gx, gy));
            }
            return;
        }

        // ACTION BUTTON
        if (world.x >= 930 && world.x <= 1280 && world.y >= BUTTON_Y && world.y <= BUTTON_Y + BUTTON_H) {
            if (state.isMovementPhase()) {
                if (state.selectedMoveTile != null) {
                    dispatch(new Action.MoveAction(state.activeUnit.team,
                            (int) state.selectedMoveTile.x, (int) state.selectedMoveTile.y));
                } else {
                    dispatch(new Action.MoveAction(state.activeUnit.team,
                            state.activeUnit.x, state.activeUnit.y));
                }
            } else {
                if (state.selectedAbility != null) {
                    int tx = (state.selectedTargetTile != null)
                            ? (int) state.selectedTargetTile.x : state.activeUnit.x;
                    int ty = (state.selectedTargetTile != null)
                            ? (int) state.selectedTargetTile.y : state.activeUnit.y;
                    dispatch(new Action.AbilityAction(state.activeUnit.team,
                            getAbilityIndex(state.selectedAbility), tx, ty));
                } else {
                    dispatch(new Action.PassAction(state.activeUnit.team));
                }
            }
            return;
        }

        // ABILITY BUTTONS
        float btnW = (9 * 58f) / 3f;
        for (int i = 0; i < 3; i++) {
            float bx = BOARD_START_X + (i * btnW);
            if (world.x >= bx && world.x <= bx + btnW
                    && world.y >= BUTTON_Y && world.y <= BUTTON_Y + BUTTON_H) {
                if (!state.isAbilityPhase()) return;
                if (i == 1 && state.activeUnit instanceof Hunter && state.activeUnit.isInvisible()) return;
                if (i == 0 && state.activeUnit instanceof Mason && state.activeUnit.isInvisible()) return;
                if (i == 2 && state.activeUnit.isUltUsed()) return;
                Ability clicked = state.activeUnit.getAbility(i);
                if (clicked == null) return;
                if (state.selectedAbility == clicked) {
                    state.selectedAbility = null;
                    state.targetableTiles.clear();
                } else {
                    state.selectedAbility = clicked;
                    if (clicked.getRange() == 0 && !clicked.isDirectionAbility) {
                        dispatch(new Action.AbilityAction(state.activeUnit.team, i, -1, -1));
                    } else {
                        engine.calculateTargetRange(state, clicked);
                    }
                }
                return;
            }
        }

        // GRID CLICK
        int gx = (int) ((world.x - BOARD_START_X) / 58f);
        int gy = (int) ((world.y - BOARD_BOTTOM_Y) / 58f);
        if (gx >= 0 && gx < 9 && gy >= 0 && gy < 9) {
            if (state.isMovementPhase()) {
                state.selectedMoveTile = null;
                for (Vector2 v : state.reachableTiles)
                    if ((int) v.x == gx && (int) v.y == gy) state.selectedMoveTile = v;
            } else if (state.selectedAbility != null) {
                // Only accept clicks that land on a highlighted (valid) target tile
                boolean validTarget = false;
                for (Vector2 v : state.targetableTiles)
                    if ((int) v.x == gx && (int) v.y == gy) { validTarget = true; break; }
                if (!validTarget) return;

                state.selectedTargetTile = new Vector2(gx, gy);
                String selAbilityName = state.selectedAbility.getName();
                if (state.activeUnit instanceof Sean && selAbilityName.equals("Painted Walls")) {
                    Sean sean = (Sean) state.activeUnit;
                    if (sean.wallAnchor == null) {
                        // First click — set the anchor, then recalculate to show direction tiles
                        sean.wallAnchor = new Vector2(gx, gy);
                        engine.calculateTargetRange(state, state.selectedAbility);
                    } else {
                        // Second click — dispatch then clean up anchor
                        sean.wallDirection = new Vector2(gx, gy);
                        dispatch(new Action.TwoStepAbilityAction(
                                state.activeUnit.team,
                                getAbilityIndex(state.selectedAbility),
                                (int) sean.wallAnchor.x,
                                (int) sean.wallAnchor.y,
                                gx, gy));
                        sean.wallAnchor    = null;
                        sean.wallDirection = null;
                    }
                } else if (state.activeUnit instanceof Lark && selAbilityName.equals("Wall of Fire")) {
                    Lark lark = (Lark) state.activeUnit;
                    if (lark.wallOfFireAnchor == null) {
                        // First click — set the anchor, then recalculate to show direction tiles
                        lark.wallOfFireAnchor = new Vector2(gx, gy);
                        engine.calculateTargetRange(state, state.selectedAbility);
                    } else {
                        // Second click — dispatch then clean up anchor
                        lark.wallOfFireDirection = new Vector2(gx, gy);
                        dispatch(new Action.TwoStepAbilityAction(
                                state.activeUnit.team,
                                getAbilityIndex(state.selectedAbility),
                                (int) lark.wallOfFireAnchor.x,
                                (int) lark.wallOfFireAnchor.y,
                                gx, gy));
                        lark.wallOfFireAnchor    = null;
                        lark.wallOfFireDirection = null;
                    }
                } else if (state.activeUnit instanceof Luke && selAbilityName.equals("Pergolatory")) {
                    Luke luke = (Luke) state.activeUnit;
                    if (luke.pergolaAnchor == null) {
                        // First click — set the anchor corner, then recalculate to show
                        // the three other diagonal corners as valid second clicks
                        luke.pergolaAnchor = new Vector2(gx, gy);
                        engine.calculateTargetRange(state, state.selectedAbility);
                    } else {
                        // Second click — dispatch with anchor + chosen diagonal corner
                        dispatch(new Action.TwoStepAbilityAction(
                                state.activeUnit.team,
                                getAbilityIndex(state.selectedAbility),
                                (int) luke.pergolaAnchor.x,
                                (int) luke.pergolaAnchor.y,
                                gx, gy));
                        luke.pergolaAnchor = null;
                    }
                } else if (state.selectedAbility.isDirectionAbility) {
                    // Direction-click ability (e.g. Bull Charge, Summon The Wind, Rockslide)
                    // — one tile click is all that's needed; dispatch immediately.
                    dispatch(new Action.AbilityAction(
                            state.activeUnit.team,
                            getAbilityIndex(state.selectedAbility),
                            gx, gy));
                }
            }
        }
    }

    private void dispatch(Action action) {
        if (client != null) {
            // Online — send to server; state updates when ACTION_RESULT arrives
            postAbilityDelay = 0.1f;
            client.sendAction(action);
        } else {
            // Local — process immediately
            Array<EngineEvent> events = engine.process(state, action);
            consumeEvents(events);
        }
    }

    private void consumeEvents(Array<EngineEvent> events) {
        for (EngineEvent e : events) {
            if (e instanceof EngineEvent.PopupEvent) {
                EngineEvent.PopupEvent pe = (EngineEvent.PopupEvent) e;
                String display = (pe.value <= 0) ? pe.text
                        : pe.text + " " + pe.value + " " + pe.type;
                Vector2 pos = getTileScreenPos(pe.tileX, pe.tileY);
                DamagePopup popup = new DamagePopup(display, pos.x, pos.y);
                popup.delay = pe.delay;
                damagePopups.add(popup);

            } else if (e instanceof EngineEvent.MoveAnimationEvent) {
                EngineEvent.MoveAnimationEvent mae = (EngineEvent.MoveAnimationEvent) e;
                animatingUnit  = mae.unit;
                moveStart.set(mae.fromX, mae.fromY);
                moveTarget.set(mae.toX, mae.toY);
                moveLerp        = 0f;
                isAnimatingMove = true;

            } else if (e instanceof EngineEvent.AbilityResolveEvent) {
                // Block input for the requested duration so the player sees the effect
                EngineEvent.AbilityResolveEvent are = (EngineEvent.AbilityResolveEvent) e;
                postAbilityDelay = Math.max(postAbilityDelay, are.duration);

            } else if (e instanceof EngineEvent.GameOverEvent) {
                // state.phase is already GAME_OVER; start a delay before showing the overlay.
                gameOverDisplayDelay = 3.0f;
            } else if (e instanceof EngineEvent.PortraitChangeEvent) {
                EngineEvent.PortraitChangeEvent pce = (EngineEvent.PortraitChangeEvent) e;
                try {
                    Texture newPortrait = new Texture(pce.newPortraitPath);
                    pce.character.portrait = newPortrait;
                } catch (Exception ignored) {}
            }
        }
    }

    // -----------------------------------------------------------------------
    // Rendering
    // -----------------------------------------------------------------------

    private void drawGameOverScreen(SpriteBatch b) {
        b.setColor(0f, 0f, 0f, 0.75f);
        b.draw(whitePixel, 0, 0, 1280, 720);
        b.setColor(Color.WHITE);
        game.font.getData().setScale(2.5f);
        game.font.setColor(state.winnerTeam == 1 ? Color.CYAN : Color.SALMON);
        String msg = teamNames[state.winnerTeam - 1].toUpperCase() + " WINS!";
        game.font.draw(b, msg, 1280 / 2f - msg.length() * 14, 720 / 2f + 30);
        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
    }

    private void drawRankedMatchOverOverlay(SpriteBatch b) {
        b.setColor(0f, 0f, 0f, 0.85f);
        b.draw(whitePixel, 0, 0, 1280, 720);
        b.setColor(Color.WHITE);

        int winner = rankedEndMsg.team1RoundWins > rankedEndMsg.team2RoundWins ? 1 : 2;
        game.font.getData().setScale(2.8f);
        game.font.setColor(winner == 1 ? Color.CYAN : Color.SALMON);
        String title = "MATCH OVER — " + teamNames[winner - 1].toUpperCase() + " WINS!";
        game.font.draw(b, title, 640f - title.length() * 15f, 500f);

        game.font.getData().setScale(1.2f);
        game.font.setColor(Color.LIGHT_GRAY);
        String score = "Final Score: " + rankedEndMsg.team1RoundWins + " — " + rankedEndMsg.team2RoundWins;
        game.font.draw(b, score, 640f - score.length() * 7.5f, 420f);

        // Menu button
        float bw = 280f, bh = 60f, bx = 640f - bw / 2f, by = 310f;
        b.setColor(0.15f, 0.15f, 0.25f, 1f);
        b.draw(whitePixel, bx, by, bw, bh);
        b.setColor(Color.GOLD);
        b.draw(whitePixel, bx, by, 4f, bh);
        b.setColor(Color.WHITE);
        game.font.getData().setScale(0.85f);
        game.font.setColor(Color.WHITE);
        game.font.draw(b, "BACK TO MENU", bx, by + bh / 2f + 12f, bw, 1, true);

        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);

        // Handle click on menu button
        if (Gdx.input.justTouched()) {
            Vector3 world = camera.unproject(new Vector3(Gdx.input.getX(), Gdx.input.getY(), 0));
            if (world.x >= bx && world.x <= bx + bw && world.y >= by && world.y <= by + bh) {
                client.disconnect();
                game.setScreen(new MenuScreen(game));
            }
        }
    }

    private void drawBoard(SpriteBatch batch) {
        float tileSize = 58f, startX = BOARD_START_X, startY = BOARD_BOTTOM_Y;
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 9; y++) {
                Character c = state.board.getCharacterAt(x, y);
                Tile t = state.board.getTile(x, y);
                if (t.isCollapsed()) {
                    batch.setColor(0.04f, 0.04f, 0.06f, 1f);
                    batch.draw(whitePixel, startX + x * tileSize, startY + y * tileSize,
                            tileSize - 1, tileSize - 1);
                    if (state.boardConfig.type == BoardConfig.BoardType.WIND) {
                        batch.setColor(Color.WHITE);
                        batch.draw(cloudTileTexture, startX + x * tileSize, startY + y * tileSize,
                                tileSize, tileSize);
                    }
                    continue;
                }
                boolean isHaven = (state.haven != null
                        && x == state.haven.getX() && y == state.haven.getY());
                if (isHaven)                              batch.setColor(Color.GOLD);
                else if ((x + y) % 2 == 0)               batch.setColor(state.boardConfig.tileColorA);
                else                                      batch.setColor(state.boardConfig.tileColorB);
                batch.draw(whitePixel, startX + x * tileSize, startY + y * tileSize,
                        tileSize - 1, tileSize - 1);

                // Subtle team color tint on occupied tiles
                if (c != null && !c.isDead()) {
                    int vTeam = (client != null) ? myTeam : (state.activeUnit != null ? state.activeUnit.team : 1);
                    if (!c.isInvisible() || c.team == vTeam) {
                        if (c.team == 1) batch.setColor(0.2f, 0.7f, 1.0f, 0.2f);
                        else             batch.setColor(1.0f, 0.3f, 0.15f, 0.2f);
                        batch.draw(whitePixel, startX + x * tileSize, startY + y * tileSize, tileSize - 1, tileSize - 1);
                        batch.setColor(Color.WHITE);
                    }
                }

                // Crack overlay — only on tiles that are actually about to collapse
                if (state.haven != null && state.collapseWait < 400f
                        && (state.pendingCollapseCols.size > 0 || state.pendingCollapseRows.size > 0)) {
                    boolean isPending = false;
                    for (int pc : state.pendingCollapseCols) if (x == pc) { isPending = true; break; }
                    if (!isPending) for (int pr : state.pendingCollapseRows) if (y == pr) { isPending = true; break; }
                    if (isPending) {
                        float ca = (400f - state.collapseWait) / 400f;
                        batch.setColor(0.1f, 0.05f, 0f, ca * 0.75f);
                        batch.draw(whitePixel, startX + x*tileSize, startY + y*tileSize, tileSize-1, tileSize-1);
                        batch.setColor(0, 0, 0, ca);
                        batch.draw(whitePixel, startX+x*tileSize+10, startY+y*tileSize, 3, (int)(tileSize*ca));
                        batch.draw(whitePixel, startX+x*tileSize+28, startY+y*tileSize, 3, (int)(tileSize*ca*0.7f));
                        batch.draw(whitePixel, startX+x*tileSize+44, startY+y*tileSize+10, 3, (int)(tileSize*ca*0.85f));
                        batch.setColor(Color.WHITE);
                    }
                }
                if (t.hasStructure() && !t.isPergola() && !t.isDrywall() && !t.isClothes() && !t.isThorn())
                    batch.draw(wallTexture, startX+x*tileSize, startY+y*tileSize, tileSize, tileSize);
                if (t.isPergola())    batch.draw(pergolaTileTexture, startX+x*tileSize, startY+y*tileSize, tileSize, tileSize);
                if (t.isDrywall())    batch.draw(drywallTileTexture, startX+x*tileSize, startY+y*tileSize, tileSize, tileSize);
                if (t.isClothes())    batch.draw(clothesTileTexture, startX+x*tileSize, startY+y*tileSize, tileSize, tileSize);
                if (t.isThorn())      batch.draw(vinesTileTexture,   startX+x*tileSize, startY+y*tileSize, tileSize, tileSize);
                if (t.isPoisoned()) batch.draw(poisonTileTexture, startX+x*tileSize, startY+y*tileSize, tileSize, tileSize);
                if (t.isOnFire())   batch.draw(fireTileTexture,   startX+x*tileSize, startY+y*tileSize, tileSize, tileSize);
                if (t.isFrozen()) {
                    batch.setColor(0.5f, 0.7f, 1.0f, 0.5f);
                    batch.draw(whitePixel, startX+x*tileSize, startY+y*tileSize, tileSize-1, tileSize-1);
                    batch.setColor(Color.WHITE);
                }
                
                if (t.isLockdown()) batch.draw(lockdownTileTexture, startX+x*tileSize, startY+y*tileSize, tileSize, tileSize);

                // Movement range
                if (state.isMovementPhase() || state.isPreGame()) {
                    for (Vector2 tile : state.reachableTiles) {
                        if ((int)tile.x == x && (int)tile.y == y) {
                            boolean sel = state.selectedMoveTile != null
                                    && (int)state.selectedMoveTile.x == x
                                    && (int)state.selectedMoveTile.y == y;
                            batch.setColor(sel ? Color.GREEN : new Color(0.5f, 0.8f, 1f, 1f));
                            batch.draw(whitePixel, startX+x*tileSize, startY+y*tileSize, tileSize, 2);
                            batch.draw(whitePixel, startX+x*tileSize, startY+y*tileSize+tileSize-2, tileSize, 2);
                            batch.draw(whitePixel, startX+x*tileSize, startY+y*tileSize, 2, tileSize);
                            batch.draw(whitePixel, startX+x*tileSize+tileSize-2, startY+y*tileSize, 2, tileSize);
                        }
                    }
                }

                // Ability target range
                if (state.selectedAbility != null) {
                    for (Vector2 tile : state.targetableTiles) {
                        if ((int)tile.x == x && (int)tile.y == y) {
                            boolean isSelected = state.selectedTargetTile != null
                                    && (int)state.selectedTargetTile.x == x
                                    && (int)state.selectedTargetTile.y == y;
                            Color outlineColor = isSelected ? Color.WHITE
                                    : (state.selectedAbility.isHeal
                                            ? new Color(0.2f, 1.0f, 0.2f, 1f)
                                            : new Color(1.0f, 0.2f, 0.2f, 1f));
                            batch.setColor(outlineColor);
                            float bx2 = startX+x*tileSize, by2 = startY+y*tileSize;
                            batch.draw(whitePixel, bx2, by2, tileSize, 2);
                            batch.draw(whitePixel, bx2, by2+tileSize-2, tileSize, 2);
                            batch.draw(whitePixel, bx2, by2, 2, tileSize);
                            batch.draw(whitePixel, bx2+tileSize-2, by2, 2, tileSize);
                        }
                    }
                }
                
                if (t.isCollapsed()) {
                    batch.setColor(0.04f, 0.04f, 0.06f, 1f);
                    batch.draw(whitePixel, startX + x * tileSize, startY + y * tileSize,
                            tileSize - 1, tileSize - 1);
                    // Cloud overlay for Wind Arena collapsed tiles
                    if (state.boardConfig.type == BoardConfig.BoardType.WIND) {
                        batch.setColor(Color.WHITE);
                        batch.draw(cloudTileTexture, startX + x * tileSize, startY + y * tileSize,
                                tileSize, tileSize);
                    }
                    continue;
                }
            }
        }

        // Draw characters
        for (int x = 0; x < 9; x++) {
            for (int y = 0; y < 9; y++) {
                Character chr = state.board.getCharacterAt(x, y);
                if (chr == null || chr.isDead()) continue;
                // Skip Billy only if he hasn't deployed yet (disguise selection phase)
                if (state.isPreGame() && chr instanceof Billy && !chr.hasDeployed) continue;
                // Hide Mason from the enemy team once deployed (he starts invisible)
                // In online mode use myTeam; in local mode hide from non-Mason's team
                int viewerTeam = (client != null) ? myTeam : state.activeUnit.team;
                if (chr instanceof Mason && chr.isInvisible() && chr.team != viewerTeam) continue;
                if (chr.isInvisible() && chr.team != viewerTeam) continue;

                float drawX = startX + chr.x * tileSize + 5;
                float drawY = startY + chr.y * tileSize + 5;

                if (chr == state.activeUnit && !isAnimatingMove)
                    drawY += Math.sin(stateTime * 6.0f) * 5.0f;
                boolean isEnemy = state.activeUnit != null
                        && chr.team != state.activeUnit.team;
                if (state.isBattle() && state.turnPhase == GameState.TurnPhase.ABILITY
                        && isEnemy && !isAnimatingMove)
                    drawX += Math.sin(stateTime * 3.0f) * 3.0f;
                if (chr == animatingUnit && isAnimatingMove) {
                    float vX = moveStart.x + (moveTarget.x - moveStart.x) * moveLerp;
                    float vY = moveStart.y + (moveTarget.y - moveStart.y) * moveLerp;
                    drawX = startX + vX * tileSize + 5;
                    drawY = startY + vY * tileSize + 5;
                }

                Texture portrait = (chr instanceof Billy && ((Billy)chr).disguisedAs != null)
                        ? ((Billy)chr).disguisedAs.getPortrait() : chr.getPortrait();
                batch.setColor(chr.isInvisible() ? new Color(1,1,1,0.5f) : Color.WHITE);
                if (portrait != null) batch.draw(portrait, drawX, drawY, tileSize-10, tileSize-10);
            }
        }
        batch.setColor(Color.WHITE);
    }

    /** True in online play when it is the opponent's turn (not ours). */
    private boolean isOpponentTurn() {
        return client != null && state.activeUnit != null && state.activeUnit.team != myTeam;
    }

    /**
     * Draws 2 small win-indicator boxes in each sidebar (ranked only).
     * Filled = won that round; unfilled = not yet won.
     */
    private void drawRoundWins(SpriteBatch b) {
        if (!isRanked) return;
        float labelY  = TIMELINE_Y + TIMELINE_H / 2f + 8f;
        float boxSize = 16f, boxGap = 5f;
        float boxY    = labelY - boxSize - 2f;  // vertically aligned with the label

        // Team 1 (left sidebar) — right-aligned against the sidebar's inner edge
        for (int i = 0; i < 2; i++) {
            float bx = (SIDEBAR_W - 12f - boxSize) - (1 - i) * (boxSize + boxGap);
            if (i < team1RoundWins) b.setColor(Color.CYAN);
            else                    b.setColor(0.05f, 0.22f, 0.22f, 1f);
            b.draw(whitePixel, bx, boxY, boxSize, boxSize);
        }

        // Team 2 (right sidebar) — right-aligned against the screen's right edge
        for (int i = 0; i < 2; i++) {
            float bx = (1280f - 12f - boxSize) - (1 - i) * (boxSize + boxGap);
            if (i < team2RoundWins) b.setColor(Color.SALMON);
            else                    b.setColor(0.25f, 0.08f, 0.06f, 1f);
            b.draw(whitePixel, bx, boxY, boxSize, boxSize);
        }

        b.setColor(Color.WHITE);
    }

    private void drawSidebars(SpriteBatch b) {
        // Sidebar backgrounds
        b.setColor(0.1f, 0.1f, 0.15f, 1f);
        b.draw(whitePixel, 0, 0, SIDEBAR_W, 720);
        b.draw(whitePixel, 910, 0, SIDEBAR_W, 720);
        b.setColor(Color.WHITE);

        // --- Team 1 label — vertically centered in the timeline band ---
        float labelY = TIMELINE_Y + TIMELINE_H / 2f + 8f;
        game.font.getData().setScale(1.1f);
        game.font.setColor(Color.CYAN);
        game.font.draw(b, teamNames[0].toUpperCase(), 18, labelY);
        game.font.setColor(Color.WHITE);

        // Team 1 unit cards fill from just below the timeline down to the button zone
        float cardAreaTop    = TIMELINE_Y - 4f;
        float cardAreaBottom = BUTTON_Y + BUTTON_H + 8f;
        int t1Count = Math.max(1, state.team1.size);
        float cardSlot1 = (cardAreaTop - cardAreaBottom) / t1Count;
        float cardH1 = Math.min(cardSlot1 - 6f, 148f);
        float t1Y = cardAreaTop;
        for (Character c : state.team1) { drawMiniUnitCard(b, c, 12, t1Y, cardH1, getTurnHighlight(c)); t1Y -= cardSlot1; }

        // --- Team 2 label ---
        float px = 1280 - SIDEBAR_W;
        game.font.getData().setScale(1.1f);
        game.font.setColor(Color.SALMON);
        game.font.draw(b, teamNames[1].toUpperCase(), px + 15, labelY);
        game.font.setColor(Color.WHITE);
        game.font.getData().setScale(1.0f);

        int t2Count = Math.max(1, state.team2.size);
        float cardSlot2 = (cardAreaTop - cardAreaBottom) / t2Count;
        float cardH2 = Math.min(cardSlot2 - 6f, 148f);
        float t2Y = cardAreaTop;
        for (Character c : state.team2) { drawMiniUnitCard(b, c, 912, t2Y, cardH2, getTurnHighlight(c)); t2Y -= cardSlot2; }

        // --- Round win indicators (ranked only) ---
        drawRoundWins(b);

        // --- Bottom-left info panel — matches ability button zone exactly ---
        if (state.selectedAbility != null) {
            b.setColor(0.15f, 0.15f, 0.25f, 1f);
            b.draw(whitePixel, 10, BUTTON_Y, SIDEBAR_W - 20f, BUTTON_H);
            b.setColor(Color.WHITE);
            game.font.getData().setScale(0.85f);
            game.font.setColor(Color.CYAN);
            game.font.draw(b, state.selectedAbility.getName().toUpperCase(),
                    22, BUTTON_Y + BUTTON_H - 8f);
            game.font.setColor(Color.WHITE);
            game.font.getData().setScale(0.75f);
            game.font.draw(b, state.selectedAbility.getDescription(),
                    22, BUTTON_Y + BUTTON_H - 28f, SIDEBAR_W - 36f, -1, true);
        } else if (state.activeUnit != null) {
            game.font.getData().setScale(0.78f);
            game.font.draw(b, "ACTING: " + state.activeUnit.getName().toUpperCase(),
                    22, BUTTON_Y + BUTTON_H / 2f + 8f);
        }
        game.font.getData().setScale(1.0f);
    }

    // Returns 1=gold (active), 2=silver (next), 3=bronze (next+1), 0=none
    private int getTurnHighlight(Character c) {
        if (c.isDead()) return 0;
        Array<Timeline.TimelineEvent> tlEvents = state.timeline.getEvents();
        if (tlEvents.size > 0 && tlEvents.get(0).actor == c) return 1;
        if (tlEvents.size > 1 && tlEvents.get(1).actor == c) return 2;
        if (tlEvents.size > 2 && tlEvents.get(2).actor == c) return 3;
        return 0;
    }

    private void drawMiniUnitCard(SpriteBatch b, Character c, float x, float y,
                                   float cardH, int turnHighlight) {
        float cardW = 346f;

        // Card background
        b.setColor(c.isDead() ? new Color(0.22f,0.07f,0.07f,1f) : new Color(0.14f,0.14f,0.20f,1f));
        b.draw(whitePixel, x, y - cardH, cardW, cardH);

        // Subtle turn-order highlight overlay
        if (turnHighlight == 1) {
            b.setColor(0.55f, 0.45f, 0.05f, 0.20f); // gold
            b.draw(whitePixel, x, y - cardH, cardW, cardH);
        } else if (turnHighlight == 2) {
            b.setColor(0.50f, 0.50f, 0.55f, 0.14f); // silver
            b.draw(whitePixel, x, y - cardH, cardW, cardH);
        } else if (turnHighlight == 3) {
            b.setColor(0.42f, 0.26f, 0.08f, 0.14f); // bronze
            b.draw(whitePixel, x, y - cardH, cardW, cardH);
        }

        // Left-edge accent bar — coloured by turn order
        if      (turnHighlight == 1) b.setColor(Color.GOLD);
        else if (turnHighlight == 2) b.setColor(0.75f, 0.75f, 0.80f, 1f);
        else if (turnHighlight == 3) b.setColor(0.60f, 0.38f, 0.12f, 1f);
        else b.setColor(c.isDead() ? new Color(0.4f,0.1f,0.1f,1f) : new Color(0.3f,0.3f,0.55f,1f));
        b.draw(whitePixel, x, y - cardH, 3, cardH);
        b.setColor(Color.WHITE);

        // Portrait
        float ps = Math.min(cardH - 8f, 80f);
        float px2 = x + 8, py = y - cardH/2f - ps/2f;
        b.setColor(c.isDead() ? new Color(0.45f,0.45f,0.45f,1f) : Color.WHITE);
        if (c.getPortrait() != null) b.draw(c.getPortrait(), px2, py, ps, ps);
        b.setColor(Color.WHITE);

        float cx = x + 8 + ps + 8, cw = cardW - ps - 24;
        // Name + icons share one tall row (28px), then health bar, stats, wait unchanged
        float nameRowH  = 28f;
        float nameY     = y - 6f;            // font baseline near top of row
        float barTop    = y - nameRowH - 2f; // health bar starts just below name row
        float stats1Y   = y - 62f;
        float stats2Y   = y - 80f;
        float waitY     = y - 98f;

        // Name — slightly larger to fill the row
        game.font.getData().setScale(0.70f);
        game.font.setColor(c.isDead() ? Color.GRAY : Color.WHITE);
        game.font.draw(b, c.getName().toUpperCase(), cx, nameY);

        // Type + Class icons — 24x24, vertically centered in the name row, right-aligned
        float iconSize      = 24f;
        float iconY         = y - nameRowH / 2f - iconSize / 2f; // centred in name row
        float iconRightEdge = x + cardW - 8f;
        Texture classIcon = classIcons.get(c.getCharClass());
        Texture typeIcon  = typeIcons.get(c.getCharType());
        b.setColor(c.isDead() ? new Color(1,1,1,0.4f) : Color.WHITE);
        if (classIcon != null) {
            b.draw(classIcon, iconRightEdge - iconSize, iconY, iconSize, iconSize);
            iconRightEdge -= iconSize + 4f;
        }
        if (typeIcon != null) {
            b.draw(typeIcon, iconRightEdge - iconSize, iconY, iconSize, iconSize);
        }
        b.setColor(Color.WHITE);

        // Health bar — taller (HP_BAR_H px)
        b.setColor(Color.BLACK);
        b.draw(whitePixel, cx, barTop - HP_BAR_H, cw, HP_BAR_H);
        b.setColor(c.isDead() ? Color.DARK_GRAY : new Color(0.85f,0.15f,0.15f,1f));
        b.draw(whitePixel, cx, barTop - HP_BAR_H, cw * c.getHealthPercent(), HP_BAR_H);

        // HP text — centered inside the bar
        game.font.getData().setScale(0.44f);
        game.font.setColor(Color.WHITE);
        String hp = c.getHealth() + " / " + c.getMaxHealth();
        float hpTextY = barTop - HP_BAR_H / 2f + 5f;
        game.font.draw(b, hp, cx, hpTextY, cw, 1, false);

        // Stats
        game.font.getData().setScale(0.50f);
        game.font.setColor(new Color(0.85f,0.85f,0.85f,1f));
        game.font.draw(b, "ATK:"+c.getAtk()+"   ARM:"+c.getArmor(), cx, stats1Y);
        game.font.draw(b, "MAG:"+c.getMag()+"   CLK:"+c.getCloak(), cx, stats2Y);

        // Wait
        game.font.getData().setScale(0.48f);
        game.font.setColor(Color.GOLD);
        game.font.draw(b, "WAIT: "+(int)c.getCurrentWait()+" / "+c.getSpeed(), cx, waitY);

        game.font.setColor(Color.WHITE);
        game.font.getData().setScale(1.0f);
        b.setColor(Color.WHITE);
    }

    private void drawTimeline(SpriteBatch b) {
        float startX = BOARD_START_X, startY = TIMELINE_Y, size = TIMELINE_H;
        Array<Timeline.TimelineEvent> events = state.timeline.getEvents();
        int count = Math.min(events.size, 9);

        // Character portrait slots
        for (int i = 0; i < count; i++) {
            Character actor = events.get(i).actor;
            b.setColor(actor.team == 1 ? Color.CYAN : Color.SALMON);
            b.draw(whitePixel, startX + i*(size+10), startY, size, size);
            b.setColor(Color.WHITE);
            Texture portrait = (actor instanceof Billy && ((Billy)actor).disguisedAs != null)
                    ? ((Billy)actor).disguisedAs.getPortrait() : actor.getPortrait();
            if (portrait != null) b.draw(portrait, startX + i*(size+10)+5, startY+5, size-10, size-10);
        }

        float barW = count * (size + 10) - 10;
        b.setColor(0.15f, 0.15f, 0.15f, 1f);
        b.draw(whitePixel, startX, startY - 10, barW, 6);

        // Ring/wind fall divider — only for timed-collapse boards, not Desert
        boolean showCollapse = state.isBattle() && state.boardConfig != null
                && state.boardConfig.collapseStyle != BoardConfig.CollapseStyle.DESERT_TILE;
        if (showCollapse) {
            float urgency = Math.min(1f, 1f - (state.collapseWait / 1000f));
            float r = 1f, g = Math.max(0f, 0.5f - urgency * 0.5f);

            // Progress bar fill
            b.setColor(urgency > 0.8f ? Color.RED : new Color(1f, 0.5f, 0.1f, 1f));
            b.draw(whitePixel, startX, startY - 10, barW * (state.collapseWait / 1000f), 6);

            // Find the first slot whose actor won't get a turn before the ring/wind falls
            int ringFallBefore = count;
            for (int i = 0; i < count; i++) {
                if (events.get(i).actor.getCurrentWait() >= state.collapseWait) {
                    ringFallBefore = i;
                    break;
                }
            }

            // Vertical divider in the gap before that slot
            float divX = startX + ringFallBefore * (size + 10) - 7f;
            b.setColor(r, g, 0f, 1f);
            b.draw(whitePixel, divX, startY, 4, size);

            // Small label above the divider
            game.font.getData().setScale(0.32f);
            game.font.setColor(r, g, 0f, 1f);
            String label = state.boardConfig.collapseStyle == BoardConfig.CollapseStyle.WIND_PUSH
                    ? "WIND" : "RING";
            game.font.draw(b, label, divX - 2f, startY + size + 12f);
            game.font.getData().setScale(1.0f);
            game.font.setColor(Color.WHITE);
        }

        b.setColor(Color.WHITE);
    }

    private void drawAbilityButtons(SpriteBatch b) {
        float boardWidth  = 9 * 58f;
        float buttonWidth = boardWidth / 3f;
        float padding     = 3f;

        boolean dimmed = isOpponentTurn() || state.isMovementPhase();

        for (int i = 0; i < 3; i++) {
            Ability ab = (state.activeUnit != null) ? state.activeUnit.getAbility(i) : null;
            float btnX = BOARD_START_X + i * buttonWidth + padding;
            float btnW = buttonWidth - padding * 2f;

            boolean locked = (i==1 && state.activeUnit instanceof Hunter && state.activeUnit.isInvisible())
                          || (i==2 && state.activeUnit != null && state.activeUnit.isUltUsed());
            boolean sel    = !dimmed && ab != null && state.selectedAbility == ab;

            if (locked || dimmed) b.setColor(0.08f, 0.08f, 0.10f, 1f);
            else if (sel)         b.setColor(0.35f, 0.35f, 0.65f, 1f);
            else                  b.setColor(0.16f, 0.16f, 0.22f, 1f);
            b.draw(whitePixel, btnX, BUTTON_Y, btnW, BUTTON_H);
            b.setColor(Color.WHITE);

            if (ab != null) {
                boolean hasStats = ab.showAtk || ab.showMag || ab.showHeal
                        || ab.armorBuff > 0 || ab.cloakBuff > 0;
                float nameYShift = hasStats ? 10f : 0f;

                // Active / Passive label at top of button
                String typeLabel = ab.isPassive ? "PASSIVE" : "ACTIVE";
                game.font.getData().setScale(0.36f);
                if (locked || dimmed)      game.font.setColor(Color.DARK_GRAY);
                else if (ab.isPassive)     game.font.setColor(0.75f, 0.40f, 0.90f, 1f);
                else                       game.font.setColor(0.55f, 0.55f, 0.65f, 1f);
                game.font.draw(b, typeLabel, btnX, BUTTON_Y + BUTTON_H - 6f, btnW, 1, false);

                String name = ab.getName().toUpperCase();
                float maxScale = 0.62f, minScale = 0.34f;
                game.font.setColor((locked || dimmed) ? Color.DARK_GRAY : (sel ? Color.CYAN : Color.WHITE));

                if (name.contains(" ")) {
                    int mid = name.lastIndexOf(' ', name.length() / 2 + 2);
                    if (mid < 0) mid = name.indexOf(' ');
                    String l1 = name.substring(0, mid).trim();
                    String l2 = name.substring(mid).trim();
                    int longest = Math.max(l1.length(), l2.length());
                    float scale = Math.max(minScale, Math.min(maxScale,
                            btnW * 0.88f / (longest * 7f)));
                    float lineH = 7f * scale * 4.8f;
                    game.font.getData().setScale(scale);
                    float topY = BUTTON_Y + BUTTON_H / 2f + lineH * 0.55f + nameYShift;
                    game.font.draw(b, l1, btnX, topY,        btnW, 1, false);
                    game.font.draw(b, l2, btnX, topY - lineH, btnW, 1, false);
                } else {
                    float scale = Math.max(minScale, Math.min(maxScale,
                            btnW * 0.88f / (name.length() * 7f)));
                    game.font.getData().setScale(scale);
                    float textY = BUTTON_Y + BUTTON_H / 2f + (7f * scale / 2f) + nameYShift;
                    game.font.draw(b, name, btnX, textY, btnW, 1, false);
                }

                // Stat hints below the ability name
                if (hasStats && state.activeUnit != null) {
                    int count = (ab.showAtk ? 1 : 0) + (ab.showMag ? 1 : 0)
                              + (ab.showHeal ? 1 : 0)
                              + (ab.armorBuff > 0 ? 1 : 0) + (ab.cloakBuff > 0 ? 1 : 0);
                    float segW  = btnW / count;
                    float statY = BUTTON_Y + 22f;
                    int   slot  = 0;
                    game.font.getData().setScale(0.38f);
                    boolean greyStats = locked || dimmed;
                    if (ab.showAtk) {
                        game.font.setColor(greyStats ? Color.DARK_GRAY : STAT_ATK_COLOR);
                        game.font.draw(b, "ATK " + state.activeUnit.getAtk(), btnX + slot * segW, statY, segW, 1, false);
                        slot++;
                    }
                    if (ab.showMag) {
                        game.font.setColor(greyStats ? Color.DARK_GRAY : STAT_MAG_COLOR);
                        game.font.draw(b, "MAG " + state.activeUnit.getMag(), btnX + slot * segW, statY, segW, 1, false);
                        slot++;
                    }
                    if (ab.showHeal) {
                        game.font.setColor(greyStats ? Color.DARK_GRAY : STAT_HEAL_COLOR);
                        game.font.draw(b, "+" + state.activeUnit.getMag() + " HP", btnX + slot * segW, statY, segW, 1, false);
                        slot++;
                    }
                    if (ab.armorBuff > 0) {
                        game.font.setColor(greyStats ? Color.DARK_GRAY : STAT_ARMOR_COLOR);
                        game.font.draw(b, "+" + ab.armorBuff + " ARM", btnX + slot * segW, statY, segW, 1, false);
                        slot++;
                    }
                    if (ab.cloakBuff > 0) {
                        game.font.setColor(greyStats ? Color.DARK_GRAY : STAT_CLOAK_COLOR);
                        game.font.draw(b, "+" + ab.cloakBuff + " CLK", btnX + slot * segW, statY, segW, 1, false);
                    }
                }

                game.font.getData().setScale(1.0f);
                game.font.setColor(Color.WHITE);

            } else {
                game.font.setColor(Color.DARK_GRAY);
                game.font.getData().setScale(0.55f);
                game.font.draw(b, "---", btnX, BUTTON_Y + BUTTON_H / 2f + 6f, btnW, 1, false);
                game.font.getData().setScale(1.0f);
                game.font.setColor(Color.WHITE);
            }
        }
        b.setColor(Color.WHITE);
    }

    private void drawActionTriggerButton(SpriteBatch batch) {
        float x = 930f, w = 350f;
        if (isOpponentTurn())                   batch.setColor(0.10f, 0.10f, 0.10f, 1f);
        else if (state.isMovementPhase())       batch.setColor(0.2f, 0.4f, 0.2f, 1f);
        else if (state.selectedAbility != null) batch.setColor(0.4f, 0.2f, 0.2f, 1f);
        else                                    batch.setColor(0.3f, 0.3f, 0.3f, 1f);
        batch.draw(whitePixel, x, BUTTON_Y, w, BUTTON_H);
        batch.setColor(Color.WHITE);

        String label;
        if (isOpponentTurn()) {
            label = "WAIT";
            game.font.setColor(Color.DARK_GRAY);
        } else if (state.isMovementPhase()) {
            label = (state.selectedMoveTile != null) ? "CONFIRM MOVE" : "STAY HERE";
            game.font.setColor(Color.WHITE);
        } else if (state.selectedAbility != null) {
            if (state.selectedAbility.getName().equals("Holy Light")) label = "CAST HOLY LIGHT";
            else label = state.selectedAbility.isHeal ? "EXECUTE HEAL" : "CONFIRM ATTACK";
            game.font.setColor(Color.WHITE);
        } else {
            label = "PASS TURN";
            game.font.setColor(Color.WHITE);
        }
        game.font.getData().setScale(0.85f);
        game.font.draw(batch, label, x, BUTTON_Y + BUTTON_H / 2f + 8f, w, 1, false);
        game.font.getData().setScale(1.0f);
        game.font.setColor(Color.WHITE);
    }

    private void drawBillySelectionWindow(SpriteBatch b) {
        float winW=600, winH=260, winX=(1280-winW)/2f, winY=(720-winH)/2f;
        b.setColor(0.1f,0.1f,0.2f,0.95f);
        b.draw(whitePixel, winX, winY, winW, winH);
        b.setColor(Color.GOLD);
        b.draw(whitePixel, winX, winY+winH-2, winW, 2f);
        b.setColor(Color.WHITE);
        game.font.getData().setScale(0.75f);
        game.font.draw(b, "BILLY: SELECT DISGUISE", winX+40, winY+winH-20);
        game.font.getData().setScale(0.50f);
        game.font.setColor(0.7f, 0.7f, 0.8f, 1f);
        game.font.draw(b, "Click a teammate to disguise as them", winX+40, winY+winH-46);
        game.font.setColor(Color.WHITE);
        game.font.getData().setScale(1.0f);
        disguiseOptionBounds.clear();
        Array<Character> teammates = (state.activeUnit.team==1) ? state.team1 : state.team2;
        float iconSize=80, spacing=110, sx=winX+50, iy=winY+60;
        for (Character ally : teammates) {
            if (ally == state.activeUnit) continue;
            b.setColor(Color.WHITE);
            if (ally.getPortrait() != null) b.draw(ally.getPortrait(), sx, iy, iconSize, iconSize);
            // name label below portrait
            game.font.getData().setScale(0.45f);
            game.font.setColor(Color.WHITE);
            game.font.draw(b, ally.getName(), sx, iy - 4f);
            game.font.getData().setScale(1.0f);
            disguiseOptionBounds.add(new Rectangle(sx, iy, iconSize, iconSize));
            sx += spacing;
        }
        b.setColor(Color.WHITE);
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------
    private Vector2 getTileScreenPos(int tx, int ty) {
        return new Vector2(BOARD_START_X + tx*58f + 29f, BOARD_BOTTOM_Y + ty*58f + 29f);
    }

    private int getAbilityIndex(Ability ab) {
        if (state.activeUnit == null) return -1;
        for (int i = 0; i < 3; i++)
            if (state.activeUnit.getAbility(i) == ab) return i;
        return -1;
    }

    public void moveHavenTo(int newX, int newY) {
        engine.moveHaven(state, newX, newY, new Array<>());
    }

    @Override public void show()   {}
    @Override public void resize(int w, int h) { viewport.update(w, h); }
    @Override public void pause()  {}
    @Override public void resume() {}
    @Override public void hide()   {}
    @Override public void dispose() {
        whitePixel.dispose();
        pergolaTileTexture.dispose();
        vinesTileTexture.dispose();
        drywallTileTexture.dispose();
        clothesTileTexture.dispose();
        cloudTileTexture.dispose();
        lockdownTileTexture.dispose();
        for (Texture t : typeIcons.values())  t.dispose();
        for (Texture t : classIcons.values()) t.dispose();
    }
}