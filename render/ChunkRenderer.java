package com.mineshaft.render;

import com.mineshaft.block.Block;
import com.mineshaft.block.Blocks;
import com.mineshaft.core.Settings;
import com.mineshaft.entity.Camera;
import com.mineshaft.world.Chunk;
import com.mineshaft.world.World;
import com.mineshaft.world.lighting.LightingEngine;
import com.mineshaft.world.lighting.SunLightCalculator;

import java.util.*;
import java.util.concurrent.*;

import static org.lwjgl.opengl.GL11.*;

/**
 * ⚡ FIXED: OpenGL-safe async mesh building
 * Data preparation in worker threads, VBO creation on main thread
 */
public class ChunkRenderer {
    
    private Map<Chunk, ChunkMesh> solidMeshes = new ConcurrentHashMap<>();
    private Map<Chunk, ChunkMesh> waterMeshes = new ConcurrentHashMap<>();
    private Map<Chunk, ChunkMesh> translucentMeshes = new ConcurrentHashMap<>();
    private World world;
    private LightingEngine lightingEngine;
    
    private final ExecutorService meshBuilder;
    private final Set<Chunk> buildingChunks = ConcurrentHashMap.newKeySet();
    private final Queue<ChunkBuildTask> buildQueue = new ConcurrentLinkedQueue<>();
    
    // ⚡ NEW: Queue for VBO creation on main thread
    private final Queue<MeshDataResult> pendingVBOCreation = new ConcurrentLinkedQueue<>();
    
    private static final int MAX_BUILDS_PER_FRAME = 3;
    private static final int MAX_VBO_UPLOADS_PER_FRAME = 5;
    private static final int WORKER_THREADS = 2;
    
    /**
     * Task for queuing chunk rebuilds
     */
    private static class ChunkBuildTask {
        Chunk chunk;
        double distanceSquared;
        
        ChunkBuildTask(Chunk chunk, double distSq) {
            this.chunk = chunk;
            this.distanceSquared = distSq;
        }
    }
    
    /**
     * ⚡ NEW: Mesh data prepared in worker thread, ready for VBO creation
     */
    private static class MeshDataResult {
        Chunk chunk;
        List<Float> solidVertices;
        List<Float> waterVertices;
        List<Float> translucentVertices;
        
        MeshDataResult(Chunk chunk, List<Float> solid, List<Float> water, List<Float> translucent) {
            this.chunk = chunk;
            this.solidVertices = solid;
            this.waterVertices = water;
            this.translucentVertices = translucent;
        }
    }
    
    public ChunkRenderer() {
        this.meshBuilder = Executors.newFixedThreadPool(WORKER_THREADS, r -> {
            Thread t = new Thread(r, "MeshBuilder");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }
    
    public void setWorld(World world) {
        this.world = world;
    }
    
    public void setLightingEngine(LightingEngine lightingEngine) {
        this.lightingEngine = lightingEngine;
    }
    
    public void renderChunk(Chunk chunk, Camera camera) {
        if (chunk.needsRebuild() && !buildingChunks.contains(chunk)) {
            queueChunkRebuild(chunk, camera);
        }
    }
    
    private void queueChunkRebuild(Chunk chunk, Camera camera) {
        float chunkCenterX = chunk.getChunkX() * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2.0f;
        float chunkCenterZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2.0f;
        
        float dx = chunkCenterX - camera.getX();
        float dz = chunkCenterZ - camera.getZ();
        double distSq = dx * dx + dz * dz;
        
        buildQueue.offer(new ChunkBuildTask(chunk, distSq));
    }
    
    /**
     * ⚡ FIXED: Two-stage update process
     */
    public void update() {
        // Stage 1: Start building mesh data (in worker threads)
        startMeshDataBuilds();
        
        // Stage 2: Upload completed mesh data to GPU (on main thread)
        uploadPendingMeshes();
    }
    
    /**
     * Stage 1: Start async mesh data preparation
     */
    private void startMeshDataBuilds() {
        int buildsStarted = 0;
        
        // Sort by distance (closest first)
        List<ChunkBuildTask> sortedTasks = new ArrayList<>();
        ChunkBuildTask task;
        while ((task = buildQueue.poll()) != null) {
            sortedTasks.add(task);
        }
        sortedTasks.sort(Comparator.comparingDouble(t -> t.distanceSquared));
        
        for (ChunkBuildTask t : sortedTasks) {
            if (buildsStarted >= MAX_BUILDS_PER_FRAME) {
                buildQueue.offer(t); // Re-queue for next frame
                continue;
            }
            
            if (t.chunk.needsRebuild() && !buildingChunks.contains(t.chunk)) {
                buildingChunks.add(t.chunk);
                
                // Build mesh data in worker thread (NO OpenGL calls)
                meshBuilder.submit(() -> buildMeshDataAsync(t.chunk));
                buildsStarted++;
            }
        }
    }
    
    /**
     * ⚡ SAFE: Build mesh data (NO OpenGL calls, runs in worker thread)
     */
    private void buildMeshDataAsync(Chunk chunk) {
        try {
            List<Float> solidVertices = new ArrayList<>();
            List<Float> waterVertices = new ArrayList<>();
            List<Float> translucentVertices = new ArrayList<>();
            
            int offsetX = chunk.getChunkX() * Chunk.CHUNK_SIZE;
            int offsetZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE;
            
            // Build vertex data (CPU work only, no OpenGL)
            for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
                for (int y = 0; y < Chunk.CHUNK_HEIGHT; y++) {
                    for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                        Block block = chunk.getBlock(x, y, z);
                        
                        if (block != null && !block.isAir()) {
                            float worldX = offsetX + x;
                            float worldY = y;
                            float worldZ = offsetZ + z;
                            
                            if (block == Blocks.WATER) {
                                addWaterBlockToList(chunk, x, y, z, worldX, worldY, worldZ, waterVertices, block);
                            } else if (block == Blocks.LEAVES) {
                                addBlockFacesToList(chunk, x, y, z, worldX, worldY, worldZ, translucentVertices, block, false, true);
                            } else {
                                addBlockFacesToList(chunk, x, y, z, worldX, worldY, worldZ, solidVertices, block, false, false);
                            }
                        }
                    }
                }
            }
            
            // Queue for VBO creation on main thread
            pendingVBOCreation.offer(new MeshDataResult(chunk, solidVertices, waterVertices, translucentVertices));
            
        } catch (Exception e) {
            System.err.println("Error building mesh data for chunk: " + e.getMessage());
            e.printStackTrace();
        } finally {
            buildingChunks.remove(chunk);
        }
    }
    
    /**
     * Stage 2: Upload mesh data to GPU (MAIN THREAD ONLY)
     */
    private void uploadPendingMeshes() {
        int uploaded = 0;
        
        while (uploaded < MAX_VBO_UPLOADS_PER_FRAME && !pendingVBOCreation.isEmpty()) {
            MeshDataResult result = pendingVBOCreation.poll();
            
            if (result != null) {
                // Create meshes and upload to GPU (OpenGL calls on main thread)
                ChunkMesh solidMesh = new ChunkMesh();
                ChunkMesh waterMesh = new ChunkMesh();
                ChunkMesh translucentMesh = new ChunkMesh();
                
                // Add vertices from prepared data
                for (int i = 0; i < result.solidVertices.size(); i += 12) {
                    solidMesh.addVertex(
                        result.solidVertices.get(i), result.solidVertices.get(i+1), result.solidVertices.get(i+2),
                        result.solidVertices.get(i+3), result.solidVertices.get(i+4), result.solidVertices.get(i+5), result.solidVertices.get(i+6),
                        result.solidVertices.get(i+7), result.solidVertices.get(i+8), result.solidVertices.get(i+9),
                        result.solidVertices.get(i+10), result.solidVertices.get(i+11)
                    );
                }
                
                for (int i = 0; i < result.waterVertices.size(); i += 12) {
                    waterMesh.addVertex(
                        result.waterVertices.get(i), result.waterVertices.get(i+1), result.waterVertices.get(i+2),
                        result.waterVertices.get(i+3), result.waterVertices.get(i+4), result.waterVertices.get(i+5), result.waterVertices.get(i+6),
                        result.waterVertices.get(i+7), result.waterVertices.get(i+8), result.waterVertices.get(i+9),
                        result.waterVertices.get(i+10), result.waterVertices.get(i+11)
                    );
                }
                
                for (int i = 0; i < result.translucentVertices.size(); i += 12) {
                    translucentMesh.addVertex(
                        result.translucentVertices.get(i), result.translucentVertices.get(i+1), result.translucentVertices.get(i+2),
                        result.translucentVertices.get(i+3), result.translucentVertices.get(i+4), result.translucentVertices.get(i+5), result.translucentVertices.get(i+6),
                        result.translucentVertices.get(i+7), result.translucentVertices.get(i+8), result.translucentVertices.get(i+9),
                        result.translucentVertices.get(i+10), result.translucentVertices.get(i+11)
                    );
                }
                
                // Build VBOs (OpenGL calls on main thread - SAFE!)
                solidMesh.build();
                waterMesh.build();
                translucentMesh.build();
                
                // Swap old meshes
                swapMeshes(result.chunk, solidMesh, waterMesh, translucentMesh);
                
                result.chunk.setNeedsRebuild(false);
                uploaded++;
            }
        }
    }
    
    private void swapMeshes(Chunk chunk, ChunkMesh newSolid, ChunkMesh newWater, ChunkMesh newTranslucent) {
        ChunkMesh oldSolid = solidMeshes.put(chunk, newSolid);
        if (oldSolid != null) oldSolid.destroy();
        
        ChunkMesh oldWater = waterMeshes.put(chunk, newWater);
        if (oldWater != null) oldWater.destroy();
        
        ChunkMesh oldTranslucent = translucentMeshes.put(chunk, newTranslucent);
        if (oldTranslucent != null) oldTranslucent.destroy();
    }
    
    public void renderSolidPass(Collection<Chunk> chunks) {
        BlockTextures.bind();
        
        for (Chunk chunk : chunks) {
            ChunkMesh solidMesh = solidMeshes.get(chunk);
            if (solidMesh != null && solidMesh.getVertexCount() > 0) {
                solidMesh.render();
            }
        }
    }
    
    public void renderTranslucentPass(Collection<Chunk> chunks, Camera camera) {
        List<Chunk> sortedChunks = new ArrayList<>(chunks);
        sortedChunks.sort((c1, c2) -> {
            float dist1 = getChunkDistanceSq(c1, camera);
            float dist2 = getChunkDistanceSq(c2, camera);
            return Float.compare(dist2, dist1);
        });
        
        BlockTextures.bind();
        
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glAlphaFunc(GL_GREATER, 0.1f);
        glEnable(GL_ALPHA_TEST);
        
        for (Chunk chunk : sortedChunks) {
            ChunkMesh translucentMesh = translucentMeshes.get(chunk);
            if (translucentMesh != null && translucentMesh.getVertexCount() > 0) {
                translucentMesh.render();
            }
        }
        
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_BLEND);
    }
    
    public void renderWaterPass(Collection<Chunk> chunks, Camera camera) {
        List<Chunk> sortedChunks = new ArrayList<>(chunks);
        sortedChunks.sort((c1, c2) -> {
            float dist1 = getChunkDistanceSq(c1, camera);
            float dist2 = getChunkDistanceSq(c2, camera);
            return Float.compare(dist2, dist1);
        });
        
        BlockTextures.bind();
        
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(false);
        
        for (Chunk chunk : sortedChunks) {
            ChunkMesh waterMesh = waterMeshes.get(chunk);
            if (waterMesh != null && waterMesh.getVertexCount() > 0) {
                waterMesh.render();
            }
        }
        
        glDepthMask(true);
        glDisable(GL_BLEND);
        glEnable(GL_CULL_FACE);
    }
    
    private float getChunkDistanceSq(Chunk chunk, Camera camera) {
        float chunkCenterX = chunk.getChunkX() * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2.0f;
        float chunkCenterZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE + Chunk.CHUNK_SIZE / 2.0f;
        
        float dx = chunkCenterX - camera.getX();
        float dz = chunkCenterZ - camera.getZ();
        
        return dx * dx + dz * dz;
    }
    
    // ========== MESH DATA BUILDING (NO OpenGL, thread-safe) ==========
    
    private void addWaterBlockToList(Chunk chunk, int x, int y, int z, 
                                     float worldX, float worldY, float worldZ, 
                                     List<Float> vertices, Block block) {
        float[] baseColor = new float[]{0.5f, 0.7f, 1.0f};
        float alpha = 0.7f;
        float topY = worldY + 1.0f;
        
        Block top = getBlockSafe(chunk, x, y + 1, z);
        Block bottom = getBlockSafe(chunk, x, y - 1, z);
        Block north = getBlockSafe(chunk, x, y, z - 1);
        Block south = getBlockSafe(chunk, x, y, z + 1);
        Block east = getBlockSafe(chunk, x + 1, y, z);
        Block west = getBlockSafe(chunk, x - 1, y, z);
        
        SunLightCalculator sunLight = (lightingEngine != null) ? lightingEngine.getSunLight() : null;
        float[] uv = BlockTextures.getUV(block, "top");
        
        if (top != Blocks.WATER) {
            float brightness = getSunBrightness(sunLight, 0, 1, 0);
            addWaterFaceToList(vertices, 
                worldX, topY, worldZ, worldX, topY, worldZ + 1,
                worldX + 1, topY, worldZ + 1, worldX + 1, topY, worldZ,
                baseColor, alpha, brightness, 0, 1, 0, uv);
        }
        
        if (bottom != null && bottom.isAir()) {
            float brightness = getSunBrightness(sunLight, 0, -1, 0);
            addWaterFaceToList(vertices,
                worldX, worldY, worldZ, worldX + 1, worldY, worldZ,
                worldX + 1, worldY, worldZ + 1, worldX, worldY, worldZ + 1,
                baseColor, alpha, brightness, 0, -1, 0, uv);
        }
        
        if (north != Blocks.WATER) {
            float brightness = getSunBrightness(sunLight, 0, 0, -1);
            addWaterFaceToList(vertices,
                worldX, worldY, worldZ, worldX, worldY + 1, worldZ,
                worldX + 1, worldY + 1, worldZ, worldX + 1, worldY, worldZ,
                baseColor, alpha, brightness, 0, 0, -1, uv);
        }
        
        if (south != Blocks.WATER) {
            float brightness = getSunBrightness(sunLight, 0, 0, 1);
            addWaterFaceToList(vertices,
                worldX, worldY, worldZ + 1, worldX + 1, worldY, worldZ + 1,
                worldX + 1, worldY + 1, worldZ + 1, worldX, worldY + 1, worldZ + 1,
                baseColor, alpha, brightness, 0, 0, 1, uv);
        }
        
        if (east != Blocks.WATER) {
            float brightness = getSunBrightness(sunLight, 1, 0, 0);
            addWaterFaceToList(vertices,
                worldX + 1, worldY, worldZ, worldX + 1, worldY + 1, worldZ,
                worldX + 1, worldY + 1, worldZ + 1, worldX + 1, worldY, worldZ + 1,
                baseColor, alpha, brightness, 1, 0, 0, uv);
        }
        
        if (west != Blocks.WATER) {
            float brightness = getSunBrightness(sunLight, -1, 0, 0);
            addWaterFaceToList(vertices,
                worldX, worldY, worldZ, worldX, worldY, worldZ + 1,
                worldX, worldY + 1, worldZ + 1, worldX, worldY + 1, worldZ,
                baseColor, alpha, brightness, -1, 0, 0, uv);
        }
    }
    
    private void addWaterFaceToList(List<Float> vertices,
                                    float x1, float y1, float z1, float x2, float y2, float z2,
                                    float x3, float y3, float z3, float x4, float y4, float z4,
                                    float[] color, float alpha, float brightness,
                                    float nx, float ny, float nz, float[] uv) {
        float r = color[0] * brightness;
        float g = color[1] * brightness;
        float b = color[2] * brightness;
        
        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];
        
        addVertexToList(vertices, x1, y1, z1, r, g, b, alpha, nx, ny, nz, u1, v1);
        addVertexToList(vertices, x2, y2, z2, r, g, b, alpha, nx, ny, nz, u1, v2);
        addVertexToList(vertices, x3, y3, z3, r, g, b, alpha, nx, ny, nz, u2, v2);
        addVertexToList(vertices, x4, y4, z4, r, g, b, alpha, nx, ny, nz, u2, v1);
    }
    
    private void addBlockFacesToList(Chunk chunk, int x, int y, int z,
                                     float worldX, float worldY, float worldZ,
                                     List<Float> vertices, Block block, boolean isWater, boolean isTranslucent) {
        float[] color = new float[]{1.0f, 1.0f, 1.0f};
        float alpha = isTranslucent ? 0.9f : 1.0f;
        
        if (shouldRenderFace(chunk, x, y + 1, z, block)) {
            addTopFaceToList(chunk, vertices, x, y, z, worldX, worldY, worldZ, color, alpha, block);
        }
        
        if (shouldRenderFace(chunk, x, y - 1, z, block)) {
            addBottomFaceToList(chunk, vertices, x, y, z, worldX, worldY, worldZ, color, alpha, block);
        }
        
        if (shouldRenderFace(chunk, x, y, z - 1, block)) {
            addNorthFaceToList(chunk, vertices, x, y, z, worldX, worldY, worldZ, color, alpha, block);
        }
        
        if (shouldRenderFace(chunk, x, y, z + 1, block)) {
            addSouthFaceToList(chunk, vertices, x, y, z, worldX, worldY, worldZ, color, alpha, block);
        }
        
        if (shouldRenderFace(chunk, x + 1, y, z, block)) {
            addEastFaceToList(chunk, vertices, x, y, z, worldX, worldY, worldZ, color, alpha, block);
        }
        
        if (shouldRenderFace(chunk, x - 1, y, z, block)) {
            addWestFaceToList(chunk, vertices, x, y, z, worldX, worldY, worldZ, color, alpha, block);
        }
    }
    
    private void addTopFaceToList(Chunk chunk, List<Float> vertices, int x, int y, int z,
                                  float worldX, float worldY, float worldZ,
                                  float[] color, float alpha, Block block) {
        SunLightCalculator sunLight = (lightingEngine != null) ? lightingEngine.getSunLight() : null;
        float sunBrightness = getSunBrightness(sunLight, 0, 1, 0);
        
        float light1 = getLightBrightness(chunk, x, y + 1, z) * sunBrightness;
        float light2 = getLightBrightness(chunk, x, y + 1, z + 1) * sunBrightness;
        float light3 = getLightBrightness(chunk, x + 1, y + 1, z + 1) * sunBrightness;
        float light4 = getLightBrightness(chunk, x + 1, y + 1, z) * sunBrightness;
        
        float[] uv = BlockTextures.getUV(block, "top");
        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];
        
        addVertexToList(vertices, worldX, worldY + 1, worldZ, 
            color[0] * light1, color[1] * light1, color[2] * light1, alpha, 0, 1, 0, u1, v1);
        addVertexToList(vertices, worldX, worldY + 1, worldZ + 1, 
            color[0] * light2, color[1] * light2, color[2] * light2, alpha, 0, 1, 0, u1, v2);
        addVertexToList(vertices, worldX + 1, worldY + 1, worldZ + 1, 
            color[0] * light3, color[1] * light3, color[2] * light3, alpha, 0, 1, 0, u2, v2);
        addVertexToList(vertices, worldX + 1, worldY + 1, worldZ, 
            color[0] * light4, color[1] * light4, color[2] * light4, alpha, 0, 1, 0, u2, v1);
    }
    
    private void addBottomFaceToList(Chunk chunk, List<Float> vertices, int x, int y, int z,
                                     float worldX, float worldY, float worldZ,
                                     float[] color, float alpha, Block block) {
        SunLightCalculator sunLight = (lightingEngine != null) ? lightingEngine.getSunLight() : null;
        float sunBrightness = getSunBrightness(sunLight, 0, -1, 0);
        
        float light1 = getLightBrightness(chunk, x, y - 1, z) * sunBrightness;
        float light2 = getLightBrightness(chunk, x + 1, y - 1, z) * sunBrightness;
        float light3 = getLightBrightness(chunk, x + 1, y - 1, z + 1) * sunBrightness;
        float light4 = getLightBrightness(chunk, x, y - 1, z + 1) * sunBrightness;
        
        float[] uv = BlockTextures.getUV(block, "bottom");
        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];
        
        addVertexToList(vertices, worldX, worldY, worldZ, 
            color[0] * light1, color[1] * light1, color[2] * light1, alpha, 0, -1, 0, u1, v1);
        addVertexToList(vertices, worldX + 1, worldY, worldZ, 
            color[0] * light2, color[1] * light2, color[2] * light2, alpha, 0, -1, 0, u2, v1);
        addVertexToList(vertices, worldX + 1, worldY, worldZ + 1, 
            color[0] * light3, color[1] * light3, color[2] * light3, alpha, 0, -1, 0, u2, v2);
        addVertexToList(vertices, worldX, worldY, worldZ + 1, 
            color[0] * light4, color[1] * light4, color[2] * light4, alpha, 0, -1, 0, u1, v2);
    }
    
    private void addNorthFaceToList(Chunk chunk, List<Float> vertices, int x, int y, int z,
                                    float worldX, float worldY, float worldZ,
                                    float[] color, float alpha, Block block) {
        SunLightCalculator sunLight = (lightingEngine != null) ? lightingEngine.getSunLight() : null;
        float sunBrightness = getSunBrightness(sunLight, 0, 0, -1);
        
        float light1 = getLightBrightness(chunk, x, y, z - 1) * sunBrightness;
        float light2 = getLightBrightness(chunk, x, y + 1, z - 1) * sunBrightness;
        float light3 = getLightBrightness(chunk, x + 1, y + 1, z - 1) * sunBrightness;
        float light4 = getLightBrightness(chunk, x + 1, y, z - 1) * sunBrightness;
        
        float[] uv = BlockTextures.getUV(block, "north");
        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];
        
        addVertexToList(vertices, worldX, worldY, worldZ, 
            color[0] * light1, color[1] * light1, color[2] * light1, alpha, 0, 0, -1, u1, v2);
        addVertexToList(vertices, worldX, worldY + 1, worldZ, 
            color[0] * light2, color[1] * light2, color[2] * light2, alpha, 0, 0, -1, u1, v1);
        addVertexToList(vertices, worldX + 1, worldY + 1, worldZ, 
            color[0] * light3, color[1] * light3, color[2] * light3, alpha, 0, 0, -1, u2, v1);
        addVertexToList(vertices, worldX + 1, worldY, worldZ, 
            color[0] * light4, color[1] * light4, color[2] * light4, alpha, 0, 0, -1, u2, v2);
    }
    
    private void addSouthFaceToList(Chunk chunk, List<Float> vertices, int x, int y, int z,
                                    float worldX, float worldY, float worldZ,
                                    float[] color, float alpha, Block block) {
        SunLightCalculator sunLight = (lightingEngine != null) ? lightingEngine.getSunLight() : null;
        float sunBrightness = getSunBrightness(sunLight, 0, 0, 1);
        
        float light1 = getLightBrightness(chunk, x, y, z + 1) * sunBrightness;
        float light2 = getLightBrightness(chunk, x + 1, y, z + 1) * sunBrightness;
        float light3 = getLightBrightness(chunk, x + 1, y + 1, z + 1) * sunBrightness;
        float light4 = getLightBrightness(chunk, x, y + 1, z + 1) * sunBrightness;
        
        float[] uv = BlockTextures.getUV(block, "south");
        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];
        
        addVertexToList(vertices, worldX, worldY, worldZ + 1, 
            color[0] * light1, color[1] * light1, color[2] * light1, alpha, 0, 0, 1, u1, v2);
        addVertexToList(vertices, worldX + 1, worldY, worldZ + 1, 
            color[0] * light2, color[1] * light2, color[2] * light2, alpha, 0, 0, 1, u2, v2);
        addVertexToList(vertices, worldX + 1, worldY + 1, worldZ + 1, 
            color[0] * light3, color[1] * light3, color[2] * light3, alpha, 0, 0, 1, u2, v1);
        addVertexToList(vertices, worldX, worldY + 1, worldZ + 1, 
            color[0] * light4, color[1] * light4, color[2] * light4, alpha, 0, 0, 1, u1, v1);
    }
    
    private void addEastFaceToList(Chunk chunk, List<Float> vertices, int x, int y, int z,
                                   float worldX, float worldY, float worldZ,
                                   float[] color, float alpha, Block block) {
        SunLightCalculator sunLight = (lightingEngine != null) ? lightingEngine.getSunLight() : null;
        float sunBrightness = getSunBrightness(sunLight, 1, 0, 0);
        
        float light1 = getLightBrightness(chunk, x + 1, y, z) * sunBrightness;
        float light2 = getLightBrightness(chunk, x + 1, y + 1, z) * sunBrightness;
        float light3 = getLightBrightness(chunk, x + 1, y + 1, z + 1) * sunBrightness;
        float light4 = getLightBrightness(chunk, x + 1, y, z + 1) * sunBrightness;
        
        float[] uv = BlockTextures.getUV(block, "east");
        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];
        
        addVertexToList(vertices, worldX + 1, worldY, worldZ, 
            color[0] * light1, color[1] * light1, color[2] * light1, alpha, 1, 0, 0, u1, v2);
        addVertexToList(vertices, worldX + 1, worldY + 1, worldZ, 
            color[0] * light2, color[1] * light2, color[2] * light2, alpha, 1, 0, 0, u1, v1);
        addVertexToList(vertices, worldX + 1, worldY + 1, worldZ + 1, 
            color[0] * light3, color[1] * light3, color[2] * light3, alpha, 1, 0, 0, u2, v1);
        addVertexToList(vertices, worldX + 1, worldY, worldZ + 1, 
            color[0] * light4, color[1] * light4, color[2] * light4, alpha, 1, 0, 0, u2, v2);
    }
    
    private void addWestFaceToList(Chunk chunk, List<Float> vertices, int x, int y, int z,
                                   float worldX, float worldY, float worldZ,
                                   float[] color, float alpha, Block block) {
        SunLightCalculator sunLight = (lightingEngine != null) ? lightingEngine.getSunLight() : null;
        float sunBrightness = getSunBrightness(sunLight, -1, 0, 0);
        
        float light1 = getLightBrightness(chunk, x - 1, y, z) * sunBrightness;
        float light2 = getLightBrightness(chunk, x - 1, y, z + 1) * sunBrightness;
        float light3 = getLightBrightness(chunk, x - 1, y + 1, z + 1) * sunBrightness;
        float light4 = getLightBrightness(chunk, x - 1, y + 1, z) * sunBrightness;
        
        float[] uv = BlockTextures.getUV(block, "west");
        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];
        
        addVertexToList(vertices, worldX, worldY, worldZ, 
            color[0] * light1, color[1] * light1, color[2] * light1, alpha, -1, 0, 0, u1, v2);
        addVertexToList(vertices, worldX, worldY, worldZ + 1, 
            color[0] * light2, color[1] * light2, color[2] * light2, alpha, -1, 0, 0, u2, v2);
        addVertexToList(vertices, worldX, worldY + 1, worldZ + 1, 
            color[0] * light3, color[1] * light3, color[2] * light3, alpha, -1, 0, 0, u2, v1);
        addVertexToList(vertices, worldX, worldY + 1, worldZ, 
            color[0] * light4, color[1] * light4, color[2] * light4, alpha, -1, 0, 0, u1, v1);
    }
    
    /**
     * Add single vertex to list (12 floats: pos, color, normal, uv)
     */
    private void addVertexToList(List<Float> vertices, 
                                 float x, float y, float z,
                                 float r, float g, float b, float a,
                                 float nx, float ny, float nz,
                                 float u, float v) {
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        vertices.add(r);
        vertices.add(g);
        vertices.add(b);
        vertices.add(a);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);
        vertices.add(u);
        vertices.add(v);
    }
    
    private float getSunBrightness(SunLightCalculator sunLight, float nx, float ny, float nz) {
        float brightness;
        
        if (sunLight == null) {
            if (ny > 0) brightness = 1.0f;
            else if (ny < 0) brightness = 0.7f;
            else if (nz != 0) brightness = 0.85f;
            else brightness = 0.75f;
        } else {
            brightness = sunLight.calculateFaceBrightness(nx, ny, nz);
        }
        
        brightness = (float) Math.pow(brightness, 1.0f / Settings.GAMMA);
        brightness += Settings.BRIGHTNESS_BOOST;
        
        return Math.max(Settings.MIN_BRIGHTNESS, Math.min(1.0f, brightness));
    }
    
    private Block getBlockSafe(Chunk chunk, int x, int y, int z) {
        if (y < 0 || y >= Chunk.CHUNK_HEIGHT) {
            return Blocks.AIR;
        }
        
        if (x >= 0 && x < Chunk.CHUNK_SIZE && z >= 0 && z < Chunk.CHUNK_SIZE) {
            Block block = chunk.getBlock(x, y, z);
            return (block != null) ? block : Blocks.AIR;
        }
        
        if (world != null) {
            int worldX = chunk.getChunkX() * Chunk.CHUNK_SIZE + x;
            int worldZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE + z;
            Block block = world.getBlock(worldX, y, worldZ);
            return (block != null) ? block : Blocks.AIR;
        }
        
        return Blocks.AIR;
    }
    
    private int getLightSafe(Chunk chunk, int x, int y, int z) {
        if (y < 0 || y >= Chunk.CHUNK_HEIGHT) {
            return 15;
        }
        
        if (x >= 0 && x < Chunk.CHUNK_SIZE && z >= 0 && z < Chunk.CHUNK_SIZE) {
            return LightingEngine.getCombinedLight(chunk, x, y, z);
        }
        
        if (world != null) {
            int worldX = chunk.getChunkX() * Chunk.CHUNK_SIZE + x;
            int worldZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE + z;
            
            int chunkX = Math.floorDiv(worldX, Chunk.CHUNK_SIZE);
            int chunkZ = Math.floorDiv(worldZ, Chunk.CHUNK_SIZE);
            
            Chunk neighborChunk = world.getChunk(chunkX, chunkZ);
            if (neighborChunk != null) {
                int localX = Math.floorMod(worldX, Chunk.CHUNK_SIZE);
                int localZ = Math.floorMod(worldZ, Chunk.CHUNK_SIZE);
                return LightingEngine.getCombinedLight(neighborChunk, localX, y, localZ);
            }
        }
        
        return 15;
    }
    
    private float getLightBrightness(Chunk chunk, int x, int y, int z) {
        int light = getLightSafe(chunk, x, y, z);
        float brightness = LightingEngine.getBrightness(light);
        brightness = (float) Math.pow(brightness, 1.0f / Settings.GAMMA);
        return brightness;
    }
    
    private boolean shouldRenderFace(Chunk chunk, int x, int y, int z, Block currentBlock) {
        if (y < 0 || y >= Chunk.CHUNK_HEIGHT) {
            return y >= Chunk.CHUNK_HEIGHT;
        }
        
        Block neighbor = getBlockSafe(chunk, x, y, z);
        
        if (neighbor.isAir()) return true;
        if (neighbor == currentBlock) return false;
        if (neighbor == Blocks.WATER || neighbor == Blocks.LEAVES) return true;
        
        return !neighbor.isSolid();
    }
    
    public void removeChunk(Chunk chunk) {
        buildQueue.removeIf(task -> task.chunk == chunk);
        pendingVBOCreation.removeIf(result -> result.chunk == chunk);
        buildingChunks.remove(chunk);
        
        ChunkMesh solidMesh = solidMeshes.remove(chunk);
        if (solidMesh != null) solidMesh.destroy();
        
        ChunkMesh waterMesh = waterMeshes.remove(chunk);
        if (waterMesh != null) waterMesh.destroy();
        
        ChunkMesh translucentMesh = translucentMeshes.remove(chunk);
        if (translucentMesh != null) translucentMesh.destroy();
    }
    
    public void cleanup() {
        meshBuilder.shutdown();
        try {
            if (!meshBuilder.awaitTermination(3, TimeUnit.SECONDS)) {
                meshBuilder.shutdownNow();
            }
        } catch (InterruptedException e) {
            meshBuilder.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        for (ChunkMesh mesh : solidMeshes.values()) mesh.destroy();
        solidMeshes.clear();
        
        for (ChunkMesh mesh : waterMeshes.values()) mesh.destroy();
        waterMeshes.clear();
        
        for (ChunkMesh mesh : translucentMeshes.values()) mesh.destroy();
        translucentMeshes.clear();
    }
    
    public int getPendingBuilds() {
        return buildQueue.size() + buildingChunks.size() + pendingVBOCreation.size();
    }
}