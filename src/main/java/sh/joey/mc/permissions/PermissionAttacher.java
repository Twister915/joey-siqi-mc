package sh.joey.mc.permissions;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Attaches resolved permissions to Bukkit players.
 * <p>
 * Maintains {@link PermissionAttachment} objects and updates them when permissions change.
 * Automatically handles:
 * <ul>
 *   <li>Player join - attach permissions</li>
 *   <li>World change - re-attach with world-specific permissions</li>
 *   <li>Player quit - remove attachment</li>
 * </ul>
 */
public final class PermissionAttacher implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final PermissionCache cache;
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public PermissionAttacher(SiqiJoeyPlugin plugin, PermissionCache cache) {
        this.plugin = plugin;
        this.cache = cache;

        // Attach on join
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> attachPermissions(event.getPlayer())));

        // Re-attach on world change
        disposables.add(plugin.watchEvent(PlayerChangedWorldEvent.class)
                .subscribe(event -> attachPermissions(event.getPlayer())));

        // Clean up on quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> removeAttachment(event.getPlayer())));
    }

    private void attachPermissions(Player player) {
        UUID playerId = player.getUniqueId();
        UUID worldId = player.getWorld().getUID();

        cache.get(playerId, worldId)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        resolved -> applyToPlayer(player, resolved),
                        err -> plugin.getLogger().warning(
                                "Failed to attach permissions for " + player.getName() + ": " + err.getMessage())
                );
    }

    private void applyToPlayer(Player player, ResolvedPermissions resolved) {
        // Remove old attachment
        removeAttachment(player);

        // Create new attachment
        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(player.getUniqueId(), attachment);

        // Apply all permissions
        for (var entry : resolved.permissions().entrySet()) {
            attachment.setPermission(entry.getKey(), entry.getValue());
        }

        // Recalculate permissions
        player.recalculatePermissions();
    }

    private void removeAttachment(Player player) {
        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try {
                player.removeAttachment(old);
            } catch (IllegalArgumentException ignored) {
                // Attachment may have already been removed
            }
        }
    }

    /**
     * Force refresh permissions for a player.
     * Use after modifying the player's permissions or group membership.
     */
    public void refresh(Player player) {
        cache.invalidatePlayer(player.getUniqueId());
        attachPermissions(player);
    }

    /**
     * Force refresh permissions for all online players.
     * Use after modifying group permissions or performing a reload.
     */
    public void refreshAll() {
        cache.invalidateAll();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            attachPermissions(player);
        }
    }

    @Override
    public void dispose() {
        disposables.dispose();

        // Remove all attachments
        for (var entry : attachments.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player != null) {
                try {
                    player.removeAttachment(entry.getValue());
                } catch (IllegalArgumentException ignored) {
                    // Attachment may have already been removed
                }
            }
        }
        attachments.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
