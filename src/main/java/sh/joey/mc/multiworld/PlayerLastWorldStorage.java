package sh.joey.mc.multiworld;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import sh.joey.mc.storage.StorageService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Storage for tracking the last world a player was in.
 * Used to detect when a player joins but their last world no longer exists,
 * requiring an inventory group swap.
 */
public final class PlayerLastWorldStorage {

    private final StorageService storage;

    public PlayerLastWorldStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Record representing the last world a player was in.
     */
    public record LastWorld(UUID worldUuid, String inventoryGroup) {}

    /**
     * Gets the last world a player was in.
     *
     * @param playerId the player's UUID
     * @return the last world info, or empty if not tracked
     */
    public Maybe<LastWorld> getLastWorld(UUID playerId) {
        return storage.queryMaybe(conn -> {
            String sql = """
                SELECT world_uuid, inventory_group
                FROM player_last_worlds
                WHERE player_id = ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new LastWorld(
                                rs.getObject("world_uuid", UUID.class),
                                rs.getString("inventory_group")
                        );
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Saves the last world a player was in.
     *
     * @param playerId       the player's UUID
     * @param worldUuid      the world's UUID
     * @param inventoryGroup the inventory group for that world
     * @return a completable that completes when saved
     */
    public Completable setLastWorld(UUID playerId, UUID worldUuid, String inventoryGroup) {
        return storage.execute(conn -> {
            String sql = """
                INSERT INTO player_last_worlds (player_id, world_uuid, inventory_group, updated_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (player_id)
                DO UPDATE SET world_uuid = EXCLUDED.world_uuid,
                              inventory_group = EXCLUDED.inventory_group,
                              updated_at = NOW()
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setObject(2, worldUuid);
                stmt.setString(3, inventoryGroup);
                stmt.executeUpdate();
            }
        });
    }
}
