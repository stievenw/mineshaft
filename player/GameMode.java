package com.mineshaft.player;

public enum GameMode {
    SURVIVAL("Survival", false),
    CREATIVE("Creative", true),
    SPECTATOR("Spectator", true);

    private final String name;
    private final boolean canFly;

    GameMode(String name, boolean canFly) {
        this.name = name;
        this.canFly = canFly;
    }

    public String getName() {
        return name;
    }

    public boolean canFly() {
        return canFly;
    }

    public GameMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public GameMode previous() {
        return values()[(ordinal() - 1 + values().length) % values().length];
    }
}
