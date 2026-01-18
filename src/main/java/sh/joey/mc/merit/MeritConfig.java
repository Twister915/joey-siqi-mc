package sh.joey.mc.merit;

import org.bukkit.plugin.Plugin;

/**
 * Configuration for the merit system.
 */
public record MeritConfig(
        boolean enabled,
        int levelBaseXp,
        double levelExponent,
        int onlineTimeReward,
        int onlineTimeIntervalMinutes,
        int onlineTimeWeeklyCap,
        int flushIntervalSeconds,
        int weeklyChallengeCount
) {
    public static MeritConfig load(Plugin plugin) {
        var config = plugin.getConfig();
        return new MeritConfig(
                config.getBoolean("merit.enabled", true),
                config.getInt("merit.level-base-xp", 100),
                config.getDouble("merit.level-exponent", 1.8),
                config.getInt("merit.online-time-reward", 10),
                config.getInt("merit.online-time-interval-minutes", 30),
                config.getInt("merit.online-time-weekly-cap", 500),
                config.getInt("merit.flush-interval-seconds", 30),
                config.getInt("merit.weekly-challenge-count", 8)
        );
    }
}
