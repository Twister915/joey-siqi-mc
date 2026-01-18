package sh.joey.mc.merit;

import org.bukkit.boss.BarColor;

/**
 * A temporary merit notification to display in the boss bar.
 */
public record MeritNotification(
        String title,
        BarColor color,
        float progress,
        long expiresAt
) {
    /**
     * Check if this notification has expired.
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }
}
