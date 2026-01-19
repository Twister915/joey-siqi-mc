package sh.joey.mc.protection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * Represents a protected region centered on a lodestone.
 */
public record Region(
        UUID id,
        UUID ownerId,
        @Nullable String ownerName,
        String name,
        UUID worldId,
        int centerX,
        int centerY,
        int centerZ,
        int radius,
        AccessLevel buildingAccess,
        AccessLevel containerAccess,
        AccessLevel doorAccess,
        Set<UUID> members
) {
    /**
     * Check if a location is within this region.
     * The region is a cylinder with infinite vertical extent.
     */
    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getUID().equals(worldId)) {
            return false;
        }
        double dx = loc.getBlockX() - centerX;
        double dz = loc.getBlockZ() - centerZ;
        return (dx * dx + dz * dz) <= ((long) radius * radius);
    }

    /**
     * Check if the given player is the owner of this region.
     */
    public boolean isOwner(UUID playerId) {
        return ownerId.equals(playerId);
    }

    /**
     * Check if the given player is a member of this region.
     */
    public boolean isMember(UUID playerId) {
        return members.contains(playerId);
    }

    /**
     * Check if the given player has access based on the specified level.
     */
    public boolean hasAccess(UUID playerId, AccessLevel level) {
        return switch (level) {
            case EVERYBODY -> true;
            case MEMBERS -> isOwner(playerId) || isMember(playerId);
            case OWNER -> isOwner(playerId);
        };
    }

    /**
     * Check if the given player can build (place/break blocks) in this region.
     */
    public boolean canBuild(UUID playerId) {
        return hasAccess(playerId, buildingAccess);
    }

    /**
     * Check if the given player can access containers in this region.
     */
    public boolean canAccessContainers(UUID playerId) {
        return hasAccess(playerId, containerAccess);
    }

    /**
     * Check if the given player can use doors in this region.
     */
    public boolean canUseDoors(UUID playerId) {
        return hasAccess(playerId, doorAccess);
    }

    /**
     * Get the center location of this region.
     * Returns null if the world is not loaded.
     */
    @Nullable
    public Location getCenterLocation() {
        World world = Bukkit.getWorld(worldId);
        if (world == null) {
            return null;
        }
        return new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
    }

    /**
     * Get the display name for the owner.
     * Falls back to truncated UUID if owner name is not available.
     */
    public String ownerDisplayName() {
        return ownerName != null ? ownerName : ownerId.toString().substring(0, 8);
    }

    /**
     * Create a new region with the specified radius.
     */
    public Region withRadius(int newRadius) {
        return new Region(id, ownerId, ownerName, name, worldId, centerX, centerY, centerZ,
                newRadius, buildingAccess, containerAccess, doorAccess, members);
    }

    /**
     * Create a new region with the specified building access level.
     */
    public Region withBuildingAccess(AccessLevel level) {
        return new Region(id, ownerId, ownerName, name, worldId, centerX, centerY, centerZ,
                radius, level, containerAccess, doorAccess, members);
    }

    /**
     * Create a new region with the specified container access level.
     */
    public Region withContainerAccess(AccessLevel level) {
        return new Region(id, ownerId, ownerName, name, worldId, centerX, centerY, centerZ,
                radius, buildingAccess, level, doorAccess, members);
    }

    /**
     * Create a new region with the specified door access level.
     */
    public Region withDoorAccess(AccessLevel level) {
        return new Region(id, ownerId, ownerName, name, worldId, centerX, centerY, centerZ,
                radius, buildingAccess, containerAccess, level, members);
    }

    /**
     * Check if this region would intersect with a proposed region.
     *
     * @param otherWorldId the world of the other region
     * @param otherCenterX the center X of the other region
     * @param otherCenterZ the center Z of the other region
     * @param otherRadius the radius of the other region
     * @return true if the regions would overlap
     */
    public boolean wouldIntersect(UUID otherWorldId, int otherCenterX, int otherCenterZ, int otherRadius) {
        if (!worldId.equals(otherWorldId)) {
            return false;
        }
        double dx = centerX - otherCenterX;
        double dz = centerZ - otherCenterZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        return distance < (radius + otherRadius);
    }
}
