package sh.joey.mc.utility;

import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Item aliases for convenience in /item and /give commands.
 */
public final class ItemAliases {

    private static final Map<String, Material> ALIASES = new HashMap<>();

    static {
        // Common shorthands
        alias("dirt", Material.DIRT);
        alias("stone", Material.STONE);
        alias("cobble", Material.COBBLESTONE);
        alias("wood", Material.OAK_LOG);
        alias("log", Material.OAK_LOG);
        alias("plank", Material.OAK_PLANKS);
        alias("planks", Material.OAK_PLANKS);
        alias("glass", Material.GLASS);
        alias("sand", Material.SAND);
        alias("gravel", Material.GRAVEL);
        alias("clay", Material.CLAY);

        // Ores and ingots
        alias("coal", Material.COAL);
        alias("iron", Material.IRON_INGOT);
        alias("gold", Material.GOLD_INGOT);
        alias("diamond", Material.DIAMOND);
        alias("emerald", Material.EMERALD);
        alias("lapis", Material.LAPIS_LAZULI);
        alias("redstone", Material.REDSTONE);
        alias("netherite", Material.NETHERITE_INGOT);
        alias("copper", Material.COPPER_INGOT);
        alias("quartz", Material.QUARTZ);

        // Ore blocks
        alias("ironore", Material.IRON_ORE);
        alias("goldore", Material.GOLD_ORE);
        alias("diamondore", Material.DIAMOND_ORE);
        alias("coalblock", Material.COAL_BLOCK);
        alias("ironblock", Material.IRON_BLOCK);
        alias("goldblock", Material.GOLD_BLOCK);
        alias("diamondblock", Material.DIAMOND_BLOCK);
        alias("emeraldblock", Material.EMERALD_BLOCK);
        alias("netheriteblock", Material.NETHERITE_BLOCK);

        // Tools
        alias("dpick", Material.DIAMOND_PICKAXE);
        alias("daxe", Material.DIAMOND_AXE);
        alias("dshovel", Material.DIAMOND_SHOVEL);
        alias("dsword", Material.DIAMOND_SWORD);
        alias("dhoe", Material.DIAMOND_HOE);
        alias("npick", Material.NETHERITE_PICKAXE);
        alias("naxe", Material.NETHERITE_AXE);
        alias("nshovel", Material.NETHERITE_SHOVEL);
        alias("nsword", Material.NETHERITE_SWORD);
        alias("nhoe", Material.NETHERITE_HOE);
        alias("ipick", Material.IRON_PICKAXE);
        alias("iaxe", Material.IRON_AXE);
        alias("ishovel", Material.IRON_SHOVEL);
        alias("isword", Material.IRON_SWORD);
        alias("ihoe", Material.IRON_HOE);

        // Armor
        alias("dhelmet", Material.DIAMOND_HELMET);
        alias("dchest", Material.DIAMOND_CHESTPLATE);
        alias("dlegs", Material.DIAMOND_LEGGINGS);
        alias("dboots", Material.DIAMOND_BOOTS);
        alias("nhelmet", Material.NETHERITE_HELMET);
        alias("nchest", Material.NETHERITE_CHESTPLATE);
        alias("nlegs", Material.NETHERITE_LEGGINGS);
        alias("nboots", Material.NETHERITE_BOOTS);
        alias("ihelmet", Material.IRON_HELMET);
        alias("ichest", Material.IRON_CHESTPLATE);
        alias("ilegs", Material.IRON_LEGGINGS);
        alias("iboots", Material.IRON_BOOTS);
        alias("elytra", Material.ELYTRA);

        // Food
        alias("steak", Material.COOKED_BEEF);
        alias("beef", Material.COOKED_BEEF);
        alias("pork", Material.COOKED_PORKCHOP);
        alias("chicken", Material.COOKED_CHICKEN);
        alias("bread", Material.BREAD);
        alias("apple", Material.APPLE);
        alias("gapple", Material.GOLDEN_APPLE);
        alias("notch", Material.ENCHANTED_GOLDEN_APPLE);
        alias("egapple", Material.ENCHANTED_GOLDEN_APPLE);
        alias("carrot", Material.CARROT);
        alias("potato", Material.BAKED_POTATO);
        alias("melon", Material.MELON_SLICE);

        // Utility
        alias("torch", Material.TORCH);
        alias("bucket", Material.BUCKET);
        alias("water", Material.WATER_BUCKET);
        alias("lava", Material.LAVA_BUCKET);
        alias("flint", Material.FLINT_AND_STEEL);
        alias("bow", Material.BOW);
        alias("arrow", Material.ARROW);
        alias("string", Material.STRING);
        alias("leather", Material.LEATHER);
        alias("feather", Material.FEATHER);
        alias("bone", Material.BONE);
        alias("slime", Material.SLIME_BALL);
        alias("ender", Material.ENDER_PEARL);
        alias("pearl", Material.ENDER_PEARL);
        alias("eye", Material.ENDER_EYE);
        alias("blaze", Material.BLAZE_ROD);
        alias("ghast", Material.GHAST_TEAR);
        alias("wart", Material.NETHER_WART);
        alias("glowstone", Material.GLOWSTONE);
        alias("obsidian", Material.OBSIDIAN);
        alias("crying", Material.CRYING_OBSIDIAN);
        alias("tnt", Material.TNT);
        alias("bed", Material.RED_BED);
        alias("chest", Material.CHEST);
        alias("furnace", Material.FURNACE);
        alias("crafting", Material.CRAFTING_TABLE);
        alias("workbench", Material.CRAFTING_TABLE);
        alias("anvil", Material.ANVIL);
        alias("enchant", Material.ENCHANTING_TABLE);
        alias("brewing", Material.BREWING_STAND);
        alias("hopper", Material.HOPPER);
        alias("dropper", Material.DROPPER);
        alias("dispenser", Material.DISPENSER);
        alias("piston", Material.PISTON);
        alias("sticky", Material.STICKY_PISTON);
        alias("observer", Material.OBSERVER);
        alias("comparator", Material.COMPARATOR);
        alias("repeater", Material.REPEATER);
        alias("lever", Material.LEVER);
        alias("button", Material.STONE_BUTTON);
        alias("pressure", Material.STONE_PRESSURE_PLATE);
        alias("rail", Material.RAIL);
        alias("minecart", Material.MINECART);
        alias("boat", Material.OAK_BOAT);
        alias("saddle", Material.SADDLE);
        alias("lead", Material.LEAD);
        alias("name", Material.NAME_TAG);
        alias("nametag", Material.NAME_TAG);
        alias("book", Material.BOOK);
        alias("paper", Material.PAPER);
        alias("map", Material.MAP);
        alias("compass", Material.COMPASS);
        alias("clock", Material.CLOCK);
        alias("shears", Material.SHEARS);
        alias("shield", Material.SHIELD);
        alias("totem", Material.TOTEM_OF_UNDYING);
        alias("trident", Material.TRIDENT);
        alias("crossbow", Material.CROSSBOW);
        alias("firework", Material.FIREWORK_ROCKET);
        alias("rocket", Material.FIREWORK_ROCKET);
        alias("spyglass", Material.SPYGLASS);
        alias("bundle", Material.BUNDLE);

        // Potions and brewing
        alias("bottle", Material.GLASS_BOTTLE);
        alias("cauldron", Material.CAULDRON);
        alias("potion", Material.POTION);
        alias("splash", Material.SPLASH_POTION);
        alias("lingering", Material.LINGERING_POTION);

        // Dyes
        alias("white", Material.WHITE_DYE);
        alias("orange", Material.ORANGE_DYE);
        alias("magenta", Material.MAGENTA_DYE);
        alias("lightblue", Material.LIGHT_BLUE_DYE);
        alias("yellow", Material.YELLOW_DYE);
        alias("lime", Material.LIME_DYE);
        alias("pink", Material.PINK_DYE);
        alias("gray", Material.GRAY_DYE);
        alias("lightgray", Material.LIGHT_GRAY_DYE);
        alias("cyan", Material.CYAN_DYE);
        alias("purple", Material.PURPLE_DYE);
        alias("blue", Material.BLUE_DYE);
        alias("brown", Material.BROWN_DYE);
        alias("green", Material.GREEN_DYE);
        alias("red", Material.RED_DYE);
        alias("black", Material.BLACK_DYE);

        // Spawn eggs
        alias("spawnegg", Material.PIG_SPAWN_EGG);
    }

    private static void alias(String name, Material material) {
        ALIASES.put(name.toLowerCase(), material);
    }

    /**
     * Resolves a material from an alias or material name.
     * Returns empty if not found.
     */
    public static Optional<Material> resolve(String input) {
        String lower = input.toLowerCase().replace("_", "").replace("-", "");

        // Check aliases first
        if (ALIASES.containsKey(lower)) {
            return Optional.of(ALIASES.get(lower));
        }

        // Try direct material name
        try {
            return Optional.of(Material.valueOf(input.toUpperCase()));
        } catch (IllegalArgumentException e) {
            // Try with underscores replaced
            try {
                String normalized = input.toUpperCase().replace("-", "_");
                return Optional.of(Material.valueOf(normalized));
            } catch (IllegalArgumentException e2) {
                return Optional.empty();
            }
        }
    }

    /**
     * Returns all alias names for tab completion.
     */
    public static Set<String> getAliasNames() {
        return ALIASES.keySet();
    }

    private ItemAliases() {}
}
