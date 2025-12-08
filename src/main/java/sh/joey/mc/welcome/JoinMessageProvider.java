package sh.joey.mc.welcome;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Sends a themed welcome message to players when they join the server.
 */
public final class JoinMessageProvider implements Listener {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.GOLD)
            .append(Component.text("\u2605").color(NamedTextColor.YELLOW)) // ★
            .append(Component.text("] ").color(NamedTextColor.GOLD));

    private final Random random = ThreadLocalRandom.current();

    public JoinMessageProvider(JavaPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String message = generateMessage(player);
        player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.WHITE)));
    }

    private String generateMessage(Player player) {
        // 40% static, 30% procedural, 30% context-aware
        int roll = random.nextInt(100);

        if (roll < 30) {
            String contextMessage = getContextMessage(player);
            if (contextMessage != null) {
                return contextMessage;
            }
            return getStaticMessage(player);
        } else if (roll < 60) {
            return getProceduralMessage(player);
        } else {
            return getStaticMessage(player);
        }
    }

    // ========================================
    // STATIC MESSAGES
    // ========================================

    private static final List<String> STATIC_GREETINGS = List.of(
            // Classic welcomes
            "Welcome back! The blocks missed you.",
            "You've returned! The creepers have been waiting.",
            "Ah, you're here! Adventure awaits.",
            "Welcome! Your pickaxe is right where you left it.",
            "You're back! The villagers were getting worried.",
            "Welcome home, builder of worlds.",
            "The server welcomes you with open arms. And lava. Watch the lava.",
            "You've arrived! Let the mining commence.",
            "Welcome! Today's forecast: blocks with a chance of explosions.",
            "Greetings, traveler! Your inventory awaits.",

            // Humorous
            "Oh good, you're here. The sheep were starting a revolution.",
            "Welcome! The zombies have prepared a welcome party. Run.",
            "You're back! Quick, the diamonds are escaping!",
            "Ah, fresh meat— I mean, welcome friend!",
            "The prophecy foretold your return. Also, we're out of cobblestone.",
            "Welcome! Your bed is definitely not surrounded by creepers.",
            "You've logged in! Achievement unlocked: Showing Up.",
            "Greetings! The chickens have multiplied in your absence. Again.",
            "Welcome back! Nothing exploded while you were gone. Mostly.",
            "The server acknowledges your existence. Congratulations!",

            // Motivational
            "A new session begins. What will you create today?",
            "Welcome! Every block placed is a step toward greatness.",
            "You're here! The world is your canvas.",
            "Another chance to build something amazing. Let's go!",
            "Welcome back, legend. Time to make history.",
            "The server is better with you in it.",
            "Ready to turn imagination into blocks? Let's do this.",
            "Welcome! Your potential is as infinite as the world border.",

            // Mysterious
            "The ancient spirits acknowledge your presence...",
            "Welcome. The void whispers your name.",
            "You've entered the realm. Tread carefully.",
            "The world shifts to accommodate your return.",
            "Welcome, wanderer. Your destiny awaits in the depths.",

            // Casual
            "Hey! Good to see you.",
            "Welcome back! Grab your tools, let's go.",
            "You're here! Time to punch some trees.",
            "Another day, another adventure. Welcome!",
            "Let's gooo! Welcome back!",

            // References
            "Welcome! Remember: the cake is a lie, but the diamonds are real.",
            "You've respawned in the overworld. Welcome!",
            "Loading player... Done! Welcome!",
            "Player joined the game. That's you. Welcome!",
            "Herobrine has not removed your welcome message."
    );

    private String getStaticMessage(Player player) {
        String name = player.getName();
        List<String> personalized = List.of(
                "Welcome, " + name + "! The server has been expecting you.",
                name + " has arrived! Let the adventures begin.",
                "All hail " + name + ", builder of worlds!",
                name + "! Your presence graces us once more.",
                "The legendary " + name + " returns!"
        );

        // 30% chance of personalized message
        if (random.nextInt(100) < 30) {
            return personalized.get(random.nextInt(personalized.size()));
        }
        return STATIC_GREETINGS.get(random.nextInt(STATIC_GREETINGS.size()));
    }

    // ========================================
    // PROCEDURAL MESSAGES
    // ========================================

    private String getProceduralMessage(Player player) {
        int template = random.nextInt(12);

        return switch (template) {
            case 0 -> "Welcome! Today feels like a good day to " + pick(ACTIVITIES) + ".";
            case 1 -> "You're back! The " + pick(MOBS) + " sends its regards.";
            case 2 -> "Welcome! May your " + pick(TOOLS) + " never break.";
            case 3 -> "Greetings! The " + pick(BIOMES) + " awaits your exploration.";
            case 4 -> "You've arrived! Time to hunt for " + pick(TREASURES) + ".";
            case 5 -> "Welcome back! A " + pick(ADJECTIVES) + " adventure awaits.";
            case 6 -> "The " + pick(MOBS) + " heard you were coming. It's " + pick(EMOTIONS) + ".";
            case 7 -> "Welcome! Your " + pick(TOOLS) + " yearns for " + pick(MATERIALS) + ".";
            case 8 -> "Greetings, seeker of " + pick(TREASURES) + "!";
            case 9 -> "You return! The " + pick(STRUCTURES) + " still stands. Probably.";
            case 10 -> "Welcome! The spirits of " + pick(BIOMES) + " bless your journey.";
            case 11 -> player.getName() + " arrives, ready to " + pick(ACTIVITIES) + "!";
            default -> getStaticMessage(player);
        };
    }

    private String pick(List<String> list) {
        return list.get(random.nextInt(list.size()));
    }

    private static final List<String> ACTIVITIES = List.of(
            "mine diamonds", "build a castle", "explore caves", "fight a dragon",
            "tame wolves", "trade with villagers", "raid a dungeon", "go fishing",
            "plant a forest", "dig to bedrock", "find ancient debris", "build a farm",
            "enchant gear", "brew potions", "map the world", "collect all the flowers"
    );

    private static final List<String> MOBS = List.of(
            "creeper", "skeleton", "zombie", "enderman", "iron golem", "villager",
            "wandering trader", "wolf", "cat", "bee", "fox", "axolotl", "warden"
    );

    private static final List<String> TOOLS = List.of(
            "pickaxe", "sword", "axe", "shovel", "bow", "trident", "shield", "fishing rod"
    );

    private static final List<String> BIOMES = List.of(
            "dark forest", "desert", "jungle", "ocean", "mountains", "swamp",
            "cherry grove", "deep dark", "lush caves", "mushroom island"
    );

    private static final List<String> TREASURES = List.of(
            "diamonds", "ancient debris", "emeralds", "enchanted books",
            "golden apples", "totems", "elytra", "tridents", "music discs"
    );

    private static final List<String> ADJECTIVES = List.of(
            "legendary", "mysterious", "epic", "glorious", "perilous",
            "magnificent", "humble", "chaotic", "peaceful", "explosive"
    );

    private static final List<String> EMOTIONS = List.of(
            "excited", "nervous", "plotting something", "hiding", "watching",
            "indifferent", "surprisingly friendly", "suspiciously quiet"
    );

    private static final List<String> MATERIALS = List.of(
            "diamonds", "gold", "iron", "wood", "stone", "netherite", "obsidian"
    );

    private static final List<String> STRUCTURES = List.of(
            "base", "farm", "tower", "underground lair", "treehouse", "castle",
            "villager trading hall", "mob grinder", "storage room"
    );

    // ========================================
    // CONTEXT-AWARE MESSAGES
    // ========================================

    private String getContextMessage(Player player) {
        World world = player.getWorld();
        List<String> candidates = new ArrayList<>();

        // Time-based messages
        long time = world.getTime();
        if (time >= 0 && time < 6000) {
            candidates.add("Good morning! The sun is young and so is your adventure.");
            candidates.add("You join us at dawn. Perfect timing!");
        } else if (time >= 6000 && time < 12000) {
            candidates.add("Afternoon! The sun is high, perfect for building.");
            candidates.add("You've arrived mid-day. The mobs slumber for now.");
        } else if (time >= 12000 && time < 18000) {
            candidates.add("Evening approaches. The monsters stir...");
            candidates.add("Sunset welcomes you. Find shelter soon!");
            candidates.add("You join as dusk falls. Hope you brought torches.");
        } else {
            candidates.add("You brave the night! The monsters are active.");
            candidates.add("Joining at night? Bold. Very bold.");
            candidates.add("The moon watches your arrival. So do the mobs.");
            candidates.add("Nighttime login detected. Danger level: elevated.");
        }

        // Weather-based
        if (world.hasStorm()) {
            if (world.isThundering()) {
                candidates.add("You arrive amid thunder! Dramatic entrance.");
                candidates.add("Lightning crackles to herald your return!");
            } else {
                candidates.add("Rain greets your arrival. Cozy vibes.");
                candidates.add("You join during a storm. Perfect fishing weather!");
            }
        }

        // Dimension-based
        if (world.getEnvironment() == World.Environment.NETHER) {
            candidates.add("You spawn in the Nether?! Brave choice.");
            candidates.add("Welcome to the hot place. Watch your step.");
        } else if (world.getEnvironment() == World.Environment.THE_END) {
            candidates.add("You return to the End. The dragon remembers you.");
            candidates.add("The void greets you. Don't look down.");
        }

        // Player state
        if (player.getHealth() < 10) {
            candidates.add("Welcome back! You look a bit... damaged. Eat something!");
        }
        if (player.getFoodLevel() < 10) {
            candidates.add("Welcome! Your stomach growls. Time to eat!");
        }

        // First join ever
        if (!player.hasPlayedBefore()) {
            return "Welcome to the server, " + player.getName() + "! Your adventure begins now!";
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(random.nextInt(candidates.size()));
    }
}
