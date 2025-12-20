package sh.joey.mc.multiworld;

import org.bukkit.Difficulty;
import org.bukkit.GameMode;
import org.bukkit.World;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Configuration for a single custom world.
 *
 * @param name              The world name (folder name)
 * @param seed              Optional seed for world generation (only used when creating new worlds)
 * @param dimension         The world dimension (OVERWORLD, NETHER, THE_END)
 * @param gamemode          The gamemode to set when a player enters this world
 * @param superflat         If true, use a superflat world type (only for new worlds)
 * @param generatorSettings Optional superflat JSON config (only for new superflat worlds)
 * @param structures        Whether to generate structures (villages, etc.) - only for new worlds
 * @param hidden            If true, world is not shown in /world list and cannot be teleported to directly
 * @param difficulty        Optional difficulty setting for this world
 * @param gameRules         Game rule overrides (e.g., doMobSpawning=false)
 * @param inventoryGroup    The inventory group this world belongs to
 * @param teleportWarmup    If false, teleports from this world are instant (no warmup countdown)
 */
public record WorldConfig(
        String name,
        OptionalLong seed,
        Dimension dimension,
        GameMode gamemode,
        boolean superflat,
        Optional<String> generatorSettings,
        boolean structures,
        boolean hidden,
        Optional<Difficulty> difficulty,
        Map<String, String> gameRules,
        String inventoryGroup,
        boolean teleportWarmup
) {
    /**
     * World dimensions with their corresponding Bukkit Environment.
     */
    public enum Dimension {
        OVERWORLD(World.Environment.NORMAL),
        NETHER(World.Environment.NETHER),
        THE_END(World.Environment.THE_END);

        private final World.Environment environment;

        Dimension(World.Environment environment) {
            this.environment = environment;
        }

        public World.Environment environment() {
            return environment;
        }

        public static Dimension fromString(String value) {
            if (value == null) {
                return OVERWORLD;
            }
            return switch (value.toLowerCase()) {
                case "nether" -> NETHER;
                case "end", "the_end" -> THE_END;
                default -> OVERWORLD;
            };
        }
    }
}
