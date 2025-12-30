package sh.joey.mc.steve;

/**
 * Factory interface for creating Steve AI models.
 * <p>
 * Each provider implementation encapsulates its own configuration
 * (API keys, model settings, etc.) and can create model instances
 * with a given system prompt.
 */
public interface SteveModelProvider {

    /**
     * Returns the unique identifier for this provider.
     * Used for config lookup (e.g., "anthropic", "openai").
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
     * Creates a new model instance with the given system prompt.
     *
     * @param systemPrompt the system prompt to use
     * @return a new model instance
     */
    SteveModel create(String systemPrompt);
}
