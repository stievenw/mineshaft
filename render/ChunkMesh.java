package com.mineshaft.render;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

/**
 * ✅ ChunkMesh - Simplified (Only essential methods)
 */
public class ChunkMesh {

    private List<Float> vertices = new ArrayList<>();
    private FloatBuffer vertexBuffer;
    private int vboId = -1;
    private int vertexCount = 0;

    /**
     * Constructor with TextureAtlas (for compatibility)
     */
    public ChunkMesh(TextureAtlas atlas) {
        // Atlas not directly used in this implementation
    }

    /**
     * Default constructor
     */
    public ChunkMesh() {
    }

    /**
     * Add a single vertex with all attributes
     * 
     * @param x  Position X
     * @param y  Position Y
     * @param z  Position Z
     * @param r  Red color (0.0 - 1.0)
     * @param g  Green color (0.0 - 1.0)
     * @param b  Blue color (0.0 - 1.0)
     * @param a  Alpha (0.0 - 1.0)
     * @param nx Normal X
     * @param ny Normal Y
     * @param nz Normal Z
     * @param u  Texture U coordinate
     * @param v  Texture V coordinate
     */
    public void addVertex(float x, float y, float z,
            float r, float g, float b, float a,
            float nx, float ny, float nz,
            float u, float v) {
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        vertices.add(r);
        vertices.add(g);
        vertices.add(b);
        vertices.add(a);
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);
        vertices.add(u);
        vertices.add(v);

        vertexCount++;
    }

    /**
     * Build VBO from vertex data
     */
    /**
     * ✅ Optimized Build from primitive array
     * Eliminates overhead of converting List<Float> to array/buffer
     */
    public void build(float[] data, int floatCount) {
        if (floatCount == 0 || data == null) {
            return;
        }

        vertexCount = floatCount / 12; // 12 floats per vertex

        // Create VBO directly from array
        // We wrap it in a FloatBuffer
        vertexBuffer = BufferUtils.createFloatBuffer(floatCount);
        vertexBuffer.put(data, 0, floatCount);
        vertexBuffer.flip();

        // Create VBO
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Build VBO from vertex data (Legacy)
     */
    public void build() {
        if (vertices.isEmpty()) {
            return;
        }

        // Convert ArrayList to FloatBuffer
        vertexBuffer = BufferUtils.createFloatBuffer(vertices.size());
        for (float v : vertices) {
            vertexBuffer.put(v);
        }
        vertexBuffer.flip();
        vertexCount = vertices.size() / 12;

        // Create VBO
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);

        // Clear CPU-side data to save memory
        vertices.clear();
    }

    /**
     * Render mesh using VBO
     */
    public void render() {
        if (vboId == -1 || vertexCount == 0) {
            return;
        }

        glBindBuffer(GL_ARRAY_BUFFER, vboId);

        // Vertex format: [x, y, z, r, g, b, a, nx, ny, nz, u, v] = 12 floats = 48 bytes
        int stride = 12 * 4;

        // Position (3 floats, offset 0)
        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(3, GL_FLOAT, stride, 0);

        // Color (4 floats, offset 12 bytes)
        glEnableClientState(GL_COLOR_ARRAY);
        glColorPointer(4, GL_FLOAT, stride, 3 * 4);

        // Normal (3 floats, offset 28 bytes)
        glEnableClientState(GL_NORMAL_ARRAY);
        glNormalPointer(GL_FLOAT, stride, 7 * 4);

        // Texture coordinates (2 floats, offset 40 bytes)
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        glTexCoordPointer(2, GL_FLOAT, stride, 10 * 4);

        // Draw quads
        glDrawArrays(GL_QUADS, 0, vertexCount);

        // Disable client states
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_NORMAL_ARRAY);
        glDisableClientState(GL_TEXTURE_COORD_ARRAY);

        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    /**
     * Destroy VBO and free resources
     */
    public void destroy() {
        if (vboId != -1) {
            glDeleteBuffers(vboId);
            vboId = -1;
        }
        vertices.clear();
        vertexCount = 0;
    }

    /**
     * Get vertex count
     */
    public int getVertexCount() {
        return vertexCount;
    }
}