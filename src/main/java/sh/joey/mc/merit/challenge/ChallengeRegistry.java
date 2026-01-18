package sh.joey.mc.merit.challenge;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static sh.joey.mc.merit.challenge.ChallengeCategory.*;

/**
 * Registry of all challenge definitions.
 */
public final class ChallengeRegistry {

    private final Map<String, Challenge> byId = new HashMap<>();
    private final Map<ChallengeCategory, List<Challenge>> byCategory = new EnumMap<>(ChallengeCategory.class);

    public ChallengeRegistry() {
        // Initialize category lists
        for (ChallengeCategory cat : ChallengeCategory.values()) {
            byCategory.put(cat, new ArrayList<>());
        }

        // Register all challenges
        registerMiningChallenges();
        registerFarmingChallenges();
        registerBuildingChallenges();
        registerPvpChallenges();
        registerPveChallenges();
        registerProgressionChallenges();
        registerCraftingChallenges();
        registerSmeltingChallenges();
        registerExplorationChallenges();
        registerTimeChallenges();
    }

    private void register(Challenge challenge) {
        byId.put(challenge.id(), challenge);
        byCategory.get(challenge.category()).add(challenge);
    }

    public Optional<Challenge> getById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<Challenge> getByCategory(ChallengeCategory category) {
        return byCategory.getOrDefault(category, List.of());
    }

    public List<Challenge> getAll() {
        return new ArrayList<>(byId.values());
    }

    // ===== MINING CHALLENGES =====
    private void registerMiningChallenges() {
        register(Challenge.of("mining_stone_breaker", "Stone Breaker",
                "Mine 500 stone blocks", MINING, 500, 50,
                "blocks_mined:STONE", "blocks_mined:COBBLESTONE", "blocks_mined:ANDESITE",
                "blocks_mined:DIORITE", "blocks_mined:GRANITE"));

        register(Challenge.of("mining_deep_digger", "Deep Digger",
                "Mine 300 deepslate blocks", MINING, 300, 75,
                "blocks_mined:DEEPSLATE", "blocks_mined:COBBLED_DEEPSLATE"));

        register(Challenge.of("mining_coal_collector", "Coal Collector",
                "Mine 100 coal ore", MINING, 100, 50,
                "blocks_mined:COAL_ORE", "blocks_mined:DEEPSLATE_COAL_ORE"));

        register(Challenge.of("mining_iron_seeker", "Iron Seeker",
                "Mine 75 iron ore", MINING, 75, 75,
                "blocks_mined:IRON_ORE", "blocks_mined:DEEPSLATE_IRON_ORE"));

        register(Challenge.of("mining_gold_hunter", "Gold Hunter",
                "Mine 50 gold ore", MINING, 50, 100,
                "blocks_mined:GOLD_ORE", "blocks_mined:DEEPSLATE_GOLD_ORE", "blocks_mined:NETHER_GOLD_ORE"));

        register(Challenge.of("mining_diamond_delver", "Diamond Delver",
                "Mine 25 diamond ore", MINING, 25, 150,
                "blocks_mined:DIAMOND_ORE", "blocks_mined:DEEPSLATE_DIAMOND_ORE"));

        register(Challenge.of("mining_emerald_excavator", "Emerald Excavator",
                "Mine 15 emerald ore", MINING, 15, 175,
                "blocks_mined:EMERALD_ORE", "blocks_mined:DEEPSLATE_EMERALD_ORE"));

        register(Challenge.of("mining_redstone_researcher", "Redstone Researcher",
                "Mine 100 redstone ore", MINING, 100, 75,
                "blocks_mined:REDSTONE_ORE", "blocks_mined:DEEPSLATE_REDSTONE_ORE"));

        register(Challenge.of("mining_lapis_lover", "Lapis Lover",
                "Mine 50 lapis ore", MINING, 50, 75,
                "blocks_mined:LAPIS_ORE", "blocks_mined:DEEPSLATE_LAPIS_ORE"));

        register(Challenge.of("mining_copper_collector", "Copper Collector",
                "Mine 75 copper ore", MINING, 75, 50,
                "blocks_mined:COPPER_ORE", "blocks_mined:DEEPSLATE_COPPER_ORE"));

        register(Challenge.of("mining_ancient_scavenger", "Ancient Scavenger",
                "Mine 2 ancient debris", MINING, 2, 200,
                "blocks_mined:ANCIENT_DEBRIS"));

        register(Challenge.of("mining_obsidian_breaker", "Obsidian Breaker",
                "Mine 20 obsidian", MINING, 20, 100,
                "blocks_mined:OBSIDIAN", "blocks_mined:CRYING_OBSIDIAN"));

        register(Challenge.of("mining_nether_miner", "Nether Miner",
                "Mine 200 netherrack", MINING, 200, 50,
                "blocks_mined:NETHERRACK"));

        register(Challenge.of("mining_quartz_quest", "Quartz Quest",
                "Mine 100 nether quartz ore", MINING, 100, 75,
                "blocks_mined:NETHER_QUARTZ_ORE"));

        register(Challenge.of("mining_end_explorer", "End Explorer",
                "Mine 100 end stone", MINING, 100, 75,
                "blocks_mined:END_STONE"));
    }

    // ===== FARMING CHALLENGES =====
    private void registerFarmingChallenges() {
        register(Challenge.of("farming_wheat_farmer", "Wheat Farmer",
                "Harvest 128 wheat", FARMING, 128, 50,
                "harvested:WHEAT"));

        register(Challenge.of("farming_carrot_cultivator", "Carrot Cultivator",
                "Harvest 128 carrots", FARMING, 128, 50,
                "harvested:CARROTS"));

        register(Challenge.of("farming_potato_picker", "Potato Picker",
                "Harvest 128 potatoes", FARMING, 128, 50,
                "harvested:POTATOES"));

        register(Challenge.of("farming_beetroot_baron", "Beetroot Baron",
                "Harvest 64 beetroot", FARMING, 64, 60,
                "harvested:BEETROOTS"));

        register(Challenge.of("farming_sugar_rush", "Sugar Rush",
                "Harvest 100 sugar cane", FARMING, 100, 50,
                "harvested:SUGAR_CANE"));

        register(Challenge.of("farming_melon_master", "Melon Master",
                "Harvest 50 melons", FARMING, 50, 60,
                "harvested:MELON"));

        register(Challenge.of("farming_pumpkin_picker", "Pumpkin Picker",
                "Harvest 50 pumpkins", FARMING, 50, 60,
                "harvested:PUMPKIN"));

        register(Challenge.of("farming_nether_wart_wizard", "Nether Wart Wizard",
                "Harvest 64 nether wart", FARMING, 64, 75,
                "harvested:NETHER_WART"));

        register(Challenge.of("farming_cocoa_collector", "Cocoa Collector",
                "Harvest 50 cocoa beans", FARMING, 50, 60,
                "harvested:COCOA"));

        register(Challenge.of("farming_bamboo_bonanza", "Bamboo Bonanza",
                "Harvest 200 bamboo", FARMING, 200, 50,
                "harvested:BAMBOO"));

        register(Challenge.of("farming_cactus_caretaker", "Cactus Caretaker",
                "Harvest 50 cactus", FARMING, 50, 50,
                "harvested:CACTUS"));

        register(Challenge.of("farming_berry_picker", "Berry Picker",
                "Harvest 64 sweet berries", FARMING, 64, 50,
                "harvested:SWEET_BERRY_BUSH"));
    }

    // ===== BUILDING CHALLENGES =====
    private void registerBuildingChallenges() {
        register(Challenge.of("building_block_placer", "Block Placer",
                "Place 500 blocks", BUILDING, 500, 50,
                "blocks_placed:ANY"));

        register(Challenge.of("building_woodworker", "Woodworker",
                "Place 200 wood planks", BUILDING, 200, 50,
                "blocks_placed:OAK_PLANKS", "blocks_placed:SPRUCE_PLANKS", "blocks_placed:BIRCH_PLANKS",
                "blocks_placed:JUNGLE_PLANKS", "blocks_placed:ACACIA_PLANKS", "blocks_placed:DARK_OAK_PLANKS",
                "blocks_placed:MANGROVE_PLANKS", "blocks_placed:CHERRY_PLANKS", "blocks_placed:BAMBOO_PLANKS",
                "blocks_placed:CRIMSON_PLANKS", "blocks_placed:WARPED_PLANKS"));

        register(Challenge.of("building_stone_mason", "Stone Mason",
                "Place 200 stone blocks", BUILDING, 200, 50,
                "blocks_placed:STONE", "blocks_placed:COBBLESTONE", "blocks_placed:STONE_BRICKS",
                "blocks_placed:MOSSY_STONE_BRICKS", "blocks_placed:SMOOTH_STONE"));

        register(Challenge.of("building_brick_layer", "Brick Layer",
                "Place 100 bricks", BUILDING, 100, 60,
                "blocks_placed:BRICKS", "blocks_placed:BRICK_SLAB", "blocks_placed:BRICK_STAIRS",
                "blocks_placed:BRICK_WALL"));

        register(Challenge.of("building_glass_glazier", "Glass Glazier",
                "Place 100 glass blocks", BUILDING, 100, 50,
                "blocks_placed:GLASS", "blocks_placed:GLASS_PANE",
                "blocks_placed:WHITE_STAINED_GLASS", "blocks_placed:ORANGE_STAINED_GLASS",
                "blocks_placed:MAGENTA_STAINED_GLASS", "blocks_placed:LIGHT_BLUE_STAINED_GLASS",
                "blocks_placed:YELLOW_STAINED_GLASS", "blocks_placed:LIME_STAINED_GLASS",
                "blocks_placed:PINK_STAINED_GLASS", "blocks_placed:GRAY_STAINED_GLASS",
                "blocks_placed:LIGHT_GRAY_STAINED_GLASS", "blocks_placed:CYAN_STAINED_GLASS",
                "blocks_placed:PURPLE_STAINED_GLASS", "blocks_placed:BLUE_STAINED_GLASS",
                "blocks_placed:BROWN_STAINED_GLASS", "blocks_placed:GREEN_STAINED_GLASS",
                "blocks_placed:RED_STAINED_GLASS", "blocks_placed:BLACK_STAINED_GLASS"));

        register(Challenge.of("building_concrete_constructor", "Concrete Constructor",
                "Place 100 concrete blocks", BUILDING, 100, 60,
                "blocks_placed:WHITE_CONCRETE", "blocks_placed:ORANGE_CONCRETE",
                "blocks_placed:MAGENTA_CONCRETE", "blocks_placed:LIGHT_BLUE_CONCRETE",
                "blocks_placed:YELLOW_CONCRETE", "blocks_placed:LIME_CONCRETE",
                "blocks_placed:PINK_CONCRETE", "blocks_placed:GRAY_CONCRETE",
                "blocks_placed:LIGHT_GRAY_CONCRETE", "blocks_placed:CYAN_CONCRETE",
                "blocks_placed:PURPLE_CONCRETE", "blocks_placed:BLUE_CONCRETE",
                "blocks_placed:BROWN_CONCRETE", "blocks_placed:GREEN_CONCRETE",
                "blocks_placed:RED_CONCRETE", "blocks_placed:BLACK_CONCRETE"));

        register(Challenge.of("building_redstone_engineer", "Redstone Engineer",
                "Place 50 redstone components", BUILDING, 50, 75,
                "blocks_placed:REDSTONE_WIRE", "blocks_placed:REDSTONE_TORCH",
                "blocks_placed:REPEATER", "blocks_placed:COMPARATOR",
                "blocks_placed:PISTON", "blocks_placed:STICKY_PISTON",
                "blocks_placed:OBSERVER", "blocks_placed:HOPPER",
                "blocks_placed:DROPPER", "blocks_placed:DISPENSER"));

        register(Challenge.of("building_light_placer", "Light Placer",
                "Place 100 light sources", BUILDING, 100, 50,
                "blocks_placed:TORCH", "blocks_placed:LANTERN", "blocks_placed:SOUL_LANTERN",
                "blocks_placed:GLOWSTONE", "blocks_placed:SEA_LANTERN", "blocks_placed:SHROOMLIGHT",
                "blocks_placed:END_ROD", "blocks_placed:CAMPFIRE", "blocks_placed:SOUL_CAMPFIRE"));

        register(Challenge.of("building_stair_stepper", "Stair Stepper",
                "Place 100 stairs", BUILDING, 100, 50,
                "blocks_placed:OAK_STAIRS", "blocks_placed:SPRUCE_STAIRS", "blocks_placed:BIRCH_STAIRS",
                "blocks_placed:JUNGLE_STAIRS", "blocks_placed:ACACIA_STAIRS", "blocks_placed:DARK_OAK_STAIRS",
                "blocks_placed:STONE_STAIRS", "blocks_placed:COBBLESTONE_STAIRS", "blocks_placed:BRICK_STAIRS",
                "blocks_placed:STONE_BRICK_STAIRS", "blocks_placed:NETHER_BRICK_STAIRS",
                "blocks_placed:SANDSTONE_STAIRS", "blocks_placed:QUARTZ_STAIRS"));

        register(Challenge.of("building_fence_builder", "Fence Builder",
                "Place 100 fences or walls", BUILDING, 100, 50,
                "blocks_placed:OAK_FENCE", "blocks_placed:SPRUCE_FENCE", "blocks_placed:BIRCH_FENCE",
                "blocks_placed:JUNGLE_FENCE", "blocks_placed:ACACIA_FENCE", "blocks_placed:DARK_OAK_FENCE",
                "blocks_placed:COBBLESTONE_WALL", "blocks_placed:STONE_BRICK_WALL", "blocks_placed:BRICK_WALL",
                "blocks_placed:NETHER_BRICK_FENCE"));

        register(Challenge.of("building_terracotta_artist", "Terracotta Artist",
                "Place 100 terracotta blocks", BUILDING, 100, 60,
                "blocks_placed:TERRACOTTA", "blocks_placed:WHITE_TERRACOTTA",
                "blocks_placed:ORANGE_TERRACOTTA", "blocks_placed:MAGENTA_TERRACOTTA",
                "blocks_placed:LIGHT_BLUE_TERRACOTTA", "blocks_placed:YELLOW_TERRACOTTA",
                "blocks_placed:LIME_TERRACOTTA", "blocks_placed:PINK_TERRACOTTA",
                "blocks_placed:GRAY_TERRACOTTA", "blocks_placed:LIGHT_GRAY_TERRACOTTA",
                "blocks_placed:CYAN_TERRACOTTA", "blocks_placed:PURPLE_TERRACOTTA",
                "blocks_placed:BLUE_TERRACOTTA", "blocks_placed:BROWN_TERRACOTTA",
                "blocks_placed:GREEN_TERRACOTTA", "blocks_placed:RED_TERRACOTTA",
                "blocks_placed:BLACK_TERRACOTTA"));

        register(Challenge.of("building_wool_weaver", "Wool Weaver",
                "Place 100 wool blocks", BUILDING, 100, 50,
                "blocks_placed:WHITE_WOOL", "blocks_placed:ORANGE_WOOL",
                "blocks_placed:MAGENTA_WOOL", "blocks_placed:LIGHT_BLUE_WOOL",
                "blocks_placed:YELLOW_WOOL", "blocks_placed:LIME_WOOL",
                "blocks_placed:PINK_WOOL", "blocks_placed:GRAY_WOOL",
                "blocks_placed:LIGHT_GRAY_WOOL", "blocks_placed:CYAN_WOOL",
                "blocks_placed:PURPLE_WOOL", "blocks_placed:BLUE_WOOL",
                "blocks_placed:BROWN_WOOL", "blocks_placed:GREEN_WOOL",
                "blocks_placed:RED_WOOL", "blocks_placed:BLACK_WOOL"));
    }

    // ===== PVP CHALLENGES =====
    private void registerPvpChallenges() {
        register(Challenge.of("pvp_first_blood", "First Blood",
                "Kill 1 player", PVP, 1, 100,
                "pvp_kills"));

        register(Challenge.of("pvp_duelist", "Duelist",
                "Kill 3 players", PVP, 3, 150,
                "pvp_kills"));

        register(Challenge.of("pvp_warrior", "Warrior",
                "Kill 5 players", PVP, 5, 200,
                "pvp_kills"));

        register(Challenge.of("pvp_sword_master", "Sword Master",
                "Kill 3 players with a sword", PVP, 3, 150,
                "pvp_kills:SWORD"));

        register(Challenge.of("pvp_archer", "Archer",
                "Kill 2 players with a bow", PVP, 2, 175,
                "pvp_kills:BOW"));

        register(Challenge.of("pvp_crossbow_champion", "Crossbow Champion",
                "Kill 2 players with a crossbow", PVP, 2, 175,
                "pvp_kills:CROSSBOW"));

        register(Challenge.of("pvp_axe_executioner", "Axe Executioner",
                "Kill 2 players with an axe", PVP, 2, 175,
                "pvp_kills:AXE"));

        register(Challenge.of("pvp_damage_dealer", "Damage Dealer",
                "Deal 100 damage to players", PVP, 100, 100,
                "damage_dealt:PLAYER"));

        register(Challenge.of("pvp_survivor", "Survivor",
                "Win 3 PvP fights (kill without dying)", PVP, 3, 200,
                "pvp_wins"));

        register(Challenge.of("pvp_trident_striker", "Trident Striker",
                "Kill 1 player with a trident", PVP, 1, 200,
                "pvp_kills:TRIDENT"));
    }

    // ===== PVE CHALLENGES =====
    private void registerPveChallenges() {
        register(Challenge.of("pve_zombie_hunter", "Zombie Hunter",
                "Kill 30 zombies", PVE, 30, 50,
                "kills:ZOMBIE", "kills:ZOMBIE_VILLAGER", "kills:HUSK", "kills:DROWNED"));

        register(Challenge.of("pve_skeleton_slayer", "Skeleton Slayer",
                "Kill 30 skeletons", PVE, 30, 50,
                "kills:SKELETON", "kills:STRAY", "kills:WITHER_SKELETON"));

        register(Challenge.of("pve_spider_squasher", "Spider Squasher",
                "Kill 20 spiders", PVE, 20, 50,
                "kills:SPIDER", "kills:CAVE_SPIDER"));

        register(Challenge.of("pve_creeper_crusher", "Creeper Crusher",
                "Kill 15 creepers", PVE, 15, 75,
                "kills:CREEPER"));

        register(Challenge.of("pve_enderman_eliminator", "Enderman Eliminator",
                "Kill 10 endermen", PVE, 10, 100,
                "kills:ENDERMAN"));

        register(Challenge.of("pve_blaze_battler", "Blaze Battler",
                "Kill 15 blazes", PVE, 15, 100,
                "kills:BLAZE"));

        register(Challenge.of("pve_ghast_buster", "Ghast Buster",
                "Kill 5 ghasts", PVE, 5, 100,
                "kills:GHAST"));

        register(Challenge.of("pve_piglin_punisher", "Piglin Punisher",
                "Kill 20 piglins", PVE, 20, 75,
                "kills:PIGLIN", "kills:PIGLIN_BRUTE", "kills:ZOMBIFIED_PIGLIN"));

        register(Challenge.of("pve_guardian_hunter", "Guardian Hunter",
                "Kill 10 guardians", PVE, 10, 100,
                "kills:GUARDIAN", "kills:ELDER_GUARDIAN"));

        register(Challenge.of("pve_phantom_fighter", "Phantom Fighter",
                "Kill 10 phantoms", PVE, 10, 75,
                "kills:PHANTOM"));

        register(Challenge.of("pve_witch_hunter", "Witch Hunter",
                "Kill 5 witches", PVE, 5, 75,
                "kills:WITCH"));

        register(Challenge.of("pve_pillager_punisher", "Pillager Punisher",
                "Kill 15 pillagers", PVE, 15, 75,
                "kills:PILLAGER", "kills:VINDICATOR", "kills:RAVAGER", "kills:EVOKER"));

        register(Challenge.of("pve_warden_slayer", "Warden Slayer",
                "Kill 1 warden", PVE, 1, 500,
                "kills:WARDEN"));

        register(Challenge.of("pve_dragon_slayer", "Dragon Slayer",
                "Kill the Ender Dragon", PVE, 1, 500,
                "kills:ENDER_DRAGON"));

        register(Challenge.of("pve_wither_defeater", "Wither Defeater",
                "Kill the Wither", PVE, 1, 400,
                "kills:WITHER"));
    }

    // ===== PROGRESSION CHALLENGES =====
    private void registerProgressionChallenges() {
        register(Challenge.of("progression_xp_collector", "XP Collector",
                "Gain 500 XP", PROGRESSION, 500, 50,
                "xp_gained"));

        register(Challenge.of("progression_level_up", "Level Up",
                "Gain 10 XP levels", PROGRESSION, 10, 75,
                "levels_gained"));

        register(Challenge.of("progression_enchanter", "Enchanter",
                "Enchant 5 items", PROGRESSION, 5, 75,
                "items_enchanted"));

        register(Challenge.of("progression_high_enchanter", "High Enchanter",
                "Use level 30 enchantments 3 times", PROGRESSION, 3, 100,
                "enchants_level_30"));

        register(Challenge.of("progression_brewer", "Brewer",
                "Brew 10 potions", PROGRESSION, 10, 75,
                "potions_brewed"));

        register(Challenge.of("progression_trader", "Trader",
                "Trade with villagers 20 times", PROGRESSION, 20, 75,
                "villager_trades"));

        register(Challenge.of("progression_advancement_hunter", "Advancement Hunter",
                "Earn 5 advancements", PROGRESSION, 5, 100,
                "advancements_earned"));

        register(Challenge.of("progression_book_worm", "Book Worm",
                "Create 3 enchanted books", PROGRESSION, 3, 100,
                "enchanted_books_created"));

        register(Challenge.of("progression_anvil_user", "Anvil User",
                "Use an anvil 5 times", PROGRESSION, 5, 50,
                "anvil_uses"));

        register(Challenge.of("progression_repair_master", "Repair Master",
                "Repair items 5 times", PROGRESSION, 5, 60,
                "items_repaired"));

        register(Challenge.of("progression_grindstone_user", "Grindstone User",
                "Use a grindstone 3 times", PROGRESSION, 3, 50,
                "grindstone_uses"));

        register(Challenge.of("progression_smithing_apprentice", "Smithing Apprentice",
                "Use a smithing table 3 times", PROGRESSION, 3, 100,
                "smithing_uses"));
    }

    // ===== CRAFTING CHALLENGES =====
    private void registerCraftingChallenges() {
        register(Challenge.of("crafting_basic_crafter", "Basic Crafter",
                "Craft 50 items", CRAFTING, 50, 50,
                "items_crafted"));

        register(Challenge.of("crafting_tool_maker", "Tool Maker",
                "Craft 10 tools", CRAFTING, 10, 50,
                "crafted:WOODEN_PICKAXE", "crafted:STONE_PICKAXE", "crafted:IRON_PICKAXE",
                "crafted:GOLDEN_PICKAXE", "crafted:DIAMOND_PICKAXE", "crafted:NETHERITE_PICKAXE",
                "crafted:WOODEN_AXE", "crafted:STONE_AXE", "crafted:IRON_AXE",
                "crafted:GOLDEN_AXE", "crafted:DIAMOND_AXE", "crafted:NETHERITE_AXE",
                "crafted:WOODEN_SHOVEL", "crafted:STONE_SHOVEL", "crafted:IRON_SHOVEL",
                "crafted:GOLDEN_SHOVEL", "crafted:DIAMOND_SHOVEL", "crafted:NETHERITE_SHOVEL",
                "crafted:WOODEN_HOE", "crafted:STONE_HOE", "crafted:IRON_HOE",
                "crafted:GOLDEN_HOE", "crafted:DIAMOND_HOE", "crafted:NETHERITE_HOE"));

        register(Challenge.of("crafting_weapon_smith", "Weapon Smith",
                "Craft 5 weapons", CRAFTING, 5, 60,
                "crafted:WOODEN_SWORD", "crafted:STONE_SWORD", "crafted:IRON_SWORD",
                "crafted:GOLDEN_SWORD", "crafted:DIAMOND_SWORD", "crafted:NETHERITE_SWORD",
                "crafted:BOW", "crafted:CROSSBOW", "crafted:TRIDENT"));

        register(Challenge.of("crafting_armor_smith", "Armor Smith",
                "Craft 8 armor pieces", CRAFTING, 8, 75,
                "crafted:LEATHER_HELMET", "crafted:LEATHER_CHESTPLATE", "crafted:LEATHER_LEGGINGS", "crafted:LEATHER_BOOTS",
                "crafted:CHAINMAIL_HELMET", "crafted:CHAINMAIL_CHESTPLATE", "crafted:CHAINMAIL_LEGGINGS", "crafted:CHAINMAIL_BOOTS",
                "crafted:IRON_HELMET", "crafted:IRON_CHESTPLATE", "crafted:IRON_LEGGINGS", "crafted:IRON_BOOTS",
                "crafted:GOLDEN_HELMET", "crafted:GOLDEN_CHESTPLATE", "crafted:GOLDEN_LEGGINGS", "crafted:GOLDEN_BOOTS",
                "crafted:DIAMOND_HELMET", "crafted:DIAMOND_CHESTPLATE", "crafted:DIAMOND_LEGGINGS", "crafted:DIAMOND_BOOTS",
                "crafted:NETHERITE_HELMET", "crafted:NETHERITE_CHESTPLATE", "crafted:NETHERITE_LEGGINGS", "crafted:NETHERITE_BOOTS"));

        register(Challenge.of("crafting_redstone_crafter", "Redstone Crafter",
                "Craft 20 redstone components", CRAFTING, 20, 75,
                "crafted:REDSTONE_TORCH", "crafted:REPEATER", "crafted:COMPARATOR",
                "crafted:PISTON", "crafted:STICKY_PISTON", "crafted:OBSERVER",
                "crafted:HOPPER", "crafted:DROPPER", "crafted:DISPENSER"));

        register(Challenge.of("crafting_rail_builder", "Rail Builder",
                "Craft 100 rails", CRAFTING, 100, 60,
                "crafted:RAIL", "crafted:POWERED_RAIL", "crafted:DETECTOR_RAIL", "crafted:ACTIVATOR_RAIL"));

        register(Challenge.of("crafting_torch_maker", "Torch Maker",
                "Craft 200 torches", CRAFTING, 200, 50,
                "crafted:TORCH", "crafted:SOUL_TORCH"));

        register(Challenge.of("crafting_arrow_fletcher", "Arrow Fletcher",
                "Craft 200 arrows", CRAFTING, 200, 50,
                "crafted:ARROW", "crafted:SPECTRAL_ARROW", "crafted:TIPPED_ARROW"));

        register(Challenge.of("crafting_food_preparer", "Food Preparer",
                "Craft 50 food items", CRAFTING, 50, 50,
                "crafted:BREAD", "crafted:CAKE", "crafted:COOKIE", "crafted:PUMPKIN_PIE",
                "crafted:GOLDEN_APPLE", "crafted:GOLDEN_CARROT", "crafted:MUSHROOM_STEW",
                "crafted:RABBIT_STEW", "crafted:BEETROOT_SOUP", "crafted:SUSPICIOUS_STEW"));

        register(Challenge.of("crafting_storage_builder", "Storage Builder",
                "Craft 20 storage blocks", CRAFTING, 20, 50,
                "crafted:CHEST", "crafted:BARREL", "crafted:SHULKER_BOX",
                "crafted:WHITE_SHULKER_BOX", "crafted:ENDER_CHEST"));

        register(Challenge.of("crafting_boat_builder", "Boat Builder",
                "Craft 5 boats", CRAFTING, 5, 40,
                "crafted:OAK_BOAT", "crafted:SPRUCE_BOAT", "crafted:BIRCH_BOAT",
                "crafted:JUNGLE_BOAT", "crafted:ACACIA_BOAT", "crafted:DARK_OAK_BOAT",
                "crafted:MANGROVE_BOAT", "crafted:CHERRY_BOAT", "crafted:BAMBOO_RAFT"));

        register(Challenge.of("crafting_bed_maker", "Bed Maker",
                "Craft 3 beds", CRAFTING, 3, 40,
                "crafted:WHITE_BED", "crafted:ORANGE_BED", "crafted:MAGENTA_BED",
                "crafted:LIGHT_BLUE_BED", "crafted:YELLOW_BED", "crafted:LIME_BED",
                "crafted:PINK_BED", "crafted:GRAY_BED", "crafted:LIGHT_GRAY_BED",
                "crafted:CYAN_BED", "crafted:PURPLE_BED", "crafted:BLUE_BED",
                "crafted:BROWN_BED", "crafted:GREEN_BED", "crafted:RED_BED", "crafted:BLACK_BED"));
    }

    // ===== SMELTING CHALLENGES =====
    private void registerSmeltingChallenges() {
        register(Challenge.of("smelting_iron_smelter", "Iron Smelter",
                "Smelt 100 iron ingots", SMELTING, 100, 50,
                "smelted:IRON_INGOT"));

        register(Challenge.of("smelting_gold_smelter", "Gold Smelter",
                "Smelt 50 gold ingots", SMELTING, 50, 75,
                "smelted:GOLD_INGOT"));

        register(Challenge.of("smelting_copper_smelter", "Copper Smelter",
                "Smelt 100 copper ingots", SMELTING, 100, 50,
                "smelted:COPPER_INGOT"));

        register(Challenge.of("smelting_glass_maker", "Glass Maker",
                "Smelt 100 glass", SMELTING, 100, 50,
                "smelted:GLASS"));

        register(Challenge.of("smelting_stone_smelter", "Stone Smelter",
                "Smelt 200 smooth stone", SMELTING, 200, 50,
                "smelted:SMOOTH_STONE", "smelted:STONE"));

        register(Challenge.of("smelting_chef", "Chef",
                "Cook 100 food items", SMELTING, 100, 50,
                "smelted:COOKED_BEEF", "smelted:COOKED_PORKCHOP", "smelted:COOKED_CHICKEN",
                "smelted:COOKED_MUTTON", "smelted:COOKED_RABBIT", "smelted:COOKED_COD",
                "smelted:COOKED_SALMON", "smelted:BAKED_POTATO", "smelted:DRIED_KELP"));

        register(Challenge.of("smelting_brick_maker", "Brick Maker",
                "Smelt 100 bricks", SMELTING, 100, 50,
                "smelted:BRICK", "smelted:NETHER_BRICK"));

        register(Challenge.of("smelting_charcoal_producer", "Charcoal Producer",
                "Smelt 100 charcoal", SMELTING, 100, 50,
                "smelted:CHARCOAL"));

        register(Challenge.of("smelting_clay_hardener", "Clay Hardener",
                "Smelt 64 terracotta", SMELTING, 64, 50,
                "smelted:TERRACOTTA"));

        register(Challenge.of("smelting_netherite_upgrader", "Netherite Upgrader",
                "Smelt 4 netherite scraps", SMELTING, 4, 200,
                "smelted:NETHERITE_SCRAP"));
    }

    // ===== EXPLORATION CHALLENGES =====
    private void registerExplorationChallenges() {
        register(Challenge.of("exploration_walker", "Walker",
                "Walk 5000 blocks", EXPLORATION, 5000, 50,
                "distance:WALK"));

        register(Challenge.of("exploration_sprinter", "Sprinter",
                "Sprint 3000 blocks", EXPLORATION, 3000, 60,
                "distance:SPRINT"));

        register(Challenge.of("exploration_swimmer", "Swimmer",
                "Swim 1000 blocks", EXPLORATION, 1000, 75,
                "distance:SWIM"));

        register(Challenge.of("exploration_climber", "Climber",
                "Climb 500 blocks", EXPLORATION, 500, 60,
                "distance:CLIMB"));

        register(Challenge.of("exploration_aviator", "Aviator",
                "Fly 5000 blocks with elytra", EXPLORATION, 5000, 100,
                "distance:ELYTRA"));

        register(Challenge.of("exploration_sailor", "Sailor",
                "Travel 2000 blocks by boat", EXPLORATION, 2000, 60,
                "distance:BOAT"));

        register(Challenge.of("exploration_rider", "Rider",
                "Travel 2000 blocks on horseback", EXPLORATION, 2000, 60,
                "distance:HORSE"));

        register(Challenge.of("exploration_minecart_rider", "Minecart Rider",
                "Travel 1000 blocks by minecart", EXPLORATION, 1000, 60,
                "distance:MINECART"));

        register(Challenge.of("exploration_pig_rider", "Pig Rider",
                "Travel 500 blocks on a pig", EXPLORATION, 500, 75,
                "distance:PIG"));

        register(Challenge.of("exploration_biome_discoverer", "Biome Discoverer",
                "Visit 10 different biomes", EXPLORATION, 10, 100,
                "biomes_visited"));

        register(Challenge.of("exploration_structure_finder", "Structure Finder",
                "Discover 5 structures", EXPLORATION, 5, 100,
                "structures_found"));

        register(Challenge.of("exploration_treasure_hunter", "Treasure Hunter",
                "Open 5 loot chests", EXPLORATION, 5, 75,
                "loot_chests_opened"));
    }

    // ===== TIME CHALLENGES =====
    private void registerTimeChallenges() {
        register(Challenge.of("time_sunrise_watcher", "Sunrise Watcher",
                "Witness 5 sunrises in the overworld", TIME, 5, 75,
                "sunrises_witnessed"));

        register(Challenge.of("time_day_survivor", "Day Survivor",
                "Survive through 7 full day/night cycles", TIME, 7, 100,
                "days_survived"));

        register(Challenge.of("time_night_owl", "Night Owl",
                "Be online during 5 full nights", TIME, 5, 80,
                "nights_survived"));
    }
}
