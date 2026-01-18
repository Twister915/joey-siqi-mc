package sh.joey.mc.home;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Configuration for home limits per permission class.
 * Each entry in the config creates a permission smp.home.{name} that grants
 * the specified number of homes.
 */
public record HomeLimitConfig(Map<String, OptionalInt> limits) {

    /**
     * Load home limit configuration from the plugin's config.yml.
     * Parses the homes.limits section where each entry is either a number
     * or "unlimited".
     */
    public static HomeLimitConfig load(JavaPlugin plugin) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("homes.limits");
        if (section == null) {
            return new HomeLimitConfig(Map.of());
        }

        Map<String, OptionalInt> limits = new HashMap<>();
        for (String key : section.getKeys(false)) {
            String value = section.getString(key);
            if (value == null) {
                continue;
            }

            value = value.trim().toLowerCase();
            if (value.equals("unlimited")) {
                limits.put(key.toLowerCase(), OptionalInt.empty());
            } else {
                try {
                    int limit = Integer.parseInt(value);
                    limits.put(key.toLowerCase(), OptionalInt.of(limit));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Invalid home limit for '" + key + "': " + value);
                }
            }
        }

        return new HomeLimitConfig(Map.copyOf(limits));
    }

    /**
     * Returns true if any home limits are configured.
     */
    public boolean hasLimits() {
        return !limits.isEmpty();
    }
}
