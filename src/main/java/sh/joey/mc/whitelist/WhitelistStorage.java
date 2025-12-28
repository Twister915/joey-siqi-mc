package sh.joey.mc.whitelist;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.storage.StorageService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Database operations for the custom whitelist system.
 */
public final class WhitelistStorage {

    private final StorageService storage;

    public WhitelistStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Check if a player is whitelisted.
     */
    public Single<Boolean> isWhitelisted(UUID playerId) {
        return storage.query(conn -> {
            try (var stmt = conn.prepareStatement("SELECT 1 FROM whitelist WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                return stmt.executeQuery().next();
            }
        });
    }

    /**
     * Get a whitelist entry for a player.
     */
    public Maybe<WhitelistEntry> getEntry(UUID playerId) {
        return storage.queryMaybe(conn -> {
            try (var stmt = conn.prepareStatement(
                    "SELECT player_id, player_name, invited_by_player_id, created_at FROM whitelist WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    return parseEntry(rs);
                }
                return null;
            }
        });
    }

    /**
     * Add a player to the whitelist.
     */
    public Completable addPlayer(UUID playerId, String playerName, @Nullable UUID invitedBy) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO whitelist (player_id, player_name, invited_by_player_id)
                    VALUES (?, ?, ?)
                    ON CONFLICT (player_id) DO UPDATE SET
                        player_name = EXCLUDED.player_name,
                        invited_by_player_id = COALESCE(whitelist.invited_by_player_id, EXCLUDED.invited_by_player_id)
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, playerName);
                stmt.setObject(3, invitedBy);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Remove a player from the whitelist.
     */
    public Single<Boolean> removePlayer(UUID playerId) {
        return storage.query(conn -> {
            try (var stmt = conn.prepareStatement("DELETE FROM whitelist WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    /**
     * Get all whitelisted players.
     */
    public Flowable<WhitelistEntry> getAllEntries() {
        return storage.queryFlowable(conn -> {
            List<WhitelistEntry> entries = new ArrayList<>();
            try (var stmt = conn.prepareStatement(
                    "SELECT player_id, player_name, invited_by_player_id, created_at FROM whitelist ORDER BY created_at DESC")) {
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    entries.add(parseEntry(rs));
                }
            }
            return entries;
        });
    }

    /**
     * Get all players invited by a specific player (audit query).
     */
    public Flowable<WhitelistEntry> getInvitedBy(UUID inviterId) {
        return storage.queryFlowable(conn -> {
            List<WhitelistEntry> entries = new ArrayList<>();
            try (var stmt = conn.prepareStatement(
                    "SELECT player_id, player_name, invited_by_player_id, created_at FROM whitelist WHERE invited_by_player_id = ? ORDER BY created_at DESC")) {
                stmt.setObject(1, inviterId);
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    entries.add(parseEntry(rs));
                }
            }
            return entries;
        });
    }

    /**
     * Count total whitelisted players.
     */
    public Single<Integer> count() {
        return storage.query(conn -> {
            try (var stmt = conn.prepareStatement("SELECT COUNT(*) FROM whitelist")) {
                var rs = stmt.executeQuery();
                rs.next();
                return rs.getInt(1);
            }
        });
    }

    private WhitelistEntry parseEntry(ResultSet rs) throws SQLException {
        String invitedByStr = rs.getString("invited_by_player_id");
        return new WhitelistEntry(
                UUID.fromString(rs.getString("player_id")),
                rs.getString("player_name"),
                invitedByStr != null ? UUID.fromString(invitedByStr) : null,
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
