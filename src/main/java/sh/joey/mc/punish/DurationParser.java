package sh.joey.mc.punish;

import java.time.Duration;
import java.util.Optional;

/**
 * Parses duration strings in the format "2d1h30m15s".
 * <p>
 * Supports:
 * <ul>
 *   <li>d - days</li>
 *   <li>h - hours</li>
 *   <li>m - minutes</li>
 *   <li>s - seconds</li>
 * </ul>
 * <p>
 * Examples: "1d", "2h30m", "1d12h", "30s", "1d2h3m4s"
 */
public final class DurationParser {

    private DurationParser() {
    }

    /**
     * Parse a duration string into a Duration.
     *
     * @param input the duration string (e.g., "2d1h30m15s")
     * @return the parsed Duration, or empty if invalid
     */
    public static Optional<Duration> parse(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        String s = input.toLowerCase().trim();
        Duration duration = Duration.ZERO;
        long currentNumber = 0;
        boolean hasNumber = false;

        for (char c : s.toCharArray()) {
            if (c >= '0' && c <= '9') {
                currentNumber = currentNumber * 10 + (c - '0');
                hasNumber = true;
            } else if (hasNumber) {
                duration = switch (c) {
                    case 'd' -> duration.plusDays(currentNumber);
                    case 'h' -> duration.plusHours(currentNumber);
                    case 'm' -> duration.plusMinutes(currentNumber);
                    case 's' -> duration.plusSeconds(currentNumber);
                    default -> {
                        yield null;  // Invalid character
                    }
                };
                if (duration == null) {
                    return Optional.empty();
                }
                currentNumber = 0;
                hasNumber = false;
            } else {
                return Optional.empty();  // Unit without preceding number
            }
        }

        // Reject trailing numbers without unit, zero, or negative
        if (hasNumber || duration.isZero() || duration.isNegative()) {
            return Optional.empty();
        }

        return Optional.of(duration);
    }

    /**
     * Format a Duration back to the short string format.
     *
     * @param duration the duration to format
     * @return the formatted string (e.g., "2d1h30m15s")
     */
    public static String format(Duration duration) {
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
     * Format a Duration to a human-readable string.
     *
     * @param duration the duration to format
     * @return the formatted string (e.g., "2 days, 1 hour, 30 minutes")
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
}
