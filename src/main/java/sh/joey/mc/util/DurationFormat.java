package sh.joey.mc.util;

import java.time.Duration;

/**
 * Centralized utility for formatting durations consistently across the plugin.
 * <p>
 * Format styles:
 * <ul>
 *   <li>{@link #formatShort} - "2d 5h 30m 15s" (with spaces)</li>
 *   <li>{@link #formatCompact} - "2d5h30m15s" (no spaces)</li>
 *   <li>{@link #formatHumanReadable} - "2 days, 5 hours, 30 minutes"</li>
 * </ul>
 */
public final class DurationFormat {

    private static final long SECONDS_PER_MINUTE = 60;
    private static final long SECONDS_PER_HOUR = 3600;
    private static final long SECONDS_PER_DAY = 86400;

    private DurationFormat() {
    }

    /**
     * Format a duration with space-separated short units.
     * <p>
     * Examples: "2d 5h 30m 15s", "5h 30m 15s", "30m 15s", "15s"
     *
     * @param duration the duration to format
     * @return formatted string
     */
    public static String formatShort(Duration duration) {
        return formatShort(duration.toSeconds());
    }

    /**
     * Format seconds with space-separated short units.
     *
     * @param totalSeconds total seconds
     * @return formatted string
     */
    public static String formatShort(long totalSeconds) {
        if (totalSeconds < 0) {
            return "0s";
        }

        long days = totalSeconds / SECONDS_PER_DAY;
        long hours = (totalSeconds % SECONDS_PER_DAY) / SECONDS_PER_HOUR;
        long minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE;
        long seconds = totalSeconds % SECONDS_PER_MINUTE;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    /**
     * Format milliseconds with space-separated short units.
     *
     * @param millis total milliseconds
     * @return formatted string
     */
    public static String formatShortMillis(long millis) {
        return formatShort(millis / 1000);
    }

    /**
     * Format a duration with compact short units (no spaces).
     * <p>
     * Examples: "2d5h30m15s", "5h30m", "30m", "15s"
     * <p>
     * Only includes non-zero components.
     *
     * @param duration the duration to format
     * @return formatted string
     */
    public static String formatCompact(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return "0s";
        }

        StringBuilder sb = new StringBuilder();

        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (days > 0) sb.append(days).append("d");
        if (hours > 0) sb.append(hours).append("h");
        if (minutes > 0) sb.append(minutes).append("m");
        if (seconds > 0) sb.append(seconds).append("s");

        return sb.isEmpty() ? "0s" : sb.toString();
    }

    /**
     * Format a duration in human-readable form with full unit names.
     * <p>
     * Examples: "2 days, 5 hours, 30 minutes", "5 hours, 30 minutes, 15 seconds"
     *
     * @param duration the duration to format
     * @return formatted string
     */
    public static String formatHumanReadable(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return "now";
        }

        StringBuilder sb = new StringBuilder();

        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (days > 0) {
            sb.append(days).append(days == 1 ? " day" : " days");
        }
        if (hours > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(hours).append(hours == 1 ? " hour" : " hours");
        }
        if (minutes > 0) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(minutes).append(minutes == 1 ? " minute" : " minutes");
        }
        if (seconds > 0 || sb.isEmpty()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(seconds).append(seconds == 1 ? " second" : " seconds");
        }

        return sb.toString();
    }

    /**
     * Format seconds in human-readable form with full unit names.
     *
     * @param totalSeconds total seconds
     * @return formatted string
     */
    public static String formatHumanReadable(long totalSeconds) {
        return formatHumanReadable(Duration.ofSeconds(totalSeconds));
    }
}
