package sh.joey.mc.statue;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Builds wool statues in the world by placing blocks.
 */
public final class StatueBuilder {

    private final WoolColorMapper colorMapper;

    public StatueBuilder(WoolColorMapper colorMapper) {
        this.colorMapper = colorMapper;
    }

    /**
     * Builds a statue at the given location facing the specified direction.
     * If WorldEdit/FAWE is available, changes are recorded in the player's undo history.
     *
     * @param player   the player building the statue (for undo history)
     * @param world    the world to build in
     * @param center   the center point of the statue base
     * @param facing   the direction the statue should face
     * @param skin     the skin texture to use
     * @return number of blocks placed
     */
    public int build(Player player, World world, Location center, CardinalDirection facing, SkinTexture skin) {
        int blocksPlaced = 0;
        int scale = StatueGeometry.SCALE;

        try (var placer = WorldEditSupport.createPlacer(player, world)) {
            for (StatueGeometry.BodyPart part : StatueGeometry.ALL_PARTS) {
                blocksPlaced += buildBodyPart(placer, center, facing, skin, part, scale);
            }
        }

        return blocksPlaced;
    }

    private int buildBodyPart(WorldEditSupport.BlockPlacer placer, Location center, CardinalDirection facing,
                               SkinTexture skin, StatueGeometry.BodyPart part, int scale) {
        int blocksPlaced = 0;

        for (StatueGeometry.Face face : StatueGeometry.Face.values()) {
            blocksPlaced += buildFace(placer, center, facing, skin, part, face, scale);
        }

        return blocksPlaced;
    }

    private int buildFace(WorldEditSupport.BlockPlacer placer, Location center, CardinalDirection facing,
                          SkinTexture skin, StatueGeometry.BodyPart part,
                          StatueGeometry.Face face, int scale) {
        StatueGeometry.UvMapping uv = part.getFace(face);

        // Extract face pixels from skin
        int[][] facePixels;

        if (skin.isLegacy() && uv.v() >= 32) {
            // Legacy skins: left limbs use mirrored right limb textures
            StatueGeometry.BodyPart mirrorPart = StatueGeometry.getLegacyMirrorPart(part);
            if (mirrorPart == null) {
                return 0; // Shouldn't happen for parts with v >= 32
            }
            StatueGeometry.UvMapping mirrorUv = mirrorPart.getFace(face);
            facePixels = skin.extractFaceMirrored(mirrorUv);
        } else {
            facePixels = skin.extractFace(uv);
        }

        // Map pixels to nearest wool colors
        Material[][] materials = colorMapper.mapFace(facePixels, uv.width(), uv.height());

        int blocksPlaced = 0;

        // Place blocks for each pixel (scaled up by 4x)
        for (int texY = 0; texY < uv.height(); texY++) {
            for (int texX = 0; texX < uv.width(); texX++) {
                Material wool = materials[texY][texX];
                if (wool == null) {
                    continue; // Transparent pixel
                }

                // Calculate base position for this pixel patch (before scaling)
                int[] localPos = calculateLocalPosition(part, face, texX, texY, uv);

                // Place a scale x scale patch of blocks
                // The patch dimensions depend on which face we're building:
                // - FRONT/BACK: patch spans X and Y, Z is fixed
                // - LEFT/RIGHT: patch spans Z and Y, X is fixed
                // - TOP/BOTTOM: patch spans X and Z, Y is fixed
                //
                // For faces at the "max" side of an axis (FRONT, LEFT, TOP), we need to
                // offset the fixed coordinate by (scale-1) to place at the outer edge.
                for (int d1 = 0; d1 < scale; d1++) {
                    for (int d2 = 0; d2 < scale; d2++) {
                        int scaledX, scaledY, scaledZ;

                        switch (face) {
                            case FRONT -> {
                                scaledX = localPos[0] * scale + d1;
                                scaledY = localPos[1] * scale + d2;
                                scaledZ = localPos[2] * scale + (scale - 1); // Outer edge
                            }
                            case BACK -> {
                                scaledX = localPos[0] * scale + d1;
                                scaledY = localPos[1] * scale + d2;
                                scaledZ = localPos[2] * scale; // Inner edge
                            }
                            case LEFT -> {
                                scaledX = localPos[0] * scale + (scale - 1); // Outer edge
                                scaledY = localPos[1] * scale + d1;
                                scaledZ = localPos[2] * scale + d2;
                            }
                            case RIGHT -> {
                                scaledX = localPos[0] * scale; // Inner edge
                                scaledY = localPos[1] * scale + d1;
                                scaledZ = localPos[2] * scale + d2;
                            }
                            case TOP -> {
                                scaledX = localPos[0] * scale + d1;
                                scaledY = localPos[1] * scale + (scale - 1); // Outer edge
                                scaledZ = localPos[2] * scale + d2;
                            }
                            case BOTTOM -> {
                                scaledX = localPos[0] * scale + d1;
                                scaledY = localPos[1] * scale; // Inner edge
                                scaledZ = localPos[2] * scale + d2;
                            }
                            default -> throw new IllegalStateException("Unknown face: " + face);
                        }

                        // Apply rotation
                        int[] worldDelta = facing.transform(scaledX, scaledY, scaledZ);

                        // Calculate final world position
                        int worldX = center.getBlockX() + worldDelta[0];
                        int worldY = center.getBlockY() + worldDelta[1];
                        int worldZ = center.getBlockZ() + worldDelta[2];

                        // Place the block
                        placer.setBlock(worldX, worldY, worldZ, wool);
                        blocksPlaced++;
                    }
                }
            }
        }

        return blocksPlaced;
    }

    /**
     * Calculates the local position (before rotation) for a pixel on a face.
     * Returns [x, y, z] in local statue coordinates.
     */
    private int[] calculateLocalPosition(StatueGeometry.BodyPart part, StatueGeometry.Face face,
                                          int texX, int texY, StatueGeometry.UvMapping uv) {
        int ox = part.offsetX();
        int oy = part.offsetY();
        int oz = part.offsetZ();
        int w = part.width();
        int h = part.height();
        int d = part.depth();

        // Texture Y is inverted (0 = top in texture, but we want 0 = bottom in world)
        int flippedY = uv.height() - 1 - texY;

        return switch (face) {
            case FRONT -> new int[]{
                    ox + texX,           // X increases left to right
                    oy + flippedY,       // Y increases bottom to top
                    oz + d - 1           // Z at front of part
            };
            case BACK -> new int[]{
                    ox + (w - 1 - texX), // X is mirrored when viewing from back
                    oy + flippedY,       // Y same
                    oz                   // Z at back of part
            };
            case LEFT -> new int[]{
                    ox + w - 1,          // X at left side of part
                    oy + flippedY,       // Y same
                    oz + texX            // Z: texX=0 is back, texX=d-1 is front
            };
            case RIGHT -> new int[]{
                    ox,                  // X at right side of part
                    oy + flippedY,       // Y same
                    oz + (d - 1 - texX)  // Z: texX=0 is front, texX=d-1 is back
            };
            case TOP -> new int[]{
                    ox + texX,           // X same as front
                    oy + h - 1,          // Y at top
                    oz + texY            // Z: texture Y is back-to-front
            };
            case BOTTOM -> new int[]{
                    ox + texX,           // X same as front
                    oy,                  // Y at bottom
                    oz + (d - 1 - texY)  // Z: texture Y is front-to-back (flipped from top)
            };
        };
    }

    /**
     * Estimates the number of blocks that will be placed for a statue.
     */
    public int estimateBlockCount(SkinTexture skin) {
        int count = 0;
        int scale = StatueGeometry.SCALE;
        int blocksPerPixel = scale * scale; // 4x4 = 16 blocks per pixel

        for (StatueGeometry.BodyPart part : StatueGeometry.ALL_PARTS) {
            for (StatueGeometry.Face face : StatueGeometry.Face.values()) {
                StatueGeometry.UvMapping uv = part.getFace(face);

                int[][] facePixels;
                if (skin.isLegacy() && uv.v() >= 32) {
                    // Legacy skins: use mirrored right limb textures
                    StatueGeometry.BodyPart mirrorPart = StatueGeometry.getLegacyMirrorPart(part);
                    if (mirrorPart == null) {
                        continue;
                    }
                    StatueGeometry.UvMapping mirrorUv = mirrorPart.getFace(face);
                    facePixels = skin.extractFaceMirrored(mirrorUv);
                } else {
                    facePixels = skin.extractFace(uv);
                }

                for (int y = 0; y < uv.height(); y++) {
                    for (int x = 0; x < uv.width(); x++) {
                        int alpha = (facePixels[y][x] >> 24) & 0xFF;
                        if (alpha >= 128) {
                            count += blocksPerPixel;
                        }
                    }
                }
            }
        }

        return count;
    }
}
