package sh.joey.mc.merit;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import sh.joey.mc.storage.StorageService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Database operations for the merit system.
 */
public final class MeritStorage {

    private final StorageService storage;

    public MeritStorage(StorageService storage) {
        this.storage = storage;
    }

    // ===== PLAYER MERIT =====

    /**
     * Get a player's total merit and level.
     */
    public Maybe<PlayerMerit> getPlayerMerit(UUID playerId) {
        return storage.queryMaybe(conn -> {
            try (var stmt = conn.prepareStatement(
                    "SELECT total_merit, level FROM player_merit WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerMerit(playerId, rs.getLong("total_merit"), rs.getInt("level"));
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Get or create a player's merit record.
     */
    public Single<PlayerMerit> getOrCreatePlayerMerit(UUID playerId) {
        return storage.query(conn -> {
            // Try to insert, or do nothing if exists
            try (var stmt = conn.prepareStatement(
                    "INSERT INTO player_merit (player_id) VALUES (?) ON CONFLICT DO NOTHING")) {
                stmt.setObject(1, playerId);
                stmt.executeUpdate();
            }

            // Now fetch
            try (var stmt = conn.prepareStatement(
                    "SELECT total_merit, level FROM player_merit WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerMerit(playerId, rs.getLong("total_merit"), rs.getInt("level"));
                    }
                    // Should never happen after insert
                    return new PlayerMerit(playerId, 0, 1);
                }
            }
        });
    }

    /**
     * Add merit to a player and update their level.
     */
    public Completable addMerit(UUID playerId, long amount, int newLevel) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO player_merit (player_id, total_merit, level, updated_at)
                    VALUES (?, ?, ?, NOW())
                    ON CONFLICT (player_id) DO UPDATE SET
                        total_merit = player_merit.total_merit + EXCLUDED.total_merit,
                        level = EXCLUDED.level,
                        updated_at = NOW()
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setLong(2, amount);
                stmt.setInt(3, newLevel);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Set a player's total merit (for admin commands).
     */
    public Completable setMerit(UUID playerId, long totalMerit, int level) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO player_merit (player_id, total_merit, level, updated_at)
                    VALUES (?, ?, ?, NOW())
                    ON CONFLICT (player_id) DO UPDATE SET
                        total_merit = EXCLUDED.total_merit,
                        level = EXCLUDED.level,
                        updated_at = NOW()
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setLong(2, totalMerit);
                stmt.setInt(3, level);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Get leaderboard of top players by total merit.
     */
    public Flowable<PlayerMerit> getLeaderboard(int limit) {
        return storage.queryFlowable(conn -> {
            List<PlayerMerit> results = new ArrayList<>();
            try (var stmt = conn.prepareStatement(
                    "SELECT player_id, total_merit, level FROM player_merit ORDER BY total_merit DESC LIMIT ?")) {
                stmt.setInt(1, limit);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(new PlayerMerit(
                                rs.getObject("player_id", UUID.class),
                                rs.getLong("total_merit"),
                                rs.getInt("level")));
                    }
                }
            }
            return results;
        });
    }

    // ===== PLAYER PROGRESS (per week) =====

    /**
     * Batch update progress stats for a player for a specific week.
     */
    public Completable updateProgress(UUID playerId, int weekNumber, Map<String, Long> deltas) {
        if (deltas.isEmpty()) {
            return Completable.complete();
        }

        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO player_progress (player_id, week_number, stat_key, value, updated_at)
                    VALUES (?, ?, ?, ?, NOW())
                    ON CONFLICT (player_id, week_number, stat_key) DO UPDATE SET
                        value = player_progress.value + EXCLUDED.value,
                        updated_at = NOW()
                    """)) {
                for (var entry : deltas.entrySet()) {
                    stmt.setObject(1, playerId);
                    stmt.setInt(2, weekNumber);
                    stmt.setString(3, entry.getKey());
                    stmt.setLong(4, entry.getValue());
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }
        });
    }

    /**
     * Get all progress stats for a player for a specific week.
     */
    public Single<Map<String, Long>> getProgress(UUID playerId, int weekNumber) {
        return storage.query(conn -> {
            Map<String, Long> progress = new HashMap<>();
            try (var stmt = conn.prepareStatement(
                    "SELECT stat_key, value FROM player_progress WHERE player_id = ? AND week_number = ?")) {
                stmt.setObject(1, playerId);
                stmt.setInt(2, weekNumber);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        progress.put(rs.getString("stat_key"), rs.getLong("value"));
                    }
                }
            }
            return progress;
        });
    }

    // ===== WEEKLY CHALLENGE PROGRESS =====

    /**
     * Get weekly challenge progress for a player.
     */
    public Single<Map<String, WeeklyChallengeProgress>> getWeeklyChallengeProgress(UUID playerId, int weekNumber) {
        return storage.query(conn -> {
            Map<String, WeeklyChallengeProgress> progress = new HashMap<>();
            try (var stmt = conn.prepareStatement(
                    "SELECT challenge_id, progress, completed, completed_at " +
                    "FROM weekly_challenge_progress WHERE player_id = ? AND week_number = ?")) {
                stmt.setObject(1, playerId);
                stmt.setInt(2, weekNumber);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        Timestamp ts = rs.getTimestamp("completed_at");
                        progress.put(rs.getString("challenge_id"), new WeeklyChallengeProgress(
                                rs.getLong("progress"),
                                rs.getBoolean("completed"),
                                ts != null ? ts.toInstant() : null));
                    }
                }
            }
            return progress;
        });
    }

    /**
     * Update weekly challenge progress for a player.
     */
    public Completable updateWeeklyChallengeProgress(UUID playerId, int weekNumber, String challengeId, long progress) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO weekly_challenge_progress (player_id, week_number, challenge_id, progress)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (player_id, week_number, challenge_id) DO UPDATE SET
                        progress = EXCLUDED.progress
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setInt(2, weekNumber);
                stmt.setString(3, challengeId);
                stmt.setLong(4, progress);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Mark a weekly challenge as completed.
     */
    public Completable completeWeeklyChallenge(UUID playerId, int weekNumber, String challengeId, long progress) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO weekly_challenge_progress (player_id, week_number, challenge_id, progress, completed, completed_at)
                    VALUES (?, ?, ?, ?, TRUE, NOW())
                    ON CONFLICT (player_id, week_number, challenge_id) DO UPDATE SET
                        progress = EXCLUDED.progress,
                        completed = TRUE,
                        completed_at = NOW()
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setInt(2, weekNumber);
                stmt.setString(3, challengeId);
                stmt.setLong(4, progress);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Record a challenge completion for history.
     */
    public Completable recordCompletion(UUID playerId, String challengeId, int weekNumber, int meritEarned) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement(
                    "INSERT INTO challenge_completions (player_id, challenge_id, week_number, merit_earned) " +
                    "VALUES (?, ?, ?, ?)")) {
                stmt.setObject(1, playerId);
                stmt.setString(2, challengeId);
                stmt.setInt(3, weekNumber);
                stmt.setInt(4, meritEarned);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Get challenge completion history for a player.
     */
    public Flowable<ChallengeCompletion> getCompletionHistory(UUID playerId, int limit) {
        return storage.queryFlowable(conn -> {
            List<ChallengeCompletion> results = new ArrayList<>();
            try (var stmt = conn.prepareStatement(
                    "SELECT challenge_id, week_number, merit_earned, completed_at " +
                    "FROM challenge_completions WHERE player_id = ? ORDER BY completed_at DESC LIMIT ?")) {
                stmt.setObject(1, playerId);
                stmt.setInt(2, limit);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(new ChallengeCompletion(
                                rs.getString("challenge_id"),
                                rs.getInt("week_number"),
                                rs.getInt("merit_earned"),
                                rs.getTimestamp("completed_at").toInstant()));
                    }
                }
            }
            return results;
        });
    }

    /**
     * Get weekly merit leaderboard.
     */
    public Flowable<WeeklyLeaderboardEntry> getWeeklyLeaderboard(int weekNumber, int limit) {
        return storage.queryFlowable(conn -> {
            List<WeeklyLeaderboardEntry> results = new ArrayList<>();
            try (var stmt = conn.prepareStatement("""
                    SELECT player_id, SUM(merit_earned) as weekly_merit
                    FROM challenge_completions
                    WHERE week_number = ?
                    GROUP BY player_id
                    ORDER BY weekly_merit DESC
                    LIMIT ?
                    """)) {
                stmt.setInt(1, weekNumber);
                stmt.setInt(2, limit);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        results.add(new WeeklyLeaderboardEntry(
                                rs.getObject("player_id", UUID.class),
                                rs.getLong("weekly_merit")));
                    }
                }
            }
            return results;
        });
    }

    // ===== WEEKLY ONLINE TIME =====

    /**
     * Get weekly online time for a player.
     */
    public Single<WeeklyOnlineTime> getWeeklyOnlineTime(UUID playerId, int weekNumber) {
        return storage.query(conn -> {
            try (var stmt = conn.prepareStatement(
                    "SELECT seconds_online, merit_claimed FROM weekly_online_time " +
                    "WHERE player_id = ? AND week_number = ?")) {
                stmt.setObject(1, playerId);
                stmt.setInt(2, weekNumber);
                try (var rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return new WeeklyOnlineTime(
                                rs.getLong("seconds_online"),
                                rs.getInt("merit_claimed"));
                    }
                    return new WeeklyOnlineTime(0, 0);
                }
            }
        });
    }

    /**
     * Update weekly online time for a player.
     */
    public Completable updateWeeklyOnlineTime(UUID playerId, int weekNumber, long secondsToAdd) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO weekly_online_time (player_id, week_number, seconds_online)
                    VALUES (?, ?, ?)
                    ON CONFLICT (player_id, week_number) DO UPDATE SET
                        seconds_online = weekly_online_time.seconds_online + EXCLUDED.seconds_online
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setInt(2, weekNumber);
                stmt.setLong(3, secondsToAdd);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Claim online time merit.
     */
    public Completable claimOnlineTimeMerit(UUID playerId, int weekNumber, int meritClaimed) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    UPDATE weekly_online_time
                    SET merit_claimed = ?
                    WHERE player_id = ? AND week_number = ?
                    """)) {
                stmt.setInt(1, meritClaimed);
                stmt.setObject(2, playerId);
                stmt.setInt(3, weekNumber);
                stmt.executeUpdate();
            }
        });
    }

    // ===== RECORD TYPES =====

    public record PlayerMerit(UUID playerId, long totalMerit, int level) {}

    public record WeeklyChallengeProgress(long progress, boolean completed, Instant completedAt) {}

    public record ChallengeCompletion(String challengeId, int weekNumber, int meritEarned, Instant completedAt) {}

    public record WeeklyLeaderboardEntry(UUID playerId, long weeklyMerit) {}

    public record WeeklyOnlineTime(long secondsOnline, int meritClaimed) {}
}
