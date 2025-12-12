package com.mineshaft.world;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.entity.Camera;

import java.util.*;
import java.util.concurrent.*;

/**
 * ⚡ NEW! LightweightChunkManager - Simple, Fast Chunk Loading System
 * 
 * This is a COMPLETE REWRITE of chunk loading with focus on:
 * - INSTANT chunk visibility (no waiting for lighting)
 * - Simple distance-based loading (spiral pattern from player)
 * - Minimal overhead and no complex queuing
 * - Direct mesh building without async complexity
 * 
 * Key differences from old system:
 * - Chunks are IMMEDIATELY visible after terrain generation
 * - Lighting is simple (vertical only) and instant
 * - No separate "lighting phase" - it's all done together
 * - Much simpler state machine: EMPTY -> READY (no intermediate states)
 */
public class LightweightChunkManager {

    // ========== CHUNK STORAGE ==========
    private final Map<Long, Chunk> chunks = new ConcurrentHashMap<>();

    // ========== THREAD POOL ==========
    private final ExecutorService chunkLoader;
    private final Set<Long> loadingChunks = ConcurrentHashMap.newKeySet();
    private final Queue<ChunkLoadResult> completedChunks = new ConcurrentLinkedQueue<>();

    // ========== CONFIGURATION ==========
    private int renderDistance = Settings.RENDER_DISTANCE;
    private int maxLoadsPerFrame = 8;
    private int maxProcessPerFrame = 16;

    // ========== PLAYER POSITION ==========
    private int lastPlayerChunkX = Integer.MIN_VALUE;
    private int lastPlayerChunkZ = Integer.MIN_VALUE;

    // ========== SPIRAL PATTERN CACHE ==========
    private int[][] spiralPattern;
    private int lastSpiralDistance = -1;

    // ========== STATISTICS ==========
    private int chunksLoadedThisFrame = 0;

    private static class ChunkLoadResult {
        final Chunk chunk;
        final long key;

        ChunkLoadResult(Chunk chunk, long key) {
            this.chunk = chunk;
            this.key = key;
        }
    }

    public LightweightChunkManager() {
        // Dynamic thread count
        int threads = Math.max(4, Runtime.getRuntime().availableProcessors());
        this.chunkLoader = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "LightChunkLoader");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY);
            return t;
        });

        System.out.printf("[LightweightChunkManager] Started with %d threads%n", threads);
    }

    // ========== MAIN UPDATE LOOP ==========

    /**
     * ✅ Main update - call every frame with player position
     */
    public void update(Camera camera) {
        int playerChunkX = (int) Math.floor(camera.getX() / Chunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(camera.getZ() / Chunk.CHUNK_SIZE);

        // Process completed chunk loads first
        processCompletedChunks();

        // Check if player moved to different chunk
        boolean playerMoved = (playerChunkX != lastPlayerChunkX || playerChunkZ != lastPlayerChunkZ);

        // Unload distant chunks
        unloadDistantChunks(playerChunkX, playerChunkZ);

        // Load new chunks in spiral pattern
        loadChunksAround(playerChunkX, playerChunkZ, playerMoved);

        lastPlayerChunkX = playerChunkX;
        lastPlayerChunkZ = playerChunkZ;
    }

    /**
     * ✅ Process chunks that finished loading in background
     */
    private void processCompletedChunks() {
        int processed = 0;
        ChunkLoadResult result;

        while ((result = completedChunks.poll()) != null && processed < maxProcessPerFrame) {
            // Add to active chunks
            chunks.put(result.key, result.chunk);
            loadingChunks.remove(result.key);
            processed++;
        }
    }

    /**
     * ✅ Unload chunks outside render distance + buffer
     */
    private void unloadDistantChunks(int playerChunkX, int playerChunkZ) {
        int unloadDistance = renderDistance + 2; // Small buffer
        int unloadDistSq = unloadDistance * unloadDistance;

        Iterator<Map.Entry<Long, Chunk>> it = chunks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Chunk> entry = it.next();
            Chunk chunk = entry.getValue();

            int dx = chunk.getChunkX() - playerChunkX;
            int dz = chunk.getChunkZ() - playerChunkZ;
            int distSq = dx * dx + dz * dz;

            if (distSq > unloadDistSq) {
                it.remove();
            }
        }
    }

    /**
     * ✅ Load chunks in spiral pattern (closest first)
     */
    private void loadChunksAround(int playerChunkX, int playerChunkZ, boolean priorityLoad) {
        // Regenerate spiral if render distance changed
        if (renderDistance != lastSpiralDistance) {
            generateSpiralPattern();
            lastSpiralDistance = renderDistance;
        }

        chunksLoadedThisFrame = 0;
        int maxLoads = priorityLoad ? maxLoadsPerFrame * 2 : maxLoadsPerFrame;

        for (int[] offset : spiralPattern) {
            if (chunksLoadedThisFrame >= maxLoads)
                break;

            int chunkX = playerChunkX + offset[0];
            int chunkZ = playerChunkZ + offset[1];
            long key = chunkKey(chunkX, chunkZ);

            // Skip if already loaded or loading
            if (chunks.containsKey(key) || loadingChunks.contains(key)) {
                continue;
            }

            // Start async load
            loadingChunks.add(key);
            chunkLoader.submit(() -> loadChunkAsync(chunkX, chunkZ, key));
            chunksLoadedThisFrame++;
        }
    }

    /**
     * ✅ Async chunk load - terrain + instant lighting
     */
    private void loadChunkAsync(int chunkX, int chunkZ, long key) {
        try {
            Chunk chunk = new Chunk(chunkX, chunkZ);

            // Generate terrain
            chunk.generate();

            // INSTANT lighting - no BFS, just vertical propagation
            initializeInstantLighting(chunk);

            // Mark as ready
            chunk.setState(ChunkState.READY);
            chunk.setLightInitialized(true);
            chunk.setNeedsGeometryRebuild(true);

            // Queue for main thread processing
            completedChunks.offer(new ChunkLoadResult(chunk, key));

        } catch (Exception e) {
            System.err.println("[LightweightChunkManager] Failed to load chunk [" + chunkX + ", " + chunkZ + "]: "
                    + e.getMessage());
            loadingChunks.remove(key);
        }
    }

    /**
     * ✅ INSTANT lighting - simple vertical propagation only
     * No BFS, no horizontal spread - super fast
     */
    private void initializeInstantLighting(Chunk chunk) {
        int skyLight = 15; // Always full for simplicity

        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                int currentLight = skyLight;

                // Top to bottom
                for (int index = Chunk.CHUNK_HEIGHT - 1; index >= 0; index--) {
                    int worldY = Settings.indexToWorldY(index);
                    GameBlock block = chunk.getBlock(x, worldY, z);

                    if (block == null || block.isAir()) {
                        chunk.setSkyLight(x, worldY, z, currentLight);
                    } else if (block == BlockRegistry.WATER || block == BlockRegistry.OAK_LEAVES) {
                        currentLight = Math.max(0, currentLight - 1);
                        chunk.setSkyLight(x, worldY, z, currentLight);
                    } else if (block.isSolid()) {
                        currentLight = 0;
                        chunk.setSkyLight(x, worldY, z, 0);
                    } else {
                        chunk.setSkyLight(x, worldY, z, currentLight);
                    }

                    // Quick blocklight for light sources
                    if (block != null && block.getLightLevel() > 0) {
                        chunk.setBlockLight(x, worldY, z, block.getLightLevel());
                    }
                }
            }
        }
    }

    /**
     * ✅ Generate spiral pattern for chunk loading order
     */
    private void generateSpiralPattern() {
        List<int[]> pattern = new ArrayList<>();

        // Add center
        pattern.add(new int[] { 0, 0 });

        // Spiral outward
        for (int ring = 1; ring <= renderDistance; ring++) {
            // Top edge (left to right)
            for (int x = -ring; x <= ring; x++) {
                pattern.add(new int[] { x, -ring });
            }
            // Right edge (top to bottom, excluding corners)
            for (int z = -ring + 1; z <= ring - 1; z++) {
                pattern.add(new int[] { ring, z });
            }
            // Bottom edge (right to left)
            for (int x = ring; x >= -ring; x--) {
                pattern.add(new int[] { x, ring });
            }
            // Left edge (bottom to top, excluding corners)
            for (int z = ring - 1; z >= -ring + 1; z--) {
                pattern.add(new int[] { -ring, z });
            }
        }

        spiralPattern = pattern.toArray(new int[0][]);
    }

    // ========== CHUNK ACCESS API ==========

    public Chunk getChunk(int chunkX, int chunkZ) {
        return chunks.get(chunkKey(chunkX, chunkZ));
    }

    public Collection<Chunk> getLoadedChunks() {
        return chunks.values();
    }

    public Collection<Chunk> getReadyChunks() {
        List<Chunk> ready = new ArrayList<>();
        for (Chunk chunk : chunks.values()) {
            if (chunk.isReady()) {
                ready.add(chunk);
            }
        }
        return ready;
    }

    public int getLoadedChunkCount() {
        return chunks.size();
    }

    public int getPendingChunkCount() {
        return loadingChunks.size();
    }

    // ========== BLOCK ACCESS API ==========

    public GameBlock getBlock(int worldX, int worldY, int worldZ) {
        if (!Settings.isValidWorldY(worldY)) {
            return BlockRegistry.AIR;
        }

        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);

        Chunk chunk = getChunk(chunkX, chunkZ);
        if (chunk == null || !chunk.isReady()) {
            return BlockRegistry.AIR;
        }

        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);

        return chunk.getBlock(localX, worldY, localZ);
    }

    public void setBlock(int worldX, int worldY, int worldZ, GameBlock block) {
        if (!Settings.isValidWorldY(worldY))
            return;

        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);

        Chunk chunk = getChunk(chunkX, chunkZ);
        if (chunk == null || !chunk.isReady())
            return;

        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);

        chunk.setBlock(localX, worldY, localZ, block);
        chunk.setNeedsGeometryRebuild(true);

        // Mark neighbors if on edge
        if (localX == 0)
            markChunkForRebuild(chunkX - 1, chunkZ);
        if (localX == Chunk.CHUNK_SIZE - 1)
            markChunkForRebuild(chunkX + 1, chunkZ);
        if (localZ == 0)
            markChunkForRebuild(chunkX, chunkZ - 1);
        if (localZ == Chunk.CHUNK_SIZE - 1)
            markChunkForRebuild(chunkX, chunkZ + 1);
    }

    private void markChunkForRebuild(int chunkX, int chunkZ) {
        Chunk chunk = getChunk(chunkX, chunkZ);
        if (chunk != null && chunk.isReady()) {
            chunk.setNeedsGeometryRebuild(true);
        }
    }

    // ========== UTILITY ==========

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public void setRenderDistance(int distance) {
        this.renderDistance = Math.max(2, Math.min(32, distance));
    }

    public int getRenderDistance() {
        return renderDistance;
    }

    // ========== CLEANUP ==========

    public void shutdown() {
        System.out.println("[LightweightChunkManager] Shutting down...");
        chunkLoader.shutdown();
        try {
            if (!chunkLoader.awaitTermination(3, TimeUnit.SECONDS)) {
                chunkLoader.shutdownNow();
            }
        } catch (InterruptedException e) {
            chunkLoader.shutdownNow();
        }
        chunks.clear();
        loadingChunks.clear();
        System.out.println("[LightweightChunkManager] Shutdown complete");
    }
}
