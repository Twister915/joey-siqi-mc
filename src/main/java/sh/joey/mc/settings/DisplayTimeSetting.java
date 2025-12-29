package sh.joey.mc.settings;

/**
 * Controls when the time is displayed in the boss bar.
 */
public enum DisplayTimeSetting {
    ALWAYS,         // Current behavior - always show time in overworld
    HOLDING_CLOCK,  // Show only when holding clock item in main or off hand
    NEVER;          // Never show time

    /**
     * Parse from database string, defaulting to ALWAYS.
     */
    public static DisplayTimeSetting fromString(String value) {
        if (value == null) return ALWAYS;
        return switch (value.toUpperCase()) {
            case "HOLDING_CLOCK" -> HOLDING_CLOCK;
            case "NEVER" -> NEVER;
            default -> ALWAYS;
        };
    }

    /**
     * Get display name for UI.
     */
    public String displayName() {
        return switch (this) {
            case ALWAYS -> "Always";
            case HOLDING_CLOCK -> "Clock";
            case NEVER -> "Never";
        };
    }
}
