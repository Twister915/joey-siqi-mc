package sh.joey.mc.teleport;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Typed configuration for the teleport system.
 */
public record PluginConfig(
        int teleportWarmupSeconds,
        double movementToleranceBlocks,
        int requestTimeoutSeconds
) {
    public static PluginConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        return new PluginConfig(
                config.getInt("teleport.warmup-seconds", 3),
                config.getDouble("teleport.movement-tolerance-blocks", 0.5),
                config.getInt("requests.timeout-seconds", 60)
        );
    }
}
