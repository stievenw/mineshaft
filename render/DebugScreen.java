package com.mineshaft.render;

import com.mineshaft.entity.Camera;
import com.mineshaft.world.Chunk;
import com.mineshaft.world.GameMode;
import com.mineshaft.world.World;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ LWJGL 3 - Minecraft-style F3 debug screen - Fixed warnings
 */
public class DebugScreen {
    private final long window;
    private boolean visible = false;
    // ✅ REMOVED: Unused field showFPS
    private boolean showHitboxes = false;
    private boolean showChunkBorders = false;
    private boolean showProfiler = false;
    private int screenWidth;
    private int screenHeight;
    
    // FPS tracking
    private int fps = 0;
    private int tps = 0;
    private long usedMemory = 0;
    private long maxMemory = 0;
    
    public DebugScreen(long window) {
        this.window = window;
        updateScreenSize();
        updateMemory();
    }
    
    private void updateScreenSize() {
        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        this.screenWidth = w[0];
        this.screenHeight = h[0];
    }
    
    public void render(Camera camera, World world, GameMode gameMode, int fps, int tps) {
        if (!visible) return;
        
        this.fps = fps;
        this.tps = tps;
        updateMemory();
        updateScreenSize();
        
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        renderLeftSide(camera, world, gameMode);
        renderRightSide();
        
        if (showChunkBorders) {
            renderChunkBordersHint();
        }
        
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }
    
    private void renderLeftSide(Camera camera, World world, GameMode gameMode) {
        int y = 10;
        int lineHeight = 12;
        
        drawText(5, y, "Mineshaft Alpha 0.7 (FPS: " + fps + " TPS: " + tps + ")", 1, 1, 1);
        y += lineHeight;
        
        y += lineHeight;
        
        drawText(5, y, String.format("XYZ: %.3f / %.3f / %.3f", 
            camera.getX(), camera.getY(), camera.getZ()), 1, 1, 1);
        y += lineHeight;
        
        int bx = (int) Math.floor(camera.getX());
        int by = (int) Math.floor(camera.getY());
        int bz = (int) Math.floor(camera.getZ());
        drawText(5, y, String.format("Block: %d %d %d", bx, by, bz), 1, 1, 1);
        y += lineHeight;
        
        int cx = Math.floorDiv(bx, Chunk.CHUNK_SIZE);
        int cz = Math.floorDiv(bz, Chunk.CHUNK_SIZE);
        int localX = Math.floorMod(bx, Chunk.CHUNK_SIZE);
        int localZ = Math.floorMod(bz, Chunk.CHUNK_SIZE);
        drawText(5, y, String.format("Chunk: %d %d in %d %d %d", 
            localX, localZ, cx, 0, cz), 1, 1, 1);
        y += lineHeight;
        
        String facing = getFacingDirection(camera.getYaw());
        drawText(5, y, String.format("Facing: %s (%.1f / %.1f)", 
            facing, camera.getYaw(), camera.getPitch()), 1, 1, 1);
        y += lineHeight;
        
        y += lineHeight;
        
        drawText(5, y, "Biome: minecraft:plains", 1, 1, 1);
        y += lineHeight;
        
        drawText(5, y, "Light: 15 (15 sky, 0 block)", 1, 1, 1);
        y += lineHeight;
        
        y += lineHeight;
        
        drawText(5, y, "Game Mode: " + gameMode.getName(), 1, 1, 0);
        y += lineHeight;
        
        if (camera.isFlying()) {
            drawText(5, y, "Flying: YES", 0, 1, 0);
        } else {
            drawText(5, y, "On Ground: " + (camera.isOnGround() ? "YES" : "NO"), 1, 1, 1);
        }
        y += lineHeight;
        
        y += lineHeight;
        
        drawText(5, y, "Loaded Chunks: " + world.getLoadedChunkCount(), 1, 1, 1);
        y += lineHeight;
        
        drawText(5, y, String.format("Memory: %d%% %dMB / %dMB", 
            (usedMemory * 100 / maxMemory), usedMemory, maxMemory), 1, 1, 1);
        y += lineHeight;
        
        long totalMemory = Runtime.getRuntime().totalMemory() / 1024 / 1024;
        drawText(5, y, "Allocated: " + totalMemory + "MB", 1, 1, 1);
        y += lineHeight;
    }
    
    private void renderRightSide() {
        int y = 10;
        int lineHeight = 12;
        
        String javaVersion = System.getProperty("java.version");
        drawTextRight(screenWidth - 5, y, "Java: " + javaVersion, 1, 1, 1);
        y += lineHeight;
        
        drawTextRight(screenWidth - 5, y, "Display: " + screenWidth + "x" + screenHeight, 1, 1, 1);
        y += lineHeight;
        
        String glVersion = glGetString(GL_VERSION);
        drawTextRight(screenWidth - 5, y, glVersion, 1, 1, 1);
        y += lineHeight;
        
        String renderer = glGetString(GL_RENDERER);
        if (renderer.length() > 40) {
            renderer = renderer.substring(0, 37) + "...";
        }
        drawTextRight(screenWidth - 5, y, renderer, 1, 1, 1);
        y += lineHeight;
        
        y += lineHeight;
        
        drawTextRight(screenWidth - 5, screenHeight - 30, 
            "Press F3 + Q for help", 0.7f, 0.7f, 0.7f);
    }
    
    private void renderChunkBordersHint() {
        int y = screenHeight / 2;
        drawTextCentered(screenWidth / 2, y, "Chunk Borders: ON", 1, 1, 0);
    }
    
    private String getFacingDirection(float yaw) {
        yaw = yaw % 360;
        if (yaw < 0) yaw += 360;
        
        if (yaw >= 337.5 || yaw < 22.5) return "south";
        if (yaw >= 22.5 && yaw < 67.5) return "southwest";
        if (yaw >= 67.5 && yaw < 112.5) return "west";
        if (yaw >= 112.5 && yaw < 157.5) return "northwest";
        if (yaw >= 157.5 && yaw < 202.5) return "north";
        if (yaw >= 202.5 && yaw < 247.5) return "northeast";
        if (yaw >= 247.5 && yaw < 292.5) return "east";
        if (yaw >= 292.5 && yaw < 337.5) return "southeast";
        
        return "unknown";
    }
    
    private void updateMemory() {
        Runtime runtime = Runtime.getRuntime();
        maxMemory = runtime.maxMemory() / 1024 / 1024;
        long totalMemory = runtime.totalMemory() / 1024 / 1024;
        long freeMemory = runtime.freeMemory() / 1024 / 1024;
        usedMemory = totalMemory - freeMemory;
    }
    
    private void drawText(int x, int y, String text, float r, float g, float b) {
        int width = text.length() * 6;
        
        glColor4f(0, 0, 0, 0.5f);
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + 10);
        glVertex2f(x, y + 10);
        glEnd();
        
        glColor3f(r, g, b);
        glBegin(GL_QUADS);
        glVertex2f(x + 2, y + 2);
        glVertex2f(x + width - 2, y + 2);
        glVertex2f(x + width - 2, y + 8);
        glVertex2f(x + 2, y + 8);
        glEnd();
    }
    
    private void drawTextRight(int x, int y, String text, float r, float g, float b) {
        int width = text.length() * 6;
        drawText(x - width, y, text, r, g, b);
    }
    
    private void drawTextCentered(int x, int y, String text, float r, float g, float b) {
        int width = text.length() * 6;
        drawText(x - width / 2, y, text, r, g, b);
    }
    
    public void toggle() { 
        visible = !visible; 
        System.out.println("Debug screen: " + (visible ? "ON" : "OFF"));
    }
    
    public void setVisible(boolean visible) { this.visible = visible; }
    public boolean isVisible() { return visible; }
    
    public void toggleHitboxes() { 
        showHitboxes = !showHitboxes; 
        System.out.println("Hitboxes: " + (showHitboxes ? "ON" : "OFF"));
    }
    public boolean showHitboxes() { return showHitboxes; }
    
    public void toggleChunkBorders() { 
        showChunkBorders = !showChunkBorders;
        System.out.println("Chunk borders: " + (showChunkBorders ? "ON" : "OFF"));
    }
    public boolean showChunkBorders() { return showChunkBorders; }
    
    public void toggleProfiler() {
        showProfiler = !showProfiler;
        System.out.println("Profiler: " + (showProfiler ? "ON" : "OFF"));
    }
    
    public void updateSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }
}