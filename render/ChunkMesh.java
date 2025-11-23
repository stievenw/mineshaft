package com.mineshaft.render;

import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;

/**
 * ✅ ChunkMesh with Texture Support
 */
public class ChunkMesh {
    
    private List<Float> vertices = new ArrayList<>();
    private FloatBuffer vertexBuffer;
    private int vboId = -1;
    private int vertexCount = 0;
    
    /**
     * ✅ UPDATED: Add vertex with texture coordinates
     */
    public void addVertex(float x, float y, float z, 
                          float r, float g, float b, float a,
                          float nx, float ny, float nz,
                          float u, float v) {
        // Position
        vertices.add(x);
        vertices.add(y);
        vertices.add(z);
        
        // Color
        vertices.add(r);
        vertices.add(g);
        vertices.add(b);
        vertices.add(a);
        
        // Normal
        vertices.add(nx);
        vertices.add(ny);
        vertices.add(nz);
        
        // Texture coordinates
        vertices.add(u);
        vertices.add(v);
        
        vertexCount++;
    }
    
    /**
     * ✅ BACKWARD COMPATIBILITY: Old method without UVs (defaults to 0,0)
     */
    public void addVertex(float x, float y, float z, 
                          float r, float g, float b, float a,
                          float nx, float ny, float nz) {
        addVertex(x, y, z, r, g, b, a, nx, ny, nz, 0, 0);
    }
    
    /**
     * Build VBO
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
        
        // Create VBO
        vboId = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer, GL_STATIC_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        
        // Clear CPU-side data
        vertices.clear();
    }
    
    /**
     * Render mesh
     */
    public void render() {
        if (vboId == -1 || vertexCount == 0) {
            return;
        }
        
        glBindBuffer(GL_ARRAY_BUFFER, vboId);
        
        int stride = 12 * 4; // 12 floats * 4 bytes = 48 bytes per vertex
        
        // Position (3 floats)
        glEnableClientState(GL_VERTEX_ARRAY);
        glVertexPointer(3, GL_FLOAT, stride, 0);
        
        // Color (4 floats)
        glEnableClientState(GL_COLOR_ARRAY);
        glColorPointer(4, GL_FLOAT, stride, 3 * 4);
        
        // Normal (3 floats) - offset 7*4
        glEnableClientState(GL_NORMAL_ARRAY);
        glNormalPointer(GL_FLOAT, stride, 7 * 4);
        
        // ✅ NEW: Texture coordinates (2 floats) - offset 10*4
        glEnableClientState(GL_TEXTURE_COORD_ARRAY);
        glTexCoordPointer(2, GL_FLOAT, stride, 10 * 4);
        
        // Draw
        glDrawArrays(GL_QUADS, 0, vertexCount);
        
        // Disable arrays
        glDisableClientState(GL_VERTEX_ARRAY);
        glDisableClientState(GL_COLOR_ARRAY);
        glDisableClientState(GL_NORMAL_ARRAY);
        glDisableClientState(GL_TEXTURE_COORD_ARRAY);
        
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
    
    /**
     * Destroy VBO
     */
    public void destroy() {
        if (vboId != -1) {
            glDeleteBuffers(vboId);
            vboId = -1;
        }
        vertices.clear();
        vertexCount = 0;
    }
    
    public int getVertexCount() {
        return vertexCount;
    }
}