package sh.joey.mc.utility;

import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Configuration for the web map feature.
 */
public record MapConfig(String url, int viewHeight) {
    public static MapConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new MapConfig(
                config.getString("map.url", "http://localhost:8090"),
                config.getInt("map.view-height", 700)
        );
    }

    /**
     * Builds a BlueMap URL centered on the given location.
     * Format: baseUrl/#world:x:0:z:y:0:0:0:0:perspective
     */
    public String buildUrl(Location location) {
        String worldName = location.getWorld().getName();
        int x = location.getBlockX();
        int z = location.getBlockZ();
        return String.format("%s/#%s:%d:0:%d:%d:0:0:0:0:perspective",
                url, worldName, x, z, viewHeight);
    }
}
