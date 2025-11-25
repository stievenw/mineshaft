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

    protected float prevX, prevY, prevZ;
    protected float lastTickX, lastTickY, lastTickZ;

    public Entity(EntityType<?> type) {
        this.type = type;
    }

    /**
     * Update entity (called every tick)
     */
    public void tick() {
        this.prevX = this.x;
        this.prevY = this.y;
        this.prevZ = this.z;
        this.lastTickX = this.x;
        this.lastTickY = this.y;
        this.lastTickZ = this.z;
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
        this.prevX = x;
        this.prevY = y;
        this.prevZ = z;
        this.lastTickX = x;
        this.lastTickY = y;
        this.lastTickZ = z;
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

    public float getRenderX(float partialTicks) {
        return lastTickX + (x - lastTickX) * partialTicks;
    }

    public float getRenderY(float partialTicks) {
        return lastTickY + (y - lastTickY) * partialTicks;
    }

    public float getRenderZ(float partialTicks) {
        return lastTickZ + (z - lastTickZ) * partialTicks;
    }

    // Getters
    public EntityType<?> getType() {
        return type;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isRemoved() {
        return removed;
    }

    public boolean isOnGround() {
        return onGround;
    }
}