package sh.joey.mc.msg;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a private message between two players.
 */
public record PrivateMessage(
        UUID id,
        UUID senderId,
        UUID recipientId,
        String content,
        @Nullable Instant readAt,
        Instant createdAt
) {}
