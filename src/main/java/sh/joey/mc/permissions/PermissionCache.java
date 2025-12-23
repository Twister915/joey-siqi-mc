package sh.joey.mc.permissions;

import com.destroystokyo.paper.profile.PlayerProfile;
import io.papermc.paper.connection.PlayerLoginConnection;
import io.papermc.paper.event.connection.PlayerConnectionValidateLoginEvent;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches resolved permissions per player per world.
 * <p>
 * Automatically invalidates on:
 * <ul>
 *   <li>World change - invalidates player's cache for all worlds</li>
 *   <li>Player quit - removes player from cache</li>
 * </ul>
 * <p>
 * Pre-populates cache on player join for fast permission checks.
 */
public final class PermissionCache implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final PermissionResolver resolver;

    // playerId -> (worldId -> ResolvedPermissions)
    private final Map<UUID, Map<UUID, ResolvedPermissions>> cache = new ConcurrentHashMap<>();

    // Cached attributes per player for synchronous access (e.g., chat formatting)
    private final Map<UUID, PermissibleAttributes> attributeCache = new ConcurrentHashMap<>();

    @SuppressWarnings("UnstableApiUsage")
    public PermissionCache(SiqiJoeyPlugin plugin, PermissionResolver resolver) {
        this.plugin = plugin;
        this.resolver = resolver;

        // Invalidate on world change
        disposables.add(plugin.watchEvent(PlayerChangedWorldEvent.class)
                .subscribe(event -> invalidatePlayer(event.getPlayer().getUniqueId())));

        // Clear on quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> {
                    UUID playerId = event.getPlayer().getUniqueId();
                    cache.remove(playerId);
                    attributeCache.remove(playerId);
                }));

        // Pre-populate on login (earliest point where we have player UUID)
        disposables.add(plugin.watchEvent(EventPriority.LOW, PlayerConnectionValidateLoginEvent.class)
                .subscribe(event -> {
                    if (!(event.getConnection() instanceof PlayerLoginConnection loginConn)) {
                        return;
                    }
                    PlayerProfile profile = loginConn.getAuthenticatedProfile();
                    if (profile == null || profile.getId() == null) {
                        return;
                    }
                    UUID playerId = profile.getId();
                    String playerName = profile.getName();
                    // Use server's default spawn world for pre-population
                    UUID worldId = plugin.getServer().getWorlds().getFirst().getUID();
                    get(playerId, worldId)
                            .subscribe(
                                    resolved -> {},
                                    err -> plugin.getLogger().warning(
                                            "Failed to pre-populate permissions for " + playerName + ": " + err.getMessage())
                            );
                }));
    }

    /**
     * Get resolved permissions for a player in a world.
     * Returns cached value if available, otherwise resolves and caches.
     */
    public Single<ResolvedPermissions> get(UUID playerId, UUID worldId) {
        return Single.defer(() -> {
            Map<UUID, ResolvedPermissions> playerCache = cache.computeIfAbsent(playerId,
                    k -> new ConcurrentHashMap<>());

            ResolvedPermissions cached = playerCache.get(worldId);
            if (cached != null) {
                return Single.just(cached);
            }

            return resolver.resolve(playerId, worldId)
                    .doOnSuccess(resolved -> {
                        playerCache.put(worldId, resolved);
                        // Also cache attributes for synchronous access
                        attributeCache.put(playerId, resolved.attributes());
                    });
        });
    }

    /**
     * Get cached attributes for a player (synchronous).
     * Returns EMPTY if not cached. Use this for chat formatting where async isn't practical.
     */
    public PermissibleAttributes getCachedAttributes(UUID playerId) {
        return attributeCache.getOrDefault(playerId, PermissibleAttributes.EMPTY);
    }

    /**
     * Invalidate all cached permissions for a player (all worlds).
     * Called when player's permissions change.
     */
    public void invalidatePlayer(UUID playerId) {
        cache.remove(playerId);
        attributeCache.remove(playerId);
    }

    /**
     * Invalidate all cached permissions for a group.
     * Called when group permissions change.
     * <p>
     * For simplicity, invalidates all caches. A more sophisticated approach
     * would track which players are in which groups.
     */
    public void invalidateGroup(String groupName) {
        // Clear all caches - group membership affects many players
        cache.clear();
        attributeCache.clear();
    }

    /**
     * Invalidate all caches.
     */
    public void invalidateAll() {
        cache.clear();
        attributeCache.clear();
    }

    /**
     * Check if a player has cached permissions.
     */
    public boolean isCached(UUID playerId) {
        return cache.containsKey(playerId);
    }

    @Override
    public void dispose() {
        disposables.dispose();
        cache.clear();
        attributeCache.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
