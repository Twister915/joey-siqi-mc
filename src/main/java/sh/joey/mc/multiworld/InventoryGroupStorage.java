package sh.joey.mc.multiworld;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import sh.joey.mc.storage.StorageService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Storage for the inventory_group_snapshots pivot table.
 * Maps (player_id, inventory_group) to a snapshot_id.
 */
public final class InventoryGroupStorage {

    private final StorageService storage;

    public InventoryGroupStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Gets the snapshot ID for a player's inventory group.
     *
     * @param playerId       the player's UUID
     * @param inventoryGroup the inventory group name
     * @return the snapshot ID, or empty if no snapshot exists for this group
     */
    public Maybe<UUID> getSnapshotForGroup(UUID playerId, String inventoryGroup) {
        return storage.queryMaybe(conn -> {
            String sql = """
                SELECT snapshot_id
                FROM inventory_group_snapshots
                WHERE player_id = ? AND inventory_group = ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, inventoryGroup);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getObject("snapshot_id", UUID.class);
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Sets the snapshot ID for a player's inventory group.
     * Uses UPSERT to insert or update.
     *
     * @param playerId       the player's UUID
     * @param inventoryGroup the inventory group name
     * @param snapshotId     the snapshot ID
     * @return a completable that completes when the operation is done
     */
    public Completable setSnapshotForGroup(UUID playerId, String inventoryGroup, UUID snapshotId) {
        return storage.execute(conn -> {
            String sql = """
                INSERT INTO inventory_group_snapshots (player_id, inventory_group, snapshot_id, updated_at)
                VALUES (?, ?, ?, NOW())
                ON CONFLICT (player_id, inventory_group)
                DO UPDATE SET snapshot_id = EXCLUDED.snapshot_id, updated_at = NOW()
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, inventoryGroup);
                stmt.setObject(3, snapshotId);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Clears the snapshot for a player's inventory group.
     *
     * @param playerId       the player's UUID
     * @param inventoryGroup the inventory group name
     * @return a completable that completes when the operation is done
     */
    public Completable clearGroup(UUID playerId, String inventoryGroup) {
        return storage.execute(conn -> {
            String sql = """
                DELETE FROM inventory_group_snapshots
                WHERE player_id = ? AND inventory_group = ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, inventoryGroup);
                stmt.executeUpdate();
            }
        });
    }
}
