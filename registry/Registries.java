package com.mineshaft.registry;

/**
 * Container for all built-in registries (like Minecraft's Registries class)
 * Central access point for all game registries
 */
public class Registries {
    // Block registry
    public static final Registry<com.mineshaft.block.Block> BLOCK = 
        new Registry<>(Identifier.of("block"));
    
    // Item registry
    public static final Registry<com.mineshaft.item.Item> ITEM = 
        new Registry<>(Identifier.of("item"));
    
    // Entity type registry
    public static final Registry<com.mineshaft.entity.EntityType<?>> ENTITY_TYPE = 
        new Registry<>(Identifier.of("entity_type"));
    
    // Block entity type registry (for future: chests, furnaces, etc.)
    // public static final Registry<BlockEntityType<?>> BLOCK_ENTITY_TYPE = 
    //     new Registry<>(Identifier.of("block_entity_type"));
    
    // Biome registry (for future)
    // public static final Registry<Biome> BIOME = 
    //     new Registry<>(Identifier.of("biome"));
    
    /**
     * Initialize all registries (called during bootstrap)
     */
    public static void init() {
        System.out.println("==============================================");
        System.out.println("Initializing registries...");
        System.out.println("==============================================");
    }
    
    /**
     * Freeze all registries (called after registration phase)
     */
    public static void freeze() {
        System.out.println("==============================================");
        System.out.println("Freezing registries...");
        BLOCK.freeze();
        ITEM.freeze();
        ENTITY_TYPE.freeze();
        System.out.println("All registries frozen");
        System.out.println("==============================================");
    }
}