package com.mineshaft.core;

/**
 * ✅ COMPLETE v2 - All game settings with camera stabilizer
 * 
 * Organized sections:
 * - Version & Window
 * - World & Terrain
 * - Camera & Movement
 * - Camera Stabilizer (NEW)
 * - Rendering & Graphics
 * - Lighting & Brightness
 * - Physics & Gameplay
 * - Performance
 * - Texture Settings
 * - Debug
 */
public class Settings {
    
    // ================================================================
    // VERSION & WINDOW
    // ================================================================
    
    public static final String VERSION = "Alpha v0.8.1 - Camera Stabilizer";
    public static final String TITLE = "Mineshaft";
    public static final String FULL_TITLE = TITLE + " " + VERSION;
    
    public static int WINDOW_WIDTH = 1280;
    public static int WINDOW_HEIGHT = 720;
    public static boolean VSYNC = false;
    public static final int TARGET_FPS = 0;  // 0 = unlimited
    public static final boolean SHOW_FPS = true;
    
    // ================================================================
    // WORLD & TERRAIN
    // ================================================================
    
    public static final int WORLD_HEIGHT = 128;
    public static final int SEA_LEVEL = 63;
    public static final long WORLD_SEED = 12345678L;
    public static final int RENDER_DISTANCE = 8;  // chunks
    public static final int CHUNK_SIZE = 16;      // blocks per chunk
    
    // ================================================================
    // CAMERA & MOVEMENT
    // ================================================================
    
    public static final float FOV = 70.0f;
    public static final float NEAR_PLANE = 0.1f;
    public static final float FAR_PLANE = 256.0f;
    public static final float MOUSE_SENSITIVITY = 0.15f;
    
    // Movement speeds (blocks per second)
    public static final float WALK_SPEED = 4.317f;     // Minecraft default
    public static final float SPRINT_SPEED = 5.612f;   // Minecraft default
    public static final float FLY_SPEED = 10.92f;      // Creative fly speed
    public static final float SNEAK_SPEED = 1.295f;    // Sneak speed
    
    // ================================================================
    // CAMERA STABILIZER (Zero Jitter System) ✅ NEW
    // ================================================================
    
    /**
     * Enable smooth camera interpolation to eliminate jitter
     * Can be toggled runtime with F3+C
     */
    public static boolean CAMERA_SMOOTHING = true;
    
    /**
     * Camera follow speed (blocks/second interpolation)
     * Higher = faster tracking, lower = smoother but more lag
     * 
     * Recommended values:
     * - 10.0f = Ultra smooth (cinematic)
     * - 20.0f = Balanced (recommended) ⭐
     * - 30.0f = Responsive
     * - 50.0f = Almost instant
     */
    public static final float CAMERA_SMOOTH_SPEED = 20.0f;
    
    /**
     * Vertical smoothing multiplier (applied to Y-axis)
     * Lower = more stable vertical movement
     * 
     * Recommended values:
     * - 0.5f = Very stable (no bobbing)
     * - 0.7f = Balanced (recommended) ⭐
     * - 1.0f = Same as horizontal
     */
    public static final float CAMERA_Y_SMOOTH_MULTIPLIER = 0.7f;
    
    /**
     * Distance threshold for instant snap (prevents micro-jitter)
     * If camera is closer than this to target, snap instantly
     * Don't change unless you know what you're doing
     */
    public static final float CAMERA_MIN_MOVE_THRESHOLD = 0.001f;
    
    // ================================================================
    // RENDERING & GRAPHICS
    // ================================================================
    
    public static final boolean ENABLE_FOG = true;
    public static final float FOG_DENSITY = 0.012f;
    
    // Culling
    public static final boolean FRUSTUM_CULLING = true;
    public static final boolean BACKFACE_CULLING = true;
    
    // Chunk rendering
    public static final int CHUNK_UPDATES_PER_FRAME = 4;
    public static final boolean REBUILD_DIRTY_CHUNKS = true;
    
    // ================================================================
    // LIGHTING & BRIGHTNESS
    // ================================================================
    
    /**
     * Gamma correction (brightness multiplier)
     * 1.0 = normal, 1.5 = brighter, 0.8 = darker
     */
    public static final float GAMMA = 1.2f;
    
    /**
     * Additional brightness boost
     * 0.0 = none, 0.3 = max recommended
     */
    public static final float BRIGHTNESS_BOOST = 0.15f;
    
    /**
     * Minimum world brightness (prevents pure black)
     * 0.0 = complete darkness, 0.5 = always visible
     */
    public static final float MIN_BRIGHTNESS = 0.35f;
    
    // Lighting features
    public static final boolean SMOOTH_LIGHTING = true;
    public static final boolean AMBIENT_OCCLUSION = true;
    public static final int MAX_LIGHT_UPDATES_PER_FRAME = 1000;
    
    // ================================================================
    // PHYSICS & GAMEPLAY
    // ================================================================
    
    public static final int TARGET_TPS = 20;  // Ticks per second (Minecraft standard)
    
    // Player physics
    public static final float GRAVITY = -0.08f;
    public static final float TERMINAL_VELOCITY = -3.92f;
    public static final float JUMP_STRENGTH = 0.50f;
    public static final float PLAYER_HEIGHT = 1.8f;
    public static final float PLAYER_WIDTH = 0.6f;
    public static final float EYE_HEIGHT = 1.62f;
    public static final float STEP_HEIGHT = 0.6f;
    
    // Water physics
    public static final float WATER_GRAVITY = -0.04f;
    public static final float WATER_TERMINAL_VELOCITY = -0.12f;
    public static final float WATER_SWIM_SPEED = 0.20f;
    
    // Reach distance
    public static final float CREATIVE_REACH = 5.0f;
    public static final float SURVIVAL_REACH = 4.5f;
    
    // ================================================================
    // PERFORMANCE
    // ================================================================
    
    // Memory
    public static final int MAX_LOADED_CHUNKS = 1024;
    public static final boolean AGGRESSIVE_CHUNK_UNLOAD = false;
    
    // Threading
    public static final boolean MULTITHREADED_CHUNK_LOADING = true;
    public static final int CHUNK_LOAD_THREADS = 4;
    
    // VBO optimization
    public static final boolean USE_VBO = true;
    public static final boolean DYNAMIC_VBO_UPDATES = true;
    
    // ================================================================
    // TEXTURE SETTINGS
    // ================================================================
    
    /**
     * Texture filtering mode
     * false = pixelated (GL_NEAREST) - Minecraft style ⭐
     * true  = smooth (GL_LINEAR) - Modern style
     */
    public static final boolean TEXTURE_FILTERING = false;
    
    /**
     * Enable mipmaps for distant textures
     * Improves performance and reduces aliasing at distance
     * Requires more VRAM
     */
    public static final boolean MIPMAPS_ENABLED = false;
    
    /**
     * Anisotropic filtering level
     * 1 = off, 16 = max quality
     * Only works if TEXTURE_FILTERING = true
     */
    public static final int ANISOTROPIC_FILTERING = 1;
    
    // ================================================================
    // DEBUG
    // ================================================================
    
    public static final boolean DEBUG_MODE = false;
    public static final boolean DEBUG_CHUNK_LOADING = false;
    public static final boolean DEBUG_LIGHTING = false;
    public static final boolean DEBUG_PHYSICS = false;
    public static final boolean DEBUG_CAMERA = false;
    
    // Debug output
    public static final boolean SHOW_CHUNK_BORDERS = false;
    public static final boolean SHOW_COLLISION_BOXES = false;
    public static final boolean LOG_PERFORMANCE = false;
    
    // ================================================================
    // RUNTIME METHODS
    // ================================================================
    
    /**
     * Update window size (called on window resize)
     */
    public static void updateWindowSize(int width, int height) {
        WINDOW_WIDTH = width;
        WINDOW_HEIGHT = height;
        System.out.println("Window resized: " + width + "x" + height);
    }
    
    /**
     * Toggle VSync (runtime)
     */
    public static void toggleVSync() {
        VSYNC = !VSYNC;
        System.out.println("VSync: " + (VSYNC ? "ON" : "OFF"));
    }
    
    /**
     * Toggle camera smoothing (F3+C)
     */
    public static void toggleCameraSmoothing() {
        CAMERA_SMOOTHING = !CAMERA_SMOOTHING;
        System.out.println("Camera Smoothing: " + (CAMERA_SMOOTHING ? "ON" : "OFF"));
    }
    
    /**
     * Print all settings (debug)
     */
    public static void printSettings() {
        System.out.println("=== GAME SETTINGS ===");
        System.out.println("Version: " + VERSION);
        System.out.println("Resolution: " + WINDOW_WIDTH + "x" + WINDOW_HEIGHT);
        System.out.println("VSync: " + VSYNC);
        System.out.println("Render Distance: " + RENDER_DISTANCE);
        System.out.println("Camera Smoothing: " + CAMERA_SMOOTHING + " (Speed: " + CAMERA_SMOOTH_SPEED + ")");
        System.out.println("FOV: " + FOV);
        System.out.println("Target TPS: " + TARGET_TPS);
        System.out.println("=====================");
    }
    
    // ================================================================
    // CONSTANTS (DO NOT MODIFY)
    // ================================================================
    
    // Block IDs
    public static final int BLOCK_AIR = 0;
    public static final int BLOCK_STONE = 1;
    public static final int BLOCK_GRASS = 2;
    public static final int BLOCK_DIRT = 3;
    public static final int BLOCK_WATER = 8;
    
    // Light levels
    public static final int MAX_LIGHT_LEVEL = 15;
    public static final int MIN_LIGHT_LEVEL = 0;
    public static final int SUNLIGHT_LEVEL = 15;
    public static final int MOONLIGHT_LEVEL = 4;
    
    // Time
    public static final int TICKS_PER_DAY = 24000;      // Minecraft default
    public static final int TICKS_PER_HOUR = 1000;
    public static final int TICKS_PER_MINUTE = 16;
    
    // Math constants
    public static final float PI = 3.14159265359f;
    public static final float DEG_TO_RAD = PI / 180.0f;
    public static final float RAD_TO_DEG = 180.0f / PI;
    
    // ================================================================
    // PRIVATE CONSTRUCTOR (Prevent instantiation)
    // ================================================================
    
    private Settings() {
        throw new AssertionError("Settings class cannot be instantiated");
    }
}