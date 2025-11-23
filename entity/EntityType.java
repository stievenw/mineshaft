package com.mineshaft.entity;

/**
 * Entity type definition (like Minecraft's EntityType)
 * Used for registering and spawning entities
 */
public class EntityType<T extends Entity> {
    private final String name;
    private final EntityFactory<T> factory;
    private final float width;
    private final float height;
    private final boolean fireImmune;
    
    private EntityType(String name, EntityFactory<T> factory, float width, float height, boolean fireImmune) {
        this.name = name;
        this.factory = factory;
        this.width = width;
        this.height = height;
        this.fireImmune = fireImmune;
    }
    
    /**
     * Create entity instance
     */
    public T create() {
        return factory.create(this);
    }
    
    // Getters
    public String getName() { return name; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public boolean isFireImmune() { return fireImmune; }
    
    /**
     * Builder for entity types
     */
    public static class Builder<T extends Entity> {
        private final String name;
        private final EntityFactory<T> factory;
        private float width = 0.6f;
        private float height = 1.8f;
        private boolean fireImmune = false;
        
        public Builder(String name, EntityFactory<T> factory) {
            this.name = name;
            this.factory = factory;
        }
        
        public Builder<T> size(float width, float height) {
            this.width = width;
            this.height = height;
            return this;
        }
        
        public Builder<T> fireImmune() {
            this.fireImmune = true;
            return this;
        }
        
        public EntityType<T> build() {
            return new EntityType<>(name, factory, width, height, fireImmune);
        }
    }
    
    /**
     * Factory interface for creating entities
     */
    @FunctionalInterface
    public interface EntityFactory<T extends Entity> {
        T create(EntityType<T> type);
    }
}