package com.mineshaft.player;

import com.mineshaft.block.Block;
import com.mineshaft.block.Blocks;
import com.mineshaft.core.Settings;
import com.mineshaft.entity.Entity;
import com.mineshaft.world.GameMode;
import com.mineshaft.world.World;

import static org.lwjgl.glfw.GLFW.*;

/**
 * ✅ FIXED - Perfect Minecraft-like movement
 * - No glitchy movement on land
 * - Smooth water surface detection (no camera jitter)
 */
public class Player extends Entity {
    private static final float PLAYER_HEIGHT = 1.8f;
    private static final float PLAYER_WIDTH = 0.6f;
    private static final float EYE_HEIGHT = 1.62f;
    
    // ✅ Physics values
    private static final float GRAVITY = -0.08f;
    private static final float WATER_GRAVITY = -0.05f;
    private static final float JUMP_STRENGTH = 0.50f;
    private static final float WATER_SWIM_SPEED = 0.20f;
    private static final float WATER_GROUND_SWIM_BOOST = 0.08f;
    private static final float TERMINAL_VELOCITY = -3.92f;
    private static final float WATER_TERMINAL_VELOCITY = -0.10f;
    private static final float MAX_WATER_UP_SPEED = 0.20f;
    
    private static final float STEP_HEIGHT = 0.6f;
    
    // ✅ FIXED - Better ground detection threshold
    private static final float GROUND_EPSILON = 0.001f; // Prevent micro-movements
    
    private final long window;
    private World world;
    private Inventory inventory;
    
    private GameMode gameMode = GameMode.CREATIVE;
    private boolean flying = true;
    private boolean inWater = false;
    private boolean headInWater = false; // ✅ NEW - Separate head detection
    private boolean sprinting = false;
    
    public Player(World world, long window) {
        super(null);
        this.world = world;
        this.window = window;
        this.inventory = new Inventory();
    }
    
    /**
     * ✅ Process movement EVERY FRAME (smooth)
     */
    public void processMovementInput(float frameDelta) {
        processMovement(frameDelta);
    }
    
    /**
     * ✅ Physics tick at 20 TPS
     */
    @Override
    public void tick() {
        checkWaterStatus();
        
        if (!flying && gameMode == GameMode.SURVIVAL) {
            if (inWater) {
                applyWaterPhysics(1.0f / Settings.TARGET_TPS);
            } else {
                applyPhysics(1.0f / Settings.TARGET_TPS);
            }
        }
    }
    
    /**
     * ✅ FIXED - Process all movement input
     */
    private void processMovement(float delta) {
        float speed = Settings.WALK_SPEED * delta;
        
        if (glfwGetKey(window, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS) {
            speed = Settings.SPRINT_SPEED * delta;
            sprinting = true;
        } else {
            sprinting = false;
        }
        
        if (inWater && !flying) {
            speed *= 0.5f;
        }
        
        if (gameMode == GameMode.SPECTATOR) {
            speed *= 2.0f;
        }
        
        // ✅ Horizontal movement (WASD)
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
        
        if (moveX != 0 || moveZ != 0) {
            if (gameMode == GameMode.SPECTATOR) {
                x += moveX;
                z += moveZ;
            } else {
                moveWithCollisionAndStep(moveX, moveZ);
            }
        }
        
        // ✅ FIXED - Vertical movement handling
        if (flying) {
            // Flying mode
            float moveY = 0;
            
            if (glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS) {
                moveY = speed * 2;
            }
            if (glfwGetKey(window, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS) {
                moveY = -speed * 2;
            }
            
            if (moveY != 0) {
                if (gameMode == GameMode.SPECTATOR) {
                    y += moveY;
                } else {
                    if (!isColliding(x, y + moveY, z)) {
                        y += moveY;
                    }
                }
            }
        } else {
            // ✅ FIXED - Survival mode jump/swim
            boolean spaceDown = glfwGetKey(window, GLFW_KEY_SPACE) == GLFW_PRESS;
            
            if (inWater) {
                // ✅ IN WATER - Swim mechanics
                if (spaceDown) {
                    if (onGround) {
                        // Gentle boost from ground
                        velocityY = Math.max(velocityY, WATER_GROUND_SWIM_BOOST);
                    } else {
                        // Normal swim
                        velocityY = WATER_SWIM_SPEED;
                    }
                }
            } else {
                // ✅ ON LAND - Spam jump when holding space
                if (spaceDown && onGround) {
                    velocityY = JUMP_STRENGTH;
                    onGround = false;
                }
            }
        }
    }
    
    /**
     * ✅ Smooth auto-step with better collision
     */
    private void moveWithCollisionAndStep(float dx, float dz) {
        if (world == null) {
            x += dx;
            z += dz;
            return;
        }
        
        float newX = x + dx;
        float newZ = z + dz;
        
        // ✅ Try direct move first (no collision)
        if (!isColliding(newX, y, newZ)) {
            x = newX;
            z = newZ;
            return;
        }
        
        // ✅ Try auto-step if on ground or in water
        if (onGround || inWater) {
            for (float step = 0.1f; step <= STEP_HEIGHT; step += 0.1f) {
                float testY = y + step;
                
                if (!isColliding(newX, testY, newZ)) {
                    x = newX;
                    z = newZ;
                    y = testY;
                    return;
                }
            }
        }
        
        // ✅ If step failed, try sliding along walls (separate X and Z)
        if (!isColliding(newX, y, z)) {
            x = newX;
        }
        
        if (!isColliding(x, y, newZ)) {
            z = newZ;
        }
    }
    
    /**
     * ✅ FIXED - Better water detection (check body AND head)
     */
    private void checkWaterStatus() {
        if (world == null) {
            inWater = false;
            headInWater = false;
            return;
        }
        
        int checkX = (int) Math.floor(x);
        int checkZ = (int) Math.floor(z);
        
        // ✅ Check head (eye level for camera)
        int headY = (int) Math.floor(y + EYE_HEIGHT - 0.1f);
        Block headBlock = world.getBlock(checkX, headY, checkZ);
        headInWater = (headBlock == Blocks.WATER);
        
        // ✅ Check body (chest level - more stable for swimming)
        int bodyY = (int) Math.floor(y + PLAYER_HEIGHT * 0.5f);
        Block bodyBlock = world.getBlock(bodyY, bodyY, checkZ);
        
        // ✅ Check feet
        int feetY = (int) Math.floor(y + 0.4f);
        Block feetBlock = world.getBlock(checkX, feetY, checkZ);
        
        // ✅ Player is "in water" if body or feet are submerged
        inWater = (bodyBlock == Blocks.WATER) || (feetBlock == Blocks.WATER);
    }
    
    /**
     * ✅ FIXED - Smooth water physics
     */
    private void applyWaterPhysics(float delta) {
        // ✅ Always apply water gravity
        velocityY += WATER_GRAVITY;
        
        // ✅ Terminal velocity
        if (velocityY < WATER_TERMINAL_VELOCITY) {
            velocityY = WATER_TERMINAL_VELOCITY;
        }
        
        // ✅ Cap maximum upward speed
        if (velocityY > MAX_WATER_UP_SPEED) {
            velocityY = MAX_WATER_UP_SPEED;
        }
        
        float newY = y + velocityY;
        
        // ✅ Ground detection
        if (velocityY <= 0) {
            if (isOnGroundCheck(x, newY, z)) {
                int groundBlockY = (int) Math.floor(newY);
                newY = groundBlockY + 1.0f;
                
                // ✅ Stop velocity when on ground
                velocityY = 0;
                onGround = true;
            } else {
                onGround = false;
            }
        } else {
            // ✅ Check ceiling
            if (isCeilingCollision(x, newY, z)) {
                velocityY = 0;
            }
            onGround = false;
        }
        
        y = newY;
    }
    
    /**
     * ✅ FIXED - Stable ground physics (no jitter)
     */
    private void applyPhysics(float delta) {
        // ✅ Don't apply gravity if firmly on ground
        if (onGround && Math.abs(velocityY) < GROUND_EPSILON) {
            velocityY = 0;
        } else {
            velocityY += GRAVITY;
        }
        
        // Terminal velocity
        if (velocityY < TERMINAL_VELOCITY) {
            velocityY = TERMINAL_VELOCITY;
        }
        
        float newY = y + velocityY;
        
        if (velocityY <= 0) {
            if (isOnGroundCheck(x, newY, z)) {
                // ✅ FIXED - Snap to exact ground position
                int groundBlockY = (int) Math.floor(newY);
                y = groundBlockY + 1.0f;
                velocityY = 0;
                onGround = true;
                return; // ✅ Early return to prevent micro-adjustments
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
    
    private boolean isColliding(float px, float py, float pz) {
        if (world == null) return false;
        
        float halfWidth = PLAYER_WIDTH / 2;
        
        int minX = (int) Math.floor(px - halfWidth);
        int maxX = (int) Math.floor(px + halfWidth);
        int minY = (int) Math.floor(py);
        int maxY = (int) Math.floor(py + PLAYER_HEIGHT - GROUND_EPSILON);
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
     * ✅ FIXED - More precise ground check
     */
    private boolean isOnGroundCheck(float px, float py, float pz) {
        if (world == null) return false;
        
        float halfWidth = PLAYER_WIDTH / 2;
        
        // ✅ Check slightly below feet
        int checkY = (int) Math.floor(py - 0.01f);
        
        int minX = (int) Math.floor(px - halfWidth + 0.01f);
        int maxX = (int) Math.floor(px + halfWidth - 0.01f);
        int minZ = (int) Math.floor(pz - halfWidth + 0.01f);
        int maxZ = (int) Math.floor(pz + halfWidth - 0.01f);
        
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
    
    private boolean isCeilingCollision(float px, float py, float pz) {
        if (world == null) return false;
        
        float halfWidth = PLAYER_WIDTH / 2;
        int checkY = (int) Math.floor(py + PLAYER_HEIGHT + 0.01f);
        
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
    
    public float getEyeY() { return y + EYE_HEIGHT; }
    public GameMode getGameMode() { return gameMode; }
    public Inventory getInventory() { return inventory; }
    public boolean isFlying() { return flying; }
    public boolean isInWater() { return inWater; }
    public boolean isHeadInWater() { return headInWater; } // ✅ NEW - For camera/fog effects
    public boolean isSprinting() { return sprinting; }
    public World getWorld() { return world; }
    
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