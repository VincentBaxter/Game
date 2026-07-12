package com.mygame.tactics;

import com.mygame.tactics.characters.Aaron;
import com.mygame.tactics.characters.Aevan;
import com.mygame.tactics.characters.Anna;
import com.mygame.tactics.characters.Ben;
import com.mygame.tactics.characters.Billy;
import com.mygame.tactics.characters.Brad;
import com.mygame.tactics.characters.Emily;
import com.mygame.tactics.characters.Evan;
import com.mygame.tactics.characters.Fescue;
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
import com.mygame.tactics.characters.Willow;

/**
 * Central registry of all playable characters.
 *
 * To add a new character: add one Entry line here.
 * GameServer, KryoRegistrar, and the online draft pool all derive from this
 * list automatically — no other files need updating.
 *
 * Order here matches DraftScreen.buildPool() (which still manages its own
 * texture loading, but must stay in sync with NAMES for pick-name lookups).
 */
public class CharacterRoster {

    private static final Entry[] ENTRIES = {
        new Entry("Hunter",     Hunter.class,     () -> new Hunter(null)),
        new Entry("Sean",       Sean.class,       () -> new Sean(null)),
        new Entry("Jaxon",      Jaxon.class,      () -> new Jaxon(null)),
        new Entry("Evan",       Evan.class,       () -> new Evan(null)),
        new Entry("Billy",      Billy.class,      () -> new Billy(null)),
        new Entry("Aaron",      Aaron.class,      () -> new Aaron(null)),
        new Entry("Speen",      Speen.class,      () -> new Speen(null)),
        new Entry("Mason",      Mason.class,      () -> new Mason(null)),
        new Entry("Lark",       Lark.class,       () -> new Lark(null)),
        new Entry("Nathan",     Nathan.class,     () -> new Nathan(null)),
        new Entry("Luke",       Luke.class,       () -> new Luke(null)),
        new Entry("Brad",       Brad.class,       () -> new Brad(null)),
        new Entry("GuardTower", GuardTower.class, () -> new GuardTower(null)),
        new Entry("Weirdguard", Weirdguard.class, () -> new Weirdguard(null)),
        new Entry("Stoneguard", Stoneguard.class, () -> new Stoneguard(null)),
        new Entry("Snowguard",  Snowguard.class,  () -> new Snowguard(null)),
        new Entry("Tyler",      Tyler.class,      () -> new Tyler(null)),
        new Entry("Anna",       Anna.class,       () -> new Anna(null)),
        new Entry("Emily",      Emily.class,      () -> new Emily(null)),
        new Entry("Thomas",     Thomas.class,     () -> new Thomas(null)),
        new Entry("Ghia",       Ghia.class,       () -> new Ghia(null)),
        new Entry("Maxx",       Maxx.class,       () -> new Maxx(null)),
        new Entry("Ben",        Ben.class,        () -> new Ben(null)),
        new Entry("Aevan",      Aevan.class,      () -> new Aevan(null)),
        new Entry("Fescue",     Fescue.class,     () -> new Fescue(null)),
        new Entry("Willow",     Willow.class,     () -> new Willow(null)),
    };

    /** All character names in draft pool order. */
    public static final String[] NAMES;

    /** All character classes in the same order — used by KryoRegistrar. */
    public static final Class<?>[] CLASSES;

    static {
        NAMES   = new String[ENTRIES.length];
        CLASSES = new Class<?>[ENTRIES.length];
        for (int i = 0; i < ENTRIES.length; i++) {
            NAMES[i]   = ENTRIES[i].name;
            CLASSES[i] = ENTRIES[i].clazz;
        }
    }

    /**
     * Builds a headless (null-texture) instance of the named character.
     * Returns null and logs a warning if the name is unrecognised.
     */
    public static Character build(String name) {
        for (Entry e : ENTRIES)
            if (e.name.equals(name)) return e.factory.create();
        System.out.println("WARNING: unknown character name: " + name);
        return null;
    }

    private CharacterRoster() {}

    private static class Entry {
        final String     name;
        final Class<?>   clazz;
        final Factory    factory;
        Entry(String name, Class<?> clazz, Factory factory) {
            this.name    = name;
            this.clazz   = clazz;
            this.factory = factory;
        }
    }

    @FunctionalInterface
    private interface Factory { Character create(); }
}
