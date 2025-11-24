package com.mineshaft.world.interaction;

import com.mineshaft.block.GameBlock;
import com.mineshaft.entity.Camera;
import com.mineshaft.player.Player;
import com.mineshaft.world.World;

/**
 * Coordinates block breaking and placing interactions.
 * Provides a simple API for game input handling.
 * 
 * Features:
 * - Manages mouse button state
 * - Updates cooldown timers
 * - Delegates to BlockBreaker and BlockPlacer
 */
public class BlockInteractionHandler {

    private final BlockBreaker breaker;
    private final BlockPlacer placer;

    private boolean breakButtonPressed = false;
    private boolean placeButtonPressed = false;

    /**
     * Create a new block interaction handler.
     * 
     * @param world         The world to interact with
     * @param player        The player performing interactions
     * @param camera        The camera for raycast direction
     * @param reachDistance Maximum reach distance
     */
    public BlockInteractionHandler(World world, Player player, Camera camera, float reachDistance) {
        this.breaker = new BlockBreaker(world, player, camera, reachDistance);
        this.placer = new BlockPlacer(world, player, camera, reachDistance);
    }

    /**
     * Update cooldown timers.
     * Call this every frame in game loop.
     * 
     * @param delta Time since last frame in seconds
     */
    public void update(float delta) {
        breaker.update(delta);
        placer.update(delta);
    }

    /**
     * Handle block breaking input.
     * Call this from input handling with current mouse button state.
     * 
     * @param mousePressed true if break button (left mouse) is currently pressed
     */
    public void handleBreakInput(boolean mousePressed) {
        if (mousePressed) {
            // Try to break while button held (respects cooldown)
            breaker.tryBreakBlock();
        }
        breakButtonPressed = mousePressed;
    }

    /**
     * Handle block placing input.
     * Call this from input handling with current mouse button state.
     * 
     * @param mousePressed  true if place button (right mouse) is currently pressed
     * @param selectedBlock The block type to place
     */
    public void handlePlaceInput(boolean mousePressed, GameBlock selectedBlock) {
        if (mousePressed) {
            // Try to place while button held (respects cooldown)
            placer.tryPlaceBlock(selectedBlock);
        }
        placeButtonPressed = mousePressed;
    }

    /**
     * Check if break button is currently held.
     * 
     * @return true if breaking
     */
    public boolean isBreaking() {
        return breakButtonPressed;
    }

    /**
     * Check if place button is currently held.
     * 
     * @return true if placing
     */
    public boolean isPlacing() {
        return placeButtonPressed;
    }
}
