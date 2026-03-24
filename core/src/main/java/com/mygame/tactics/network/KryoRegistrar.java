package com.mygame.tactics.network;

import com.esotericsoftware.kryo.Kryo;
import com.mygame.tactics.Action;
import com.mygame.tactics.Ability;
import com.mygame.tactics.BoardConfig;
import com.mygame.tactics.CombatBoard;
import com.mygame.tactics.Character;
import com.mygame.tactics.EngineEvent;
import com.mygame.tactics.Enums;
import com.mygame.tactics.GameState;
import com.mygame.tactics.Haven;
import com.mygame.tactics.NetworkAction;
import com.mygame.tactics.NetworkMessage;
import com.mygame.tactics.Tile;
import com.mygame.tactics.Timeline;
import com.mygame.tactics.characters.Aaron;
import com.mygame.tactics.characters.Anna;
import com.mygame.tactics.characters.Ben;
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
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import org.objenesis.strategy.StdInstantiatorStrategy;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Single source of truth for Kryo class registration.
 * Both GameServer and NetworkClient call this before opening connections.
 * Every class that crosses the network MUST be registered here.
 *
 * Uses StdInstantiatorStrategy (via Objenesis) so classes without no-arg
 * constructors (e.g. all Character subclasses) can still be deserialized.
 */
public class KryoRegistrar {

    private KryoRegistrar() {} // utility class

    public static void register(Kryo kryo) {
        // Use Objenesis to instantiate classes without no-arg constructors.
        // Required for Character subclasses (Mason, Hunter, etc.) which all
        // take a Texture in their constructor and have no no-arg form.
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());

        // Safety net — serialize unregistered types by name rather than crash.
        kryo.setRegistrationRequired(false);

        // --- Network wrapper classes ---
        kryo.register(NetworkAction.class);
        kryo.register(NetworkMessage.class);
        kryo.register(NetworkMessage.Type.class);

        // --- Action subclasses ---
        kryo.register(Action.class);
        kryo.register(Action.MoveAction.class);
        kryo.register(Action.AbilityAction.class);
        kryo.register(Action.PassAction.class);
        kryo.register(Action.DeployAction.class);
        kryo.register(Action.ChooseDisguiseAction.class);
        kryo.register(Action.TwoStepAbilityAction.class);
        kryo.register(Action.DraftPickAction.class);
        kryo.register(Action.BoardChoiceAction.class);

        // --- EngineEvent subclasses ---
        kryo.register(EngineEvent.class);
        kryo.register(EngineEvent.PopupEvent.class);
        kryo.register(EngineEvent.MoveAnimationEvent.class);
        kryo.register(EngineEvent.TileEffectEvent.class);
        kryo.register(EngineEvent.TileEffectEvent.Effect.class);
        kryo.register(EngineEvent.GameOverEvent.class);
        kryo.register(EngineEvent.AbilityResolveEvent.class);
        kryo.register(EngineEvent.CharacterKilledEvent.class);
        kryo.register(EngineEvent.PortraitChangeEvent.class);
        kryo.register(EngineEvent.HavenMoveEvent.class);

        // --- Game state classes ---
        kryo.register(GameState.class);
        kryo.register(GameState.Phase.class);
        kryo.register(GameState.TurnPhase.class);
        kryo.register(CombatBoard.class);
        kryo.register(Tile.class);
        kryo.register(Haven.class);
        kryo.register(Timeline.class);
        kryo.register(Timeline.TimelineEvent.class);
        kryo.register(Timeline.EventType.class);
        kryo.register(Ability.class);

        // --- Character base and all subclasses ---
        kryo.register(Character.class);
        kryo.register(Aaron.class);
        kryo.register(Anna.class);
        kryo.register(Ben.class);
        kryo.register(Billy.class);
        kryo.register(Brad.class);
        kryo.register(Emily.class);
        kryo.register(Evan.class);
        kryo.register(Ghia.class);
        kryo.register(GuardTower.class);
        kryo.register(Hunter.class);
        kryo.register(Jaxon.class);
        kryo.register(Lark.class);
        kryo.register(Luke.class);
        kryo.register(Mason.class);
        kryo.register(Maxx.class);
        kryo.register(Nathan.class);
        kryo.register(Sean.class);
        kryo.register(Snowguard.class);
        kryo.register(Speen.class);
        kryo.register(Stoneguard.class);
        kryo.register(Thomas.class);
        kryo.register(Tyler.class);
        kryo.register(Weirdguard.class);

        // --- Enums ---
        kryo.register(Enums.Alliance.class);
        kryo.register(Enums.CharClass.class);
        kryo.register(Enums.CharType.class);
        kryo.register(Enums.Rarity.class);
        kryo.register(BoardConfig.class);
        kryo.register(BoardConfig.BoardType.class);
        kryo.register(BoardConfig.CollapseStyle.class);

        // --- LibGDX types ---
        kryo.register(Vector2.class);
        kryo.register(Color.class);
        kryo.register(Array.class);

        // --- Java collections and primitives ---
        kryo.register(ArrayList.class);
        kryo.register(HashMap.class);
        kryo.register(String.class);
        kryo.register(String[].class);
        kryo.register(Object[].class);
        kryo.register(int[].class);
        kryo.register(float[].class);
        kryo.register(boolean[].class);
        kryo.register(char[].class);

        System.out.println("Kryo classes registered.");
    }
}