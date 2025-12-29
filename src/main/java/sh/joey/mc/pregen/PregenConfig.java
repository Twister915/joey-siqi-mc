package sh.joey.mc.pregen;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Configuration for the chunk pre-generation system.
 */
public record PregenConfig(
        boolean enabled,
        List<String> worlds,
        int sideLength,
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
                config.getStringList("pregen.worlds"),
                config.getInt("pregen.side-length", 25000),
                rate,
                config.getInt("pregen.progress-log-interval-seconds", 60)
        );
    }

    /**
     * Convert side length in blocks to chunks.
     */
    public int sideChunks() {
        return (sideLength + 15) / 16;  // Round up
    }

    /**
     * Total chunks in the area (per world).
     */
    public long totalChunks() {
        long side = sideChunks();
        return side * side;
    }
}
