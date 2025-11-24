package com.mineshaft.player;

import com.mineshaft.block.Block;
import com.mineshaft.block.Blocks;
import com.mineshaft.core.Settings;
import com.mineshaft.entity.Entity;
// ❌ REMOVED: import com.mineshaft.entity.EntityType;
import com.mineshaft.world.GameMode;
import com.mineshaft.world.World;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Player Entity - Handles all player logic
 * Movement, physics, collision, swimming, inventory
 */
public class Player extends Entity {
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
    
    private final long window;
    private World world;
    private Inventory inventory;
    
    private GameMode gameMode = GameMode.CREATIVE;
    private boolean flying = true;
    private boolean inWater = false;
    private boolean sprinting = false;
    
    public Player(World world, long window) {
        super(null); // EntityType will be implemented later
        this.world = world;
        this.window = window;
        this.inventory = new Inventory();
    }
    
    /**
     * Process player input (movement only, mouse handled by Camera)
     */
    public void processInput(float delta) {
        processKeyboard(delta);
        
        // Check if in water
        checkWaterStatus();
        
        // Apply physics if not flying
        if (!flying && gameMode == GameMode.SURVIVAL) {
            if (inWater) {
                applyWaterPhysics(delta);
            } else {
                applyPhysics(delta);
            }
        }
    }
    
    /**
     * Process keyboard input for movement
     */
    private void processKeyboard(float delta) {
        float speed = Settings.WALK_SPEED * delta;
        
        // Sprint
        if (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS) {
            speed = Settings.SPRINT_SPEED * delta;
            sprinting = true;
        } else {
            sprinting = false;
        }
        
        // Swimming is slower
        if (inWater && !flying) {
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
        if (flying) {
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
            if (isOnGroundCheck(x, newY, z)) {
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
    private boolean isOnGroundCheck(float px, float py, float pz) {
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
     * Toggle flying mode
     */
    public void toggleFlying() {
        if (gameMode.canFly()) {
            flying = !flying;
            if (flying) {
                velocityY = 0;
            }
            System.out.println("Flying: " + (flying ? "ON" : "OFF"));
        } else {
            System.out.println("Cannot fly in " + gameMode.getName() + " mode!");
        }
    }
    
    @Override
    public void tick() {
        // Called every game tick
        // Physics already applied in processInput
    }
    
    // ========== GETTERS ==========
    
    public float getEyeY() {
        return y + EYE_HEIGHT;
    }
    
    public GameMode getGameMode() {
        return gameMode;
    }
    
    public Inventory getInventory() {
        return inventory;
    }
    
    public boolean isFlying() {
        return flying;
    }
    
    public boolean isInWater() {
        return inWater;
    }
    
    public boolean isSprinting() {
        return sprinting;
    }
    
    public World getWorld() {
        return world;
    }
    
    // ========== SETTERS ==========
    
    public void setGameMode(GameMode mode) {
        this.gameMode = mode;
        
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            flying = true;
            velocityY = 0;
        } else {
            flying = false;
        }
        
        System.out.println("Game mode: " + mode.getName());
    }
    
    public void setWorld(World world) {
        this.world = world;
    }
}