package sh.joey.mc.protection;

import org.bukkit.permissions.Permissible;

import java.util.OptionalInt;

/**
 * Resolves the effective region limit for a player based on their permissions.
 *
 * Resolution algorithm:
 * 1. If no limits configured → unlimited
 * 2. Check each smp.protection.limit.{class} permission
 * 3. Track highest numeric limit found
 * 4. If any unlimited permission matches → return unlimited
 * 5. If no permissions match → unlimited (backwards compat)
 * 6. Otherwise return highest numeric limit
 */
public final class RegionLimitResolver {

    private static final String PERMISSION_PREFIX = "smp.protection.limit.";

    private RegionLimitResolver() {}

    /**
     * Resolves the effective region limit for a permissible entity (typically a player).
     *
     * @param permissible the permissible to check
     * @param config the protection configuration
     * @return the effective limit, or empty for unlimited
     */
    public static OptionalInt resolve(Permissible permissible, ProtectionConfig config) {
        if (!config.hasRegionLimits()) {
            return OptionalInt.empty(); // No limits configured = unlimited
        }

        int highestLimit = -1;
        boolean hasAnyPermission = false;

        for (var entry : config.regionLimits().entrySet()) {
            String permission = PERMISSION_PREFIX + entry.getKey();
            if (permissible.hasPermission(permission)) {
                hasAnyPermission = true;
                OptionalInt limit = entry.getValue();

                if (limit.isEmpty()) {
                    // Unlimited permission found
                    return OptionalInt.empty();
                }

                highestLimit = Math.max(highestLimit, limit.getAsInt());
            }
        }

        if (!hasAnyPermission) {
            // No matching permissions = unlimited (backwards compat)
            return OptionalInt.empty();
        }

        return OptionalInt.of(highestLimit);
    }

    /**
     * Checks if a permissible can create a new region given their current count.
     *
     * @param permissible the permissible to check (typically a player)
     * @param config the protection configuration
     * @param currentCount the current number of owned regions
     * @return true if a new region can be created
     */
    public static boolean canCreateRegion(Permissible permissible, ProtectionConfig config, int currentCount) {
        OptionalInt limit = resolve(permissible, config);
        return limit.isEmpty() || currentCount < limit.getAsInt();
    }
}
