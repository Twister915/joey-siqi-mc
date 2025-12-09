package sh.joey.mc.teleport;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Location;
import org.bukkit.event.entity.PlayerDeathEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.UUID;

/**
 * Tracks death locations and teleport-from locations for each player.
 * Used by the /back command. Persists to PostgreSQL.
 * Each player has one back location - the most recent death or teleport.
 */
public final class LocationTracker implements Disposable {
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final BackLocationStorage storage;

    public LocationTracker(SiqiJoeyPlugin plugin, BackLocationStorage storage) {
        this.plugin = plugin;
        this.storage = storage;

        disposables.add(plugin.watchEvent(PlayerDeathEvent.class)
                .flatMapCompletable(this::handleDeath)
                .subscribe());
    }

    private Completable handleDeath(PlayerDeathEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Location location = event.getPlayer().getLocation();
        BackLocation backLocation = BackLocation.from(playerId, BackLocation.LocationType.DEATH, location);

        return storage.saveLocation(backLocation)
                .doOnError(err -> plugin.getLogger().warning("Failed to save death location: " + err.getMessage()))
                .onErrorComplete();
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    /**
     * Records a teleport-from location for /back.
     */
    public Completable recordTeleportFrom(UUID playerId, Location location) {
        BackLocation backLocation = BackLocation.from(playerId, BackLocation.LocationType.TELEPORT, location);
        return storage.saveLocation(backLocation)
                .doOnError(err -> plugin.getLogger().warning("Failed to save teleport location: " + err.getMessage()))
                .onErrorComplete();
    }

    /**
     * Gets the back location - either death or teleport-from.
     */
    public Maybe<BackLocation> getBackLocation(UUID playerId) {
        return storage.getBackLocation(playerId);
    }
}
