package com.mineshaft.block;

import com.mineshaft.resource.BlockData;
import com.mineshaft.resource.ResourceManager;

import com.mineshaft.registry.Identifier;
import com.mineshaft.registry.Registries;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for all game blocks.
 */
public class BlockRegistry {
    private static final Map<String, GameBlock> BLOCKS = new HashMap<>();

    public static GameBlock AIR;
    public static GameBlock STONE;
    public static GameBlock DIRT;
    public static GameBlock GRASS;
    public static GameBlock COBBLESTONE;
    public static GameBlock BEDROCK;
    public static GameBlock SAND;
    public static GameBlock GRAVEL;
    public static GameBlock OAK_PLANKS;
    public static GameBlock OAK_LOG;
    public static GameBlock OAK_LEAVES;
    public static GameBlock WATER;
    public static GameBlock COAL_ORE;
    public static GameBlock IRON_ORE;
    public static GameBlock GOLD_ORE;
    public static GameBlock DIAMOND_ORE;
    public static GameBlock TORCH;
    public static GameBlock GLOWSTONE;
    public static GameBlock LAVA;

    public static void init() {
        System.out.println("Initializing Block Registry...");

        AIR = register("mineshaft:air");
        STONE = register("mineshaft:stone");
        DIRT = register("mineshaft:dirt");
        GRASS = register("mineshaft:grass");
        COBBLESTONE = register("mineshaft:cobblestone");
        BEDROCK = register("mineshaft:bedrock");
        SAND = register("mineshaft:sand");
        GRAVEL = register("mineshaft:gravel");
        OAK_PLANKS = register("mineshaft:oak_planks");
        OAK_LOG = register("mineshaft:oak_log");
        OAK_LEAVES = register("mineshaft:oak_leaves");
        WATER = register("mineshaft:water");
        COAL_ORE = register("mineshaft:coal_ore");
        IRON_ORE = register("mineshaft:iron_ore");
        GOLD_ORE = register("mineshaft:gold_ore");
        DIAMOND_ORE = register("mineshaft:diamond_ore");
        TORCH = register("mineshaft:torch");
        GLOWSTONE = register("mineshaft:glowstone");
        LAVA = register("mineshaft:lava");

        System.out.println("✅ Registered " + BLOCKS.size() + " blocks.");
    }

    public static GameBlock register(String id) {
        BlockData data = ResourceManager.loadBlockData(id);
        if (data == null) {
            System.err.println("❌ CRITICAL: Failed to load block data for " + id);
            // Fallback to air to prevent crash
            data = new BlockData();
            data.id = id;
            data.properties = new BlockData.BlockPropertiesData();
            data.textures = new HashMap<>();
        }

        GameBlock block = new GameBlock(data);
        BLOCKS.put(id, block);

        // Register to central registry
        Registries.BLOCK.register(Identifier.of(id.replace("mineshaft:", "")), block);

        return block;
    }

    public static GameBlock get(String id) {
        return BLOCKS.getOrDefault(id, AIR);
    }

    public static java.util.Collection<GameBlock> getBlocks() {
        return BLOCKS.values();
    }
}
