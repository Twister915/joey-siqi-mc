package sh.joey.mc.nickname;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import sh.joey.mc.storage.StorageService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles persistence of player nicknames to PostgreSQL.
 * All operations are async and return RxJava types.
 */
public final class NicknameStorage {

    private final StorageService storage;

    public NicknameStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Get a player's nickname.
     */
    public Maybe<Nickname> getNickname(UUID playerId) {
        return storage.queryMaybe(conn -> {
            String sql = "SELECT player_id, nickname, created_at, updated_at FROM player_nicknames WHERE player_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return readNickname(rs);
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Find a player ID by their nickname (case-insensitive).
     */
    public Maybe<UUID> findPlayerIdByNickname(String nickname) {
        String normalized = Nickname.normalize(nickname);
        return storage.queryMaybe(conn -> {
            String sql = "SELECT player_id FROM player_nicknames WHERE LOWER(nickname) = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, normalized);

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getObject("player_id", UUID.class);
                    }
                    return null;
                }
            }
        });
    }

    /**
     * Check if a nickname is available (not used by any player).
     */
    public Single<Boolean> isNicknameAvailable(String nickname) {
        String normalized = Nickname.normalize(nickname);
        return storage.query(conn -> {
            String sql = "SELECT NOT EXISTS(SELECT 1 FROM player_nicknames WHERE LOWER(nickname) = ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, normalized);

                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    return rs.getBoolean(1);
                }
            }
        });
    }

    /**
     * Check if a nickname is available for a specific player (excluding their own current nickname).
     */
    public Single<Boolean> isNicknameAvailableFor(UUID playerId, String nickname) {
        String normalized = Nickname.normalize(nickname);
        return storage.query(conn -> {
            String sql = "SELECT NOT EXISTS(SELECT 1 FROM player_nicknames WHERE LOWER(nickname) = ? AND player_id != ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, normalized);
                stmt.setObject(2, playerId);

                try (ResultSet rs = stmt.executeQuery()) {
                    rs.next();
                    return rs.getBoolean(1);
                }
            }
        });
    }

    /**
     * Set or update a player's nickname.
     */
    public Completable setNickname(UUID playerId, String nickname) {
        return storage.execute(conn -> {
            String sql = """
                INSERT INTO player_nicknames (player_id, nickname, created_at, updated_at)
                VALUES (?, ?, NOW(), NOW())
                ON CONFLICT (player_id) DO UPDATE SET
                    nickname = EXCLUDED.nickname,
                    updated_at = NOW()
                """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                stmt.setString(2, nickname);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Remove a player's nickname.
     *
     * @return true if a nickname was removed
     */
    public Single<Boolean> removeNickname(UUID playerId) {
        return storage.query(conn -> {
            String sql = "DELETE FROM player_nicknames WHERE player_id = ?";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, playerId);
                return stmt.executeUpdate() > 0;
            }
        });
    }

    /**
     * Find nicknames by prefix for tab completion (case-insensitive).
     */
    public Flowable<String> findNicknamesByPrefix(String prefix, int limit) {
        String normalized = Nickname.normalize(prefix);
        return storage.queryFlowable(conn -> {
            String sql = """
                SELECT nickname FROM player_nicknames
                WHERE LOWER(nickname) LIKE ? || '%'
                ORDER BY nickname
                LIMIT ?
                """;

            List<String> nicknames = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, normalized);
                stmt.setInt(2, limit);

                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        nicknames.add(rs.getString("nickname"));
                    }
                }
            }
            return nicknames;
        });
    }

    private Nickname readNickname(ResultSet rs) throws java.sql.SQLException {
        UUID playerId = rs.getObject("player_id", UUID.class);
        String nickname = rs.getString("nickname");
        Timestamp createdAt = rs.getTimestamp("created_at");
        Timestamp updatedAt = rs.getTimestamp("updated_at");

        return new Nickname(
                playerId,
                nickname,
                createdAt != null ? createdAt.toInstant() : Instant.now(),
                updatedAt != null ? updatedAt.toInstant() : Instant.now()
        );
    }
}
