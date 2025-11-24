package com.mineshaft.block;

import com.mineshaft.block.properties.Material;
import com.mineshaft.resource.BlockData;
import com.mineshaft.resource.ResourceManager;

/**
 * Runtime representation of a block, loaded from JSON data.
 */
public class GameBlock {
    private final BlockData data;

    public GameBlock(BlockData data) {
        this.data = data;
    }

    public String getId() {
        return data.id;
    }

    public float getHardness() {
        return data.properties.hardness;
    }

    public float getResistance() {
        return data.properties.resistance;
    }

    public boolean requiresCorrectTool() {
        return data.properties.requiresTool;
    }

    public String getToolType() {
        return data.properties.toolType;
    }

    public int getLightLevel() {
        return data.properties.lightLevel;
    }

    public boolean isSolid() {
        return data.properties.isSolid;
    }

    public boolean isLiquid() {
        return data.properties.isLiquid;
    }

    public boolean isReplaceable() {
        return data.properties.isReplaceable;
    }

    public boolean isAir() {
        return "mineshaft:air".equals(data.id);
    }

    public Material getMaterial() {
        if (isAir())
            return Material.AIR;
        if (isLiquid())
            return Material.WATER;
        if (!isSolid())
            return Material.PLANT; // Approximation
        return Material.STONE; // Default
    }

    /**
     * Get texture path for a specific face.
     * 
     * @param face Face name (top, bottom, side, all)
     * @return Texture resource path
     */
    public String getTexture(String face) {
        if (data.textures.containsKey(face)) {
            return ResourceManager.getTexturePath(data.textures.get(face));
        }
        if (data.textures.containsKey("all")) {
            return ResourceManager.getTexturePath(data.textures.get("all"));
        }
        // Fallback for side/top/bottom if not explicitly set but "all" is missing
        if (face.equals("top") && data.textures.containsKey("top"))
            return ResourceManager.getTexturePath(data.textures.get("top"));
        if (face.equals("bottom") && data.textures.containsKey("bottom"))
            return ResourceManager.getTexturePath(data.textures.get("bottom"));
        if (face.equals("side") && data.textures.containsKey("side"))
            return ResourceManager.getTexturePath(data.textures.get("side"));

        return "assets/mineshaft/textures/missing.png";
    }
}
