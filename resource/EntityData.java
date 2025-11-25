package com.mineshaft.resource;

/**
 * POJO for loading entity data from JSON.
 */
public class EntityData {
    public String id;
    public EntityAttributes attributes;
    public EntityDimensions dimensions;

    public static class EntityAttributes {
        public double maxHealth = 20.0;
        public double movementSpeed = 0.1;
    }

    public static class EntityDimensions {
        public float width = 0.6f;
        public float height = 1.8f;
    }
}
