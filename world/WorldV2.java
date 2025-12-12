// src/main/java/com/mineshaft/world/WorldV2.java
package com.mineshaft.world;

import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.core.TimeOfDay;
import com.mineshaft.entity.Camera;
import com.mineshaft.render.SimpleMeshRenderer;

import java.util.*;

/**
 * ⚡ NEW! WorldV2 - Lightweight World System
 * 
 * Complete rewrite using:
 * - LightweightChunkManager (simple chunk loading)
 * - SimpleMeshRenderer (efficient mesh building)
 * - Instant lighting (no complex BFS)
 * 
 * Key improvements:
 * - Chunks are visible INSTANTLY after generation
 * - No separate "lighting phase" blocking visibility
 * - Time-of-day affects rendering, not mesh data
 * - Much simpler code flow and less overhead
 */
public class WorldV2 {

    // ========== CORE SYSTEMS ==========
    private final LightweightChunkManager chunkManager;
    private final SimpleMeshRenderer meshRenderer;
    private final TimeOfDay timeOfDay;

    // ========== CONFIGURATION ==========
    private long seed = System.currentTimeMillis();
    private int renderDistance = Settings.RENDER_DISTANCE;

    public WorldV2(TimeOfDay timeOfDay) {
        this.timeOfDay = timeOfDay;
        this.chunkManager = new LightweightChunkManager();
        this.meshRenderer = new SimpleMeshRenderer();

        // Set initial render distance
        chunkManager.setRenderDistance(renderDistance);

        // Set initial time brightness
        if (timeOfDay != null) {
            meshRenderer.setTimeOfDayBrightness(timeOfDay.getBrightness());
        }

        System.out.println("[WorldV2] Created with lightweight systems (render distance: " + renderDistance + ")");
    }

    // ========== MAIN LOOP ==========

    /**
     * ✅ Update world - call every frame
     */
    public void update(Camera camera) {
        // 1. Update chunk loading (loads/unloads based on player position)
        chunkManager.update(camera);

        // 2. Update mesh builder (processes chunks that need mesh rebuild)
        meshRenderer.update(chunkManager.getLoadedChunks(), camera);

        // 3. Update time-of-day brightness
        if (timeOfDay != null) {
            meshRenderer.setTimeOfDayBrightness(timeOfDay.getBrightness());
        }
    }

    /**
     * ✅ Render world
     */
    public void render(Camera camera) {
        Collection<Chunk> readyChunks = chunkManager.getReadyChunks();

        // Render solid geometry
        meshRenderer.renderSolid(readyChunks, camera);

        // Render water (transparent)
        meshRenderer.renderWater(readyChunks, camera);
    }

    // ========== CHUNK ACCESS ==========

    public Chunk getChunk(int chunkX, int chunkZ) {
        return chunkManager.getChunk(chunkX, chunkZ);
    }

    public Collection<Chunk> getChunks() {
        return chunkManager.getLoadedChunks();
    }

    public int getLoadedChunkCount() {
        return chunkManager.getLoadedChunkCount();
    }

    public int getPendingChunkCount() {
        return chunkManager.getPendingChunkCount();
    }

    // ========== BLOCK ACCESS ==========

    public GameBlock getBlock(int worldX, int worldY, int worldZ) {
        return chunkManager.getBlock(worldX, worldY, worldZ);
    }

    public void setBlock(int worldX, int worldY, int worldZ, GameBlock block) {
        chunkManager.setBlock(worldX, worldY, worldZ, block);
    }

    // ========== LIGHTING ==========

    public int getSkyLight(int worldX, int worldY, int worldZ) {
        if (!Settings.isValidWorldY(worldY))
            return 0;

        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);

        Chunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        if (chunk == null || !chunk.isReady())
            return 0;

        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);

        return chunk.getSkyLight(localX, worldY, localZ);
    }

    public int getBlockLight(int worldX, int worldY, int worldZ) {
        if (!Settings.isValidWorldY(worldY))
            return 0;

        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);

        Chunk chunk = chunkManager.getChunk(chunkX, chunkZ);
        if (chunk == null || !chunk.isReady())
            return 0;

        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);

        return chunk.getBlockLight(localX, worldY, localZ);
    }

    public int getLight(int worldX, int worldY, int worldZ) {
        return Math.max(getSkyLight(worldX, worldY, worldZ),
                getBlockLight(worldX, worldY, worldZ));
    }

    // ========== TIME OF DAY ==========

    public TimeOfDay getTimeOfDay() {
        return timeOfDay;
    }

    public void setTimeOfDayBrightness(float brightness) {
        meshRenderer.setTimeOfDayBrightness(brightness);
    }

    public float getTimeOfDayBrightness() {
        return meshRenderer.getTimeOfDayBrightness();
    }

    // ========== CONFIGURATION ==========

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public long getSeed() {
        return seed;
    }

    public void setRenderDistance(int distance) {
        this.renderDistance = Math.max(2, Math.min(32, distance));
        chunkManager.setRenderDistance(this.renderDistance);
    }

    public int getRenderDistance() {
        return renderDistance;
    }

    // ========== GETTERS ==========

    public SimpleMeshRenderer getRenderer() {
        return meshRenderer;
    }

    public LightweightChunkManager getChunkManager() {
        return chunkManager;
    }

    // ========== CLEANUP ==========

    public void cleanup() {
        System.out.println("[WorldV2] Cleaning up...");
        chunkManager.shutdown();
        meshRenderer.cleanup();
        System.out.println("[WorldV2] Cleanup complete");
    }
}
