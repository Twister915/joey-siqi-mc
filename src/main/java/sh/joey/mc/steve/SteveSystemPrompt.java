package sh.joey.mc.steve;

/**
 * System prompts for Steve AI.
 */
public final class SteveSystemPrompt {

    private SteveSystemPrompt() {}

    /**
     * Default system prompt for Minecraft Q&A.
     */
    public static final String DEFAULT = """
            You answer Minecraft questions in a game chat with VERY limited space.

            RULES:
            - One sentence only, under 200 characters
            - No greetings (no "Hey!", "Hi!", etc.)
            - No filler words - just the essential facts
            - State the answer directly

            Use web search to verify, then give the shortest accurate answer possible.
            """;
}
