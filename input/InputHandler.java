package com.mineshaft.input;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.glfw.GLFW.*;

/**
 * ✅ LWJGL 3 - Proper input handler with debouncing
 */
public class InputHandler {
    private final long window;
    private Map<Integer, Boolean> keyStates = new HashMap<>();
    private Map<Integer, Boolean> lastKeyStates = new HashMap<>();
    
    public InputHandler(long window) {
        this.window = window;
    }
    
    /**
     * Update key states (call once per frame)
     */
    public void update() {
        // Copy current states to last states
        lastKeyStates.clear();
        lastKeyStates.putAll(keyStates);
        
        // Update current states
        keyStates.clear();
    }
    
    /**
     * Check if key is currently pressed
     */
    public boolean isKeyDown(int key) {
        return glfwGetKey(window, key) == GLFW_PRESS;
    }
    
    /**
     * Check if key was just pressed (press event, not hold)
     */
    public boolean isKeyPressed(int key) {
        boolean currentState = glfwGetKey(window, key) == GLFW_PRESS;
        Boolean lastState = lastKeyStates.get(key);
        
        // Key just pressed if it's down now but wasn't before
        if (currentState && (lastState == null || !lastState)) {
            keyStates.put(key, true);
            return true;
        }
        
        keyStates.put(key, currentState);
        return false;
    }
    
    /**
     * Check if key was just released
     */
    public boolean isKeyReleased(int key) {
        boolean currentState = glfwGetKey(window, key) == GLFW_PRESS;
        Boolean lastState = lastKeyStates.get(key);
        
        // Key just released if it was down before but isn't now
        return !currentState && (lastState != null && lastState);
    }
}