// ============================================
// File 1: BlockTextures.java (FIXED)
// ============================================
package com.mineshaft.render;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;

import java.util.HashMap;
import java.util.Map;

/**
 * ✅ Block Texture Registry - Dynamic with Overlay Support
 */
public class BlockTextures {

    private static TextureAtlas atlas;
    private static Map<String, int[]> textureIndexMap = new HashMap<>();

    private static final String MISSING_TEXTURE = "assets/mineshaft/textures/missing.png";

    /**
     * Initialize texture atlas by scanning all registered blocks
     */
    public static void init() {
        System.out.println("===============================================");
        System.out.println("Building Block Texture Atlas (Dynamic)...");

        // Create atlas (16x16 grid = 256 texture slots, each 16x16 pixels)
        atlas = new TextureAtlas(16, 16, 16);

        // Always add missing texture first
        addTexture(MISSING_TEXTURE);

        // Iterate over all registered blocks
        for (GameBlock block : BlockRegistry.getBlocks()) {
            // Check all possible faces (including overlays)
            String[] faces = {
                    "top", "bottom", "north", "south", "east", "west", "side", "all",
                    "side_overlay", "side_snowed" // ✅ Support for grass_block overlays
            };

            for (String face : faces) {
                String path = block.getTexture(face);
                if (path != null && !path.isEmpty() && !textureIndexMap.containsKey(path)) {
                    addTexture(path);
                }

                // ✅ Also check overlay textures
                if (block.hasOverlay(face)) {
                    String overlayPath = block.getOverlayTexture(face);
                    if (overlayPath != null && !overlayPath.isEmpty() && !textureIndexMap.containsKey(overlayPath)) {
                        addTexture(overlayPath);
                    }
                }
            }
        }

        // Build atlas (upload to GPU)
        atlas.build();

        System.out.println("✅ Texture atlas built with " + textureIndexMap.size() + " unique textures");
        System.out.println("===============================================");
    }

    /**
     * Add texture to atlas
     */
    private static void addTexture(String path) {
        if (path == null || path.isEmpty())
            return;

        if (textureIndexMap.containsKey(path))
            return;

        try {
            int[] index = atlas.addTexture(path);
            textureIndexMap.put(path, index);
            // System.out.println(" ✓ Loaded: " + path);
        } catch (Exception e) {
            System.err.println("❌ Failed to load texture: " + path);
            // Map to missing texture
            if (textureIndexMap.containsKey(MISSING_TEXTURE)) {
                textureIndexMap.put(path, textureIndexMap.get(MISSING_TEXTURE));
            }
        }
    }

    /**
     * Get UV coordinates for block face
     * 
     * @param block Block to get texture from
     * @param face  Face name (top, bottom, side, north, south, east, west, all)
     * @return UV coordinates [u1, v1, u2, v2]
     */
    public static float[] getUV(GameBlock block, String face) {
        if (block == null) {
            return getFallbackUV();
        }

        String path = block.getTexture(face);
        if (path == null || path.isEmpty()) {
            return getFallbackUV();
        }

        int[] index = textureIndexMap.get(path);
        if (index == null) {
            // Try missing texture
            index = textureIndexMap.get(MISSING_TEXTURE);
            if (index == null) {
                return getFallbackUV();
            }
        }

        return atlas.getUV(index[0], index[1]);
    }

    /**
     * Get UV coordinates for a specific texture path
     * 
     * @param texturePath Full texture path
     * @return UV coordinates [u1, v1, u2, v2]
     */
    public static float[] getUV(String texturePath) {
        if (texturePath == null || texturePath.isEmpty()) {
            return getFallbackUV();
        }

        int[] index = textureIndexMap.get(texturePath);
        if (index == null) {
            // Try missing texture
            index = textureIndexMap.get(MISSING_TEXTURE);
            if (index == null) {
                return getFallbackUV();
            }
        }

        return atlas.getUV(index[0], index[1]);
    }

    /**
     * Get texture index in atlas
     * 
     * @param texturePath Texture path
     * @return Texture index, or -1 if not found
     */
    public static int getTextureIndex(String texturePath) {
        if (texturePath == null || texturePath.isEmpty()) {
            return -1;
        }

        int[] index = textureIndexMap.get(texturePath);
        if (index == null) {
            return -1;
        }

        // Convert 2D index to 1D
        return index[1] * 16 + index[0]; // Assuming 16x16 grid
    }

    /**
     * Check if texture exists in atlas
     */
    public static boolean hasTexture(String texturePath) {
        return texturePath != null && textureIndexMap.containsKey(texturePath);
    }

    /**
     * Fallback UV coordinates (full texture)
     */
    private static float[] getFallbackUV() {
        return new float[] { 0.0f, 0.0f, 1.0f, 1.0f };
    }

    /**
     * Bind texture atlas for rendering
     */
    public static void bind() {
        if (atlas != null) {
            atlas.bind();
        }
    }

    /**
     * Get the texture atlas instance
     * 
     * @return TextureAtlas instance
     * @throws IllegalStateException if atlas not initialized
     */
    public static TextureAtlas getAtlas() {
        if (atlas == null) {
            throw new IllegalStateException("BlockTextures not initialized! Call BlockTextures.init() first.");
        }
        return atlas;
    }

    /**
     * Get number of textures in atlas
     */
    public static int getTextureCount() {
        return textureIndexMap.size();
    }

    /**
     * Cleanup resources
     */
    public static void cleanup() {
        // TextureAtlas will be cleaned up by TextureManager
        textureIndexMap.clear();
        atlas = null;
    }
}