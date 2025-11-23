package com.mineshaft.render;

import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * ✅ LWJGL 3 - Minecraft-style chat overlay
 */
public class ChatOverlay {
    
    private final long window;
    private boolean chatOpen = false;
    private StringBuilder currentInput = new StringBuilder();
    private List<String> chatHistory = new ArrayList<>();
    private List<String> messageLog = new ArrayList<>();
    
    private static final int MAX_MESSAGES = 10;
    private static final int MAX_HISTORY = 100;
    private long lastMessageTime = 0;
    private static final long MESSAGE_FADE_TIME = 5000; // 5 seconds
    
    private int screenWidth = 1280;
    private int screenHeight = 720;
    
    // ✅ Track last key to prevent double input
    private int lastProcessedKey = -1;
    
    public ChatOverlay(long window) {
        this.window = window;
        
        // Get initial window size
        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        this.screenWidth = w[0];
        this.screenHeight = h[0];
        
        setupCallbacks();
    }
    
    /**
     * ✅ Setup GLFW input callbacks for chat
     */
    private void setupCallbacks() {
        // Character callback for text input
        glfwSetCharCallback(window, (w, codepoint) -> {
            if (chatOpen && codepoint >= 32 && codepoint < 127) {
                if (currentInput.length() < 100) {
                    currentInput.append((char) codepoint);
                }
            }
        });
    }
    
    /**
     * ✅ Open chat (T key)
     */
    public void openChat() {
        chatOpen = true;
        currentInput.setLength(0);
    }
    
    /**
     * ✅ Open chat with "/" (/ key)
     */
    public void openChatWithSlash() {
        chatOpen = true;
        currentInput.setLength(0);
        currentInput.append("/");
    }
    
    /**
     * ✅ Close chat
     */
    public void closeChat() {
        chatOpen = false;
        currentInput.setLength(0);
    }
    
    /**
     * ✅ LWJGL 3 - Handle keyboard input
     */
    public String handleInput() {
        if (!chatOpen) return null;
        
        // Enter - send message
        if (glfwGetKey(window, GLFW_KEY_ENTER) == GLFW_PRESS) {
            if (lastProcessedKey != GLFW_KEY_ENTER) {
                String message = currentInput.toString().trim();
                if (!message.isEmpty()) {
                    addMessage("> " + message);
                    chatHistory.add(message);
                    if (chatHistory.size() > MAX_HISTORY) {
                        chatHistory.remove(0);
                    }
                    closeChat();
                    lastProcessedKey = GLFW_KEY_ENTER;
                    return message;
                }
                closeChat();
                lastProcessedKey = GLFW_KEY_ENTER;
            }
            return null;
        }
        
        // Escape - close chat
        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_PRESS) {
            if (lastProcessedKey != GLFW_KEY_ESCAPE) {
                closeChat();
                lastProcessedKey = GLFW_KEY_ESCAPE;
            }
            return null;
        }
        
        // Backspace
        if (glfwGetKey(window, GLFW_KEY_BACKSPACE) == GLFW_PRESS) {
            if (lastProcessedKey != GLFW_KEY_BACKSPACE) {
                if (currentInput.length() > 0) {
                    currentInput.deleteCharAt(currentInput.length() - 1);
                }
                lastProcessedKey = GLFW_KEY_BACKSPACE;
            }
        } else if (lastProcessedKey == GLFW_KEY_BACKSPACE) {
            lastProcessedKey = -1;
        }
        
        // Reset key tracking for other keys
        if (glfwGetKey(window, GLFW_KEY_ENTER) == GLFW_RELEASE && lastProcessedKey == GLFW_KEY_ENTER) {
            lastProcessedKey = -1;
        }
        if (glfwGetKey(window, GLFW_KEY_ESCAPE) == GLFW_RELEASE && lastProcessedKey == GLFW_KEY_ESCAPE) {
            lastProcessedKey = -1;
        }
        
        return null;
    }
    
    /**
     * ✅ Add message to chat log
     */
    public void addMessage(String message) {
        messageLog.add(message);
        if (messageLog.size() > MAX_MESSAGES) {
            messageLog.remove(0);
        }
        lastMessageTime = System.currentTimeMillis();
        System.out.println("[Chat] " + message);
    }
    
    /**
     * ✅ Render chat overlay
     */
    public void render() {
        // Update screen size
        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetFramebufferSize(window, w, h);
        screenWidth = w[0];
        screenHeight = h[0];
        
        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, screenWidth, screenHeight, 0, -1, 1);
        
        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();
        
        glDisable(GL_DEPTH_TEST);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        
        float chatY = screenHeight - 50;
        
        // Render chat messages (if chat open or recent)
        if (chatOpen || (System.currentTimeMillis() - lastMessageTime < MESSAGE_FADE_TIME)) {
            float alpha = chatOpen ? 0.8f : Math.max(0, 1.0f - (System.currentTimeMillis() - lastMessageTime) / (float) MESSAGE_FADE_TIME);
            
            for (int i = 0; i < messageLog.size(); i++) {
                String msg = messageLog.get(messageLog.size() - 1 - i);
                float msgY = chatY - (i * 12);
                
                // Background
                glColor4f(0, 0, 0, 0.5f * alpha);
                drawRect(5, msgY - 10, 400, 12);
                
                // Text (simple render - you can improve this)
                glColor4f(1, 1, 1, alpha);
                renderText(msg, 10, msgY - 8);
            }
        }
        
        // Render input box if chat open
        if (chatOpen) {
            float inputY = screenHeight - 30;
            
            // Background
            glColor4f(0, 0, 0, 0.8f);
            drawRect(5, inputY - 5, screenWidth - 10, 20);
            
            // Border
            glColor4f(1, 1, 1, 1);
            drawRectOutline(5, inputY - 5, screenWidth - 10, 20);
            
            // Input text
            String displayText = currentInput.toString();
            
            // Cursor blink
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                displayText += "_";
            }
            
            glColor4f(1, 1, 1, 1);
            renderText(displayText, 10, inputY);
        }
        
        glDisable(GL_BLEND);
        glEnable(GL_DEPTH_TEST);
        
        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();
    }
    
    /**
     * Simple text renderer (basic implementation)
     */
    private void renderText(String text, float x, float y) {
        // This is a placeholder - you can implement proper font rendering later
        // For now, just print to console
        // In a real implementation, you'd use LWJGL's font rendering or a texture-based font
    }
    
    private void drawRect(float x, float y, float width, float height) {
        glBegin(GL_QUADS);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
    }
    
    private void drawRectOutline(float x, float y, float width, float height) {
        glLineWidth(1.0f);
        glBegin(GL_LINE_LOOP);
        glVertex2f(x, y);
        glVertex2f(x + width, y);
        glVertex2f(x + width, y + height);
        glVertex2f(x, y + height);
        glEnd();
    }
    
    public boolean isChatOpen() {
        return chatOpen;
    }
    
    public void updateSize(int width, int height) {
        this.screenWidth = width;
        this.screenHeight = height;
    }
}