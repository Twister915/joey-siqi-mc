package sh.joey.mc.adminmode;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a player's active admin mode session.
 *
 * @param playerId   The player in admin mode
 * @param worldId    The world they entered admin mode in
 * @param snapshotId Reference to their saved inventory snapshot
 * @param enteredAt  When they entered admin mode
 */
public record AdminModeState(
        UUID playerId,
        UUID worldId,
        UUID snapshotId,
        Instant enteredAt
) {}
