package sh.joey.mc.protection;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Configuration for the protection system.
 */
public record ProtectionConfig(
        Map<String, OptionalInt> regionLimits,
        Map<String, OptionalInt> radiusLimits,
        int defaultRadius,
        int minRadius
) {
    public static ProtectionConfig load(JavaPlugin plugin) {
        var config = plugin.getConfig();

        // Load region limits (how many regions a player can have)
        Map<String, OptionalInt> regionLimits = new HashMap<>();
        ConfigurationSection limitsSection = config.getConfigurationSection("protection.limits");
        if (limitsSection != null) {
            for (String key : limitsSection.getKeys(false)) {
                String value = limitsSection.getString(key);
                if (value == null) continue;

                value = value.trim().toLowerCase();
                if (value.equals("unlimited")) {
                    regionLimits.put(key.toLowerCase(), OptionalInt.empty());
                } else {
                    try {
                        int limit = Integer.parseInt(value);
                        regionLimits.put(key.toLowerCase(), OptionalInt.of(limit));
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Invalid protection limit for '" + key + "': " + value);
                    }
                }
            }
        }

        // Load max radius limits
        Map<String, OptionalInt> radiusLimits = new HashMap<>();
        ConfigurationSection radiusSection = config.getConfigurationSection("protection.max-radius");
        if (radiusSection != null) {
            for (String key : radiusSection.getKeys(false)) {
                int radius = radiusSection.getInt(key);
                radiusLimits.put(key.toLowerCase(), OptionalInt.of(radius));
            }
        }

        int defaultRadius = config.getInt("protection.default-radius", 16);
        int minRadius = config.getInt("protection.min-radius", 8);

        return new ProtectionConfig(
                Map.copyOf(regionLimits),
                Map.copyOf(radiusLimits),
                defaultRadius,
                minRadius
        );
    }

    /**
     * Returns true if any region limits are configured.
     */
    public boolean hasRegionLimits() {
        return !regionLimits.isEmpty();
    }

    /**
     * Returns true if any radius limits are configured.
     */
    public boolean hasRadiusLimits() {
        return !radiusLimits.isEmpty();
    }
}
