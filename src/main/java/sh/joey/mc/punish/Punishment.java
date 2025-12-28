package sh.joey.mc.punish;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a punishment record from the database.
 *
 * @param id                 Unique identifier for this punishment
 * @param targetPlayerId     The punished player's UUID (NULL for direct IP bans)
 * @param targetIp           The banned IP address (for IP_BAN type)
 * @param type               The type of punishment
 * @param issuedByPlayerId   Who issued the punishment (NULL = console)
 * @param reason             Optional reason for the punishment
 * @param expiresAt          When the punishment expires (NULL = permanent)
 * @param createdAt          When the punishment was issued
 * @param revokedAt          When the punishment was revoked (NULL = still active)
 * @param revokedByPlayerId  Who revoked the punishment (NULL = console or not revoked)
 */
public record Punishment(
        UUID id,
        @Nullable UUID targetPlayerId,
        @Nullable String targetIp,
        PunishmentType type,
        @Nullable UUID issuedByPlayerId,
        @Nullable String reason,
        @Nullable Instant expiresAt,
        Instant createdAt,
        @Nullable Instant revokedAt,
        @Nullable UUID revokedByPlayerId
) {
    /**
     * Check if this punishment is currently active.
     * A punishment is active if it has not been revoked and has not expired.
     */
    public boolean isActive() {
        if (revokedAt != null) {
            return false;
        }
        if (expiresAt != null && Instant.now().isAfter(expiresAt)) {
            return false;
        }
        return true;
    }

    /**
     * Check if this punishment is permanent (no expiration).
     */
    public boolean isPermanent() {
        return expiresAt == null;
    }

    /**
     * Check if this punishment has expired.
     */
    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }

    /**
     * Check if this punishment has been revoked.
     */
    public boolean isRevoked() {
        return revokedAt != null;
    }
}
