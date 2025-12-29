package sh.joey.mc.settings;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import sh.joey.mc.storage.StorageService;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Storage for player settings.
 */
public final class SettingsStorage {

    private final StorageService storage;

    public SettingsStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Gets a player's settings from the database.
     *
     * @param playerId the player's UUID
     * @return the settings, or empty if no settings are saved
     */
    public Maybe<PlayerSettings> getSettings(UUID playerId) {
        return storage.queryMaybe(conn -> {
            try (var stmt = conn.prepareStatement(
                    "SELECT keep_inventory, display_time, easy_mode FROM player_settings WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    return new PlayerSettings(
                            rs.getBoolean("keep_inventory"),
                            DisplayTimeSetting.fromString(rs.getString("display_time")),
                            rs.getBoolean("easy_mode")
                    );
                }
                return null;
            }
        });
    }

    /**
     * Saves a player's settings to the database.
     *
     * @param playerId the player's UUID
     * @param settings the settings to save
     * @return completable that completes when saved
     */
    public Completable saveSettings(UUID playerId, PlayerSettings settings) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO player_settings (player_id, keep_inventory, display_time, easy_mode, updated_at)
                    VALUES (?, ?, ?, ?, NOW())
                    ON CONFLICT (player_id) DO UPDATE SET
                        keep_inventory = EXCLUDED.keep_inventory,
                        display_time = EXCLUDED.display_time,
                        easy_mode = EXCLUDED.easy_mode,
                        updated_at = NOW()
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setBoolean(2, settings.keepInventory());
                stmt.setString(3, settings.displayTime().name());
                stmt.setBoolean(4, settings.easyMode());
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Loads all player settings from the database.
     * Used for cache initialization on startup.
     *
     * @return flowable of player ID to settings entries
     */
    public Flowable<Map.Entry<UUID, PlayerSettings>> getAllSettings() {
        return storage.queryFlowable(conn -> {
            List<Map.Entry<UUID, PlayerSettings>> results = new ArrayList<>();
            try (var stmt = conn.prepareStatement(
                    "SELECT player_id, keep_inventory, display_time, easy_mode FROM player_settings")) {
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    UUID playerId = rs.getObject("player_id", UUID.class);
                    PlayerSettings settings = new PlayerSettings(
                            rs.getBoolean("keep_inventory"),
                            DisplayTimeSetting.fromString(rs.getString("display_time")),
                            rs.getBoolean("easy_mode")
                    );
                    results.add(new AbstractMap.SimpleImmutableEntry<>(playerId, settings));
                }
            }
            return results;
        });
    }
}
