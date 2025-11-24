package com.mineshaft.world.interaction;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;
import com.mineshaft.entity.Camera;
import com.mineshaft.player.Player;
import com.mineshaft.world.RayCast;
import com.mineshaft.world.World;

/**
 * Handles block placement logic with hold-to-place and collision detection.
 * 
 * Features:
 * - Continuous placing while mouse held
 * - Configurable cooldown between placements
 * - Player collision check (can't place where player stands)
 * - Entity collision check (can't place where entities are)
 */
public class BlockPlacer {

    private float cooldownTimer = 0f;
    private static final float COOLDOWN_DURATION = 0.25f; // 250ms between placements

    private final World world;
    private final Player player;
    private final Camera camera;
    private final float reachDistance;

    /**
     * Create a new block placer.
     * 
     * @param world         The world to place blocks in
     * @param player        The player placing blocks
     * @param camera        The camera for raycast direction
     * @param reachDistance Maximum reach distance
     */
    public BlockPlacer(World world, Player player, Camera camera, float reachDistance) {
        this.world = world;
        this.player = player;
        this.camera = camera;
        this.reachDistance = reachDistance;
    }

    /**
     * Update cooldown timer.
     * Call this every frame.
     * 
     * @param delta Time since last frame in seconds
     */
    public void update(float delta) {
        if (cooldownTimer > 0) {
            cooldownTimer -= delta;
        }
    }

    /**
     * Check if placing is allowed (cooldown elapsed).
     * 
     * @return true if can place now
     */
    public boolean canPlace() {
        return cooldownTimer <= 0;
    }

    /**
     * Attempt to place a block where the player is looking.
     * 
     * @param block The block type to place
     * @return true if a block was placed
     */
    public boolean tryPlaceBlock(GameBlock block) {
        if (!canPlace() || block == null || block == BlockRegistry.AIR) {
            return false;
        }

        // Get look direction from camera
        float[] dir = camera.getForwardVector();

        // Cast ray to find placement position
        RayCast.RayResult ray = RayCast.cast(
                world,
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                dir[0], dir[1], dir[2],
                reachDistance);

        // Place block at previous position (adjacent to hit block)
        if (ray.hit) {
            int px = ray.px;
            int py = ray.py;
            int pz = ray.pz;

            // Check if placement position is valid
            if (!isValidPlacement(px, py, pz)) {
                return false;
            }

            // Place the block
            world.setBlock(px, py, pz, block);
            cooldownTimer = COOLDOWN_DURATION;
            return true;
        }

        return false;
    }

    /**
     * Check if a block can be placed at the given position.
     * Validates collision with player and entities.
     * 
     * @param x Block X position
     * @param y Block Y position
     * @param z Block Z position
     * @return true if placement is valid
     */
    private boolean isValidPlacement(int x, int y, int z) {
        // Check if position already has a solid block
        GameBlock existingBlock = world.getBlock(x, y, z);
        if (existingBlock != null && existingBlock.isSolid()) {
            return false;
        }

        // Check player collision
        if (isPlayerColliding(x, y, z)) {
            System.out.println("Cannot place block: Player is in the way");
            return false;
        }

        // Check entity collision (for future entity system)
        if (isEntityColliding(x, y, z)) {
            System.out.println("Cannot place block: Entity is in the way");
            return false;
        }

        return true;
    }

    /**
     * Check if player bounding box overlaps with block position.
     * 
     * Player bounding box: 0.6 wide, 1.8 tall
     * Block bounding box: 1.0 x 1.0 x 1.0
     * 
     * @param blockX Block X position
     * @param blockY Block Y position
     * @param blockZ Block Z position
     * @return true if player collides with block position
     */
    private boolean isPlayerColliding(int blockX, int blockY, int blockZ) {
        // Player bounding box (centered on player position)
        float playerX = player.getX();
        float playerY = player.getY();
        float playerZ = player.getZ();

        // Player dimensions: 0.6 wide (0.3 on each side), 1.8 tall
        float playerMinX = playerX - 0.3f;
        float playerMaxX = playerX + 0.3f;
        float playerMinY = playerY;
        float playerMaxY = playerY + 1.8f;
        float playerMinZ = playerZ - 0.3f;
        float playerMaxZ = playerZ + 0.3f;

        // Block bounding box
        float blockMinX = blockX;
        float blockMaxX = blockX + 1.0f;
        float blockMinY = blockY;
        float blockMaxY = blockY + 1.0f;
        float blockMinZ = blockZ;
        float blockMaxZ = blockZ + 1.0f;

        // AABB collision check
        boolean collideX = playerMaxX > blockMinX && playerMinX < blockMaxX;
        boolean collideY = playerMaxY > blockMinY && playerMinY < blockMaxY;
        boolean collideZ = playerMaxZ > blockMinZ && playerMinZ < blockMaxZ;

        return collideX && collideY && collideZ;
    }

    /**
     * Check if any entity bounding box overlaps with block position.
     * 
     * @param blockX Block X position
     * @param blockY Block Y position
     * @param blockZ Block Z position
     * @return true if any entity collides with block position
     */
    private boolean isEntityColliding(int blockX, int blockY, int blockZ) {
        // Future: Check entity collisions when entity system is added
        return false;
    }
}
