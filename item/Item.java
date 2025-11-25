package com.mineshaft.item;

import com.mineshaft.item.properties.ItemProperties;

/**
 * Base item class (like Minecraft's Item)
 * Represents items in inventory, not in world
 */
public class Item {
    protected final ItemProperties properties;
    
    public Item(ItemProperties properties) {
        this.properties = properties;
    }
    
    // Property getters
    public int getMaxStackSize() {
        return properties.getMaxStackSize();
    }
    
    public int getMaxDurability() {
        return properties.getMaxDurability();
    }
    
    public boolean isFood() {
        return properties.isFood();
    }
    
    public int getFoodValue() {
        return properties.getFoodValue();
    }
    
    public String getItemGroup() {
        return properties.getItemGroup();
    }
    
    // Behavior methods
    
    /**
     * Called when item is used (right-click)
     */
    public boolean onUse() {
        return false;
    }
    
    /**
     * Called when used on a block
     */
    public boolean onUseOnBlock(int x, int y, int z) {
        return false;
    }
    
    /**
     * Get mining speed multiplier for blocks
     */
    public float getMiningSpeed() {
        return 1.0f;
    }
    
    /**
     * Check if item is tool
     */
    public boolean isTool() {
        return properties.getMaxDurability() > 0;
    }
}