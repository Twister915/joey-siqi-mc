package sh.joey.mc.pet;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.pet.entity.PetEntityGroup;
import sh.joey.mc.pet.entity.PetPart;

import java.util.List;

/**
 * Abstract base class for all pet types.
 * Handles lifecycle, following behavior, and provides hooks for animations.
 */
public abstract class Pet implements Disposable {

    private static final double TELEPORT_DISTANCE = 15.0;
    private static final double FOLLOW_DISTANCE = 2.5;
    private static final double MOVE_SPEED = 0.25;

    protected final SiqiJoeyPlugin plugin;
    protected final Player owner;
    protected final PetManager manager;
    protected final CompositeDisposable disposables = new CompositeDisposable();
    protected final PetEntityGroup entityGroup;

    protected PetState state = PetState.FOLLOWING;
    protected boolean isMoving = false;
    protected double animationPhase = 0;

    protected Pet(SiqiJoeyPlugin plugin, Player owner, PetManager manager) {
        this.plugin = plugin;
        this.owner = owner;
        this.manager = manager;
        this.entityGroup = new PetEntityGroup();

        // Add parts defined by subclass
        for (PetPart part : defineParts()) {
            entityGroup.addPart(part);
        }
    }

    /**
     * Returns the pet type.
     */
    public abstract PetType getType();

    /**
     * Defines the parts that make up this pet's visual appearance.
     */
    protected abstract List<PetPart> defineParts();

    /**
     * Called each tick to update animations.
     * Subclasses should update animationPhase and call entityGroup.setPartHeadPose().
     */
    protected abstract void animate();

    /**
     * Called each tick when the pet is idle (not moving).
     */
    protected abstract void animateIdle();

    /**
     * Called each tick when the pet is moving.
     */
    protected abstract void animateMoving();

    /**
     * Spawns the pet at the given location.
     */
    public void spawn(Location location) {
        entityGroup.spawn(location);
    }

    /**
     * Despawns the pet.
     */
    public void despawn() {
        entityGroup.remove();
    }

    /**
     * Gets the current state of the pet.
     */
    public PetState getState() {
        return state;
    }

    /**
     * Sets the pet's behavioral state.
     */
    public void setState(PetState state) {
        this.state = state;
    }

    /**
     * Teleports the pet to the given location.
     */
    public void teleportTo(Location location) {
        if (!entityGroup.isSpawned()) return;
        entityGroup.moveTo(location);
    }

    /**
     * Gets the current location of the pet.
     */
    public Location getLocation() {
        return entityGroup.getLocation();
    }

    /**
     * Called each tick by PetManager to update the pet.
     */
    public void tick() {
        if (!entityGroup.isSpawned()) return;
        if (!owner.isOnline()) return;

        updateMovement();

        if (isMoving) {
            animateMoving();
        } else {
            animateIdle();
        }
    }

    /**
     * Updates the pet's position based on owner location.
     */
    protected void updateMovement() {
        if (state == PetState.SITTING) {
            isMoving = false;
            return;
        }

        Location petLoc = entityGroup.getLocation();
        if (petLoc == null) return;

        Location ownerLoc = owner.getLocation();

        // Check if in different world
        if (!petLoc.getWorld().equals(ownerLoc.getWorld())) {
            teleportTo(findLocationNearPlayer());
            return;
        }

        double distance = petLoc.distance(ownerLoc);

        if (distance > TELEPORT_DISTANCE) {
            // Too far - teleport to player
            teleportTo(findLocationNearPlayer());
            isMoving = false;
        } else if (distance > FOLLOW_DISTANCE) {
            // Move towards player
            isMoving = true;
            moveTowards(ownerLoc);
        } else {
            // Close enough - stop
            isMoving = false;
            // Face the player
            faceLocation(ownerLoc);
        }
    }

    /**
     * Moves the pet towards a target location.
     */
    protected void moveTowards(Location target) {
        Location petLoc = entityGroup.getLocation();
        if (petLoc == null) return;

        Vector direction = target.toVector().subtract(petLoc.toVector()).normalize();
        Location newLoc = petLoc.add(direction.multiply(MOVE_SPEED));

        // Calculate yaw to face movement direction
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        newLoc.setYaw(yaw);

        entityGroup.moveTo(newLoc);
    }

    /**
     * Rotates the pet to face a location.
     */
    protected void faceLocation(Location target) {
        Location petLoc = entityGroup.getLocation();
        if (petLoc == null) return;

        Vector direction = target.toVector().subtract(petLoc.toVector());
        if (direction.lengthSquared() < 0.01) return; // Too close to calculate direction

        float yaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        entityGroup.setYaw(yaw);
    }

    /**
     * Finds a suitable location near the player to spawn/teleport to.
     */
    protected Location findLocationNearPlayer() {
        Location playerLoc = owner.getLocation();
        // Spawn behind and to the side of the player
        double angle = Math.toRadians(playerLoc.getYaw() + 135);
        double x = playerLoc.getX() + Math.cos(angle) * 1.5;
        double z = playerLoc.getZ() + Math.sin(angle) * 1.5;
        Location loc = new Location(playerLoc.getWorld(), x, playerLoc.getY(), z);
        loc.setYaw(playerLoc.getYaw());
        return loc;
    }

    /**
     * Plays spawn particle effects and sound.
     */
    public void playSpawnEffect() {
        Location loc = entityGroup.getLocation();
        if (loc == null) return;

        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.HEART, loc.add(0, 0.5, 0), 5, 0.3, 0.3, 0.3, 0);
        world.playSound(loc, Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.2f);
    }

    /**
     * Plays despawn particle effects and sound.
     */
    public void playDespawnEffect() {
        Location loc = entityGroup.getLocation();
        if (loc == null) return;

        World world = loc.getWorld();
        if (world == null) return;

        world.spawnParticle(Particle.CLOUD, loc.add(0, 0.5, 0), 10, 0.3, 0.3, 0.3, 0.05);
        world.playSound(loc, Sound.ENTITY_CHICKEN_HURT, 0.5f, 1.5f);
    }

    /**
     * Returns the pet's owner.
     */
    public Player getOwner() {
        return owner;
    }

    @Override
    public void dispose() {
        disposables.dispose();
        despawn();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
