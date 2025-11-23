package com.mineshaft.render;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * ✅ Texture Atlas - Combine multiple textures into one
 */
public class TextureAtlas {
    
    private BufferedImage atlasImage;
    private int textureSize = 16;      // Size of each texture (16x16)
    private int atlasWidth = 16;       // Atlas grid width (16 textures)
    private int atlasHeight = 16;      // Atlas grid height (16 textures)
    private int currentX = 0;
    private int currentY = 0;
    
    private int glTextureId = -1;
    
    public TextureAtlas(int textureSize, int atlasWidth, int atlasHeight) {
        this.textureSize = textureSize;
        this.atlasWidth = atlasWidth;
        this.atlasHeight = atlasHeight;
        
        // Create atlas image
        atlasImage = new BufferedImage(
            atlasWidth * textureSize, 
            atlasHeight * textureSize, 
            BufferedImage.TYPE_INT_ARGB
        );
        
        // Fill with transparent
        Graphics2D g = atlasImage.createGraphics();
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, atlasImage.getWidth(), atlasImage.getHeight());
        g.dispose();
    }
    
    /**
     * Add texture to atlas
     * @param image Texture image (must be textureSize x textureSize)
     * @return Texture coordinates [x, y] in atlas grid
     */
    public int[] addTexture(BufferedImage image) {
        if (currentY >= atlasHeight) {
            System.err.println("❌ Atlas is full!");
            return new int[]{0, 0};
        }
        
        // Resize if needed
        if (image.getWidth() != textureSize || image.getHeight() != textureSize) {
            BufferedImage resized = new BufferedImage(textureSize, textureSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = resized.createGraphics();
            g.drawImage(image, 0, 0, textureSize, textureSize, null);
            g.dispose();
            image = resized;
        }
        
        // Draw texture to atlas
        Graphics2D g = atlasImage.createGraphics();
        g.drawImage(image, currentX * textureSize, currentY * textureSize, null);
        g.dispose();
        
        int[] coords = {currentX, currentY};
        
        // Move to next position
        currentX++;
        if (currentX >= atlasWidth) {
            currentX = 0;
            currentY++;
        }
        
        return coords;
    }
    
    /**
     * Add texture from path
     */
    public int[] addTexture(String path) {
        BufferedImage image = TextureManager.loadImage(path);
        return addTexture(image);
    }
    
    /**
     * Build atlas and upload to GPU
     */
    public void build() {
        glTextureId = TextureManager.createTexture(atlasImage);
        System.out.println("✅ Texture atlas built: " + atlasImage.getWidth() + "x" + atlasImage.getHeight() + " (GL ID: " + glTextureId + ")");
    }
    
    /**
     * Get UV coordinates for atlas position
     * @param atlasX X position in atlas grid
     * @param atlasY Y position in atlas grid
     * @return [u1, v1, u2, v2] UV coordinates
     */
    public float[] getUV(int atlasX, int atlasY) {
        float u1 = (float) atlasX / atlasWidth;
        float v1 = (float) atlasY / atlasHeight;
        float u2 = (float) (atlasX + 1) / atlasWidth;
        float v2 = (float) (atlasY + 1) / atlasHeight;
        
        return new float[]{u1, v1, u2, v2};
    }
    
    /**
     * Bind atlas texture
     */
    public void bind() {
        if (glTextureId != -1) {
            TextureManager.bindTexture(glTextureId);
        }
    }
    
    public int getTextureId() {
        return glTextureId;
    }
    
    public int getTextureSize() {
        return textureSize;
    }
    
    public int getAtlasWidth() {
        return atlasWidth;
    }
    
    public int getAtlasHeight() {
        return atlasHeight;
    }
}