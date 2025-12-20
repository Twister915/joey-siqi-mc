package sh.joey.mc.inventory;

import com.google.gson.reflect.TypeToken;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import sh.joey.mc.Json;
import sh.joey.mc.storage.StorageService;

import java.lang.reflect.Type;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PostgreSQL storage for inventory snapshots.
 * Pure CRUD operations - no business logic.
 */
public final class InventorySnapshotStorage {

    private static final Type EFFECT_LIST_TYPE = new TypeToken<List<InventorySnapshot.EffectData>>() {}.getType();
    private static final Type LABELS_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final StorageService storage;

    public InventorySnapshotStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Saves a snapshot and returns its ID.
     */
    public Single<UUID> save(InventorySnapshot snapshot) {
        return storage.query(conn -> {
            String sql = """
                INSERT INTO inventory_snapshots (
                    id, player_id,
                    inventory_data, armor_data, offhand_data, ender_chest_data,
                    xp_level, xp_progress, health, max_health, hunger, saturation,
                    effects_json, labels, snapshot_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?)
                RETURNING id
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, snapshot.id());
                stmt.setObject(2, snapshot.playerId());
                stmt.setBytes(3, snapshot.inventoryData());
                stmt.setBytes(4, snapshot.armorData());
                stmt.setBytes(5, snapshot.offhandData());
                stmt.setBytes(6, snapshot.enderChestData());
                stmt.setInt(7, snapshot.xpLevel());
                stmt.setFloat(8, snapshot.xpProgress());
                stmt.setDouble(9, snapshot.health());
                stmt.setDouble(10, snapshot.maxHealth());
                stmt.setInt(11, snapshot.hunger());
                stmt.setFloat(12, snapshot.saturation());
                stmt.setString(13, Json.GSON.toJson(snapshot.effects()));
                stmt.setString(14, Json.GSON.toJson(snapshot.labels()));
                stmt.setTimestamp(15, Timestamp.from(snapshot.snapshotAt()));

                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    return rs.getObject("id", UUID.class);
                }
            }
        });
    }

    /**
     * Gets a snapshot by its ID.
     */
    public Maybe<InventorySnapshot> getById(UUID snapshotId) {
        return storage.queryMaybe(conn -> {
            String sql = """
                SELECT id, player_id,
                       inventory_data, armor_data, offhand_data, ender_chest_data,
                       xp_level, xp_progress, health, max_health, hunger, saturation,
                       effects_json, labels, snapshot_at
                FROM inventory_snapshots
                WHERE id = ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, snapshotId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return readSnapshot(rs);
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Lists snapshots for a player, ordered by time descending.
     */
    public Flowable<InventorySnapshot> listByPlayer(UUID playerId, int limit, int offset) {
        return storage.queryFlowable(conn -> {
            String sql = """
                SELECT id, player_id,
                       inventory_data, armor_data, offhand_data, ender_chest_data,
                       xp_level, xp_progress, health, max_health, hunger, saturation,
                       effects_json, labels, snapshot_at
                FROM inventory_snapshots
                WHERE player_id = ?
                ORDER BY snapshot_at DESC
                LIMIT ? OFFSET ?
                """;

            List<InventorySnapshot> snapshots = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setInt(2, limit);
                stmt.setInt(3, offset);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        snapshots.add(readSnapshot(rs));
                    }
                }
            }
            return snapshots;
        });
    }

    /**
     * Deletes a snapshot by its ID.
     */
    public Completable deleteById(UUID snapshotId) {
        return storage.execute(conn -> {
            String sql = "DELETE FROM inventory_snapshots WHERE id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, snapshotId);
                stmt.executeUpdate();
            }
        });
    }

    private InventorySnapshot readSnapshot(ResultSet rs) throws java.sql.SQLException {
        String effectsJson = rs.getString("effects_json");
        List<InventorySnapshot.EffectData> effects = effectsJson != null
                ? Json.GSON.fromJson(effectsJson, EFFECT_LIST_TYPE)
                : Collections.emptyList();

        String labelsJson = rs.getString("labels");
        Map<String, Object> labels = labelsJson != null
                ? Json.GSON.fromJson(labelsJson, LABELS_TYPE)
                : Collections.emptyMap();

        Timestamp snapshotAt = rs.getTimestamp("snapshot_at");

        return new InventorySnapshot(
                rs.getObject("id", UUID.class),
                rs.getObject("player_id", UUID.class),
                rs.getBytes("inventory_data"),
                rs.getBytes("armor_data"),
                rs.getBytes("offhand_data"),
                rs.getBytes("ender_chest_data"),
                rs.getInt("xp_level"),
                rs.getFloat("xp_progress"),
                rs.getDouble("health"),
                rs.getDouble("max_health"),
                rs.getInt("hunger"),
                rs.getFloat("saturation"),
                effects,
                labels,
                snapshotAt.toInstant()
        );
    }
}
