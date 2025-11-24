package com.mineshaft.render;

import static org.lwjgl.opengl.GL11.*;

/**
 * Renders a wireframe outline around the selected block.
 * Shows which block the player is targeting with the crosshair.
 */
public class BlockOutlineRenderer {

    private static final float LINE_WIDTH = 2.0f;
    private static final float OFFSET = 0.002f; // Prevent z-fighting

    /**
     * Render outline around a block at the given position.
     * 
     * @param x Block X coordinate
     * @param y Block Y coordinate
     * @param z Block Z coordinate
     */
    public void render(int x, int y, int z) {
        // Save current state
        glPushAttrib(GL_ENABLE_BIT | GL_LINE_BIT | GL_CURRENT_BIT);

        // Setup outline rendering
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_LIGHTING);
        glEnable(GL_LINE_SMOOTH);
        glLineWidth(LINE_WIDTH);

        // Black outline with slight transparency
        glColor4f(0.0f, 0.0f, 0.0f, 0.4f);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // Expand slightly to prevent z-fighting
        float minX = x - OFFSET;
        float minY = y - OFFSET;
        float minZ = z - OFFSET;
        float maxX = x + 1 + OFFSET;
        float maxY = y + 1 + OFFSET;
        float maxZ = z + 1 + OFFSET;

        // Draw wireframe cube (12 edges)
        glBegin(GL_LINES);

        // Bottom face (Y = minY)
        glVertex3f(minX, minY, minZ);
        glVertex3f(maxX, minY, minZ);

        glVertex3f(maxX, minY, minZ);
        glVertex3f(maxX, minY, maxZ);

        glVertex3f(maxX, minY, maxZ);
        glVertex3f(minX, minY, maxZ);

        glVertex3f(minX, minY, maxZ);
        glVertex3f(minX, minY, minZ);

        // Top face (Y = maxY)
        glVertex3f(minX, maxY, minZ);
        glVertex3f(maxX, maxY, minZ);

        glVertex3f(maxX, maxY, minZ);
        glVertex3f(maxX, maxY, maxZ);

        glVertex3f(maxX, maxY, maxZ);
        glVertex3f(minX, maxY, maxZ);

        glVertex3f(minX, maxY, maxZ);
        glVertex3f(minX, maxY, minZ);

        // Vertical edges
        glVertex3f(minX, minY, minZ);
        glVertex3f(minX, maxY, minZ);

        glVertex3f(maxX, minY, minZ);
        glVertex3f(maxX, maxY, minZ);

        glVertex3f(maxX, minY, maxZ);
        glVertex3f(maxX, maxY, maxZ);

        glVertex3f(minX, minY, maxZ);
        glVertex3f(minX, maxY, maxZ);

        glEnd();

        // Restore state
        glPopAttrib();
    }

    /**
     * No resources to clean up.
     */
    public void cleanup() {
        // Nothing to cleanup
    }
}
