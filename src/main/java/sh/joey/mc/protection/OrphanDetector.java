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
 * Detects orphaned regions (where the lodestone is missing).
 * Runs at startup and periodically to catch WorldEdit/chunk regeneration.
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
            validateRegion(region);
        }
    }

    /**
     * Validate a single region's lodestone.
     *
     * @param region the region to validate
     * @return true if the lodestone is present, false if orphaned
     */
    public boolean validateRegion(Region region) {
        Location center = region.getCenterLocation();
        if (center == null || center.getWorld() == null) {
            // World not loaded - can't validate
            return true;
        }

        // Check if the chunk is loaded before accessing the block
        if (!center.getWorld().isChunkLoaded(region.centerX() >> 4, region.centerZ() >> 4)) {
            // Chunk not loaded - can't validate, assume valid
            return true;
        }

        Block block = center.getBlock();
        boolean isLodestone = block.getType() == Material.LODESTONE;

        if (!isLodestone && !cache.isOrphaned(region.id())) {
            // Lodestone missing - mark as orphaned
            cache.markOrphaned(region.id());
            logger.warning("[Protection] Orphaned region '" + region.name() + "' (" +
                    region.ownerDisplayName() + ") - lodestone missing at " +
                    region.centerX() + ", " + region.centerY() + ", " + region.centerZ());
        } else if (isLodestone && cache.isOrphaned(region.id())) {
            // Lodestone restored - clear orphaned status
            cache.clearOrphaned(region.id());
            logger.info("[Protection] Region '" + region.name() + "' (" +
                    region.ownerDisplayName() + ") lodestone restored");
        }

        return isLodestone;
    }

    /**
     * Validate a region and return whether it's orphaned.
     * Does not log warnings - used for checking before modifications.
     */
    public boolean isOrphaned(Region region) {
        if (cache.isOrphaned(region.id())) {
            return true;
        }

        Location center = region.getCenterLocation();
        if (center == null || center.getWorld() == null) {
            return false; // Can't check - assume not orphaned
        }

        if (!center.getWorld().isChunkLoaded(region.centerX() >> 4, region.centerZ() >> 4)) {
            return false; // Chunk not loaded - assume not orphaned
        }

        return center.getBlock().getType() != Material.LODESTONE;
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
