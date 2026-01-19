package sh.joey.mc.protection;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.SiqiJoeyPlugin;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Central manager for the protection system.
 * Coordinates storage, cache, and orphan detection.
 */
public final class RegionManager implements Disposable {

    private final SiqiJoeyPlugin plugin;
    private final RegionStorage storage;
    private final RegionCache cache;
    private final OrphanDetector orphanDetector;
    private final ProtectionConfig config;
    private final Logger logger;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public RegionManager(SiqiJoeyPlugin plugin, RegionStorage storage, ProtectionConfig config) {
        this.plugin = plugin;
        this.storage = storage;
        this.cache = new RegionCache();
        this.config = config;
        this.logger = plugin.getLogger();

        // Load all regions into cache (blocking)
        loadCacheBlocking();

        // Initialize orphan detector (after cache is loaded)
        this.orphanDetector = new OrphanDetector(plugin, cache);

        // Notify players of orphaned regions on join
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(this::notifyOrphanedRegions));

        logger.info("[Protection] Initialized with " + cache.size() + " regions");
    }

    private void loadCacheBlocking() {
        try {
            List<Region> regions = storage.getAllRegions()
                    .toList()
                    .blockingGet();
            cache.loadAll(regions);
        } catch (Exception e) {
            logger.warning("[Protection] Failed to load regions cache: " + e.getMessage());
        }
    }

    private void notifyOrphanedRegions(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Check for orphaned regions owned by this player
        for (Region region : cache.getOwnedRegions(playerId)) {
            if (cache.isOrphaned(region.id())) {
                plugin.timer(3, java.util.concurrent.TimeUnit.SECONDS)
                        .subscribe(tick -> {
                            if (player.isOnline()) {
                                Messages.warn(player, "Your protection '" + region.name() +
                                        "' is orphaned (lodestone missing). Use /protection repair to fix it.");
                            }
                        });
            }
        }
    }

    // === Public API ===

    /**
     * Get the region cache for synchronous lookups.
     */
    public RegionCache getCache() {
        return cache;
    }

    /**
     * Get the protection config.
     */
    public ProtectionConfig getConfig() {
        return config;
    }

    /**
     * Get a region by ID.
     */
    public Optional<Region> getRegion(UUID regionId) {
        return cache.get(regionId);
    }

    /**
     * Get a region by owner ID and name.
     */
    public Optional<Region> getRegion(UUID ownerId, String name) {
        return cache.get(ownerId, name);
    }

    /**
     * Get the region at a location.
     */
    @Nullable
    public Region getRegionAt(Location location) {
        return cache.getRegionAt(location);
    }

    /**
     * Find a region by anchor location (exact lodestone block).
     */
    @Nullable
    public Region getRegionByAnchor(UUID worldId, int x, int y, int z) {
        return cache.findByAnchorLocation(worldId, x, y, z);
    }

    /**
     * Get all regions owned by a player.
     */
    public List<Region> getOwnedRegions(UUID ownerId) {
        return cache.getOwnedRegions(ownerId);
    }

    /**
     * Get all regions where the player is a member (not owner).
     */
    public List<Region> getMemberRegions(UUID memberId) {
        return cache.getMemberRegions(memberId);
    }

    /**
     * Count the number of regions owned by a player.
     */
    public int countOwnedRegions(UUID ownerId) {
        return cache.countOwnedRegions(ownerId);
    }

    /**
     * Check if a region is orphaned.
     */
    public boolean isOrphaned(UUID regionId) {
        return cache.isOrphaned(regionId);
    }

    /**
     * Check if a player can create a new region based on their limit.
     */
    public boolean canCreateRegion(Player player) {
        int currentCount = countOwnedRegions(player.getUniqueId());
        return RegionLimitResolver.canCreateRegion(player, config, currentCount);
    }

    /**
     * Get the maximum radius a player can set.
     */
    public int getMaxRadius(Player player) {
        return RadiusLimitResolver.resolve(player, config);
    }

    /**
     * Find nearby owned regions that could be expanded with a new anchor.
     */
    public List<Region> findNearbyOwnedRegions(UUID ownerId, UUID worldId, int x, int z) {
        return cache.findNearbyOwnedRegions(ownerId, worldId, x, z, config.defaultRadius());
    }

    /**
     * Create a new region with its first anchor.
     *
     * @param ownerId the owner's UUID
     * @param name the region name
     * @param location the lodestone location
     * @return Single that completes with the created region, or errors on failure
     */
    public Single<Region> createRegion(UUID ownerId, String name, Location location) {
        UUID regionId = UUID.randomUUID();
        UUID anchorId = UUID.randomUUID();

        Anchor firstAnchor = new Anchor(
                anchorId,
                regionId,
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                Instant.now()
        );

        Region region = new Region(
                regionId,
                ownerId,
                null, // Owner name will be loaded from DB
                RegionStorage.normalizeName(name),
                location.getWorld().getUID(),
                config.defaultRadius(),
                AccessLevel.MEMBERS,
                AccessLevel.MEMBERS,
                AccessLevel.EVERYBODY,
                new HashSet<>(),
                List.of(firstAnchor)
        );

        return storage.createRegion(region, firstAnchor)
                .toSingleDefault(region)
                .doOnSuccess(r -> cache.add(r));
    }

    /**
     * Delete a region.
     *
     * @param regionId the region ID
     * @return Single that completes with true if deleted
     */
    public Single<Boolean> deleteRegion(UUID regionId) {
        return storage.deleteRegion(regionId)
                .doOnSuccess(deleted -> {
                    if (deleted) {
                        cache.remove(regionId);
                    }
                });
    }

    /**
     * Update a region's radius.
     *
     * @param regionId the region ID
     * @param radius the new radius
     * @return Completable that completes on success
     */
    public Completable updateRadius(UUID regionId, int radius) {
        return storage.updateRadius(regionId, radius)
                .doOnComplete(() -> {
                    cache.get(regionId).ifPresent(region -> {
                        cache.update(region.withRadius(radius));
                    });
                });
    }

    /**
     * Update a region's access settings.
     *
     * @param regionId the region ID
     * @param building building access level
     * @param containers container access level
     * @param doors door access level
     * @return Completable that completes on success
     */
    public Completable updateAccess(UUID regionId, AccessLevel building, AccessLevel containers, AccessLevel doors) {
        return storage.updateAccess(regionId, building, containers, doors)
                .doOnComplete(() -> {
                    cache.get(regionId).ifPresent(region -> {
                        cache.update(region
                                .withBuildingAccess(building)
                                .withContainerAccess(containers)
                                .withDoorAccess(doors));
                    });
                });
    }

    /**
     * Add a member to a region.
     *
     * @param regionId the region ID
     * @param memberId the member's UUID
     * @return Single that completes with true if added (false if already member)
     */
    public Single<Boolean> addMember(UUID regionId, UUID memberId) {
        return storage.addMember(regionId, memberId)
                .doOnSuccess(added -> {
                    if (added) {
                        cache.get(regionId).ifPresent(region -> {
                            HashSet<UUID> newMembers = new HashSet<>(region.members());
                            newMembers.add(memberId);
                            cache.update(new Region(
                                    region.id(), region.ownerId(), region.ownerName(), region.name(),
                                    region.worldId(), region.radius(),
                                    region.buildingAccess(), region.containerAccess(), region.doorAccess(),
                                    newMembers, region.anchors()
                            ));
                        });
                    }
                });
    }

    /**
     * Remove a member from a region.
     *
     * @param regionId the region ID
     * @param memberId the member's UUID
     * @return Single that completes with true if removed
     */
    public Single<Boolean> removeMember(UUID regionId, UUID memberId) {
        return storage.removeMember(regionId, memberId)
                .doOnSuccess(removed -> {
                    if (removed) {
                        cache.get(regionId).ifPresent(region -> {
                            HashSet<UUID> newMembers = new HashSet<>(region.members());
                            newMembers.remove(memberId);
                            cache.update(new Region(
                                    region.id(), region.ownerId(), region.ownerName(), region.name(),
                                    region.worldId(), region.radius(),
                                    region.buildingAccess(), region.containerAccess(), region.doorAccess(),
                                    newMembers, region.anchors()
                            ));
                        });
                    }
                });
    }

    // === Anchor Operations ===

    /**
     * Add an anchor to an existing region.
     *
     * @param regionId the region ID
     * @param location the lodestone location
     * @return Single that completes with the new anchor
     */
    public Single<Anchor> addAnchor(UUID regionId, Location location) {
        Anchor anchor = new Anchor(
                UUID.randomUUID(),
                regionId,
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ(),
                Instant.now()
        );

        return storage.addAnchor(anchor)
                .toSingleDefault(anchor)
                .doOnSuccess(a -> {
                    cache.get(regionId).ifPresent(region -> {
                        cache.update(region.withAnchor(a));
                    });
                });
    }

    /**
     * Remove an anchor from a region.
     * Cannot remove the last anchor - use deleteRegion instead.
     *
     * @param anchorId the anchor ID
     * @return Single that completes with true if removed
     */
    public Single<Boolean> removeAnchor(UUID anchorId) {
        return storage.removeAnchor(anchorId)
                .doOnSuccess(removed -> {
                    if (removed) {
                        // Find the region containing this anchor and update cache
                        for (Region region : cache.getAllRegions()) {
                            for (Anchor anchor : region.anchors()) {
                                if (anchor.id().equals(anchorId)) {
                                    cache.update(region.withoutAnchor(anchorId));
                                    return;
                                }
                            }
                        }
                    }
                });
    }

    /**
     * Check if a proposed region would intersect with any existing region.
     *
     * @param worldId the world of the proposed region
     * @param centerX the center X of the proposed region
     * @param centerZ the center Z of the proposed region
     * @param radius the radius of the proposed region
     * @param excludeRegionId region ID to exclude (for radius adjustment)
     * @return the intersecting region, or null if none
     */
    @Nullable
    public Region findIntersecting(UUID worldId, int centerX, int centerZ, int radius, @Nullable UUID excludeRegionId) {
        return cache.findIntersecting(worldId, centerX, centerZ, radius, excludeRegionId);
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
        return cache.findContaining(location, excludeOwnerId);
    }

    /**
     * Mark a region as repaired (lodestone restored).
     */
    public void markRepaired(UUID regionId) {
        cache.clearOrphaned(regionId);
    }

    /**
     * Mark an anchor as orphaned.
     */
    public void markAnchorOrphaned(UUID regionId, UUID anchorId) {
        // For now, mark the whole region as orphaned if any anchor is missing
        // Future enhancement: track per-anchor orphan status
        cache.markOrphaned(regionId);
    }

    /**
     * Force revalidate all orphaned regions.
     */
    public void revalidateOrphans() {
        orphanDetector.validateAllLoadedWorlds();
    }

    @Override
    public void dispose() {
        orphanDetector.dispose();
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
