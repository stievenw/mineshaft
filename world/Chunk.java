package com.mineshaft.world;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.util.SimplexNoise;

import java.util.Random;

/**
 * ✅ Chunk with lighting support
 */
public class Chunk {
    public static final int CHUNK_SIZE = 16;
    public static final int CHUNK_HEIGHT = Settings.WORLD_HEIGHT;

    private GameBlock[][][] blocks;
    private byte[][][] skyLight; // ✅ NEW: Skylight storage (0-15)
    private byte[][][] blockLight; // ✅ NEW: Blocklight storage (0-15)

    private int chunkX, chunkZ;
    private boolean needsRebuild = true;
    private boolean generated = false;
    private boolean lightInitialized = false; // ✅ NEW: Track if lighting calculated

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.blocks = new GameBlock[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE];
        this.skyLight = new byte[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE]; // ✅ NEW
        this.blockLight = new byte[CHUNK_SIZE][CHUNK_HEIGHT][CHUNK_SIZE]; // ✅ NEW

        initializeBlocks();
        generateTerrain();
    }

    /**
     * Initialize all blocks to AIR
     */
    private void initializeBlocks() {
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int y = 0; y < CHUNK_HEIGHT; y++) {
                for (int z = 0; z < CHUNK_SIZE; z++) {
                    blocks[x][y][z] = BlockRegistry.AIR;
                    skyLight[x][y][z] = 0; // ✅ NEW
                    blockLight[x][y][z] = 0; // ✅ NEW
                }
            }
        }
    }

    /**
     * Generate Minecraft-style terrain
     */
    private void generateTerrain() {
        Random random = new Random(Settings.WORLD_SEED + chunkX * 341873128712L + chunkZ * 132897987541L);

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldZ = chunkZ * CHUNK_SIZE + z;

                int height = getTerrainHeight(worldX, worldZ);
                double biome = getBiomeValue(worldX, worldZ);

                for (int y = 0; y < CHUNK_HEIGHT; y++) {
                    GameBlock block;

                    if (y == 0) {
                        block = BlockRegistry.BEDROCK;
                    } else if (y > height) {
                        block = BlockRegistry.AIR;
                    } else if (y == height) {
                        if (height < Settings.SEA_LEVEL - 1) {
                            block = BlockRegistry.SAND;
                        } else if (height > 85) {
                            block = BlockRegistry.STONE;
                        } else if (biome > 0.65) {
                            block = BlockRegistry.SAND;
                        } else {
                            block = BlockRegistry.GRASS_BLOCK;
                        }
                    } else if (y > height - 4 && height >= Settings.SEA_LEVEL) {
                        block = BlockRegistry.DIRT;
                    } else if (y > height - 3 && height < Settings.SEA_LEVEL) {
                        block = random.nextDouble() > 0.5 ? BlockRegistry.GRAVEL : BlockRegistry.SAND;
                    } else {
                        block = generateOre(y, random);
                    }

                    blocks[x][y][z] = block;
                }

                // Water
                if (height < Settings.SEA_LEVEL) {
                    for (int y = height + 1; y <= Settings.SEA_LEVEL; y++) {
                        blocks[x][y][z] = BlockRegistry.WATER;
                    }
                }

                // Trees
                if (height >= Settings.SEA_LEVEL && height < 75 && biome < 0.5) {
                    if (random.nextDouble() < 0.015) {
                        generateTree(x, height + 1, z, random);
                    }
                }
            }
        }

        generateCaves();

        generated = true;
    }

    private int getTerrainHeight(int worldX, int worldZ) {
        double scale1 = 0.004;
        double scale2 = 0.015;
        double scale3 = 0.06;

        double noise = SimplexNoise.noise(worldX * scale1, worldZ * scale1) * 35;
        noise += SimplexNoise.noise(worldX * scale2, worldZ * scale2) * 18;
        noise += SimplexNoise.noise(worldX * scale3, worldZ * scale3) * 7;

        return Settings.SEA_LEVEL + (int) noise;
    }

    private double getBiomeValue(int worldX, int worldZ) {
        double scale = 0.002;
        return (SimplexNoise.noise(worldX * scale + 5000, worldZ * scale + 5000) + 1.0) / 2.0;
    }

    private GameBlock generateOre(int y, Random random) {
        if (y < 100 && random.nextDouble() < 0.012)
            return BlockRegistry.COAL_ORE;
        if (y < 64 && random.nextDouble() < 0.008)
            return BlockRegistry.IRON_ORE;
        if (y < 32 && random.nextDouble() < 0.004)
            return BlockRegistry.GOLD_ORE;
        if (y < 16 && random.nextDouble() < 0.0015)
            return BlockRegistry.DIAMOND_ORE;
        return BlockRegistry.STONE;
    }

    private void generateCaves() {
        Random random = new Random(Settings.WORLD_SEED + chunkX * 7919L + chunkZ * 5419L);

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldZ = chunkZ * CHUNK_SIZE + z;

                for (int y = 1; y < 55; y++) {
                    double caveNoise1 = SimplexNoise.noise(
                            worldX * 0.05,
                            (worldZ + y * 100) * 0.05);

                    double caveNoise2 = SimplexNoise.noise(
                            (worldX + y * 50) * 0.08,
                            worldZ * 0.08);

                    double combinedNoise = (caveNoise1 + caveNoise2 * 0.5);

                    if (combinedNoise > 0.7) {
                        GameBlock current = blocks[x][y][z];
                        if (current != BlockRegistry.BEDROCK && current != BlockRegistry.WATER) {
                            if (y < Settings.SEA_LEVEL - 5) {
                                blocks[x][y][z] = BlockRegistry.AIR;
                            } else if (y < Settings.SEA_LEVEL && random.nextBoolean()) {
                                blocks[x][y][z] = BlockRegistry.WATER;
                            } else {
                                blocks[x][y][z] = BlockRegistry.AIR;
                            }
                        }
                    }
                }
            }
        }
    }

    private void generateTree(int x, int y, int z, Random random) {
        int height = 5 + random.nextInt(2);

        for (int i = 0; i < height; i++) {
            setBlockSafe(x, y + i, z, BlockRegistry.OAK_LOG);
        }

        int leafY = y + height;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) + Math.abs(dy) + Math.abs(dz) < 5) {
                        setBlockSafe(x + dx, leafY + dy, z + dz, BlockRegistry.OAK_LEAVES);
                    }
                }
            }
        }
    }

    private void setBlockSafe(int x, int y, int z, GameBlock block) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            if (blocks[x][y][z].isAir()) {
                blocks[x][y][z] = block;
            }
        }
    }

    // ========== ✅ LIGHTING METHODS (NEW) ==========

    /**
     * ✅ Get skylight level (0-15)
     */
    public int getSkyLight(int x, int y, int z) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
            return 0;
        }
        return skyLight[x][y][z] & 0xFF;
    }

    /**
     * ✅ Set skylight level
     */
    public void setSkyLight(int x, int y, int z, int level) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
            return;
        }
        skyLight[x][y][z] = (byte) Math.max(0, Math.min(15, level));
    }

    /**
     * ✅ Get blocklight level (0-15)
     */
    public int getBlockLight(int x, int y, int z) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
            return 0;
        }
        return blockLight[x][y][z] & 0xFF;
    }

    /**
     * ✅ Set blocklight level
     */
    public void setBlockLight(int x, int y, int z, int level) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
            return;
        }
        blockLight[x][y][z] = (byte) Math.max(0, Math.min(15, level));
    }

    /**
     * ✅ Check if lighting has been initialized
     */
    public boolean isLightInitialized() {
        return lightInitialized;
    }

    /**
     * ✅ Mark lighting as initialized
     */
    public void setLightInitialized(boolean initialized) {
        this.lightInitialized = initialized;
    }

    // ========== EXISTING METHODS ==========

    public GameBlock getBlock(int x, int y, int z) {
        if (x < 0 || x >= CHUNK_SIZE || y < 0 || y >= CHUNK_HEIGHT || z < 0 || z >= CHUNK_SIZE) {
            return null;
        }
        return blocks[x][y][z];
    }

    public void setBlock(int x, int y, int z, GameBlock block) {
        if (x >= 0 && x < CHUNK_SIZE && y >= 0 && y < CHUNK_HEIGHT && z >= 0 && z < CHUNK_SIZE) {
            blocks[x][y][z] = block;
            needsRebuild = true;
        }
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public boolean needsRebuild() {
        return needsRebuild;
    }

    public boolean isGenerated() {
        return generated;
    }

    public void setNeedsRebuild(boolean needsRebuild) {
        this.needsRebuild = needsRebuild;
    }
}