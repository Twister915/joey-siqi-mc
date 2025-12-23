package sh.joey.mc.permissions;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A single permission grant (allow or deny).
 *
 * @param id         Unique identifier for this grant
 * @param permission The parsed permission string
 * @param worldId    World UUID for world-specific grants, or null for global
 * @param state      true = ALLOW, false = DENY
 * @param priority   Priority for conflict resolution (higher = evaluated first)
 */
public record PermissionGrant(
        UUID id,
        ParsedPermission permission,
        @Nullable UUID worldId,
        boolean state,
        int priority
) {

    /**
     * Creates a grant from database fields.
     *
     * @throws IllegalArgumentException if the permission string is invalid
     */
    public static PermissionGrant of(UUID id, String permissionStr, @Nullable UUID worldId, boolean state, int priority) {
        ParsedPermission parsed = ParsedPermission.parse(permissionStr)
                .orElseThrow(() -> new IllegalArgumentException("Invalid permission: " + permissionStr));
        return new PermissionGrant(id, parsed, worldId, state, priority);
    }

    /**
     * Returns true if this grant applies to the given world.
     * Global grants (worldId == null) apply to all worlds.
     */
    public boolean appliesTo(@Nullable UUID targetWorldId) {
        return worldId == null || worldId.equals(targetWorldId);
    }

    /**
     * Returns the permission as a string.
     */
    public String permissionString() {
        return permission.asString();
    }

    /**
     * Returns true if this is a global grant (applies to all worlds).
     */
    public boolean isGlobal() {
        return worldId == null;
    }
}
