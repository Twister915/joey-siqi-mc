package sh.joey.mc.bossbar;

import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;

/**
 * Immutable state for a boss bar display.
 */
public record BossBarState(
        String title,
        BarColor color,
        float progress,
        BarStyle style
) {
    /**
     * Returns a new BossBarState with the specified progress, keeping other fields unchanged.
     * Used for efficient caching when only progress changes between ticks.
     */
    public BossBarState withProgress(float newProgress) {
        return new BossBarState(title, color, newProgress, style);
    }
}
