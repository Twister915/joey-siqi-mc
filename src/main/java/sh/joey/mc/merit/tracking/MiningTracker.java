package sh.joey.mc.merit.tracking;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.event.block.BlockBreakEvent;
import sh.joey.mc.SiqiJoeyPlugin;

/**
 * Tracks block mining for mining challenges.
 */
public final class MiningTracker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();

    public MiningTracker(SiqiJoeyPlugin plugin, ProgressTracker progressTracker) {
        disposables.add(plugin.watchEvent(true, BlockBreakEvent.class)
                .subscribe(event -> {
                    String material = event.getBlock().getType().name();
                    progressTracker.increment(
                            event.getPlayer().getUniqueId(),
                            "blocks_mined:" + material
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
