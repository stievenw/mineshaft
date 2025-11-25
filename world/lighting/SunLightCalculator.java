package com.mineshaft.world.lighting;

import com.mineshaft.core.TimeOfDay;

/**
 * ⚡ Sun Light Calculator - Optimized with Skylight Level Support
 * Handles sun direction, intensity, and skylight level calculation
 */
public class SunLightCalculator {
    
    private TimeOfDay timeOfDay;
    
    private float sunDirX = 0;
    private float sunDirY = 1;
    private float sunDirZ = 0;
    
    private float sunIntensity = 1.0f;
    private float ambientIntensity = 0.5f;
    
    // Track changes for rebuild detection
    private float lastSunDirY = 1;
    private long lastUpdateTime = -1;
    private static final float SIGNIFICANT_CHANGE_THRESHOLD = 0.05f;
    
    public SunLightCalculator(TimeOfDay timeOfDay) {
        this.timeOfDay = timeOfDay;
        updateSunDirection();
    }
    
    /**
     * Updates sun direction and returns true if changed significantly
     * @return true if sun moved enough to require chunk rebuilds
     */
    public boolean updateSunDirection() {
        long time = timeOfDay.getTimeOfDay();
        
        // Skip update if time hasn't changed much (performance optimization)
        if (Math.abs(time - lastUpdateTime) < 50) {
            return false;
        }
        
        lastUpdateTime = time;
        
        // Calculate sun position based on time (0-24000)
        float timeNormalized = time / 24000.0f;
        float angleRad = timeNormalized * (float)(Math.PI * 2.0);
        
        sunDirX = 0.0f;
        sunDirY = -(float)(Math.sin(angleRad));
        sunDirZ = -(float)(Math.cos(angleRad));
        
        // Normalize direction vector
        float length = (float)Math.sqrt(sunDirX * sunDirX + sunDirY * sunDirY + sunDirZ * sunDirZ);
        if (length > 0) {
            sunDirX /= length;
            sunDirY /= length;
            sunDirZ /= length;
        }
        
        // Calculate intensity based on sun height
        if (sunDirY < 0) {
            // Day time (sun is above horizon)
            sunIntensity = Math.abs(sunDirY);
            ambientIntensity = 0.5f + (sunIntensity * 0.3f);
        } else {
            // Night time (sun is below horizon)
            sunIntensity = 0.0f;
            ambientIntensity = 0.3f;
        }
        
        // Check if change is significant enough for chunk rebuilds
        boolean significantChange = Math.abs(sunDirY - lastSunDirY) > SIGNIFICANT_CHANGE_THRESHOLD;
        
        if (significantChange) {
            lastSunDirY = sunDirY;
        }
        
        return significantChange;
    }
    
    /**
     * Get skylight level (0-15) based on sun intensity
     * Used by LightingEngine for chunk skylight updates
     * @return skylight level from 4 (night) to 15 (noon)
     */
    public int getSkylightLevel() {
        // Convert sun intensity (0.0-1.0) to Minecraft-style light level (4-15)
        // Minimum level 4 represents moonlight/starlight at night
        
        if (sunIntensity >= 0.9f) {
            // Full daylight (noon) - brightest
            return 15;
            
        } else if (sunIntensity >= 0.6f) {
            // Bright daylight (mid-morning to mid-afternoon)
            // Linear interpolation from 13 to 15
            float t = (sunIntensity - 0.6f) / 0.3f; // 0.0 to 1.0
            return 13 + (int)(t * 2); // 13-15
            
        } else if (sunIntensity >= 0.3f) {
            // Transition period (sunrise/sunset)
            // Linear interpolation from 8 to 13
            float t = (sunIntensity - 0.3f) / 0.3f; // 0.0 to 1.0
            return 8 + (int)(t * 5); // 8-13
            
        } else if (sunIntensity > 0.0f) {
            // Dawn/dusk (very early morning or late evening)
            // Linear interpolation from 4 to 8
            float t = sunIntensity / 0.3f; // 0.0 to 1.0
            return 4 + (int)(t * 4); // 4-8
            
        } else {
            // Night - minimum light from moon and stars
            return 4;
        }
    }
    
    /**
     * Calculate brightness for a face with given normal vector
     * Uses softer shadows for better visual quality
     * @param normalX normal vector X component
     * @param normalY normal vector Y component
     * @param normalZ normal vector Z component
     * @return brightness value (0.45 to 1.0)
     */
    public float calculateFaceBrightness(float normalX, float normalY, float normalZ) {
        // Calculate dot product (cosine of angle between normal and sun direction)
        float dotProduct = (normalX * -sunDirX) + (normalY * -sunDirY) + (normalZ * -sunDirZ);
        float directLight = Math.max(0.0f, dotProduct);
        
        // Apply softer shadow falloff (gamma correction)
        directLight = (float)Math.pow(directLight, 0.6f);
        
        // Combine direct sunlight with ambient light
        float totalLight = ambientIntensity + (directLight * sunIntensity * 0.4f);
        
        // Clamp to reasonable range (never darker than 0.45)
        return Math.max(0.45f, Math.min(1.0f, totalLight));
    }
    
    /**
     * Get pre-calculated brightness for standard cube faces
     * @param face the face direction
     * @return brightness multiplier for that face
     */
    public float getBaseFaceBrightness(FaceDirection face) {
        switch (face) {
            case TOP:
                return calculateFaceBrightness(0, 1, 0);
            case BOTTOM:
                return calculateFaceBrightness(0, -1, 0);
            case NORTH:
                return calculateFaceBrightness(0, 0, -1);
            case SOUTH:
                return calculateFaceBrightness(0, 0, 1);
            case EAST:
                return calculateFaceBrightness(1, 0, 0);
            case WEST:
                return calculateFaceBrightness(-1, 0, 0);
            default:
                return 1.0f;
        }
    }
    
    /**
     * Face direction enum for cube faces
     */
    public enum FaceDirection {
        TOP, BOTTOM, NORTH, SOUTH, EAST, WEST
    }
    
    // === Getters ===
    
    public float getSunDirX() { 
        return sunDirX; 
    }
    
    public float getSunDirY() { 
        return sunDirY; 
    }
    
    public float getSunDirZ() { 
        return sunDirZ; 
    }
    
    public float getSunIntensity() { 
        return sunIntensity; 
    }
    
    public float getAmbientIntensity() { 
        return ambientIntensity; 
    }
    
    /**
     * Check if it's currently daytime
     * @return true if sun is above horizon
     */
    public boolean isDaytime() {
        return sunIntensity > 0.3f;
    }
    
    /**
     * Check if it's currently nighttime
     * @return true if sun is below horizon
     */
    public boolean isNighttime() {
        return sunIntensity <= 0.0f;
    }
}