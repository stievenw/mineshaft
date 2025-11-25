
package com.mineshaft.world;

import com.mineshaft.block.GameBlock;
import com.mineshaft.block.BlockRegistry;
import com.mineshaft.core.TimeOfDay;
import com.mineshaft.entity.Camera;
import com.mineshaft.render.ChunkRenderer;
import com.mineshaft.world.lighting.LightingEngine;

import java.util.*;

/**
 * ⚡ OPTIMIZED World with Async Lighting & Mesh Building
 * ✅ FIXED - Added logging and consistency checks for chunk tracking
 */
public class World {
    private Map<ChunkPos, Chunk> chunks = new HashMap<>();
    private ChunkRenderer renderer = new ChunkRenderer();
    private LightingEngine lightingEngine;

    private int renderDistance = 8;

    private TimeOfDay timeOfDay;

    private long lastSunRebuildTime = 0;
    private static final long SUN_REBUILD_INTERVAL_MS = 100;

    // ✅ Debug tracking
    private int lastChunkCount = 0;
    private long lastChunkCountLog = 0;
    private static final long CHUNK_LOG_INTERVAL = 2000; // Log every 2 seconds

    public World(TimeOfDay timeOfDay) {
        this.timeOfDay = timeOfDay;
        renderer.setWorld(this);
        lightingEngine = new LightingEngine(this, timeOfDay);
        renderer.setLightingEngine(lightingEngine);

        System.out.println("World created (render distance: " + renderDistance + " chunks)");
    }

    public TimeOfDay getTimeOfDay() {
        return timeOfDay;
    }

    public LightingEngine getLightingEngine() {
        return lightingEngine;
    }

    public ChunkRenderer getRenderer() {
        return renderer;
    }

    public Collection<Chunk> getChunks() {
        return chunks.values();
    }

    /**
     * ✅ FIXED - Better chunk management with logging
     */
    public void updateChunks(int centerChunkX, int centerChunkZ) {
        Set<ChunkPos> chunksToLoad = new HashSet<>();
        Set<ChunkPos> chunksToUnload = new HashSet<>();

        // ✅ Calculate which chunks should be loaded
        for (int x = centerChunkX - renderDistance; x <= centerChunkX + renderDistance; x++) {
            for (int z = centerChunkZ - renderDistance; z <= centerChunkZ + renderDistance; z++) {
                int dx = x - centerChunkX;
                int dz = z - centerChunkZ;
                if (dx * dx + dz * dz <= renderDistance * renderDistance) {
                    ChunkPos pos = new ChunkPos(x, z);
                    chunksToLoad.add(pos);
                }
            }
        }

        // ✅ Find chunks to unload (chunks that exist but shouldn't)
        for (ChunkPos pos : chunks.keySet()) {
            if (!chunksToLoad.contains(pos)) {
                chunksToUnload.add(pos);
            }
        }

        // ✅ Unload first
        for (ChunkPos pos : chunksToUnload) {
            unloadChunk(pos);
        }

        // ✅ Then load new chunks
        for (ChunkPos pos : chunksToLoad) {
            if (!chunks.containsKey(pos)) {
                loadChunk(pos.x, pos.z);
            }
        }

        updateLighting();

        // ✅ Debug logging
        logChunkCount();
    }

    /**
     * ✅ NEW - Log chunk count changes
     */
    private void logChunkCount() {
        int currentCount = chunks.size();
        long now = System.currentTimeMillis();

        // Log if count changed OR every 2 seconds
        if (currentCount != lastChunkCount || (now - lastChunkCountLog > CHUNK_LOG_INTERVAL)) {
            if (currentCount != lastChunkCount) {
                System.out.println(String.format(
                        "[World] Chunks: %d -> %d (%+d)",
                        lastChunkCount, currentCount, (currentCount - lastChunkCount)));
            }
            lastChunkCount = currentCount;
            lastChunkCountLog = now;
        }
    }

    private void updateLighting() {
        int skylightLevel = (timeOfDay != null) ? timeOfDay.getSkylightLevel() : 15;

        for (Chunk chunk : chunks.values()) {
            if (chunk.isGenerated() && !chunk.isLightInitialized()) {
                lightingEngine.initializeSkylightForChunk(chunk, skylightLevel);
                lightingEngine.initializeBlocklightForChunk(chunk);

                chunk.setLightInitialized(true);
                chunk.setNeedsRebuild(true);
            }
        }
    }

    public void updateSkylightForTimeChange() {
        if (timeOfDay == null)
            return;

        for (Chunk chunk : chunks.values()) {
            if (chunk.isGenerated() && chunk.isLightInitialized()) {
                lightingEngine.queueChunkForLightUpdate(chunk);
            }
        }
    }

    public void updateSunLight() {
        if (lightingEngine != null) {
            boolean sunDirectionChanged = lightingEngine.updateSunLight();

            if (sunDirectionChanged) {
                long currentTime = System.currentTimeMillis();

                if (currentTime - lastSunRebuildTime >= SUN_REBUILD_INTERVAL_MS) {
                    lastSunRebuildTime = currentTime;

                    for (Chunk chunk : chunks.values()) {
                        if (chunk.isGenerated() && chunk.isLightInitialized()) {
                            chunk.setNeedsRebuild(true);
                        }
                    }
                }
            }
        }
    }

    /**
     * ✅ FIXED - Better logging
     */
    private void loadChunk(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (!chunks.containsKey(pos)) {
            Chunk chunk = new Chunk(chunkX, chunkZ);
            chunks.put(pos, chunk);

            markNeighborsForRebuild(chunkX, chunkZ);

            // ✅ Optional: Detailed logging (comment out for production)
            // System.out.println("[World] Loaded chunk " + pos);
        }
    }

    private void markNeighborsForRebuild(int chunkX, int chunkZ) {
        int[][] neighbors = {
                { chunkX - 1, chunkZ },
                { chunkX + 1, chunkZ },
                { chunkX, chunkZ - 1 },
                { chunkX, chunkZ + 1 },
        };

        for (int[] neighbor : neighbors) {
            ChunkPos neighborPos = new ChunkPos(neighbor[0], neighbor[1]);
            Chunk neighborChunk = chunks.get(neighborPos);
            if (neighborChunk != null) {
                neighborChunk.setNeedsRebuild(true);
            }
        }
    }

    /**
     * ✅ FIXED - Better logging and cleanup
     */
    private void unloadChunk(ChunkPos pos) {
        Chunk chunk = chunks.remove(pos);
        if (chunk != null) {
            // Cancel any pending lighting updates for this chunk
            lightingEngine.cancelChunkUpdates(chunk);

            // Remove from renderer
            renderer.removeChunk(chunk);

            // ✅ Optional: Detailed logging (comment out for production)
            // System.out.println("[World] Unloaded chunk " + pos);
        }
    }

    public GameBlock getBlock(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= Chunk.CHUNK_HEIGHT) {
            return BlockRegistry.AIR;
        }

        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);

        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        Chunk chunk = chunks.get(pos);

        if (chunk == null) {
            return BlockRegistry.AIR;
        }

        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);

        return chunk.getBlock(localX, worldY, localZ);
    }

    public void setBlock(int worldX, int worldY, int worldZ, GameBlock block) {
        if (worldY < 0 || worldY >= Chunk.CHUNK_HEIGHT) {
            return;
        }

        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);

        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        Chunk chunk = chunks.get(pos);

        if (chunk != null) {
            int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
            int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);

            chunk.setBlock(localX, worldY, localZ, block);

            if (block.isAir()) {
                lightingEngine.onBlockRemoved(chunk, localX, worldY, localZ);
            } else {
                lightingEngine.onBlockPlaced(chunk, localX, worldY, localZ, block);
            }

            if (localX == 0 || localX == Chunk.CHUNK_SIZE - 1 ||
                    localZ == 0 || localZ == Chunk.CHUNK_SIZE - 1) {
                markNeighborsForRebuild(chunkX, chunkZ);
            }
        }
    }

    // ========== Light Query Methods ==========

    public int getLight(int worldX, int worldY, int worldZ) {
        int skyLight = getSkyLight(worldX, worldY, worldZ);
        int blockLight = getBlockLight(worldX, worldY, worldZ);
        return Math.max(skyLight, blockLight);
    }

    public int getSkyLight(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= Chunk.CHUNK_HEIGHT) {
            return 0;
        }

        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);

        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        Chunk chunk = chunks.get(pos);

        if (chunk == null || !chunk.isLightInitialized()) {
            return 0;
        }

        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);

        return chunk.getSkyLight(localX, worldY, localZ);
    }

    public int getBlockLight(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= Chunk.CHUNK_HEIGHT) {
            return 0;
        }

        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);

        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        Chunk chunk = chunks.get(pos);

        if (chunk == null || !chunk.isLightInitialized()) {
            return 0;
        }

        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);

        return chunk.getBlockLight(localX, worldY, localZ);
    }

    // ========== Rendering ==========

    public void render(Camera camera) {
        List<Chunk> visibleChunks = new ArrayList<>();

        for (Chunk chunk : chunks.values()) {
            if (chunk.isGenerated()) {
                float chunkCenterX = chunk.getChunkX() * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2.0f;
                float chunkCenterZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2.0f;

                float dx = chunkCenterX - camera.getX();
                float dz = chunkCenterZ - camera.getZ();
                float distance = (float) Math.sqrt(dx * dx + dz * dz);

                if (distance < renderDistance * Chunk.CHUNK_SIZE) {
                    renderer.renderChunk(chunk, camera);
                    visibleChunks.add(chunk);
                }
            }
        }

        renderer.renderSolidPass(visibleChunks);
        renderer.renderTranslucentPass(visibleChunks, camera);
        renderer.renderWaterPass(visibleChunks, camera);
    }

    public Chunk getChunk(int chunkX, int chunkZ) {
        return chunks.get(new ChunkPos(chunkX, chunkZ));
    }

    /**
     * ✅ VERIFIED - This is correct, returns current chunk count
     */
    public int getLoadedChunkCount() {
        return chunks.size();
    }

    /**
     * ✅ NEW - Get render distance
     */
    public int getRenderDistance() {
        return renderDistance;
    }

    /**
     * ✅ NEW - Debug method to verify chunk system
     */
    public void debugChunkSystem(int playerChunkX, int playerChunkZ) {
        System.out.println("=== CHUNK DEBUG ===");
        System.out.println("Player at chunk: [" + playerChunkX + ", " + playerChunkZ + "]");
        System.out.println("Render distance: " + renderDistance);
        System.out.println("Total chunks: " + chunks.size());
        System.out.println("Chunks map: " + chunks.keySet());

        // Calculate expected chunks
        int expectedChunks = 0;
        for (int x = playerChunkX - renderDistance; x <= playerChunkX + renderDistance; x++) {
            for (int z = playerChunkZ - renderDistance; z <= playerChunkZ + renderDistance; z++) {
                int dx = x - playerChunkX;
                int dz = z - playerChunkZ;
                if (dx * dx + dz * dz <= renderDistance * renderDistance) {
                    expectedChunks++;
                }
            }
        }
        System.out.println("Expected chunks: " + expectedChunks);
        System.out.println("==================");
    }

    public void cleanup() {
        System.out.println("Cleaning up world (" + chunks.size() + " chunks)...");

        if (lightingEngine != null) {
            lightingEngine.shutdown();
        }

        renderer.cleanup();
        chunks.clear();
    }

    private static class ChunkPos {
        final int x, z;

        ChunkPos(int x, int z) {
            this.x = x;
            this.z = z;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof ChunkPos))
                return false;
            ChunkPos that = (ChunkPos) o;
            return x == that.x && z == that.z;
        }

        @Override
        public int hashCode() {
            return 31 * x + z;
        }

        @Override
        public String toString() {
            return "[" + x + ", " + z + "]";
        }
    }
}