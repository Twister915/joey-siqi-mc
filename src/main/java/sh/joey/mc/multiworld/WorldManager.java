package sh.joey.mc.multiworld;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Manages custom worlds: loading, creation, and inventory group mapping.
 * Worlds are loaded eagerly on plugin enable.
 */
public final class WorldManager {

    private static final String DEFAULT_INVENTORY_GROUP = "default";

    private final JavaPlugin plugin;
    private final WorldsConfig config;
    private final Logger logger;
    private final Map<String, World> loadedWorlds = new HashMap<>();

    public WorldManager(JavaPlugin plugin, WorldsConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();
    }

    /**
     * Loads or creates all configured worlds.
     * This should be called during plugin enable, after migrations have run.
     */
    public void loadWorlds() {
        for (var entry : config.worlds().entrySet()) {
            String name = entry.getKey();
            WorldConfig worldConfig = entry.getValue();

            World existing = Bukkit.getWorld(name);
            if (existing != null) {
                loadedWorlds.put(name, existing);
                applyWorldSettings(existing, worldConfig);
                logger.info("Found existing world: " + name);
            } else {
                World created = createWorld(name, worldConfig);
                if (created != null) {
                    loadedWorlds.put(name, created);
                    applyWorldSettings(created, worldConfig);
                    logger.info("Created new world: " + name);
                } else {
                    logger.warning("Failed to create world: " + name);
                }
            }
        }

        logger.info("Loaded " + loadedWorlds.size() + " custom world(s)");
    }

    private World createWorld(String name, WorldConfig config) {
        WorldCreator creator = new WorldCreator(name)
                .environment(config.dimension().environment())
                .generateStructures(config.structures());

        config.seed().ifPresent(creator::seed);

        if (config.superflat()) {
            creator.type(WorldType.FLAT);
            config.generatorSettings().ifPresent(creator::generatorSettings);
        }

        return creator.createWorld();
    }

    private void applyWorldSettings(World world, WorldConfig config) {
        // Apply difficulty
        config.difficulty().ifPresent(world::setDifficulty);

        // Apply fixed time
        config.time().ifPresent(time -> world.setFullTime(time));

        // Apply fixed weather
        config.weather().ifPresent(weather -> {
            switch (weather) {
                case CLEAR -> {
                    world.setStorm(false);
                    world.setThundering(false);
                }
                case RAIN -> {
                    world.setStorm(true);
                    world.setThundering(false);
                }
                case THUNDER -> {
                    world.setStorm(true);
                    world.setThundering(true);
                }
            }
        });

        // Apply game rules
        for (var entry : config.gameRules().entrySet()) {
            String ruleName = entry.getKey();
            String value = entry.getValue();

            NamespacedKey key = NamespacedKey.minecraft(ruleName);

            @SuppressWarnings("unchecked")
            GameRule<Object> rule = (GameRule<Object>) Registry.GAME_RULE.get(key);
            if (rule == null) {
                logger.warning("Unknown game rule '" + ruleName + "' for world " + world.getName());
                continue;
            }

            Object parsedValue = parseGameRuleValue(rule, value);
            if (parsedValue != null) {
                world.setGameRule(rule, parsedValue);
            } else {
                logger.warning("Invalid value '" + value + "' for game rule " + ruleName);
            }
        }
    }

    private Object parseGameRuleValue(GameRule<?> rule, String value) {
        Class<?> type = rule.getType();
        if (type == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if (type == Integer.class) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Gets a configured world by name.
     *
     * @param name the world name (case-insensitive)
     * @return the world, or empty if not found or not loaded
     */
    public Optional<World> getWorld(String name) {
        return Optional.ofNullable(loadedWorlds.get(name.toLowerCase()));
    }

    /**
     * Gets the configuration for a world by name.
     *
     * @param worldName the world name (case-insensitive)
     * @return the configuration, or empty if not configured
     */
    public Optional<WorldConfig> getConfig(String worldName) {
        return Optional.ofNullable(config.worlds().get(worldName.toLowerCase()));
    }

    /**
     * Gets the configuration for a world.
     *
     * @param world the world
     * @return the configuration, or empty if not configured
     */
    public Optional<WorldConfig> getConfig(World world) {
        return getConfig(world.getName());
    }

    /**
     * Gets the inventory group for a world.
     * Returns "default" for unconfigured worlds.
     *
     * @param world the world
     * @return the inventory group name
     */
    public String getInventoryGroup(World world) {
        return getConfig(world)
                .map(WorldConfig::inventoryGroup)
                .orElse(DEFAULT_INVENTORY_GROUP);
    }

    /**
     * Gets all configured world names that are not hidden.
     *
     * @return an unmodifiable set of world names
     */
    public Set<String> getWorldNames() {
        return loadedWorlds.keySet().stream()
                .filter(name -> !isHidden(name))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Checks if a world is hidden from the /world command.
     *
     * @param worldName the world name
     * @return true if hidden
     */
    public boolean isHidden(String worldName) {
        return getConfig(worldName).map(WorldConfig::hidden).orElse(false);
    }

    /**
     * Checks if a world is managed by this manager.
     *
     * @param world the world to check
     * @return true if the world is in our configuration
     */
    public boolean isManaged(World world) {
        return config.worlds().containsKey(world.getName().toLowerCase());
    }
}
