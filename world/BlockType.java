package com.mineshaft.world;

/**
 * Enum for all block types in the game
 */
public enum BlockType {
    AIR(0, false, false, "Air"),
    GRASS(1, true, true, "Grass Block"),
    DIRT(2, true, true, "Dirt"),
    STONE(3, true, true, "Stone"),
    COBBLESTONE(4, true, true, "Cobblestone"),
    WOOD(5, true, true, "Wood Planks"),
    LOG(6, true, true, "Oak Log"),
    LEAVES(7, true, false, "Oak Leaves"),
    BEDROCK(8, true, true, "Bedrock"),
    SAND(9, true, true, "Sand"),
    GRAVEL(10, true, true, "Gravel"),
    WATER(11, false, false, "Water"),
    COAL_ORE(12, true, true, "Coal Ore"),
    IRON_ORE(13, true, true, "Iron Ore"),
    GOLD_ORE(14, true, true, "Gold Ore"),
    DIAMOND_ORE(15, true, true, "Diamond Ore");
    
    private final int id;
    private final boolean solid;
    private final boolean opaque;
    private final String name;
    
    BlockType(int id, boolean solid, boolean opaque, String name) {
        this.id = id;
        this.solid = solid;
        this.opaque = opaque;
        this.name = name;
    }
    
    public int getId() {
        return id;
    }
    
    public boolean isSolid() {
        return solid;
    }
    
    public boolean isOpaque() {
        return opaque;
    }
    
    public String getName() {
        return name;
    }
    
    public static BlockType getById(int id) {
        for (BlockType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        return AIR;
    }
}