package sh.joey.mc.steve;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import sh.joey.mc.storage.StorageService;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage for Steve AI chatbot cooldowns and history.
 */
public final class SteveStorage {

    private static final Gson GSON = new Gson();
    private static final Type CITATION_LIST_TYPE = new TypeToken<List<SteveAnswer.Citation>>() {}.getType();

    private final StorageService storage;

    public SteveStorage(StorageService storage) {
        this.storage = storage;
    }

    // ========================================
    // HISTORY
    // ========================================

    /**
     * Save a Steve Q&A to history.
     *
     * @param playerId the player who asked
     * @param question the question text
     * @param answer the AI response
     * @param modelName the model used
     * @param contextCount number of prior Q&A turns included as conversation context
     */
    public Completable saveHistory(UUID playerId, String question, SteveAnswer answer, String modelName, int contextCount) {
        return storage.execute(conn -> {
            try (var stmt = conn.prepareStatement("""
                    INSERT INTO steve_history (player_id, question, answer, citations, cost_cents, model_name, context_count)
                    VALUES (?, ?, ?, ?::jsonb, ?, ?, ?)
                    """)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, question);
                stmt.setString(3, answer.text());

                // Serialize citations to JSON (null if empty)
                if (answer.citations().isEmpty()) {
                    stmt.setNull(4, java.sql.Types.OTHER);
                } else {
                    stmt.setString(4, GSON.toJson(answer.citations()));
                }

                // Cost (null if zero)
                if (answer.costCents() > 0) {
                    stmt.setDouble(5, answer.costCents());
                } else {
                    stmt.setNull(5, java.sql.Types.DOUBLE);
                }

                stmt.setString(6, modelName);
                stmt.setInt(7, contextCount);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Get history entries for a player, most recent first.
     */
    public Flowable<SteveHistoryEntry> getHistory(UUID playerId) {
        return storage.queryFlowable(conn -> {
            List<SteveHistoryEntry> entries = new ArrayList<>();
            try (var stmt = conn.prepareStatement("""
                    SELECT id, player_id, question, answer, citations, cost_cents, model_name, context_count, asked_at
                    FROM steve_history
                    WHERE player_id = ?
                    ORDER BY asked_at DESC
                    """)) {
                stmt.setObject(1, playerId);
                var rs = stmt.executeQuery();
                while (rs.next()) {
                    // Parse citations from JSON
                    List<SteveAnswer.Citation> citations = List.of();
                    String citationsJson = rs.getString("citations");
                    if (citationsJson != null && !citationsJson.isEmpty()) {
                        citations = GSON.fromJson(citationsJson, CITATION_LIST_TYPE);
                    }

                    // Parse optional cost
                    Optional<Double> costCents = Optional.empty();
                    double cost = rs.getDouble("cost_cents");
                    if (!rs.wasNull()) {
                        costCents = Optional.of(cost);
                    }

                    // Parse optional model name
                    Optional<String> modelName = Optional.ofNullable(rs.getString("model_name"));

                    entries.add(new SteveHistoryEntry(
                            rs.getObject("id", UUID.class),
                            rs.getObject("player_id", UUID.class),
                            rs.getString("question"),
                            rs.getString("answer"),
                            citations,
                            costCents,
                            modelName,
                            rs.getInt("context_count"),
                            rs.getTimestamp("asked_at").toInstant()
                    ));
                }
            }
            return entries;
        });
    }

    /**
     * Get the count of history entries for a player.
     */
    public Single<Integer> getHistoryCount(UUID playerId) {
        return storage.query(conn -> {
            try (var stmt = conn.prepareStatement(
                    "SELECT COUNT(*) FROM steve_history WHERE player_id = ?")) {
                stmt.setObject(1, playerId);
                var rs = stmt.executeQuery();
                rs.next();
                return rs.getInt(1);
            }
        });
    }

    // ========================================
    // COOLDOWNS
    // ========================================

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
