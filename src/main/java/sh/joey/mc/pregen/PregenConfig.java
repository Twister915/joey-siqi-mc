package sh.joey.mc.pregen;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Configuration for the chunk pre-generation system.
 * Per-world pregen sizes are specified in the worlds configuration.
 */
public record PregenConfig(
        boolean enabled,
        PregenRate rate,
        int progressLogIntervalSeconds
) {
    public static PregenConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();

        String rateStr = config.getString("pregen.rate", "FAST").toUpperCase();
        PregenRate rate;
        try {
            rate = PregenRate.valueOf(rateStr);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[Pregen] Invalid rate '" + rateStr + "', defaulting to FAST");
            rate = PregenRate.FAST;
        }

        return new PregenConfig(
                config.getBoolean("pregen.enabled", false),
                rate,
                config.getInt("pregen.progress-log-interval-seconds", 60)
        );
    }
}
