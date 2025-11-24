package com.mineshaft.player;

import com.mineshaft.block.BlockRegistry;
import com.mineshaft.block.GameBlock;

/**
 * Player inventory system
 */
public class Inventory {
    private GameBlock[] hotbar;
    private int selectedSlot;

    public Inventory() {
        this.hotbar = new GameBlock[9];
        this.selectedSlot = 0;

        // Default creative inventory
        initializeCreativeInventory();
    }

    /**
     * Initialize with common blocks (creative mode)
     */
    private void initializeCreativeInventory() {
        hotbar[0] = BlockRegistry.GRASS_BLOCK;
        hotbar[1] = BlockRegistry.DIRT;
        hotbar[2] = BlockRegistry.STONE;
        hotbar[3] = BlockRegistry.COBBLESTONE;
        hotbar[4] = BlockRegistry.OAK_PLANKS;
        hotbar[5] = BlockRegistry.OAK_LOG;
        hotbar[6] = BlockRegistry.OAK_LEAVES;
        hotbar[7] = BlockRegistry.SAND;
        hotbar[8] = BlockRegistry.GRAVEL;
    }

    /**
     * Get currently selected block
     */
    public GameBlock getSelectedBlock() {
        return hotbar[selectedSlot];
    }

    /**
     * Select slot (0-8)
     */
    public void selectSlot(int slot) {
        if (slot >= 0 && slot < 9) {
            this.selectedSlot = slot;
        }
    }

    /**
     * Select next slot
     */
    public void nextSlot() {
        selectedSlot = (selectedSlot + 1) % 9;
    }

    /**
     * Select previous slot
     */
    public void prevSlot() {
        selectedSlot--;
        if (selectedSlot < 0)
            selectedSlot = 8;
    }

    /**
     * Set block in slot
     */
    public void setSlot(int slot, GameBlock block) {
        if (slot >= 0 && slot < 9) {
            hotbar[slot] = block;
        }
    }

    public int getSelectedSlot() {
        return selectedSlot;
    }

    public GameBlock[] getHotbar() {
        return hotbar;
    }
}