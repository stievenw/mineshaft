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

import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ Chunk renderer with TEXTURED realistic lighting - Fixed warnings
 */
public class ChunkRenderer {
    
    private Map<Chunk, ChunkMesh> solidMeshes = new HashMap<>();
    private Map<Chunk, ChunkMesh> waterMeshes = new HashMap<>();
    private Map<Chunk, ChunkMesh> translucentMeshes = new HashMap<>();
    private World world;
    private LightingEngine lightingEngine;
    
    // ✅ REMOVED: Unused field smoothLighting
    
    public void setWorld(World world) {
        this.world = world;
    }
    
    public void setLightingEngine(LightingEngine lightingEngine) {
        this.lightingEngine = lightingEngine;
    }
    
    // ✅ REMOVED: Unused setSmoothLighting method
    
    public void renderChunk(Chunk chunk, Camera camera) {
        if (!solidMeshes.containsKey(chunk) || chunk.needsRebuild()) {
            buildChunkMesh(chunk);
            chunk.setNeedsRebuild(false);
        }
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
    
    private void buildChunkMesh(Chunk chunk) {
        ChunkMesh solidMesh = solidMeshes.get(chunk);
        if (solidMesh == null) {
            solidMesh = new ChunkMesh();
            solidMeshes.put(chunk, solidMesh);
        } else {
            solidMesh.destroy();
        }
        
        ChunkMesh waterMesh = waterMeshes.get(chunk);
        if (waterMesh == null) {
            waterMesh = new ChunkMesh();
            waterMeshes.put(chunk, waterMesh);
        } else {
            waterMesh.destroy();
        }
        
        ChunkMesh translucentMesh = translucentMeshes.get(chunk);
        if (translucentMesh == null) {
            translucentMesh = new ChunkMesh();
            translucentMeshes.put(chunk, translucentMesh);
        } else {
            translucentMesh.destroy();
        }
        
        int offsetX = chunk.getChunkX() * Chunk.CHUNK_SIZE;
        int offsetZ = chunk.getChunkZ() * Chunk.CHUNK_SIZE;
        
        for (int x = 0; x < Chunk.CHUNK_SIZE; x++) {
            for (int y = 0; y < Chunk.CHUNK_HEIGHT; y++) {
                for (int z = 0; z < Chunk.CHUNK_SIZE; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    
                    if (block != null && !block.isAir()) {
                        float worldX = offsetX + x;
                        float worldY = y;
                        float worldZ = offsetZ + z;
                        
                        if (block == Blocks.WATER) {
                            addWaterBlockFixed(chunk, x, y, z, worldX, worldY, worldZ, waterMesh, block);
                        } else if (block == Blocks.LEAVES) {
                            addBlockFaces(chunk, x, y, z, worldX, worldY, worldZ, translucentMesh, block, false, true);
                        } else {
                            addBlockFaces(chunk, x, y, z, worldX, worldY, worldZ, solidMesh, block, false, false);
                        }
                    }
                }
            }
        }
        
        solidMesh.build();
        waterMesh.build();
        translucentMesh.build();
    }
    
    private void addWaterBlockFixed(Chunk chunk, int x, int y, int z, 
                                    float worldX, float worldY, float worldZ, ChunkMesh mesh, Block block) {
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
            addWaterFace(mesh, 
                worldX, topY, worldZ,
                worldX, topY, worldZ + 1,
                worldX + 1, topY, worldZ + 1,
                worldX + 1, topY, worldZ,
                baseColor, alpha, brightness,
                0, 1, 0, uv);
        }
        
        if (bottom != null && bottom.isAir()) {
            float brightness = getSunBrightness(sunLight, 0, -1, 0);
            addWaterFace(mesh,
                worldX, worldY, worldZ,
                worldX + 1, worldY, worldZ,
                worldX + 1, worldY, worldZ + 1,
                worldX, worldY, worldZ + 1,
                baseColor, alpha, brightness,
                0, -1, 0, uv);
        }
        
        if (north != Blocks.WATER) {
            float brightness = getSunBrightness(sunLight, 0, 0, -1);
            addWaterFace(mesh,
                worldX, worldY, worldZ,
                worldX, worldY + 1, worldZ,
                worldX + 1, worldY + 1, worldZ,
                worldX + 1, worldY, worldZ,
                baseColor, alpha, brightness,
                0, 0, -1, uv);
        }
        
        if (south != Blocks.WATER) {
            float brightness = getSunBrightness(sunLight, 0, 0, 1);
            addWaterFace(mesh,
                worldX, worldY, worldZ + 1,
                worldX + 1, worldY, worldZ + 1,
                worldX + 1, worldY + 1, worldZ + 1,
                worldX, worldY + 1, worldZ + 1,
                baseColor, alpha, brightness,
                0, 0, 1, uv);
        }
        
        if (east != Blocks.WATER) {
            float brightness = getSunBrightness(sunLight, 1, 0, 0);
            addWaterFace(mesh,
                worldX + 1, worldY, worldZ,
                worldX + 1, worldY + 1, worldZ,
                worldX + 1, worldY + 1, worldZ + 1,
                worldX + 1, worldY, worldZ + 1,
                baseColor, alpha, brightness,
                1, 0, 0, uv);
        }
        
        if (west != Blocks.WATER) {
            float brightness = getSunBrightness(sunLight, -1, 0, 0);
            addWaterFace(mesh,
                worldX, worldY, worldZ,
                worldX, worldY, worldZ + 1,
                worldX, worldY + 1, worldZ + 1,
                worldX, worldY + 1, worldZ,
                baseColor, alpha, brightness,
                -1, 0, 0, uv);
        }
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
    
    private void addWaterFace(ChunkMesh mesh,
                             float x1, float y1, float z1,
                             float x2, float y2, float z2,
                             float x3, float y3, float z3,
                             float x4, float y4, float z4,
                             float[] color, float alpha, float brightness,
                             float nx, float ny, float nz, float[] uv) {
        float r = color[0] * brightness;
        float g = color[1] * brightness;
        float b = color[2] * brightness;
        
        float u1 = uv[0], v1 = uv[1], u2 = uv[2], v2 = uv[3];
        
        mesh.addVertex(x1, y1, z1, r, g, b, alpha, nx, ny, nz, u1, v1);
        mesh.addVertex(x2, y2, z2, r, g, b, alpha, nx, ny, nz, u1, v2);
        mesh.addVertex(x3, y3, z3, r, g, b, alpha, nx, ny, nz, u2, v2);
        mesh.addVertex(x4, y4, z4, r, g, b, alpha, nx, ny, nz, u2, v1);
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
    
    private void addBlockFaces(Chunk chunk, int x, int y, int z,
                            float worldX, float worldY, float worldZ,
                            ChunkMesh mesh, Block block, boolean isWater, boolean isTranslucent) {
        
        float[] color = new float[]{1.0f, 1.0f, 1.0f};
        float alpha = isTranslucent ? 0.9f : 1.0f;
        
        if (shouldRenderFace(chunk, x, y + 1, z, block)) {
            addTopFaceWithLighting(chunk, mesh, x, y, z, worldX, worldY, worldZ, color, alpha, block);
        }
        
        if (shouldRenderFace(chunk, x, y - 1, z, block)) {
            addBottomFaceWithLighting(chunk, mesh, x, y, z, worldX, worldY, worldZ, color, alpha, block);
        }
        
        if (shouldRenderFace(chunk, x, y, z - 1, block)) {
            addNorthFaceWithLighting(chunk, mesh, x, y, z, worldX, worldY, worldZ, color, alpha, block);
        }
        
        if (shouldRenderFace(chunk, x, y, z + 1, block)) {
            addSouthFaceWithLighting(chunk, mesh, x, y, z, worldX, worldY, worldZ, color, alpha, block);
        }
        
        if (shouldRenderFace(chunk, x + 1, y, z, block)) {
            addEastFaceWithLighting(chunk, mesh, x, y, z, worldX, worldY, worldZ, color, alpha, block);
        }
        
        if (shouldRenderFace(chunk, x - 1, y, z, block)) {
            addWestFaceWithLighting(chunk, mesh, x, y, z, worldX, worldY, worldZ, color, alpha, block);
        }
    }
    
    private void addTopFaceWithLighting(Chunk chunk, ChunkMesh mesh, int x, int y, int z,
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
        
        mesh.addVertex(worldX,     worldY + 1, worldZ,     
            color[0] * light1, color[1] * light1, color[2] * light1, alpha, 0, 1, 0, u1, v1);
        mesh.addVertex(worldX,     worldY + 1, worldZ + 1, 
            color[0] * light2, color[1] * light2, color[2] * light2, alpha, 0, 1, 0, u1, v2);
        mesh.addVertex(worldX + 1, worldY + 1, worldZ + 1, 
            color[0] * light3, color[1] * light3, color[2] * light3, alpha, 0, 1, 0, u2, v2);
        mesh.addVertex(worldX + 1, worldY + 1, worldZ,     
            color[0] * light4, color[1] * light4, color[2] * light4, alpha, 0, 1, 0, u2, v1);
    }
    
    private void addBottomFaceWithLighting(Chunk chunk, ChunkMesh mesh, int x, int y, int z,
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
        
        mesh.addVertex(worldX,     worldY, worldZ,     
            color[0] * light1, color[1] * light1, color[2] * light1, alpha, 0, -1, 0, u1, v1);
        mesh.addVertex(worldX + 1, worldY, worldZ,     
            color[0] * light2, color[1] * light2, color[2] * light2, alpha, 0, -1, 0, u2, v1);
        mesh.addVertex(worldX + 1, worldY, worldZ + 1, 
            color[0] * light3, color[1] * light3, color[2] * light3, alpha, 0, -1, 0, u2, v2);
        mesh.addVertex(worldX,     worldY, worldZ + 1, 
            color[0] * light4, color[1] * light4, color[2] * light4, alpha, 0, -1, 0, u1, v2);
    }
    
    private void addNorthFaceWithLighting(Chunk chunk, ChunkMesh mesh, int x, int y, int z,
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
        
        mesh.addVertex(worldX,     worldY,     worldZ, 
            color[0] * light1, color[1] * light1, color[2] * light1, alpha, 0, 0, -1, u1, v2);
        mesh.addVertex(worldX,     worldY + 1, worldZ, 
            color[0] * light2, color[1] * light2, color[2] * light2, alpha, 0, 0, -1, u1, v1);
        mesh.addVertex(worldX + 1, worldY + 1, worldZ, 
            color[0] * light3, color[1] * light3, color[2] * light3, alpha, 0, 0, -1, u2, v1);
        mesh.addVertex(worldX + 1, worldY,     worldZ, 
            color[0] * light4, color[1] * light4, color[2] * light4, alpha, 0, 0, -1, u2, v2);
    }
    
    private void addSouthFaceWithLighting(Chunk chunk, ChunkMesh mesh, int x, int y, int z,
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
        
        mesh.addVertex(worldX,     worldY,     worldZ + 1, 
            color[0] * light1, color[1] * light1, color[2] * light1, alpha, 0, 0, 1, u1, v2);
        mesh.addVertex(worldX + 1, worldY,     worldZ + 1, 
            color[0] * light2, color[1] * light2, color[2] * light2, alpha, 0, 0, 1, u2, v2);
        mesh.addVertex(worldX + 1, worldY + 1, worldZ + 1, 
            color[0] * light3, color[1] * light3, color[2] * light3, alpha, 0, 0, 1, u2, v1);
        mesh.addVertex(worldX,     worldY + 1, worldZ + 1, 
            color[0] * light4, color[1] * light4, color[2] * light4, alpha, 0, 0, 1, u1, v1);
    }
    
    private void addEastFaceWithLighting(Chunk chunk, ChunkMesh mesh, int x, int y, int z,
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
        
        mesh.addVertex(worldX + 1, worldY,     worldZ,     
            color[0] * light1, color[1] * light1, color[2] * light1, alpha, 1, 0, 0, u1, v2);
        mesh.addVertex(worldX + 1, worldY + 1, worldZ,     
            color[0] * light2, color[1] * light2, color[2] * light2, alpha, 1, 0, 0, u1, v1);
        mesh.addVertex(worldX + 1, worldY + 1, worldZ + 1, 
            color[0] * light3, color[1] * light3, color[2] * light3, alpha, 1, 0, 0, u2, v1);
        mesh.addVertex(worldX + 1, worldY,     worldZ + 1, 
            color[0] * light4, color[1] * light4, color[2] * light4, alpha, 1, 0, 0, u2, v2);
    }
    
    private void addWestFaceWithLighting(Chunk chunk, ChunkMesh mesh, int x, int y, int z,
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
        
        mesh.addVertex(worldX, worldY,     worldZ,     
            color[0] * light1, color[1] * light1, color[2] * light1, alpha, -1, 0, 0, u1, v2);
        mesh.addVertex(worldX, worldY,     worldZ + 1, 
            color[0] * light2, color[1] * light2, color[2] * light2, alpha, -1, 0, 0, u2, v2);
        mesh.addVertex(worldX, worldY + 1, worldZ + 1, 
            color[0] * light3, color[1] * light3, color[2] * light3, alpha, -1, 0, 0, u2, v1);
        mesh.addVertex(worldX, worldY + 1, worldZ,     
            color[0] * light4, color[1] * light4, color[2] * light4, alpha, -1, 0, 0, u1, v1);
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
        ChunkMesh solidMesh = solidMeshes.remove(chunk);
        if (solidMesh != null) solidMesh.destroy();
        
        ChunkMesh waterMesh = waterMeshes.remove(chunk);
        if (waterMesh != null) waterMesh.destroy();
        
        ChunkMesh translucentMesh = translucentMeshes.remove(chunk);
        if (translucentMesh != null) translucentMesh.destroy();
    }
    
    public void cleanup() {
        for (ChunkMesh mesh : solidMeshes.values()) mesh.destroy();
        solidMeshes.clear();
        
        for (ChunkMesh mesh : waterMeshes.values()) mesh.destroy();
        waterMeshes.clear();
        
        for (ChunkMesh mesh : translucentMeshes.values()) mesh.destroy();
        translucentMeshes.clear();
    }
}