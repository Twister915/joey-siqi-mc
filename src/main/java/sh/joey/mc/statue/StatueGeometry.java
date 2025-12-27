package sh.joey.mc.statue;

import java.util.List;

/**
 * Defines the geometry of a Minecraft player model for statue generation.
 * Contains body part dimensions, UV coordinates, and positioning.
 */
public final class StatueGeometry {

    /**
     * Scale factor: each skin pixel becomes NxN blocks.
     */
    public static final int SCALE = 4;

    /**
     * Total height of the statue in blocks (32 pixels * 4 scale).
     */
    public static final int TOTAL_HEIGHT = 32 * SCALE; // 128 blocks

    /**
     * The six faces of a rectangular body part.
     */
    public enum Face {
        FRONT, BACK, LEFT, RIGHT, TOP, BOTTOM
    }

    /**
     * UV coordinate mapping for a face.
     *
     * @param u      top-left U coordinate in skin texture
     * @param v      top-left V coordinate in skin texture
     * @param width  width of the face in pixels
     * @param height height of the face in pixels
     */
    public record UvMapping(int u, int v, int width, int height) {}

    /**
     * A body part with its dimensions, position, and UV mappings.
     */
    public record BodyPart(
            String name,
            int width,    // X dimension in pixels
            int height,   // Y dimension in pixels
            int depth,    // Z dimension in pixels
            int offsetX,  // X offset from statue center (in pixels)
            int offsetY,  // Y offset from ground (in pixels)
            int offsetZ,  // Z offset from statue center (in pixels)
            UvMapping front,
            UvMapping back,
            UvMapping left,
            UvMapping right,
            UvMapping top,
            UvMapping bottom
    ) {
        public UvMapping getFace(Face face) {
            return switch (face) {
                case FRONT -> front;
                case BACK -> back;
                case LEFT -> left;
                case RIGHT -> right;
                case TOP -> top;
                case BOTTOM -> bottom;
            };
        }
    }

    // Body part definitions with UV coordinates from the Minecraft skin format
    // Positions are centered on X=0, Z=0 at ground level

    public static final BodyPart HEAD = new BodyPart(
            "head",
            8, 8, 8,        // 8x8x8 cube
            -4, 24, -4,     // Centered above torso
            new UvMapping(8, 8, 8, 8),    // Front
            new UvMapping(24, 8, 8, 8),   // Back
            new UvMapping(0, 8, 8, 8),    // Left (character's left = viewer's right)
            new UvMapping(16, 8, 8, 8),   // Right (character's right = viewer's left)
            new UvMapping(8, 0, 8, 8),    // Top
            new UvMapping(16, 0, 8, 8)    // Bottom
    );

    public static final BodyPart TORSO = new BodyPart(
            "torso",
            8, 12, 4,       // 8 wide, 12 tall, 4 deep
            -4, 12, -2,     // Centered, above legs
            new UvMapping(20, 20, 8, 12),  // Front
            new UvMapping(32, 20, 8, 12),  // Back
            new UvMapping(28, 20, 4, 12),  // Left
            new UvMapping(16, 20, 4, 12),  // Right
            new UvMapping(20, 16, 8, 4),   // Top
            new UvMapping(28, 16, 8, 4)    // Bottom
    );

    public static final BodyPart RIGHT_ARM = new BodyPart(
            "right_arm",
            4, 12, 4,       // 4 wide, 12 tall, 4 deep
            -8, 12, -2,     // To the right of torso (character's right = viewer's left)
            new UvMapping(44, 20, 4, 12),  // Front
            new UvMapping(52, 20, 4, 12),  // Back
            new UvMapping(48, 20, 4, 12),  // Left (inner)
            new UvMapping(40, 20, 4, 12),  // Right (outer)
            new UvMapping(44, 16, 4, 4),   // Top
            new UvMapping(48, 16, 4, 4)    // Bottom
    );

    public static final BodyPart LEFT_ARM = new BodyPart(
            "left_arm",
            4, 12, 4,       // 4 wide, 12 tall, 4 deep
            4, 12, -2,      // To the left of torso (character's left = viewer's right)
            new UvMapping(36, 52, 4, 12),  // Front
            new UvMapping(44, 52, 4, 12),  // Back
            new UvMapping(32, 52, 4, 12),  // Left (outer)
            new UvMapping(40, 52, 4, 12),  // Right (inner)
            new UvMapping(36, 48, 4, 4),   // Top
            new UvMapping(40, 48, 4, 4)    // Bottom
    );

    public static final BodyPart RIGHT_LEG = new BodyPart(
            "right_leg",
            4, 12, 4,       // 4 wide, 12 tall, 4 deep
            -4, 0, -2,      // Right side of center, on ground
            new UvMapping(4, 20, 4, 12),   // Front
            new UvMapping(12, 20, 4, 12),  // Back
            new UvMapping(8, 20, 4, 12),   // Left (inner)
            new UvMapping(0, 20, 4, 12),   // Right (outer)
            new UvMapping(4, 16, 4, 4),    // Top
            new UvMapping(8, 16, 4, 4)     // Bottom
    );

    public static final BodyPart LEFT_LEG = new BodyPart(
            "left_leg",
            4, 12, 4,       // 4 wide, 12 tall, 4 deep
            0, 0, -2,       // Left side of center, on ground
            new UvMapping(20, 52, 4, 12),  // Front
            new UvMapping(28, 52, 4, 12),  // Back
            new UvMapping(24, 52, 4, 12),  // Left (outer)
            new UvMapping(16, 52, 4, 12),  // Right (inner)
            new UvMapping(20, 48, 4, 4),   // Top
            new UvMapping(24, 48, 4, 4)    // Bottom
    );

    /**
     * All body parts in rendering order (bottom to top).
     */
    public static final List<BodyPart> ALL_PARTS = List.of(
            RIGHT_LEG, LEFT_LEG, TORSO, RIGHT_ARM, LEFT_ARM, HEAD
    );

    /**
     * For legacy 64x32 skins, returns the right-side body part to mirror for the given left-side part.
     * Legacy skins don't have separate left arm/leg textures - they mirror the right side.
     *
     * @param part the body part to check
     * @return the corresponding right-side part to mirror, or null if no mirroring needed
     */
    public static BodyPart getLegacyMirrorPart(BodyPart part) {
        if (part == LEFT_ARM) return RIGHT_ARM;
        if (part == LEFT_LEG) return RIGHT_LEG;
        return null;
    }

    private StatueGeometry() {}
}
