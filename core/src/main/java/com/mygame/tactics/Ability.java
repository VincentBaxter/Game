package com.mygame.tactics;

import com.badlogic.gdx.utils.Array;

/**
 * Base class for all character abilities.
 *
 * execute() now receives GameState and an event list instead of CombatScreen.
 * Abilities append EngineEvents (popups, animations) to the list; GameEngine
 * applies damage/healing by calling helpers on GameState directly.
 */
public abstract class Ability {
    protected String  name;
    protected String  description;
    protected int     range;
    protected boolean needsTarget;
    public    boolean isHeal             = false;
    public    boolean isPassive          = false; // auto-triggered, never clicked by player
    public    boolean isDirectionAbility = false; // range==0 but needs a direction tile click
    public    boolean showAtk    = false;
    public    boolean showMag    = false;
    public    boolean showHeal   = false;
    public    int     armorBuff  = 0;   // > 0 renders beige armor bonus
    public    int     cloakBuff  = 0;   // > 0 renders blue cloak bonus

    public Ability(String name, String description, int range, boolean needsTarget) {
        this.name        = name;
        this.description = description;
        this.range       = range;
        this.needsTarget = needsTarget;
    }

    public String  getName()        { return name; }
    public String  getDescription() { return description; }
    public int     getRange()       { return range; }
    public boolean needsTarget()    { return needsTarget; }

    /**
     * Executes the ability's game-logic effects.
     *
     * @param user    The acting character.
     * @param target  The character on the target tile (may be null).
     * @param state   Full game state — abilities may read and mutate this.
     * @param events  Append EngineEvents here for the renderer to consume.
     * @param tx      Target tile X.
     * @param ty      Target tile Y.
     * @return AbilityResult telling GameEngine what phase transition to perform.
     */
    public abstract AbilityResult execute(Character user, Character target,
                                          GameState state, Array<EngineEvent> events,
                                          int tx, int ty);
}