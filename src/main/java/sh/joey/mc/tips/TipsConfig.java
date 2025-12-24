package sh.joey.mc.tips;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Configuration for the tips system.
 */
public record TipsConfig(boolean enabled) {
    public static TipsConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new TipsConfig(config.getBoolean("tips.enabled", true));
    }
}
