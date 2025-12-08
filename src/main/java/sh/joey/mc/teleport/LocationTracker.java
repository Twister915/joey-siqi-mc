package sh.joey.mc.teleport;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Location;
import org.bukkit.event.entity.PlayerDeathEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Tracks death locations and teleport-from locations for each player.
 * Used by the /back command.
 */
public final class LocationTracker implements Disposable {
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private final Map<UUID, Location> teleportFromLocations = new HashMap<>();

    public LocationTracker(SiqiJoeyPlugin plugin) {
        disposables.add(plugin.watchEvent(PlayerDeathEvent.class)
                .subscribe(event -> deathLocations.put(
                        event.getPlayer().getUniqueId(),
                        event.getPlayer().getLocation()
                )));
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    public void recordTeleportFrom(UUID playerId, Location location) {
        teleportFromLocations.put(playerId, location.clone());
    }

    /**
     * Gets the most recent "back" location - either death or teleport-from,
     * whichever is more appropriate. Death takes priority if it exists.
     */
    public Optional<Location> getBackLocation(UUID playerId) {
        Location death = deathLocations.get(playerId);
        if (death != null) {
            return Optional.of(death);
        }
        return Optional.ofNullable(teleportFromLocations.get(playerId));
    }

    public void clearDeathLocation(UUID playerId) {
        deathLocations.remove(playerId);
    }

    public void clearTeleportFromLocation(UUID playerId) {
        teleportFromLocations.remove(playerId);
    }

    public boolean hasDeathLocation(UUID playerId) {
        return deathLocations.containsKey(playerId);
    }
}
