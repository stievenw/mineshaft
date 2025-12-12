package com.mineshaft.render;

import static org.lwjgl.opengl.GL20.*;

/**
 * ✅ Simple shader for chunk rendering with time-based brightness
 * 
 * This shader multiplies vertex colors by a uniform brightness value,
 * allowing time-of-day lighting without mesh rebuilds.
 */
public class ChunkShader {
    private int programId;
    private int vertexShaderId;
    private int fragmentShaderId;

    // Uniform locations
    private int uBrightnessLocation;
    private int uTextureLocation;

    // Vertex shader - passes through with brightness modulation
    private static final String VERTEX_SHADER = """
            #version 120

            uniform float uBrightness;

            varying vec4 vColor;
            varying vec2 vTexCoord;

            void main() {
                gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;
                vTexCoord = gl_MultiTexCoord0.st;

                // Multiply vertex color by time-based brightness
                vColor = gl_Color * uBrightness;
            }
            """;

    // Fragment shader - texture × modulated color
    private static final String FRAGMENT_SHADER = """
            #version 120

            uniform sampler2D uTexture;

            varying vec4 vColor;
            varying vec2 vTexCoord;

            void main() {
                vec4 texColor = texture2D(uTexture, vTexCoord);
                gl_FragColor = texColor * vColor;
            }
            """;

    public ChunkShader() {
        try {
            programId = glCreateProgram();
            if (programId == 0) {
                throw new RuntimeException("Failed to create shader program");
            }

            // Compile shaders
            vertexShaderId = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER);
            fragmentShaderId = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

            // Link program
            glAttachShader(programId, vertexShaderId);
            glAttachShader(programId, fragmentShaderId);
            glLinkProgram(programId);

            if (glGetProgrami(programId, GL_LINK_STATUS) == GL_FALSE) {
                String log = glGetProgramInfoLog(programId, 1024);
                throw new RuntimeException("Failed to link shader program: " + log);
            }

            glValidateProgram(programId);
            if (glGetProgrami(programId, GL_VALIDATE_STATUS) == GL_FALSE) {
                String log = glGetProgramInfoLog(programId, 1024);
                System.err.println("Shader validation warning: " + log);
            }

            // Get uniform locations
            uBrightnessLocation = glGetUniformLocation(programId, "uBrightness");
            uTextureLocation = glGetUniformLocation(programId, "uTexture");

            System.out.println("[ChunkShader] Shader compiled successfully");
            System.out.println("[ChunkShader] uBrightness location: " + uBrightnessLocation);
            System.out.println("[ChunkShader] uTexture location: " + uTextureLocation);

        } catch (Exception e) {
            cleanup();
            throw new RuntimeException("Failed to create ChunkShader", e);
        }
    }

    private int compileShader(int type, String source) {
        int shaderId = glCreateShader(type);
        if (shaderId == 0) {
            throw new RuntimeException("Failed to create shader");
        }

        glShaderSource(shaderId, source);
        glCompileShader(shaderId);

        if (glGetShaderi(shaderId, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shaderId, 1024);
            String shaderType = (type == GL_VERTEX_SHADER) ? "vertex" : "fragment";
            throw new RuntimeException("Failed to compile " + shaderType + " shader: " + log);
        }

        return shaderId;
    }

    public void use() {
        glUseProgram(programId);
    }

    public void stop() {
        glUseProgram(0);
    }

    /**
     * Set the time-based brightness multiplier
     * 
     * @param brightness Value from 0.0 to 1.0
     */
    public void setBrightness(float brightness) {
        glUniform1f(uBrightnessLocation, brightness);
    }

    /**
     * Set the texture sampler unit
     * 
     * @param textureUnit Texture unit (usually 0)
     */
    public void setTexture(int textureUnit) {
        glUniform1i(uTextureLocation, textureUnit);
    }

    public void cleanup() {
        stop();

        if (programId != 0) {
            if (vertexShaderId != 0) {
                glDetachShader(programId, vertexShaderId);
                glDeleteShader(vertexShaderId);
            }
            if (fragmentShaderId != 0) {
                glDetachShader(programId, fragmentShaderId);
                glDeleteShader(fragmentShaderId);
            }
            glDeleteProgram(programId);
        }
    }
}
