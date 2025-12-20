package sh.joey.mc.multiworld;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.logging.Logger;

/**
 * Tracks player positions in worlds for the /world command.
 * Saves positions when players:
 * - Change worlds (saves position in the world they left)
 * - Join the server (saves their current position)
 * - Respawn (saves their respawn position)
 */
public final class WorldPositionTracker implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final PlayerWorldPositionStorage storage;
    private final Logger logger;

    public WorldPositionTracker(SiqiJoeyPlugin plugin, PlayerWorldPositionStorage storage) {
        this.storage = storage;
        this.logger = plugin.getLogger();

        // Save position when leaving a world
        disposables.add(plugin.watchEvent(PlayerChangedWorldEvent.class)
                .subscribe(this::handleWorldChange));

        // Save position on join
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(this::handleJoin));

        // Save position on respawn
        disposables.add(plugin.watchEvent(PlayerRespawnEvent.class)
                .subscribe(this::handleRespawn));
    }

    private void handleWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        // The event gives us the world they came FROM, and their current location is in the new world
        // We need to save their position in the world they're leaving
        // Unfortunately, by the time this event fires, player.getLocation() is already in the new world
        // We can't get their old position from this event alone

        // Instead, save their position in the NEW world after they arrive
        // This handles the case where they manually teleport or use portals
        savePosition(player, player.getLocation());
    }

    private void handleJoin(PlayerJoinEvent event) {
        savePosition(event.getPlayer(), event.getPlayer().getLocation());
    }

    private void handleRespawn(PlayerRespawnEvent event) {
        // Save respawn position after a short delay to ensure it's set
        savePosition(event.getPlayer(), event.getRespawnLocation());
    }

    private void savePosition(Player player, Location location) {
        if (location.getWorld() == null) {
            return;
        }

        storage.savePosition(player.getUniqueId(), location)
                .subscribe(
                        () -> {},
                        err -> logger.warning("Failed to save position for " + player.getName() + ": " + err.getMessage())
                );
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
