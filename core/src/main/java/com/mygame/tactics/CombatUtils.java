package com.mygame.tactics;

import com.badlogic.gdx.utils.Array;

/**
 * Stateless combat math helpers shared by all ability implementations.
 *
 * Crit Chance  — if the attacker's critChance roll succeeds, the final
 *                post-mitigation damage is doubled.  The check happens
 *                AFTER armor/cloak reduction so big-armor targets still
 *                feel the spike.
 *
 * Dodge Chance — if the defender's dodgeChance roll succeeds, the attack
 *                is completely avoided.  Only applies when an enemy is
 *                targeting the defender (ally heals / buffs are never
 *                dodged).
 *
 * Usage pattern in an Ability:
 *
 *   if (target != null && target.team != user.team) {
 *       if (CombatUtils.rollDodge(target, events, tx, ty)) return AbilityResult.END_TURN;
 *       int raw   = user.getAtk();
 *       int final = Math.max(0, raw - target.getArmor());
 *       final = CombatUtils.applyCrit(user, final, events, tx, ty);
 *       target.health = Math.max(0, target.health - final);
 *       events.add(new EngineEvent.PopupEvent("HIT", final, "ATK", tx, ty));
 *   }
 */
public final class CombatUtils {

    private CombatUtils() {}   // utility class — no instantiation

    /**
     * Rolls a dodge check against the defending character.
     *
     * @param target  The character being attacked.
     * @param events  Event list — a "DODGED!" popup is appended on success.
     * @param tx      Target tile X (for popup placement).
     * @param ty      Target tile Y (for popup placement).
     * @return {@code true} if the attack is dodged and should be skipped.
     */
    public static boolean rollDodge(Character target,
                                    Array<EngineEvent> events,
                                    int tx, int ty) {
        if (target.dodgeChance <= 0.0) return false;
        if (Math.random() < target.dodgeChance) {
            events.add(new EngineEvent.PopupEvent("DODGED!", 0, "STATUS", tx, ty));
            return true;
        }
        return false;
    }

    /**
     * Applies a crit check for the attacking character and returns the
     * (possibly doubled) final damage value.
     *
     * Call this AFTER armor/cloak mitigation has already been applied.
     *
     * @param user        The attacking character.
     * @param finalDamage Post-mitigation damage value.
     * @param events      Event list — a "CRIT!" popup is appended on success.
     * @param tx          Target tile X (for popup placement).
     * @param ty          Target tile Y (for popup placement).
     * @return {@code finalDamage * 2} on a crit, otherwise {@code finalDamage}.
     */
    public static int applyCrit(Character user, int finalDamage,
                                Array<EngineEvent> events,
                                int tx, int ty) {
        if (user.critChance <= 0.0) return finalDamage;
        if (Math.random() < user.critChance) {
            events.add(new EngineEvent.PopupEvent("CRIT!", 0, "ACTIVE", tx, ty));
            return finalDamage * 2;
        }
        return finalDamage;
    }
}