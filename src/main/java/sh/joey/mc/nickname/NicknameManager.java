package sh.joey.mc.nickname;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages player nicknames, including caching and display application.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Maintain in-memory cache of active nicknames</li>
 *   <li>Load nicknames on player join</li>
 *   <li>Apply display name via player.displayName() and player.playerListName()</li>
 *   <li>Provide getDisplayName() methods</li>
 *   <li>Clean up cache on player quit</li>
 * </ul>
 */
public final class NicknameManager implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final NicknameStorage storage;
    private final Map<UUID, String> nicknameCache = new ConcurrentHashMap<>();

    public NicknameManager(SiqiJoeyPlugin plugin, NicknameStorage storage) {
        this.plugin = plugin;
        this.storage = storage;

        // Pre-load nickname during async pre-login (before PlayerJoinEvent)
        // This runs on an async thread, so blocking database query is safe
        disposables.add(plugin.watchEvent(AsyncPlayerPreLoginEvent.class)
                .subscribe(this::preloadNickname));

        // Apply display name on join (nickname already in cache from pre-login)
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> applyNickname(event.getPlayer())));

        // Clean up on quit - delay removal to handle reconnect-from-another-location
        // where new session's preload may race with old session's quit
        disposables.add(plugin.watchEvent(EventPriority.MONITOR, PlayerQuitEvent.class)
                .delay(1, TimeUnit.SECONDS, plugin.mainScheduler())
                .subscribe(event -> {
                    UUID playerId = event.getPlayer().getUniqueId();
                    if (Bukkit.getPlayer(playerId) == null) {
                        nicknameCache.remove(playerId);
                    }
                }));
    }

    /**
     * Pre-load a player's nickname into cache during async pre-login.
     * This runs on an async thread, so we can safely block on the database query.
     */
    private void preloadNickname(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return; // Don't load for denied logins
        }

        UUID playerId = event.getUniqueId();
        try {
            Nickname nickname = storage.getNickname(playerId).blockingGet();
            if (nickname != null) {
                nicknameCache.put(playerId, nickname.nickname());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to preload nickname for " + event.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Apply the cached nickname to a player on join.
     */
    private void applyNickname(Player player) {
        String nickname = nicknameCache.get(player.getUniqueId());
        String displayName = nickname != null ? nickname : player.getName();
        applyDisplayName(player, displayName);
    }

    /**
     * Apply a display name to a player.
     * Updates displayName (for chat) and playerListName (for tab list).
     */
    private void applyDisplayName(Player player, String displayName) {
        Component displayComponent = Component.text(displayName);
        player.displayName(displayComponent);
        player.playerListName(displayComponent);
    }

    /**
     * Set a player's nickname and apply it immediately if online.
     */
    public Completable setNickname(UUID playerId, String nickname) {
        return storage.setNickname(playerId, nickname)
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> {
                    nicknameCache.put(playerId, nickname);
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        applyDisplayName(player, nickname);
                    }
                });
    }

    /**
     * Clear a player's nickname and revert to username if online.
     */
    public Completable clearNickname(UUID playerId) {
        return storage.removeNickname(playerId)
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(removed -> {
                    nicknameCache.remove(playerId);
                    Player player = Bukkit.getPlayer(playerId);
                    if (player != null) {
                        applyDisplayName(player, player.getName());
                    }
                })
                .ignoreElement();
    }

    /**
     * Get a player's display name (nickname if set, otherwise username).
     */
    public String getDisplayName(Player player) {
        return getDisplayName(player.getUniqueId(), player.getName());
    }

    /**
     * Get a player's display name by UUID.
     * Falls back to the provided default name if nickname is not set.
     */
    public String getDisplayName(UUID playerId, String defaultName) {
        String nickname = nicknameCache.get(playerId);
        return nickname != null ? nickname : defaultName;
    }

    /**
     * Get a player's display name by UUID.
     * Returns empty if player has no nickname and is not online.
     */
    public Optional<String> getDisplayName(UUID playerId) {
        String nickname = nicknameCache.get(playerId);
        if (nickname != null) {
            return Optional.of(nickname);
        }
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            return Optional.of(player.getName());
        }
        return Optional.empty();
    }

    /**
     * Get a player's nickname if set.
     */
    @Nullable
    public String getNickname(UUID playerId) {
        return nicknameCache.get(playerId);
    }

    /**
     * Check if a player has a nickname set.
     */
    public boolean hasNickname(UUID playerId) {
        return nicknameCache.containsKey(playerId);
    }

    /**
     * Find an online player by their nickname.
     * Returns empty if no online player has that nickname.
     */
    public Optional<Player> findOnlinePlayerByNickname(String nickname) {
        String normalized = Nickname.normalize(nickname);
        for (Map.Entry<UUID, String> entry : nicknameCache.entrySet()) {
            if (Nickname.normalize(entry.getValue()).equals(normalized)) {
                Player player = Bukkit.getPlayer(entry.getKey());
                if (player != null && player.isOnline()) {
                    return Optional.of(player);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public void dispose() {
        disposables.dispose();
        nicknameCache.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
