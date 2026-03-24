package com.mygame.tactics;

import com.badlogic.gdx.graphics.Texture;

public abstract class Character {
    // --- IDENTITY ---
    protected String name;
    protected Texture portrait;
    protected Enums.CharClass charClass;
    protected Enums.CharType type;
    protected Enums.Alliance alliance;
    protected Enums.Rarity rarity;
    protected String originLocation;
    protected int turnsTaken = 0;

    // --- BASE STATS (DNA) ---
    protected int baseAtk, baseMag, baseArmor, baseCloak, baseSpeed;
    protected int baseMoveDist, baseRange;
    protected double baseSpeedReduction, baseCritChance, baseDodgeChance, baseSpecialChance;

    // --- BATTLE STATS (Current state) ---
    public int baseMaxHealth, atk, mag, armor, cloak, speed, moveDist, range;
    public double speedReduction, critChance, dodgeChance, specialChance;
    public int health;       // Current HP
    public int maxHealth;    // The limit for healing
    public int poisonLevel;  // Current poison stack — deals this much true damage at turn start

    // --- STATUS & TIMELINE ---
    protected boolean skip;
    protected boolean ultUsed = false; 
    protected boolean ultActive = false; 
    public boolean isFrozen = false;
    public boolean isRooted = false;
    public boolean isInvisible = false;
    public boolean hasDeployed = false;
    public boolean pergolaRangeActive = false; // true while standing on a Luke pergola tile
    
    // The "Wait" Countdown logic (Relativity System)
    // A lower value means the character is closer to their turn.
    protected float currentWait = 0; 
    
    public int x, y; // Current grid position
    public int team;

    protected Ability[] abilities = new Ability[3];

    public Character(String name, Texture portrait, Enums.CharClass charClass, Enums.CharType type, Enums.Alliance alliance) {
        this.name = name;
        this.portrait = portrait;
        this.charClass = charClass;
        this.type = type;
        this.alliance = alliance;
    }

    /**
     * Initializes stats at the start of combat.
     * Sets initial wait based on the speed stat.
     */
    public void startBattle() {
        this.health = baseMaxHealth;
        this.atk = baseAtk;
        this.mag = baseMag;
        this.armor = baseArmor;
        this.cloak = baseCloak;
        this.speed = baseSpeed;
        this.moveDist = baseMoveDist;
        this.range = baseRange;
        this.speedReduction = baseSpeedReduction;
        this.critChance     = baseCritChance;
        this.dodgeChance    = baseDodgeChance;
        this.turnsTaken = 0;
        this.poisonLevel = 0;
        this.maxHealth = this.baseMaxHealth;
        this.health = this.maxHealth;
        
        // Initial wait is based on speed. 
        // In this system, higher speed characters can be given lower initial waits 
        // or wait can be speed itself and we subtract.
        this.currentWait = (float)baseSpeed;
    }

    /**
     * Called after a turn is finished to reset the countdown.
     * Uses the Turn Multiplier formula to increase wait for subsequent turns.
     */
    public void resetWaitAfterTurn() {
        this.turnsTaken++;
        // Formula: baseSpeed * (speedReduction ^ turnsTaken)
        double multiplier = Math.pow(this.baseSpeedReduction, this.turnsTaken);
        this.currentWait = (float)(this.baseSpeed * multiplier);
    }

    public void takePhysicalDamage(int rawAtk) {
        int damage = Math.max(1, rawAtk - armor);
        health -= damage;
        if (health < 0) health = 0;
    }

    public void takeMagicDamage(int rawMag) {
        int damage = Math.max(1, rawMag - cloak);
        health -= damage;
        if (health < 0) health = 0;
    }

    public void takeTrueDamage(int amount) {
        health -= amount;
        if (health < 0) health = 0;
    }

    public void heal(int amount) {
        health += amount;
        if (health > maxHealth) health = maxHealth;
    }

    public boolean isDead() { return health <= 0; }

    public float getHealthPercent() {
        if (maxHealth <= 0) return 0;
        return (float) health / (float) maxHealth;
    }

    // --- GETTERS & SETTERS ---
    public String getName() { return name; }
    public Texture getPortrait() { return portrait; }
    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public int getAtk() { return atk; }
    public int getMag() { return mag; }
    public int getArmor() { return armor; }
    public int getCloak() { return cloak; }
    public int getMoveDist() { return moveDist; }
    public int getSpeed() { return speed; }
    public double getCritChance()  { return critChance; }
    public double getDodgeChance() { return dodgeChance; }
    public int getPoisonLevel()    { return poisonLevel; }

    /** Infects this character, incrementing their poison counter by 1. */
    public void applyPoison() { poisonLevel++; }
    
    // TIMELINE GETTERS
    public float getCurrentWait() { return currentWait; }
    public void setCurrentWait(float value) { this.currentWait = value; }
    
    public Enums.Alliance getAlliance() { return alliance; }
    public Enums.CharClass getCharClass() { return charClass; }
    public Enums.CharType getCharType() { return type; }
    public Enums.Rarity getRarity() { return rarity; }
    public String getOriginLocation() { return originLocation; }

    public Ability[] getAbilities() { return abilities; }
    public Ability getAbility(int index) {
        if (index >= 0 && index < abilities.length) return abilities[index];
        return null;
    }

    public void setUltActive(boolean active) {
        this.ultActive = active;
        if (active) this.ultUsed = true;
    }

    public boolean isUltActive() { return ultActive; }
    public boolean isUltUsed() { return ultUsed; }
    public int getTurnsTaken() { return turnsTaken; }
    
    public boolean isInvisible() { return isInvisible; }
    public void setInvisible(boolean invisible) { this.isInvisible = invisible; }
    

    public void setHealth(int health) {
        this.health = health;
        // Cap health at max
        if (this.health > maxHealth) {
            this.health = maxHealth;
        }
        // Ensure it doesn't drop below 0 without logic
        if (this.health < 0) {
            this.health = 0;
        }
    }
}