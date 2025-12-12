package com.mineshaft.render;

import org.lwjgl.BufferUtils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.lwjgl.opengl.GL20.*;

/**
 * ✅ ShaderProgram - Manages OpenGL shader programs
 * 
 * Handles:
 * - Loading and compiling GLSL shaders
 * - Linking shader programs
 * - Managing uniform locations
 * - Setting uniform values
 */
public class ShaderProgram {

    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;
    private boolean valid = false;

    private final Map<String, Integer> uniformLocations = new HashMap<>();

    // Reusable buffer for matrix uniforms
    private static final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    /**
     * Create shader program from resource files
     */
    public ShaderProgram(String vertexPath, String fragmentPath) {
        try {
            // Load and compile vertex shader
            String vertexSource = loadResource(vertexPath);
            vertexShaderId = compileShader(vertexSource, GL_VERTEX_SHADER);

            // Load and compile fragment shader
            String fragmentSource = loadResource(fragmentPath);
            fragmentShaderId = compileShader(fragmentSource, GL_FRAGMENT_SHADER);

            // Create and link program
            programId = glCreateProgram();
            glAttachShader(programId, vertexShaderId);
            glAttachShader(programId, fragmentShaderId);
            glLinkProgram(programId);

            // Check link status
            if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
                String log = glGetProgramInfoLog(programId);
                System.err.println("[ShaderProgram] Link error: " + log);
                cleanup();
                return;
            }

            // Validate program
            glValidateProgram(programId);
            if (glGetProgrami(programId, GL_VALIDATE_STATUS) == GL_FALSE) {
                System.err.println("[ShaderProgram] Validation warning: " + glGetProgramInfoLog(programId));
                // Continue anyway - validation warnings are sometimes spurious
            }

            valid = true;
            System.out.println("[ShaderProgram] Successfully loaded: " + vertexPath + " + " + fragmentPath);

        } catch (Exception e) {
            System.err.println("[ShaderProgram] Failed to load shaders: " + e.getMessage());
            e.printStackTrace();
            cleanup();
        }
    }

    /**
     * Load shader source from resources
     */
    private String loadResource(String path) throws Exception {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new Exception("Resource not found: " + path);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }

    /**
     * Compile a shader from source
     */
    private int compileShader(String source, int type) {
        int shaderId = glCreateShader(type);
        glShaderSource(shaderId, source);
        glCompileShader(shaderId);

        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shaderId);
            String typeName = (type == GL_VERTEX_SHADER) ? "vertex" : "fragment";
            throw new RuntimeException("Failed to compile " + typeName + " shader: " + log);
        }

        return shaderId;
    }

    /**
     * Use this shader program
     */
    public void bind() {
        if (valid) {
            glUseProgram(programId);
        }
    }

    /**
     * Stop using this shader program
     */
    public void unbind() {
        glUseProgram(0);
    }

    /**
     * Get or cache uniform location
     */
    private int getUniformLocation(String name) {
        return uniformLocations.computeIfAbsent(name, n -> glGetUniformLocation(programId, n));
    }

    // ========== UNIFORM SETTERS ==========

    public void setUniform1i(String name, int value) {
        int location = getUniformLocation(name);
        if (location >= 0) {
            glUniform1i(location, value);
        }
    }

    public void setUniform1f(String name, float value) {
        int location = getUniformLocation(name);
        if (location >= 0) {
            glUniform1f(location, value);
        }
    }

    public void setUniform3f(String name, float x, float y, float z) {
        int location = getUniformLocation(name);
        if (location >= 0) {
            glUniform3f(location, x, y, z);
        }
    }

    public void setUniform4f(String name, float x, float y, float z, float w) {
        int location = getUniformLocation(name);
        if (location >= 0) {
            glUniform4f(location, x, y, z, w);
        }
    }

    public void setUniformMatrix4f(String name, float[] matrix) {
        int location = getUniformLocation(name);
        if (location >= 0) {
            matrixBuffer.clear();
            matrixBuffer.put(matrix);
            matrixBuffer.flip();
            glUniformMatrix4fv(location, false, matrixBuffer);
        }
    }

    /**
     * Check if shader is valid and ready to use
     */
    public boolean isValid() {
        return valid;
    }

    /**
     * Cleanup shader resources
     */
    public void cleanup() {
        unbind();

        if (vertexShaderId != 0) {
            glDetachShader(programId, vertexShaderId);
            glDeleteShader(vertexShaderId);
        }

        if (fragmentShaderId != 0) {
            glDetachShader(programId, fragmentShaderId);
            glDeleteShader(fragmentShaderId);
        }

        if (programId != 0) {
            glDeleteProgram(programId);
        }

        valid = false;
    }

    public int getProgramId() {
        return programId;
    }
}
