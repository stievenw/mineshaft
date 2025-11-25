// src/main/java/com/mineshaft/world/Chunk.java
package com.mineshaft.world;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.util.SimplexNoise;

import java.util.Random;

/**
 * ✅ ASYNC-READY Chunk with state management
 * ✅ FIXED: Proper Y=-64 to Y=320 support (Minecraft 1.18+ standard)
 * ⚡ Terrain generation can be called from background thread
 */
public class Chunk {
    public static final int CHUNK_SIZE = 16;
    public static final int CHUNK_HEIGHT = Settings.WORLD_HEIGHT; // 384 blocks

    // ✅ MINECRAFT-STYLE: Chunk divided into 16×16×16 sections
    public static final int SECTION_COUNT = CHUNK_HEIGHT / ChunkSection.SECTION_SIZE; // 24 sections

    private ChunkSection[] sections; // 24 sections (Y=-64 to Y=320)

    private int chunkX, chunkZ;
    private boolean needsRebuild = true;

    // ✅ Async generation support
    private volatile ChunkState state = ChunkState.EMPTY;
    private boolean lightInitialized = false;

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.sections = new ChunkSection[SECTION_COUNT];

        // Sections are created lazily during terrain generation
        // Empty sections (all air) are never allocated, saving memory

        // ✅ DON'T generate terrain in constructor!
        // Will be called from background thread via generate()
    }

    // ========== ✅ ASYNC GENERATION ==========

    /**
     * ✅ Generate terrain (safe to call from background thread)
     */
    public void generate() {
        if (state != ChunkState.EMPTY) {
            return; // Already generated or generating
        }

        state = ChunkState.GENERATING;

        try {
            generateTerrain();
            state = ChunkState.GENERATED;
            needsRebuild = true;
        } catch (Exception e) {
            System.err.println("Error generating chunk [" + chunkX + ", " + chunkZ + "]: " + e.getMessage());
            e.printStackTrace();
            state = ChunkState.EMPTY; // Reset on error
        }
    }

    /**
     * ✅ Check if chunk is ready to render
     */
    public boolean isReady() {
        return state == ChunkState.READY;
    }

    /**
     * ✅ Check if terrain is generated
     */
    public boolean isGenerated() {
        return state.ordinal() >= ChunkState.GENERATED.ordinal();
    }

    /**
     * ✅ Get current state
     */
    public ChunkState getState() {
        return state;
    }

    /**
     * ✅ Set state (for lighting system)
     */
    public void setState(ChunkState state) {
        this.state = state;
    }

    // ========== \u2705 SECTION MANAGEMENT ==========

    /**
     * Get section index from world Y coordinate
     */
    private int getSectionIndex(int worldY) {
        return (worldY - Settings.WORLD_MIN_Y) / ChunkSection.SECTION_SIZE;
    }

    /**
     * Get section at index, or null if not allocated
     */
    public ChunkSection getSection(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= SECTION_COUNT) {
            return null;
        }
        return sections[sectionIndex];
    }

    /**
     * Get or create section at index (lazy allocation)
     */
    private ChunkSection getOrCreateSection(int sectionIndex) {
        if (sectionIndex < 0 || sectionIndex >= SECTION_COUNT) {
            return null;
        }

        if (sections[sectionIndex] == null) {
            sections[sectionIndex] = new ChunkSection(sectionIndex);
        }

        return sections[sectionIndex];
    }

    /**
     * Convert world Y to local section Y coordinate (0-15)
     */
    private int toLocalY(int worldY) {
        return (worldY - Settings.WORLD_MIN_Y) % ChunkSection.SECTION_SIZE;
    }

    // ========== ✅ TERRAIN GENERATION (FIXED Y RANGE) ==========

    /**
     * ✅ SIMPLIFIED: Smooth, Minecraft-like terrain generation
     * ✅ OPTIMIZED: Uses section-based storage (lazy allocation)
     */
    private void generateTerrain() {
        Random random = new Random(Settings.WORLD_SEED + chunkX * 341873128712L + chunkZ * 132897987541L);

        // Pre-calculate heights for all columns
        int[][] heights = new int[CHUNK_SIZE][CHUNK_SIZE];

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldZ = chunkZ * CHUNK_SIZE + z;
                heights[x][z] = getTerrainHeight(worldX, worldZ);
            }
        }

        // ✅ Generate blocks for ENTIRE height range (Y=-64 to Y=319)
        // Sections created only when needed (lazy allocation)
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int height = heights[x][z];

                // ✅ Loop through ALL Y levels from -64 to 319
                for (int worldY = Settings.WORLD_MIN_Y; worldY <= Settings.WORLD_MAX_Y; worldY++) {
                    GameBlock block;

                    // ✅ Bedrock bottom layer (Y=-64)
                    if (worldY == Settings.BEDROCK_FLOOR) {
                        block = BlockRegistry.BEDROCK;
                    }
                    // ✅ Bedrock transitional layers (Y=-63 to Y=-60)
                    else if (worldY < Settings.BEDROCK_FLOOR + Settings.BEDROCK_LAYERS) {
                        int distanceFromBottom = worldY - Settings.BEDROCK_FLOOR;
                        double bedrockChance = 1.0 - (distanceFromBottom / (double) Settings.BEDROCK_LAYERS);
                        block = random.nextDouble() < bedrockChance ? BlockRegistry.BEDROCK : BlockRegistry.STONE;
                    }
                    // ✅ Above terrain = air (don't allocate section for empty sky)
                    else if (worldY > height) {
                        block = BlockRegistry.AIR;
                    }
                    // ✅ Surface block
                    else if (worldY == height) {
                        block = getSurfaceBlock(height, random);
                    }
                    // ✅ Subsurface (3-5 blocks of dirt/sand below surface)
                    else if (worldY > height - 5) {
                        if (height >= Settings.SEA_LEVEL) {
                            // Land: dirt layers
                            block = BlockRegistry.DIRT;
                        } else {
                            // Ocean floor: sand/gravel mix
                            block = random.nextDouble() > 0.5 ? BlockRegistry.SAND : BlockRegistry.GRAVEL;
                        }
                    }
                    // ✅ Deep underground = stone with ores
                    else {
                        block = generateOre(worldY, random);
                    }

                    // Set block in appropriate section (only if not air)
                    if (!block.isAir()) {
                        int sectionIndex = getSectionIndex(worldY);
                        ChunkSection section = getOrCreateSection(sectionIndex);

                        if (section != null) {
                            int localY = toLocalY(worldY);
                            section.setBlock(x, localY, z, block);
                        }
                    }
                }

                // ✅ Fill water from terrain surface to sea level
                if (height < Settings.SEA_LEVEL) {
                    for (int worldY = height + 1; worldY <= Settings.SEA_LEVEL; worldY++) {
                        int sectionIndex = getSectionIndex(worldY);
                        ChunkSection section = getOrCreateSection(sectionIndex);

                        if (section != null) {
                            int localY = toLocalY(worldY);
                            section.setBlock(x, localY, z, BlockRegistry.WATER);
                        }
                    }
                }

                // ✅ Trees (sparse, only on land)
                if (height >= Settings.SEA_LEVEL + 1 && height < 85) {
                    // 2% chance on plains
                    if (random.nextDouble() < 0.02) {
                        generateTree(x, height + 1, z, random);
                    }
                }
            }
        }

        generateCaves();
    }

    /**
     * ✅ SIMPLIFIED: Smooth terrain height generation
     * Returns: Y=-58 (ocean floor) to Y=180 (mountain peaks)
     */
    private int getTerrainHeight(int worldX, int worldZ) {
        // Large-scale continent noise (smooth ocean vs land)
        double continentScale = 0.001;
        double continent = SimplexNoise.noise(worldX * continentScale, worldZ * continentScale);

        // Medium-scale terrain variation
        double terrainScale = 0.005;
        double terrain = SimplexNoise.noise(worldX * terrainScale, worldZ * terrainScale);

        // Fine detail
        double detailScale = 0.02;
        double detail = SimplexNoise.noise(worldX * detailScale, worldZ * detailScale);

        // Combine noises with weighted influence
        double combined = continent * 50 + terrain * 20 + detail * 5;

        // Base height at sea level
        int baseHeight = Settings.SEA_LEVEL + (int) combined;

        // Clamp to reasonable range (allow ocean floors down to near bedrock)
        return Math.max(-58, Math.min(180, baseHeight));
    }

    /**
     * ✅ SIMPLIFIED: Get surface block based on height only
     */
    private GameBlock getSurfaceBlock(int height, Random random) {
        // Ocean floor
        if (height < Settings.SEA_LEVEL - 5) {
            return random.nextDouble() > 0.5 ? BlockRegistry.GRAVEL : BlockRegistry.SAND;
        }
        // Shallow ocean to beach
        else if (height < Settings.SEA_LEVEL) {
            return BlockRegistry.SAND;
        }
        // Beach (just above sea level)
        else if (height <= Settings.SEA_LEVEL + 2) {
            return BlockRegistry.SAND;
        }
        // High mountains (stone peaks)
        else if (height > 90) {
            return BlockRegistry.STONE;
        }
        // Normal grass land
        else {
            return BlockRegistry.GRASS_BLOCK;
        }
    }

    /**
     * ✅ FIXED: Ore generation with proper Y distribution (1.18+ style)
     * 
     * Distribution matches Minecraft 1.18+ ore generation:
     * - Coal: Y=-64 to Y=136 (common throughout)
     * - Iron: Y=-64 to Y=72 (peak at Y=16)
     * - Gold: Y=-64 to Y=32 (peak at Y=-16)
     * - Diamond: Y=-64 to Y=16 (peak at Y=-59)
     * 
     * Note: Deepslate variants will be added in future update
     */
    private GameBlock generateOre(int worldY, Random random) {
        // ✅ Coal: Y=-64 to Y=136 (common throughout)
        if (worldY >= -64 && worldY <= 136 && random.nextDouble() < 0.012) {
            return BlockRegistry.COAL_ORE;
        }

        // ✅ Iron: Y=-64 to Y=72 (peak at Y=16)
        if (worldY >= -64 && worldY <= 72) {
            double ironChance = 0.008;
            // More common in mid-range (Y=0 to Y=32)
            if (worldY >= 0 && worldY <= 32) {
                ironChance = 0.012;
            }
            if (random.nextDouble() < ironChance) {
                return BlockRegistry.IRON_ORE;
            }
        }

        // ✅ Gold: Y=-64 to Y=32 (peak at Y=-16)
        if (worldY >= -64 && worldY <= 32) {
            double goldChance = 0.004;
            // More common below Y=0 (will be deepslate layer in future)
            if (worldY >= -32 && worldY <= 0) {
                goldChance = 0.006;
            }
            if (random.nextDouble() < goldChance) {
                return BlockRegistry.GOLD_ORE;
            }
        }

        // ✅ Diamond: Y=-64 to Y=16 (peak at Y=-59)
        if (worldY >= -64 && worldY <= 16) {
            double diamondChance = 0.0015;
            // Much more common in deep layer (Y=-64 to Y=-32)
            if (worldY >= -64 && worldY <= -32) {
                diamondChance = 0.003;
            }
            if (random.nextDouble() < diamondChance) {
                return BlockRegistry.DIAMOND_ORE;
            }
        }

        // ✅ Default: Stone below Y=0 will become deepslate in future
        return BlockRegistry.STONE;
    }

    /**
     * ✅ FIXED: Cave generation from bedrock to sea level
     */
    private void generateCaves() {
        Random random = new Random(Settings.WORLD_SEED + chunkX * 7919L + chunkZ * 5419L);

        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                int worldX = chunkX * CHUNK_SIZE + x;
                int worldZ = chunkZ * CHUNK_SIZE + z;

                // ✅ Generate caves from just above bedrock to sea level
                int minCaveY = Settings.BEDROCK_FLOOR + Settings.BEDROCK_LAYERS + 1; // Y=-58
                int maxCaveY = Settings.SEA_LEVEL; // Y=63

                for (int worldY = minCaveY; worldY <= maxCaveY; worldY++) {
                    int index = toIndex(worldY);
                    if (!isValidIndex(index))
                        continue;

                    // 3D Perlin noise for cave generation
                    double caveNoise1 = SimplexNoise.noise(
                            worldX * 0.05,
                            (worldZ + worldY * 100) * 0.05);

                    double caveNoise2 = SimplexNoise.noise(
                            (worldX + worldY * 50) * 0.08,
                            worldZ * 0.08);

                    double combinedNoise = (caveNoise1 + caveNoise2 * 0.5);

                    // ✅ Create cave if noise threshold exceeded
                    if (combinedNoise > 0.7) {
                        GameBlock current = getBlock(x, worldY, z);

                        // Don't replace bedrock or existing water
                        if (current != BlockRegistry.BEDROCK && current != BlockRegistry.WATER) {
                            // Below Y=20: sometimes fill with water (flooded caves)
                            if (worldY < Settings.SEA_LEVEL - 43 && random.nextDouble() < 0.3) {
                                setBlock(x, worldY, z, BlockRegistry.WATER);
                            } else {
                                setBlock(x, worldY, z, BlockRegistry.AIR);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * ✅ SIMPLIFIED: Generate simple oak tree
     */
    private void generateTree(int x, int worldY, int z, Random random) {
        int height = 5 + random.nextInt(2);

        if (!canPlaceTree(x, worldY, z, height + 4)) {
            return;
        }

        // Trunk
        for (int i = 0; i < height; i++) {
            setBlock(x, worldY + i, z, BlockRegistry.OAK_LOG);
        }

        // Leaves
        int leafY = worldY + height;
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

    private boolean canPlaceTree(int x, int worldY, int z, int totalHeight) {
        // Edge protection
        if (x < 2 || x > CHUNK_SIZE - 3 || z < 2 || z > CHUNK_SIZE - 3) {
            return false;
        }

        // Check all blocks in tree space
        for (int y = 0; y < totalHeight; y++) {
            GameBlock check = getBlock(x, worldY + y, z);
            if (check != null && !check.isAir()) {
                return false;
            }
        }

        return true;
    }

    private void setBlockSafe(int x, int worldY, int z, GameBlock block) {
        if (x >= 0 && x < CHUNK_SIZE && z >= 0 && z < CHUNK_SIZE && Settings.isValidWorldY(worldY)) {
            GameBlock current = getBlock(x, worldY, z);
            if (current != null && current.isAir()) {
                setBlock(x, worldY, z, block);
            }
        }
    }

    // ========== ✅ COORDINATE CONVERSION ==========

    /**
     * ✅ Convert world Y to array index
     * Example: Y=-64 -> 0, Y=0 -> 64, Y=319 -> 383
     */
    private int toIndex(int worldY) {
        return Settings.worldYToIndex(worldY);
    }

    /**
     * ✅ Check if array index is valid (0 to 383)
     */
    private boolean isValidIndex(int index) {
        return index >= 0 && index < CHUNK_HEIGHT;
    }

    // ========== LIGHTING METHODS ==========

    /**
     * ✅ Get skylight level (0-15) at world Y coordinate
     */
    public int getSkyLight(int x, int worldY, int z) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE || !Settings.isValidWorldY(worldY)) {
            return 0;
        }

        int sectionIndex = getSectionIndex(worldY);
        ChunkSection section = getSection(sectionIndex);

        if (section == null) {
            return 15; // Empty sections are fully lit
        }

        int localY = toLocalY(worldY);
        return section.getSkyLight(x, localY, z);
    }

    /**
     * ✅ Set skylight level at world Y coordinate
     */
    public void setSkyLight(int x, int worldY, int z, int level) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE || !Settings.isValidWorldY(worldY)) {
            return;
        }

        int sectionIndex = getSectionIndex(worldY);
        ChunkSection section = getOrCreateSection(sectionIndex);

        if (section != null) {
            int localY = toLocalY(worldY);
            section.setSkyLight(x, localY, z, level);
        }
    }

    /**
     * ✅ Get blocklight level (0-15) at world Y coordinate
     */
    public int getBlockLight(int x, int worldY, int z) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE || !Settings.isValidWorldY(worldY)) {
            return 0;
        }

        int sectionIndex = getSectionIndex(worldY);
        ChunkSection section = getSection(sectionIndex);

        if (section == null) {
            return 0; // Empty sections have no block light
        }

        int localY = toLocalY(worldY);
        return section.getBlockLight(x, localY, z);
    }

    /**
     * ✅ Set blocklight level at world Y coordinate
     */
    public void setBlockLight(int x, int worldY, int z, int level) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE || !Settings.isValidWorldY(worldY)) {
            return;
        }

        int sectionIndex = getSectionIndex(worldY);
        ChunkSection section = getOrCreateSection(sectionIndex);

        if (section != null) {
            int localY = toLocalY(worldY);
            section.setBlockLight(x, localY, z, level);
        }
    }

    public boolean isLightInitialized() {
        return lightInitialized;
    }

    public void setLightInitialized(boolean initialized) {
        this.lightInitialized = initialized;
    }

    // ========== BLOCK ACCESS ==========

    /**
     * ✅ Get block at world Y coordinate
     */
    public GameBlock getBlock(int x, int worldY, int z) {
        if (x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE || !Settings.isValidWorldY(worldY)) {
            return null;
        }

        int sectionIndex = getSectionIndex(worldY);
        ChunkSection section = getSection(sectionIndex);

        if (section == null) {
            return BlockRegistry.AIR; // Empty sections are all air
        }

        int localY = toLocalY(worldY);
        return section.getBlock(x, localY, z);
    }

    /**
     * ✅ Set block at world Y coordinate
     */
    public void setBlock(int x, int worldY, int z, GameBlock block) {
        if (!Settings.isValidWorldY(worldY) || x < 0 || x >= CHUNK_SIZE || z < 0 || z >= CHUNK_SIZE) {
            return;
        }

        int sectionIndex = getSectionIndex(worldY);
        ChunkSection section = getOrCreateSection(sectionIndex);

        if (section != null) {
            int localY = toLocalY(worldY);
            section.setBlock(x, localY, z, block);
            needsRebuild = true;
        }
    }

    /**
     * ✅ Get block by array index (for internal use - deprecated, use getBlock)
     */
    public GameBlock getBlockByIndex(int x, int index, int z) {
        int worldY = Settings.indexToWorldY(index);
        return getBlock(x, worldY, z);
    }

    // ========== GETTERS ==========

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public boolean needsRebuild() {
        return needsRebuild;
    }

    public void setNeedsRebuild(boolean needsRebuild) {
        this.needsRebuild = needsRebuild;
    }
}