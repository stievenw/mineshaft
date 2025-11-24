package com.mineshaft.entity;

import com.mineshaft.core.Settings;
import com.mineshaft.player.Player;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ FIXED - Mouse processed every frame via callback
 */
public class Camera {
    private final Player player;
    private final long window;
    
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private boolean firstMouse = true;
    
    private static final float MOUSE_SENSITIVITY = Settings.MOUSE_SENSITIVITY;
    
    public Camera(Player player, long window) {
        this.player = player;
        this.window = window;
        setupMouseCallback();
    }
    
    private void setupMouseCallback() {
        glfwSetCursorPosCallback(window, (w, xpos, ypos) -> {
            if (firstMouse) {
                lastMouseX = xpos;
                lastMouseY = ypos;
                firstMouse = false;
                return;
            }
            
            float xoffset = (float) (xpos - lastMouseX);
            float yoffset = (float) (lastMouseY - ypos);
            
            lastMouseX = xpos;
            lastMouseY = ypos;
            
            processMouse(xoffset, yoffset);
        });
    }
    
    private void processMouse(float xoffset, float yoffset) {
        xoffset *= MOUSE_SENSITIVITY;
        yoffset *= MOUSE_SENSITIVITY;
        
        float newYaw = player.getYaw() + xoffset;
        float newPitch = player.getPitch() - yoffset;
        
        if (newPitch > 89.0f) newPitch = 89.0f;
        if (newPitch < -89.0f) newPitch = -89.0f;
        
        while (newYaw > 360.0f) newYaw -= 360.0f;
        while (newYaw < 0.0f) newYaw += 360.0f;
        
        player.setRotation(newYaw, newPitch);
    }
    
    public void applyTranslations() {
        glRotatef(player.getPitch(), 1, 0, 0);
        glRotatef(player.getYaw(), 0, 1, 0);
        glTranslatef(-player.getX(), -player.getEyeY(), -player.getZ());
    }
    
    public void applyUnderwaterEffect() {
        if (player.isInWater()) {
            glEnable(GL_FOG);
            glFogi(GL_FOG_MODE, GL_LINEAR);
            glFogf(GL_FOG_START, 5.0f);
            glFogf(GL_FOG_END, 35.0f);
            
            FloatBuffer fogColorBuffer = BufferUtils.createFloatBuffer(4);
            fogColorBuffer.put(0.2f);
            fogColorBuffer.put(0.4f);
            fogColorBuffer.put(0.7f);
            fogColorBuffer.put(1.0f);
            fogColorBuffer.flip();
            glFogfv(GL_FOG_COLOR, fogColorBuffer);
            
            glHint(GL_FOG_HINT, GL_NICEST);
            
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
            if (Settings.ENABLE_FOG) {
                glEnable(GL_FOG);
                glFogi(GL_FOG_MODE, GL_EXP2);
                glFogf(GL_FOG_DENSITY, Settings.FOG_DENSITY);
            }
        }
    }
    
    public float[] getForwardVector() {
        float yawRad = (float) Math.toRadians(player.getYaw());
        float pitchRad = (float) Math.toRadians(player.getPitch());
        
        float x = (float) (-Math.sin(yawRad) * Math.cos(pitchRad));
        float y = (float) (-Math.sin(pitchRad));
        float z = (float) (Math.cos(yawRad) * Math.cos(pitchRad));
        
        return new float[]{x, y, z};
    }
    
    public float getX() { return player.getX(); }
    public float getY() { return player.getY(); }
    public float getZ() { return player.getZ(); }
    public float getYaw() { return player.getYaw(); }
    public float getPitch() { return player.getPitch(); }
    
    public void setPosition(float x, float y, float z) {
        player.setPosition(x, y, z);
    }
    
    public Player getPlayer() { return player; }
}