package sh.joey.mc.protection;

/**
 * Access levels for region permissions.
 * Determines who can perform certain actions within a region.
 */
public enum AccessLevel {
    /**
     * Only the region owner.
     */
    OWNER,

    /**
     * Owner and trusted members.
     */
    MEMBERS,

    /**
     * Anyone (no protection for this action).
     */
    EVERYBODY;

    /**
     * Parse an access level from a string, case-insensitive.
     * Returns MEMBERS as the default if the string is not recognized.
     */
    public static AccessLevel fromString(String value) {
        if (value == null) {
            return MEMBERS;
        }
        return switch (value.toUpperCase()) {
            case "OWNER" -> OWNER;
            case "EVERYBODY" -> EVERYBODY;
            default -> MEMBERS;
        };
    }
}
