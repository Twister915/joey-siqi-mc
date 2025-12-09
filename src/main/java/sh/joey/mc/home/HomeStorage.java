package sh.joey.mc.home;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import sh.joey.mc.storage.StorageService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Handles persistence of player homes to PostgreSQL.
 * All operations are async and return RxJava types.
 */
public final class HomeStorage {

    private final StorageService storage;

    public HomeStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Normalizes a home name: lowercase and trimmed.
     */
    public static String normalizeName(String name) {
        return name.toLowerCase().trim();
    }

    /**
     * Get a specific home by name.
     */
    public Maybe<Home> getHome(UUID playerId, String name) {
        String normalizedName = normalizeName(name);
        return storage.<Home>queryMaybe(conn -> {
            String sql = """
                SELECT h.player_id, h.name, h.world_id, h.x, h.y, h.z, h.pitch, h.yaw,
                       COALESCE(array_agg(hs.shared_with_id) FILTER (WHERE hs.shared_with_id IS NOT NULL), '{}') as shared_with
                FROM homes h
                LEFT JOIN home_shares hs ON h.player_id = hs.owner_id AND h.name = hs.home_name
                WHERE h.player_id = ? AND h.name = ?
                GROUP BY h.player_id, h.name, h.world_id, h.x, h.y, h.z, h.pitch, h.yaw
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, normalizedName);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return readHome(rs);
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Get all homes accessible to a player (owned + shared with them).
     */
    public Flowable<Home> getHomes(UUID playerId) {
        return storage.queryFlowable(conn -> {
            String sql = """
                SELECT h.player_id, h.name, h.world_id, h.x, h.y, h.z, h.pitch, h.yaw,
                       COALESCE(array_agg(hs.shared_with_id) FILTER (WHERE hs.shared_with_id IS NOT NULL), '{}') as shared_with
                FROM homes h
                LEFT JOIN home_shares hs ON h.player_id = hs.owner_id AND h.name = hs.home_name
                WHERE h.player_id = ?
                GROUP BY h.player_id, h.name, h.world_id, h.x, h.y, h.z, h.pitch, h.yaw
                UNION ALL
                SELECT h.player_id, h.name, h.world_id, h.x, h.y, h.z, h.pitch, h.yaw,
                       COALESCE(array_agg(hs2.shared_with_id) FILTER (WHERE hs2.shared_with_id IS NOT NULL), '{}') as shared_with
                FROM homes h
                INNER JOIN home_shares hs ON h.player_id = hs.owner_id AND h.name = hs.home_name AND hs.shared_with_id = ?
                LEFT JOIN home_shares hs2 ON h.player_id = hs2.owner_id AND h.name = hs2.home_name
                GROUP BY h.player_id, h.name, h.world_id, h.x, h.y, h.z, h.pitch, h.yaw
                """;

            List<Home> homes = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setObject(2, playerId);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        homes.add(readHome(rs));
                    }
                }
            }
            return homes;
        });
    }

    /**
     * Check if a player has any homes.
     */
    public Single<Boolean> hasAnyHomes(UUID playerId) {
        return storage.query(conn -> {
            String sql = "SELECT EXISTS(SELECT 1 FROM homes WHERE player_id = ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);

                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    return rs.getBoolean(1);
                }
            }
        });
    }

    /**
     * Save a home (insert or update).
     */
    public Completable setHome(UUID playerId, Home home) {
        String normalizedName = normalizeName(home.name());
        return storage.execute(conn -> {
            String sql = """
                INSERT INTO homes (player_id, name, world_id, x, y, z, pitch, yaw)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (player_id, name)
                DO UPDATE SET world_id = EXCLUDED.world_id,
                              x = EXCLUDED.x,
                              y = EXCLUDED.y,
                              z = EXCLUDED.z,
                              pitch = EXCLUDED.pitch,
                              yaw = EXCLUDED.yaw,
                              updated_at = NOW()
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, normalizedName);
                stmt.setObject(3, home.worldId());
                stmt.setDouble(4, home.x());
                stmt.setDouble(5, home.y());
                stmt.setDouble(6, home.z());
                stmt.setFloat(7, home.pitch());
                stmt.setFloat(8, home.yaw());
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Delete a home.
     *
     * @return true if a home was deleted
     */
    public Single<Boolean> deleteHome(UUID playerId, String name) {
        String normalizedName = normalizeName(name);
        return storage.query(conn -> {
            String sql = "DELETE FROM homes WHERE player_id = ? AND name = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, normalizedName);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    /**
     * Share a home with another player.
     *
     * @return true if the share was added (false if home doesn't exist or already shared)
     */
    public Single<Boolean> shareHome(UUID ownerId, String homeName, UUID targetId) {
        String normalizedName = normalizeName(homeName);
        return storage.query(conn -> {
            // First verify the home exists
            String checkSql = "SELECT 1 FROM homes WHERE player_id = ? AND name = ?";
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setObject(1, ownerId);
                checkStmt.setString(2, normalizedName);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (!rs.next()) {
                        return false; // Home doesn't exist
                    }
                }
            }

            String sql = """
                INSERT INTO home_shares (owner_id, home_name, shared_with_id)
                VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, ownerId);
                stmt.setString(2, normalizedName);
                stmt.setObject(3, targetId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    /**
     * Unshare a home from another player.
     *
     * @return true if the share was removed
     */
    public Single<Boolean> unshareHome(UUID ownerId, String homeName, UUID targetId) {
        String normalizedName = normalizeName(homeName);
        return storage.query(conn -> {
            String sql = """
                DELETE FROM home_shares
                WHERE owner_id = ? AND home_name = ? AND shared_with_id = ?
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, ownerId);
                stmt.setString(2, normalizedName);
                stmt.setObject(3, targetId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    private Home readHome(ResultSet rs) throws java.sql.SQLException {
        UUID ownerId = rs.getObject("player_id", UUID.class);
        String name = rs.getString("name");
        UUID worldId = rs.getObject("world_id", UUID.class);
        double x = rs.getDouble("x");
        double y = rs.getDouble("y");
        double z = rs.getDouble("z");
        float pitch = rs.getFloat("pitch");
        float yaw = rs.getFloat("yaw");

        Set<UUID> sharedWith = new HashSet<>();
        java.sql.Array sharedArray = rs.getArray("shared_with");
        if (sharedArray != null) {
            UUID[] uuids = (UUID[]) sharedArray.getArray();
            for (UUID uuid : uuids) {
                if (uuid != null) {
                    sharedWith.add(uuid);
                }
            }
        }

        return new Home(name, ownerId, worldId, x, y, z, pitch, yaw, sharedWith);
    }
}
