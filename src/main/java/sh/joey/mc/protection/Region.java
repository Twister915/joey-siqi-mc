package sh.joey.mc.protection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a protected region with one or more lodestone anchors.
 * A location is protected if it falls within ANY anchor's radius circle.
 */
public record Region(
        UUID id,
        UUID ownerId,
        @Nullable String ownerName,
        String name,
        UUID worldId,
        int radius,
        AccessLevel buildingAccess,
        AccessLevel containerAccess,
        AccessLevel doorAccess,
        Set<UUID> members,
        List<Anchor> anchors
) {
    /**
     * Check if a location is within this region (inside any anchor's circle).
     * The region is a union of cylinders with infinite vertical extent.
     */
    public boolean contains(Location loc) {
        if (loc.getWorld() == null || !loc.getWorld().getUID().equals(worldId)) {
            return false;
        }
        for (Anchor anchor : anchors) {
            if (anchor.contains(loc, radius)) {
                return true;
            }
        }
        return false;
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
     * Get the primary anchor (first/oldest anchor).
     * Returns null if no anchors exist.
     */
    @Nullable
    public Anchor getPrimaryAnchor() {
        return anchors.isEmpty() ? null : anchors.getFirst();
    }

    /**
     * Get the location of the primary anchor.
     * Returns null if no anchors or world not loaded.
     */
    @Nullable
    public Location getPrimaryLocation() {
        Anchor primary = getPrimaryAnchor();
        if (primary == null) return null;
        return primary.getLocation(worldId);
    }

    /**
     * Find the anchor at a specific location (exact block match).
     */
    @Nullable
    public Anchor getAnchorAt(int x, int y, int z) {
        for (Anchor anchor : anchors) {
            if (anchor.x() == x && anchor.y() == y && anchor.z() == z) {
                return anchor;
            }
        }
        return null;
    }

    /**
     * Find the anchor closest to a location.
     */
    @Nullable
    public Anchor getClosestAnchor(Location loc) {
        Anchor closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Anchor anchor : anchors) {
            double dist = anchor.distanceTo(loc);
            if (dist < closestDist) {
                closestDist = dist;
                closest = anchor;
            }
        }
        return closest;
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
        return new Region(id, ownerId, ownerName, name, worldId,
                newRadius, buildingAccess, containerAccess, doorAccess, members, anchors);
    }

    /**
     * Create a new region with the specified building access level.
     */
    public Region withBuildingAccess(AccessLevel level) {
        return new Region(id, ownerId, ownerName, name, worldId,
                radius, level, containerAccess, doorAccess, members, anchors);
    }

    /**
     * Create a new region with the specified container access level.
     */
    public Region withContainerAccess(AccessLevel level) {
        return new Region(id, ownerId, ownerName, name, worldId,
                radius, buildingAccess, level, doorAccess, members, anchors);
    }

    /**
     * Create a new region with the specified door access level.
     */
    public Region withDoorAccess(AccessLevel level) {
        return new Region(id, ownerId, ownerName, name, worldId,
                radius, buildingAccess, containerAccess, level, members, anchors);
    }

    /**
     * Create a new region with an additional anchor.
     */
    public Region withAnchor(Anchor anchor) {
        var newAnchors = new java.util.ArrayList<>(anchors);
        newAnchors.add(anchor);
        return new Region(id, ownerId, ownerName, name, worldId,
                radius, buildingAccess, containerAccess, doorAccess, members, List.copyOf(newAnchors));
    }

    /**
     * Create a new region without the specified anchor.
     */
    public Region withoutAnchor(UUID anchorId) {
        var newAnchors = anchors.stream()
                .filter(a -> !a.id().equals(anchorId))
                .toList();
        return new Region(id, ownerId, ownerName, name, worldId,
                radius, buildingAccess, containerAccess, doorAccess, members, newAnchors);
    }

    /**
     * Check if this region would intersect with a proposed circle.
     * Returns true if ANY anchor would intersect.
     *
     * @param otherWorldId the world of the other circle
     * @param otherX       the center X of the other circle
     * @param otherZ       the center Z of the other circle
     * @param otherRadius  the radius of the other circle
     * @return true if any anchor would overlap
     */
    public boolean wouldIntersect(UUID otherWorldId, int otherX, int otherZ, int otherRadius) {
        if (!worldId.equals(otherWorldId)) {
            return false;
        }
        for (Anchor anchor : anchors) {
            if (anchor.wouldIntersect(otherX, otherZ, otherRadius, radius)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a proposed anchor would intersect with any existing anchor in this region.
     * Used to prevent adding overlapping anchors to the same region.
     */
    public boolean anchorWouldOverlapOwn(int x, int z) {
        for (Anchor anchor : anchors) {
            if (anchor.wouldIntersect(x, z, radius, radius)) {
                return true;
            }
        }
        return false;
    }
}
