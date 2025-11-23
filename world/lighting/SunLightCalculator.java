package com.mineshaft.world.lighting;

import com.mineshaft.core.TimeOfDay;

/**
 * ✅ Sun Light Calculator - WITH CHANGE DETECTION
 */
public class SunLightCalculator {
    
    private TimeOfDay timeOfDay;
    
    private float sunDirX = 0;
    private float sunDirY = 1;
    private float sunDirZ = 0;
    
    private float sunIntensity = 1.0f;
    private float ambientIntensity = 0.5f;
    
    // ✅ NEW: Track changes for rebuild detection
    private float lastSunDirY = 1;
    private long lastUpdateTime = -1;
    private static final float SIGNIFICANT_CHANGE_THRESHOLD = 0.05f; // ~5 degrees
    
    public SunLightCalculator(TimeOfDay timeOfDay) {
        this.timeOfDay = timeOfDay;
        updateSunDirection();
    }
    
    /**
     * ✅ UPDATED: Returns true if sun direction changed significantly
     */
    public boolean updateSunDirection() {
        long time = timeOfDay.getTimeOfDay();
        
        // ✅ Skip update if time hasn't changed much (optimization)
        if (Math.abs(time - lastUpdateTime) < 50) {
            return false; // No significant change
        }
        
        lastUpdateTime = time;
        
        float timeNormalized = time / 24000.0f;
        float angleRad = timeNormalized * (float)(Math.PI * 2.0);
        
        sunDirX = 0.0f;
        sunDirY = -(float)(Math.sin(angleRad));
        sunDirZ = -(float)(Math.cos(angleRad));
        
        float length = (float)Math.sqrt(sunDirX * sunDirX + sunDirY * sunDirY + sunDirZ * sunDirZ);
        if (length > 0) {
            sunDirX /= length;
            sunDirY /= length;
            sunDirZ /= length;
        }
        
        // ✅ Calculate intensity
        if (sunDirY < 0) {
            // Day time
            sunIntensity = Math.abs(sunDirY);
            ambientIntensity = 0.5f + (sunIntensity * 0.3f);
        } else {
            // Night time
            sunIntensity = 0.0f;
            ambientIntensity = 0.3f;
        }
        
        // ✅ Check if change is significant enough for rebuild
        boolean significantChange = Math.abs(sunDirY - lastSunDirY) > SIGNIFICANT_CHANGE_THRESHOLD;
        
        if (significantChange) {
            lastSunDirY = sunDirY;
        }
        
        return significantChange;
    }
    
    /**
     * ✅ BRIGHTENED: Calculate brightness for a face with softer shadows
     */
    public float calculateFaceBrightness(float normalX, float normalY, float normalZ) {
        float dotProduct = (normalX * -sunDirX) + (normalY * -sunDirY) + (normalZ * -sunDirZ);
        float directLight = Math.max(0.0f, dotProduct);
        
        // Softer shadow falloff
        directLight = (float)Math.pow(directLight, 0.6f);
        
        // Combine direct light with ambient
        float totalLight = ambientIntensity + (directLight * sunIntensity * 0.4f);
        
        // Never darker than 0.45
        return Math.max(0.45f, Math.min(1.0f, totalLight));
    }
    
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
    
    public enum FaceDirection {
        TOP, BOTTOM, NORTH, SOUTH, EAST, WEST
    }
    
    // Getters
    public float getSunDirX() { return sunDirX; }
    public float getSunDirY() { return sunDirY; }
    public float getSunDirZ() { return sunDirZ; }
    public float getSunIntensity() { return sunIntensity; }
    public float getAmbientIntensity() { return ambientIntensity; }
}