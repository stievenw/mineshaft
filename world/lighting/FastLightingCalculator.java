package com.mineshaft.world.lighting;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.world.Chunk;

/**
 * ⚡ FastLightingCalculator - Instant lighting without complex BFS
 * 
 * This provides INSTANT lighting calculation without queuing or threading.
 * Used for:
 * - Quick initial skylight (vertical only, no horizontal spread)
 * - Immediate block light updates
 * - Real-time lighting during gameplay
 * 
 * For full Minecraft-quality lighting with horizontal propagation,
 * use LightingEngine.initializeLighting() instead.
 */
public class FastLightingCalculator {

    /**
     * ✅ INSTANT skylight initialization - runs synchronously
     * Only does vertical propagation (no horizontal BFS)
     * Much faster but doesn't illuminate caves from side openings
     */
    public static void initializeFastSkylight(Chunk chunk, int skylightLevel) {
        if (chunk == null)
            return;

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                propagateColumnSkylight(chunk, x, z, skylightLevel);
            }
        }

        chunk.setLightInitialized(true);
    }

    /**
     * Propagate skylight down a single column
     */
    private static void propagateColumnSkylight(Chunk chunk, int x, int z, int skylightLevel) {
        int current = skylightLevel;

        for (int index = Chunk.CHUNK_HEIGHT - 1; index >= 0; index--) {
            int worldY = Settings.indexToWorldY(index);
            GameBlock block = chunk.getBlock(x, worldY, z);

            if (block == null || block.isAir()) {
                // Air: full light
                chunk.setSkyLight(x, worldY, z, current);
            } else if (block == BlockRegistry.WATER || block == BlockRegistry.OAK_LEAVES) {
                // Semi-transparent: decay
                current = Math.max(0, current - 1);
                chunk.setSkyLight(x, worldY, z, current);
            } else if (block.isSolid()) {
                // Solid: blocks light
                current = 0;
                chunk.setSkyLight(x, worldY, z, 0);
            } else {
                // Non-solid (flowers): pass through
                chunk.setSkyLight(x, worldY, z, current);
            }
        }
    }

    /**
     * ✅ INSTANT blocklight from a single source
     * Uses simple distance-based attenuation instead of BFS
     */
    public static void updateBlockLightAt(Chunk chunk, int x, int worldY, int z, int lightLevel) {
        if (chunk == null || lightLevel <= 0)
            return;

        // Set source light
        chunk.setBlockLight(x, worldY, z, lightLevel);

        // Simple radius-based propagation
        int radius = lightLevel;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0)
                        continue;

                    int nx = x + dx;
                    int ny = worldY + dy;
                    int nz = z + dz;

                    // Bounds check
                    if (nx < 0 || nx >= Chunk.CHUNK_SIZE)
                        continue;
                    if (nz < 0 || nz >= Chunk.CHUNK_SIZE)
                        continue;
                    if (!Settings.isValidWorldY(ny))
                        continue;

                    // Check if neighbor is air
                    GameBlock neighbor = chunk.getBlock(nx, ny, nz);
                    if (neighbor != null && neighbor.isSolid() &&
                            neighbor != BlockRegistry.WATER && neighbor != BlockRegistry.OAK_LEAVES) {
                        continue; // Skip solid blocks
                    }

                    // Calculate distance-based light
                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    int newLight = Math.max(0, lightLevel - distance);

                    // Only update if brighter
                    int existing = chunk.getBlockLight(nx, ny, nz);
                    if (newLight > existing) {
                        chunk.setBlockLight(nx, ny, nz, newLight);
                    }
                }
            }
        }
    }

    /**
     * ✅ Convert light level (0-15) to brightness (0.0-1.0)
     * Minecraft-style non-linear curve
     */
    public static float getBrightness(int lightLevel) {
        if (lightLevel <= 0)
            return 0.2f; // Minimum ambient
        if (lightLevel >= 15)
            return 1.0f; // Maximum

        // Non-linear curve like Minecraft
        float normalized = lightLevel / 15.0f;
        return 0.2f + (normalized * normalized * 0.8f);
    }

    /**
     * ✅ Get combined brightness at position
     */
    public static float getCombinedBrightness(Chunk chunk, int x, int worldY, int z, float timeOfDayFactor) {
        if (chunk == null)
            return 0.5f;

        int skyLight = chunk.getSkyLight(x, worldY, z);
        int blockLight = chunk.getBlockLight(x, worldY, z);

        // Sky light is affected by time of day, block light is not
        float skyBrightness = getBrightness(skyLight) * timeOfDayFactor;
        float blockBrightness = getBrightness(blockLight);

        // Return maximum
        return Math.max(skyBrightness, blockBrightness);
    }
}
