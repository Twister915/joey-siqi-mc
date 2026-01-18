package sh.joey.mc.branding;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Configuration for server branding.
 * Allows customization of server name, IP/domain, and tagline.
 */
public record BrandingConfig(
        String serverName,
        String serverIp,
        String tagline
) {
    public static BrandingConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new BrandingConfig(
                config.getString("branding.server-name", "Minecraft Server"),
                config.getString("branding.server-ip", "play.example.com"),
                config.getString("branding.tagline", "Welcome to the server!")
        );
    }
}
