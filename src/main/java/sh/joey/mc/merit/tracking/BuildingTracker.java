package sh.joey.mc.merit.tracking;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.event.block.BlockPlaceEvent;
import sh.joey.mc.SiqiJoeyPlugin;

/**
 * Tracks block placement for building challenges.
 */
public final class BuildingTracker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();

    public BuildingTracker(SiqiJoeyPlugin plugin, ProgressTracker progressTracker) {
        disposables.add(plugin.watchEvent(true, BlockPlaceEvent.class)
                .subscribe(event -> {
                    String material = event.getBlock().getType().name();
                    progressTracker.increment(
                            event.getPlayer().getUniqueId(),
                            "blocks_placed:" + material
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
