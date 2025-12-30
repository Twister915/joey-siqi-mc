package sh.joey.mc.steve;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Configuration for the Steve AI chatbot.
 */
public record SteveConfig(
        boolean enabled,
        String model,
        int cooldownSeconds,
        // Anthropic provider config
        String anthropicApiKey,
        int anthropicMaxSearches,
        // LM Studio provider config
        String lmstudioEndpoint,
        String lmstudioModel
) {
    public static SteveConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new SteveConfig(
                config.getBoolean("steve.enabled", true),
                config.getString("steve.model", "anthropic"),
                config.getInt("steve.cooldown-seconds", 30),
                config.getString("steve.anthropic.api-key", ""),
                config.getInt("steve.anthropic.max-searches", 3),
                config.getString("steve.lmstudio.endpoint", "http://localhost:1234"),
                config.getString("steve.lmstudio.model", "")
        );
    }
}
