package sh.joey.mc.home;

import org.bukkit.permissions.Permissible;

import java.util.OptionalInt;

/**
 * Resolves the effective home limit for a player based on their permissions.
 *
 * Resolution algorithm:
 * 1. If no limits configured → unlimited
 * 2. Check each smp.home.{class} permission
 * 3. Track highest numeric limit found
 * 4. If any unlimited permission matches → return unlimited
 * 5. If no permissions match → unlimited (backwards compat)
 * 6. Otherwise return highest numeric limit
 */
public final class HomeLimitResolver {

    private static final String PERMISSION_PREFIX = "smp.home.";

    private HomeLimitResolver() {}

    /**
     * Resolves the effective home limit for a permissible entity (typically a player).
     *
     * @param permissible the permissible to check
     * @param config the home limit configuration
     * @return the effective limit, or empty for unlimited
     */
    public static OptionalInt resolve(Permissible permissible, HomeLimitConfig config) {
        if (!config.hasLimits()) {
            return OptionalInt.empty(); // No limits configured = unlimited
        }

        int highestLimit = -1;
        boolean hasAnyPermission = false;

        for (var entry : config.limits().entrySet()) {
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
     * Checks if a permissible can create a new home given their current count.
     *
     * @param permissible the permissible to check (typically a player)
     * @param config the home limit configuration
     * @param currentCount the current number of owned homes
     * @return true if a new home can be created
     */
    public static boolean canCreateHome(Permissible permissible, HomeLimitConfig config, int currentCount) {
        OptionalInt limit = resolve(permissible, config);
        return limit.isEmpty() || currentCount < limit.getAsInt();
    }
}
