package sh.joey.mc.whitelist;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a whitelisted player.
 *
 * @param playerId the whitelisted player's UUID
 * @param playerName cached username for display
 * @param invitedBy UUID of the player who invited them, or null if added by admin/console
 * @param createdAt when the player was whitelisted
 */
public record WhitelistEntry(
        UUID playerId,
        String playerName,
        @Nullable UUID invitedBy,
        Instant createdAt
) {}
