package com.mygame.tactics;

/**
 * Returned by Ability.execute() to tell CombatScreen what should happen next.
 *
 * Abilities express their *intent* here instead of reaching into the screen
 * and mutating it directly. The screen owns phase transitions; abilities own
 * game state. This is the contract between them.
 *
 * Future multiplayer note: a server will run execute() and read this result
 * to determine the next valid action — no Screen required on the server side.
 */
public enum AbilityResult {

    /**
     * Normal end-of-ability flow.
     * CombatScreen applies the standard class transition:
     *   FIGHTER  → bonus 1-tile move, then end turn
     *   SNIPER   → movement phase, then end turn
     *   everyone else → end turn immediately
     */
    END_TURN,

    /**
     * The ability grants the user a fresh movement phase right now.
     * CombatScreen switches to MOVEMENT and recalculates range.
     * Used by: Vanish, Snake In The Grass, Poison Trail, Marathon,
     *          Take Flight, Grand Entrance.
     */
    GRANT_MOVEMENT,

    /**
     * The ability needs a second tile click before it can fully execute.
     * CombatScreen stays in ABILITY phase and waits.
     * Used by: Painted Walls (anchor -> direction),
     *          Wall of Fire (anchor -> direction).
     */
    AWAIT_SECOND_CLICK,

    /**
     * The ability killed its target and the user enters a special
     * extended-turn state (Hunter's Dagger's Cull on kill).
     * CombatScreen grants a fresh movement phase without ending the turn.
     */
    GRANT_RESET_ON_KILL,
}