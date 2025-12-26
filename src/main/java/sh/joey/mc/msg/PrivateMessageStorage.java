package sh.joey.mc.msg;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import sh.joey.mc.storage.StorageService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Storage operations for private messages.
 */
public final class PrivateMessageStorage {

    private final StorageService storage;

    public PrivateMessageStorage(StorageService storage) {
        this.storage = storage;
    }

    /**
     * Store a new private message.
     */
    public Completable storeMessage(UUID senderId, UUID recipientId, String content) {
        return storage.execute(conn -> {
            String sql = """
                INSERT INTO private_messages (sender_id, recipient_id, content)
                VALUES (?, ?, ?)
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, senderId);
                stmt.setObject(2, recipientId);
                stmt.setString(3, content);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Get all unread messages for a player, ordered by creation time (oldest first).
     */
    public Flowable<PrivateMessage> getUnreadMessages(UUID recipientId) {
        return storage.queryFlowable(conn -> {
            String sql = """
                SELECT id, sender_id, recipient_id, content, read_at, created_at
                FROM private_messages
                WHERE recipient_id = ? AND read_at IS NULL
                ORDER BY created_at ASC
                """;
            List<PrivateMessage> messages = new ArrayList<>();
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, recipientId);
                try (var rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        messages.add(readMessage(rs));
                    }
                }
            }
            return messages;
        });
    }

    /**
     * Mark a specific message as read.
     */
    public Completable markAsRead(UUID messageId) {
        return storage.execute(conn -> {
            String sql = """
                UPDATE private_messages
                SET read_at = NOW()
                WHERE id = ?
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, messageId);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Mark all unread messages for a player as read.
     */
    public Completable markAllAsRead(UUID recipientId) {
        return storage.execute(conn -> {
            String sql = """
                UPDATE private_messages
                SET read_at = NOW()
                WHERE recipient_id = ? AND read_at IS NULL
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, recipientId);
                stmt.executeUpdate();
            }
        });
    }

    /**
     * Count pending (unread) messages from a specific sender to a recipient.
     * Used to enforce the max queued messages limit.
     */
    public Single<Integer> countPendingFromSender(UUID senderId, UUID recipientId) {
        return storage.query(conn -> {
            String sql = """
                SELECT COUNT(*)
                FROM private_messages
                WHERE sender_id = ? AND recipient_id = ? AND read_at IS NULL
                """;
            try (var stmt = conn.prepareStatement(sql)) {
                stmt.setObject(1, senderId);
                stmt.setObject(2, recipientId);
                try (var rs = stmt.executeQuery()) {
                    rs.next();
                    return rs.getInt(1);
                }
            }
        });
    }

    private PrivateMessage readMessage(ResultSet rs) throws SQLException {
        Timestamp readAtTs = rs.getTimestamp("read_at");
        return new PrivateMessage(
                rs.getObject("id", UUID.class),
                rs.getObject("sender_id", UUID.class),
                rs.getObject("recipient_id", UUID.class),
                rs.getString("content"),
                readAtTs != null ? readAtTs.toInstant() : null,
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
