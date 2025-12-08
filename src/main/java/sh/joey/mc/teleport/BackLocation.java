package sh.joey.mc.teleport;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Optional;
import java.util.UUID;

/**
 * Represents a saved location for the /back command.
 */
public record BackLocation(
        UUID playerId,
        LocationType type,
        UUID worldId,
        double x,
        double y,
        double z,
        float pitch,
        float yaw
) {
    public enum LocationType {
        DEATH("death"),
        TELEPORT("teleport");

        private final String dbValue;

        LocationType(String dbValue) {
            this.dbValue = dbValue;
        }

        public String toDbValue() {
            return dbValue;
        }

        public static LocationType fromDbValue(String value) {
            return switch (value) {
                case "death" -> DEATH;
                case "teleport" -> TELEPORT;
                default -> throw new IllegalArgumentException("Unknown location type: " + value);
            };
        }
    }

    /**
     * Creates a BackLocation from a Bukkit Location.
     */
    public static BackLocation from(UUID playerId, LocationType type, Location location) {
        return new BackLocation(
                playerId,
                type,
                location.getWorld().getUID(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getPitch(),
                location.getYaw()
        );
    }

    /**
     * Converts this to a Bukkit Location, or empty if the world is not loaded.
     */
    public Optional<Location> toBukkitLocation() {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, x, y, z, yaw, pitch));
    }
}
