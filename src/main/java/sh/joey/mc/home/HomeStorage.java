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

    public enum ShareResult {
        SUCCESS,
        HOME_NOT_FOUND,
        ALREADY_SHARED
    }

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
                SELECT h.id, h.player_id, h.name, h.world_id, h.x, h.y, h.z, h.pitch, h.yaw,
                       pn.username as owner_name,
                       COALESCE(array_agg(hs.shared_with_id) FILTER (WHERE hs.shared_with_id IS NOT NULL), '{}') as shared_with
                FROM homes h
                LEFT JOIN home_shares hs ON h.id = hs.home_id
                LEFT JOIN player_names pn ON h.player_id = pn.player_id
                WHERE h.player_id = ? AND h.name = ? AND h.deleted_at IS NULL
                GROUP BY h.id, h.player_id, h.name, h.world_id, h.x, h.y, h.z, h.pitch, h.yaw, pn.username
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
                SELECT h.id, h.player_id, h.name, h.world_id, h.x, h.y, h.z, h.pitch, h.yaw,
                       pn.username as owner_name,
                       COALESCE(array_agg(hs.shared_with_id) FILTER (WHERE hs.shared_with_id IS NOT NULL), '{}') as shared_with
                FROM homes h
                LEFT JOIN home_shares hs ON h.id = hs.home_id
                LEFT JOIN player_names pn ON h.player_id = pn.player_id
                WHERE h.player_id = ? AND h.deleted_at IS NULL
                GROUP BY h.id, h.player_id, h.name, h.world_id, h.x, h.y, h.z, h.pitch, h.yaw, pn.username
                UNION ALL
                SELECT h.id, h.player_id, h.name, h.world_id, h.x, h.y, h.z, h.pitch, h.yaw,
                       pn.username as owner_name,
                       COALESCE(array_agg(hs2.shared_with_id) FILTER (WHERE hs2.shared_with_id IS NOT NULL), '{}') as shared_with
                FROM homes h
                INNER JOIN home_shares hs ON h.id = hs.home_id AND hs.shared_with_id = ?
                LEFT JOIN home_shares hs2 ON h.id = hs2.home_id
                LEFT JOIN player_names pn ON h.player_id = pn.player_id
                WHERE h.deleted_at IS NULL
                GROUP BY h.id, h.player_id, h.name, h.world_id, h.x, h.y, h.z, h.pitch, h.yaw, pn.username
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
            String sql = "SELECT EXISTS(SELECT 1 FROM homes WHERE player_id = ? AND deleted_at IS NULL)";

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
     * Save a home. If a home with the same name exists, it is soft-deleted first.
     * This runs in a transaction to ensure atomicity.
     */
    public Completable setHome(UUID playerId, Home home) {
        String normalizedName = normalizeName(home.name());
        return storage.execute(conn -> {
            conn.setAutoCommit(false);
            try {
                // Step 1: Soft-delete any existing active home with this name
                String softDeleteSql = """
                    UPDATE homes
                    SET deleted_at = NOW()
                    WHERE player_id = ? AND name = ? AND deleted_at IS NULL
                    """;
                try (PreparedStatement stmt = conn.prepareStatement(softDeleteSql)) {
                    stmt.setObject(1, playerId);
                    stmt.setString(2, normalizedName);
                    stmt.executeUpdate();
                }

                // Step 2: Insert new home
                String insertSql = """
                    INSERT INTO homes (id, player_id, name, world_id, x, y, z, pitch, yaw)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
                try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                    stmt.setObject(1, home.id());
                    stmt.setObject(2, playerId);
                    stmt.setString(3, normalizedName);
                    stmt.setObject(4, home.worldId());
                    stmt.setDouble(5, home.x());
                    stmt.setDouble(6, home.y());
                    stmt.setDouble(7, home.z());
                    stmt.setFloat(8, home.pitch());
                    stmt.setFloat(9, home.yaw());
                    stmt.executeUpdate();
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        });
    }

    /**
     * Soft-delete a home by setting deleted_at.
     *
     * @return true if a home was deleted
     */
    public Single<Boolean> deleteHome(UUID playerId, String name) {
        String normalizedName = normalizeName(name);
        return storage.query(conn -> {
            String sql = """
                UPDATE homes
                SET deleted_at = NOW()
                WHERE player_id = ? AND name = ? AND deleted_at IS NULL
                """;

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
     * @return ShareResult indicating success, home not found, or already shared
     */
    public Single<ShareResult> shareHome(UUID ownerId, String homeName, UUID targetId) {
        String normalizedName = normalizeName(homeName);
        return storage.query(conn -> {
            // First get the home's id
            String findSql = "SELECT id FROM homes WHERE player_id = ? AND name = ? AND deleted_at IS NULL";
            UUID homeId;
            try (PreparedStatement stmt = conn.prepareStatement(findSql)) {
                stmt.setObject(1, ownerId);
                stmt.setString(2, normalizedName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return ShareResult.HOME_NOT_FOUND;
                    }
                    homeId = rs.getObject("id", UUID.class);
                }
            }

            String sql = """
                INSERT INTO home_shares (home_id, shared_with_id)
                VALUES (?, ?)
                ON CONFLICT DO NOTHING
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, homeId);
                stmt.setObject(2, targetId);
                return stmt.executeUpdate() > 0 ? ShareResult.SUCCESS : ShareResult.ALREADY_SHARED;
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
            // First get the home's id
            String findSql = "SELECT id FROM homes WHERE player_id = ? AND name = ? AND deleted_at IS NULL";
            UUID homeId;
            try (PreparedStatement stmt = conn.prepareStatement(findSql)) {
                stmt.setObject(1, ownerId);
                stmt.setString(2, normalizedName);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) {
                        return false; // Home doesn't exist
                    }
                    homeId = rs.getObject("id", UUID.class);
                }
            }

            String sql = "DELETE FROM home_shares WHERE home_id = ? AND shared_with_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, homeId);
                stmt.setObject(2, targetId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    private Home readHome(ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID ownerId = rs.getObject("player_id", UUID.class);
        String name = rs.getString("name");
        String ownerName = rs.getString("owner_name");
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

        return new Home(id, name, ownerId, ownerName, worldId, x, y, z, pitch, yaw, sharedWith);
    }
}
