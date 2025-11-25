package com.mineshaft.world;

import com.mineshaft.block.GameBlock;

/**
 * Raycasting for block selection (Minecraft DDA algorithm)
 */
public class RayCast {

    public static class RayResult {
        public int x, y, z; // Block position
        public int px, py, pz; // Previous block position (for placing)
        public int face; // Hit face (0=bottom, 1=top, 2=north, 3=south, 4=west, 5=east)
        public boolean hit;
        public float distance;

        public RayResult() {
            this.hit = false;
        }
    }

    /**
     * Cast ray from camera position in direction
     * 
     * @param world       World reference
     * @param px          Player X
     * @param py          Player Y (eye level)
     * @param pz          Player Z
     * @param dx          Direction X
     * @param dy          Direction Y
     * @param dz          Direction Z
     * @param maxDistance Max ray distance
     * @return RayResult with hit information
     */
    public static RayResult cast(World world, float px, float py, float pz,
            float dx, float dy, float dz, float maxDistance) {
        RayResult result = new RayResult();

        // Normalize direction
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= length;
        dy /= length;
        dz /= length;

        // Current position
        float x = px;
        float y = py;
        float z = pz;

        // Step increments - FIXED: Smaller steps to catch corner intersections
        float stepSize = 0.05f;
        float stepX = dx * stepSize;
        float stepY = dy * stepSize;
        float stepZ = dz * stepSize;

        // Previous block position
        int prevBlockX = (int) Math.floor(x);
        int prevBlockY = (int) Math.floor(y);
        int prevBlockZ = (int) Math.floor(z);

        // Ray march
        for (float dist = 0; dist < maxDistance; dist += stepSize) {
            x += stepX;
            y += stepY;
            z += stepZ;

            int blockX = (int) Math.floor(x);
            int blockY = (int) Math.floor(y);
            int blockZ = (int) Math.floor(z);

            // Check if we hit a solid block
            GameBlock block = world.getBlock(blockX, blockY, blockZ);
            if (block != null && block.isSolid()) {
                result.hit = true;
                result.x = blockX;
                result.y = blockY;
                result.z = blockZ;
                result.px = prevBlockX;
                result.py = prevBlockY;
                result.pz = prevBlockZ;
                result.distance = dist;

                // Determine hit face
                if (blockX != prevBlockX) {
                    result.face = blockX > prevBlockX ? 4 : 5; // West or East
                } else if (blockY != prevBlockY) {
                    result.face = blockY > prevBlockY ? 0 : 1; // Bottom or Top
                } else if (blockZ != prevBlockZ) {
                    result.face = blockZ > prevBlockZ ? 2 : 3; // North or South
                }

                return result;
            }

            // Store previous block
            prevBlockX = blockX;
            prevBlockY = blockY;
            prevBlockZ = blockZ;
        }

        return result;
    }
}