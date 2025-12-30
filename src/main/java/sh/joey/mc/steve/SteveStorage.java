package sh.joey.mc.steve;

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
 * Storage for Steve AI chatbot cooldowns.
 */
public final class SteveStorage {

    private final StorageService storage;

    public SteveStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Get the last time a player asked Steve a question.
     */
    public Maybe<Instant> getLastSteveTime(UUID playerId) {
        return storage.queryMaybe(conn -> {
            try (var stmt = conn.prepareStatement(
                    "SELECT last_used_at FROM steve_cooldowns WHERE player_id = ?")) {
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
     * Record that a player asked Steve a question (upserts the timestamp).
     */
    public Completable recordSteveUsage(UUID playerId) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO steve_cooldowns (player_id, last_used_at)
                    VALUES (?, NOW())
                    ON CONFLICT (player_id) DO UPDATE SET last_used_at = NOW()
                    """)) {
                stmt.setObject(1, playerId);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Get all active cooldowns (players who asked Steve within the specified duration).
     * Used to restore cooldowns on server startup.
     */
    public Flowable<CooldownEntry> getActiveCooldowns(Duration cooldownDuration) {
        return storage.queryFlowable(conn -> {
            List<CooldownEntry> entries = new ArrayList<>();
            try (var stmt = conn.prepareStatement(
                    "SELECT player_id, last_used_at FROM steve_cooldowns WHERE last_used_at > NOW() - ?::INTERVAL")) {
                stmt.setString(1, cooldownDuration.toSeconds() + " seconds");
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
