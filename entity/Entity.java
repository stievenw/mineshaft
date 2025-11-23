package com.mineshaft.entity;

/**
 * Base entity class (like Minecraft's Entity)
 * All moving objects in the world (players, mobs, items, etc.)
 */
public abstract class Entity {
    protected EntityType<?> type;
    protected float x, y, z;
    protected float velocityX, velocityY, velocityZ;
    protected float yaw, pitch;
    protected boolean onGround;
    protected boolean removed;
    
    public Entity(EntityType<?> type) {
        this.type = type;
    }
    
    /**
     * Update entity (called every tick)
     */
    public void tick() {
        // Override in subclasses
    }
    
    /**
     * Move entity with collision
     */
    public void move(float dx, float dy, float dz) {
        // Simple movement (collision detection will be added later)
        x += dx;
        y += dy;
        z += dz;
    }
    
    /**
     * Set position
     */
    public void setPosition(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    /**
     * Set rotation
     */
    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }
    
    /**
     * Mark for removal
     */
    public void remove() {
        this.removed = true;
    }
    
    // Getters
    public EntityType<?> getType() { return type; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public boolean isRemoved() { return removed; }
    public boolean isOnGround() { return onGround; }
}