package sh.joey.mc.pet.entity;

import org.bukkit.util.EulerAngle;

/**
 * Definition for a single armor stand part of a pet.
 *
 * @param name            Unique name for this part (e.g., "head", "body", "leg_front_left")
 * @param offsetX         X offset from the pet's center position
 * @param offsetY         Y offset from the pet's center position
 * @param offsetZ         Z offset from the pet's center position
 * @param texture         Base64 texture string for the player head
 * @param defaultPose     Default head pose for this part
 * @param small           Whether to use a small armor stand
 * @param useRightArm     If true, equip item in right hand instead of helmet
 */
public record PetPart(
        String name,
        double offsetX,
        double offsetY,
        double offsetZ,
        String texture,
        EulerAngle defaultPose,
        boolean small,
        boolean useRightArm
) {
    /**
     * Creates a standard helmet-based part.
     */
    public static PetPart helmet(String name, double offsetX, double offsetY, double offsetZ, String texture) {
        return new PetPart(name, offsetX, offsetY, offsetZ, texture, EulerAngle.ZERO, true, false);
    }

    /**
     * Creates a helmet-based part with a custom pose.
     */
    public static PetPart helmet(String name, double offsetX, double offsetY, double offsetZ, String texture, EulerAngle pose) {
        return new PetPart(name, offsetX, offsetY, offsetZ, texture, pose, true, false);
    }
}
