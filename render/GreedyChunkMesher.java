package com.mineshaft.render;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.render.ChunkRenderer.ThreadBuilders;
import com.mineshaft.world.Chunk;
import com.mineshaft.world.ChunkSection;
import com.mineshaft.world.World;
import com.mineshaft.world.lighting.LightingEngine;

/**
 * ⚡ OPTIMIZED Greedy Meshing System
 * Merges identical block faces into larger quads to reduce vertex count.
 * Handles baked lighting correctly by checking light values before merging.
 */
public class GreedyChunkMesher {

    private static final int CHUNK_SIZE = Chunk.CHUNK_SIZE;
    private static final int SIZE_SQ = CHUNK_SIZE * CHUNK_SIZE;

    // Reusable arrays for greedy meshing (ThreadLocal to avoid allocation)
    private static final ThreadLocal<GreedyCache> CACHE = ThreadLocal.withInitial(GreedyCache::new);

    private static class GreedyCache {
        final boolean[] mask = new boolean[SIZE_SQ];
        final GameBlock[] blocks = new GameBlock[SIZE_SQ];
        final float[] brightness = new float[SIZE_SQ];
    }

    public static void mesh(ChunkSection section, ThreadBuilders builders, World world) {
        if (section == null || section.isEmpty())
            return;

        Chunk chunk = section.getParentChunk();
        int chunkX = chunk.getChunkX();
        int chunkZ = chunk.getChunkZ();
        int minWorldY = section.getMinWorldY();

        long chunkOffsetX = (long) chunkX * CHUNK_SIZE;
        long chunkOffsetZ = (long) chunkZ * CHUNK_SIZE;

        GreedyCache cache = CACHE.get();

        // Iterate over 6 faces
        // 0: Up (Y+), 1: Down (Y-), 2: North (Z-), 3: South (Z+), 4: East (X+), 5: West
        // (X-)

        // --- UP FACE (Y+) ---
        meshYFaces(section, chunk, builders, cache, minWorldY, chunkOffsetX, chunkOffsetZ, 1, world);
        // --- DOWN FACE (Y-) ---
        meshYFaces(section, chunk, builders, cache, minWorldY, chunkOffsetX, chunkOffsetZ, -1, world);

        // --- NORTH FACE (Z-) ---
        meshZFaces(section, chunk, builders, cache, minWorldY, chunkOffsetX, chunkOffsetZ, -1, world);
        // --- SOUTH FACE (Z+) ---
        meshZFaces(section, chunk, builders, cache, minWorldY, chunkOffsetX, chunkOffsetZ, 1, world);

        // --- EAST FACE (X+) ---
        meshXFaces(section, chunk, builders, cache, minWorldY, chunkOffsetX, chunkOffsetZ, 1, world);
        // --- WEST FACE (X-) ---
        meshXFaces(section, chunk, builders, cache, minWorldY, chunkOffsetX, chunkOffsetZ, -1, world);
    }

    private static void meshYFaces(ChunkSection section, Chunk chunk, ThreadBuilders builders, GreedyCache cache,
            int minWorldY, long chunkOffsetX, long chunkOffsetZ, int dir, World world) {
        boolean isUp = dir > 0;
        float ny = isUp ? 1 : -1;
        float faceBrightness = getStaticFaceBrightness(0, ny, 0);

        for (int y = 0; y < 16; y++) {
            int worldY = minWorldY + y;
            // Fill mask
            int n = 0;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    GameBlock block = section.getBlock(x, y, z);
                    GameBlock neighbor = getBlock(chunk, x, y + dir, z, worldY + dir, world);

                    boolean visible = block != null && !block.isAir() &&
                            (neighbor == null || neighbor.isAir() || !neighbor.isSolid());

                    if (visible) {
                        cache.mask[n] = true;
                        cache.blocks[n] = block;
                        cache.brightness[n] = getLightBrightnessAt(chunk, x, y + dir, z, worldY + dir, world)
                                * faceBrightness;
                    } else {
                        cache.mask[n] = false;
                    }
                    n++;
                }
            }
            greedyScan(builders, cache, chunkOffsetX, worldY + (isUp ? 1 : 0), chunkOffsetZ, 0, ny, 0,
                    isUp ? "top" : "bottom");
        }
    }

    private static void meshZFaces(ChunkSection section, Chunk chunk, ThreadBuilders builders, GreedyCache cache,
            int minWorldY, long chunkOffsetX, long chunkOffsetZ, int dir, World world) {
        boolean isSouth = dir > 0;
        float nz = isSouth ? 1 : -1;
        float faceBrightness = getStaticFaceBrightness(0, 0, nz);

        for (int z = 0; z < 16; z++) {
            // Fill mask (iterating X, Y slice)
            int n = 0;
            for (int y = 0; y < 16; y++) {
                int worldY = minWorldY + y;
                for (int x = 0; x < 16; x++) {
                    GameBlock block = section.getBlock(x, y, z);
                    GameBlock neighbor = getBlock(chunk, x, y, z + dir, worldY, world);

                    boolean visible = block != null && !block.isAir() &&
                            (neighbor == null || neighbor.isAir() || !neighbor.isSolid());

                    if (visible) {
                        cache.mask[n] = true;
                        cache.blocks[n] = block;
                        cache.brightness[n] = getLightBrightnessAt(chunk, x, y, z + dir, worldY, world)
                                * faceBrightness;
                    } else {
                        cache.mask[n] = false;
                    }
                    n++;
                }
            }
            // Correct offset for Z faces: If South (+), render at Z+1. If North (-), render
            // at Z.
            greedyScan(builders, cache, chunkOffsetX, minWorldY, chunkOffsetZ + z + (isSouth ? 1 : 0), 0, 0, nz,
                    "side");
        }
    }

    private static void meshXFaces(ChunkSection section, Chunk chunk, ThreadBuilders builders, GreedyCache cache,
            int minWorldY, long chunkOffsetX, long chunkOffsetZ, int dir, World world) {
        boolean isEast = dir > 0;
        float nx = isEast ? 1 : -1;
        float faceBrightness = getStaticFaceBrightness(nx, 0, 0);

        for (int x = 0; x < 16; x++) {
            // Fill mask (iterating Z, Y slice)
            int n = 0;
            for (int y = 0; y < 16; y++) {
                int worldY = minWorldY + y;
                for (int z = 0; z < 16; z++) {
                    GameBlock block = section.getBlock(x, y, z);
                    GameBlock neighbor = getBlock(chunk, x + dir, y, z, worldY, world);

                    boolean visible = block != null && !block.isAir() &&
                            (neighbor == null || neighbor.isAir() || !neighbor.isSolid());

                    if (visible) {
                        cache.mask[n] = true;
                        cache.blocks[n] = block;
                        cache.brightness[n] = getLightBrightnessAt(chunk, x + dir, y, z, worldY, world)
                                * faceBrightness;
                    } else {
                        cache.mask[n] = false;
                    }
                    n++;
                }
            }
            // Correct offset for X faces: If East (+), render at X+1. If West (-), render
            // at X.
            greedyScan(builders, cache, chunkOffsetX + x + (isEast ? 1 : 0), minWorldY, chunkOffsetZ, nx, 0, 0, "side");
        }
    }

    // Generic Greedy Scanner (2D)
    private static void greedyScan(ThreadBuilders builders, GreedyCache cache,
            float wx, float wy, float wz,
            float nx, float ny, float nz, String textureFace) {

        int n = 0;
        // Slice dimensions always 16x16
        // i -> First dimension (Width)
        // j -> Second dimension (Height)
        for (int j = 0; j < 16; j++) {
            for (int i = 0; i < 16; i++) {
                if (cache.mask[n]) {
                    // Start of a new quad
                    GameBlock block = cache.blocks[n];
                    float light = cache.brightness[n];

                    int width = 1;

                    // DISABLE GREEDY MERGE TEMPORARILY
                    // Texture Atlas requires GL_TEXTURE_2D_ARRAY for correct tiling of merged
                    // faces.
                    // For now, we render individual faces to ensure correct visuals.
                    /*
                     * while (i + width < 16 && cache.mask[n + width]
                     * && cache.blocks[n + width] == block
                     * && Math.abs(cache.brightness[n + width] - light) < 0.01f) {
                     * width++;
                     * }
                     */

                    // Compute height (expand in j direction)
                    int height = 1;
                    /*
                     * boolean done = false;
                     * while (j + height < 16 && !done) {
                     * for (int k = 0; k < width; k++) {
                     * int idx = n + k + (height * 16);
                     * if (!cache.mask[idx] || cache.blocks[idx] != block
                     * || Math.abs(cache.brightness[idx] - light) >= 0.01f) {
                     * done = true;
                     * break;
                     * }
                     * }
                     * if (!done) height++;
                     * }
                     */

                    // Add Quad
                    addQuad(builders, block, textureFace,
                            wx, wy, wz,
                            nx, ny, nz,
                            i, j, width, height, light);

                    // Mark used
                    for (int h = 0; h < height; h++) {
                        for (int w = 0; w < width; w++) {
                            cache.mask[n + w + (h * 16)] = false;
                        }
                    }

                    i += width - 1;
                    n += width - 1;
                }
                n++;
            }
        }
    }

    private static void addQuad(ThreadBuilders builders, GameBlock block, String textureFace,
            float wx, float wy, float wz,
            float nx, float ny, float nz,
            int u, int v, int width, int height, float brightness) {

        SmartMeshBuilder builder;
        if (block == BlockRegistry.WATER)
            builder = builders.water;
        else if (!block.isSolid())
            builder = builders.translucent;
        else
            builder = builders.solid;

        float[] uv = BlockTextures.getUV(block, textureFace);
        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];

        // Tiling UVs
        float du = Math.abs(u2 - u1);
        float dv = Math.abs(v2 - v1);

        if (u2 > u1)
            u2 = u1 + du * width;
        else
            u2 = u1 - du * width;
        if (v2 > v1)
            v2 = v1 + dv * height;
        else
            v2 = v1 - dv * height;

        float[] color = block.getBiomeColor();
        if (block != BlockRegistry.GRASS_BLOCK && block != BlockRegistry.OAK_LEAVES && block != BlockRegistry.WATER) {
            color = new float[] { 1f, 1f, 1f };
        }

        float r = color[0] * brightness;
        float g = color[1] * brightness;
        float b = color[2] * brightness;
        float a = 1.0f;

        // Quad Vertex Order: (0,0), (0,1), (1,1), (1,0)
        // 0,0 -> x1
        // 0,1 -> x2
        // 1,1 -> x3
        // 1,0 -> x4

        float x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4;

        if (ny != 0) { // UP/DOWN (X-Z plane). i=X, j=Z.
            // wx, wy, wz represents base corner
            // U increases along X, V increases along Z
            x1 = wx + u;
            z1 = wz + v;
            x2 = wx + u;
            z2 = wz + v + height;
            x3 = wx + u + width;
            z3 = wz + v + height;
            x4 = wx + u + width;
            z4 = wz + v;
            // Y is constant
            y1 = y2 = y3 = y4 = wy;
        } else if (nz != 0) { // NORTH/SOUTH (X-Y plane). i=X, j=Y.
            // U increases along X, V increases along Y
            x1 = wx + u;
            y1 = wy + v;
            x2 = wx + u;
            y2 = wy + v + height;
            x3 = wx + u + width;
            y3 = wy + v + height;
            x4 = wx + u + width;
            y4 = wy + v;
            // Z is constant
            z1 = z2 = z3 = z4 = wz;
        } else { // EAST/WEST (Z-Y plane). i=Z, j=Y.
            // U increases along Z, V increases along Y
            z1 = wz + u;
            y1 = wy + v;
            z2 = wz + u;
            y2 = wy + v + height;
            z3 = wz + u + width;
            y3 = wy + v + height;
            z4 = wz + u + width;
            y4 = wy + v;
            // X is constant
            x1 = x2 = x3 = x4 = wx;
        }

        // Logic:
        // XZ Plane Basis (Top/Bot) = Down (Y-)
        // XY Plane Basis (South/North) = South (Z+)
        // ZY Plane Basis (East/West) = West (X-)

        // We define 1-4-3-2 as "Basis Normal". 1-2-3-4 is "-Basis Normal".

        boolean flip = (ny > 0) || (nz < 0) || (nx > 0);

        if (flip) {
            // Render 1-2-3-4 to reverse the basis normal
            builder.addVertex(x1, y1, z1, r, g, b, a, nx, ny, nz, u1, v1);
            builder.addVertex(x2, y2, z2, r, g, b, a, nx, ny, nz, u1, v2);
            builder.addVertex(x3, y3, z3, r, g, b, a, nx, ny, nz, u2, v2);
            builder.addVertex(x4, y4, z4, r, g, b, a, nx, ny, nz, u2, v1);
        } else {
            // Render 1-4-3-2 to keep the basis normal
            builder.addVertex(x1, y1, z1, r, g, b, a, nx, ny, nz, u1, v1);
            builder.addVertex(x4, y4, z4, r, g, b, a, nx, ny, nz, u2, v1);
            builder.addVertex(x3, y3, z3, r, g, b, a, nx, ny, nz, u2, v2);
            builder.addVertex(x2, y2, z2, r, g, b, a, nx, ny, nz, u1, v2);
        }
    }

    private static GameBlock getBlock(Chunk chunk, int x, int y, int z, int worldY, World world) {
        if (!Settings.isValidWorldY(worldY))
            return BlockRegistry.AIR;
        if (x >= 0 && x < 16 && z >= 0 && z < 16 && y >= 0 && y < 16) {
            return chunk.getBlock(x, worldY, z);
        }
        if (world != null) {
            return world.getBlock(chunk.getChunkX() * 16 + x, worldY, chunk.getChunkZ() * 16 + z);
        }
        return BlockRegistry.AIR;
    }

    private static float getLightBrightnessAt(Chunk chunk, int x, int y, int z, int worldY, World world) {
        if (!chunk.isReady())
            return 0f;
        int light = 0;

        // ✅ Get TimeOfDay for time-based sky light scaling
        com.mineshaft.core.TimeOfDay timeOfDay = (world != null) ? world.getTimeOfDay() : null;

        // Use World.getCombinedLight if outside chunk
        if (x >= 0 && x < 16 && z >= 0 && z < 16) {
            light = LightingEngine.getCombinedLight(chunk, x, worldY, z, timeOfDay);
        } else if (world != null) {
            // Need neighbor access. LightingEngine handles coordinates?
            // LightingEngine.getCombinedLight requires chunk, local coords.
            // We need to resolve neighbor.
            int worldX = chunk.getChunkX() * 16 + x;
            int worldZ = chunk.getChunkZ() * 16 + z;
            int cx = Math.floorDiv(worldX, 16);
            int cz = Math.floorDiv(worldZ, 16);
            Chunk neighbor = world.getChunk(cx, cz);
            if (neighbor != null && neighbor.isReady()) {
                light = LightingEngine.getCombinedLight(neighbor, Math.floorMod(worldX, 16), worldY,
                        Math.floorMod(worldZ, 16), timeOfDay);
            } else {
                light = 0; // Default dark
            }
        }

        return getLightBrightness(light);
    }

    private static float getLightBrightness(int lightValue) {
        float val = lightValue / 15.0f;
        float brightness = val * val; // Power 2.0 (Less aggressive, brighter mid-tones)
        return Math.max(0.05f, brightness); // Min brightness 0.05
    }

    private static float getStaticFaceBrightness(float nx, float ny, float nz) {
        if (ny > 0)
            return 1.0f;
        if (ny < 0)
            return 0.7f;
        if (nz != 0)
            return 0.85f;
        return 0.75f;
    }
}
