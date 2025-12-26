package sh.joey.mc.resourcepack;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import sh.joey.mc.storage.StorageService;

import java.util.UUID;

/**
 * Storage for player resource pack preferences.
 */
public final class ResourcePackStorage {

    private final StorageService storage;

    public ResourcePackStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Gets the saved resource pack ID for a player.
     *
     * @param playerId the player's UUID
     * @return the pack ID, or empty if no pack is saved
     */
    public Maybe<String> getPlayerPack(UUID playerId) {
        return storage.queryMaybe(conn -> {
            try (var stmt = conn.prepareStatement(
                    "SELECT pack_id FROM player_resource_packs WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getString("pack_id");
                }
                return null;
            }
        });
    }

    /**
     * Saves a player's resource pack preference.
     *
     * @param playerId the player's UUID
     * @param packId the resource pack ID
     * @return completable that completes when saved
     */
    public Completable setPlayerPack(UUID playerId, String packId) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO player_resource_packs (player_id, pack_id, set_at)
                    VALUES (?, ?, NOW())
                    ON CONFLICT (player_id) DO UPDATE SET
                        pack_id = EXCLUDED.pack_id,
                        set_at = NOW()
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, packId.toLowerCase());
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Clears a player's resource pack preference.
     *
     * @param playerId the player's UUID
     * @return completable that completes when cleared
     */
    public Completable clearPlayerPack(UUID playerId) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement(
                    "DELETE FROM player_resource_packs WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                stmt.executeUpdate();
            }
        });
    }
}
