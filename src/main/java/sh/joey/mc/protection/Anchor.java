package sh.joey.mc.protection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A lodestone anchor point within a protected region.
 * Each anchor defines a circular protection area with the region's shared radius.
 */
public record Anchor(
        UUID id,
        UUID regionId,
        int x,
        int y,
        int z,
        Instant createdAt
) {
    /**
     * Checks if the given location is within this anchor's protection circle.
     *
     * @param loc    the location to check
     * @param radius the protection radius
     * @return true if within the circle
     */
    public boolean contains(Location loc, int radius) {
        double dx = loc.getBlockX() - x;
        double dz = loc.getBlockZ() - z;
        return (dx * dx + dz * dz) <= (radius * radius);
    }

    /**
     * Checks if a circle at the given position would intersect this anchor's circle.
     *
     * @param otherX      center X of other circle
     * @param otherZ      center Z of other circle
     * @param otherRadius radius of other circle
     * @param thisRadius  radius of this anchor
     * @return true if circles would intersect
     */
    public boolean wouldIntersect(int otherX, int otherZ, int otherRadius, int thisRadius) {
        double dx = x - otherX;
        double dz = z - otherZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        return distance < (thisRadius + otherRadius);
    }

    /**
     * Gets the Bukkit Location for this anchor.
     *
     * @param worldId the world UUID
     * @return the location, or null if world not loaded
     */
    @Nullable
    public Location getLocation(UUID worldId) {
        World world = Bukkit.getWorld(worldId);
        if (world == null) return null;
        return new Location(world, x, y, z);
    }

    /**
     * Calculates the distance from this anchor to a location (2D, ignoring Y).
     *
     * @param loc the location
     * @return the horizontal distance
     */
    public double distanceTo(Location loc) {
        double dx = loc.getBlockX() - x;
        double dz = loc.getBlockZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
