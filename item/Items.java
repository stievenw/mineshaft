package com.mineshaft.item;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.item.properties.ItemProperties;
import com.mineshaft.registry.Identifier;
import com.mineshaft.registry.Registries;

/**
 * All vanilla items (like Minecraft's Items class)
 * Includes BlockItems (items from blocks)
 */
public class Items {
        // Block items (automatically created from blocks)
        public static Item GRASS_BLOCK;
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
                GRASS_BLOCK = registerBlockItem("grass_block", BlockRegistry.GRASS_BLOCK);
                DIRT = registerBlockItem("dirt", BlockRegistry.DIRT);
                STONE = registerBlockItem("stone", BlockRegistry.STONE);
                COBBLESTONE = registerBlockItem("cobblestone", BlockRegistry.COBBLESTONE);
                BEDROCK = registerBlockItem("bedrock", BlockRegistry.BEDROCK);
                SAND = registerBlockItem("sand", BlockRegistry.SAND);
                GRAVEL = registerBlockItem("gravel", BlockRegistry.GRAVEL);
                OAK_PLANKS = registerBlockItem("oak_planks", BlockRegistry.OAK_PLANKS);
                OAK_LOG = registerBlockItem("oak_log", BlockRegistry.OAK_LOG);
                OAK_LEAVES = registerBlockItem("oak_leaves", BlockRegistry.OAK_LEAVES);
                COAL_ORE = registerBlockItem("coal_ore", BlockRegistry.COAL_ORE);
                IRON_ORE = registerBlockItem("iron_ore", BlockRegistry.IRON_ORE);
                GOLD_ORE = registerBlockItem("gold_ore", BlockRegistry.GOLD_ORE);
                DIAMOND_ORE = registerBlockItem("diamond_ore", BlockRegistry.DIAMOND_ORE);

                // Register pure items (materials, drops, etc.)
                COAL = register("coal", new Item(
                                new ItemProperties()
                                                .group("materials")));

                IRON_INGOT = register("iron_ingot", new Item(
                                new ItemProperties()
                                                .group("materials")));

                GOLD_INGOT = register("gold_ingot", new Item(
                                new ItemProperties()
                                                .group("materials")));

                DIAMOND = register("diamond", new Item(
                                new ItemProperties()
                                                .group("materials")));

                // Tools
                WOODEN_PICKAXE = register("wooden_pickaxe", new Item(
                                new ItemProperties()
                                                .maxDurability(59)
                                                .group("tools")));

                STONE_PICKAXE = register("stone_pickaxe", new Item(
                                new ItemProperties()
                                                .maxDurability(131)
                                                .group("tools")));

                IRON_PICKAXE = register("iron_pickaxe", new Item(
                                new ItemProperties()
                                                .maxDurability(250)
                                                .group("tools")));
        }

        /**
         * Helper: Register block item
         */
        private static Item registerBlockItem(String name, GameBlock block) {
                return register(name, new BlockItem(block, new ItemProperties().group("blocks")));
        }

        /**
         * Helper: Register item
         */
        private static Item register(String name, Item item) {
                return Registries.ITEM.register(Identifier.of(name), item);
        }
}