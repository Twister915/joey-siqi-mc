package sh.joey.mc.permissions;

import java.util.Map;
import java.util.UUID;

/**
 * The fully resolved permissions for a player in a specific world.
 * This is the result of the resolution algorithm.
 *
 * @param playerId   The player's UUID
 * @param worldId    The world these permissions apply to
 * @param attributes Resolved display attributes (merged from player + groups)
 * @param permissions Map of permission string to state (true = allow, false = deny)
 */
public record ResolvedPermissions(
        UUID playerId,
        UUID worldId,
        PermissibleAttributes attributes,
        Map<String, Boolean> permissions
) {

    /**
     * Check if a specific permission is granted.
     * Uses wildcard matching against all resolved permissions.
     *
     * @param permission the permission to check (e.g., "worldedit.wand")
     * @return true if the permission is granted, false otherwise
     */
    public boolean hasPermission(String permission) {
        // Check exact match first (most common case)
        String lowerPerm = permission.toLowerCase();
        Boolean exact = permissions.get(lowerPerm);
        if (exact != null) {
            return exact;
        }

        // Parse the target permission
        ParsedPermission target = ParsedPermission.parse(permission).orElse(null);
        if (target == null) {
            return false;
        }

        // Find the most specific matching grant
        int bestSpecificity = -1;
        Boolean bestState = null;

        for (var entry : permissions.entrySet()) {
            ParsedPermission grant = ParsedPermission.parse(entry.getKey()).orElse(null);
            if (grant != null && grant.matches(target)) {
                int specificity = grant.specificity();
                if (specificity > bestSpecificity) {
                    bestSpecificity = specificity;
                    bestState = entry.getValue();
                }
            }
        }

        return bestState != null && bestState;
    }

    /**
     * Returns the number of permissions in this resolved set.
     */
    public int permissionCount() {
        return permissions.size();
    }

    /**
     * Returns true if the player has any permissions (including denies).
     */
    public boolean hasAnyPermissions() {
        return !permissions.isEmpty();
    }
}
