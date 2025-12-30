package sh.joey.mc.steve;

/**
 * Metadata about a Steve AI model.
 */
public record SteveModelInfo(
        String providerName,  // e.g., "Anthropic"
        String modelId,       // e.g., "claude-sonnet-4-20250514"
        String displayName    // e.g., "Claude Sonnet 4"
) {}
