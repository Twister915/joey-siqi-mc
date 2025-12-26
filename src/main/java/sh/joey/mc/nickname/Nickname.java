package sh.joey.mc.nickname;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a player's custom display name (nickname).
 */
public record Nickname(
        UUID playerId,
        String nickname,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Normalizes a nickname for comparison: lowercase and trimmed.
     */
    public static String normalize(String nickname) {
        return nickname.toLowerCase().trim();
    }
}
