package com.mineshaft.world.lighting;

import com.mineshaft.block.Block;
import com.mineshaft.block.Blocks;
import com.mineshaft.core.TimeOfDay;
import com.mineshaft.world.Chunk;

import java.util.LinkedList;
import java.util.Queue;

/**
 * ✅ Enhanced Lighting Engine - Fixed warnings
 */
public class LightingEngine {
    
    // ✅ REMOVED: Unused field 'world'
    private SunLightCalculator sunLight;
    
    private static final float[] BRIGHTNESS_TABLE = new float[16];
    
    static {
        for (int i = 0; i < 16; i++) {
            if (i <= 0) {
                BRIGHTNESS_TABLE[i] = 0.4f;
            } else if (i >= 15) {
                BRIGHTNESS_TABLE[i] = 1.0f;
            } else {
                float normalized = i / 15.0f;
                BRIGHTNESS_TABLE[i] = 0.4f + (normalized * 0.6f);
            }
        }
    }
    
    private static class LightNode {
        int x, y, z;
        int lightLevel;
        
        LightNode(int x, int y, int z, int lightLevel) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lightLevel = lightLevel;
        }
    }
    
    public LightingEngine(com.mineshaft.world.World world, TimeOfDay timeOfDay) {
        // ✅ REMOVED: this.world = world; (unused)
        this.sunLight = new SunLightCalculator(timeOfDay);
    }
    
    /**
     * ✅ Returns true if sun direction changed significantly
     */
    public boolean updateSunLight() {
        return sunLight.updateSunDirection();
    }
    
    public SunLightCalculator getSunLight() {
        return sunLight;
    }
    
    public void initializeSkylightForChunk(Chunk chunk, int skylightLevel) {
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                int currentLight = skylightLevel;
                
                for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
                    Block block = chunk.getBlock(x, y, z);
                    
                    if (block == null || block.isAir() || !block.isSolid()) {
                        chunk.setSkyLight(x, y, z, currentLight);
                    } else {
                        chunk.setSkyLight(x, y, z, 0);
                        currentLight = 0;
                    }
                }
            }
        }
    }
    
    public void initializeBlocklightForChunk(Chunk chunk) {
        Queue<LightNode> lightQueue = new LinkedList<>();
        
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_HEIGHT; y++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    int lightLevel = (block != null) ? block.getLightLevel() : 0;
                    
                    if (lightLevel > 0) {
                        chunk.setBlockLight(x, y, z, lightLevel);
                        lightQueue.add(new LightNode(x, y, z, lightLevel));
                    }
                }
            }
        }
        
        propagateLight(chunk, lightQueue, false);
    }
    
    private void propagateLight(Chunk chunk, Queue<LightNode> queue, boolean isSkylight) {
        int[][] directions = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
        };
        
        while (!queue.isEmpty()) {
            LightNode node = queue.poll();
            
            int newLight = node.lightLevel - 1;
            if (newLight <= 0) continue;
            
            for (int[] dir : directions) {
                int nx = node.x + dir[0];
                int ny = node.y + dir[1];
                int nz = node.z + dir[2];
                
                if (nx < 0 || nx >= Chunk.CHUNK_SIZE || 
                    ny < 0 || ny >= Chunk.CHUNK_HEIGHT || 
                    nz < 0 || nz >= Chunk.CHUNK_SIZE) {
                    continue;
                }
                
                Block neighbor = chunk.getBlock(nx, ny, nz);
                
                if (neighbor != null && neighbor.isSolid() && neighbor != Blocks.LEAVES) {
                    continue;
                }
                
                int currentLight = isSkylight ? 
                    chunk.getSkyLight(nx, ny, nz) : 
                    chunk.getBlockLight(nx, ny, nz);
                
                if (newLight > currentLight) {
                    if (isSkylight) {
                        chunk.setSkyLight(nx, ny, nz, newLight);
                    } else {
                        chunk.setBlockLight(nx, ny, nz, newLight);
                    }
                    queue.add(new LightNode(nx, ny, nz, newLight));
                }
            }
        }
    }
    
    public void onBlockPlaced(Chunk chunk, int x, int y, int z, Block block) {
        if (block.isSolid()) {
            chunk.setSkyLight(x, y, z, 0);
            propagateDarknessDown(chunk, x, y, z);
        }
        
        int lightLevel = block.getLightLevel();
        if (lightLevel > 0) {
            chunk.setBlockLight(x, y, z, lightLevel);
            Queue<LightNode> queue = new LinkedList<>();
            queue.add(new LightNode(x, y, z, lightLevel));
            propagateLight(chunk, queue, false);
        }
        
        chunk.setNeedsRebuild(true);
    }
    
    public void onBlockRemoved(Chunk chunk, int x, int y, int z) {
        recalculateSkylightColumn(chunk, x, z);
        chunk.setBlockLight(x, y, z, 0);
        recalculateBlocklightAround(chunk, x, y, z);
        chunk.setNeedsRebuild(true);
    }
    
    private void recalculateSkylightColumn(Chunk chunk, int x, int z) {
        int skylightLevel = 15;
        
        for (int y = Chunk.CHUNK_HEIGHT - 1; y >= 0; y--) {
            Block block = chunk.getBlock(x, y, z);
            
            if (block == null || block.isAir() || !block.isSolid()) {
                chunk.setSkyLight(x, y, z, skylightLevel);
            } else {
                chunk.setSkyLight(x, y, z, 0);
                skylightLevel = 0;
            }
        }
    }
    
    private void propagateDarknessDown(Chunk chunk, int x, int startY, int z) {
        for (int y = startY - 1; y >= 0; y--) {
            Block block = chunk.getBlock(x, y, z);
            if (block != null && block.isSolid()) break;
            chunk.setSkyLight(x, y, z, 0);
        }
    }
    
    private void recalculateBlocklightAround(Chunk chunk, int x, int y, int z) {
        Queue<LightNode> queue = new LinkedList<>();
        
        int[][] directions = {
            {1, 0, 0}, {-1, 0, 0},
            {0, 1, 0}, {0, -1, 0},
            {0, 0, 1}, {0, 0, -1}
        };
        
        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            int nz = z + dir[2];
            
            if (nx < 0 || nx >= Chunk.CHUNK_SIZE || 
                ny < 0 || ny >= Chunk.CHUNK_HEIGHT || 
                nz < 0 || nz >= Chunk.CHUNK_SIZE) {
                continue;
            }
            
            int light = chunk.getBlockLight(nx, ny, nz);
            if (light > 0) {
                queue.add(new LightNode(nx, ny, nz, light));
            }
        }
        
        propagateLight(chunk, queue, false);
    }
    
    public static int getCombinedLight(Chunk chunk, int x, int y, int z) {
        if (chunk == null) return 15;
        
        int skyLight = chunk.getSkyLight(x, y, z);
        int blockLight = chunk.getBlockLight(x, y, z);
        
        return Math.max(skyLight, blockLight);
    }
    
    public static float getBrightness(int lightLevel) {
        if (lightLevel < 0) return BRIGHTNESS_TABLE[0];
        if (lightLevel > 15) return BRIGHTNESS_TABLE[15];
        return BRIGHTNESS_TABLE[lightLevel];
    }
    
    public static float getBrightnessWithGamma(int lightLevel, float gamma) {
        float brightness = getBrightness(lightLevel);
        return (float) Math.pow(brightness, 1.0f / gamma);
    }
}