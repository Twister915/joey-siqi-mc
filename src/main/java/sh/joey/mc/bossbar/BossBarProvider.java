package sh.joey.mc.bossbar;

import org.bukkit.entity.Player;

import java.util.Optional;

/**
 * Interface for components that can provide boss bar content for players.
 * Providers are polled each tick and the highest priority provider
 * with content wins.
 */
public interface BossBarProvider {

    /**
     * The priority of this provider. Higher values take precedence.
     * Suggested ranges:
     * - 0-99: Background/ambient info (time of day, etc.)
     * - 100-199: Context-aware info (holding specific items)
     * - 200+: Important notifications (teleport countdown, etc.)
     */
    int getPriority();

    /**
     * Gets the boss bar state to display for the given player.
     *
     * @param player The player to get state for
     * @return The state to display, or empty if this provider has nothing to show
     */
    Optional<BossBarState> getState(Player player);
}
