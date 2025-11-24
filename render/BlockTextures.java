package com.mineshaft.render;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;

import java.util.HashMap;
import java.util.Map;

/**
 * ✅ Block Texture Registry - Dynamic
 */
public class BlockTextures {

    private static TextureAtlas atlas;
    // Map texture path to atlas index (int[])
    private static Map<String, int[]> textureIndexMap = new HashMap<>();

    private static final String MISSING_TEXTURE = "assets/mineshaft/textures/missing.png";

    /**
     * Initialize texture atlas by scanning all registered blocks
     */
    public static void init() {
        System.out.println("===============================================");
        System.out.println("Building Block Texture Atlas (Dynamic)...");

        // Create atlas (16x16 texture grid, 256 slots total)
        atlas = new TextureAtlas(16, 16, 16);

        // Always add missing texture first
        addTexture(MISSING_TEXTURE);

        // Iterate over all registered blocks
        for (GameBlock block : BlockRegistry.getBlocks()) {
            // Check all possible faces
            String[] faces = { "top", "bottom", "north", "south", "east", "west", "side", "all" };

            for (String face : faces) {
                String path = block.getTexture(face);
                if (path != null && !textureIndexMap.containsKey(path)) {
                    addTexture(path);
                }
            }
        }

        // Build atlas (upload to GPU)
        atlas.build();

        System.out.println("✅ Texture atlas built with " + textureIndexMap.size() + " unique textures");
        System.out.println("===============================================");
    }

    private static void addTexture(String path) {
        if (textureIndexMap.containsKey(path))
            return;

        try {
            int[] index = atlas.addTexture(path);
            textureIndexMap.put(path, index);
            // System.out.println(" Loaded: " + path);
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
     */
    public static float[] getUV(GameBlock block, String face) {
        String path = block.getTexture(face);

        int[] index = textureIndexMap.get(path);
        if (index == null) {
            // Try to find missing texture
            index = textureIndexMap.get(MISSING_TEXTURE);
            if (index == null) {
                return new float[] { 0, 0, 1, 1 }; // Absolute fallback
            }
        }

        return atlas.getUV(index[0], index[1]);
    }

    /**
     * Bind texture atlas
     */
    public static void bind() {
        if (atlas != null) {
            atlas.bind();
        }
    }

    public static TextureAtlas getAtlas() {
        return atlas;
    }

    public static void cleanup() {
        textureIndexMap.clear();
    }
}