package sh.joey.mc.day;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sends a themed message to players at the start of each Minecraft day.
 * Uses a mix of static messages, procedural templates, and context-aware messages.
 */
public final class DayMessageProvider implements Listener {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.GOLD)
            .append(Component.text("\u2600").color(NamedTextColor.YELLOW)) // ☀
            .append(Component.text("] ").color(NamedTextColor.GOLD));

    private final JavaPlugin plugin;
    private final Random random = ThreadLocalRandom.current();

    // Track which worlds we've already sent messages for this day
    private final Map<UUID, Long> lastDayMessageTime = new HashMap<>();

    public DayMessageProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startDayDetection();
    }

    private void startDayDetection() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (World world : Bukkit.getWorlds()) {
                if (world.getEnvironment() != World.Environment.NORMAL) continue;

                long time = world.getTime();
                // Day starts at time 0, check if we're in the first 100 ticks of the day
                if (time >= 0 && time < 100) {
                    long dayNumber = world.getFullTime() / 24000;
                    Long lastDay = lastDayMessageTime.get(world.getUID());

                    if (lastDay == null || lastDay < dayNumber) {
                        lastDayMessageTime.put(world.getUID(), dayNumber);
                        sendDayMessages(world);
                    }
                }
            }
        }, 20L, 20L); // Check every second
    }

    private void sendDayMessages(World world) {
        for (Player player : world.getPlayers()) {
            String message = generateMessage(player);
            player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.WHITE)));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Clean up tracking for worlds the player might have been in
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // Could send a message when entering a new world during its dawn
    }

    private String generateMessage(Player player) {
        // Weighted random selection: 40% static, 30% procedural, 30% context
        int roll = random.nextInt(100);

        if (roll < 30) {
            // Try context-aware first
            String contextMessage = getContextMessage(player);
            if (contextMessage != null) {
                return contextMessage;
            }
            // Fallback to static
            return getStaticMessage();
        } else if (roll < 60) {
            return getProceduralMessage();
        } else {
            return getStaticMessage();
        }
    }

    // ========================================
    // STATIC MESSAGES (~50 messages)
    // ========================================

    private static final List<String> STATIC_MESSAGES = List.of(
            // Classic greetings
            "The sun rises. Time to punch some trees.",
            "A new day dawns. The diamonds aren't going to mine themselves.",
            "Good morning! Remember: never dig straight down.",
            "Rise and shine! The creepers are sleeping... for now.",
            "Another beautiful day in a world made entirely of cubes.",
            "The sun peeks over the horizon. Adventure awaits!",
            "Dawn breaks. The mobs retreat to their caves.",
            "A fresh start. Yesterday's creeper explosion is behind you.",

            // Fortune cookie style
            "Today's fortune: You will find what you seek... in the last chest you check.",
            "Today's fortune: A skeleton will miss you today. Literally.",
            "Today's fortune: Dig with confidence, but not straight down.",
            "Today's fortune: The gravel remembers. Tread carefully.",
            "Today's fortune: Your lucky ore today is... coal. Sorry.",
            "Today's fortune: A villager will offer you something useless for 64 emeralds.",
            "Today's fortune: The Ender Dragon thinks about you sometimes.",
            "Today's fortune: Trust your instincts. Except near lava.",
            "Today's fortune: Today you will place exactly 347 blocks. Give or take.",
            "Today's fortune: A wandering trader approaches. His llamas judge you.",

            // Wise sayings
            "A wise villager once said: 'Hmm.'",
            "Ancient proverb: The torch you don't place is the spawner you didn't see.",
            "As the old miners say: 'Fortune III or go home.'",
            "Remember the old saying: 'A creeper in the mine is worth two in the base.'",
            "The elders teach: 'Always bring a water bucket.'",
            "Words of wisdom: The best time to build a roof was before the phantoms.",
            "The ancient builders knew: Symmetry is optional, vibes are mandatory.",

            // Observations
            "Another day, another 64 cobblestone.",
            "The chickens are plotting something. Stay vigilant.",
            "Somewhere, a chest is waiting to be organized. Not today though.",
            "The villagers are already trading. Capitalism never sleeps.",
            "A zombie burned in the sunrise. Nature is healing.",
            "The iron golem stands watch. Silently. Judging.",
            "Your bed is exactly where you left it. Probably.",
            "The furnaces hunger for fuel. Feed them.",
            "Somewhere, a creeper is practicing its sneak attack.",
            "The sheep have multiplied overnight. Again.",

            // Motivational
            "Every block you place is a step toward greatness.",
            "Today could be the day you finally find that stronghold.",
            "Dream big. Build bigger.",
            "You've survived this long. That's actually impressive.",
            "The world is your canvas. And it's made of blocks.",
            "Legends aren't born. They're crafted. At a crafting table.",
            "Your base isn't ugly. It has character.",
            "Embrace the grind. The diamonds are worth it.",

            // Humorous
            "Plot twist: The real treasure was the cobblestone we mined along the way.",
            "Fun fact: Endermen just want to redecorate. Aggressively.",
            "Remember: It's not hoarding if you organize it nicely.",
            "Technically, every day is a survival achievement.",
            "The void sends its regards. Stay away from the edge.",
            "Pro tip: Beds explode in the Nether. Don't ask how we know.",
            "Creepers aren't evil. They just want a hug. A very explosive hug.",
            "If at first you don't succeed, respawn and try again.",

            // Minecraft references
            "The cave sounds were just the wind. Definitely just the wind.",
            "Herobrine was not removed in this update.",
            "Achievement unlocked: Survived another night!",
            "Loading new day... Please wait... Just kidding, it's instant.",

            // Existential
            "Why do we mine? To craft. Why do we craft? To mine better.",
            "In a world of infinite blocks, what will you build today?",
            "The sun rises in the east. Or is it the west? Blocks are confusing.",
            "Another day in paradise. Cubic paradise."
    );

    private String getStaticMessage() {
        return STATIC_MESSAGES.get(random.nextInt(STATIC_MESSAGES.size()));
    }

    // ========================================
    // PROCEDURAL TEMPLATES (~30 templates with word banks)
    // ========================================

    private String getProceduralMessage() {
        int template = random.nextInt(15);

        return switch (template) {
            case 0 -> "Today feels like a good day to " + pick(ACTIVITIES) + ".";
            case 1 -> "The ancient texts recommend " + pick(ADVICE) + ".";
            case 2 -> "A " + pick(ADJECTIVES) + " " + pick(DISCOVERIES) + " awaits you today.";
            case 3 -> "The spirits whisper of " + pick(WHISPERS) + ".";
            case 4 -> "Legends speak of a " + pick(ADJECTIVES) + " " + pick(TREASURES) + " nearby.";
            case 5 -> "Perhaps today you'll finally " + pick(GOALS) + ".";
            case 6 -> "The " + pick(MOBS) + " slept well. You should be concerned.";
            case 7 -> "Your " + pick(ITEMS) + " yearns for adventure.";
            case 8 -> "May your " + pick(ITEMS) + " strike true and your " + pick(TOOLS) + " never break.";
            case 9 -> "Somewhere, a " + pick(MOBS) + " is thinking about you.";
            case 10 -> "Today's agenda: " + pick(ACTIVITIES) + ", " + pick(ACTIVITIES) + ", and maybe some " + pick(ACTIVITIES) + ".";
            case 11 -> "The " + pick(BIOMES) + " calls to you. Will you answer?";
            case 12 -> "A " + pick(MOBS) + " and a " + pick(MOBS) + " walk into a bar. The bar is your base. Run.";
            case 13 -> "If you listen closely, you can hear the " + pick(SOUNDS) + ".";
            case 14 -> "The " + pick(ORES) + " is out there. You can feel it.";
            default -> getStaticMessage();
        };
    }

    private String pick(List<String> list) {
        return list.get(random.nextInt(list.size()));
    }

    private static final List<String> ACTIVITIES = List.of(
            "explore some caves",
            "go fishing",
            "build something ridiculous",
            "reorganize your chests",
            "hunt for diamonds",
            "tame a wolf",
            "start a farm",
            "dig a very deep hole",
            "build a bridge to nowhere",
            "collect every flower type",
            "find a village",
            "map the nearby terrain",
            "breed some animals",
            "enchant your gear",
            "fight a witch",
            "raid a dungeon",
            "build an automatic farm",
            "explore a shipwreck",
            "trade with villagers",
            "collect some honey",
            "mine until your pickaxe breaks",
            "build a monument to yourself",
            "terraform something",
            "finally organize that storage room"
    );

    private static final List<String> ADVICE = List.of(
            "bringing extra torches",
            "avoiding creepers",
            "eating a golden apple",
            "sleeping before the phantoms come",
            "never trusting gravel",
            "keeping a water bucket handy",
            "respecting the iron golem",
            "not punching the zombie piglin",
            "staying away from the edge",
            "mining at Y level -59",
            "bringing a shield",
            "labeling your chests",
            "backing up your world",
            "being kind to villagers",
            "watching for silverfish",
            "carrying a spare pickaxe"
    );

    private static final List<String> ADJECTIVES = List.of(
            "mysterious",
            "hidden",
            "forgotten",
            "ancient",
            "legendary",
            "suspicious",
            "glittering",
            "dangerous",
            "peculiar",
            "magnificent",
            "humble",
            "cursed",
            "blessed",
            "enormous",
            "tiny",
            "explosive"
    );

    private static final List<String> DISCOVERIES = List.of(
            "treasure",
            "cave system",
            "village",
            "stronghold",
            "adventure",
            "dungeon",
            "mineshaft",
            "temple",
            "monument",
            "outpost",
            "shipwreck",
            "ruined portal",
            "geode",
            "lush cave"
    );

    private static final List<String> WHISPERS = List.of(
            "diamonds in the deep",
            "a stronghold nearby",
            "wandering traders approaching",
            "ancient debris in the Nether",
            "a woodland mansion to the north",
            "buried treasure on the coast",
            "an ocean monument in the depths",
            "emeralds in the mountains",
            "a witch's hut in the swamp",
            "secrets beneath your feet"
    );

    private static final List<String> TREASURES = List.of(
            "diamond vein",
            "enchanted book",
            "golden apple",
            "treasure chest",
            "totem of undying",
            "elytra",
            "trident",
            "beacon",
            "dragon egg",
            "music disc"
    );

    private static final List<String> GOALS = List.of(
            "find that elusive netherite",
            "complete your beacon",
            "finish your mega base",
            "defeat the Ender Dragon",
            "get a full set of diamond armor",
            "max out your enchantments",
            "build that redstone contraption",
            "create the perfect villager trading hall",
            "explore every biome",
            "collect all the music discs",
            "build an iron farm",
            "tame every animal type",
            "cure a zombie villager"
    );

    private static final List<String> MOBS = List.of(
            "creeper",
            "skeleton",
            "zombie",
            "spider",
            "enderman",
            "witch",
            "phantom",
            "drowned",
            "pillager",
            "warden",
            "iron golem",
            "wandering trader",
            "villager",
            "wolf",
            "cat",
            "fox",
            "bee"
    );

    private static final List<String> ITEMS = List.of(
            "sword",
            "pickaxe",
            "bow",
            "shield",
            "axe",
            "trident",
            "crossbow",
            "fishing rod",
            "shovel",
            "hoe"
    );

    private static final List<String> TOOLS = List.of(
            "pickaxe",
            "shovel",
            "axe",
            "hoe",
            "shears",
            "flint and steel",
            "fishing rod"
    );

    private static final List<String> BIOMES = List.of(
            "dark forest",
            "desert",
            "jungle",
            "ocean",
            "mountains",
            "swamp",
            "mushroom island",
            "savanna",
            "tundra",
            "cherry grove",
            "deep dark",
            "lush caves",
            "badlands"
    );

    private static final List<String> SOUNDS = List.of(
            "distant cave noises",
            "zombies groaning underground",
            "water dripping in a cave",
            "lava bubbling below",
            "the hum of the void",
            "villagers trading",
            "bees buzzing contentedly",
            "wolves howling in the distance",
            "thunder on the horizon",
            "the silence before a creeper"
    );

    private static final List<String> ORES = List.of(
            "diamond",
            "emerald",
            "ancient debris",
            "gold",
            "lapis lazuli",
            "redstone",
            "iron",
            "copper",
            "coal"
    );

    // ========================================
    // CONTEXT-AWARE MESSAGES
    // ========================================

    private String getContextMessage(Player player) {
        World world = player.getWorld();
        List<String> candidates = new ArrayList<>();

        // Moon phase messages (check what tonight will be)
        int moonPhase = (int) ((world.getFullTime() / 24000) % 8);
        switch (moonPhase) {
            case 0 -> candidates.add("Full moon tonight. The undead will be restless.");
            case 4 -> candidates.add("New moon tonight. Darkness will be absolute.");
        }

        // Weather messages
        if (world.hasStorm()) {
            if (world.isThundering()) {
                candidates.add("Thunder rumbles across the land. The charged creepers stir.");
                candidates.add("A storm rages. Perhaps stay indoors today?");
                candidates.add("Lightning crackles in the sky. Free mob heads, anyone?");
            } else {
                candidates.add("Rain falls gently. A good day to tend the crops.");
                candidates.add("The rain begins. Perfect fishing weather!");
                candidates.add("Gray skies today. The zombies won't burn, so stay alert.");
            }
        } else {
            candidates.add("Clear skies ahead. The sun will keep you safe.");
            candidates.add("Beautiful weather today. The mobs will burn nicely.");
        }

        // Biome-specific messages
        var biome = world.getBiome(player.getLocation());
        String biomeName = biome.getKey().getKey().toLowerCase();

        if (biomeName.contains("desert")) {
            candidates.add("Another scorching day in the desert. The cacti judge you silently.");
            candidates.add("Sand stretches in every direction. Watch out for husks.");
        } else if (biomeName.contains("ocean")) {
            candidates.add("The sea stretches before you. Adventure awaits beneath the waves.");
            candidates.add("Ocean breeze fills the air. Perhaps a shipwreck hunt?");
        } else if (biomeName.contains("jungle")) {
            candidates.add("The jungle awakens. Parrots chatter, ocelots lurk.");
            candidates.add("Vines hang heavy with dew. A temple hides somewhere nearby.");
        } else if (biomeName.contains("swamp")) {
            candidates.add("Mist rises from the swamp. Witches brew in their huts.");
            candidates.add("The swamp gurgles. Slimes bounce in the murky waters.");
        } else if (biomeName.contains("mountain") || biomeName.contains("peak")) {
            candidates.add("The mountain air is crisp. Goats leap on the cliffs above.");
            candidates.add("High altitude today. Emeralds hide in these peaks.");
        } else if (biomeName.contains("snow") || biomeName.contains("ice") || biomeName.contains("frozen")) {
            candidates.add("Frost clings to everything. Bundle up!");
            candidates.add("Snow blankets the land. Strays lurk in the cold.");
        } else if (biomeName.contains("mushroom")) {
            candidates.add("Mycelium squishes underfoot. No hostile mobs here, just vibes.");
            candidates.add("The mooshrooms watch you with knowing eyes.");
        } else if (biomeName.contains("dark_forest")) {
            candidates.add("The dark forest looms. Woodland mansions hide the vindictive.");
            candidates.add("Little light penetrates the canopy. Mobs may spawn even now.");
        } else if (biomeName.contains("cherry")) {
            candidates.add("Cherry blossoms drift on the breeze. Peaceful.");
            candidates.add("Pink petals carpet the ground. A beautiful day.");
        } else if (biomeName.contains("badlands") || biomeName.contains("mesa")) {
            candidates.add("Terracotta towers catch the morning light. Gold hides in these hills.");
            candidates.add("The badlands bake under the sun. Mineshafts thread through the terrain.");
        } else if (biomeName.contains("deep_dark")) {
            candidates.add("Sculk spreads in the darkness. The Warden listens.");
            candidates.add("You wake in the deep dark. Question your life choices.");
        } else if (biomeName.contains("lush")) {
            candidates.add("Axolotls splash in the pools. The lush caves welcome you.");
            candidates.add("Glow berries illuminate the cavern. Nature thrives below.");
        }

        // World-specific messages
        if (world.getEnvironment() == World.Environment.NETHER) {
            candidates.add("Another day in the Nether. Try not to catch fire.");
            candidates.add("The Nether's heat is unrelenting. At least ghasts provide ambiance.");
            candidates.add("Netherrack stretches endlessly. Ancient debris hides in the depths.");
        } else if (world.getEnvironment() == World.Environment.THE_END) {
            candidates.add("The void hums. The dragon remembers.");
            candidates.add("End stone beneath your feet. Don't look down.");
            candidates.add("Endermen wander aimlessly. Don't make eye contact.");
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(random.nextInt(candidates.size()));
    }
}
