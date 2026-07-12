package com.mygame.tactics;

import java.util.ArrayList;
import java.util.List;

public class PlayerInventory {

    public Enums.CharClass charClass = Enums.CharClass.FIGHTER;
    public Enums.CharType  charType  = Enums.CharType.FAUNA;

    public int gold = 0;

    // Base stats (before equipment)
    public int    baseMaxHealth      = 80;
    public int    baseAtk            = 10;
    public int    baseMag            = 5;
    public int    baseArmor          = 5;
    public int    baseCloak          = 5;
    public int    baseSpeed          = 1000;
    public double baseSpeedReduction = 1.0;
    public int    baseMoveDist       = 2;
    public int    baseRange          = 1;
    public double baseCritChance     = 0.0;
    public double baseDodgeChance    = 0.0;

    public int currentHealth;

    // Equipment slots
    public Item helmet, body, shoes, weapon, special;

    // Bag — 16 slots, null = empty
    public Item[] bag = new Item[16];

    // Runic Stone — deposited souls unlock ability pools
    public List<String> unlockedCharacters = new ArrayList<>();

    // Selected abilities for quest combat — "CharName:abilityIndex" or empty
    public String abilitySlot1 = "";
    public String abilitySlot2 = "";
    public String abilitySlot3 = "";

    public PlayerInventory() {
        currentHealth = baseMaxHealth;
    }

    // ---- Computed stats (base + all equipped items) ----

    public int    getMaxHealth()         { return baseMaxHealth; }
    public int    getAtk()               { return baseAtk   + eqInt(i -> i.atkMod); }
    public int    getMag()               { return baseMag   + eqInt(i -> i.magMod); }
    public int    getArmor()             { return baseArmor + eqInt(i -> i.armorMod); }
    public int    getCloak()             { return baseCloak + eqInt(i -> i.cloakMod); }
    public int    getSpeed()             { return baseSpeed + eqInt(i -> i.speedMod); }
    public double getSpeedReduction()    { return baseSpeedReduction; }
    public int    getMoveDist()          { return baseMoveDist + eqInt(i -> i.moveDistMod); }
    public int    getRange()             { return baseRange    + eqInt(i -> i.rangeMod); }
    public double getCritChance()        { return baseCritChance  + eqDbl(i -> i.critMod); }
    public double getDodgeChance()       { return baseDodgeChance + eqDbl(i -> i.dodgeMod); }

    private interface IntGetter { int    get(Item i); }
    private interface DblGetter { double get(Item i); }

    private int eqInt(IntGetter g) {
        return (helmet  != null ? g.get(helmet)  : 0)
             + (body    != null ? g.get(body)    : 0)
             + (shoes   != null ? g.get(shoes)   : 0)
             + (weapon  != null ? g.get(weapon)  : 0)
             + (special != null ? g.get(special) : 0);
    }

    private double eqDbl(DblGetter g) {
        return (helmet  != null ? g.get(helmet)  : 0.0)
             + (body    != null ? g.get(body)    : 0.0)
             + (shoes   != null ? g.get(shoes)   : 0.0)
             + (weapon  != null ? g.get(weapon)  : 0.0)
             + (special != null ? g.get(special) : 0.0);
    }

    /** Add item to first empty bag slot. Returns false if full. */
    public boolean addToBag(Item item) {
        for (int i = 0; i < bag.length; i++) {
            if (bag[i] == null) { bag[i] = item; return true; }
        }
        return false;
    }

    /** Returns true if a soul for this character is in the bag. */
    public boolean hasSoulInBag(String charName) {
        String soulName = charName + " Soul";
        for (Item item : bag)
            if (item != null && item.name.equals(soulName)) return true;
        return false;
    }

    /** Counts how many items with the given name are in the bag. */
    public int countInBag(String name) {
        int n = 0;
        for (Item item : bag) if (item != null && item.name.equals(name)) n++;
        return n;
    }

    /** Removes up to count items with the given name. Returns how many were removed. */
    public int removeFromBag(String name, int count) {
        int removed = 0;
        for (int i = 0; i < bag.length && removed < count; i++) {
            if (bag[i] != null && bag[i].name.equals(name)) { bag[i] = null; removed++; }
        }
        return removed;
    }

    /** Removes a soul item from the bag. Returns true if found and removed. */
    public boolean removeSoulFromBag(String charName) {
        String soulName = charName + " Soul";
        for (int i = 0; i < bag.length; i++) {
            if (bag[i] != null && bag[i].name.equals(soulName)) {
                bag[i] = null;
                return true;
            }
        }
        return false;
    }

    // ---- Persistence ----

    public void save(PlayerFlags flags) {
        flags.set("inv_gold",   gold);
        flags.set("inv_health", currentHealth);
        flags.setString("inv_class",    charClass.name());
        flags.setString("inv_type",     charType.name());
        flags.setString("inv_ability1", abilitySlot1);
        flags.setString("inv_ability2", abilitySlot2);
        flags.setString("inv_ability3", abilitySlot3);
        flags.setString("inv_unlocked", String.join(",", unlockedCharacters));
        saveSlot(flags, "inv_eq_helm",    helmet);
        saveSlot(flags, "inv_eq_body",    body);
        saveSlot(flags, "inv_eq_shoes",   shoes);
        saveSlot(flags, "inv_eq_weapon",  weapon);
        saveSlot(flags, "inv_eq_special", special);
        for (int i = 0; i < bag.length; i++)
            saveSlot(flags, "inv_bag_" + i, bag[i]);
    }

    public void load(PlayerFlags flags) {
        gold          = flags.get("inv_gold");
        currentHealth = flags.get("inv_health");
        if (currentHealth <= 0) currentHealth = baseMaxHealth;
        charClass    = parseClass(flags.getString("inv_class",    "FIGHTER"));
        charType     = parseType(flags.getString("inv_type",     "FAUNA"));
        abilitySlot1 = flags.getString("inv_ability1", "");
        abilitySlot2 = flags.getString("inv_ability2", "");
        abilitySlot3 = flags.getString("inv_ability3", "");
        String unlocked = flags.getString("inv_unlocked", "");
        unlockedCharacters.clear();
        if (!unlocked.isEmpty())
            for (String s : unlocked.split(","))
                if (!s.isEmpty()) unlockedCharacters.add(s);
        helmet  = loadSlot(flags, "inv_eq_helm");
        body    = loadSlot(flags, "inv_eq_body");
        shoes   = loadSlot(flags, "inv_eq_shoes");
        weapon  = loadSlot(flags, "inv_eq_weapon");
        special = loadSlot(flags, "inv_eq_special");
        for (int i = 0; i < bag.length; i++)
            bag[i] = loadSlot(flags, "inv_bag_" + i);
    }

    private static void saveSlot(PlayerFlags flags, String key, Item item) {
        flags.setString(key, item != null ? item.serialize() : "");
    }

    private static Item loadSlot(PlayerFlags flags, String key) {
        return Item.deserialize(flags.getString(key, ""));
    }

    private static Enums.CharClass parseClass(String s) {
        try { return Enums.CharClass.valueOf(s); } catch (Exception e) { return Enums.CharClass.FIGHTER; }
    }

    private static Enums.CharType parseType(String s) {
        try { return Enums.CharType.valueOf(s); } catch (Exception e) { return Enums.CharType.FAUNA; }
    }
}
