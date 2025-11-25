// src/main/java/com/mineshaft/world/ChunkSection.java
package com.mineshaft.world;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;

/**
 * ✅ Minecraft-style Chunk Section (16×16×16 blocks)
 * 
 * Chunks are divided vertically into sections to optimize:
 * - Memory (empty sections not allocated)
 * - Rendering (section-level culling)
 * - Mesh building (only rebuild affected section)
 */
public class ChunkSection {
    public static final int SECTION_SIZE = 16;

    private GameBlock[][][] blocks; // 16×16×16
    private byte[][][] skyLight; // 16×16×16
    private byte[][][] blockLight; // 16×16×16

    private final int sectionY; // Section index (0-23 for Y=-64 to Y=320)
    private boolean isEmpty = true;
    private int nonAirBlockCount = 0;

    /**
     * Create a new chunk section
     * 
     * @param sectionY Section index (0 = Y=-64 to Y=-48, 23 = Y=304 to Y=320)
     */
    public ChunkSection(int sectionY) {
        this.sectionY = sectionY;
        this.blocks = new GameBlock[SECTION_SIZE][SECTION_SIZE][SECTION_SIZE];
        this.skyLight = new byte[SECTION_SIZE][SECTION_SIZE][SECTION_SIZE];
        this.blockLight = new byte[SECTION_SIZE][SECTION_SIZE][SECTION_SIZE];

        // Initialize all blocks to AIR
        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    blocks[x][y][z] = BlockRegistry.AIR;
                    skyLight[x][y][z] = 0;
                    blockLight[x][y][z] = 0;
                }
            }
        }
    }

    /**
     * Get block at local section coordinates (0-15)
     */
    public GameBlock getBlock(int x, int y, int z) {
        if (x < 0 || x >= SECTION_SIZE || y < 0 || y >= SECTION_SIZE || z < 0 || z >= SECTION_SIZE) {
            return null;
        }
        return blocks[x][y][z];
    }

    /**
     * Set block at local section coordinates (0-15)
     */
    public void setBlock(int x, int y, int z, GameBlock block) {
        if (x < 0 || x >= SECTION_SIZE || y < 0 || y >= SECTION_SIZE || z < 0 || z >= SECTION_SIZE) {
            return;
        }

        GameBlock oldBlock = blocks[x][y][z];
        blocks[x][y][z] = block;

        // Update empty state tracking
        if (oldBlock.isAir() && !block.isAir()) {
            nonAirBlockCount++;
            isEmpty = false;
        } else if (!oldBlock.isAir() && block.isAir()) {
            nonAirBlockCount--;
            if (nonAirBlockCount == 0) {
                isEmpty = true;
            }
        }
    }

    /**
     * Get skylight level (0-15)
     */
    public int getSkyLight(int x, int y, int z) {
        if (x < 0 || x >= SECTION_SIZE || y < 0 || y >= SECTION_SIZE || z < 0 || z >= SECTION_SIZE) {
            return 0;
        }
        return skyLight[x][y][z] & 0xFF;
    }

    /**
     * Set skylight level (0-15)
     */
    public void setSkyLight(int x, int y, int z, int level) {
        if (x < 0 || x >= SECTION_SIZE || y < 0 || y >= SECTION_SIZE || z < 0 || z >= SECTION_SIZE) {
            return;
        }
        skyLight[x][y][z] = (byte) Math.max(0, Math.min(15, level));
    }

    /**
     * Get blocklight level (0-15)
     */
    public int getBlockLight(int x, int y, int z) {
        if (x < 0 || x >= SECTION_SIZE || y < 0 || y >= SECTION_SIZE || z < 0 || z >= SECTION_SIZE) {
            return 0;
        }
        return blockLight[x][y][z] & 0xFF;
    }

    /**
     * Set blocklight level (0-15)
     */
    public void setBlockLight(int x, int y, int z, int level) {
        if (x < 0 || x >= SECTION_SIZE || y < 0 || y >= SECTION_SIZE || z < 0 || z >= SECTION_SIZE) {
            return;
        }
        blockLight[x][y][z] = (byte) Math.max(0, Math.min(15, level));
    }

    /**
     * Check if this section is empty (all air blocks)
     * Empty sections are not stored in memory and not rendered
     */
    public boolean isEmpty() {
        return isEmpty;
    }

    /**
     * Check if this section is fully opaque (all solid blocks)
     * Used for occlusion culling - fully opaque sections can hide adjacent sections
     */
    public boolean isFullyOpaque() {
        if (isEmpty) {
            return false;
        }

        for (int x = 0; x < SECTION_SIZE; x++) {
            for (int y = 0; y < SECTION_SIZE; y++) {
                for (int z = 0; z < SECTION_SIZE; z++) {
                    GameBlock block = blocks[x][y][z];
                    if (block == null || block.isAir() || !block.isSolid()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * Get the section Y index (0-23)
     */
    public int getSectionY() {
        return sectionY;
    }

    /**
     * Get the number of non-air blocks in this section
     */
    public int getNonAirBlockCount() {
        return nonAirBlockCount;
    }

    /**
     * Get the minimum world Y coordinate for this section
     */
    public int getMinWorldY() {
        // Section 0 = Y=-64, Section 1 = Y=-48, etc.
        return sectionY * SECTION_SIZE + com.mineshaft.core.Settings.WORLD_MIN_Y;
    }

    /**
     * Get the maximum world Y coordinate for this section
     */
    public int getMaxWorldY() {
        return getMinWorldY() + SECTION_SIZE - 1;
    }
}
