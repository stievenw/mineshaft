package com.mineshaft.block;

import com.mineshaft.block.properties.BlockProperties;
import com.mineshaft.block.properties.Material;
import com.mineshaft.registry.Identifier;
import com.mineshaft.registry.Registries;

/**
 * ✅ All vanilla blocks with light-emitting blocks
 */
public class Blocks {
    public static Block AIR;
    public static Block GRASS;
    public static Block DIRT;
    public static Block STONE;
    public static Block COBBLESTONE;
    public static Block BEDROCK;
    public static Block SAND;
    public static Block GRAVEL;
    public static Block WOOD;
    public static Block LOG;
    public static Block LEAVES;
    public static Block WATER;
    public static Block COAL_ORE;
    public static Block IRON_ORE;
    public static Block GOLD_ORE;
    public static Block DIAMOND_ORE;
    
    // ✅ NEW: Light-emitting blocks
    public static Block TORCH;
    public static Block GLOWSTONE;
    public static Block LAVA;
    
    public static void init() {
        System.out.println("Registering blocks...");
        
        AIR = register("air", new Block(
            BlockProperties.of(Material.AIR)
        ));
        
        GRASS = register("grass", new Block(
            BlockProperties.of(Material.DIRT)
                .strength(0.6f)
                .tool("shovel")
                .color(0.35f, 0.75f, 0.30f)
        ));
        
        DIRT = register("dirt", new Block(
            BlockProperties.of(Material.DIRT)
                .strength(0.5f)
                .tool("shovel")
                .color(0.55f, 0.35f, 0.20f)
        ));
        
        STONE = register("stone", new Block(
            BlockProperties.of(Material.STONE)
                .strength(1.5f)
                .requiresCorrectToolForDrops()
                .tool("pickaxe")
                .color(0.50f, 0.50f, 0.50f)
        ));
        
        COBBLESTONE = register("cobblestone", new Block(
            BlockProperties.of(Material.STONE)
                .strength(2.0f)
                .requiresCorrectToolForDrops()
                .tool("pickaxe")
                .color(0.40f, 0.40f, 0.40f)
        ));
        
        BEDROCK = register("bedrock", new Block(
            BlockProperties.of(Material.STONE)
                .unbreakable()
                .color(0.15f, 0.15f, 0.15f)
        ));
        
        SAND = register("sand", new Block(
            BlockProperties.of(Material.SAND)
                .strength(0.5f)
                .tool("shovel")
                .color(0.85f, 0.80f, 0.55f)
        ));
        
        GRAVEL = register("gravel", new Block(
            BlockProperties.of(Material.SAND)
                .strength(0.6f)
                .tool("shovel")
                .color(0.50f, 0.45f, 0.45f)
        ));
        
        WOOD = register("oak_planks", new Block(
            BlockProperties.of(Material.WOOD)
                .strength(2.0f)
                .tool("axe")
                .color(0.65f, 0.50f, 0.30f)
        ));
        
        LOG = register("oak_log", new Block(
            BlockProperties.of(Material.WOOD)
                .strength(2.0f)
                .tool("axe")
                .color(0.40f, 0.30f, 0.20f)
        ));
        
        LEAVES = register("oak_leaves", new Block(
            BlockProperties.of(Material.LEAVES)
                .strength(0.2f)
                .tool("shears")
                .color(0.20f, 0.60f, 0.20f)
        ));
        
        WATER = register("water", new Block(
            BlockProperties.of(Material.WATER)
                .unbreakable()
                .color(0.18f, 0.45f, 0.85f)
        ));
        
        COAL_ORE = register("coal_ore", new Block(
            BlockProperties.of(Material.STONE)
                .strength(3.0f)
                .requiresCorrectToolForDrops()
                .tool("pickaxe")
                .color(0.30f, 0.30f, 0.30f)
        ));
        
        IRON_ORE = register("iron_ore", new Block(
            BlockProperties.of(Material.STONE)
                .strength(3.0f)
                .requiresCorrectToolForDrops()
                .tool("pickaxe")
                .color(0.60f, 0.50f, 0.45f)
        ));
        
        GOLD_ORE = register("gold_ore", new Block(
            BlockProperties.of(Material.STONE)
                .strength(3.0f)
                .requiresCorrectToolForDrops()
                .tool("pickaxe")
                .color(0.80f, 0.70f, 0.30f)
        ));
        
        DIAMOND_ORE = register("diamond_ore", new Block(
            BlockProperties.of(Material.STONE)
                .strength(3.0f)
                .requiresCorrectToolForDrops()
                .tool("pickaxe")
                .color(0.40f, 0.70f, 0.80f)
        ));
        
        // ✅ NEW: Light-emitting blocks
        
        TORCH = register("torch", new Block(
            BlockProperties.of(Material.PLANT)
                .strength(0.0f)
                .lightLevel(14) // ✅ Light level 14 (like Minecraft)
                .color(1.0f, 0.85f, 0.55f) // Warm yellow-orange
        ));
        
        GLOWSTONE = register("glowstone", new Block(
            BlockProperties.of(Material.STONE)
                .strength(0.3f)
                .tool("pickaxe")
                .lightLevel(15) // ✅ Max light level
                .color(1.0f, 0.95f, 0.70f) // Bright yellow
        ));
        
        LAVA = register("lava", new Block(
            BlockProperties.of(Material.WATER)
                .unbreakable()
                .lightLevel(15) // ✅ Max light level
                .color(1.0f, 0.35f, 0.0f) // Bright orange-red
        ));
        
        System.out.println("✅ Registered " + (16 + 3) + " blocks (including 3 light sources)");
    }
    
    private static Block register(String name, Block block) {
        return Registries.BLOCK.register(Identifier.of(name), block);
    }
}