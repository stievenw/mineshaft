package com.mineshaft.world;

import com.mineshaft.block.Block;
import com.mineshaft.block.Blocks;
import com.mineshaft.core.TimeOfDay;
import com.mineshaft.entity.Camera;
import com.mineshaft.render.ChunkRenderer;
import com.mineshaft.world.lighting.LightingEngine;

import java.util.*;

/**
 * ✅ World with SYNCHRONIZED sun lighting
 */
public class World {
    private Map<ChunkPos, Chunk> chunks = new HashMap<>();
    private ChunkRenderer renderer = new ChunkRenderer();
    private LightingEngine lightingEngine;
    
    private int renderDistance = 8;
    private Set<ChunkPos> loadedChunks = new HashSet<>();
    
    private TimeOfDay timeOfDay;
    
    // ✅ NEW: Rebuild throttling
    private long lastSunRebuildTime = 0;
    private static final long SUN_REBUILD_INTERVAL_MS = 100; // Rebuild max every 100ms
    
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
    
    public Collection<Chunk> getChunks() {
        return chunks.values();
    }
    
    public void updateChunks(int centerChunkX, int centerChunkZ) {
        Set<ChunkPos> chunksToLoad = new HashSet<>();
        Set<ChunkPos> chunksToUnload = new HashSet<>(loadedChunks);
        
        for (int x = centerChunkX - renderDistance; x <= centerChunkX + renderDistance; x++) {
            for (int z = centerChunkZ - renderDistance; z <= centerChunkZ + renderDistance; z++) {
                int dx = x - centerChunkX;
                int dz = z - centerChunkZ;
                if (dx * dx + dz * dz <= renderDistance * renderDistance) {
                    ChunkPos pos = new ChunkPos(x, z);
                    chunksToLoad.add(pos);
                    chunksToUnload.remove(pos);
                }
            }
        }
        
        for (ChunkPos pos : chunksToUnload) {
            unloadChunk(pos);
        }
        
        for (ChunkPos pos : chunksToLoad) {
            if (!chunks.containsKey(pos)) {
                loadChunk(pos.x, pos.z);
            }
        }
        
        updateLighting();
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
        if (timeOfDay == null) return;
        
        int skylightLevel = timeOfDay.getSkylightLevel();
        
        for (Chunk chunk : chunks.values()) {
            if (chunk.isGenerated() && chunk.isLightInitialized()) {
                lightingEngine.initializeSkylightForChunk(chunk, skylightLevel);
                chunk.setNeedsRebuild(true);
            }
        }
    }
    
    /**
     * ✅ UPDATED: Rebuild chunks when sun direction changes significantly
     */
    public void updateSunLight() {
        if (lightingEngine != null) {
            // Update sun direction and check if it changed significantly
            boolean sunDirectionChanged = lightingEngine.updateSunLight();
            
            if (sunDirectionChanged) {
                long currentTime = System.currentTimeMillis();
                
                // ✅ Throttle rebuilds (prevent lag from too frequent updates)
                if (currentTime - lastSunRebuildTime >= SUN_REBUILD_INTERVAL_MS) {
                    lastSunRebuildTime = currentTime;
                    
                    // ✅ Rebuild all loaded chunks for updated shadows
                    for (Chunk chunk : chunks.values()) {
                        if (chunk.isGenerated() && chunk.isLightInitialized()) {
                            chunk.setNeedsRebuild(true);
                        }
                    }
                }
            }
        }
    }
    
    private void loadChunk(int chunkX, int chunkZ) {
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        if (!chunks.containsKey(pos)) {
            Chunk chunk = new Chunk(chunkX, chunkZ);
            chunks.put(pos, chunk);
            loadedChunks.add(pos);
            
            markNeighborsForRebuild(chunkX, chunkZ);
        }
    }
    
    private void markNeighborsForRebuild(int chunkX, int chunkZ) {
        int[][] neighbors = {
            {chunkX - 1, chunkZ},
            {chunkX + 1, chunkZ},
            {chunkX, chunkZ - 1},
            {chunkX, chunkZ + 1},
        };
        
        for (int[] neighbor : neighbors) {
            ChunkPos neighborPos = new ChunkPos(neighbor[0], neighbor[1]);
            Chunk neighborChunk = chunks.get(neighborPos);
            if (neighborChunk != null) {
                neighborChunk.setNeedsRebuild(true);
            }
        }
    }
    
    private void unloadChunk(ChunkPos pos) {
        Chunk chunk = chunks.remove(pos);
        if (chunk != null) {
            renderer.removeChunk(chunk);
            loadedChunks.remove(pos);
        }
    }
    
    public Block getBlock(int worldX, int worldY, int worldZ) {
        if (worldY < 0 || worldY >= Chunk.CHUNK_HEIGHT) {
            return Blocks.AIR;
        }
        
        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);
        
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        Chunk chunk = chunks.get(pos);
        
        if (chunk == null) {
            return null;
        }
        
        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);
        
        return chunk.getBlock(localX, worldY, localZ);
    }
    
    public void setBlock(int worldX, int worldY, int worldZ, Block block) {
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
    
    public int getLoadedChunkCount() {
        return chunks.size();
    }
    
    public void cleanup() {
        System.out.println("Cleaning up world (" + chunks.size() + " chunks)...");
        renderer.cleanup();
        chunks.clear();
        loadedChunks.clear();
    }
    
    private static class ChunkPos {
        final int x, z;
        
        ChunkPos(int x, int z) {
            this.x = x;
            this.z = z;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ChunkPos)) return false;
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