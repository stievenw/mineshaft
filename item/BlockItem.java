package com.mineshaft.item;

import com.mineshaft.block.GameBlock;
import com.mineshaft.item.properties.ItemProperties;

/**
 * Item that places a block when used (like Minecraft's BlockItem)
 */
public class BlockItem extends Item {
    private final GameBlock block;

    public BlockItem(GameBlock block, ItemProperties properties) {
        super(properties);
        this.block = block;
    }

    public GameBlock getBlock() {
        return block;
    }

    @Override
    public boolean onUseOnBlock(int x, int y, int z) {
        // Place block logic (will implement later)
        return true;
    }
}