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
}
