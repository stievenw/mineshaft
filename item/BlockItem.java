package com.mineshaft.item;

import com.mineshaft.block.Block;
import com.mineshaft.item.properties.ItemProperties;

/**
 * Item that places a block when used (like Minecraft's BlockItem)
 */
public class BlockItem extends Item {
    private final Block block;
    
    public BlockItem(Block block, ItemProperties properties) {
        super(properties);
        this.block = block;
    }
    
    public Block getBlock() {
        return block;
    }
    
    @Override
    public boolean onUseOnBlock(int x, int y, int z) {
        // Place block logic (will implement later)
        return true;
    }
}