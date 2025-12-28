package sh.joey.mc.whitelist;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Configuration for the whitelist system.
 */
public record WhitelistConfig(boolean enabled) {
    public static WhitelistConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new WhitelistConfig(config.getBoolean("whitelist.enabled", true));
    }
}
