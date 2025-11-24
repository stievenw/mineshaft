package com.mineshaft.core;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.entity.EntityTypes;
import com.mineshaft.item.Items;
import com.mineshaft.registry.Registries;

/**
 * Bootstrap class - initializes all registries (like Minecraft's Bootstrap)
 * Called once at game startup BEFORE anything else
 */
public class Bootstrap {
    private static boolean initialized = false;

    /**
     * Initialize the game (registries, blocks, items, entities, etc.)
     */
    public static void initialize() {
        if (initialized) {
            throw new IllegalStateException("Already initialized!");
        }

        long startTime = System.currentTimeMillis();

        System.out.println("==============================================");
        System.out.println("   MINESHAFT BOOTSTRAP");
        System.out.println("   Initializing game systems...");
        System.out.println("==============================================");

        // 1. Initialize registry system
        Registries.init();

        // 2. Register blocks (must be first - items depend on blocks)
        BlockRegistry.init();

        // 3. Register items (depends on blocks for BlockItems)
        Items.init();

        // 4. Register entity types
        EntityTypes.init();

        // 5. Freeze all registries (no more registration allowed)
        Registries.freeze();

        long endTime = System.currentTimeMillis();

        System.out.println("==============================================");
        System.out.println("   Bootstrap completed in " + (endTime - startTime) + "ms");
        System.out.println("   - Blocks: " + Registries.BLOCK.size());
        System.out.println("   - Items: " + Registries.ITEM.size());
        System.out.println("   - Entity Types: " + Registries.ENTITY_TYPE.size());
        System.out.println("==============================================");

        initialized = true;
    }

    /**
     * Check if bootstrap has been run
     */
    public static boolean isInitialized() {
        return initialized;
    }
}