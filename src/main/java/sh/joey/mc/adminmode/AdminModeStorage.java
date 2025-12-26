package sh.joey.mc.adminmode;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import sh.joey.mc.storage.StorageService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Storage for admin mode state.
 */
public final class AdminModeStorage {

    private final StorageService storage;

    public AdminModeStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Gets the admin mode state for a player, if they are in admin mode.
     */
    public Maybe<AdminModeState> getState(UUID playerId) {
        return storage.queryMaybe(conn -> {
            try (var stmt = conn.prepareStatement(
                    "SELECT player_id, world_id, snapshot_id, entered_at FROM admin_mode_state WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    return parseState(rs);
                }
                return null;
            }
        });
    }

    /**
     * Gets all players currently in admin mode.
     * Used on server startup to populate the in-memory cache.
     */
    public Flowable<AdminModeState> getAllInAdminMode() {
        return storage.queryFlowable(conn -> {
            List<AdminModeState> states = new ArrayList<>();
            try (var stmt = conn.prepareStatement(
                    "SELECT player_id, world_id, snapshot_id, entered_at FROM admin_mode_state")) {
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    states.add(parseState(rs));
                }
            }
            return states;
        });
    }

    /**
     * Records a player entering admin mode.
     */
    public Completable enterAdminMode(UUID playerId, UUID worldId, UUID snapshotId) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO admin_mode_state (player_id, world_id, snapshot_id, entered_at)
                    VALUES (?, ?, ?, NOW())
                    ON CONFLICT (player_id) DO UPDATE SET
                        world_id = EXCLUDED.world_id,
                        snapshot_id = EXCLUDED.snapshot_id,
                        entered_at = NOW()
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setObject(2, worldId);
                stmt.setObject(3, snapshotId);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Records a player exiting admin mode.
     */
    public Completable exitAdminMode(UUID playerId) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("DELETE FROM admin_mode_state WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                stmt.executeUpdate();
            }
        });
    }

    private AdminModeState parseState(ResultSet rs) throws SQLException {
        return new AdminModeState(
                UUID.fromString(rs.getString("player_id")),
                UUID.fromString(rs.getString("world_id")),
                UUID.fromString(rs.getString("snapshot_id")),
                rs.getTimestamp("entered_at").toInstant()
        );
    }
}
