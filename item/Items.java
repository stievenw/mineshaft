package com.mineshaft.item;

import com.mineshaft.block.Blocks;
import com.mineshaft.item.properties.ItemProperties;
import com.mineshaft.registry.Identifier;
import com.mineshaft.registry.Registries;

/**
 * All vanilla items (like Minecraft's Items class)
 * Includes BlockItems (items from blocks)
 */
public class Items {
    // Block items (automatically created from blocks)
    public static Item GRASS;
    public static Item DIRT;
    public static Item STONE;
    public static Item COBBLESTONE;
    public static Item BEDROCK;
    public static Item SAND;
    public static Item GRAVEL;
    public static Item OAK_PLANKS;
    public static Item OAK_LOG;
    public static Item OAK_LEAVES;
    public static Item COAL_ORE;
    public static Item IRON_ORE;
    public static Item GOLD_ORE;
    public static Item DIAMOND_ORE;
    
    // Pure items (not from blocks)
    public static Item COAL;
    public static Item IRON_INGOT;
    public static Item GOLD_INGOT;
    public static Item DIAMOND;
    
    // Tools (future)
    public static Item WOODEN_PICKAXE;
    public static Item STONE_PICKAXE;
    public static Item IRON_PICKAXE;
    
    /**
     * Register all items (called during bootstrap)
     */
    public static void init() {
        System.out.println("Registering items...");
        
        // Register block items (items from blocks)
        GRASS = registerBlockItem("grass", Blocks.GRASS);
        DIRT = registerBlockItem("dirt", Blocks.DIRT);
        STONE = registerBlockItem("stone", Blocks.STONE);
        COBBLESTONE = registerBlockItem("cobblestone", Blocks.COBBLESTONE);
        BEDROCK = registerBlockItem("bedrock", Blocks.BEDROCK);
        SAND = registerBlockItem("sand", Blocks.SAND);
        GRAVEL = registerBlockItem("gravel", Blocks.GRAVEL);
        OAK_PLANKS = registerBlockItem("oak_planks", Blocks.WOOD);
        OAK_LOG = registerBlockItem("oak_log", Blocks.LOG);
        OAK_LEAVES = registerBlockItem("oak_leaves", Blocks.LEAVES);
        COAL_ORE = registerBlockItem("coal_ore", Blocks.COAL_ORE);
        IRON_ORE = registerBlockItem("iron_ore", Blocks.IRON_ORE);
        GOLD_ORE = registerBlockItem("gold_ore", Blocks.GOLD_ORE);
        DIAMOND_ORE = registerBlockItem("diamond_ore", Blocks.DIAMOND_ORE);
        
        // Register pure items (materials, drops, etc.)
        COAL = register("coal", new Item(
            new ItemProperties()
                .group("materials")
        ));
        
        IRON_INGOT = register("iron_ingot", new Item(
            new ItemProperties()
                .group("materials")
        ));
        
        GOLD_INGOT = register("gold_ingot", new Item(
            new ItemProperties()
                .group("materials")
        ));
        
        DIAMOND = register("diamond", new Item(
            new ItemProperties()
                .group("materials")
        ));
        
        // Tools
        WOODEN_PICKAXE = register("wooden_pickaxe", new Item(
            new ItemProperties()
                .maxDurability(59)
                .group("tools")
        ));
        
        STONE_PICKAXE = register("stone_pickaxe", new Item(
            new ItemProperties()
                .maxDurability(131)
                .group("tools")
        ));
        
        IRON_PICKAXE = register("iron_pickaxe", new Item(
            new ItemProperties()
                .maxDurability(250)
                .group("tools")
        ));
    }
    
    /**
     * Helper: Register block item
     */
    private static Item registerBlockItem(String name, com.mineshaft.block.Block block) {
        return register(name, new BlockItem(block, new ItemProperties().group("blocks")));
    }
    
    /**
     * Helper: Register item
     */
    private static Item register(String name, Item item) {
        return Registries.ITEM.register(Identifier.of(name), item);
    }
}