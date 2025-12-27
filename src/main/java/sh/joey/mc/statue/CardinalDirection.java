package sh.joey.mc.statue;

/**
 * Cardinal directions for statue facing with rotation transforms.
 */
public enum CardinalDirection {
    /**
     * Facing south (+Z direction). This is the default orientation.
     * Minecraft yaw 0 = south.
     */
    SOUTH,

    /**
     * Facing west (-X direction).
     * Minecraft yaw 90 = west.
     */
    WEST,

    /**
     * Facing north (-Z direction).
     * Minecraft yaw 180 = north.
     */
    NORTH,

    /**
     * Facing east (+X direction).
     * Minecraft yaw 270 = east.
     */
    EAST;

    /**
     * Converts a Minecraft yaw angle to the nearest cardinal direction.
     * Minecraft yaw: 0 = south, 90 = west, 180 = north, 270 = east
     */
    public static CardinalDirection fromYaw(float yaw) {
        // Normalize to 0-360
        float normalized = ((yaw % 360) + 360) % 360;

        if (normalized >= 315 || normalized < 45) {
            return SOUTH;
        } else if (normalized >= 45 && normalized < 135) {
            return WEST;
        } else if (normalized >= 135 && normalized < 225) {
            return NORTH;
        } else {
            return EAST;
        }
    }

    /**
     * Transforms local statue coordinates to world-relative offsets.
     * Local coordinate system:
     * - X: positive = right from statue's perspective (viewer's left)
     * - Y: positive = up
     * - Z: positive = forward (toward viewer)
     *
     * @param localX local X coordinate
     * @param localY local Y coordinate (unchanged by rotation)
     * @param localZ local Z coordinate
     * @return array of [worldDeltaX, worldDeltaY, worldDeltaZ]
     */
    public int[] transform(int localX, int localY, int localZ) {
        return switch (this) {
            case SOUTH -> new int[]{-localX, localY, localZ};
            case NORTH -> new int[]{localX, localY, -localZ};
            case EAST -> new int[]{localZ, localY, localX};
            case WEST -> new int[]{-localZ, localY, -localX};
        };
    }

    /**
     * Gets the opposite direction (for back-facing surfaces).
     */
    public CardinalDirection opposite() {
        return switch (this) {
            case SOUTH -> NORTH;
            case NORTH -> SOUTH;
            case EAST -> WEST;
            case WEST -> EAST;
        };
    }
}
