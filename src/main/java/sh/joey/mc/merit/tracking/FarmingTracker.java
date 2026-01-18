package sh.joey.mc.merit.tracking;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.event.player.PlayerHarvestBlockEvent;
import sh.joey.mc.SiqiJoeyPlugin;

/**
 * Tracks crop harvesting for farming challenges.
 */
public final class FarmingTracker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();

    public FarmingTracker(SiqiJoeyPlugin plugin, ProgressTracker progressTracker) {
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
