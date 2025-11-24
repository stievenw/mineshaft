package com.mineshaft.render;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ Simple bitmap font renderer with SCALE support
 */
public class SimpleFont {
    
    private static final int CHAR_WIDTH = 8;
    private static final int CHAR_HEIGHT = 8;
    private static final int GRID_SIZE = 16;
    
    private int fontTexture = 0;
    private boolean textureLoaded = false;
    
    public SimpleFont() {
        System.out.println("[SimpleFont] Initializing...");
        loadFontTexture();
    }
    
    private void loadFontTexture() {
        String path = "assets/mineshaft/font/ascii.png";
        
        try {
            InputStream stream = getClass().getClassLoader().getResourceAsStream(path);
            
            if (stream == null) {
                System.err.println("⚠️ [SimpleFont] ascii.png not found at: " + path);
                System.err.println("   Creating fallback texture");
                fontTexture = generateFallbackTexture();
                textureLoaded = false;
                return;
            }
            
            BufferedImage image = ImageIO.read(stream);
            stream.close();
            
            if (image == null) {
                System.err.println("❌ [SimpleFont] Failed to decode ascii.png");
                fontTexture = generateFallbackTexture();
                textureLoaded = false;
                return;
            }
            
            System.out.println("✅ [SimpleFont] Loaded ascii.png: " + image.getWidth() + "x" + image.getHeight());
            
            fontTexture = createTexture(image);
            textureLoaded = true;
            
            System.out.println("✅ [SimpleFont] Texture ID: " + fontTexture);
            
        } catch (IOException e) {
            System.err.println("❌ [SimpleFont] Exception loading ascii.png");
            e.printStackTrace();
            fontTexture = generateFallbackTexture();
            textureLoaded = false;
        }
    }
    
    private int createTexture(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        int[] pixels = new int[width * height];
        image.getRGB(0, 0, width, height, pixels, 0, width);
        
        ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
        
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = pixels[y * width + x];
                
                buffer.put((byte) ((pixel >> 16) & 0xFF));
                buffer.put((byte) ((pixel >> 8) & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) ((pixel >> 24) & 0xFF));
            }
        }
        
        buffer.flip();
        
        int texID = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texID);
        
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP);
        
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
        
        glBindTexture(GL_TEXTURE_2D, 0);
        
        return texID;
    }
    
    private int generateFallbackTexture() {
        System.out.println("⚠️ [SimpleFont] Generating 128x128 fallback texture");
        
        BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        
        for (int gridY = 0; gridY < 16; gridY++) {
            for (int gridX = 0; gridX < 16; gridX++) {
                int baseX = gridX * 8;
                int baseY = gridY * 8;
                
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        boolean border = (x == 0 || x == 7 || y == 0 || y == 7);
                        boolean inner = (x >= 2 && x <= 5 && y >= 2 && y <= 5);
                        
                        int color;
                        if (border) {
                            color = 0xFF606060;
                        } else if (inner) {
                            color = 0xFFFFFFFF;
                        } else {
                            color = 0x00000000;
                        }
                        
                        image.setRGB(baseX + x, baseY + y, color);
                    }
                }
            }
        }
        
        return createTexture(image);
    }
    
    /**
     * ✅ Draw string with custom scale
     */
    public void drawString(String text, float x, float y, float r, float g, float b, float alpha, float scale) {
        if (text == null || text.isEmpty()) return;
        
        glPushAttrib(GL_ENABLE_BIT | GL_CURRENT_BIT);
        
        glEnable(GL_TEXTURE_2D);
        glBindTexture(GL_TEXTURE_2D, fontTexture);
        
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        glColor4f(r, g, b, alpha);
        glDisable(GL_DEPTH_TEST);
        
        float curX = x;
        float scaledWidth = CHAR_WIDTH * scale;
        float scaledHeight = CHAR_HEIGHT * scale;
        
        for (char c : text.toCharArray()) {
            if (c == ' ') {
                curX += (CHAR_WIDTH / 2) * scale;
                continue;
            }
            
            drawCharScaled(c, curX, y, scaledWidth, scaledHeight);
            curX += scaledWidth;
        }
        
        glPopAttrib();
        glEnable(GL_DEPTH_TEST);
    }
    
    /**
     * ✅ Draw character with scale
     */
    private void drawCharScaled(char c, float x, float y, float width, float height) {
        int ascii = (int) c;
        if (ascii < 0 || ascii >= 256) return;
        
        int gridX = ascii % GRID_SIZE;
        int gridY = ascii / GRID_SIZE;
        
        float u1 = (float) gridX / GRID_SIZE;
        float v1 = (float) gridY / GRID_SIZE;
        float u2 = (float) (gridX + 1) / GRID_SIZE;
        float v2 = (float) (gridY + 1) / GRID_SIZE;
        
        glBegin(GL_QUADS);
        glTexCoord2f(u1, v1); glVertex2f(x, y);
        glTexCoord2f(u2, v1); glVertex2f(x + width, y);
        glTexCoord2f(u2, v2); glVertex2f(x + width, y + height);
        glTexCoord2f(u1, v2); glVertex2f(x, y + height);
        glEnd();
    }
    
    /**
     * ✅ Original method (calls new method with scale = 1.0)
     */
    public void drawString(String text, float x, float y, float r, float g, float b, float alpha) {
        drawString(text, x, y, r, g, b, alpha, 1.0f);
    }
    
    /**
     * ✅ Draw string with shadow and custom scale
     */
    public void drawStringWithShadow(String text, float x, float y, float r, float g, float b, float alpha, float scale) {
        // Shadow (black, offset by scale)
        drawString(text, x + scale, y + scale, 0, 0, 0, alpha * 0.5f, scale);
        // Main text
        drawString(text, x, y, r, g, b, alpha, scale);
    }
    
    /**
     * ✅ Original method (calls new method with scale = 1.0)
     */
    public void drawStringWithShadow(String text, float x, float y, float r, float g, float b, float alpha) {
        drawStringWithShadow(text, x, y, r, g, b, alpha, 1.0f);
    }
    
    /**
     * ✅ Get width with scale
     */
    public int getStringWidth(String text, float scale) {
        if (text == null || text.isEmpty()) return 0;
        
        int width = 0;
        for (char c : text.toCharArray()) {
            if (c == ' ') {
                width += (CHAR_WIDTH / 2) * scale;
            } else {
                width += CHAR_WIDTH * scale;
            }
        }
        return width;
    }
    
    /**
     * ✅ Original method (calls new method with scale = 1.0)
     */
    public int getStringWidth(String text) {
        return getStringWidth(text, 1.0f);
    }
    
    public void drawCenteredString(String text, float centerX, float y, float r, float g, float b, float alpha) {
        int width = getStringWidth(text);
        float x = centerX - (width / 2.0f);
        drawString(text, x, y, r, g, b, alpha);
    }
    
    public void cleanup() {
        if (fontTexture != 0) {
            glDeleteTextures(fontTexture);
            fontTexture = 0;
            System.out.println("[SimpleFont] Cleaned up texture");
        }
    }
    
    public boolean isTextureLoaded() {
        return textureLoaded;
    }
    
    public int getTextureID() {
        return fontTexture;
    }
}