package sh.joey.mc.rtp;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Configuration for the Random Teleport system.
 */
public record RtpConfig(
        int cooldownMinutes,
        int searchRadius,
        int minDistance,
        int candidateCount,
        int candidateTimeoutSeconds,
        int chunkTimeoutSeconds
) {
    public static RtpConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new RtpConfig(
                config.getInt("rtp.cooldown-minutes", 5),
                config.getInt("rtp.search-radius", 25000),
                config.getInt("rtp.min-distance", 500),
                config.getInt("rtp.candidate-count", 5),
                config.getInt("rtp.candidate-timeout-seconds", 120),
                config.getInt("rtp.chunk-timeout-seconds", 3)
        );
    }
}
