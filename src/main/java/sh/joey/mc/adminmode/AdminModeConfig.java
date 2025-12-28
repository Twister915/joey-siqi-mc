package sh.joey.mc.adminmode;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * Configuration for admin mode permissions.
 */
public record AdminModeConfig(List<String> permissions) {

    /**
     * Load admin mode config from plugin configuration.
     */
    public static AdminModeConfig load(JavaPlugin plugin) {
        var permissions = plugin.getConfig().getStringList("adminmode.permissions");
        return new AdminModeConfig(permissions);
    }
}
