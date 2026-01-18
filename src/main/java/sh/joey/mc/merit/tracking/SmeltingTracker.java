package sh.joey.mc.merit.tracking;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import sh.joey.mc.SiqiJoeyPlugin;

/**
 * Tracks furnace smelting for smelting challenges.
 */
public final class SmeltingTracker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();

    public SmeltingTracker(SiqiJoeyPlugin plugin, ProgressTracker progressTracker) {
        disposables.add(plugin.watchEvent(FurnaceExtractEvent.class)
                .subscribe(event -> {
                    String material = event.getItemType().name();
                    int amount = event.getItemAmount();

                    if (amount > 0) {
                        progressTracker.increment(
                                event.getPlayer().getUniqueId(),
                                "smelted:" + material,
                                amount
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
