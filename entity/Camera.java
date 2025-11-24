package com.mineshaft.entity;

import com.mineshaft.core.Settings;
import com.mineshaft.player.Player;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Camera - ONLY handles view/rendering
 * Follows the player, processes mouse input, applies transformations
 */
public class Camera {
    private final Player player;
    private final long window;
    
    // Mouse tracking for GLFW
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private boolean firstMouse = true;
    
    public Camera(Player player, long window) {
        this.player = player;
        this.window = window;
        setupMouseCallback();
    }
    
    /**
     * Setup GLFW mouse callback
     */
    private void setupMouseCallback() {
        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
            }
            
            float xoffset = (float) (xpos - lastMouseX);
            float yoffset = (float) (lastMouseY - ypos); // Reversed
            
            lastMouseX = xpos;
            lastMouseY = ypos;
            
            processMouse(xoffset, yoffset);
        });
    }
    
    /**
     * Process camera input (calls player input)
     */
    public void processInput(float delta) {
        // Mouse is handled by callback
        // Delegate movement to player
        player.processInput(delta);
    }
    
    /**
     * Process mouse movement
     */
    private void processMouse(float xoffset, float yoffset) {
        xoffset *= Settings.MOUSE_SENSITIVITY;
        yoffset *= Settings.MOUSE_SENSITIVITY;
        
        float newYaw = player.getYaw() + xoffset;
        float newPitch = player.getPitch() - yoffset; // ✅ Fixed
        
        // Clamp pitch
        if (newPitch > 90) newPitch = 90;
        if (newPitch < -90) newPitch = -90;
        
        // Normalize yaw
        if (newYaw > 360) newYaw -= 360;
        if (newYaw < 0) newYaw += 360;
        
        player.setRotation(newYaw, newPitch);
    }
    
    /**
     * Apply camera transformations for rendering
     */
    public void applyTranslations() {
        glRotatef(player.getPitch(), 1, 0, 0);
        glRotatef(player.getYaw(), 0, 1, 0);
        glTranslatef(-player.getX(), -player.getEyeY(), -player.getZ());
    }
    
    /**
     * Apply underwater visual effects
     */
    public void applyUnderwaterEffect() {
        if (player.isInWater()) {
            // ✅ IMPROVED: Better underwater fog
            glEnable(GL_FOG);
            glFogi(GL_FOG_MODE, GL_LINEAR);
            glFogf(GL_FOG_START, 5.0f);
            glFogf(GL_FOG_END, 35.0f);
            
            // Lighter blue fog color
            FloatBuffer fogColorBuffer = BufferUtils.createFloatBuffer(4);
            fogColorBuffer.put(0.2f);
            fogColorBuffer.put(0.4f);
            fogColorBuffer.put(0.7f);
            fogColorBuffer.put(1.0f);
            fogColorBuffer.flip();
            glFogfv(GL_FOG_COLOR, fogColorBuffer);
            
            glHint(GL_FOG_HINT, GL_NICEST);
            
            // Much lighter tint overlay
            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            
            glMatrixMode(GL_PROJECTION);
            glPushMatrix();
            glLoadIdentity();
            glOrtho(0, 1, 1, 0, -1, 1);
            
            glMatrixMode(GL_MODELVIEW);
            glPushMatrix();
            glLoadIdentity();
            
            glDisable(GL_DEPTH_TEST);
            glColor4f(0.0f, 0.25f, 0.55f, 0.15f);
            glBegin(GL_QUADS);
            glVertex2f(0, 0);
            glVertex2f(1, 0);
            glVertex2f(1, 1);
            glVertex2f(0, 1);
            glEnd();
            glEnable(GL_DEPTH_TEST);
            
            glMatrixMode(GL_PROJECTION);
            glPopMatrix();
            glMatrixMode(GL_MODELVIEW);
            glPopMatrix();
            
            glColor3f(1, 1, 1);
            glDisable(GL_BLEND);
        } else {
            // Restore normal fog
            if (Settings.ENABLE_FOG) {
                glEnable(GL_FOG);
                glFogi(GL_FOG_MODE, GL_EXP2);
                glFogf(GL_FOG_DENSITY, Settings.FOG_DENSITY);
            }
        }
    }
    
    /**
     * Get forward direction vector (for raycasting)
     */
    public float[] getForwardVector() {
        float pitchRad = (float) Math.toRadians(player.getPitch());
        float yawRad = (float) Math.toRadians(player.getYaw());
        
        return new float[]{
            (float) (Math.cos(pitchRad) * Math.sin(yawRad)),
            (float) (-Math.sin(pitchRad)),
            (float) (Math.cos(pitchRad) * -Math.cos(yawRad))
        };
    }
    
    // ========== DELEGATE GETTERS TO PLAYER ==========
    
    public float getX() { return player.getX(); }
    public float getY() { return player.getY(); }
    public float getZ() { return player.getZ(); }
    public float getPitch() { return player.getPitch(); }
    public float getYaw() { return player.getYaw(); }
    
    public Player getPlayer() { return player; }
}