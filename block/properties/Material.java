package com.mineshaft.block.properties;

/**
 * Material types for blocks (like Minecraft's Material)
 * Defines basic physical properties
 */
public class Material {
    private final boolean solid;
    private final boolean blocksMovement;
    private final boolean liquid;
    private final boolean replaceable;
    
    private Material(boolean solid, boolean blocksMovement, boolean liquid, boolean replaceable) {
        this.solid = solid;
        this.blocksMovement = blocksMovement;
        this.liquid = liquid;
        this.replaceable = replaceable;
    }
    
    public boolean isSolid() { return solid; }
    public boolean blocksMovement() { return blocksMovement; }
    public boolean isLiquid() { return liquid; }
    public boolean isReplaceable() { return replaceable; }
    
    // Built-in materials (like Minecraft)
    public static final Material AIR = new Material(false, false, false, true);
    public static final Material STONE = new Material(true, true, false, false);
    public static final Material DIRT = new Material(true, true, false, false);
    public static final Material WOOD = new Material(true, true, false, false);
    public static final Material PLANT = new Material(false, false, false, false);
    public static final Material WATER = new Material(false, false, true, false);
    public static final Material SAND = new Material(true, true, false, false);
    public static final Material LEAVES = new Material(true, false, false, false);
    
    /**
     * Builder for custom materials
     */
    public static class Builder {
        private boolean solid = true;
        private boolean blocksMovement = true;
        private boolean liquid = false;
        private boolean replaceable = false;
        
        public Builder solid() {
            this.solid = true;
            return this;
        }
        
        public Builder notSolid() {
            this.solid = false;
            this.blocksMovement = false;
            return this;
        }
        
        public Builder liquid() {
            this.liquid = true;
            this.blocksMovement = false;
            return this;
        }
        
        public Builder replaceable() {
            this.replaceable = true;
            return this;
        }
        
        public Material build() {
            return new Material(solid, blocksMovement, liquid, replaceable);
        }
    }
}