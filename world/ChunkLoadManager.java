// src/main/java/com/mineshaft/world/ChunkLoadManager.java
package com.mineshaft.world;

import com.mineshaft.core.Settings;
import com.mineshaft.entity.Camera;

import java.util.*;
import java.util.concurrent.*;

/**
 * ✅ OPTIMIZED Chunk Load Manager v2.0
 * - Spiral loading pattern (closest chunks first like Minecraft)
 * - Deduplication to avoid queue bloat
 * - Dynamic thread count based on CPU cores
 * - Batched loading for smoother performance
 */
public class ChunkLoadManager {

    private final World world;
    private final Set<Long> loadedChunks = ConcurrentHashMap.newKeySet();
    private final Set<Long> simulationChunks = ConcurrentHashMap.newKeySet();
    private final Set<Long> queuedChunks = ConcurrentHashMap.newKeySet(); // ✅ Prevent duplicates
    private final PriorityBlockingQueue<ChunkLoadTask> loadQueue = new PriorityBlockingQueue<>();

    // ✅ Cache for spiral pattern
    private int[][] spiralPattern;
    private int cachedRenderDistance = -1;

    public ChunkLoadManager(World world) {
        this.world = world;
        Settings.validateDistances();
        rebuildSpiralPattern();
    }

    /**
     * ✅ Build spiral loading pattern (Minecraft-style: center outward)
     */
    private void rebuildSpiralPattern() {
        int rd = Settings.RENDER_DISTANCE;
        if (rd == cachedRenderDistance && spiralPattern != null) {
            return;
        }
        cachedRenderDistance = rd;

        int diameter = rd * 2 + 1;
        List<int[]> points = new ArrayList<>(diameter * diameter);

        // Generate all points within render distance
        for (int dx = -rd; dx <= rd; dx++) {
            for (int dz = -rd; dz <= rd; dz++) {
                points.add(new int[] { dx, dz, dx * dx + dz * dz });
            }
        }

        // Sort by distance squared (spiral from center)
        points.sort(Comparator.comparingInt(a -> a[2]));

        spiralPattern = new int[points.size()][2];
        for (int i = 0; i < points.size(); i++) {
            spiralPattern[i][0] = points.get(i)[0];
            spiralPattern[i][1] = points.get(i)[1];
        }
    }

    /**
     * ✅ Update chunks berdasarkan posisi player
     */
    public void update(Camera camera) {
        int playerChunkX = (int) Math.floor(camera.getX() / Chunk.CHUNK_SIZE);
        int playerChunkZ = (int) Math.floor(camera.getZ() / Chunk.CHUNK_SIZE);

        // Rebuild spiral if render distance changed
        if (cachedRenderDistance != Settings.RENDER_DISTANCE) {
            rebuildSpiralPattern();
        }

        queueChunksToLoad(playerChunkX, playerChunkZ);
        updateSimulationChunks(playerChunkX, playerChunkZ);
        unloadDistantChunks(playerChunkX, playerChunkZ);
        processLoadQueue();
    }

    private void queueChunksToLoad(int playerChunkX, int playerChunkZ) {
        // ✅ Use spiral pattern for priority loading
        for (int[] offset : spiralPattern) {
            int chunkX = playerChunkX + offset[0];
            int chunkZ = playerChunkZ + offset[1];
            long key = chunkKey(chunkX, chunkZ);

            // ✅ Skip if already loaded or queued
            if (!loadedChunks.contains(key) && !queuedChunks.contains(key)) {
                double distSq = offset[0] * offset[0] + offset[1] * offset[1];
                loadQueue.offer(new ChunkLoadTask(chunkX, chunkZ, distSq));
                queuedChunks.add(key);
            }
        }
    }

    private void updateSimulationChunks(int playerChunkX, int playerChunkZ) {
        simulationChunks.clear();
        int simDist = Settings.getEffectiveSimulationDistance();

        for (int dx = -simDist; dx <= simDist; dx++) {
            for (int dz = -simDist; dz <= simDist; dz++) {
                int chunkX = playerChunkX + dx;
                int chunkZ = playerChunkZ + dz;
                long key = chunkKey(chunkX, chunkZ);

                if (loadedChunks.contains(key)) {
                    simulationChunks.add(key);
                }
            }
        }
    }

    private void unloadDistantChunks(int playerChunkX, int playerChunkZ) {
        int unloaded = 0;
        int maxUnload = Settings.MAX_CHUNKS_UNLOAD_PER_FRAME;
        int unloadDist = Settings.RENDER_DISTANCE + Settings.CHUNK_UNLOAD_BUFFER;

        Iterator<Long> iterator = loadedChunks.iterator();

        while (iterator.hasNext() && unloaded < maxUnload) {
            long key = iterator.next();
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;

            int dx = Math.abs(chunkX - playerChunkX);
            int dz = Math.abs(chunkZ - playerChunkZ);

            if (dx > unloadDist || dz > unloadDist) {
                iterator.remove();
                simulationChunks.remove(key);
                queuedChunks.remove(key);
                world.unloadChunk(chunkX, chunkZ);
                unloaded++;
            }
        }
    }

    private void processLoadQueue() {
        int loaded = 0;
        int maxLoad = Settings.MAX_CHUNKS_LOAD_PER_FRAME;

        while (!loadQueue.isEmpty() && loaded < maxLoad) {
            ChunkLoadTask task = loadQueue.poll();
            if (task != null) {
                long key = chunkKey(task.chunkX, task.chunkZ);

                if (!loadedChunks.contains(key)) {
                    world.loadChunk(task.chunkX, task.chunkZ);
                    loadedChunks.add(key);
                    loaded++;
                }
                queuedChunks.remove(key);
            }
        }
    }

    /**
     * ✅ Cek apakah chunk dalam simulation distance
     */
    public boolean isSimulationChunk(int chunkX, int chunkZ) {
        return simulationChunks.contains(chunkKey(chunkX, chunkZ));
    }

    /**
     * ✅ Get chunks yang perlu di-render
     */
    public Collection<Chunk> getChunksToRender() {
        List<Chunk> chunks = new ArrayList<>();
        for (long key : loadedChunks) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            Chunk chunk = world.getChunk(chunkX, chunkZ);
            if (chunk != null && chunk.isReady()) {
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    public int getLoadedChunkCount() {
        return loadedChunks.size();
    }

    public int getPendingLoadCount() {
        return loadQueue.size();
    }

    private long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static class ChunkLoadTask implements Comparable<ChunkLoadTask> {
        final int chunkX, chunkZ;
        final double distanceSquared;

        ChunkLoadTask(int x, int z, double distSq) {
            this.chunkX = x;
            this.chunkZ = z;
            this.distanceSquared = distSq;
        }

        @Override
        public int compareTo(ChunkLoadTask other) {
            return Double.compare(this.distanceSquared, other.distanceSquared);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof ChunkLoadTask))
                return false;
            ChunkLoadTask other = (ChunkLoadTask) obj;
            return chunkX == other.chunkX && chunkZ == other.chunkZ;
        }

        @Override
        public int hashCode() {
            return Objects.hash(chunkX, chunkZ);
        }
    }
}