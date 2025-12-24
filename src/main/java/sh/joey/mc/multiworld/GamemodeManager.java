package sh.joey.mc.multiworld;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.logging.Logger;

/**
 * Manages automatic gamemode switching when players enter configured worlds.
 * Watches PlayerChangedWorldEvent and PlayerJoinEvent to set the player's gamemode
 * based on world config.
 * <p>
 * Also handles the case where a player joins but their last world no longer exists,
 * ensuring their gamemode matches the current world's config.
 */
public final class GamemodeManager implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final WorldManager worldManager;
    private final PlayerLastWorldStorage lastWorldStorage;
    private final Logger logger;

    public GamemodeManager(SiqiJoeyPlugin plugin, WorldManager worldManager,
                           PlayerLastWorldStorage lastWorldStorage) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.lastWorldStorage = lastWorldStorage;
        this.logger = plugin.getLogger();

        disposables.add(plugin.watchEvent(PlayerChangedWorldEvent.class)
                .subscribe(this::handleWorldChange));

        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(this::handleJoin));
    }

    private void handleWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        enforceGamemode(player, player.getWorld());
    }

    private void handleJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        World currentWorld = player.getWorld();

        // Check if player was in a deleted world - if so, enforce gamemode immediately
        lastWorldStorage.getLastWorld(player.getUniqueId())
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        lastWorld -> {
                            World previousWorld = Bukkit.getWorld(lastWorld.worldUuid());
                            if (previousWorld == null) {
                                // Last world no longer exists - enforce gamemode for current world
                                enforceGamemode(player, currentWorld);
                            }
                        },
                        err -> logger.warning("Failed to check last world for gamemode: " + err.getMessage()),
                        () -> {} // No last world recorded - nothing to do
                );
    }

    private void enforceGamemode(Player player, World world) {
        worldManager.getConfig(world).ifPresent(config -> {
            if (player.getGameMode() != config.gamemode()) {
                player.setGameMode(config.gamemode());
            }
        });
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
