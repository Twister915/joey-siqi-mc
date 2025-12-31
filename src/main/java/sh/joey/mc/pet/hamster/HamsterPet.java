package sh.joey.mc.pet.hamster;

import org.bukkit.entity.Player;
import org.bukkit.util.EulerAngle;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.pet.Pet;
import sh.joey.mc.pet.PetManager;
import sh.joey.mc.pet.PetType;
import sh.joey.mc.pet.entity.PetPart;

import java.util.List;

/**
 * A cute hamster pet that follows the player around.
 * The hamster is composed of multiple armor stands with custom skull textures
 * that animate for idle and walking states.
 */
public final class HamsterPet extends Pet {

    // Animation parameters
    private static final double ANIMATION_SPEED_IDLE = 0.08;
    private static final double ANIMATION_SPEED_MOVING = 0.3;
    private static final double IDLE_AMPLITUDE = 3.0;
    private static final double WALK_AMPLITUDE = 8.0;
    private static final double HEAD_BOB_AMPLITUDE = 4.0;

    public HamsterPet(SiqiJoeyPlugin plugin, Player owner, PetManager manager) {
        super(plugin, owner, manager);
    }

    @Override
    public PetType getType() {
        return PetType.HAMSTER;
    }

    @Override
    protected List<PetPart> defineParts() {
        // The hamster is built from head and body skulls positioned
        // on small armor stands. Offsets are relative to the pet's center.
        return List.of(
                // Head - slightly forward and up
                PetPart.helmet("head", 0, 0.1, 0.15, HamsterTextures.HEAD,
                        new EulerAngle(Math.toRadians(175), Math.toRadians(180), 0)),

                // Body - slightly back and lower
                PetPart.helmet("body", 0, 0.0, -0.1, HamsterTextures.BODY,
                        new EulerAngle(Math.toRadians(180), 0, 0))
        );
    }

    @Override
    protected void animate() {
        // Called by tick() - handled by animateIdle/animateMoving
    }

    @Override
    protected void animateIdle() {
        // Gentle breathing animation - subtle head bob
        animationPhase += ANIMATION_SPEED_IDLE;

        double headAngle = Math.sin(animationPhase) * IDLE_AMPLITUDE;

        // Head bobs slightly up and down
        entityGroup.setPartHeadPose("head",
                new EulerAngle(
                        Math.toRadians(175 + headAngle),
                        Math.toRadians(180),
                        0
                ));
    }

    @Override
    protected void animateMoving() {
        // Walking animation - more pronounced bobbing
        animationPhase += ANIMATION_SPEED_MOVING;

        double headAngle = Math.sin(animationPhase * 2) * HEAD_BOB_AMPLITUDE;
        double bodyAngle = Math.sin(animationPhase) * WALK_AMPLITUDE;

        // Head bobs more vigorously while walking
        entityGroup.setPartHeadPose("head",
                new EulerAngle(
                        Math.toRadians(175 + headAngle),
                        Math.toRadians(180),
                        0
                ));

        // Body sways slightly while walking
        entityGroup.setPartHeadPose("body",
                new EulerAngle(
                        Math.toRadians(180),
                        Math.toRadians(bodyAngle),
                        0
                ));
    }
}
