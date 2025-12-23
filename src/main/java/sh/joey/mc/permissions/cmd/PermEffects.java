package sh.joey.mc.permissions.cmd;

import io.reactivex.rxjava3.core.Completable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import sh.joey.mc.permissions.DisplayManager;
import sh.joey.mc.permissions.PermissionAttacher;
import sh.joey.mc.permissions.PermissionCache;

import java.util.UUID;

/**
 * Handles cache invalidation and player refresh after permission changes.
 * Ensures changes take effect immediately.
 */
public final class PermEffects {

    private final PermissionCache cache;
    private final PermissionAttacher attacher;
    private final DisplayManager displayManager;

    public PermEffects(PermissionCache cache, PermissionAttacher attacher, DisplayManager displayManager) {
        this.cache = cache;
        this.attacher = attacher;
        this.displayManager = displayManager;
    }

    /**
     * Call after any group permission/attribute change.
     * Invalidates all caches and refreshes all online players.
     */
    public Completable onGroupChanged(String groupName) {
        return Completable.fromAction(() -> {
            cache.invalidateGroup(groupName);
            attacher.refreshAll();
            displayManager.refreshAll();
        });
    }

    /**
     * Call after any player permission/attribute change.
     * Invalidates the player's cache and refreshes their permissions/display.
     */
    public Completable onPlayerChanged(UUID playerId) {
        return Completable.fromAction(() -> {
            cache.invalidatePlayer(playerId);
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                attacher.refresh(player);
                displayManager.updateDisplay(player);
            }
        });
    }

    /**
     * Call after reload command.
     * Invalidates all caches and refreshes all online players.
     */
    public Completable onReload() {
        return Completable.fromAction(() -> {
            cache.invalidateAll();
            attacher.refreshAll();
            displayManager.refreshAll();
        });
    }
}
