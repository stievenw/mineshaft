package com.mineshaft.render;

import com.mineshaft.core.TimeOfDay;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ Sky Renderer with 3D Cube Sun & Moon
 * - Sun and Moon rendered as 3D cubes with 6 faces
 * - Rotating cubes for dynamic effect
 * - Fixed celestial positioning
 */
public class SkyRenderer {
    
    private TimeOfDay timeOfDay;
    private float cloudOffsetX = 0.0f;
    private float cloudOffsetZ = 0.0f;
    
    // Cloud settings
    private static final float CLOUD_HEIGHT = 128.0f;
    private static final float CLOUD_SPEED_X = 0.15f;
    private static final float CLOUD_SPEED_Z = 0.10f;
    private static final int CLOUD_RENDER_DISTANCE = 16;
    private static final float CLOUD_THICKNESS = 4.0f;
    private static final float CLOUD_ALPHA = 0.85f;
    
    // Celestial settings
    private static final float SUN_SIZE_MIN = 8.0f;
    private static final float SUN_SIZE_MAX = 20.0f;
    private static final float MOON_SIZE_MIN = 6.0f;
    private static final float MOON_SIZE_MAX = 14.0f;
    private static final float CELESTIAL_DISTANCE = 100.0f;
    private static final int STAR_COUNT = 600;
    
    // Cache
    private Map<Long, CloudData> cloudCache = new HashMap<>();
    private static final int MAX_CACHE_SIZE = 10000;
    
    // Display lists
    private int starsDisplayList = -1;
    
    // Cached values
    private float sunY = 0;
    private float sunZ = 0;
    private float moonY = 0;
    private float moonZ = 0;
    private float currentSunSize = SUN_SIZE_MAX;
    private float currentMoonSize = MOON_SIZE_MAX;
    private int frameCounter = 0;
    private float cubeRotation = 0.0f; // For cube rotation animation
    
    public SkyRenderer(TimeOfDay timeOfDay) {
        this.timeOfDay = timeOfDay;
        initializeStarsList();
    }
    
    private void initializeStarsList() {
        starsDisplayList = glGenLists(1);
        glNewList(starsDisplayList, GL_COMPILE);
        renderStarsGeometry();
        glEndList();
    }
    
    public void renderSky(float playerX, float playerY, float playerZ) {
        // Update positions every 3 frames
        frameCounter++;
        if (frameCounter % 3 == 0) {
            updateCelestialPositions();
        }
        
        glPushMatrix();
        
        glDepthMask(false);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_FOG);
        glDisable(GL_CULL_FACE);
        
        glTranslatef(playerX, playerY, playerZ);
        
        // Stars
        if (timeOfDay.getStarBrightness() > 0) {
            float starBrightness = timeOfDay.getStarBrightness();
            glColor4f(starBrightness, starBrightness, starBrightness, starBrightness);
            glCallList(starsDisplayList);
            glColor4f(1, 1, 1, 1);
        }
        
        // Sun (3D Cube)
        if (timeOfDay.isSunVisible()) {
            glPushMatrix();
            glTranslatef(0, sunY, sunZ);
            
            // Rotate cube for dynamic effect
            glRotatef(cubeRotation, 1, 1, 0);
            
            // Dynamic scaling
            float scale = currentSunSize / SUN_SIZE_MAX;
            glScalef(scale, scale, scale);
            
            renderSunCube();
            glPopMatrix();
        }
        
        // Moon (3D Cube)
        if (timeOfDay.isMoonVisible()) {
            glPushMatrix();
            glTranslatef(0, moonY, moonZ);
            
            // Rotate cube slower than sun
            glRotatef(cubeRotation * 0.5f, 0, 1, 1);
            
            // Dynamic scaling
            float scale = currentMoonSize / MOON_SIZE_MAX;
            glScalef(scale, scale, scale);
            
            renderMoonCube();
            glPopMatrix();
        }
        
        glEnable(GL_CULL_FACE);
        glEnable(GL_FOG);
        glDepthMask(true);
        
        glPopMatrix();
        
        // Clouds
        renderCloudsOptimized(playerX, playerZ);
    }
    
    private void updateCelestialPositions() {
        long time = timeOfDay.getTimeOfDay();
        
        float timeNormalized = time / 24000.0f;
        float angleRad = timeNormalized * (float)(Math.PI * 2.0);
        
        sunY = (float)(Math.sin(angleRad)) * CELESTIAL_DISTANCE;
        sunZ = (float)(Math.cos(angleRad)) * CELESTIAL_DISTANCE;
        
        moonY = -sunY;
        moonZ = -sunZ;
        
        float sunHeightFactor = Math.abs(sunY) / CELESTIAL_DISTANCE;
        currentSunSize = SUN_SIZE_MAX - (sunHeightFactor * (SUN_SIZE_MAX - SUN_SIZE_MIN));
        
        float moonHeightFactor = Math.abs(moonY) / CELESTIAL_DISTANCE;
        currentMoonSize = MOON_SIZE_MAX - (moonHeightFactor * (MOON_SIZE_MAX - MOON_SIZE_MIN));
    }
    
    /**
     * ✅ NEW: Render Sun as 3D Cube with 6 faces
     */
    private void renderSunCube() {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        
        // Core sun cube (brightest)
        glColor4f(1.0f, 1.0f, 0.95f, 1.0f);
        renderCube(SUN_SIZE_MAX);
        
        // Inner glow cube
        glColor4f(1.0f, 0.95f, 0.7f, 0.5f);
        renderCube(SUN_SIZE_MAX * 1.4f);
        
        // Outer glow cube
        glColor4f(1.0f, 0.8f, 0.5f, 0.2f);
        renderCube(SUN_SIZE_MAX * 1.8f);
        
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_BLEND);
    }
    
    /**
     * ✅ NEW: Render Moon as 3D Cube with 6 faces
     */
    private void renderMoonCube() {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        // Base moon cube
        glColor4f(0.85f, 0.85f, 0.9f, 1.0f);
        renderCube(MOON_SIZE_MAX);
        
        // Moon phase shadow overlay
        int phase = timeOfDay.getMoonPhase();
        if (phase > 0 && phase < 8) {
            glColor4f(0.05f, 0.05f, 0.1f, 0.7f);
            renderMoonPhaseCube(phase);
        }
        
        // Subtle glow
        glColor4f(0.7f, 0.7f, 0.8f, 0.3f);
        renderCube(MOON_SIZE_MAX * 1.3f);
        
        glDisable(GL_BLEND);
    }
    
    /**
     * ✅ NEW: Helper method to render a cube with 6 faces
     */
    private void renderCube(float size) {
        float s = size;
        
        glBegin(GL_QUADS);
        
        // Front face (Z+)
        glVertex3f(-s, -s, s);
        glVertex3f(s, -s, s);
        glVertex3f(s, s, s);
        glVertex3f(-s, s, s);
        
        // Back face (Z-)
        glVertex3f(-s, -s, -s);
        glVertex3f(-s, s, -s);
        glVertex3f(s, s, -s);
        glVertex3f(s, -s, -s);
        
        // Top face (Y+)
        glVertex3f(-s, s, -s);
        glVertex3f(-s, s, s);
        glVertex3f(s, s, s);
        glVertex3f(s, s, -s);
        
        // Bottom face (Y-)
        glVertex3f(-s, -s, -s);
        glVertex3f(s, -s, -s);
        glVertex3f(s, -s, s);
        glVertex3f(-s, -s, s);
        
        // Right face (X+)
        glVertex3f(s, -s, -s);
        glVertex3f(s, s, -s);
        glVertex3f(s, s, s);
        glVertex3f(s, -s, s);
        
        // Left face (X-)
        glVertex3f(-s, -s, -s);
        glVertex3f(-s, -s, s);
        glVertex3f(-s, s, s);
        glVertex3f(-s, s, -s);
        
        glEnd();
    }
    
    /**
     * ✅ FIXED: Removed unused variable 'coverage' + simplified moon phase rendering
     */
    private void renderMoonPhaseCube(int phase) {
        float s = MOON_SIZE_MAX;
        
        glBegin(GL_QUADS);
        
        // Apply shadow based on phase (simplified)
        switch (phase) {
            case 1: // Waxing Crescent
            case 2: // First Quarter
            case 3: // Waxing Gibbous
                // Shadow on right side
                // Right face
                glVertex3f(s * 0.5f, -s, -s);
                glVertex3f(s, -s, -s);
                glVertex3f(s, s, -s);
                glVertex3f(s * 0.5f, s, -s);
                
                glVertex3f(s * 0.5f, -s, s);
                glVertex3f(s, -s, s);
                glVertex3f(s, s, s);
                glVertex3f(s * 0.5f, s, s);
                break;
                
            case 4: // Full Moon (fully shadowed)
                renderCube(s);
                break;
                
            case 5: // Waning Gibbous
            case 6: // Last Quarter
            case 7: // Waning Crescent
                // Shadow on left side
                // Left face
                glVertex3f(-s, -s, -s);
                glVertex3f(-s * 0.5f, -s, -s);
                glVertex3f(-s * 0.5f, s, -s);
                glVertex3f(-s, s, -s);
                
                glVertex3f(-s, -s, s);
                glVertex3f(-s * 0.5f, -s, s);
                glVertex3f(-s * 0.5f, s, s);
                glVertex3f(-s, s, s);
                break;
        }
        
        glEnd();
    }
    
    private void renderStarsGeometry() {
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        
        glPointSize(2.0f);
        glBegin(GL_POINTS);
        
        java.util.Random rand = new java.util.Random(10842);
        
        for (int i = 0; i < STAR_COUNT; i++) {
            float clusterX = (rand.nextFloat() - 0.5f) * 400;
            float clusterY = rand.nextFloat() * 100 + 50;
            float clusterZ = (rand.nextFloat() - 0.5f) * 400;
            
            int regionHash = (int)(clusterX / 50) * 374761393 + (int)(clusterZ / 50) * 668265263;
            if ((regionHash & 0xFF) < 80) continue;
            
            float x = clusterX + (rand.nextFloat() - 0.5f) * 20;
            float y = clusterY + (rand.nextFloat() - 0.5f) * 10;
            float z = clusterZ + (rand.nextFloat() - 0.5f) * 20;
            
            float brightness = 0.4f + rand.nextFloat() * 0.6f;
            glColor4f(1.0f, 1.0f, 1.0f, brightness);
            glVertex3f(x, y, z);
        }
        
        glEnd();
        
        glPointSize(3.0f);
        glBegin(GL_POINTS);
        
        rand = new java.util.Random(54321);
        
        for (int i = 0; i < 50; i++) {
            float x = (rand.nextFloat() - 0.5f) * 400;
            float y = rand.nextFloat() * 100 + 50;
            float z = (rand.nextFloat() - 0.5f) * 400;
            
            glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
            glVertex3f(x, y, z);
        }
        
        glEnd();
        
        glPointSize(1.0f);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDisable(GL_BLEND);
    }
    
    private void renderCloudsOptimized(float playerX, float playerZ) {
        glPushMatrix();
        
        glEnable(GL_DEPTH_TEST);
        glDepthMask(false);
        glDepthFunc(GL_LEQUAL);
        
        glDisable(GL_CULL_FACE);
        glDisable(GL_TEXTURE_2D);
        
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        int cellSize = 12;
        
        float adjustedX = playerX + cloudOffsetX;
        float adjustedZ = playerZ + cloudOffsetZ;
        
        int gridX = (int) Math.floor(adjustedX / cellSize) * cellSize;
        int gridZ = (int) Math.floor(adjustedZ / cellSize) * cellSize;
        
        int distance = CLOUD_RENDER_DISTANCE;
        
        float brightness = Math.max(0.6f, timeOfDay.getBrightness());
        glColor4f(brightness, brightness, brightness, CLOUD_ALPHA);
        
        glBegin(GL_QUADS);
        for (int x = -distance; x <= distance; x++) {
            for (int z = -distance; z <= distance; z++) {
                float worldX = gridX + (x * cellSize);
                float worldZ = gridZ + (z * cellSize);
                
                CloudData cloud = getCloudDataCached(worldX, worldZ);
                if (cloud.exists) {
                    // Top
                    glVertex3f(worldX, CLOUD_HEIGHT + CLOUD_THICKNESS, worldZ);
                    glVertex3f(worldX + cloud.size, CLOUD_HEIGHT + CLOUD_THICKNESS, worldZ);
                    glVertex3f(worldX + cloud.size, CLOUD_HEIGHT + CLOUD_THICKNESS, worldZ + cloud.size);
                    glVertex3f(worldX, CLOUD_HEIGHT + CLOUD_THICKNESS, worldZ + cloud.size);
                    
                    // Bottom
                    glVertex3f(worldX, CLOUD_HEIGHT, worldZ);
                    glVertex3f(worldX, CLOUD_HEIGHT, worldZ + cloud.size);
                    glVertex3f(worldX + cloud.size, CLOUD_HEIGHT, worldZ + cloud.size);
                    glVertex3f(worldX + cloud.size, CLOUD_HEIGHT, worldZ);
                    
                    // North
                    glVertex3f(worldX, CLOUD_HEIGHT, worldZ);
                    glVertex3f(worldX + cloud.size, CLOUD_HEIGHT, worldZ);
                    glVertex3f(worldX + cloud.size, CLOUD_HEIGHT + CLOUD_THICKNESS, worldZ);
                    glVertex3f(worldX, CLOUD_HEIGHT + CLOUD_THICKNESS, worldZ);
                    
                    // South
                    glVertex3f(worldX, CLOUD_HEIGHT, worldZ + cloud.size);
                    glVertex3f(worldX, CLOUD_HEIGHT + CLOUD_THICKNESS, worldZ + cloud.size);
                    glVertex3f(worldX + cloud.size, CLOUD_HEIGHT + CLOUD_THICKNESS, worldZ + cloud.size);
                    glVertex3f(worldX + cloud.size, CLOUD_HEIGHT, worldZ + cloud.size);
                    
                    // East
                    glVertex3f(worldX + cloud.size, CLOUD_HEIGHT, worldZ);
                    glVertex3f(worldX + cloud.size, CLOUD_HEIGHT, worldZ + cloud.size);
                    glVertex3f(worldX + cloud.size, CLOUD_HEIGHT + CLOUD_THICKNESS, worldZ + cloud.size);
                    glVertex3f(worldX + cloud.size, CLOUD_HEIGHT + CLOUD_THICKNESS, worldZ);
                    
                    // West
                    glVertex3f(worldX, CLOUD_HEIGHT, worldZ);
                    glVertex3f(worldX, CLOUD_HEIGHT + CLOUD_THICKNESS, worldZ);
                    glVertex3f(worldX, CLOUD_HEIGHT + CLOUD_THICKNESS, worldZ + cloud.size);
                    glVertex3f(worldX, CLOUD_HEIGHT, worldZ + cloud.size);
                }
            }
        }
        glEnd();
        
        glDisable(GL_BLEND);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_CULL_FACE);
        glDepthFunc(GL_LESS);
        glDepthMask(true);
        
        glPopMatrix();
    }
    
    private static class CloudData {
        boolean exists;
        float size;
        
        CloudData(boolean exists, float size) {
            this.exists = exists;
            this.size = size;
        }
    }
    
    private CloudData getCloudDataCached(float x, float z) {
        long key = ((long)(x / 12) << 32) | ((long)(z / 12) & 0xFFFFFFFFL);
        
        CloudData cached = cloudCache.get(key);
        if (cached != null) return cached;
        
        if (cloudCache.size() > MAX_CACHE_SIZE) {
            cloudCache.clear();
        }
        
        CloudData data = getCloudData(x, z);
        cloudCache.put(key, data);
        return data;
    }
    
    private CloudData getCloudData(float x, float z) {
        int ix = (int) Math.floor(x / 12);
        int iz = (int) Math.floor(z / 12);
        
        int regionX = ix / 8;
        int regionZ = iz / 8;
        float regionDensity = hash(regionX, regionZ, 999999);
        
        if (regionDensity < -0.2f) {
            return new CloudData(false, 0);
        }
        
        float localNoise = smoothNoise(ix, iz, 123456);
        
        float threshold;
        if (regionDensity > 0.5f) {
            threshold = 0.2f;
        } else if (regionDensity > 0.0f) {
            threshold = 0.4f;
        } else {
            threshold = 0.6f;
        }
        
        if (localNoise < threshold) {
            return new CloudData(false, 0);
        }
        
        float sizeNoise = hash(ix + 5000, iz + 5000, 111111);
        float size = (sizeNoise < -0.3f) ? 8.0f : ((sizeNoise < 0.3f) ? 12.0f : 16.0f);
        
        return new CloudData(true, size);
    }
    
    private float smoothNoise(int x, int z, int seed) {
        float corners = (hash(x - 1, z - 1, seed) + hash(x + 1, z - 1, seed) + 
                        hash(x - 1, z + 1, seed) + hash(x + 1, z + 1, seed)) / 16.0f;
        float sides = (hash(x - 1, z, seed) + hash(x + 1, z, seed) + 
                      hash(x, z - 1, seed) + hash(x, z + 1, seed)) / 8.0f;
        float center = hash(x, z, seed) / 4.0f;
        return corners + sides + center;
    }
    
    private float hash(int x, int z, int seed) {
        int n = x + z * 57 + seed * 131;
        n = (n << 13) ^ n;
        return (1.0f - ((n * (n * n * 15731 + 789221) + 1376312589) & 0x7fffffff) / 1073741824.0f);
    }
    
    /**
     * ✅ Update with cube rotation animation
     */
    public void update(float deltaTime) {
        cloudOffsetX += CLOUD_SPEED_X * deltaTime;
        cloudOffsetZ += CLOUD_SPEED_Z * deltaTime;
        
        if (cloudOffsetX > 1000.0f) cloudOffsetX -= 1000.0f;
        if (cloudOffsetZ > 1000.0f) cloudOffsetZ -= 1000.0f;
        
        // Animate cube rotation
        cubeRotation += deltaTime * 2.0f; // Adjust speed as needed
        if (cubeRotation > 360.0f) cubeRotation -= 360.0f;
    }
    
    public void cleanup() {
        if (starsDisplayList != -1) glDeleteLists(starsDisplayList, 1);
        cloudCache.clear();
    }
}