package com.mineshaft.render;

import com.mineshaft.entity.Camera;
import com.mineshaft.world.GameMode;
import com.mineshaft.world.World;
import com.mineshaft.core.Settings;

import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryStack;

import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ Minecraft-style F3 Debug Screen
 * Shows detailed game information on left and right side
 */
public class DebugScreen {
    
    private final long window;
    private SimpleFont font;
    
    private boolean visible = false;
    private boolean showHitboxes = false;
    private boolean showChunkBorders = false;
    
    private int windowWidth;
    private int windowHeight;
    
    // Performance tracking
    private long lastMemoryCheck = 0;
    private long usedMemory = 0;
    private long maxMemory = 0;
    private int memoryPercent = 0;
    
    private static final int LINE_HEIGHT = 10;
    private static final int MARGIN = 4;
    
    public DebugScreen(long window) {
        this.window = window;
        this.font = new SimpleFont();
        
        updateWindowSize();
        
        System.out.println("[DebugScreen] Initialized");
    }
    
    /**
     * Update window size (call when window resizes)
     */
    private void updateWindowSize() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            
            glfwGetWindowSize(window, width, height);
            
            windowWidth = width.get(0);
            windowHeight = height.get(0);
        }
    }
    
    /**
     * Toggle debug screen visibility
     */
    public void toggle() {
        visible = !visible;
        System.out.println("Debug screen: " + (visible ? "ON" : "OFF"));
    }
    
    /**
     * Toggle hitbox rendering
     */
    public void toggleHitboxes() {
        showHitboxes = !showHitboxes;
        System.out.println("Hitboxes: " + (showHitboxes ? "ON" : "OFF"));
    }
    
    /**
     * Toggle chunk border rendering
     */
    public void toggleChunkBorders() {
        showChunkBorders = !showChunkBorders;
        System.out.println("Chunk borders: " + (showChunkBorders ? "ON" : "OFF"));
    }
    
    /**
     * Main render method
     */
    public void render(Camera camera, World world, GameMode gameMode, int fps, int tps) {
        if (!visible) return;
        
        updateWindowSize();
        updateMemoryStats();
        
        // Setup 2D orthographic projection
        setup2D();
        
        // Render semi-transparent background
        renderBackground();
        
        // Render debug info
        renderLeftSide(camera, world, gameMode, fps, tps);
        renderRightSide();
        
        // Restore 3D perspective
        restore3D();
    }
    
    /**
     * Setup 2D rendering mode
     */
    private void setup2D() {
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, windowWidth, windowHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_LIGHTING);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    }
    
    /**
     * Restore 3D rendering mode
     */
    private void restore3D() {
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
        
        glEnable(GL_DEPTH_TEST);
    }
    
    /**
     * Render semi-transparent background
     */
    private void renderBackground() {
        glDisable(GL_TEXTURE_2D);
        glColor4f(0.0f, 0.0f, 0.0f, 0.5f);
        
        // Left panel
        glBegin(GL_QUADS);
        glVertex2f(0, 0);
        glVertex2f(windowWidth / 2.0f, 0);
        glVertex2f(windowWidth / 2.0f, windowHeight / 2.0f);
        glVertex2f(0, windowHeight / 2.0f);
        glEnd();
        
        // Right panel
        glBegin(GL_QUADS);
        glVertex2f(windowWidth / 2.0f, 0);
        glVertex2f(windowWidth, 0);
        glVertex2f(windowWidth, windowHeight / 2.0f);
        glVertex2f(windowWidth / 2.0f, windowHeight / 2.0f);
        glEnd();
        
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
    }
    
    /**
     * Render left side debug info (like Minecraft F3 left)
     */
    private void renderLeftSide(Camera camera, World world, GameMode gameMode, int fps, int tps) {
        float x = MARGIN;
        float y = MARGIN;
        
        // Title
        drawText("Mineshaft " + Settings.VERSION, x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        
        // Separator
        y += 2;
        
        // FPS & TPS
        drawText(String.format("FPS: %d | TPS: %d", fps, tps), x, y, 1, 1, 0);
        y += LINE_HEIGHT;
        
        // Separator
        y += 2;
        
        // Position
        drawText(String.format("XYZ: %.3f / %.3f / %.3f", 
            camera.getX(), camera.getY(), camera.getZ()), x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        
        // Block position
        int blockX = (int) Math.floor(camera.getX());
        int blockY = (int) Math.floor(camera.getY());
        int blockZ = (int) Math.floor(camera.getZ());
        drawText(String.format("Block: %d %d %d", blockX, blockY, blockZ), x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        
        // Chunk position
        int chunkX = blockX >> 4;
        int chunkZ = blockZ >> 4;
        int chunkLocalX = blockX & 15;
        int chunkLocalZ = blockZ & 15;
        drawText(String.format("Chunk: %d %d %d in %d %d", 
            chunkLocalX, blockY, chunkLocalZ, chunkX, chunkZ), x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        
        // Separator
        y += 2;
        
        // Rotation
        drawText(String.format("Facing: %s (%.1f / %.1f)", 
            getCardinalDirection(camera.getYaw()), 
            camera.getYaw(), 
            camera.getPitch()), x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        
        // Separator
        y += 2;
        
        // Light level (if in world)
        if (world != null) {
            int lightLevel = world.getLight(blockX, blockY, blockZ);
            int skyLight = world.getSkyLight(blockX, blockY, blockZ);
            int blockLight = world.getBlockLight(blockX, blockY, blockZ);
            
            drawText(String.format("Light: %d (Sky: %d, Block: %d)", 
                lightLevel, skyLight, blockLight), x, y, 1, 1, 0);
            y += LINE_HEIGHT;
        }
        
        // Separator
        y += 2;
        
        // Game mode
        drawText("Game Mode: " + gameMode.getName(), x, y, 0.5f, 1, 0.5f);
        y += LINE_HEIGHT;
        
        // Player state
        String state = "Walking";
        if (camera.getPlayer().isFlying()) {
            state = "Flying";
        } else if (camera.getPlayer().isInWater()) {
            state = "Swimming";
        } else if (camera.getPlayer().isSprinting()) {
            state = "Sprinting";
        }
        drawText("State: " + state, x, y, 0.5f, 1, 0.5f);
        y += LINE_HEIGHT;
        
        // Separator
        y += 2;
        
        // Biome (placeholder)
        drawText("Biome: Plains", x, y, 0.5f, 1, 1);
        y += LINE_HEIGHT;
        
        // Separator
        y += 2;
        
        // Render distance
        int renderDistance = Settings.RENDER_DISTANCE;
        drawText(String.format("Render Distance: %d chunks", renderDistance), x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        
        // Loaded chunks
        int loadedChunks = world != null ? world.getLoadedChunkCount() : 0;
        drawText(String.format("Loaded Chunks: %d", loadedChunks), x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        
        // Separator
        y += 2;
        
        // Debug flags
        if (showChunkBorders) {
            drawText("Chunk Borders: ON", x, y, 1, 0.5f, 0.5f);
            y += LINE_HEIGHT;
        }
        
        if (showHitboxes) {
            drawText("Hitboxes: ON", x, y, 1, 0.5f, 0.5f);
            y += LINE_HEIGHT;
        }
    }
    
    /**
     * Render right side debug info (like Minecraft F3 right)
     */
    private void renderRightSide() {
        float x = windowWidth / 2.0f + MARGIN;
        float y = MARGIN;
        
        // Java version
        String javaVersion = System.getProperty("java.version");
        drawText("Java: " + javaVersion, x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        
        // Separator
        y += 2;
        
        // Memory usage
        drawText(String.format("Mem: %d%% %dMB / %dMB", 
            memoryPercent, usedMemory, maxMemory), x, y, 1, 1, 0);
        y += LINE_HEIGHT;
        
        // Memory bar
        renderMemoryBar(x, y, 150, 8);
        y += LINE_HEIGHT + 2;
        
        // Separator
        y += 2;
        
        // Allocated memory
        long totalMemory = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        drawText(String.format("Allocated: %dMB", totalMemory), x, y, 0.7f, 0.7f, 0.7f);
        y += LINE_HEIGHT;
        
        // Separator
        y += 2;
        
        // OpenGL info
        String glVersion = GL11.glGetString(GL11.GL_VERSION);
        drawText("OpenGL: " + (glVersion != null ? glVersion : "Unknown"), x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        
        String glRenderer = GL11.glGetString(GL11.GL_RENDERER);
        if (glRenderer != null && glRenderer.length() > 40) {
            glRenderer = glRenderer.substring(0, 37) + "...";
        }
        drawText("GPU: " + (glRenderer != null ? glRenderer : "Unknown"), x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        
        // Separator
        y += 2;
        
        // Display info
        drawText(String.format("Display: %dx%d", windowWidth, windowHeight), x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        
        // VSync
        drawText("VSync: " + (Settings.VSYNC ? "ON" : "OFF"), x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        
        // Separator
        y += 2;
        
        // CPU info
        int cores = Runtime.getRuntime().availableProcessors();
        drawText(String.format("CPU: %d cores", cores), x, y, 1, 1, 1);
        y += LINE_HEIGHT;
        
        // Separator
        y += 2;
        
        // OS info
        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        drawText(String.format("OS: %s (%s)", os, arch), x, y, 0.7f, 0.7f, 0.7f);
        y += LINE_HEIGHT;
        
        // Separator
        y += 2;
        
        // Help text
        y = windowHeight / 2.0f - LINE_HEIGHT * 6;
        drawText("F3 + Q = Help", x, y, 0.5f, 0.5f, 0.5f);
        y += LINE_HEIGHT;
        drawText("F3 + A = Reload chunks", x, y, 0.5f, 0.5f, 0.5f);
        y += LINE_HEIGHT;
        drawText("F3 + B = Hitboxes", x, y, 0.5f, 0.5f, 0.5f);
        y += LINE_HEIGHT;
        drawText("F3 + G = Chunk borders", x, y, 0.5f, 0.5f, 0.5f);
        y += LINE_HEIGHT;
        drawText("F3 + N = Gamemode", x, y, 0.5f, 0.5f, 0.5f);
        y += LINE_HEIGHT;
    }
    
    /**
     * Render memory usage bar
     */
    private void renderMemoryBar(float x, float y, float width, float height) {
        glDisable(GL_TEXTURE_2D);
        
        // Background (dark gray)
        glColor4f(0.2f, 0.2f, 0.2f, 0.8f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        
        // Memory bar (gradient: green -> yellow -> red)
        float percent = memoryPercent / 100.0f;
        float barWidth = width * percent;
        
        float r = Math.min(1.0f, percent * 2);
        float g = Math.min(1.0f, 2 - percent * 2);
        float b = 0.0f;
        
        glColor4f(r, g, b, 0.9f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + barWidth, y);
        glVertex2f(x + barWidth, y + height);
        glVertex2f(x, y + height);
        glEnd();
        
        // Border
        glColor4f(1, 1, 1, 0.5f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        
        glEnable(GL_TEXTURE_2D);
        glColor4f(1, 1, 1, 1);
    }
    
    /**
     * Update memory statistics
     */
    private void updateMemoryStats() {
        long now = System.currentTimeMillis();
        if (now - lastMemoryCheck < 500) return; // Update every 500ms
        
        lastMemoryCheck = now;
        
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemoryBytes = runtime.maxMemory();
        
        usedMemory = (totalMemory - freeMemory) / 1024 / 1024; // MB
        maxMemory = maxMemoryBytes / 1024 / 1024; // MB
        
        memoryPercent = (int) ((usedMemory * 100) / maxMemory);
    }
    
    /**
     * Get cardinal direction from yaw
     */
    private String getCardinalDirection(float yaw) {
        yaw = yaw % 360;
        if (yaw < 0) yaw += 360;
        
        if (yaw >= 337.5 || yaw < 22.5) return "North (Z-)";
        if (yaw >= 22.5 && yaw < 67.5) return "North-East";
        if (yaw >= 67.5 && yaw < 112.5) return "East (X+)";
        if (yaw >= 112.5 && yaw < 157.5) return "South-East";
        if (yaw >= 157.5 && yaw < 202.5) return "South (Z+)";
        if (yaw >= 202.5 && yaw < 247.5) return "South-West";
        if (yaw >= 247.5 && yaw < 292.5) return "West (X-)";
        if (yaw >= 292.5 && yaw < 337.5) return "North-West";
        
        return "Unknown";
    }
    
    /**
     * Draw text using SimpleFont
     */
    private void drawText(String text, float x, float y, float r, float g, float b) {
        font.drawStringWithShadow(text, x, y, r, g, b, 1.0f);
    }
    
    // Getters
    public boolean isVisible() {
        return visible;
    }
    
    public boolean showChunkBorders() {
        return showChunkBorders;
    }
    
    public boolean showHitboxes() {
        return showHitboxes;
    }
    
    /**
     * Cleanup
     */
    public void cleanup() {
        if (font != null) {
            font.cleanup();
        }
    }
}