package sh.joey.mc.settings;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Central manager for player settings.
 * Handles in-memory caching, persistence, and keep inventory.
 */
public final class SettingsManager implements Disposable {

    private final SettingsStorage storage;
    private final Logger logger;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Map<UUID, PlayerSettings> cache = new ConcurrentHashMap<>();

    public SettingsManager(SiqiJoeyPlugin plugin, SettingsStorage storage) {
        this.storage = storage;
        this.logger = plugin.getLogger();

        // Load all settings into cache on startup (blocking)
        loadCacheBlocking();

        // Player join - load settings from database into cache
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> loadPlayerSettings(event.getPlayer().getUniqueId())));

        // Player quit - remove from cache
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> cache.remove(event.getPlayer().getUniqueId())));

        // Keep inventory handler
        disposables.add(plugin.watchEvent(EventPriority.HIGHEST, PlayerDeathEvent.class)
                .subscribe(this::handleDeath));

        logger.info("[Settings] Initialized with " + cache.size() + " cached settings");
    }

    private void loadCacheBlocking() {
        try {
            storage.getAllSettings()
                    .blockingForEach(entry -> cache.put(entry.getKey(), entry.getValue()));
        } catch (Exception e) {
            logger.warning("[Settings] Failed to load settings cache: " + e.getMessage());
        }
    }

    private void loadPlayerSettings(UUID playerId) {
        storage.getSettings(playerId)
                .defaultIfEmpty(PlayerSettings.DEFAULTS)
                .subscribe(
                        settings -> cache.put(playerId, settings),
                        err -> {
                            logger.warning("[Settings] Failed to load settings for " + playerId + ": " + err.getMessage());
                            cache.put(playerId, PlayerSettings.DEFAULTS);
                        }
                );
    }

    private void handleDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        PlayerSettings settings = getSettings(player.getUniqueId());

        if (settings.keepInventory()) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    // === Public API ===

    /**
     * Gets settings for a player. Returns defaults if not in cache.
     */
    public PlayerSettings getSettings(UUID playerId) {
        return cache.getOrDefault(playerId, PlayerSettings.DEFAULTS);
    }

    /**
     * Sets the keep inventory setting for a player.
     */
    public void setKeepInventory(UUID playerId, boolean enabled) {
        updateSetting(playerId, getSettings(playerId).withKeepInventory(enabled));
    }

    /**
     * Sets the display time setting for a player.
     */
    public void setDisplayTime(UUID playerId, DisplayTimeSetting setting) {
        updateSetting(playerId, getSettings(playerId).withDisplayTime(setting));
    }

    /**
     * Sets the easy mode setting for a player.
     */
    public void setEasyMode(UUID playerId, boolean enabled) {
        updateSetting(playerId, getSettings(playerId).withEasyMode(enabled));
    }

    /**
     * Sets the passive mode setting for a player.
     */
    public void setPassiveMode(UUID playerId, boolean enabled) {
        updateSetting(playerId, getSettings(playerId).withPassiveMode(enabled));
    }

    private void updateSetting(UUID playerId, PlayerSettings updated) {
        cache.put(playerId, updated);
        storage.saveSettings(playerId, updated)
                .subscribe(
                        () -> {},
                        err -> logger.warning("[Settings] Failed to save settings for " + playerId + ": " + err.getMessage())
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
