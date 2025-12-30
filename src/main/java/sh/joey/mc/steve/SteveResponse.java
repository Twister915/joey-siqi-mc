package sh.joey.mc.steve;

import java.util.List;

/**
 * Parsed response from Claude API for Steve.
 */
public record SteveResponse(
        String text,
        List<Citation> citations,
        double costCents
) {
    /**
     * A citation to a source URL.
     */
    public record Citation(String title, String url) {}
}
