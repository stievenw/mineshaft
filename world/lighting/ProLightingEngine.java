package com.mineshaft.world.lighting;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.core.Settings;
import com.mineshaft.world.Chunk;
import com.mineshaft.world.World;

import java.util.*;
import java.util.concurrent.*;

/**
 * ⚡ ProLightingEngine - Minecraft-accurate cross-chunk light propagation
 * 
 * Key features:
 * - Light properly propagates INTO caves/tunnels
 * - Cross-chunk boundary light spreading
 * - Gradual light decay (15→14→13→...→0)
 * - Both skylight AND blocklight propagation
 * 
 * How it works:
 * 1. Initial pass: Mark sky-exposed blocks with light level 15
 * 2. Queue exposed blocks for horizontal spread
 * 3. BFS spreads light in all 6 directions, crossing chunk boundaries
 * 4. Light decreases by 1 per block (except water: -3)
 */
public class ProLightingEngine {

    private final World world;

    // Light update queue - processes across all chunks
    private final Queue<LightNode> skylightQueue = new ConcurrentLinkedQueue<>();
    private final Queue<LightNode> blocklightQueue = new ConcurrentLinkedQueue<>();

    // Track chunks that need mesh rebuild using long key (chunkX << 32 | chunkZ)
    private final Set<Long> dirtyChunks = ConcurrentHashMap.newKeySet();

    // Configuration
    private static final int MAX_UPDATES_PER_FRAME = 5000;
    private static final int[] DX = { 1, -1, 0, 0, 0, 0 };
    private static final int[] DY = { 0, 0, 1, -1, 0, 0 };
    private static final int[] DZ = { 0, 0, 0, 0, 1, -1 };

    private static class LightNode {
        final int x, y, z;
        final int light;

        LightNode(int x, int y, int z, int light) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.light = light;
        }
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    public ProLightingEngine(World world) {
        this.world = world;
        System.out.println("[ProLightingEngine] Initialized with cross-chunk propagation");
    }

    // ========== MAIN API ==========

    /**
     * ✅ Initialize lighting for a newly generated chunk
     * This does the initial vertical pass and queues for horizontal spread
     */
    public void initializeChunkLighting(Chunk chunk) {
        if (chunk == null)
            return;

        int chunkWorldX = chunk.getChunkX() * Chunk.CHUNK_SIZE;
        int chunkWorldZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE;

        // Step 1: Vertical skylight propagation
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                int worldX = chunkWorldX + x;
                int worldZ = chunkWorldZ + z;
                int currentLight = 15;

                for (int index = Chunk.CHUNK_HEIGHT - 1; index >= 0; index--) {
                    int worldY = Settings.indexToWorldY(index);
                    GameBlock block = chunk.getBlock(x, worldY, z);

                    if (block == null || block.isAir()) {
                        chunk.setSkyLight(x, worldY, z, currentLight);
                        // Queue for horizontal spreading
                        if (currentLight > 0) {
                            skylightQueue.add(new LightNode(worldX, worldY, worldZ, currentLight));
                        }
                    } else if (isTransparent(block)) {
                        currentLight = Math.max(0, currentLight - getLightDecay(block));
                        chunk.setSkyLight(x, worldY, z, currentLight);
                        if (currentLight > 0) {
                            skylightQueue.add(new LightNode(worldX, worldY, worldZ, currentLight));
                        }
                    } else {
                        // Solid block - stop vertical propagation
                        currentLight = 0;
                        chunk.setSkyLight(x, worldY, z, 0);
                    }

                    // Block light sources
                    if (block != null && block.getLightLevel() > 0) {
                        int lightLevel = block.getLightLevel();
                        chunk.setBlockLight(x, worldY, z, lightLevel);
                        blocklightQueue.add(new LightNode(worldX, worldY, worldZ, lightLevel));
                    }
                }
            }
        }

        chunk.setLightInitialized(true);
        dirtyChunks.add(chunkKey(chunk.getChunkX(), chunk.getChunkZ()));
    }

    /**
     * ✅ Process queued light updates (call every frame)
     * This spreads light horizontally across chunk boundaries
     */
    public void update() {
        int processed = 0;

        // Process skylight queue
        while (!skylightQueue.isEmpty() && processed < MAX_UPDATES_PER_FRAME) {
            LightNode node = skylightQueue.poll();
            if (node == null)
                break;

            propagateSkylight(node.x, node.y, node.z, node.light);
            processed++;
        }

        // Process blocklight queue
        while (!blocklightQueue.isEmpty() && processed < MAX_UPDATES_PER_FRAME) {
            LightNode node = blocklightQueue.poll();
            if (node == null)
                break;

            propagateBlocklight(node.x, node.y, node.z, node.light);
            processed++;
        }

        // Mark dirty chunks for rebuild
        for (Long key : dirtyChunks) {
            int cx = (int) (key >> 32);
            int cz = key.intValue();
            Chunk chunk = world.getChunk(cx, cz);
            if (chunk != null) {
                chunk.setNeedsGeometryRebuild(true);
            }
        }
        dirtyChunks.clear();
    }

    /**
     * ✅ Propagate skylight to neighbors (crosses chunk boundaries)
     */
    private void propagateSkylight(int x, int y, int z, int light) {
        for (int i = 0; i < 6; i++) {
            int nx = x + DX[i];
            int ny = y + DY[i];
            int nz = z + DZ[i];

            if (!Settings.isValidWorldY(ny))
                continue;

            // Get chunk and local coords (handles cross-chunk)
            int chunkX = Math.floorDiv(nx, Chunk.CHUNK_SIZE);
            int chunkZ = Math.floorDiv(nz, Chunk.CHUNK_SIZE);
            int localX = Math.floorMod(nx, Chunk.CHUNK_SIZE);
            int localZ = Math.floorMod(nz, Chunk.CHUNK_SIZE);

            Chunk chunk = world.getChunk(chunkX, chunkZ);
            if (chunk == null)
                continue;

            GameBlock neighbor = chunk.getBlock(localX, ny, localZ);

            // Skip solid blocks (they block light)
            if (neighbor != null && !neighbor.isAir() && !isTransparent(neighbor)) {
                continue;
            }

            // Calculate new light level
            int decay = (neighbor != null) ? getLightDecay(neighbor) : 1;
            int newLight = light - decay;

            if (newLight <= 0)
                continue;

            // Only update if brighter
            int currentLight = chunk.getSkyLight(localX, ny, localZ);
            if (newLight > currentLight) {
                chunk.setSkyLight(localX, ny, localZ, newLight);
                dirtyChunks.add(chunkKey(chunkX, chunkZ));

                // Continue propagation
                if (newLight > 1) {
                    skylightQueue.add(new LightNode(nx, ny, nz, newLight));
                }
            }
        }
    }

    /**
     * ✅ Propagate blocklight to neighbors (crosses chunk boundaries)
     */
    private void propagateBlocklight(int x, int y, int z, int light) {
        for (int i = 0; i < 6; i++) {
            int nx = x + DX[i];
            int ny = y + DY[i];
            int nz = z + DZ[i];

            if (!Settings.isValidWorldY(ny))
                continue;

            int chunkX = Math.floorDiv(nx, Chunk.CHUNK_SIZE);
            int chunkZ = Math.floorDiv(nz, Chunk.CHUNK_SIZE);
            int localX = Math.floorMod(nx, Chunk.CHUNK_SIZE);
            int localZ = Math.floorMod(nz, Chunk.CHUNK_SIZE);

            Chunk chunk = world.getChunk(chunkX, chunkZ);
            if (chunk == null)
                continue;

            GameBlock neighbor = chunk.getBlock(localX, ny, localZ);

            if (neighbor != null && !neighbor.isAir() && !isTransparent(neighbor)) {
                continue;
            }

            int decay = (neighbor != null) ? getLightDecay(neighbor) : 1;
            int newLight = light - decay;

            if (newLight <= 0)
                continue;

            int currentLight = chunk.getBlockLight(localX, ny, localZ);
            if (newLight > currentLight) {
                chunk.setBlockLight(localX, ny, localZ, newLight);
                dirtyChunks.add(chunkKey(chunkX, chunkZ));

                if (newLight > 1) {
                    blocklightQueue.add(new LightNode(nx, ny, nz, newLight));
                }
            }
        }
    }

    // ========== BLOCK CHANGE HANDLING ==========

    /**
     * ✅ Called when block is placed/removed - recalculates affected lighting
     */
    public void onBlockChange(int worldX, int worldY, int worldZ, GameBlock oldBlock, GameBlock newBlock) {
        int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
        int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);
        int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);

        Chunk chunk = world.getChunk(chunkX, chunkZ);
        if (chunk == null)
            return;

        // If block was removed (now air), recalculate light at this position
        if (newBlock == null || newBlock.isAir()) {
            // Find max light from neighbors
            int maxSkyLight = 0;
            int maxBlockLight = 0;

            for (int i = 0; i < 6; i++) {
                int nx = worldX + DX[i];
                int ny = worldY + DY[i];
                int nz = worldZ + DZ[i];

                if (!Settings.isValidWorldY(ny))
                    continue;

                int ncx = Math.floorDiv(nx, Chunk.CHUNK_SIZE);
                int ncz = Math.floorDiv(nz, Chunk.CHUNK_SIZE);
                int nlx = Math.floorMod(nx, Chunk.CHUNK_SIZE);
                int nlz = Math.floorMod(nz, Chunk.CHUNK_SIZE);

                Chunk nc = world.getChunk(ncx, ncz);
                if (nc == null)
                    continue;

                maxSkyLight = Math.max(maxSkyLight, nc.getSkyLight(nlx, ny, nlz) - 1);
                maxBlockLight = Math.max(maxBlockLight, nc.getBlockLight(nlx, ny, nlz) - 1);
            }

            // Check above for direct skylight
            if (worldY < Settings.WORLD_MAX_Y) {
                int aboveY = worldY + 1;
                int aboveSky = chunk.getSkyLight(localX, aboveY, localZ);
                if (aboveSky == 15) {
                    maxSkyLight = 15; // Direct skylight
                }
            }

            if (maxSkyLight > 0) {
                chunk.setSkyLight(localX, worldY, localZ, maxSkyLight);
                skylightQueue.add(new LightNode(worldX, worldY, worldZ, maxSkyLight));
            }

            if (maxBlockLight > 0) {
                chunk.setBlockLight(localX, worldY, localZ, maxBlockLight);
                blocklightQueue.add(new LightNode(worldX, worldY, worldZ, maxBlockLight));
            }
        }
        // If block was placed (now solid), block light
        else if (newBlock.isSolid() && !isTransparent(newBlock)) {
            chunk.setSkyLight(localX, worldY, localZ, 0);
            chunk.setBlockLight(localX, worldY, localZ, 0);

            // Recalculate neighbors
            for (int i = 0; i < 6; i++) {
                int nx = worldX + DX[i];
                int ny = worldY + DY[i];
                int nz = worldZ + DZ[i];

                if (Settings.isValidWorldY(ny)) {
                    // Force neighbor update by re-queueing
                    int ncx = Math.floorDiv(nx, Chunk.CHUNK_SIZE);
                    int ncz = Math.floorDiv(nz, Chunk.CHUNK_SIZE);
                    int nlx = Math.floorMod(nx, Chunk.CHUNK_SIZE);
                    int nlz = Math.floorMod(nz, Chunk.CHUNK_SIZE);

                    Chunk nc = world.getChunk(ncx, ncz);
                    if (nc != null) {
                        int sl = nc.getSkyLight(nlx, ny, nlz);
                        int bl = nc.getBlockLight(nlx, ny, nlz);
                        if (sl > 0)
                            skylightQueue.add(new LightNode(nx, ny, nz, sl));
                        if (bl > 0)
                            blocklightQueue.add(new LightNode(nx, ny, nz, bl));
                    }
                }
            }
        }

        // If new block emits light
        if (newBlock != null && newBlock.getLightLevel() > 0) {
            int light = newBlock.getLightLevel();
            chunk.setBlockLight(localX, worldY, localZ, light);
            blocklightQueue.add(new LightNode(worldX, worldY, worldZ, light));
        }

        dirtyChunks.add(chunkKey(chunkX, chunkZ));
    }

    // ========== HELPERS ==========

    private boolean isTransparent(GameBlock block) {
        if (block == null)
            return true;
        if (block.isAir())
            return true;
        if (block == BlockRegistry.WATER)
            return true;
        if (block == BlockRegistry.OAK_LEAVES)
            return true;
        // Skip GLASS check since it may not exist in registry
        return !block.isSolid();
    }

    private int getLightDecay(GameBlock block) {
        if (block == null || block.isAir())
            return 1;
        if (block == BlockRegistry.WATER)
            return 3; // Water absorbs more light
        if (block == BlockRegistry.OAK_LEAVES)
            return 1;
        return 1;
    }

    public int getPendingUpdates() {
        return skylightQueue.size() + blocklightQueue.size();
    }

    public void clearQueues() {
        skylightQueue.clear();
        blocklightQueue.clear();
        dirtyChunks.clear();
    }

    /**
     * ✅ Queue a chunk's edge light for cross-chunk propagation
     * Call this after a chunk is generated and has basic lighting
     * This will spread light from this chunk into neighboring chunks
     */
    public void queueChunkEdgeLighting(Chunk chunk) {
        if (chunk == null)
            return;

        int chunkWorldX = chunk.getChunkX() * Chunk.CHUNK_SIZE;
        int chunkWorldZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE;

        // Queue edges of the chunk for spreading to neighbors
        for (int y = Settings.WORLD_MIN_Y; y <= Settings.WORLD_MAX_Y; y++) {
            // West edge (x=0) - spreads to chunk at x-1
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                int light = chunk.getSkyLight(0, y, z);
                if (light > 1) {
                    skylightQueue.add(new LightNode(chunkWorldX, y, chunkWorldZ + z, light));
                }
                light = chunk.getBlockLight(0, y, z);
                if (light > 1) {
                    blocklightQueue.add(new LightNode(chunkWorldX, y, chunkWorldZ + z, light));
                }
            }

            // East edge (x=15) - spreads to chunk at x+1
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                int light = chunk.getSkyLight(15, y, z);
                if (light > 1) {
                    skylightQueue.add(new LightNode(chunkWorldX + 15, y, chunkWorldZ + z, light));
                }
                light = chunk.getBlockLight(15, y, z);
                if (light > 1) {
                    blocklightQueue.add(new LightNode(chunkWorldX + 15, y, chunkWorldZ + z, light));
                }
            }

            // North edge (z=0) - spreads to chunk at z-1
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                int light = chunk.getSkyLight(x, y, 0);
                if (light > 1) {
                    skylightQueue.add(new LightNode(chunkWorldX + x, y, chunkWorldZ, light));
                }
                light = chunk.getBlockLight(x, y, 0);
                if (light > 1) {
                    blocklightQueue.add(new LightNode(chunkWorldX + x, y, chunkWorldZ, light));
                }
            }

            // South edge (z=15) - spreads to chunk at z+1
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                int light = chunk.getSkyLight(x, y, 15);
                if (light > 1) {
                    skylightQueue.add(new LightNode(chunkWorldX + x, y, chunkWorldZ + 15, light));
                }
                light = chunk.getBlockLight(x, y, 15);
                if (light > 1) {
                    blocklightQueue.add(new LightNode(chunkWorldX + x, y, chunkWorldZ + 15, light));
                }
            }
        }
    }
}
