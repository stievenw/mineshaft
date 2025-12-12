package com.mineshaft.render;

import java.util.Arrays;

/**
 * ✅ SmartMeshBuilder - Optimized Primitive Buffer
 * Replaces ArrayList<Float> to eliminate boxing/unboxing overhead.
 * Designed for high-performance mesh generation.
 */
public class SmartMeshBuilder {
    private float[] data;
    private int count; // Number of floats, not vertices

    // Vertex size in floats (x,y,z, r,g,b,a, nx,ny,nz, u,v) = 12
    private static final int VERTEX_SIZE = 12;

    public SmartMeshBuilder(int initialCapacity) {
        this.data = new float[initialCapacity];
        this.count = 0;
    }

    public void addVertex(float x, float y, float z,
            float r, float g, float b, float a,
            float nx, float ny, float nz,
            float u, float v) {
        ensureCapacity(VERTEX_SIZE);

        data[count++] = x;
        data[count++] = y;
        data[count++] = z;

        data[count++] = r;
        data[count++] = g;
        data[count++] = b;
        data[count++] = a;

        data[count++] = nx;
        data[count++] = ny;
        data[count++] = nz;

        data[count++] = u;
        data[count++] = v;
    }

    private void ensureCapacity(int extra) {
        if (count + extra > data.length) {
            int newCapacity = data.length * 2;
            if (newCapacity < count + extra) {
                newCapacity = count + extra;
            }
            data = Arrays.copyOf(data, newCapacity);
        }
    }

    public void clear() {
        count = 0;
    }

    public float[] getData() {
        return data;
    }

    public int getCount() {
        return count;
    }

    public int getVertexCount() {
        return count / VERTEX_SIZE;
    }

    public boolean isEmpty() {
        return count == 0;
    }
}
