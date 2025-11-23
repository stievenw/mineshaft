package com.mineshaft.block;

import com.mineshaft.block.properties.BlockProperties;
import com.mineshaft.block.properties.Material;

/**
 * Base block class (FIXED version)
 */
public class Block {
    protected final BlockProperties properties;
    
    public Block(BlockProperties properties) {
        this.properties = properties;
    }
    
    public Material getMaterial() {
        return properties.getMaterial();
    }
    
    public float getHardness() {
        return properties.getHardness();
    }
    
    public float getResistance() {
        return properties.getResistance();
    }
    
    /**
     * ✅ FIXED: Use correct getter
     */
    public boolean requiresCorrectTool() {
        return properties.isRequiresCorrectTool();
    }
    
    public String getToolType() {
        return properties.getToolType();
    }
    
    public int getLightLevel() {
        return properties.getLightLevel();
    }
    
    public float[] getColor() {
        return properties.getColor();
    }
    
    public void onPlace() {
    }
    
    public void onBreak() {
    }
    
    public boolean onUse() {
        return false;
    }
    
    public String getDroppedItem() {
        return null;
    }
    
    public void randomTick() {
    }
    
    public boolean isSolid() {
        return getMaterial().isSolid();
    }
    
    public boolean isLiquid() {
        return getMaterial().isLiquid();
    }
    
    public boolean isReplaceable() {
        return getMaterial().isReplaceable();
    }
    
    public boolean isAir() {
        return this == Blocks.AIR;
    }
}