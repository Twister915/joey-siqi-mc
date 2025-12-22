package sh.joey.mc.multiworld;

import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Configuration loader for custom worlds.
 * Reads the 'worlds' section from config.yml.
 */
public record WorldsConfig(Map<String, WorldConfig> worlds) {

    /**
     * Loads world configurations from config.yml.
     *
     * @param plugin the plugin instance
     * @return the loaded configuration
     */
    public static WorldsConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        ConfigurationSection worldsSection = config.getConfigurationSection("worlds");
        if (worldsSection == null) {
            return new WorldsConfig(Collections.emptyMap());
        }

        Map<String, WorldConfig> worlds = new HashMap<>();

        for (String worldName : worldsSection.getKeys(false)) {
            ConfigurationSection worldSection = worldsSection.getConfigurationSection(worldName);
            if (worldSection == null) {
                continue;
            }

            // Parse seed (optional)
            OptionalLong seed;
            if (worldSection.contains("seed")) {
                seed = OptionalLong.of(worldSection.getLong("seed"));
            } else {
                seed = OptionalLong.empty();
            }

            // Parse dimension
            String dimensionStr = worldSection.getString("dimension", "overworld");
            WorldConfig.Dimension dimension = WorldConfig.Dimension.fromString(dimensionStr);

            // Parse gamemode
            String gamemodeStr = worldSection.getString("gamemode", "SURVIVAL");
            GameMode gamemode = parseGameMode(gamemodeStr);

            // Parse superflat
            boolean superflat = worldSection.getBoolean("superflat", false);

            // Parse generator settings (for superflat customization)
            Optional<String> generatorSettings = Optional.ofNullable(
                    worldSection.getString("generator_settings"));

            // Parse structures (default true)
            boolean structures = worldSection.getBoolean("structures", true);

            // Parse hidden (default false)
            boolean hidden = worldSection.getBoolean("hidden", false);

            // Parse difficulty
            Optional<Difficulty> difficulty = Optional.ofNullable(
                    worldSection.getString("difficulty")).map(WorldsConfig::parseDifficulty);

            // Parse game rules
            Map<String, String> gameRules = new HashMap<>();
            ConfigurationSection rulesSection = worldSection.getConfigurationSection("game_rules");
            if (rulesSection != null) {
                for (String rule : rulesSection.getKeys(false)) {
                    gameRules.put(rule, String.valueOf(rulesSection.get(rule)));
                }
            }

            // Parse inventory group (defaults to world name if not specified)
            String inventoryGroup = worldSection.getString("inventory_group", worldName);

            // Parse teleport warmup (default true - use warmup; false = instant teleport)
            boolean teleportWarmup = worldSection.getBoolean("teleport_warmup", true);

            // Parse fixed time (optional, 0-24000)
            OptionalLong time;
            if (worldSection.contains("time")) {
                time = OptionalLong.of(worldSection.getLong("time"));
            } else {
                time = OptionalLong.empty();
            }

            // Parse fixed weather (optional)
            Optional<WorldConfig.Weather> weather = Optional.ofNullable(
                    worldSection.getString("weather"))
                    .map(WorldConfig.Weather::fromString);

            // Parse disable advancements (default false)
            boolean disableAdvancements = worldSection.getBoolean("disable_advancements", false);

            WorldConfig worldConfig = new WorldConfig(
                    worldName.toLowerCase(),
                    seed,
                    dimension,
                    gamemode,
                    superflat,
                    generatorSettings,
                    structures,
                    hidden,
                    difficulty,
                    Collections.unmodifiableMap(gameRules),
                    inventoryGroup.toLowerCase(),
                    teleportWarmup,
                    time,
                    weather,
                    disableAdvancements
            );

            worlds.put(worldName.toLowerCase(), worldConfig);
        }

        return new WorldsConfig(Collections.unmodifiableMap(worlds));
    }

    private static GameMode parseGameMode(String value) {
        if (value == null) {
            return GameMode.SURVIVAL;
        }
        return switch (value.toUpperCase()) {
            case "CREATIVE" -> GameMode.CREATIVE;
            case "ADVENTURE" -> GameMode.ADVENTURE;
            case "SPECTATOR" -> GameMode.SPECTATOR;
            default -> GameMode.SURVIVAL;
        };
    }

    private static Difficulty parseDifficulty(String value) {
        if (value == null) {
            return Difficulty.NORMAL;
        }
        return switch (value.toUpperCase()) {
            case "PEACEFUL" -> Difficulty.PEACEFUL;
            case "EASY" -> Difficulty.EASY;
            case "HARD" -> Difficulty.HARD;
            default -> Difficulty.NORMAL;
        };
    }
}
