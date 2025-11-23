package com.mineshaft.world;

/**
 * Game modes (like Minecraft)
 */
public enum GameMode {
    SURVIVAL(0, "Survival", false, true, true),
    CREATIVE(1, "Creative", true, false, false),
    SPECTATOR(2, "Spectator", true, false, false);
    
    private final int id;
    private final String name;
    private final boolean canFly;
    private final boolean takeDamage;
    private final boolean hasCollision;
    
    GameMode(int id, String name, boolean canFly, boolean takeDamage, boolean hasCollision) {
        this.id = id;
        this.name = name;
        this.canFly = canFly;
        this.takeDamage = takeDamage;
        this.hasCollision = hasCollision;
    }
    
    public int getId() { return id; }
    public String getName() { return name; }
    public boolean canFly() { return canFly; }
    public boolean takesDamage() { return takeDamage; }
    public boolean hasCollision() { return hasCollision; }
    
    public GameMode next() {
        return values()[(ordinal() + 1) % values().length];
    }
    
    public GameMode previous() {
        int prev = ordinal() - 1;
        if (prev < 0) prev = values().length - 1;
        return values()[prev];
    }
    
    public static GameMode fromId(int id) {
        for (GameMode mode : values()) {
            if (mode.id == id) return mode;
        }
        return SURVIVAL;
    }
}