package sh.joey.mc.protection;

import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory cache for protection regions.
 * Provides fast synchronous lookups for event handlers.
 * Thread-safe for concurrent access.
 */
public final class RegionCache {

    // All regions by ID
    private final Map<UUID, Region> regionsById = new ConcurrentHashMap<>();

    // Regions grouped by world for faster spatial queries
    private final Map<UUID, List<Region>> regionsByWorld = new ConcurrentHashMap<>();

    // Orphaned region IDs (lodestone missing)
    private final Set<UUID> orphanedRegions = ConcurrentHashMap.newKeySet();

    /**
     * Load all regions into the cache.
     * Should be called on startup with data from RegionStorage.
     */
    public void loadAll(List<Region> regions) {
        regionsById.clear();
        regionsByWorld.clear();
        orphanedRegions.clear();

        for (Region region : regions) {
            regionsById.put(region.id(), region);
            regionsByWorld.computeIfAbsent(region.worldId(), k -> new CopyOnWriteArrayList<>())
                    .add(region);
        }
    }

    /**
     * Add a region to the cache.
     */
    public void add(Region region) {
        regionsById.put(region.id(), region);
        regionsByWorld.computeIfAbsent(region.worldId(), k -> new CopyOnWriteArrayList<>())
                .add(region);
    }

    /**
     * Remove a region from the cache.
     */
    public void remove(UUID regionId) {
        Region region = regionsById.remove(regionId);
        if (region != null) {
            List<Region> worldRegions = regionsByWorld.get(region.worldId());
            if (worldRegions != null) {
                worldRegions.removeIf(r -> r.id().equals(regionId));
            }
        }
        orphanedRegions.remove(regionId);
    }

    /**
     * Update a region in the cache.
     */
    public void update(Region region) {
        Region old = regionsById.put(region.id(), region);
        if (old != null) {
            List<Region> worldRegions = regionsByWorld.get(old.worldId());
            if (worldRegions != null) {
                worldRegions.replaceAll(r -> r.id().equals(region.id()) ? region : r);
            }
        }
    }

    /**
     * Get a region by ID.
     */
    public Optional<Region> get(UUID regionId) {
        return Optional.ofNullable(regionsById.get(regionId));
    }

    /**
     * Get a region by owner ID and name (case-insensitive).
     */
    public Optional<Region> get(UUID ownerId, String name) {
        String normalizedName = RegionStorage.normalizeName(name);
        return regionsById.values().stream()
                .filter(r -> r.ownerId().equals(ownerId) && r.name().equalsIgnoreCase(normalizedName))
                .findFirst();
    }

    /**
     * Find the region containing a location.
     * Returns the first matching region (regions should not overlap).
     */
    @Nullable
    public Region getRegionAt(Location location) {
        if (location.getWorld() == null) {
            return null;
        }

        List<Region> worldRegions = regionsByWorld.get(location.getWorld().getUID());
        if (worldRegions == null) {
            return null;
        }

        for (Region region : worldRegions) {
            if (region.contains(location)) {
                return region;
            }
        }
        return null;
    }

    /**
     * Get all regions in a world.
     */
    public List<Region> getRegionsInWorld(UUID worldId) {
        List<Region> regions = regionsByWorld.get(worldId);
        return regions != null ? Collections.unmodifiableList(regions) : Collections.emptyList();
    }

    /**
     * Get all regions owned by a player.
     */
    public List<Region> getOwnedRegions(UUID ownerId) {
        List<Region> result = new ArrayList<>();
        for (Region region : regionsById.values()) {
            if (region.ownerId().equals(ownerId)) {
                result.add(region);
            }
        }
        return result;
    }

    /**
     * Get all regions where the player is a member (not owner).
     */
    public List<Region> getMemberRegions(UUID memberId) {
        List<Region> result = new ArrayList<>();
        for (Region region : regionsById.values()) {
            if (region.isMember(memberId) && !region.isOwner(memberId)) {
                result.add(region);
            }
        }
        return result;
    }

    /**
     * Check if a proposed region would intersect with any existing region.
     *
     * @param worldId the world of the proposed region
     * @param centerX the center X of the proposed region
     * @param centerZ the center Z of the proposed region
     * @param radius the radius of the proposed region
     * @param excludeRegionId region ID to exclude from check (for radius adjustment)
     * @return the first intersecting region, or null if none
     */
    @Nullable
    public Region findIntersecting(UUID worldId, int centerX, int centerZ, int radius, @Nullable UUID excludeRegionId) {
        List<Region> worldRegions = regionsByWorld.get(worldId);
        if (worldRegions == null) {
            return null;
        }

        for (Region region : worldRegions) {
            if (excludeRegionId != null && region.id().equals(excludeRegionId)) {
                continue;
            }
            if (region.wouldIntersect(worldId, centerX, centerZ, radius)) {
                return region;
            }
        }
        return null;
    }

    /**
     * Check if a location is inside any existing region.
     *
     * @param location the location to check
     * @param excludeOwnerId optionally exclude regions owned by this player
     * @return the region containing the location, or null
     */
    @Nullable
    public Region findContaining(Location location, @Nullable UUID excludeOwnerId) {
        if (location.getWorld() == null) {
            return null;
        }

        List<Region> worldRegions = regionsByWorld.get(location.getWorld().getUID());
        if (worldRegions == null) {
            return null;
        }

        for (Region region : worldRegions) {
            if (excludeOwnerId != null && region.isOwner(excludeOwnerId)) {
                continue;
            }
            if (region.contains(location)) {
                return region;
            }
        }
        return null;
    }

    /**
     * Find a region that has an anchor at the exact block coordinates.
     *
     * @param worldId the world UUID
     * @param x block X coordinate
     * @param y block Y coordinate
     * @param z block Z coordinate
     * @return the region with an anchor at this location, or null
     */
    @Nullable
    public Region findByAnchorLocation(UUID worldId, int x, int y, int z) {
        List<Region> worldRegions = regionsByWorld.get(worldId);
        if (worldRegions == null) {
            return null;
        }

        for (Region region : worldRegions) {
            if (region.getAnchorAt(x, y, z) != null) {
                return region;
            }
        }
        return null;
    }

    /**
     * Find all regions owned by a player that are close enough to add an anchor at the given location.
     * "Close enough" means the new anchor's circle would touch or overlap with an existing anchor's circle.
     *
     * @param ownerId the owner to check
     * @param worldId the world UUID
     * @param x the proposed anchor X
     * @param z the proposed anchor Z
     * @param radius the region's radius
     * @return list of nearby owned regions that could be expanded
     */
    public List<Region> findNearbyOwnedRegions(UUID ownerId, UUID worldId, int x, int z, int radius) {
        List<Region> worldRegions = regionsByWorld.get(worldId);
        if (worldRegions == null) {
            return Collections.emptyList();
        }

        List<Region> nearby = new ArrayList<>();
        // Allow anchors to be placed up to 2x radius away (circles can touch but not overlap)
        int maxDistance = radius * 4; // generous range for "nearby"

        for (Region region : worldRegions) {
            if (!region.isOwner(ownerId)) {
                continue;
            }
            // Check if any anchor is close enough
            for (Anchor anchor : region.anchors()) {
                double dx = anchor.x() - x;
                double dz = anchor.z() - z;
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance <= maxDistance) {
                    nearby.add(region);
                    break; // Don't add same region multiple times
                }
            }
        }
        return nearby;
    }

    /**
     * Count the number of regions owned by a player.
     */
    public int countOwnedRegions(UUID ownerId) {
        int count = 0;
        for (Region region : regionsById.values()) {
            if (region.ownerId().equals(ownerId)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get all regions.
     */
    public List<Region> getAllRegions() {
        return new ArrayList<>(regionsById.values());
    }

    /**
     * Mark a region as orphaned (lodestone missing).
     */
    public void markOrphaned(UUID regionId) {
        orphanedRegions.add(regionId);
    }

    /**
     * Mark a region as no longer orphaned.
     */
    public void clearOrphaned(UUID regionId) {
        orphanedRegions.remove(regionId);
    }

    /**
     * Check if a region is orphaned.
     */
    public boolean isOrphaned(UUID regionId) {
        return orphanedRegions.contains(regionId);
    }

    /**
     * Get all orphaned region IDs.
     */
    public Set<UUID> getOrphanedRegions() {
        return Collections.unmodifiableSet(orphanedRegions);
    }

    /**
     * Get the total number of regions.
     */
    public int size() {
        return regionsById.size();
    }
}
