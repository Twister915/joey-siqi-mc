package sh.joey.mc.rtp;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import sh.joey.mc.storage.StorageService;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Storage for RTP cooldowns.
 */
public final class RtpStorage {

    private final StorageService storage;

    public RtpStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Get the last time a player used RTP.
     */
    public Maybe<Instant> getLastRtpTime(UUID playerId) {
        return storage.queryMaybe(conn -> {
            try (var stmt = conn.prepareStatement(
                    "SELECT last_used_at FROM rtp_cooldowns WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                var rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getTimestamp("last_used_at").toInstant();
                }
                return null;
            }
        });
    }

    /**
     * Record that a player used RTP (upserts the timestamp).
     */
    public Completable recordRtpUsage(UUID playerId) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO rtp_cooldowns (player_id, last_used_at)
                    VALUES (?, NOW())
                    ON CONFLICT (player_id) DO UPDATE SET last_used_at = NOW()
                    """)) {
                stmt.setObject(1, playerId);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Get all active cooldowns (players who used RTP within the specified duration).
     * Used to restore cooldowns on server startup.
     */
    public Flowable<CooldownEntry> getActiveCooldowns(Duration cooldownDuration) {
        return storage.queryFlowable(conn -> {
            List<CooldownEntry> entries = new ArrayList<>();
            try (var stmt = conn.prepareStatement(
                    "SELECT player_id, last_used_at FROM rtp_cooldowns WHERE last_used_at > NOW() - ?::INTERVAL")) {
                stmt.setString(1, cooldownDuration.toMinutes() + " minutes");
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    entries.add(new CooldownEntry(
                            UUID.fromString(rs.getString("player_id")),
                            rs.getTimestamp("last_used_at").toInstant()
                    ));
                }
            }
            return entries;
        });
    }

    public record CooldownEntry(UUID playerId, Instant lastUsedAt) {}
}
