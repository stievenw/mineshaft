package com.mineshaft.player;

import com.mineshaft.block.Block;
import com.mineshaft.block.Blocks;

/**
 * Player inventory system
 */
public class Inventory {
    private Block[] hotbar;
    private int selectedSlot;
    
    public Inventory() {
        this.hotbar = new Block[9];
        this.selectedSlot = 0;
        
        // Default creative inventory
        initializeCreativeInventory();
    }
    
    /**
     * Initialize with common blocks (creative mode)
     */
    private void initializeCreativeInventory() {
        hotbar[0] = Blocks.GRASS;
        hotbar[1] = Blocks.DIRT;
        hotbar[2] = Blocks.STONE;
        hotbar[3] = Blocks.COBBLESTONE;
        hotbar[4] = Blocks.WOOD;
        hotbar[5] = Blocks.LOG;
        hotbar[6] = Blocks.LEAVES;
        hotbar[7] = Blocks.SAND;
        hotbar[8] = Blocks.GRAVEL;
    }
    
    /**
     * Get currently selected block
     */
    public Block getSelectedBlock() {
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
        if (selectedSlot < 0) selectedSlot = 8;
    }
    
    /**
     * Set block in slot
     */
    public void setSlot(int slot, Block block) {
        if (slot >= 0 && slot < 9) {
            hotbar[slot] = block;
        }
    }
    
    public int getSelectedSlot() {
        return selectedSlot;
    }
    
    public Block[] getHotbar() {
        return hotbar;
    }
}