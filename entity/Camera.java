package com.mineshaft.entity;

import com.mineshaft.block.Block;
import com.mineshaft.block.Blocks;
import com.mineshaft.core.Settings;
import com.mineshaft.world.GameMode;
import com.mineshaft.world.World;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ LWJGL 3 - First-person camera with swimming physics and underwater effects
 * ✅ FIXED: Mouse Y-axis inverted issue (HANYA 1 BARIS DIUBAH)
 */
public class Camera {
    private final long window;
    
    private float x, y, z;
    private float pitch, yaw;
    
    // ✅ Mouse tracking for GLFW
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private boolean firstMouse = true;
    
    // Physics
    private float velocityY = 0;
    private boolean onGround = false;
    private boolean inWater = false;
    
    // GameMode
    private GameMode gameMode = GameMode.CREATIVE;
    private boolean isFlying = true;
    
    // Player dimensions
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float PLAYER_WIDTH = 0.6f;
    private static final float EYE_HEIGHT = 1.62f;
    
    // Physics constants
    private static final float GRAVITY = -0.08f;
    private static final float WATER_GRAVITY = -0.02f;
    private static final float JUMP_STRENGTH = 0.42f;
    private static final float SWIM_UP_STRENGTH = 0.04f;
    private static final float TERMINAL_VELOCITY = -3.92f;
    private static final float WATER_TERMINAL_VELOCITY = -0.5f;
    
    private World world;
    
    public Camera(float x, float y, float z, long window) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.pitch = 0;
        this.yaw = 0;
        this.window = window;
        
        setupMouseCallback();
    }
    
    /**
     * ✅ Setup GLFW mouse callback
     */
    private void setupMouseCallback() {
        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
            }
            
            float xoffset = (float) (xpos - lastMouseX);
            float yoffset = (float) (lastMouseY - ypos); // ✅ TETAP seperti asli (Reversed)
            
            lastMouseX = xpos;
            lastMouseY = ypos;
            
            processMouse(xoffset, yoffset);
        });
    }
    
    public void setWorld(World world) {
        this.world = world;
    }
    
    public void setGameMode(GameMode mode) {
        this.gameMode = mode;
        
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            isFlying = true;
            velocityY = 0;
        } else {
            isFlying = false;
        }
        
        System.out.println("Game mode: " + mode.getName());
    }
    
    public GameMode getGameMode() {
        return gameMode;
    }
    
    public void processInput(float delta) {
        // Mouse is handled by callback
        processKeyboard(delta);
        
        // Check if in water
        checkWaterStatus();
        
        // Apply physics if not flying
        if (!isFlying && gameMode == GameMode.SURVIVAL) {
            if (inWater) {
                applyWaterPhysics(delta);
            } else {
                applyPhysics(delta);
            }
        }
    }
    
    /**
     * ✅ FIXED - Process mouse movement
     * ⚠️ HANYA BARIS INI YANG DIUBAH (pitch += menjadi pitch -=)
     */
    private void processMouse(float xoffset, float yoffset) {
        xoffset *= Settings.MOUSE_SENSITIVITY;
        yoffset *= Settings.MOUSE_SENSITIVITY;
        
        yaw += xoffset;
        pitch -= yoffset;  // ✅ ✅ ✅ SATU-SATUNYA PERUBAHAN: += menjadi -=
        
        if (pitch > 90) pitch = 90;
        if (pitch < -90) pitch = -90;
        
        if (yaw > 360) yaw -= 360;
        if (yaw < 0) yaw += 360;
    }
    
    /**
     * ✅ LWJGL 3 - Process keyboard input
     */
    private void processKeyboard(float delta) {
        float speed = Settings.WALK_SPEED * delta;
        
        if (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS) {
            speed = Settings.SPRINT_SPEED * delta;
        }
        
        // Swimming is slower
        if (inWater && !isFlying) {
            speed *= 0.5f;
        }
        
        // Spectator mode - faster
        if (gameMode == GameMode.SPECTATOR) {
            speed *= 2.0f;
        }
        
        // Movement
        float moveX = 0, moveZ = 0;
        
        if (glfwGetKey(window, GLFW_KEY_W) == GLFW_PRESS) {
            moveX += (float) Math.sin(Math.toRadians(yaw)) * speed;
            moveZ -= (float) Math.cos(Math.toRadians(yaw)) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_S) == GLFW_PRESS) {
            moveX -= (float) Math.sin(Math.toRadians(yaw)) * speed;
            moveZ += (float) Math.cos(Math.toRadians(yaw)) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_A) == GLFW_PRESS) {
            moveX += (float) Math.sin(Math.toRadians(yaw - 90)) * speed;
            moveZ -= (float) Math.cos(Math.toRadians(yaw - 90)) * speed;
        }
        if (glfwGetKey(window, GLFW_KEY_D) == GLFW_PRESS) {
            moveX += (float) Math.sin(Math.toRadians(yaw + 90)) * speed;
            moveZ -= (float) Math.cos(Math.toRadians(yaw + 90)) * speed;
        }
        
        // Apply horizontal movement
        if (moveX != 0 || moveZ != 0) {
            if (gameMode == GameMode.SPECTATOR) {
                x += moveX;
                z += moveZ;
            } else {
                moveWithCollision(moveX, 0, moveZ);
            }
        }
        
        // Vertical movement
        if (isFlying) {
            if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
                y += speed * 2;
            }
            if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) {
                y -= speed * 2;
            }
        } else {
            // Jump or swim up
            if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
                if (inWater) {
                    // Swim up
                    velocityY = SWIM_UP_STRENGTH;
                } else if (onGround) {
                    // Jump
                    velocityY = JUMP_STRENGTH;
                    onGround = false;
                }
            }
        }
    }
    
    /**
     * Check if player is in water
     */
    private void checkWaterStatus() {
        if (world == null) {
            inWater = false;
            return;
        }
        
        // Check if head is in water
        int checkX = (int) Math.floor(x);
        int checkY = (int) Math.floor(y + EYE_HEIGHT - 0.1f);
        int checkZ = (int) Math.floor(z);
        
        Block block = world.getBlock(checkX, checkY, checkZ);
        inWater = (block == Blocks.WATER);
    }
    
    /**
     * Water physics (swimming)
     */
    private void applyWaterPhysics(float delta) {
        // Apply water gravity (slower than air)
        velocityY += WATER_GRAVITY;
        
        if (velocityY < WATER_TERMINAL_VELOCITY) {
            velocityY = WATER_TERMINAL_VELOCITY;
        }
        
        float newY = y + velocityY;
        
        // Water buoyancy - slow descent
        if (glfwGetKey(window, GLFW_KEY_SPACE) != GLFW_PRESS) {
            velocityY *= 0.95f; // Drag in water
        }
        
        y = newY;
        onGround = false;
    }
    
    /**
     * Apply gravity and ground collision
     */
    private void applyPhysics(float delta) {
        velocityY += GRAVITY;
        if (velocityY < TERMINAL_VELOCITY) {
            velocityY = TERMINAL_VELOCITY;
        }
        
        float newY = y + velocityY;
        
        if (velocityY < 0) {
            if (isOnGround(x, newY, z)) {
                newY = (float) Math.floor(newY) + 1.001f;
                velocityY = 0;
                onGround = true;
            } else {
                onGround = false;
            }
        } else {
            if (isCeilingCollision(x, newY, z)) {
                velocityY = 0;
            }
            onGround = false;
        }
        
        y = newY;
    }
    
    /**
     * Move with collision detection
     */
    private void moveWithCollision(float dx, float dy, float dz) {
        if (world == null) {
            x += dx;
            y += dy;
            z += dz;
            return;
        }
        
        if (!isColliding(x + dx, y, z)) {
            x += dx;
        }
        
        if (!isColliding(x, y, z + dz)) {
            z += dz;
        }
        
        if (!isColliding(x, y + dy, z)) {
            y += dy;
        }
    }
    
    /**
     * Check collision with solid blocks
     */
    private boolean isColliding(float px, float py, float pz) {
        if (world == null) return false;
        
        float halfWidth = PLAYER_WIDTH / 2;
        
        int minX = (int) Math.floor(px - halfWidth);
        int maxX = (int) Math.floor(px + halfWidth);
        int minY = (int) Math.floor(py);
        int maxY = (int) Math.floor(py + PLAYER_HEIGHT);
        int minZ = (int) Math.floor(pz - halfWidth);
        int maxZ = (int) Math.floor(pz + halfWidth);
        
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    Block block = world.getBlock(bx, by, bz);
                    if (block != null && block.isSolid()) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check if player is on ground
     */
    private boolean isOnGround(float px, float py, float pz) {
        if (world == null) return false;
        
        float halfWidth = PLAYER_WIDTH / 2;
        int checkY = (int) Math.floor(py - 0.1f);
        
        int minX = (int) Math.floor(px - halfWidth);
        int maxX = (int) Math.floor(px + halfWidth);
        int minZ = (int) Math.floor(pz - halfWidth);
        int maxZ = (int) Math.floor(pz + halfWidth);
        
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                Block block = world.getBlock(bx, checkY, bz);
                if (block != null && block.isSolid()) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Check ceiling collision
     */
    private boolean isCeilingCollision(float px, float py, float pz) {
        if (world == null) return false;
        
        float halfWidth = PLAYER_WIDTH / 2;
        int checkY = (int) Math.floor(py + PLAYER_HEIGHT + 0.1f);
        
        int minX = (int) Math.floor(px - halfWidth);
        int maxX = (int) Math.floor(px + halfWidth);
        int minZ = (int) Math.floor(pz - halfWidth);
        int maxZ = (int) Math.floor(pz + halfWidth);
        
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                Block block = world.getBlock(bx, checkY, bz);
                if (block != null && block.isSolid()) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Apply camera transformations
     */
    public void applyTranslations() {
        glRotatef(pitch, 1, 0, 0);
        glRotatef(yaw, 0, 1, 0);
        glTranslatef(-x, -y, -z);
    }
    
    /**
     * Apply underwater visual effects (IMPROVED - Better visibility)
     */
    public void applyUnderwaterEffect() {
        if (inWater) {
            // ✅ IMPROVED: Better underwater fog (less dense)
            glEnable(GL_FOG);
            glFogi(GL_FOG_MODE, GL_LINEAR);
            glFogf(GL_FOG_START, 5.0f);
            glFogf(GL_FOG_END, 35.0f);
            
            // ✅ Lighter blue fog color
            FloatBuffer fogColorBuffer = BufferUtils.createFloatBuffer(4);
            fogColorBuffer.put(0.2f);
            fogColorBuffer.put(0.4f);
            fogColorBuffer.put(0.7f);
            fogColorBuffer.put(1.0f);
            fogColorBuffer.flip();
            glFogfv(GL_FOG_COLOR, fogColorBuffer);
            
            glHint(GL_FOG_HINT, GL_NICEST);
            
            // ✅ Much lighter tint overlay
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            
            glMatrixMode(GL_PROJECTION);
            glPushMatrix();
            glLoadIdentity();
            glOrtho(0, 1, 1, 0, -1, 1);
            
            glMatrixMode(GL_MODELVIEW);
            glPushMatrix();
            glLoadIdentity();
            
            glDisable(GL_DEPTH_TEST);
            glColor4f(0.0f, 0.25f, 0.55f, 0.15f);
            glBegin(GL_QUADS);
            glVertex2f(0, 0);
            glVertex2f(1, 0);
            glVertex2f(1, 1);
            glVertex2f(0, 1);
            glEnd();
            glEnable(GL_DEPTH_TEST);
            
            glMatrixMode(GL_PROJECTION);
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
            glPopMatrix();
            
            glColor3f(1, 1, 1);
            glDisable(GL_BLEND);
        } else {
            // Restore normal fog
            if (Settings.ENABLE_FOG) {
                glEnable(GL_FOG);
                glFogi(GL_FOG_MODE, GL_EXP2);
                glFogf(GL_FOG_DENSITY, Settings.FOG_DENSITY);
            }
        }
    }
    
    /**
     * Get forward direction vector (for raycasting)
     */
    public float[] getForwardVector() {
        float pitchRad = (float) Math.toRadians(pitch);
        float yawRad = (float) Math.toRadians(yaw);
        
        return new float[]{
            (float) (Math.cos(pitchRad) * Math.sin(yawRad)),
            (float) (-Math.sin(pitchRad)),
            (float) (Math.cos(pitchRad) * -Math.cos(yawRad))
        };
    }
    
    // ========== GETTERS ==========
    
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public float getPitch() { return pitch; }
    public float getYaw() { return yaw; }
    public boolean isFlying() { return isFlying; }
    public boolean isOnGround() { return onGround; }
    public boolean isInWater() { return inWater; }
    
    // ========== SETTERS ==========
    
    public void setPosition(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    /**
     * Toggle flying mode (creative/spectator only)
     */
    public void toggleFlying() {
        if (gameMode.canFly()) {
            isFlying = !isFlying;
            if (isFlying) {
                velocityY = 0;
            }
            System.out.println("Flying: " + (isFlying ? "ON" : "OFF"));
        } else {
            System.out.println("Cannot fly in " + gameMode.getName() + " mode!");
        }
    }
}