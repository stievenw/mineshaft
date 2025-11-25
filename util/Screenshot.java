package com.mineshaft.util;

import org.lwjgl.BufferUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ LWJGL 3 - Screenshot utility (F2)
 */
public class Screenshot {
    private static final String SCREENSHOT_DIR = "screenshots";
    
    /**
     * ✅ Take screenshot and save to file (LWJGL 3)
     */
    public static boolean takeScreenshot(long window) {
        try {
            // Create screenshots directory if it doesn't exist
            File dir = new File(SCREENSHOT_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            
            // ✅ Get window size from GLFW
            int[] w = new int[1];
            int[] h = new int[1];
            glfwGetFramebufferSize(window, w, h);
            int width = w[0];
            int height = h[0];
            
            // Read pixels from framebuffer
            ByteBuffer buffer = BufferUtils.createByteBuffer(width * height * 4);
            glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, buffer);
            
            // Create BufferedImage
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            
            // Convert RGBA to RGB and flip vertically
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int i = (x + (height - 1 - y) * width) * 4;
                    int r = buffer.get(i) & 0xFF;
                    int g = buffer.get(i + 1) & 0xFF;
                    int b = buffer.get(i + 2) & 0xFF;
                    image.setRGB(x, y, (r << 16) | (g << 8) | b);
                }
            }
            
            // Generate filename with timestamp
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
            String filename = SCREENSHOT_DIR + "/screenshot_" + dateFormat.format(new Date()) + ".png";
            
            // Save to file
            File file = new File(filename);
            ImageIO.write(image, "PNG", file);
            
            System.out.println("Screenshot saved: " + filename);
            return true;
            
        } catch (Exception e) {
            System.err.println("Failed to take screenshot: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}