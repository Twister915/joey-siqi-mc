package sh.joey.mc.rtp;

import org.bukkit.block.Biome;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates vague, mysterious hints about biomes for RTP location descriptions.
 */
public final class BiomeHints {

    private static final Random random = new Random();

    private enum BiomeCategory {
        FOREST,
        PLAINS,
        MOUNTAIN,
        DESERT,
        SNOWY,
        SWAMP,
        TAIGA,
        BEACH,
        RIVER,
        JUNGLE,
        SAVANNA,
        BADLANDS,
        CAVE,
        CHERRY,
        PALE_GARDEN,
        END,
        NETHER,
        UNKNOWN
    }

    private static final Map<BiomeCategory, List<String>> HINTS = Map.ofEntries(
            Map.entry(BiomeCategory.FOREST, List.of(
                    "dense woodland awaits",
                    "tall trees obscure the horizon",
                    "a canopy of leaves overhead",
                    "shadows dance beneath the trees",
                    "the forest beckons"
            )),
            Map.entry(BiomeCategory.PLAINS, List.of(
                    "wide open spaces",
                    "gentle rolling hills",
                    "grasslands stretch before you",
                    "an open expanse awaits",
                    "flat terrain as far as the eye can see"
            )),
            Map.entry(BiomeCategory.MOUNTAIN, List.of(
                    "peaks rise in the distance",
                    "rugged terrain ahead",
                    "rocky heights await",
                    "steep cliffs and valleys",
                    "the mountains call"
            )),
            Map.entry(BiomeCategory.DESERT, List.of(
                    "sand stretches endlessly",
                    "dry heat awaits",
                    "dunes ripple across the land",
                    "a barren but resource-rich expanse",
                    "cacti dot the landscape"
            )),
            Map.entry(BiomeCategory.SNOWY, List.of(
                    "a frozen landscape",
                    "snow blankets the ground",
                    "ice crystals glitter in the light",
                    "a winter wonderland awaits",
                    "the cold bites at your skin"
            )),
            Map.entry(BiomeCategory.SWAMP, List.of(
                    "murky waters ahead",
                    "twisted trees emerge from fog",
                    "the air hangs thick and heavy",
                    "lily pads float on still water",
                    "a mysterious wetland awaits"
            )),
            Map.entry(BiomeCategory.TAIGA, List.of(
                    "spruce forests stand tall",
                    "a crisp northern breeze",
                    "evergreen trees stretch skyward",
                    "wolves may roam these woods",
                    "a boreal forest awaits"
            )),
            Map.entry(BiomeCategory.BEACH, List.of(
                    "waves lap at the shore",
                    "sandy ground underfoot",
                    "the ocean breeze is refreshing",
                    "seashells scattered about",
                    "where land meets sea"
            )),
            Map.entry(BiomeCategory.RIVER, List.of(
                    "water carves through the land",
                    "a winding waterway",
                    "fish swim in the current",
                    "the river flows ever onward",
                    "clay and sand line the banks"
            )),
            Map.entry(BiomeCategory.JUNGLE, List.of(
                    "dense tropical foliage",
                    "vines hang from towering trees",
                    "exotic birds call overhead",
                    "ancient temples may hide here",
                    "a lush green paradise"
            )),
            Map.entry(BiomeCategory.SAVANNA, List.of(
                    "acacia trees dot the plains",
                    "golden grass sways in the wind",
                    "a warm, dry climate",
                    "vast open terrain",
                    "african-inspired landscapes"
            )),
            Map.entry(BiomeCategory.BADLANDS, List.of(
                    "terracotta towers rise high",
                    "red and orange layers stripe the hills",
                    "gold may hide in these hills",
                    "a striking, barren beauty",
                    "mesa formations await"
            )),
            Map.entry(BiomeCategory.CAVE, List.of(
                    "echoes from below",
                    "caverns await exploration",
                    "underground wonders",
                    "darkness and mystery below",
                    "subterranean secrets"
            )),
            Map.entry(BiomeCategory.CHERRY, List.of(
                    "pink petals drift on the wind",
                    "a grove of beauty",
                    "cherry blossoms in bloom",
                    "a serene, peaceful landscape",
                    "delicate flowers everywhere"
            )),
            Map.entry(BiomeCategory.PALE_GARDEN, List.of(
                    "an eerie stillness hangs in the air",
                    "pale trees loom overhead",
                    "something watches from the shadows",
                    "beauty with an unsettling edge",
                    "tread carefully here"
            )),
            Map.entry(BiomeCategory.END, List.of(
                    "the void stretches endlessly",
                    "chorus plants sway silently",
                    "end stone beneath your feet"
            )),
            Map.entry(BiomeCategory.NETHER, List.of(
                    "heat and danger await",
                    "lava flows nearby",
                    "a hostile dimension"
            )),
            Map.entry(BiomeCategory.UNKNOWN, List.of(
                    "uncharted territory",
                    "adventure awaits",
                    "the unknown beckons",
                    "new lands to explore",
                    "mystery lies ahead"
            ))
    );

    private BiomeHints() {}

    /**
     * Get a random hint for the given biome.
     */
    public static String getHint(Biome biome) {
        BiomeCategory category = categorize(biome);
        List<String> hints = HINTS.get(category);
        return hints.get(random.nextInt(hints.size()));
    }

    private static BiomeCategory categorize(Biome biome) {
        String name = biome.getKey().getKey().toLowerCase();

        // Check for specific biomes first
        if (name.contains("cherry")) return BiomeCategory.CHERRY;
        if (name.contains("pale_garden")) return BiomeCategory.PALE_GARDEN;

        // Forest variants
        if (name.contains("forest") || name.contains("grove") && !name.contains("cherry")) {
            return BiomeCategory.FOREST;
        }

        // Plains and meadows
        if (name.contains("plains") || name.contains("meadow")) {
            return BiomeCategory.PLAINS;
        }

        // Mountain variants
        if (name.contains("mountain") || name.contains("peak") || name.contains("windswept")
                || name.contains("stony") || name.contains("hill")) {
            return BiomeCategory.MOUNTAIN;
        }

        // Desert
        if (name.contains("desert")) {
            return BiomeCategory.DESERT;
        }

        // Snowy/frozen
        if (name.contains("snow") || name.contains("frozen") || name.contains("ice")) {
            return BiomeCategory.SNOWY;
        }

        // Swamp/mangrove
        if (name.contains("swamp") || name.contains("mangrove")) {
            return BiomeCategory.SWAMP;
        }

        // Taiga variants
        if (name.contains("taiga")) {
            return BiomeCategory.TAIGA;
        }

        // Beach/shore
        if (name.contains("beach") || name.contains("shore")) {
            return BiomeCategory.BEACH;
        }

        // River
        if (name.contains("river")) {
            return BiomeCategory.RIVER;
        }

        // Jungle
        if (name.contains("jungle") || name.contains("bamboo")) {
            return BiomeCategory.JUNGLE;
        }

        // Savanna
        if (name.contains("savanna")) {
            return BiomeCategory.SAVANNA;
        }

        // Badlands/mesa
        if (name.contains("badlands") || name.contains("mesa") || name.contains("eroded")) {
            return BiomeCategory.BADLANDS;
        }

        // Cave biomes
        if (name.contains("cave") || name.contains("dripstone") || name.contains("lush")) {
            return BiomeCategory.CAVE;
        }

        // End biomes
        if (name.contains("end") && !name.contains("windswept")) {
            return BiomeCategory.END;
        }

        // Nether biomes
        if (name.contains("nether") || name.contains("soul") || name.contains("crimson")
                || name.contains("warped") || name.contains("basalt")) {
            return BiomeCategory.NETHER;
        }

        return BiomeCategory.UNKNOWN;
    }

    /**
     * Format a biome key into a readable name.
     * E.g., "dark_forest" -> "Dark Forest"
     */
    public static String formatBiomeName(Biome biome) {
        String key = biome.getKey().getKey();
        String[] words = key.split("_");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            String word = words[i];
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }
}
