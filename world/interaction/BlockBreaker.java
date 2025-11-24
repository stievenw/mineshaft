package com.mineshaft.world.interaction;

import com.mineshaft.entity.Camera;
import com.mineshaft.player.Player;
import com.mineshaft.world.RayCast;
import com.mineshaft.world.World;
import com.mineshaft.block.Blocks;

/**
 * Handles block breaking logic with hold-to-break functionality.
 * 
 * Features:
 * - Continuous breaking while mouse held
 * - Configurable cooldown between breaks
 * - Reach distance validation
 */
public class BlockBreaker {

    private float cooldownTimer = 0f;
    private static final float COOLDOWN_DURATION = 0.25f; // 250ms between breaks

    private final World world;
    private final Player player;
    private final Camera camera;
    private final float reachDistance;

    /**
     * Create a new block breaker.
     * 
     * @param world         The world to break blocks in
     * @param player        The player breaking blocks
     * @param camera        The camera for raycast direction
     * @param reachDistance Maximum reach distance
     */
    public BlockBreaker(World world, Player player, Camera camera, float reachDistance) {
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
     * Check if breaking is allowed (cooldown elapsed).
     * 
     * @return true if can break now
     */
    public boolean canBreak() {
        return cooldownTimer <= 0;
    }

    /**
     * Attempt to break the block the player is looking at.
     * 
     * @return true if a block was broken
     */
    public boolean tryBreakBlock() {
        if (!canBreak()) {
            return false;
        }

        // Get look direction from camera
        float[] dir = camera.getForwardVector();

        // Cast ray to find target block
        RayCast.RayResult ray = RayCast.cast(
                world,
                player.getX(),
                player.getEyeY(),
                player.getZ(),
                dir[0], dir[1], dir[2],
                reachDistance);

        // Break the block if hit
        if (ray.hit) {
            world.setBlock(ray.x, ray.y, ray.z, Blocks.AIR);
            cooldownTimer = COOLDOWN_DURATION;
            return true;
        }

        return false;
    }
}
