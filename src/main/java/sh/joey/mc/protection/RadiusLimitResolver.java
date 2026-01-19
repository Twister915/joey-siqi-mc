package sh.joey.mc.protection;

import org.bukkit.permissions.Permissible;

/**
 * Resolves the maximum radius a player can set for their regions
 * based on their permissions.
 *
 * Resolution algorithm:
 * 1. If no limits configured → use default radius as max
 * 2. Check each smp.protection.radius.{class} permission
 * 3. Return highest matching value
 * 4. If no permissions match → use first configured value as default
 */
public final class RadiusLimitResolver {

    private static final String PERMISSION_PREFIX = "smp.protection.radius.";

    private RadiusLimitResolver() {}

    /**
     * Resolves the maximum radius for a permissible entity.
     *
     * @param permissible the permissible to check
     * @param config the protection configuration
     * @return the maximum radius allowed
     */
    public static int resolve(Permissible permissible, ProtectionConfig config) {
        if (!config.hasRadiusLimits()) {
            return config.defaultRadius(); // No limits configured = use default
        }

        int highestRadius = -1;
        boolean hasAnyPermission = false;

        for (var entry : config.radiusLimits().entrySet()) {
            String permission = PERMISSION_PREFIX + entry.getKey();
            if (permissible.hasPermission(permission)) {
                hasAnyPermission = true;
                int radius = entry.getValue().orElse(config.defaultRadius());
                highestRadius = Math.max(highestRadius, radius);
            }
        }

        if (!hasAnyPermission) {
            // No matching permissions = use first configured value or default
            return config.radiusLimits().values().stream()
                    .findFirst()
                    .map(opt -> opt.orElse(config.defaultRadius()))
                    .orElse(config.defaultRadius());
        }

        return highestRadius;
    }

    /**
     * Checks if a radius value is valid for a player.
     *
     * @param permissible the permissible to check
     * @param config the protection configuration
     * @param radius the radius to check
     * @return true if the radius is within bounds
     */
    public static boolean isValidRadius(Permissible permissible, ProtectionConfig config, int radius) {
        int maxRadius = resolve(permissible, config);
        return radius >= config.minRadius() && radius <= maxRadius;
    }
}
