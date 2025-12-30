package sh.joey.mc.steve;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry for Steve AI model providers.
 * <p>
 * Providers are registered by their unique ID and can be looked up
 * for creating model instances based on configuration.
 */
public final class SteveModelRegistry {

    private final Map<String, SteveModelProvider> providers = new HashMap<>();

    /**
     * Registers a model provider.
     *
     * @param provider the provider to register
     */
    public void register(SteveModelProvider provider) {
        providers.put(provider.id(), provider);
    }

    /**
     * Gets a provider by its ID.
     *
     * @param id the provider ID
     * @return the provider, or empty if not found
     */
    public Optional<SteveModelProvider> get(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    /**
     * Returns all registered providers.
     *
     * @return collection of all providers
     */
    public Collection<SteveModelProvider> all() {
        return providers.values();
    }
}
