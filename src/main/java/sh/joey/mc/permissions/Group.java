package sh.joey.mc.permissions;

import java.time.Instant;
import java.util.List;

/**
 * A permission group with its attributes and grants.
 *
 * @param canonicalName Lowercase normalized name (primary key)
 * @param displayName   Case-preserved display name
 * @param priority      Higher = more important in resolution order
 * @param isDefault     If true, all players are implicitly members
 * @param attributes    Prefix/suffix display attributes
 * @param grants        Permission grants for this group
 * @param createdAt     When the group was created
 * @param updatedAt     When the group was last modified
 */
public record Group(
        String canonicalName,
        String displayName,
        int priority,
        boolean isDefault,
        PermissibleAttributes attributes,
        List<PermissionGrant> grants,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Normalize a group name to canonical form (lowercase, trimmed).
     */
    public static String normalize(String name) {
        return name.toLowerCase().trim();
    }

    /**
     * Creates a Group with empty grants (for when grants are loaded separately).
     */
    public static Group withoutGrants(
            String canonicalName,
            String displayName,
            int priority,
            boolean isDefault,
            PermissibleAttributes attributes,
            Instant createdAt,
            Instant updatedAt
    ) {
        return new Group(canonicalName, displayName, priority, isDefault, attributes, List.of(), createdAt, updatedAt);
    }

    /**
     * Returns a new Group with the specified grants.
     */
    public Group withGrants(List<PermissionGrant> grants) {
        return new Group(canonicalName, displayName, priority, isDefault, attributes, grants, createdAt, updatedAt);
    }

    /**
     * Returns a new Group with the specified priority.
     */
    public Group withPriority(int priority) {
        return new Group(canonicalName, displayName, priority, isDefault, attributes, grants, createdAt, updatedAt);
    }

    /**
     * Returns a new Group with the specified default flag.
     */
    public Group withDefault(boolean isDefault) {
        return new Group(canonicalName, displayName, priority, isDefault, attributes, grants, createdAt, updatedAt);
    }

    /**
     * Returns a new Group with the specified attributes.
     */
    public Group withAttributes(PermissibleAttributes attributes) {
        return new Group(canonicalName, displayName, priority, isDefault, attributes, grants, createdAt, updatedAt);
    }
}
