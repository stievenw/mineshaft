package com.mineshaft.render;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.entity.Camera;
import com.mineshaft.world.Chunk;
import com.mineshaft.world.ChunkSection;

import java.util.*;
import java.util.concurrent.*;

import static org.lwjgl.opengl.GL11.*;

/**
 * ⚡ NEW! SimpleMeshRenderer - Lightweight Chunk Rendering
 * 
 * Complete rewrite with focus on:
 * - SIMPLE mesh building (no complex async queuing)
 * - IMMEDIATE mesh generation when chunk is ready
 * - NO separate lighting phase - brightness baked during build
 * - Time-of-day via glColor (no mesh rebuild)
 * 
 * Key simplifications:
 * - One mesh per chunk (not per section) for less overhead
 * - Direct build on main thread for urgent chunks
 * - Simple face culling and brightness calculation
 */
public class SimpleMeshRenderer {

    // ========== MESH STORAGE ==========
    // Simple: one solid mesh + one water mesh per chunk
    private final Map<Chunk, ChunkMesh> solidMeshes = new ConcurrentHashMap<>();
    private final Map<Chunk, ChunkMesh> waterMeshes = new ConcurrentHashMap<>();

    // ========== ASYNC BUILDING ==========
    private final ExecutorService meshBuilder;
    private final Set<Chunk> buildingChunks = ConcurrentHashMap.newKeySet();
    private final Queue<MeshBuildResult> completedBuilds = new ConcurrentLinkedQueue<>();

    // ========== CONFIGURATION ==========
    private float timeOfDayBrightness = 1.0f;
    private int maxBuildsPerFrame = 4;
    private int maxUploadsPerFrame = 8;

    // ========== TEXTURE ATLAS ==========
    private TextureAtlas atlas;

    // ========== BUILD RESULT ==========
    private static class MeshBuildResult {
        final Chunk chunk;
        final float[] solidData;
        final int solidCount;
        final float[] waterData;
        final int waterCount;

        MeshBuildResult(Chunk chunk, float[] solid, int solidCount, float[] water, int waterCount) {
            this.chunk = chunk;
            this.solidData = solid;
            this.solidCount = solidCount;
            this.waterData = water;
            this.waterCount = waterCount;
        }
    }

    public SimpleMeshRenderer() {
        this.atlas = BlockTextures.getAtlas();

        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        this.meshBuilder = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "SimpleMeshBuilder");
            t.setDaemon(true);
            return t;
        });

        System.out.printf("[SimpleMeshRenderer] Started with %d build threads%n", threads);
    }

    // ========== UPDATE LOOP ==========

    /**
     * ✅ Update meshes - call every frame
     */
    public void update(Collection<Chunk> chunks, Camera camera) {
        // 1. Upload completed meshes
        uploadCompletedMeshes();

        // 2. Queue new builds for chunks that need it
        int buildsStarted = 0;
        for (Chunk chunk : chunks) {
            if (buildsStarted >= maxBuildsPerFrame)
                break;

            if (chunk.isReady() && chunk.needsGeometryRebuild() && !buildingChunks.contains(chunk)) {
                startMeshBuild(chunk);
                buildsStarted++;
            }
        }
    }

    /**
     * ✅ Start async mesh build for a chunk
     */
    private void startMeshBuild(Chunk chunk) {
        buildingChunks.add(chunk);
        chunk.clearRebuildFlags();

        meshBuilder.submit(() -> {
            try {
                MeshBuildResult result = buildChunkMesh(chunk);
                completedBuilds.offer(result);
            } catch (Exception e) {
                System.err.println(
                        "[SimpleMeshRenderer] Build failed for [" + chunk.getChunkX() + ", " + chunk.getChunkZ() + "]");
            } finally {
                buildingChunks.remove(chunk);
            }
        });
    }

    /**
     * ✅ Upload completed meshes to GPU
     */
    private void uploadCompletedMeshes() {
        int uploaded = 0;
        MeshBuildResult result;

        while ((result = completedBuilds.poll()) != null && uploaded < maxUploadsPerFrame) {
            // Destroy old meshes
            ChunkMesh oldSolid = solidMeshes.remove(result.chunk);
            ChunkMesh oldWater = waterMeshes.remove(result.chunk);
            if (oldSolid != null)
                oldSolid.destroy();
            if (oldWater != null)
                oldWater.destroy();

            // Create new meshes
            if (result.solidCount > 0) {
                ChunkMesh mesh = new ChunkMesh();
                mesh.build(result.solidData, result.solidCount);
                solidMeshes.put(result.chunk, mesh);
            }

            if (result.waterCount > 0) {
                ChunkMesh mesh = new ChunkMesh();
                mesh.build(result.waterData, result.waterCount);
                waterMeshes.put(result.chunk, mesh);
            }

            uploaded++;
        }
    }

    // ========== MESH BUILDING ==========

    /**
     * ✅ Build mesh data for entire chunk (runs on background thread)
     */
    private MeshBuildResult buildChunkMesh(Chunk chunk) {
        // Pre-allocate buffers (12 floats per vertex, 4 vertices per face, 6 faces per
        // block max)
        // Estimate: 16*384*16 blocks * 6 faces * 4 vertices * 12 floats = ~70MB max
        // But most faces are culled, so typically much smaller
        float[] solidBuffer = new float[65536 * 12]; // ~780KB buffer
        float[] waterBuffer = new float[16384 * 12]; // ~195KB buffer
        int solidCount = 0;
        int waterCount = 0;

        int chunkWorldX = chunk.getChunkX() * Chunk.CHUNK_SIZE;
        int chunkWorldZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE;

        for (int sectionIdx = 0; sectionIdx < Chunk.SECTION_COUNT; sectionIdx++) {
            ChunkSection section = chunk.getSection(sectionIdx);
            if (section == null || section.isEmpty())
                continue;

            int sectionWorldY = section.getMinWorldY();

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        GameBlock block = section.getBlock(x, y, z);
                        if (block == null || block.isAir())
                            continue;

                        int worldY = sectionWorldY + y;
                        float worldX = chunkWorldX + x;
                        float worldZ = chunkWorldZ + z;

                        boolean isWater = (block == BlockRegistry.WATER);

                        // Check each face for visibility
                        boolean[] faces = checkVisibleFaces(chunk, x, worldY, z, block);

                        if (isWater) {
                            waterCount = addBlockFaces(waterBuffer, waterCount,
                                    worldX, worldY, worldZ,
                                    chunk, x, worldY, z, block, faces, true);
                        } else {
                            solidCount = addBlockFaces(solidBuffer, solidCount,
                                    worldX, worldY, worldZ,
                                    chunk, x, worldY, z, block, faces, false);
                        }
                    }
                }
            }
        }

        // Trim buffers to actual size
        float[] solidFinal = (solidCount > 0) ? Arrays.copyOf(solidBuffer, solidCount) : null;
        float[] waterFinal = (waterCount > 0) ? Arrays.copyOf(waterBuffer, waterCount) : null;

        return new MeshBuildResult(chunk, solidFinal, solidCount, waterFinal, waterCount);
    }

    /**
     * ✅ Check which faces of a block are visible
     */
    private boolean[] checkVisibleFaces(Chunk chunk, int x, int worldY, int z, GameBlock block) {
        boolean[] faces = new boolean[6]; // top, bottom, north, south, east, west

        faces[0] = shouldRenderFace(chunk, x, worldY + 1, z, block); // Top
        faces[1] = shouldRenderFace(chunk, x, worldY - 1, z, block); // Bottom
        faces[2] = shouldRenderFace(chunk, x, worldY, z - 1, block); // North
        faces[3] = shouldRenderFace(chunk, x, worldY, z + 1, block); // South
        faces[4] = shouldRenderFace(chunk, x + 1, worldY, z, block); // East
        faces[5] = shouldRenderFace(chunk, x - 1, worldY, z, block); // West

        return faces;
    }

    /**
     * ✅ Should we render this face?
     */
    private boolean shouldRenderFace(Chunk chunk, int x, int worldY, int z, GameBlock block) {
        if (!Settings.isValidWorldY(worldY)) {
            return worldY > Settings.WORLD_MAX_Y; // Render top faces above world
        }

        // Handle cross-chunk neighbors
        if (x < 0 || x >= Chunk.CHUNK_SIZE || z < 0 || z >= Chunk.CHUNK_SIZE) {
            return true; // Render border faces (simplified)
        }

        GameBlock neighbor = chunk.getBlock(x, worldY, z);
        if (neighbor == null || neighbor.isAir())
            return true;
        if (!neighbor.isSolid())
            return true;
        if (block == BlockRegistry.WATER && neighbor != BlockRegistry.WATER)
            return true;

        return false;
    }

    /**
     * ✅ Add faces to buffer - returns new buffer position
     */
    private int addBlockFaces(float[] buffer, int pos,
            float wx, float wy, float wz,
            Chunk chunk, int localX, int worldY, int localZ,
            GameBlock block, boolean[] faces, boolean isWater) {

        // Get light at block position
        int skyLight = chunk.getSkyLight(localX, worldY, localZ);
        int blockLight = chunk.getBlockLight(localX, worldY, localZ);
        float baseBrightness = getBrightness(Math.max(skyLight, blockLight));

        // Get texture UVs
        float[] uvTop = BlockTextures.getUV(block, "top");
        float[] uvBottom = BlockTextures.getUV(block, "bottom");
        float[] uvSide = BlockTextures.getUV(block, "side");

        // Block color (for grass tinting, etc.)
        float[] tint = block.getBiomeColor();
        float r = tint[0], g = tint[1], b = tint[2];
        float a = isWater ? 0.7f : 1.0f;

        // Top face (Y+)
        if (faces[0]) {
            float brightness = baseBrightness * 1.0f; // Top is brightest
            if (block == BlockRegistry.GRASS_BLOCK) {
                pos = addFace(buffer, pos,
                        wx, wy + 1, wz, wx, wy + 1, wz + 1, wx + 1, wy + 1, wz + 1, wx + 1, wy + 1, wz,
                        r * brightness, g * brightness, b * brightness, a,
                        0, 1, 0, uvTop);
            } else {
                pos = addFace(buffer, pos,
                        wx, wy + 1, wz, wx, wy + 1, wz + 1, wx + 1, wy + 1, wz + 1, wx + 1, wy + 1, wz,
                        brightness, brightness, brightness, a,
                        0, 1, 0, uvTop);
            }
        }

        // Bottom face (Y-)
        if (faces[1]) {
            float brightness = baseBrightness * 0.5f; // Bottom is darkest
            pos = addFace(buffer, pos,
                    wx, wy, wz, wx + 1, wy, wz, wx + 1, wy, wz + 1, wx, wy, wz + 1,
                    brightness, brightness, brightness, a,
                    0, -1, 0, uvBottom);
        }

        // North face (Z-)
        if (faces[2]) {
            float brightness = baseBrightness * 0.8f;
            pos = addFace(buffer, pos,
                    wx, wy, wz, wx, wy + 1, wz, wx + 1, wy + 1, wz, wx + 1, wy, wz,
                    brightness, brightness, brightness, a,
                    0, 0, -1, uvSide);
        }

        // South face (Z+)
        if (faces[3]) {
            float brightness = baseBrightness * 0.8f;
            pos = addFace(buffer, pos,
                    wx, wy, wz + 1, wx + 1, wy, wz + 1, wx + 1, wy + 1, wz + 1, wx, wy + 1, wz + 1,
                    brightness, brightness, brightness, a,
                    0, 0, 1, uvSide);
        }

        // East face (X+)
        if (faces[4]) {
            float brightness = baseBrightness * 0.6f;
            pos = addFace(buffer, pos,
                    wx + 1, wy, wz, wx + 1, wy + 1, wz, wx + 1, wy + 1, wz + 1, wx + 1, wy, wz + 1,
                    brightness, brightness, brightness, a,
                    1, 0, 0, uvSide);
        }

        // West face (X-)
        if (faces[5]) {
            float brightness = baseBrightness * 0.6f;
            pos = addFace(buffer, pos,
                    wx, wy, wz, wx, wy, wz + 1, wx, wy + 1, wz + 1, wx, wy + 1, wz,
                    brightness, brightness, brightness, a,
                    -1, 0, 0, uvSide);
        }

        return pos;
    }

    /**
     * ✅ Add a single quad face to buffer
     */
    private int addFace(float[] buffer, int pos,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float x4, float y4, float z4,
            float r, float g, float b, float a,
            float nx, float ny, float nz,
            float[] uv) {

        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];

        // Vertex 1
        buffer[pos++] = x1;
        buffer[pos++] = y1;
        buffer[pos++] = z1;
        buffer[pos++] = r;
        buffer[pos++] = g;
        buffer[pos++] = b;
        buffer[pos++] = a;
        buffer[pos++] = nx;
        buffer[pos++] = ny;
        buffer[pos++] = nz;
        buffer[pos++] = u1;
        buffer[pos++] = v1;

        // Vertex 2
        buffer[pos++] = x2;
        buffer[pos++] = y2;
        buffer[pos++] = z2;
        buffer[pos++] = r;
        buffer[pos++] = g;
        buffer[pos++] = b;
        buffer[pos++] = a;
        buffer[pos++] = nx;
        buffer[pos++] = ny;
        buffer[pos++] = nz;
        buffer[pos++] = u1;
        buffer[pos++] = v2;

        // Vertex 3
        buffer[pos++] = x3;
        buffer[pos++] = y3;
        buffer[pos++] = z3;
        buffer[pos++] = r;
        buffer[pos++] = g;
        buffer[pos++] = b;
        buffer[pos++] = a;
        buffer[pos++] = nx;
        buffer[pos++] = ny;
        buffer[pos++] = nz;
        buffer[pos++] = u2;
        buffer[pos++] = v2;

        // Vertex 4
        buffer[pos++] = x4;
        buffer[pos++] = y4;
        buffer[pos++] = z4;
        buffer[pos++] = r;
        buffer[pos++] = g;
        buffer[pos++] = b;
        buffer[pos++] = a;
        buffer[pos++] = nx;
        buffer[pos++] = ny;
        buffer[pos++] = nz;
        buffer[pos++] = u2;
        buffer[pos++] = v1;

        return pos;
    }

    /**
     * ✅ Convert light level (0-15) to brightness
     */
    private float getBrightness(int lightLevel) {
        if (lightLevel <= 0)
            return 0.2f;
        if (lightLevel >= 15)
            return 1.0f;
        return 0.2f + (lightLevel / 15.0f) * 0.8f;
    }

    // ========== RENDERING ==========

    /**
     * ✅ Render all solid chunks
     */
    public void renderSolid(Collection<Chunk> chunks, Camera camera) {
        if (atlas != null) {
            glBindTexture(GL_TEXTURE_2D, atlas.getTextureId());
        }

        // Apply time-of-day brightness globally
        glColor4f(timeOfDayBrightness, timeOfDayBrightness, timeOfDayBrightness, 1.0f);

        for (Chunk chunk : chunks) {
            if (!chunk.isReady())
                continue;

            ChunkMesh mesh = solidMeshes.get(chunk);
            if (mesh != null && mesh.getVertexCount() > 0) {
                mesh.render();
            }
        }

        // Reset color
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * ✅ Render all water chunks (with transparency)
     */
    public void renderWater(Collection<Chunk> chunks, Camera camera) {
        if (atlas != null) {
            glBindTexture(GL_TEXTURE_2D, atlas.getTextureId());
        }

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glColor4f(timeOfDayBrightness, timeOfDayBrightness, timeOfDayBrightness, 1.0f);

        for (Chunk chunk : chunks) {
            if (!chunk.isReady())
                continue;

            ChunkMesh mesh = waterMeshes.get(chunk);
            if (mesh != null && mesh.getVertexCount() > 0) {
                mesh.render();
            }
        }

        glDisable(GL_BLEND);
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    // ========== UTILITY ==========

    public void setTimeOfDayBrightness(float brightness) {
        this.timeOfDayBrightness = Math.max(0.2f, Math.min(1.0f, brightness));
    }

    public float getTimeOfDayBrightness() {
        return timeOfDayBrightness;
    }

    public void removeChunk(Chunk chunk) {
        ChunkMesh solid = solidMeshes.remove(chunk);
        ChunkMesh water = waterMeshes.remove(chunk);
        if (solid != null)
            solid.destroy();
        if (water != null)
            water.destroy();
    }

    public int getMeshCount() {
        return solidMeshes.size() + waterMeshes.size();
    }

    public int getPendingBuilds() {
        return buildingChunks.size() + completedBuilds.size();
    }

    public void cleanup() {
        System.out.println("[SimpleMeshRenderer] Cleaning up...");

        meshBuilder.shutdown();

        for (ChunkMesh mesh : solidMeshes.values()) {
            mesh.destroy();
        }
        for (ChunkMesh mesh : waterMeshes.values()) {
            mesh.destroy();
        }

        solidMeshes.clear();
        waterMeshes.clear();
        buildingChunks.clear();
        completedBuilds.clear();

        System.out.println("[SimpleMeshRenderer] Cleanup complete");
    }
}
