package sh.joey.mc.punish;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.storage.StorageService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles persistence of punishments to PostgreSQL.
 * All operations are async and return RxJava types.
 */
public final class PunishmentStorage {

    private final StorageService storage;

    public PunishmentStorage(StorageService storage) {
        this.storage = storage;
    }

    // ===== CREATE OPERATIONS =====

    /**
     * Create a ban (permanent or temporary).
     */
    public Completable createBan(UUID targetId, @Nullable UUID issuerId,
                                  @Nullable String reason, @Nullable Instant expiresAt) {
        return createPunishment(targetId, null, PunishmentType.BAN, issuerId, reason, expiresAt);
    }

    /**
     * Create an IP ban.
     *
     * @param ip               the IP address to ban
     * @param associatedPlayer optionally, the player whose IP this is
     */
    public Completable createIpBan(String ip, @Nullable UUID associatedPlayer,
                                    @Nullable UUID issuerId, @Nullable String reason) {
        return createPunishment(associatedPlayer, ip, PunishmentType.IP_BAN, issuerId, reason, null);
    }

    /**
     * Create a mute (permanent or temporary).
     */
    public Completable createMute(UUID targetId, @Nullable UUID issuerId,
                                   @Nullable String reason, @Nullable Instant expiresAt) {
        return createPunishment(targetId, null, PunishmentType.MUTE, issuerId, reason, expiresAt);
    }

    /**
     * Record a kick.
     */
    public Completable createKick(UUID targetId, @Nullable UUID issuerId, @Nullable String reason) {
        return createPunishment(targetId, null, PunishmentType.KICK, issuerId, reason, null);
    }

    /**
     * Record a warning.
     */
    public Completable createWarning(UUID targetId, @Nullable UUID issuerId, String reason) {
        return createPunishment(targetId, null, PunishmentType.WARN, issuerId, reason, null);
    }

    private Completable createPunishment(@Nullable UUID targetPlayerId, @Nullable String targetIp,
                                          PunishmentType type, @Nullable UUID issuerId,
                                          @Nullable String reason, @Nullable Instant expiresAt) {
        return storage.execute(conn -> {
            String sql = """
                INSERT INTO punishments (target_player_id, target_ip, type, issued_by_player_id, reason, expires_at)
                VALUES (?, ?, ?::punishment_type, ?, ?, ?)
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (targetPlayerId != null) {
                    stmt.setObject(1, targetPlayerId);
                } else {
                    stmt.setNull(1, Types.OTHER);
                }

                if (targetIp != null) {
                    stmt.setString(2, targetIp);
                } else {
                    stmt.setNull(2, Types.VARCHAR);
                }

                stmt.setString(3, type.name());

                if (issuerId != null) {
                    stmt.setObject(4, issuerId);
                } else {
                    stmt.setNull(4, Types.OTHER);
                }

                if (reason != null) {
                    stmt.setString(5, reason);
                } else {
                    stmt.setNull(5, Types.VARCHAR);
                }

                if (expiresAt != null) {
                    stmt.setTimestamp(6, Timestamp.from(expiresAt));
                } else {
                    stmt.setNull(6, Types.TIMESTAMP_WITH_TIMEZONE);
                }

                stmt.executeUpdate();
            }
        });
    }

    // ===== QUERY ACTIVE OPERATIONS =====

    /**
     * Get active ban for a player (not revoked, not expired).
     */
    public Maybe<Punishment> getActiveBan(UUID playerId) {
        return storage.queryMaybe(conn -> {
            String sql = """
                SELECT id, target_player_id, target_ip, type, issued_by_player_id, reason,
                       expires_at, created_at, revoked_at, revoked_by_player_id
                FROM punishments
                WHERE target_player_id = ?
                  AND type = 'BAN'
                  AND revoked_at IS NULL
                  AND (expires_at IS NULL OR expires_at > NOW())
                ORDER BY created_at DESC
                LIMIT 1
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return readPunishment(rs);
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Get active IP ban (not revoked).
     */
    public Maybe<Punishment> getActiveIpBan(String ip) {
        return storage.queryMaybe(conn -> {
            String sql = """
                SELECT id, target_player_id, target_ip, type, issued_by_player_id, reason,
                       expires_at, created_at, revoked_at, revoked_by_player_id
                FROM punishments
                WHERE target_ip = ?
                  AND type = 'IP_BAN'
                  AND revoked_at IS NULL
                ORDER BY created_at DESC
                LIMIT 1
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, ip);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return readPunishment(rs);
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Get active mute for a player (not revoked, not expired).
     */
    public Maybe<Punishment> getActiveMute(UUID playerId) {
        return storage.queryMaybe(conn -> {
            String sql = """
                SELECT id, target_player_id, target_ip, type, issued_by_player_id, reason,
                       expires_at, created_at, revoked_at, revoked_by_player_id
                FROM punishments
                WHERE target_player_id = ?
                  AND type = 'MUTE'
                  AND revoked_at IS NULL
                  AND (expires_at IS NULL OR expires_at > NOW())
                ORDER BY created_at DESC
                LIMIT 1
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return readPunishment(rs);
                    }
                    return null;
                }
            }
        });
    }

    // ===== REVOKE OPERATIONS =====

    /**
     * Revoke all active bans for a player.
     *
     * @return number of bans revoked
     */
    public Single<Integer> revokeBans(UUID playerId, @Nullable UUID revokedById) {
        return revokeByPlayerAndType(playerId, PunishmentType.BAN, revokedById);
    }

    /**
     * Revoke all active IP bans for an IP address.
     *
     * @return number of IP bans revoked
     */
    public Single<Integer> revokeIpBans(String ip, @Nullable UUID revokedById) {
        return storage.query(conn -> {
            String sql = """
                UPDATE punishments
                SET revoked_at = NOW(), revoked_by_player_id = ?
                WHERE target_ip = ?
                  AND type = 'IP_BAN'
                  AND revoked_at IS NULL
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (revokedById != null) {
                    stmt.setObject(1, revokedById);
                } else {
                    stmt.setNull(1, Types.OTHER);
                }
                stmt.setString(2, ip);
                return stmt.executeUpdate();
            }
        });
    }

    /**
     * Revoke all active IP bans associated with a player.
     *
     * @return number of IP bans revoked
     */
    public Single<Integer> revokeIpBansByPlayer(UUID playerId, @Nullable UUID revokedById) {
        return storage.query(conn -> {
            String sql = """
                UPDATE punishments
                SET revoked_at = NOW(), revoked_by_player_id = ?
                WHERE target_player_id = ?
                  AND type = 'IP_BAN'
                  AND revoked_at IS NULL
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (revokedById != null) {
                    stmt.setObject(1, revokedById);
                } else {
                    stmt.setNull(1, Types.OTHER);
                }
                stmt.setObject(2, playerId);
                return stmt.executeUpdate();
            }
        });
    }

    /**
     * Revoke all active mutes for a player.
     *
     * @return number of mutes revoked
     */
    public Single<Integer> revokeMutes(UUID playerId, @Nullable UUID revokedById) {
        return revokeByPlayerAndType(playerId, PunishmentType.MUTE, revokedById);
    }

    private Single<Integer> revokeByPlayerAndType(UUID playerId, PunishmentType type, @Nullable UUID revokedById) {
        return storage.query(conn -> {
            String sql = """
                UPDATE punishments
                SET revoked_at = NOW(), revoked_by_player_id = ?
                WHERE target_player_id = ?
                  AND type = ?::punishment_type
                  AND revoked_at IS NULL
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                if (revokedById != null) {
                    stmt.setObject(1, revokedById);
                } else {
                    stmt.setNull(1, Types.OTHER);
                }
                stmt.setObject(2, playerId);
                stmt.setString(3, type.name());
                return stmt.executeUpdate();
            }
        });
    }

    // ===== HISTORY OPERATIONS =====

    /**
     * Get punishment history for a player (all types, newest first).
     */
    public Flowable<Punishment> getPunishmentHistory(UUID playerId) {
        return storage.queryFlowable(conn -> {
            String sql = """
                SELECT id, target_player_id, target_ip, type, issued_by_player_id, reason,
                       expires_at, created_at, revoked_at, revoked_by_player_id
                FROM punishments
                WHERE target_player_id = ?
                ORDER BY created_at DESC
                """;

            List<Punishment> punishments = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        punishments.add(readPunishment(rs));
                    }
                }
            }
            return punishments;
        });
    }

    /**
     * Get all punishments (for admin view), newest first, limited.
     */
    public Flowable<Punishment> getAllPunishments(int limit) {
        return storage.queryFlowable(conn -> {
            String sql = """
                SELECT id, target_player_id, target_ip, type, issued_by_player_id, reason,
                       expires_at, created_at, revoked_at, revoked_by_player_id
                FROM punishments
                ORDER BY created_at DESC
                LIMIT ?
                """;

            List<Punishment> punishments = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setInt(1, limit);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        punishments.add(readPunishment(rs));
                    }
                }
            }
            return punishments;
        });
    }

    // ===== HELPERS =====

    private Punishment readPunishment(ResultSet rs) throws java.sql.SQLException {
        UUID id = rs.getObject("id", UUID.class);
        UUID targetPlayerId = rs.getObject("target_player_id", UUID.class);
        String targetIp = rs.getString("target_ip");
        PunishmentType type = PunishmentType.valueOf(rs.getString("type"));
        UUID issuedByPlayerId = rs.getObject("issued_by_player_id", UUID.class);
        String reason = rs.getString("reason");

        Timestamp expiresAtTs = rs.getTimestamp("expires_at");
        Instant expiresAt = expiresAtTs != null ? expiresAtTs.toInstant() : null;

        Timestamp createdAtTs = rs.getTimestamp("created_at");
        Instant createdAt = createdAtTs.toInstant();

        Timestamp revokedAtTs = rs.getTimestamp("revoked_at");
        Instant revokedAt = revokedAtTs != null ? revokedAtTs.toInstant() : null;

        UUID revokedByPlayerId = rs.getObject("revoked_by_player_id", UUID.class);

        return new Punishment(id, targetPlayerId, targetIp, type, issuedByPlayerId,
                reason, expiresAt, createdAt, revokedAt, revokedByPlayerId);
    }
}
