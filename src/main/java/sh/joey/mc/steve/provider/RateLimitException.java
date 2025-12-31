package sh.joey.mc.steve.provider;

import java.io.IOException;

/**
 * Exception thrown when an API rate limit is exceeded.
 * The message is suitable for displaying directly to the user.
 * Extends IOException so it can be thrown from API methods.
 */
public class RateLimitException extends IOException {
    public RateLimitException(String message) {
        super(message);
    }
}
