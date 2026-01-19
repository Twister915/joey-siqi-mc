package sh.joey.mc.merit.tracking;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Material;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Set;

/**
 * Tracks crop harvesting for farming challenges.
 * <p>
 * PlayerHarvestBlockEvent only fires for crops with a true harvest mechanic
 * (wheat, carrots, potatoes, beetroots, sweet berries). Other crops like
 * sugar cane, bamboo, cactus, melon, pumpkin, nether wart, and cocoa are
 * tracked via BlockBreakEvent.
 */
public final class FarmingTracker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();

    /**
     * Crops that don't trigger PlayerHarvestBlockEvent and must be tracked via BlockBreakEvent.
     */
    private static final Set<Material> BLOCK_BREAK_CROPS = Set.of(
            Material.SUGAR_CANE,
            Material.BAMBOO,
            Material.CACTUS,
            Material.MELON,
            Material.PUMPKIN,
            Material.NETHER_WART,
            Material.COCOA
    );

    public FarmingTracker(SiqiJoeyPlugin plugin, ProgressTracker progressTracker) {
        // Handle crops with harvest mechanic (wheat, carrots, potatoes, beetroots, sweet berries)
        disposables.add(plugin.watchEvent(true, PlayerHarvestBlockEvent.class)
                .subscribe(event -> {
                    String material = event.getHarvestedBlock().getType().name();
                    // Count the number of items harvested
                    int count = event.getItemsHarvested().stream()
                            .mapToInt(item -> item.getAmount())
                            .sum();
                    if (count > 0) {
                        progressTracker.increment(
                                event.getPlayer().getUniqueId(),
                                "harvested:" + material,
                                count
                        );
                    }
                }));

        // Handle crops that are just block breaks (sugar cane, bamboo, cactus, etc.)
        disposables.add(plugin.watchEvent(true, BlockBreakEvent.class)
                .filter(event -> BLOCK_BREAK_CROPS.contains(event.getBlock().getType()))
                .subscribe(event -> {
                    String material = event.getBlock().getType().name();
                    progressTracker.increment(
                            event.getPlayer().getUniqueId(),
                            "harvested:" + material
                    );
                }));
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
