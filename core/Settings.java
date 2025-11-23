package com.mineshaft.core;

public class Settings {
    // Version
    public static final String VERSION = "Alpha v0.8 - Water & Physics Update";
    public static final String FULL_TITLE = "Mineshaft " + VERSION;
    
    // Display
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;
    public static final boolean VSYNC = false;
    public static final int TARGET_FPS = 0;
    public static final boolean SHOW_FPS = true;
    
    // World
    public static final int WORLD_HEIGHT = 128;
    public static final int SEA_LEVEL = 63;
    public static final long WORLD_SEED = 12345678L;
    
    // Rendering
    public static final float FOV = 70.0f;
    public static final float NEAR_PLANE = 0.1f;
    public static final float FAR_PLANE = 256.0f;
    public static final boolean ENABLE_FOG = true;
    public static final float FOG_DENSITY = 0.012f;
    
    // ✅ NEW: Lighting settings
    public static final float GAMMA = 1.2f; // Brightness gamma (1.0 = normal, 1.5 = brighter)
    public static final float BRIGHTNESS_BOOST = 0.15f; // Additional brightness (0.0 - 0.3)
    public static final float MIN_BRIGHTNESS = 0.35f; // Minimum world brightness (0.0 - 0.5)
    
    // Camera
    public static final float WALK_SPEED = 4.317f;
    public static final float SPRINT_SPEED = 5.612f;
    public static final float MOUSE_SENSITIVITY = 0.15f;
    
    // Game
    public static final int TARGET_TPS = 20;
    public static final boolean DEBUG_MODE = false;

    // ✅ NEW: Texture settings
    public static final boolean TEXTURE_FILTERING = false; // false = pixelated (NEAREST), true = smooth (LINEAR)
    public static final boolean MIPMAPS_ENABLED = false;   // Enable mipmaps for distant textures

}