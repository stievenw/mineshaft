package com.mineshaft.core;

import com.mineshaft.block.Block;
import com.mineshaft.entity.Camera;
import com.mineshaft.input.InputHandler;
import com.mineshaft.player.Inventory;
import com.mineshaft.player.Player;
import com.mineshaft.render.BlockTextures;
import com.mineshaft.render.ChatOverlay;
import com.mineshaft.render.DebugScreen;
import com.mineshaft.render.HUD;
import com.mineshaft.render.SkyRenderer;
import com.mineshaft.render.BlockOutlineRenderer;
import com.mineshaft.render.TextureManager;
import com.mineshaft.util.Screenshot;
import com.mineshaft.world.GameMode;
import com.mineshaft.world.RayCast;
import com.mineshaft.world.World;
import com.mineshaft.world.interaction.BlockInteractionHandler;

import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryUtil.*;

/**
 * ✅ Main game class with smooth mouse & flying movement
 */
public class Game {
    private long window;

    private boolean running = false;
    private World world;
    private Player player;
    private Camera camera;
    private TimeOfDay timeOfDay;
    private Inventory inventory;
    private HUD hud;
    private DebugScreen debugScreen;
    private InputHandler input;
    private ChatOverlay chatOverlay;
    private CommandHandler commandHandler;
    private SkyRenderer skyRenderer;
    private BlockInteractionHandler interactionHandler;
    private BlockOutlineRenderer outlineRenderer;

    private int fps = 0;
    private int tps = 0;
    private int peakFps = 0;

    private boolean vsyncEnabled = Settings.VSYNC;
    private boolean fullscreen = false;
    private boolean guiVisible = true;
    private boolean pauseOnLostFocus = true;

    private boolean renderSky = true;

    private GameMode previousGameMode = GameMode.CREATIVE;

    private static final float REACH_DISTANCE = 5.0f;

    private int cameraMode = 0;

    private double scrollOffset = 0;

    public void start() {
        try {
            System.out.println("===============================================");
            System.out.println("   MINESHAFT - Minecraft-style Voxel Engine");
            System.out.println("   " + Settings.VERSION);
            System.out.println("===============================================");

            initDisplay();
            initGL();
            initGame();

            System.out.println("Game initialized successfully");
            System.out.println("===============================================");
            running = true;
            gameLoop();

        } catch (Exception e) {
            System.err.println("Failed to initialize!");
            e.printStackTrace();
        } finally {
            cleanup();
        }
    }

    private void initDisplay() {
        GLFWErrorCallback.createPrint(System.err).set();

        if (!glfwInit()) {
            throw new IllegalStateException("Unable to initialize GLFW");
        }

        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 2);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 1);

        window = glfwCreateWindow(
                Settings.WINDOW_WIDTH,
                Settings.WINDOW_HEIGHT,
                Settings.FULL_TITLE,
                NULL,
                NULL);

        if (window == NULL) {
            throw new RuntimeException("Failed to create GLFW window");
        }

        setupCallbacks();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer pWidth = stack.mallocInt(1);
            IntBuffer pHeight = stack.mallocInt(1);
            glfwGetWindowSize(window, pWidth, pHeight);

            GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
            if (vidmode != null) {
                glfwSetWindowPos(
                        window,
                        (vidmode.width() - pWidth.get(0)) / 2,
                        (vidmode.height() - pHeight.get(0)) / 2);
            }
        }

        glfwMakeContextCurrent(window);
        glfwSwapInterval(vsyncEnabled ? 1 : 0);
        glfwShowWindow(window);

        GL.createCapabilities();

        glfwSetInputMode(window, GLFW_CURSOR, GLFW_CURSOR_DISABLED);

        System.out.println("Display: " + Settings.WINDOW_WIDTH + "x" + Settings.WINDOW_HEIGHT);
        System.out.println("VSync: " + (vsyncEnabled ? "ON" : "OFF"));
    }

    private void setupCallbacks() {
        glfwSetScrollCallback(window, (w, xoffset, yoffset) -> {
            if (!chatOverlay.isChatOpen()) {
                scrollOffset += yoffset;
            }
        });

        glfwSetFramebufferSizeCallback(window, (w, width, height) -> {
            glViewport(0, 0, width, height);
            updateProjectionMatrix(width, height);

            if (chatOverlay != null) {
                chatOverlay.updateSize(width, height);
            }
        });
    }

    private void updateProjectionMatrix(int width, int height) {
        float aspect = (float) width / (float) height;
        float fov = Settings.FOV;
        float near = Settings.NEAR_PLANE;
        float far = Settings.FAR_PLANE;

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();

        float fH = (float) Math.tan(Math.toRadians(fov) / 2) * near;
        float fW = fH * aspect;
        glFrustum(-fW, fW, -fH, fH, near, far);

        glMatrixMode(GL_MODELVIEW);
    }

    private void initGL() {
        glViewport(0, 0, Settings.WINDOW_WIDTH, Settings.WINDOW_HEIGHT);
        updateProjectionMatrix(Settings.WINDOW_WIDTH, Settings.WINDOW_HEIGHT);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(GL_LEQUAL);

        glEnable(GL_CULL_FACE);
        glCullFace(GL_BACK);

        glShadeModel(GL_SMOOTH);

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        glClearColor(0.5f, 0.7f, 1.0f, 1.0f);

        glEnable(GL_TEXTURE_2D);

        if (Settings.ENABLE_FOG) {
            glEnable(GL_FOG);
            glFogi(GL_FOG_MODE, GL_EXP2);
            glFogf(GL_FOG_DENSITY, Settings.FOG_DENSITY);
            glHint(GL_FOG_HINT, GL_NICEST);
        }

        System.out.println("OpenGL: " + glGetString(GL_VERSION));
        System.out.println("Renderer: " + glGetString(GL_RENDERER));

        BlockTextures.init();
    }

    private void initGame() {
        timeOfDay = new TimeOfDay();
        world = new World(timeOfDay);

        player = new Player(world, window);
        player.setPosition(0, 80, 0);
        player.setGameMode(GameMode.CREATIVE);

        camera = new Camera(player, window);

        inventory = player.getInventory();
        hud = new HUD(window);
        debugScreen = new DebugScreen(window);
        input = new InputHandler(window);

        chatOverlay = new ChatOverlay(window);
        commandHandler = new CommandHandler(world, player, timeOfDay, chatOverlay);

        // Initialize block interaction system
        interactionHandler = new BlockInteractionHandler(world, player, camera, REACH_DISTANCE);

        // Initialize block outline renderer
        outlineRenderer = new BlockOutlineRenderer();

        skyRenderer = new SkyRenderer(timeOfDay);
    }

    /**
     * ✅ FIXED - Smooth flying movement (every frame)
     */
    private void gameLoop() {
        long lastTime = System.nanoTime();
        long lastFrameTime = System.nanoTime();
        long timer = System.currentTimeMillis();

        double nsPerTick = 1000000000.0 / Settings.TARGET_TPS;
        double delta = 0;

        int frames = 0;
        int ticks = 0;

        while (running && !glfwWindowShouldClose(window)) {
            long now = System.nanoTime();
            delta += (now - lastTime) / nsPerTick;

            // ✅ Calculate frame delta for smooth movement
            float frameDelta = (now - lastFrameTime) / 1000000000.0f;
            lastFrameTime = now;
            lastTime = now;

            // ✅ Fixed 20 TPS updates (physics only)
            while (delta >= 1) {
                updatePhysics();
                ticks++;
                delta--;
            }

            // ✅ Process movement input EVERY FRAME (smooth flying)
            if (!chatOverlay.isChatOpen()) {
                player.processMovementInput(frameDelta);
            }

            // Update block interaction cooldowns
            interactionHandler.update(frameDelta);

            // ✅ Render every frame
            render(frameDelta); // ✅ Pass frameDelta for animations
            frames++;

            glfwSwapBuffers(window);
            glfwPollEvents();

            input.update();
            handleInput();

            // FPS/TPS counter
            if (System.currentTimeMillis() - timer > 1000) {
                timer += 1000;
                fps = frames;
                tps = ticks;

                if (fps > peakFps)
                    peakFps = fps;

                if (Settings.SHOW_FPS && !debugScreen.isVisible()) {
                    glfwSetWindowTitle(window, String.format(
                            "%s | FPS: %d | TPS: %d | Camera: %s",
                            Settings.FULL_TITLE,
                            fps,
                            tps,
                            getCameraModeName() // ✅ Show camera mode
                    ));
                }

                frames = 0;
                ticks = 0;
            }
        }
    }

    /**
     * ✅ Physics and world updates (20 TPS)
     */
    private void updatePhysics() {
        player.tick();

        int oldSkylight = timeOfDay.getSkylightLevel();
        timeOfDay.update();
        int newSkylight = timeOfDay.getSkylightLevel();

        if (oldSkylight != newSkylight) {
            world.updateSkylightForTimeChange();
        }

        world.updateSunLight();
        world.getLightingEngine().update();
        skyRenderer.update(1.0f / Settings.TARGET_TPS);

        int playerChunkX = (int) Math.floor(player.getX() / 16);
        int playerChunkZ = (int) Math.floor(player.getZ() / 16);
        world.updateChunks(playerChunkX, playerChunkZ);
    }

    private void render(float partialTicks) {
        float[] skyColor = timeOfDay.getSkyColor();
        float[] fogColor = timeOfDay.getFogColor();

        glClearColor(skyColor[0], skyColor[1], skyColor[2], 1.0f);

        if (Settings.ENABLE_FOG) {
            FloatBuffer fogColorBuffer = BufferUtils.createByteBuffer(16).asFloatBuffer();
            fogColorBuffer.put(fogColor[0]);
            fogColorBuffer.put(fogColor[1]);
            fogColorBuffer.put(fogColor[2]);
            fogColorBuffer.put(1.0f);
            fogColorBuffer.flip();
            glFogfv(GL_FOG_COLOR, fogColorBuffer);
        }

        glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
        glLoadIdentity();

        // ✅ Apply camera transformations
        if (cameraMode == 0) {
            camera.applyTranslations();
        } else if (cameraMode == 1) {
            applyThirdPersonCamera(4.0f);
        } else {
            applyThirdPersonCamera(-4.0f);
        }

        // Render sky
        if (renderSky) {
            skyRenderer.renderSky(player.getX(), player.getY(), player.getZ());
        }

        // Render world
        glColor3f(1.0f, 1.0f, 1.0f);
        world.render(camera);
        world.getRenderer().update();

        // Render chunk borders (debug)
        if (debugScreen.showChunkBorders()) {
            renderChunkBorders();
        }

        // Render block selection outline
        if (player.getGameMode() != GameMode.SPECTATOR) {
            renderBlockOutline();
        }

        // Apply underwater effect
        glColor3f(1.0f, 1.0f, 1.0f);
        camera.applyUnderwaterEffect();

        // Render GUI
        if (guiVisible) {
            hud.render(inventory.getSelectedSlot());
        }

        debugScreen.render(camera, world, player.getGameMode(), fps, tps, vsyncEnabled);
        chatOverlay.render();
    }

    private void applyThirdPersonCamera(float distance) {
        // 1. Move camera back by distance
        glTranslatef(0, 0, -Math.abs(distance));

        // 2. Rotate camera to match player view
        glRotatef(player.getPitch(), 1, 0, 0);
        // If front view (distance < 0), rotate 180 degrees to look at face
        // Back view (distance > 0) should have 0 offset
        float yawOffset = (distance < 0) ? 180.0f : 0.0f;
        glRotatef(player.getYaw() + yawOffset, 0, 1, 0);

        // 3. Move camera to player position (eye level)
        glTranslatef(-player.getX(), -player.getEyeY(), -player.getZ());
    }

    private void renderChunkBorders() {
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glLineWidth(2.0f);
        glColor3f(0, 0, 1);

        int px = (int) player.getX();
        int pz = (int) player.getZ();

        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                int chunkX = (px / 16 + cx) * 16;
                int chunkZ = (pz / 16 + cz) * 16;

                glBegin(GL_LINES);
                for (int i = 0; i <= 16; i += 16) {
                    for (int j = 0; j <= 16; j += 16) {
                        glVertex3f(chunkX + i, 0, chunkZ + j);
                        glVertex3f(chunkX + i, 128, chunkZ + j);
                    }
                }
                glEnd();
            }
        }

        glLineWidth(1.0f);
        glEnable(GL_TEXTURE_2D);
    }

    /**
     * Render outline around the block being targeted by the crosshair.
     */
    private void renderBlockOutline() {
        // Cast ray to find targeted block
        float[] dir = camera.getForwardVector();
        RayCast.RayResult ray = RayCast.cast(
                world,
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                dir[0], dir[1], dir[2],
                REACH_DISTANCE);

        // Render outline if block found
        if (ray.hit) {
            outlineRenderer.render(ray.x, ray.y, ray.z);
        }
    }

    private void handleInput() {
        String chatMessage = chatOverlay.getPendingMessage();
        if (chatMessage != null) {
            commandHandler.executeCommand(chatMessage);
            return;
        }

        if (chatOverlay.isChatOpen()) {
            return;
        }

        if (input.isKeyPressed(GLFW_KEY_ESCAPE)) {
            running = false;
        }

        if (input.isKeyPressed(GLFW_KEY_F3)) {
            boolean hasCombination = input.isKeyDown(GLFW_KEY_Q) ||
                    input.isKeyDown(GLFW_KEY_A) ||
                    input.isKeyDown(GLFW_KEY_B) ||
                    input.isKeyDown(GLFW_KEY_C) ||
                    input.isKeyDown(GLFW_KEY_G) ||
                    input.isKeyDown(GLFW_KEY_N) ||
                    input.isKeyDown(GLFW_KEY_P) ||
                    input.isKeyDown(GLFW_KEY_S);

            if (!hasCombination) {
                debugScreen.toggle();
            }
        }

        if (input.isKeyDown(GLFW_KEY_F3)) {
            handleF3Combinations();
        }

        if (!input.isKeyDown(GLFW_KEY_F3)) {
            if (input.isKeyPressed(GLFW_KEY_F1)) {
                guiVisible = !guiVisible;
            }

            if (input.isKeyPressed(GLFW_KEY_F2)) {
                Screenshot.takeScreenshot(window);
            }

            if (input.isKeyPressed(GLFW_KEY_F4)) {
                cycleGameMode(1);
            }

            if (input.isKeyPressed(GLFW_KEY_F5)) {
                cameraMode = (cameraMode + 1) % 3;
                System.out.println("Camera mode: " + getCameraModeName());
            }

            if (input.isKeyPressed(GLFW_KEY_F11)) {
                toggleFullscreen();
            }
        }

        if (input.isKeyPressed(GLFW_KEY_V) && !input.isKeyDown(GLFW_KEY_F3)) {
            vsyncEnabled = !vsyncEnabled;
            glfwSwapInterval(vsyncEnabled ? 1 : 0);
        }

        if (input.isKeyPressed(GLFW_KEY_F) && !input.isKeyDown(GLFW_KEY_F3)) {
            player.toggleFlying();
        }

        for (int i = 0; i < 9; i++) {
            if (input.isKeyPressed(GLFW_KEY_1 + i)) {
                inventory.selectSlot(i);
            }
        }

        int dwheel = getScrollDelta();
        if (dwheel > 0) {
            inventory.prevSlot();
        } else if (dwheel < 0) {
            inventory.nextSlot();
        }

        // Block breaking - hold left mouse to continuously break
        if (player.getGameMode() != GameMode.SPECTATOR) {
            boolean leftMouseHeld = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS;
            interactionHandler.handleBreakInput(leftMouseHeld);
        }

        // Block placing - hold right mouse to continuously place
        if (player.getGameMode() != GameMode.SPECTATOR) {
            boolean rightMouseHeld = glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS;
            Block selectedBlock = inventory.getSelectedBlock();
            interactionHandler.handlePlaceInput(rightMouseHeld, selectedBlock);
        }

        // Middle mouse - pick block
        if (glfwGetMouseButton(window, GLFW_MOUSE_BUTTON_MIDDLE) == GLFW_PRESS) {
            // Simple pick block (not hold-based, just on click)
            float[] dir = camera.getForwardVector();
            RayCast.RayResult ray = RayCast.cast(
                    world,
                    player.getX(),
                    player.getEyeY(),
                    player.getZ(),
                    dir[0], dir[1], dir[2],
                    REACH_DISTANCE);

            if (ray.hit) {
                Block block = world.getBlock(ray.x, ray.y, ray.z);
                if (block != null && !block.isAir()) {
                    inventory.setSlot(inventory.getSelectedSlot(), block);
                }
            }
        }
    }

    private int getScrollDelta() {
        int delta = 0;
        if (scrollOffset > 0) {
            delta = 1;
            scrollOffset = 0;
        } else if (scrollOffset < 0) {
            delta = -1;
            scrollOffset = 0;
        }
        return delta;
    }

    private void handleF3Combinations() {
        if (input.isKeyPressed(GLFW_KEY_A)) {
            for (com.mineshaft.world.Chunk chunk : world.getChunks()) {
                chunk.setNeedsRebuild(true);
            }
        }

        if (input.isKeyPressed(GLFW_KEY_B)) {
            debugScreen.toggleHitboxes();
        }

        if (input.isKeyPressed(GLFW_KEY_G)) {
            debugScreen.toggleChunkBorders();
        }

        if (input.isKeyPressed(GLFW_KEY_N)) {
            toggleSpectatorMode();
        }

        if (input.isKeyPressed(GLFW_KEY_P)) {
            pauseOnLostFocus = !pauseOnLostFocus;
        }

        if (input.isKeyPressed(GLFW_KEY_S)) {
            renderSky = !renderSky;
        }
    }

    private void cycleGameMode(int direction) {
        GameMode current = player.getGameMode();
        GameMode next = direction > 0 ? current.next() : current.previous();

        previousGameMode = current;
        player.setGameMode(next);
    }

    private void toggleSpectatorMode() {
        GameMode current = player.getGameMode();

        if (current == GameMode.SPECTATOR) {
            player.setGameMode(previousGameMode);
        } else {
            previousGameMode = current;
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;

        long monitor = glfwGetPrimaryMonitor();
        GLFWVidMode vidmode = glfwGetVideoMode(monitor);

        if (vidmode != null) {
            if (fullscreen) {
                glfwSetWindowMonitor(
                        window,
                        monitor,
                        0, 0,
                        vidmode.width(),
                        vidmode.height(),
                        vidmode.refreshRate());
            } else {
                glfwSetWindowMonitor(
                        window,
                        NULL,
                        100, 100,
                        Settings.WINDOW_WIDTH,
                        Settings.WINDOW_HEIGHT,
                        GLFW_DONT_CARE);
            }
        }
    }

    private String getCameraModeName() {
        switch (cameraMode) {
            case 0:
                return "First Person";
            case 1:
                return "Third Person Back";
            case 2:
                return "Third Person Front";
            default:
                return "Unknown";
        }
    }

    private void cleanup() {
        System.out.println("===============================================");
        System.out.println("Shutting down...");

        if (world != null) {
            world.cleanup();
        }

        if (skyRenderer != null) {
            skyRenderer.cleanup();
        }

        if (chatOverlay != null) {
            chatOverlay.cleanup();
        }

        BlockTextures.cleanup();
        TextureManager.cleanup();

        glfwDestroyWindow(window);
        glfwTerminate();
        GLFWErrorCallback callback = glfwSetErrorCallback(null);
        if (callback != null) {
            callback.free();
        }

        System.out.println("Peak FPS: " + peakFps);
        System.out.println("Game closed successfully");
        System.out.println("===============================================");
    }

    public int getFPS() {
        return fps;
    }

    public int getTPS() {
        return tps;
    }

    public long getWindow() {
        return window;
    }
}