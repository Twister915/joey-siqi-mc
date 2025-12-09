package sh.joey.mc.home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a saved home location.
 */
public record Home(
        UUID id,
        String name,
        UUID ownerId,
        @Nullable String ownerName,
        UUID worldId,
        double x,
        double y,
        double z,
        float pitch,
        float yaw,
        Set<UUID> sharedWith
) {
    /**
     * Creates a new home with a generated UUID.
     */
    public Home(String name, UUID ownerId, Location location) {
        this(
                UUID.randomUUID(),
                name,
                ownerId,
                null,
                location.getWorld().getUID(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getPitch(),
                location.getYaw(),
                new HashSet<>()
        );
    }

    /**
     * Get the display name for the owner.
     * Falls back to truncated UUID if owner name is not available.
     */
    public String ownerDisplayName() {
        return ownerName != null ? ownerName : ownerId.toString().substring(0, 8);
    }

    /**
     * Check if this home is owned by the given player.
     */
    public boolean isOwnedBy(UUID playerId) {
        return playerId.equals(ownerId);
    }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z, yaw, pitch);
    }

    public boolean isSharedWith(UUID playerId) {
        return sharedWith.contains(playerId);
    }

    public Home withSharedPlayer(UUID playerId) {
        Set<UUID> newShared = new HashSet<>(sharedWith);
        newShared.add(playerId);
        return new Home(id, name, ownerId, ownerName, worldId, x, y, z, pitch, yaw, newShared);
    }

    public Home withoutSharedPlayer(UUID playerId) {
        Set<UUID> newShared = new HashSet<>(sharedWith);
        newShared.remove(playerId);
        return new Home(id, name, ownerId, ownerName, worldId, x, y, z, pitch, yaw, newShared);
    }
}
