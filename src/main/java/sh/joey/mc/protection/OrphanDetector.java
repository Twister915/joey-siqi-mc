package sh.joey.mc.protection;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.world.WorldLoadEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * Detects orphaned regions (where all lodestone anchors are missing).
 * Runs at startup and periodically to catch WorldEdit/chunk regeneration.
 *
 * A region is considered orphaned only if ALL of its anchors are missing.
 */
public final class OrphanDetector implements Disposable {

    private final SiqiJoeyPlugin plugin;
    private final RegionCache cache;
    private final Logger logger;
    private final CompositeDisposable disposables = new CompositeDisposable();

    public OrphanDetector(SiqiJoeyPlugin plugin, RegionCache cache) {
        this.plugin = plugin;
        this.cache = cache;
        this.logger = plugin.getLogger();

        // Validate regions in loaded worlds on startup
        validateAllLoadedWorlds();

        // Validate regions when a world loads
        disposables.add(plugin.watchEvent(WorldLoadEvent.class)
                .subscribe(event -> validateWorld(event.getWorld())));

        // Periodic revalidation every 5 minutes
        disposables.add(plugin.interval(5, TimeUnit.MINUTES)
                .subscribe(tick -> validateAllLoadedWorlds()));
    }

    /**
     * Validate all regions in all currently loaded worlds.
     */
    public void validateAllLoadedWorlds() {
        for (World world : Bukkit.getWorlds()) {
            validateWorld(world);
        }
    }

    /**
     * Validate all regions in a specific world.
     */
    public void validateWorld(World world) {
        for (Region region : cache.getRegionsInWorld(world.getUID())) {
            validateRegion(region, world);
        }
    }

    /**
     * Validate a single region's anchors.
     * A region is orphaned only if ALL anchors are missing.
     *
     * @param region the region to validate
     * @param world  the world containing the region
     * @return true if at least one anchor has a lodestone, false if all orphaned
     */
    public boolean validateRegion(Region region, World world) {
        if (region.anchors().isEmpty()) {
            // No anchors - consider orphaned
            markOrphanedIfNeeded(region, true, null);
            return false;
        }

        int validAnchors = 0;
        int checkableAnchors = 0;
        Anchor lastMissingAnchor = null;

        for (Anchor anchor : region.anchors()) {
            // Check if chunk is loaded
            if (!world.isChunkLoaded(anchor.x() >> 4, anchor.z() >> 4)) {
                // Chunk not loaded - can't validate this anchor, skip it
                continue;
            }

            checkableAnchors++;
            Location loc = anchor.getLocation(region.worldId());
            if (loc != null) {
                Block block = loc.getBlock();
                if (block.getType() == Material.LODESTONE) {
                    validAnchors++;
                } else {
                    lastMissingAnchor = anchor;
                }
            }
        }

        // If we couldn't check any anchors (all chunks unloaded), assume valid
        if (checkableAnchors == 0) {
            return true;
        }

        // Region is orphaned only if ALL checkable anchors are missing
        boolean allMissing = validAnchors == 0;
        markOrphanedIfNeeded(region, allMissing, lastMissingAnchor);

        return validAnchors > 0;
    }

    private void markOrphanedIfNeeded(Region region, boolean shouldBeOrphaned, Anchor missingAnchor) {
        boolean currentlyOrphaned = cache.isOrphaned(region.id());

        if (shouldBeOrphaned && !currentlyOrphaned) {
            // Becoming orphaned
            cache.markOrphaned(region.id());
            if (missingAnchor != null) {
                logger.warning("[Protection] Orphaned region '" + region.name() + "' (" +
                        region.ownerDisplayName() + ") - all lodestones missing");
            } else {
                logger.warning("[Protection] Orphaned region '" + region.name() + "' (" +
                        region.ownerDisplayName() + ") - no anchors");
            }
        } else if (!shouldBeOrphaned && currentlyOrphaned) {
            // Lodestone restored
            cache.clearOrphaned(region.id());
            logger.info("[Protection] Region '" + region.name() + "' (" +
                    region.ownerDisplayName() + ") lodestone restored");
        }
    }

    /**
     * Validate a region and return whether it's orphaned.
     * Does not log warnings - used for checking before modifications.
     */
    public boolean isOrphaned(Region region) {
        if (cache.isOrphaned(region.id())) {
            return true;
        }

        World world = Bukkit.getWorld(region.worldId());
        if (world == null) {
            return false; // World not loaded - assume not orphaned
        }

        // Check if at least one anchor has a lodestone
        for (Anchor anchor : region.anchors()) {
            if (!world.isChunkLoaded(anchor.x() >> 4, anchor.z() >> 4)) {
                continue; // Chunk not loaded - can't check this anchor
            }

            Location loc = anchor.getLocation(region.worldId());
            if (loc != null && loc.getBlock().getType() == Material.LODESTONE) {
                return false; // At least one anchor is valid
            }
        }

        // If we couldn't check any anchors, assume not orphaned
        boolean checkedAny = region.anchors().stream()
                .anyMatch(a -> world.isChunkLoaded(a.x() >> 4, a.z() >> 4));

        return checkedAny; // Only orphaned if we checked at least one anchor and all were missing
    }

    /**
     * Check if a specific anchor has its lodestone present.
     */
    public boolean isAnchorValid(Anchor anchor, World world) {
        if (!world.isChunkLoaded(anchor.x() >> 4, anchor.z() >> 4)) {
            return true; // Can't check - assume valid
        }

        Location loc = new Location(world, anchor.x(), anchor.y(), anchor.z());
        return loc.getBlock().getType() == Material.LODESTONE;
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
