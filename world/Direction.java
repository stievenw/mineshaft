package com.mineshaft.world;

/**
 * Represents the six cardinal directions of a block face.
 */
public enum Direction {
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0),
    TOP(0, 1, 0),
    BOTTOM(0, -1, 0);

    private final int offsetX;
    private final int offsetY;
    private final int offsetZ;

    Direction(int offsetX, int offsetY, int offsetZ) {
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
    }

    public int getOffsetX() {
        return offsetX;
    }

    public int getOffsetY() {
        return offsetY;
    }

    public int getOffsetZ() {
        return offsetZ;
    }

    /**
     * Get the texture key name for this direction.
     * Maps to JSON texture keys (top, bottom, side).
     */
    public String getTextureName() {
        switch (this) {
            case TOP:
                return "top";
            case BOTTOM:
                return "bottom";
            case NORTH:
            case SOUTH:
            case EAST:
            case WEST:
                return "side";
            default:
                return "all";
        }
    }

    /**
     * Get normal vector as float array [x, y, z]
     */
    public float[] getNormal() {
        return new float[] { offsetX, offsetY, offsetZ };
    }

    /**
     * Get opposite direction
     */
    public Direction getOpposite() {
        switch (this) {
            case NORTH:
                return SOUTH;
            case SOUTH:
                return NORTH;
            case WEST:
                return EAST;
            case EAST:
                return WEST;
            case TOP:
                return BOTTOM;
            case BOTTOM:
                return TOP;
            default:
                return this;
        }
    }
}