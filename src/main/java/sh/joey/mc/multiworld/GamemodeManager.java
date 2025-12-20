package sh.joey.mc.multiworld;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import sh.joey.mc.SiqiJoeyPlugin;

/**
 * Manages automatic gamemode switching when players enter configured worlds.
 * Watches PlayerChangedWorldEvent and sets the player's gamemode based on world config.
 */
public final class GamemodeManager implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final WorldManager worldManager;

    public GamemodeManager(SiqiJoeyPlugin plugin, WorldManager worldManager) {
        this.worldManager = worldManager;

        disposables.add(plugin.watchEvent(PlayerChangedWorldEvent.class)
                .subscribe(this::handleWorldChange));
    }

    private void handleWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World newWorld = player.getWorld();

        worldManager.getConfig(newWorld).ifPresent(config -> {
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
