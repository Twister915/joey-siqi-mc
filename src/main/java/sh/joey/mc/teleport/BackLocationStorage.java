package sh.joey.mc.teleport;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import sh.joey.mc.storage.StorageService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

/**
 * Handles persistence of back locations (death or teleport-from) to PostgreSQL.
 * Each player has at most one back location - the most recent.
 * All operations are async and return RxJava types.
 */
public final class BackLocationStorage {

    private final StorageService storage;

    public BackLocationStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Get the back location for a player.
     */
    public Maybe<BackLocation> getBackLocation(UUID playerId) {
        return storage.queryMaybe(conn -> {
            String sql = """
                SELECT player_id, location_type, world_id, x, y, z, pitch, yaw
                FROM back_locations
                WHERE player_id = ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return readBackLocation(rs);
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Save or update a back location.
     */
    public Completable saveLocation(BackLocation location) {
        return storage.execute(conn -> {
            String sql = """
                INSERT INTO back_locations (player_id, location_type, world_id, x, y, z, pitch, yaw)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (player_id)
                DO UPDATE SET location_type = EXCLUDED.location_type,
                              world_id = EXCLUDED.world_id,
                              x = EXCLUDED.x,
                              y = EXCLUDED.y,
                              z = EXCLUDED.z,
                              pitch = EXCLUDED.pitch,
                              yaw = EXCLUDED.yaw,
                              created_at = NOW()
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, location.playerId());
                stmt.setString(2, location.type().toDbValue());
                stmt.setObject(3, location.worldId());
                stmt.setDouble(4, location.x());
                stmt.setDouble(5, location.y());
                stmt.setDouble(6, location.z());
                stmt.setFloat(7, location.pitch());
                stmt.setFloat(8, location.yaw());
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Delete the back location for a player.
     */
    public Completable clearBackLocation(UUID playerId) {
        return storage.execute(conn -> {
            String sql = "DELETE FROM back_locations WHERE player_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.executeUpdate();
            }
        });
    }

    private BackLocation readBackLocation(ResultSet rs) throws java.sql.SQLException {
        return new BackLocation(
                rs.getObject("player_id", UUID.class),
                BackLocation.LocationType.fromDbValue(rs.getString("location_type")),
                rs.getObject("world_id", UUID.class),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("pitch"),
                rs.getFloat("yaw")
        );
    }
}
