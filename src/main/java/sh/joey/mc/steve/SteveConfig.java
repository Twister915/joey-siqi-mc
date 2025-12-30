package sh.joey.mc.steve;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Configuration for the Steve AI chatbot.
 */
public record SteveConfig(
        boolean enabled,
        String apiKey,
        int cooldownSeconds,
        int maxSearches
) {
    public static SteveConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new SteveConfig(
                config.getBoolean("steve.enabled", true),
                config.getString("steve.api-key", ""),
                config.getInt("steve.cooldown-seconds", 30),
                config.getInt("steve.max-searches", 3)
        );
    }
}
