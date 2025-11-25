package com.mineshaft.item.properties;

/**
 * Properties for items (like Minecraft's Item.Properties)
 */
public class ItemProperties {
    private int maxStackSize = 64;
    private int maxDurability = 0;
    private boolean isFood = false;
    private int foodValue = 0;
    private String itemGroup = "misc";
    
    public ItemProperties() {}
    
    /**
     * Set max stack size (default 64)
     */
    public ItemProperties maxStackSize(int size) {
        this.maxStackSize = size;
        return this;
    }
    
    /**
     * Set max durability (for tools, armor)
     */
    public ItemProperties maxDurability(int durability) {
        this.maxDurability = durability;
        if (durability > 0) {
            this.maxStackSize = 1; // Tools don't stack
        }
        return this;
    }
    
    /**
     * Mark as food item
     */
    public ItemProperties food(int foodValue) {
        this.isFood = true;
        this.foodValue = foodValue;
        return this;
    }
    
    /**
     * Set creative tab group
     */
    public ItemProperties group(String group) {
        this.itemGroup = group;
        return this;
    }
    
    // Getters
    public int getMaxStackSize() { return maxStackSize; }
    public int getMaxDurability() { return maxDurability; }
    public boolean isFood() { return isFood; }
    public int getFoodValue() { return foodValue; }
    public String getItemGroup() { return itemGroup; }
}