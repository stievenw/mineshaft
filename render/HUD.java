package com.mineshaft.render;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ LWJGL 3 - HUD rendering - Fixed warnings
 */
public class HUD {
    private final long window;
    private int screenWidth;
    private int screenHeight;
    
    public HUD(long window) {
        this.window = window;
        updateScreenSize();
    }
    
    private void updateScreenSize() {
        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        this.screenWidth = w[0];
        this.screenHeight = h[0];
    }
    
    public void render(int selectedSlot) {
        updateScreenSize();
        
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_LIGHTING);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        renderCrosshair();
        renderHotbar(selectedSlot);
        
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }
    
    private void renderCrosshair() {
        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;
        float size = 8.0f;
        float thickness = 2.0f;
        float gap = 2.0f;
        
        glLineWidth(thickness + 2);
        glColor4f(0, 0, 0, 0.9f);
        glBegin(GL_LINES);
        glVertex2f(centerX - size, centerY);
        glVertex2f(centerX - gap, centerY);
        glVertex2f(centerX + gap, centerY);
        glVertex2f(centerX + size, centerY);
        glVertex2f(centerX, centerY - size);
        glVertex2f(centerX, centerY - gap);
        glVertex2f(centerX, centerY + gap);
        glVertex2f(centerX, centerY + size);
        glEnd();
        
        glLineWidth(thickness);
        glColor4f(1, 1, 1, 1);
        glBegin(GL_LINES);
        glVertex2f(centerX - size, centerY);
        glVertex2f(centerX - gap, centerY);
        glVertex2f(centerX + gap, centerY);
        glVertex2f(centerX + size, centerY);
        glVertex2f(centerX, centerY - size);
        glVertex2f(centerX, centerY - gap);
        glVertex2f(centerX, centerY + gap);
        glVertex2f(centerX, centerY + size);
        glEnd();
        
        glLineWidth(1);
    }
    
    private void renderHotbar(int selectedSlot) {
        float slotSize = 40.0f;
        float padding = 4.0f;
        float hotbarWidth = 9 * (slotSize + padding) + padding;
        float hotbarHeight = slotSize + padding * 2;
        
        float startX = (screenWidth - hotbarWidth) / 2.0f;
        float startY = screenHeight - hotbarHeight - 20;
        
        glColor4f(0, 0, 0, 0.6f);
        drawRect(startX, startY, hotbarWidth, hotbarHeight);
        
        for (int i = 0; i < 9; i++) {
            float slotX = startX + padding + i * (slotSize + padding);
            float slotY = startY + padding;
            
            if (i == selectedSlot) {
                glColor4f(1, 1, 1, 0.9f);
                drawRectOutline(slotX - 3, slotY - 3, slotSize + 6, slotSize + 6, 3.0f);
            }
            
            glColor4f(0.2f, 0.2f, 0.2f, 0.9f);
            drawRect(slotX, slotY, slotSize, slotSize);
            
            glColor4f(0.4f, 0.4f, 0.4f, 1.0f);
            drawRectOutline(slotX, slotY, slotSize, slotSize, 1.0f);
            
            glColor4f(0.8f, 0.8f, 0.8f, 0.7f);
            float numSize = 8.0f;
            drawRect(slotX + 2, slotY + 2, numSize, numSize);
        }
    }
    
    private void drawRect(float x, float y, float width, float height) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
    }
    
    // ✅ REMOVED: Unused drawRectOutline(4 params) method - only keep the 5-param version
    
    private void drawRectOutline(float x, float y, float width, float height, float lineWidth) {
        glLineWidth(lineWidth);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
        glLineWidth(1.0f);
    }
    
    public void updateSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }
}