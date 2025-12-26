package sh.joey.mc.resourcepack;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Configuration for resource packs.
 * Reads the 'resource_packs' section from config.yml.
 */
public record ResourcePackConfig(Map<String, ResourcePackEntry> packs) {

    /**
     * A single resource pack entry from config.
     */
    public record ResourcePackEntry(
            String id,
            String name,
            String url,
            String hash,
            String description
    ) {}

    /**
     * Loads resource pack configurations from config.yml.
     *
     * @param plugin the plugin instance
     * @return the loaded configuration
     */
    public static ResourcePackConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();
        Logger logger = plugin.getLogger();

        ConfigurationSection packsSection = config.getConfigurationSection("resource_packs");
        if (packsSection == null) {
            return new ResourcePackConfig(Collections.emptyMap());
        }

        Map<String, ResourcePackEntry> packs = new HashMap<>();

        for (String packId : packsSection.getKeys(false)) {
            ConfigurationSection packSection = packsSection.getConfigurationSection(packId);
            if (packSection == null) {
                continue;
            }

            String name = packSection.getString("name");
            String url = packSection.getString("url");
            String hash = packSection.getString("hash");
            String description = packSection.getString("description", "");

            // Validate required fields
            if (name == null || name.isBlank()) {
                logger.warning("Resource pack '" + packId + "' is missing required 'name' field, skipping");
                continue;
            }
            if (url == null || url.isBlank()) {
                logger.warning("Resource pack '" + packId + "' is missing required 'url' field, skipping");
                continue;
            }
            if (hash == null || hash.isBlank()) {
                logger.warning("Resource pack '" + packId + "' is missing required 'hash' field, skipping");
                continue;
            }

            packs.put(packId.toLowerCase(), new ResourcePackEntry(
                    packId.toLowerCase(),
                    name,
                    url,
                    hash,
                    description
            ));
        }

        return new ResourcePackConfig(Collections.unmodifiableMap(packs));
    }

    /**
     * Gets a resource pack entry by ID.
     *
     * @param id the pack ID
     * @return the pack entry, or null if not found
     */
    public ResourcePackEntry get(String id) {
        return packs.get(id.toLowerCase());
    }
}
