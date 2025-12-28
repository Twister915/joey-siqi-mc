package sh.joey.mc.trails;

import org.bukkit.Location;

/**
 * Interface for trail particle effects.
 * Implementations can be enums (built-in effects) or classes (custom effects).
 */
public interface TrailEffect {

    /**
     * The unique identifier for this effect (e.g., "flame", "rainbow", "rgb:ff5500").
     */
    String id();

    /**
     * The display name shown in menus (e.g., "Fire Trail", "Rainbow").
     */
    String displayName();

    /**
     * Spawns particles at the given location.
     *
     * @param location  Where to spawn particles
     * @param tick      Current world tick (for cycling effects like rainbow)
     * @param intensity Controls particle count and other parameters
     */
    void spawn(Location location, long tick, TrailIntensity intensity);
}
