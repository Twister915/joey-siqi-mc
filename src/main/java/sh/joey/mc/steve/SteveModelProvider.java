package sh.joey.mc.steve;

/**
 * Factory interface for creating Steve AI models.
 * <p>
 * Each provider implementation encapsulates its own configuration
 * (API keys, model settings, system prompts, etc.) and creates
 * model instances configured for that provider's needs.
 * <p>
 * Providers manage their own system prompts internally:
 * <ul>
 *   <li>Anthropic uses a cached system prompt with Minecraft knowledge</li>
 *   <li>Local models use a simple instruction prompt</li>
 * </ul>
 */
public interface SteveModelProvider {

    /**
     * Returns the unique identifier for this provider.
     * Used for config lookup (e.g., "anthropic", "lmstudio").
     *
     * @return provider identifier
     */
    String id();

    /**
     * Returns metadata about this provider's model.
     *
     * @return model metadata
     */
    SteveModelInfo info();

    /**
     * Creates a new model instance configured for this provider.
     * Each provider uses its own optimized system prompt internally.
     *
     * @return a new model instance
     */
    SteveModel create();
}
