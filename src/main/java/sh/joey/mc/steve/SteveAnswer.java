package sh.joey.mc.steve;

import java.util.List;

/**
 * Answer from a Steve AI model.
 */
public record SteveAnswer(
        String text,
        List<Citation> citations,
        double costCents
) {
    /**
     * A citation to a source URL.
     */
    public record Citation(String title, String url) {}
}
