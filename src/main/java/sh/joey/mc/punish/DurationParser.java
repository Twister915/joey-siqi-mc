package sh.joey.mc.punish;

import sh.joey.mc.util.DurationFormat;

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
        return DurationFormat.formatCompact(duration);
    }

    /**
     * Format a Duration to a human-readable string.
     *
     * @param duration the duration to format
     * @return the formatted string (e.g., "2 days, 1 hour, 30 minutes")
     */
    public static String formatHumanReadable(Duration duration) {
        return DurationFormat.formatHumanReadable(duration);
    }
}
