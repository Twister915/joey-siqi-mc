package sh.joey.mc.messages;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static sh.joey.mc.messages.WordBanks.*;

/**
 * Centralized message generation system used by DayMessageProvider, JoinMessageProvider,
 * and ServerPingProvider. Combines static messages, procedural templates, and context-aware
 * generation with shared word banks.
 */
public final class MessageGenerator {

    private static final Random random = new Random();

    private MessageGenerator() {}

    // ========================================
    // CONTEXT PROVIDER REGISTRY
    // ========================================

    /**
     * Represents a context provider that can generate messages based on player/world state.
     * The registry pattern ensures that adding a new context type automatically includes it
     * in both message generation and the debug command.
     */
    public record ContextProvider(
            String category,
            int priority,
            boolean biomeOnly,
            @Nullable MessageType onlyFor,
            ContextMethod method
    ) {
        @FunctionalInterface
        public interface ContextMethod {
            void addMessages(Player player, World world, List<String> candidates, MessageType type, String displayName);
        }
    }

    /**
     * Registry of all context providers. Order matters - providers are evaluated in list order.
     * Add new context types here and they will automatically appear in both message generation
     * and the debug command.
     */
    private static final List<ContextProvider> CONTEXT_PROVIDERS = List.of(
            // HIGH-VARIETY CONTEXTS (prioritized)
            new ContextProvider("High-Variety Contexts", 10, false, null,
                    (p, w, c, t, d) -> addNearbyEntityMessages(p, w, c)),
            new ContextProvider("High-Variety Contexts", 10, false, null,
                    (p, w, c, t, d) -> addInventoryStateMessages(p, c)),
            new ContextProvider("High-Variety Contexts", 10, false, null,
                    (p, w, c, t, d) -> addNearbyBlockMessages(p, w, c)),

            // POTION & BUFF STATE
            new ContextProvider("Potion & Buff State", 20, false, null,
                    (p, w, c, t, d) -> addPotionEffectMessages(p, c)),

            // EQUIPMENT STATE
            new ContextProvider("Equipment State", 30, false, null,
                    (p, w, c, t, d) -> addDurabilityMessages(p, c)),
            new ContextProvider("Equipment State", 30, false, null,
                    (p, w, c, t, d) -> addCombatReadyMessages(p, c)),

            // PLAYER STATE
            new ContextProvider("Player State", 40, false, null,
                    (p, w, c, t, d) -> addHealthMessages(p, c)),
            new ContextProvider("Player State", 40, false, null,
                    (p, w, c, t, d) -> addHungerMessages(p, c)),
            new ContextProvider("Player State", 40, false, null,
                    (p, w, c, t, d) -> addSaturationMessages(p, c)),
            new ContextProvider("Player State", 40, false, null,
                    (p, w, c, t, d) -> addExperienceMessages(p, c)),
            new ContextProvider("Player State", 40, false, null,
                    (p, w, c, t, d) -> addArmorMessages(p, c)),
            new ContextProvider("Player State", 40, false, null,
                    (p, w, c, t, d) -> addHeldItemMessages(p, c, t)),

            // INVENTORY SPECIAL ITEMS
            new ContextProvider("Inventory Special Items", 50, false, null,
                    (p, w, c, t, d) -> addBuildingMaterialMessages(p, c)),
            new ContextProvider("Inventory Special Items", 50, false, null,
                    (p, w, c, t, d) -> addRareDropMessages(p, c)),
            new ContextProvider("Inventory Special Items", 50, false, null,
                    (p, w, c, t, d) -> addBrewingIngredientMessages(p, c)),
            new ContextProvider("Inventory Special Items", 50, false, null,
                    (p, w, c, t, d) -> addMusicDiscMessages(p, c)),

            // LOCATION STATE
            new ContextProvider("Location State", 60, false, null,
                    (p, w, c, t, d) -> addYLevelMessages(p, c)),
            new ContextProvider("Location State", 60, false, null,
                    (p, w, c, t, d) -> addUndergroundMessages(p, w, c, t)),
            new ContextProvider("Location State", 60, false, null,
                    (p, w, c, t, d) -> addWaterMessages(p, c, t)),
            new ContextProvider("Location State", 60, false, null,
                    (p, w, c, t, d) -> addVehicleMessages(p, c, t)),
            new ContextProvider("Location State", 60, false, null,
                    (p, w, c, t, d) -> addBedStatusMessages(p, c)),

            // ENVIRONMENTAL HAZARDS/FEATURES
            new ContextProvider("Environmental", 70, false, null,
                    (p, w, c, t, d) -> addNearLavaMessages(p, w, c)),
            new ContextProvider("Environmental", 70, false, null,
                    (p, w, c, t, d) -> addNearSpawnerMessages(p, w, c)),
            new ContextProvider("Environmental", 70, false, null,
                    (p, w, c, t, d) -> addNearPortalMessages(p, w, c)),
            new ContextProvider("Environmental", 70, false, null,
                    (p, w, c, t, d) -> addLightLevelMessages(p, w, c)),
            new ContextProvider("Environmental", 70, false, null,
                    (p, w, c, t, d) -> addFlowerFieldMessages(p, w, c)),

            // WORLD STATE (DAY only for milestones/difficulty/moon)
            new ContextProvider("World State", 80, false, MessageType.DAY,
                    (p, w, c, t, d) -> addDayMilestoneMessages(w, c)),
            new ContextProvider("World State", 80, false, MessageType.DAY,
                    (p, w, c, t, d) -> addDifficultyMessages(w, c)),
            new ContextProvider("World State", 80, false, MessageType.DAY,
                    (p, w, c, t, d) -> addMoonPhaseMessages(w, c)),
            new ContextProvider("World State", 80, false, null,
                    (p, w, c, t, d) -> addWeatherMessages(w, c, t)),

            // DIMENSION
            new ContextProvider("Dimension", 85, false, null,
                    (p, w, c, t, d) -> addDimensionMessages(p, w, c, t)),

            // SPECIAL SITUATIONS
            new ContextProvider("Special Situations", 90, false, null,
                    (p, w, c, t, d) -> addSpecialSituationMessages(p, c, t)),

            // JOIN-SPECIFIC (JOIN only)
            new ContextProvider("Join-Specific", 95, false, MessageType.JOIN,
                    (p, w, c, t, d) -> addJoinSpecificMessages(p, w, c, d)),

            // BIOME (fallback only - used when no other contexts match)
            new ContextProvider("Biome", 900, true, null,
                    (p, w, c, t, d) -> addBiomeMessages(p, w, c))
    );

    /**
     * Returns the list of all context providers for use by debug commands.
     */
    public static List<ContextProvider> getContextProviders() {
        return CONTEXT_PROVIDERS;
    }

    // ========================================
    // MAIN GENERATION METHODS
    // ========================================

    /**
     * Generates a message for the start of a Minecraft day.
     * Uses full context (player state, location, world state).
     *
     * @deprecated Use {@link #generateDayMessage(Player, String)} instead for display name support.
     */
    @Deprecated
    public static String generateDayMessage(Player player) {
        return generateDayMessage(player, player.getName());
    }

    /**
     * Generates a message for the start of a Minecraft day.
     * Uses full context (player state, location, world state).
     *
     * @param player the player to generate a message for
     * @param displayName the player's display name (nickname or username)
     */
    public static String generateDayMessage(Player player, String displayName) {
        int roll = random.nextInt(100);

        // 35% context, 25% procedural, 40% static
        if (roll < 35) {
            String contextMessage = getPlayerContextMessage(player, MessageType.DAY, displayName);
            if (contextMessage != null) {
                return contextMessage;
            }
            return pickDayMessage();
        } else if (roll < 60) {
            return getProceduralMessage(MessageType.DAY);
        } else {
            return pickDayMessage();
        }
    }

    /**
     * Generates a welcome message for a player joining the server.
     * Uses full context but with a welcoming tone.
     *
     * @deprecated Use {@link #generateJoinMessage(Player, String)} instead for display name support.
     */
    @Deprecated
    public static String generateJoinMessage(Player player) {
        return generateJoinMessage(player, player.getName());
    }

    /**
     * Generates a welcome message for a player joining the server.
     * Uses full context but with a welcoming tone.
     *
     * @param player the player to generate a message for
     * @param displayName the player's display name (nickname or username)
     */
    public static String generateJoinMessage(Player player, String displayName) {
        int roll = random.nextInt(100);

        // 50% context, 20% procedural, 30% static
        if (roll < 50) {
            String contextMessage = getPlayerContextMessage(player, MessageType.JOIN, displayName);
            if (contextMessage != null) {
                return contextMessage;
            }
            return pickJoinMessage(displayName);
        } else if (roll < 70) {
            return getProceduralMessage(MessageType.JOIN);
        } else {
            return pickJoinMessage(displayName);
        }
    }

    /**
     * Generates a short MOTD message for the server list.
     * Uses limited context (world only, no player).
     */
    public static String generateMotdMessage(@Nullable World overworld, int playerCount) {
        // 30% context-aware, 70% static
        if (random.nextInt(100) < 30 && overworld != null) {
            String contextMsg = getWorldContextMessage(overworld, playerCount);
            if (contextMsg != null) {
                return contextMsg;
            }
        }
        return pickMotdMessage();
    }

    /**
     * Message type enum for context-specific generation.
     */
    public enum MessageType {
        DAY,    // Start of Minecraft day
        JOIN,   // Player joining server
        MOTD    // Server list ping
    }

    // ========================================
    // STATIC MESSAGE POOLS
    // ========================================

    private static String pickDayMessage() {
        return pick(DAY_MESSAGES);
    }

    private static String pickJoinMessage(String displayName) {
        // 30% chance of personalized message
        if (random.nextInt(100) < 30) {
            return getPersonalizedGreeting(displayName);
        }
        return pick(JOIN_MESSAGES);
    }

    private static String pickMotdMessage() {
        return pick(MOTD_MESSAGES);
    }

    private static String getPersonalizedGreeting(String displayName) {
        List<String> personalized = List.of(
                "Welcome, " + displayName + "! The server has been expecting you.",
                displayName + " has arrived! Let the adventures begin.",
                "All hail " + displayName + ", builder of worlds!",
                displayName + "! Your presence graces us once more.",
                "The legendary " + displayName + " returns!",
                displayName + " joins the fray!",
                "Look who's here! It's " + displayName + "!",
                displayName + ", the blocks await your command."
        );
        return pick(personalized);
    }

    // ========================================
    // DAY MESSAGES (~150 messages)
    // ========================================

    private static final List<String> DAY_MESSAGES = List.of(
            // Classic greetings
            "The sun rises. Time to punch some trees.",
            "A new day dawns. The diamonds aren't going to mine themselves.",
            "Good morning! Remember: never dig straight down.",
            "Rise and shine! The creepers are sleeping... for now.",
            "Another beautiful day in a world made entirely of cubes.",
            "The sun peeks over the horizon. Adventure awaits!",
            "Dawn breaks. The mobs retreat to their caves.",
            "A fresh start. Yesterday's creeper explosion is behind you.",
            "Morning light spills across the land. Time to build something great.",
            "The rooster crows. Wait, there are no roosters. Only chickens.",
            "Wake up, miner. The ores await.",
            "First light touches the treetops. What will you create today?",
            "The morning mist fades. Your destiny awaits.",
            "Good morning, adventurer. The world hasn't changed. Much.",
            "Sunrise: the only time zombies regret their choices.",
            "A new dawn, a new chance to not fall in lava.",

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
            "Today's fortune: Your next enchantment will be Bane of Arthropods. Again.",
            "Today's fortune: The cave you're about to enter has exactly one diamond. Find it.",
            "Today's fortune: A witch will throw a potion at you. Duck.",
            "Today's fortune: Your favorite tool will break at the worst moment.",
            "Today's fortune: Something good is buried exactly where you won't dig.",
            "Today's fortune: The mob you don't see is the one that gets you.",
            "Today's fortune: An enderman will take a block you actually needed.",
            "Today's fortune: Your farm will finally work today. Probably.",
            "Today's fortune: A creeper is waiting around a corner. Which corner? Yes.",
            "Today's fortune: The loot chest will contain bread. Just bread.",

            // Wise sayings
            "A wise villager once said: 'Hmm.'",
            "Ancient proverb: The torch you don't place is the spawner you didn't see.",
            "As the old miners say: 'Fortune III or go home.'",
            "Remember the old saying: 'A creeper in the mine is worth two in the base.'",
            "The elders teach: 'Always bring a water bucket.'",
            "Words of wisdom: The best time to build a roof was before the phantoms.",
            "The ancient builders knew: Symmetry is optional, vibes are mandatory.",
            "Proverb: The player who hoards never has inventory space.",
            "The wise know: Mending is worth more than gold.",
            "Ancient wisdom: The night is dark and full of phantoms.",
            "As grandmother always said: 'Eat your steak before a boss fight.'",
            "The sages teach: Never leave your furnace mid-smelt.",
            "Old saying: A water bucket is worth a thousand hearts.",
            "The ancients knew: Every stronghold has one too few eyes of ender.",
            "Wisdom from the depths: Light level 7 is not your friend.",
            "The builders' code: Measure twice, place once. Unless it's dirt.",

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
            "The cows stare blankly into the distance. They know something.",
            "A bat flutters past. It contributes nothing, as always.",
            "Your item sorter has jammed again. Classic.",
            "The world border is still exactly where you left it.",
            "A pig wanders aimlessly. Relatable.",
            "The lava pool bubbles menacingly. It remembers your last visit.",
            "Your crops grew overnight. The farmer's life pays off.",
            "A squid is doing squid things. Squiddily.",
            "The End portal frame sits empty. For now.",
            "Your anvil is one repair closer to breaking.",

            // Motivational
            "Every block you place is a step toward greatness.",
            "Today could be the day you finally find that stronghold.",
            "Dream big. Build bigger.",
            "You've survived this long. That's actually impressive.",
            "The world is your canvas. And it's made of blocks.",
            "Legends aren't born. They're crafted. At a crafting table.",
            "Your base isn't ugly. It has character.",
            "Embrace the grind. The diamonds are worth it.",
            "You're not lost. You're exploring aggressively.",
            "The Warden fears your footsteps. Or it should.",
            "Every great build started with a single block.",
            "Today's the day you finally finish that project. Maybe.",
            "The dragon doesn't know what's coming. You. You're coming.",
            "Your inventory management skills are improving. Probably.",
            "The Nether awaits. And you're ready. Mostly.",
            "Believe in yourself. And also in your armor.",

            // Humorous
            "Plot twist: The real treasure was the cobblestone we mined along the way.",
            "Fun fact: Endermen just want to redecorate. Aggressively.",
            "Remember: It's not hoarding if you organize it nicely.",
            "Technically, every day is a survival achievement.",
            "The void sends its regards. Stay away from the edge.",
            "Pro tip: Beds explode in the Nether. Don't ask how we know.",
            "Creepers aren't evil. They just want a hug. A very explosive hug.",
            "If at first you don't succeed, respawn and try again.",
            "Your inventory is full. It's always full. This is the way.",
            "That sound? Just ambient noise. Definitely not a creeper.",
            "Ghasts aren't crying. They're just emotionally complex.",
            "The pufferfish was warned not to inflate. It didn't listen.",
            "Gravel: gravity's favorite prank.",
            "You're not procrastinating. You're strategic waiting.",
            "The cake is not a lie. You just haven't found it yet.",
            "Silverfish: because regular caves weren't annoying enough.",
            "Fun fact: Creepers explode because they care too much.",
            "Your elytra will break mid-flight someday. Not today. Probably.",
            "Magma cubes: the floor is literally them.",
            "Bats: proof that not everything needs a purpose.",

            // Minecraft references
            "The cave sounds were just the wind. Definitely just the wind.",
            "Herobrine was not removed in this update.",
            "Achievement unlocked: Survived another night!",
            "Loading new day... Please wait... Just kidding, it's instant.",
            "This message was brought to you by: Noteblocks.",
            "The disc 11 broke again. Suspicious.",
            "Chunk loading complete. Reality is now rendered.",
            "Game saved. Your progress is preserved. For now.",

            // Existential
            "Why do we mine? To craft. Why do we craft? To mine better.",
            "In a world of infinite blocks, what will you build today?",
            "The sun rises in the east. Or is it the west? Blocks are confusing.",
            "Another day in paradise. Cubic paradise.",
            "Are we the player, or is the player us? Deep thoughts.",
            "The compass always points home. But where is home, really?",
            "We break. We place. We break again. Such is the cycle.",
            "What if villagers see us as the hostile mob?",

            // Cozy
            "Time for tea and terraforming.",
            "The cats are napping in the sun. Join them?",
            "A perfect day to tend the garden and ignore the todo list.",
            "The bees are buzzing. All is well.",
            "Warm bread from the furnace. Simple pleasures.",
            "The fireplace crackles. Even if it's just a campfire.",
            "Rain patters on the roof you finally built. Cozy.",
            "Your dogs are happy to see you. As always.",

            // Redstone and technical
            "The comparator clicks. Your contraption lives.",
            "Somewhere, a hopper is clogged. As is tradition.",
            "The observer observes. The piston pushes. Satisfaction.",
            "Redstone dust trails across your floor like electric spaghetti.",
            "Today's project: a door that requires 47 components to open.",
            "The flying machine flies. You are basically an engineer now.",
            "Your item sorter works. You don't know why, but it works.",
            "The T flip-flop flips. Or flops. You're not sure which.",

            // Enchantments
            "May Fortune III bless your mining today.",
            "Silk Touch: for when you want the block, not the drops.",
            "Mending: the enchantment that makes tools immortal. Almost.",
            "Curse of Vanishing: because losing items wasn't painful enough.",
            "Bane of Arthropods V. For when you REALLY hate spiders.",
            "Thorns III: the enchantment of 'no, YOU take damage.'",
            "Feather Falling IV: trust issues with heights, solved.",
            "Infinity on a bow is nice. Mending is nicer. You can't have both.",

            // Potions and brewing
            "The brewing stand bubbles. Alchemy awaits.",
            "Fire Resistance: for when the Nether stops being polite.",
            "Night Vision makes caves less terrifying. Slightly.",
            "Splash Potion of Healing: for when eating is too slow.",
            "Slow Falling: because sometimes you just need to float.",
            "Invisibility: the mobs can still hear you, you know.",
            "Awkward Potion: useless alone, essential for everything.",
            "Water Breathing: the ocean is now your domain.",

            // Food and cooking
            "Golden carrots: the food of champions and the wealthy.",
            "Suspicious stew: will it help or hurt? Only one way to find out.",
            "Steak: the optimal food. Don't let the golden carrot elitists fool you.",
            "Cake: the only food you have to place before eating.",
            "Rabbit stew: effort required, satisfaction questionable.",
            "Pumpkin pie: autumn vibes, year-round.",
            "Chorus fruit: teleports you randomly. What could go wrong?",
            "Rotten flesh: emergency food with consequences.",

            // Death and respawn
            "You died. Somewhere, a skeleton is wearing your armor.",
            "Items despawn in 5 minutes. The clock is always ticking.",
            "At least you set your spawn point. You did set it, right?",
            "Death by kinetic energy. Translation: you hit the ground too hard.",
            "Killed by intentional game design. The Warden doesn't play fair.",
            "Your stuff is in the lava now. It belongs to the lava.",
            "Respawn, retrieve, revenge. The three Rs of Minecraft.",
            "Keep Inventory is not cheating. It's self-care.",

            // Inventory management
            "27 slots. 27 problems. Wait, 36. Wait, more with armor.",
            "Full inventory, diamonds on the ground. Classic.",
            "The stack limit is 64. Your hoarding limit is infinite.",
            "Shulker boxes: the solution to inventory problems. Mostly.",
            "You don't need 15 stacks of cobblestone. But you're keeping them anyway.",
            "Ender chest: your pocket dimension of chaos.",
            "Why do you have 47 stone swords? When did you make these?",
            "One day you'll organize. Today is not that day.",

            // XP and grinding
            "'Too Expensive.' Two words. Infinite pain.",
            "Level 30. The magic number. The grind is real.",
            "XP orbs are the sound of progress. Collect them all.",
            "The grindstone removes enchantments. And a small piece of your soul.",
            "Anvil says 'Too Expensive.' You say nothing. You just stare.",
            "39 levels to repair. Anvil wants 40. Mathematics is cruel.",
            "Experience farms: because punching mobs is so last century.",

            // Rare events
            "Somewhere out there, a pink sheep exists. Find it.",
            "Baby zombie riding a chicken. Nature's cruelest joke.",
            "Skeleton horse trap: free horses! And also lightning and death.",
            "Charged creeper: rare, terrifying, and oddly useful.",
            "Spider jockey: because spiders needed ranged attacks apparently.",
            "A brown mooshroom exists. It requires lightning. And luck.",
            "Natural diamond armor zombie. Someone won the lottery today.",

            // Specific painful experiences
            "You forgot to write down the coordinates. We've all been there.",
            "The pickaxe broke on the last diamond. Poetic tragedy.",
            "You mined into lava. The diamonds watched as they burned.",
            "Forgot to bring food. The walk of shame begins.",
            "Creeper in the storage room. How did it even get there?",
            "You fell in the End void. With all your stuff. The void thanks you.",
            "Built the portal in the wrong spot. Now there are two bases.",
            "The enderman stole a block from your build. That specific block."
    );

    // ========================================
    // JOIN MESSAGES (~60 messages)
    // ========================================

    private static final List<String> JOIN_MESSAGES = List.of(
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
            "Ah, fresh meat-- I mean, welcome friend!",
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
            "Herobrine has not removed your welcome message.",

            // Additional welcomes
            "The mobs heard you were coming. They're excited. Nervously.",
            "Welcome! Your chests are exactly as disorganized as you left them.",
            "You're online! The adventure continues.",
            "Welcome back! The sun shines a little brighter now.",
            "The blocks await your vision. Welcome!",
            "You've connected! Time to make memories.",
            "Welcome! May your FPS be high and your lag be low.",
            "The world loaded just for you. Welcome!",
            "Another hero joins the server. Welcome!",
            "You're here! The Nether is a little less scary now."
    );

    // ========================================
    // MOTD MESSAGES (~45 messages - short and punchy)
    // ========================================

    private static final List<String> MOTD_MESSAGES = List.of(
            // Inviting
            "Come build something amazing!",
            "Adventure awaits within...",
            "Your next masterpiece starts here.",
            "Diamonds are waiting. Are you?",
            "A world of possibilities.",
            "Where creativity meets survival.",
            "Join us! The creepers are friendly. Mostly.",
            "Building dreams, one block at a time.",
            "Your adventure continues here.",
            "Home of epic builds and good vibes.",

            // Humorous
            "Now with 100% more blocks!",
            "Creeper-tested, Steve-approved.",
            "Warning: May cause excessive mining.",
            "Side effects include fun.",
            "No refunds on lost diamonds.",
            "Certified lava-free login area.",
            "Where beds actually let you sleep.",
            "Gravel-free since... well, nevermind.",
            "Scientifically proven to contain blocks.",
            "The villagers are only mildly suspicious.",

            // Mysterious
            "Secrets hide in the depths...",
            "The void whispers... 'join us'",
            "Ancient treasures await discovery.",
            "What will you uncover today?",
            "Legends are made here.",

            // Casual
            "Come hang out!",
            "Good times ahead.",
            "Grab your pickaxe!",
            "Let's build something cool.",
            "Ready when you are!",

            // Minecraft references
            "Punch trees, get wood, ???, profit.",
            "Fortune III recommended.",
            "Torches sold separately.",
            "Achievement Get: Reading the MOTD",
            "Press any key to... wait, wrong game.",

            // Additional MOTD
            "The blocks are calling your name.",
            "Bring your creativity!",
            "Every world needs a builder.",
            "Come make some memories.",
            "Adventure is just a click away.",
            "Your story continues here.",
            "The perfect escape awaits.",
            "Where imagination becomes reality.",
            "Craft, build, survive, thrive.",
            "The community awaits you!"
    );

    // ========================================
    // CONTEXT-SPECIFIC MESSAGE POOLS
    // ========================================

    private static final List<String> NIGHT_MESSAGES = List.of(
            "It's nighttime... if you dare.",
            "The mobs are active. Join anyway!",
            "Monsters roam the overworld. Perfect.",
            "Night has fallen. Adventure calls!",
            "Dark outside, but the server's cozy.",
            "The moon is up. So are the zombies."
    );

    private static final List<String> RAIN_MESSAGES = List.of(
            "Rainy day vibes. Perfect for building!",
            "It's raining! Great fishing weather.",
            "Storm's rolling in. Bring an umbrella.",
            "Cozy rain sounds await you.",
            "Wet outside, warm inside."
    );

    private static final List<String> THUNDER_MESSAGES = List.of(
            "Thunder rumbles! Epic timing.",
            "Lightning strikes! Dramatic entry awaits.",
            "Storm raging! Charged creepers possible.",
            "Thunder and lightning! Very exciting!",
            "Epic storm in progress. Join the drama!"
    );

    private static final List<String> EMPTY_SERVER_MESSAGES = List.of(
            "It's quiet... too quiet. Fix that!",
            "The server feels lonely. Help!",
            "Empty server = all the resources!",
            "First one here gets bragging rights.",
            "Be the first to join today!",
            "The mobs outnumber the players. Change that!",
            "Your friends aren't here yet. Beat them!"
    );

    // ========================================
    // PROCEDURAL MESSAGE GENERATION
    // ========================================

    /**
     * Generates a procedural message using word bank templates.
     * Templates vary by message type to match the appropriate tone.
     */
    public static String getProceduralMessage(MessageType type) {
        return switch (type) {
            case DAY -> getDayProceduralMessage();
            case JOIN -> getJoinProceduralMessage();
            case MOTD -> getMotdProceduralMessage();
        };
    }

    private static String getDayProceduralMessage() {
        int template = random.nextInt(45);

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
            case 10 -> "Today's agenda: " + pick(ACTIVITIES) + ", " + pick(ACTIVITIES) + ", and maybe " + pick(ACTIVITIES) + ".";
            case 11 -> "The " + pick(BIOMES) + " calls to you. Will you answer?";
            case 12 -> "A " + pick(MOBS) + " and a " + pick(MOBS) + " walk into a bar. The bar is your base. Run.";
            case 13 -> "If you listen closely, you can hear the " + pick(SOUNDS) + ".";
            case 14 -> "The " + pick(ORES) + " is out there. You can feel it.";
            case 15 -> "A " + pick(MOBS) + " wonders if you'll " + pick(ACTIVITIES) + " today.";
            case 16 -> "The " + pick(ADJECTIVES) + " " + pick(BIOMES) + " holds secrets untold.";
            case 17 -> "Rumor has it there's " + pick(WHISPERS) + ".";
            case 18 -> "Your " + pick(TOOLS) + " grows restless in your inventory.";
            case 19 -> "Today's quest: " + pick(ACTIVITIES) + ". Or just vibes.";
            case 20 -> "The stars foretold you would " + pick(ACTIVITIES) + ". The stars are weird.";
            case 21 -> "A " + pick(MOBS) + " dreams of finding " + pick(TREASURES) + ". Don't let it.";
            case 22 -> "The " + pick(ORES) + " calls from the depths. Answer wisely.";
            case 23 -> "Why " + pick(ACTIVITIES) + " when you could " + pick(ACTIVITIES) + " instead?";
            case 24 -> "News from the " + pick(BIOMES) + ": a " + pick(ADJECTIVES) + " " + pick(MOBS) + " was spotted.";
            case 25 -> "The universe suggests you try " + pick(ADVICE) + ".";
            case 26 -> "Prophecy: The " + pick(MOBS) + " and the " + pick(ITEMS) + " will meet today.";
            case 27 -> "Deep in the " + pick(BIOMES) + ", treasure awaits the brave.";
            case 28 -> "A " + pick(ADJECTIVES) + " feeling hangs in the air. Time to " + pick(ACTIVITIES) + ".";
            case 29 -> "The " + pick(SOUNDS) + " suggest it's time to " + pick(ACTIVITIES) + ".";
            case 30 -> "Dear " + pick(MOBS) + ": please don't. Sincerely, everyone.";
            case 31 -> "Loading tip: Try " + pick(ADVICE) + " for best results.";
            case 32 -> "Breaking news: Local player plans to " + pick(ACTIVITIES) + ". More at never.";
            case 33 -> "Your " + pick(ENCHANTMENTS) + " enchantment tingles with anticipation.";
            case 34 -> "The " + pick(VILLAGER_PROFESSIONS) + " villager nods approvingly at your " + pick(ITEMS) + ".";
            case 35 -> "Step 1: " + pick(ACTIVITIES) + ". Step 2: ???. Step 3: Profit.";
            case 36 -> "Today's vibe: " + pick(ADJECTIVES) + " with a chance of " + pick(MOBS) + ".";
            case 37 -> "The " + pick(FOODS) + " in your inventory grows cold. Eat it already.";
            case 38 -> "Achievement idea: " + pick(ACTIVITIES) + " while avoiding " + pick(MOBS) + ".";
            case 39 -> "Your " + pick(ARMOR) + " armor shines in the morning light.";
            case 40 -> "The " + pick(MOBS) + " council has decided your fate: " + pick(ACTIVITIES) + ".";
            case 41 -> "Plot twist: The " + pick(DISCOVERIES) + " was inside you all along. (It wasn't.)";
            case 42 -> "Overheard in the village: 'That player is going to " + pick(ACTIVITIES) + " today.'";
            case 43 -> "The " + pick(BLOCKS) + " awaits placement. It believes in you.";
            case 44 -> "Warning: " + pick(MOBS) + " levels are elevated in the " + pick(BIOMES) + " today.";
            default -> pickDayMessage();
        };
    }

    private static String getJoinProceduralMessage() {
        int template = random.nextInt(15);

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
            case 11 -> "You're here! Ready to " + pick(ACTIVITIES) + "?";
            case 12 -> "Welcome! A " + pick(ADJECTIVES) + " " + pick(DISCOVERIES) + " awaits discovery.";
            case 13 -> "The " + pick(MOBS) + " council welcomes you back.";
            case 14 -> "Greetings! Today's goal: " + pick(GOALS) + ".";
            default -> pick(JOIN_MESSAGES);
        };
    }

    private static String getMotdProceduralMessage() {
        // MOTD needs to be short, so fewer templates
        int template = random.nextInt(8);

        return switch (template) {
            case 0 -> "Come " + pick(ACTIVITIES) + "!";
            case 1 -> pick(ADJECTIVES).substring(0, 1).toUpperCase() + pick(ADJECTIVES).substring(1) + " adventures await!";
            case 2 -> "The " + pick(BIOMES) + " awaits you.";
            case 3 -> "Seek " + pick(TREASURES) + " within!";
            case 4 -> pick(MOBS) + "-approved server.";
            case 5 -> "Home of " + pick(ADJECTIVES) + " builds.";
            case 6 -> pick(DISCOVERIES) + " found here!";
            case 7 -> "May your " + pick(TOOLS) + " never break.";
            default -> pickMotdMessage();
        };
    }

    // ========================================
    // CONTEXT-AWARE MESSAGE GENERATION
    // ========================================

    /**
     * Gets a context-aware message based on world state only.
     * Used for MOTD where no player is available.
     */
    private static String getWorldContextMessage(World world, int playerCount) {
        long time = world.getTime();
        boolean isNight = time >= 13000 && time < 23000;
        boolean isStorm = world.hasStorm();
        boolean isThunder = world.isThundering();

        if (isThunder) {
            return pick(THUNDER_MESSAGES);
        }
        if (isStorm) {
            return pick(RAIN_MESSAGES);
        }
        if (isNight) {
            return pick(NIGHT_MESSAGES);
        }
        if (playerCount == 0) {
            return pick(EMPTY_SERVER_MESSAGES);
        }
        if (playerCount >= 2) {
            return "Friends are online! Join the fun.";
        }

        return null;
    }

    /**
     * Gets a context-aware message based on player and world state.
     * Used for DAY and JOIN messages.
     */
    private static String getPlayerContextMessage(Player player, MessageType type) {
        return getPlayerContextMessage(player, type, player.getName());
    }

    /**
     * Gets a context-aware message based on player and world state.
     * Used for DAY and JOIN messages.
     *
     * @param displayName the player's display name (nickname or username)
     */
    private static String getPlayerContextMessage(Player player, MessageType type, String displayName) {
        World world = player.getWorld();
        List<String> candidates = new ArrayList<>();
        List<String> biomeCandidates = new ArrayList<>();

        // Iterate through all registered context providers
        for (ContextProvider provider : CONTEXT_PROVIDERS) {
            // Skip if this provider is only for a different message type
            if (provider.onlyFor() != null && provider.onlyFor() != type) {
                continue;
            }

            // Route biome-only providers to separate list (fallback behavior)
            List<String> targetList = provider.biomeOnly() ? biomeCandidates : candidates;
            provider.method().addMessages(player, world, targetList, type, displayName);
        }

        // Only use biome messages if no other context found
        if (candidates.isEmpty() && !biomeCandidates.isEmpty()) {
            candidates.addAll(biomeCandidates);
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return pick(candidates);
    }

    // ========================================
    // CONTEXT HELPER METHODS
    // ========================================
    // Public for debug command access

    public static void addHealthMessages(Player player, List<String> candidates) {
        double health = player.getHealth();
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();

        if (health <= 4) {
            candidates.add("You're barely alive. Maybe eat something?");
            candidates.add("Your health is critical. Today might be short.");
            candidates.add("Two hearts left. Living dangerously, I see.");
            candidates.add("One good hit and you're done. Find food or shelter.");
            candidates.add("Death lurks close. Your health bar screams for attention.");
            candidates.add("The grim reaper is taking notes. Heal up!");
        } else if (health >= maxHealth) {
            candidates.add("Full health! You're ready for anything.");
            candidates.add("Peak condition. The mobs should be worried.");
            candidates.add("Hearts topped off. Time to do something reckless?");
            candidates.add("Maximum health achieved. The world is your oyster.");
        } else if (health <= maxHealth / 2) {
            candidates.add("Half health. Not critical, but not comfortable either.");
            candidates.add("You've seen better days. A golden apple wouldn't hurt.");
        }
    }

    public static void addHungerMessages(Player player, List<String> candidates) {
        int foodLevel = player.getFoodLevel();

        if (foodLevel <= 6) {
            candidates.add("Your stomach growls. Feed yourself, adventurer.");
            candidates.add("Hunger gnaws at you. Time to eat.");
            candidates.add("Running on empty. Find food before the sprint fails.");
            candidates.add("The hunger is real. Your sprint bar is crying.");
            candidates.add("Starving artist vibes, minus the art. Just starving.");
            candidates.add("Your inventory has food, right? RIGHT?");
        } else if (foodLevel >= 20) {
            candidates.add("Well fed and ready to go!");
            candidates.add("Stuffed like a turkey. Maximum food achieved.");
            candidates.add("Your hunger bar is full. Sprint to your heart's content.");
            candidates.add("Fed and fabulous. Nothing can stop you now.");
        } else if (foodLevel <= 10) {
            candidates.add("Getting a bit peckish. Keep some food handy.");
            candidates.add("Not starving yet, but your stomach is making suggestions.");
        }
    }

    public static void addExperienceMessages(Player player, List<String> candidates) {
        int level = player.getLevel();

        if (level >= 30) {
            candidates.add("Level " + level + "! Time to enchant something.");
            candidates.add("All that XP is burning a hole in your pocket.");
            candidates.add("You're glowing with experience. Literally.");
            candidates.add("Max enchanting power achieved. The table awaits.");
            candidates.add("That's a lot of levels. Don't die and lose them all.");
            candidates.add("XP-rich and ready to enchant. What'll it be?");
        } else if (level >= 50) {
            candidates.add("Level " + level + "?! You're an XP hoarder!");
            candidates.add("All those levels... one death and they're mist.");
        } else if (level == 0) {
            candidates.add("Starting fresh with zero XP. The grind begins.");
            candidates.add("Zero levels. Clean slate or recent tragedy?");
            candidates.add("No XP. Time to punch some zombies.");
            candidates.add("Level zero. The journey of a thousand levels begins with a single mob.");
        } else if (level >= 15 && level < 30) {
            candidates.add("Level " + level + ". Almost at enchanting tier. Keep going!");
            candidates.add("Halfway to max enchanting. The XP grind continues.");
        }
    }

    public static void addArmorMessages(Player player, List<String> candidates) {
        var helmet = player.getInventory().getHelmet();
        var chestplate = player.getInventory().getChestplate();
        var leggings = player.getInventory().getLeggings();
        var boots = player.getInventory().getBoots();

        if (helmet == null && chestplate == null) {
            candidates.add("No armor? Bold strategy. Let's see if it pays off.");
            candidates.add("Unarmored and unafraid. Or just unprepared.");
            candidates.add("Feeling brave without armor? The mobs appreciate it.");
            candidates.add("Running around naked. The creepers love an easy target.");
        } else if (chestplate != null) {
            String type = chestplate.getType().name();
            if (type.contains("NETHERITE")) {
                candidates.add("Netherite armor gleams. You've made it.");
                candidates.add("Decked in netherite. The endgame is now.");
                candidates.add("Ancient debris forged into protection. Impressive.");
                candidates.add("Netherite! The ultimate flex. Mobs fear you.");
            } else if (type.contains("DIAMOND")) {
                candidates.add("Diamond armor shines in the morning light.");
                candidates.add("Diamonds aren't just for pickaxes. Looking sharp!");
                candidates.add("Blue and beautiful. Diamond armor suits you.");
                candidates.add("Dressed in diamonds. Living the dream.");
            } else if (type.contains("IRON")) {
                candidates.add("Iron armor. Reliable. Respectable.");
                candidates.add("Trusty iron. The workhorse of armor.");
                candidates.add("Iron-clad and ready. A solid choice.");
                candidates.add("Silver shine of iron. Classic adventurer look.");
            } else if (type.contains("LEATHER")) {
                candidates.add("Leather armor. It's a start!");
                candidates.add("Leather protection. Better than nothing!");
                candidates.add("Rocking the leather look. Fashion or function?");
                candidates.add("Hide armor. The cows gave their lives for this.");
            } else if (type.contains("GOLD")) {
                candidates.add("Gold armor. Stylish but fragile. Very fragile.");
                candidates.add("Golden and glorious. Piglins will love you.");
                candidates.add("Bling bling! Gold armor is a fashion statement.");
                candidates.add("Shiny gold armor. Just don't expect it to last.");
            } else if (type.contains("CHAINMAIL")) {
                candidates.add("Chainmail! Rare and stylish. Where'd you get that?");
                candidates.add("Rattling chains protect you. Knight vibes.");
            } else if (chestplate.getType() == org.bukkit.Material.ELYTRA) {
                candidates.add("Elytra equipped. The sky is yours today.");
                candidates.add("Wings ready. Where will you fly?");
                candidates.add("Freedom of flight. Gravity is merely a suggestion.");
                candidates.add("Elytra on. Time to soar above it all.");
            }
        }

        // Check for turtle helmet specifically
        if (helmet != null && helmet.getType() == org.bukkit.Material.TURTLE_HELMET) {
            candidates.add("Turtle helmet! Ten seconds of water breathing on demand.");
            candidates.add("Sporting the turtle shell. Ocean explorer mode.");
        }
    }

    public static void addHeldItemMessages(Player player, List<String> candidates, MessageType type) {
        var mainHand = player.getInventory().getItemInMainHand();
        String itemType = mainHand.getType().name();

        if (itemType.equals("DIAMOND_PICKAXE")) {
            candidates.add("Diamond pickaxe in hand. The caves await.");
            candidates.add("Blue steel at the ready. Mining time!");
            candidates.add("Diamond pick equipped. The ores don't stand a chance.");
            candidates.add("Trusty diamond pickaxe. Your best friend underground.");
        } else if (itemType.equals("NETHERITE_PICKAXE")) {
            candidates.add("Netherite pickaxe ready. Nothing can stop you.");
            candidates.add("The ultimate mining tool. Ancient debris is toast.");
            candidates.add("Netherite pick in hand. You've achieved peak miner status.");
            candidates.add("That pickaxe has seen some things. And mined them all.");
        } else if (itemType.equals("FISHING_ROD")) {
            candidates.add("Fishing rod at the ready. A peaceful " + (type == MessageType.DAY ? "day" : "session") + " ahead?");
            candidates.add("Gone fishing? The ponds await your patience.");
            candidates.add("Rod in hand. Time to catch some treasure. Or fish. Mostly fish.");
            candidates.add("The fishing life calls. Relaxation and rare loot await.");
        } else if (itemType.contains("SWORD")) {
            candidates.add("Sword drawn" + (type == MessageType.DAY ? " at dawn" : "") + ". Expecting trouble?");
            candidates.add("Blade at the ready. The mobs should be concerned.");
            candidates.add("Steel in hand. Looking for a fight or just prepared?");
            candidates.add("Sword equipped. Sweep attacks are your friend.");
        } else if (itemType.equals("BOW")) {
            candidates.add("Bow at the ready. Keep your distance from danger.");
            candidates.add("Ranged weapon ready. Smart choice.");
            candidates.add("Arrow nocked and ready. Skeletons, meet your match.");
            candidates.add("The bow string hums with potential energy.");
        } else if (itemType.equals("CROSSBOW")) {
            candidates.add("Crossbow locked and loaded. Piercing power awaits.");
            candidates.add("The crossbow clicks with mechanical precision.");
            candidates.add("Multishot or piercing? Either way, devastation.");
            candidates.add("Ranged weapon ready. Smart choice.");
        } else if (itemType.equals("SHIELD")) {
            candidates.add("Shield up. A defensive start" + (type == MessageType.DAY ? " to the day" : "") + ".");
            candidates.add("Blocking ready. Creeper explosions hate this one trick.");
            candidates.add("Shield raised. Protection is your priority.");
            candidates.add("Turtle mode activated. Nothing gets through.");
        } else if (itemType.equals("TRIDENT")) {
            candidates.add("Trident in hand. Poseidon would be proud.");
            candidates.add("The fork of the sea. Riptide? Loyalty? Channeling?");
            candidates.add("Trident ready. The drowned wish they kept theirs.");
            candidates.add("Ocean's mightiest weapon. Throw it or stab with it?");
        } else if (itemType.contains("_AXE") && !itemType.equals("PICKAXE")) {
            candidates.add("Axe in hand. Chopping wood or chopping mobs?");
            candidates.add("The lumberjack look. Trees beware.");
            candidates.add("Axe ready. High damage, low speed. Worth it.");
            candidates.add("Wood-cutting mode engaged. Or head-cutting. Your choice.");
        } else if (itemType.contains("_HOE")) {
            candidates.add("Hoe in hand. Farming time!");
            candidates.add("The humble hoe. Agriculture awaits.");
            candidates.add("Tilling soil is honest work. Get those crops started.");
            candidates.add("Farmer mode activated. The fields need tending.");
        } else if (itemType.equals("SPYGLASS")) {
            candidates.add("Spyglass out. Looking for something in the distance?");
            candidates.add("The scout life. Survey your surroundings.");
        } else if (itemType.equals("COMPASS")) {
            candidates.add("Compass in hand. Finding your way home?");
            candidates.add("The needle points to spawn. Or does it?");
        } else if (itemType.equals("CLOCK")) {
            candidates.add("Checking the time? It's always adventure o'clock.");
            candidates.add("Clock in hand. Time is... relative underground.");
        }
    }

    public static void addYLevelMessages(Player player, List<String> candidates) {
        int y = player.getLocation().getBlockY();

        if (y < -50) {
            candidates.add("Deep in the world. Diamonds lurk at this depth.");
            candidates.add("Y level " + y + ". The deep dark isn't far.");
            candidates.add("This far down, even the stone changes.");
            candidates.add("The deepslate surrounds you. Ancient rock, ancient treasures.");
            candidates.add("Diamond depth achieved. May Fortune favor your pickaxe.");
            candidates.add("The bottom of the world. Where the best ores hide.");
        } else if (y < 0) {
            candidates.add("Below sea level. The deepslate zone.");
            candidates.add("Negative Y coordinates. The depths welcome you.");
            candidates.add("Underground and loving it. Well, maybe.");
            candidates.add("The caves run deep here. Watch for sculk.");
        } else if (y > 200) {
            candidates.add("High in the sky! Don't look down.");
            candidates.add("Y level " + y + ". The clouds are below you.");
            candidates.add("Mountain peak living. The air is thin up here.");
            candidates.add("Vertigo territory. The ground is far below.");
            candidates.add("Sky-high! Even phantoms would get tired flying this high.");
            candidates.add("You're practically in space. Build height almost reached.");
        } else if (y > 100) {
            candidates.add("Above the clouds. The view must be incredible.");
            candidates.add("Cloud level achieved. Time for a sky base?");
            candidates.add("High altitude adventures. The air tastes different up here.");
            candidates.add("The world spreads below. King of the mountain vibes.");
        } else if (y >= 62 && y <= 70) {
            candidates.add("Sea level. The oceans stretch to the horizon.");
            candidates.add("Coastal vibes. Beach episode when?");
        }
    }

    public static void addUndergroundMessages(Player player, World world, List<String> candidates, MessageType type) {
        int highestBlockY = world.getHighestBlockYAt(player.getLocation());

        if (player.getLocation().getBlockY() < highestBlockY - 10) {
            if (type == MessageType.DAY) {
                candidates.add("Underground at dawn. The sun rises without you.");
                candidates.add("Waking up in a cave. Classic miner lifestyle.");
                candidates.add("No sunrise for you today. Just stone and ore.");
                candidates.add("The sun greets the world above. You greet the rocks.");
                candidates.add("Surface dwellers see the dawn. You see deepslate.");
                candidates.add("Who needs sunlight when you have torches?");
            } else {
                candidates.add("Underground already? Efficient.");
                candidates.add("You're deep below. The surface is overrated anyway.");
                candidates.add("Arriving in a cave. No time wasted on travel.");
                candidates.add("The underground welcomes you. Mind the lava.");
                candidates.add("Cave life chose you. Or did you choose it?");
                candidates.add("Below ground, where the treasures hide.");
            }
        }
    }

    public static void addWaterMessages(Player player, List<String> candidates, MessageType type) {
        if (player.isInWater()) {
            if (type == MessageType.DAY) {
                candidates.add("Starting the day in water. Refreshing!");
                candidates.add("You're soaking wet. Interesting sleeping arrangements.");
                candidates.add("Swimming at dawn. The fish are your alarm clock.");
                candidates.add("Aquatic morning! The dolphins say hello.");
                candidates.add("Waterlogged at sunrise. At least you won't catch fire.");
                candidates.add("The ocean cradles you at dawn. Peaceful, if cold.");
            } else {
                candidates.add("You're... in the water. Interesting spawn point.");
                candidates.add("Arriving with a splash! Literally.");
                candidates.add("Ocean spawn point. Did you log out in a boat?");
                candidates.add("Swimming in! The traditional mermaid entrance.");
                candidates.add("Submerged on arrival. Watch your air bubbles.");
                candidates.add("Wet welcome! Hope you packed water breathing.");
            }
        }
    }

    public static void addVehicleMessages(Player player, List<String> candidates, MessageType type) {
        if (player.isInsideVehicle()) {
            var vehicle = player.getVehicle();
            if (vehicle != null) {
                String vehicleType = vehicle.getType().name().toLowerCase().replace("_", " ");
                String timeRef = type == MessageType.DAY ? " at dawn" : "";

                candidates.add("Good " + (type == MessageType.DAY ? "morning" : "to see you") + " from your " + vehicleType + "!");

                if (vehicle.getType() == org.bukkit.entity.EntityType.HORSE) {
                    candidates.add("Your horse greets the " + (type == MessageType.DAY ? "dawn" : "server") + " with you.");
                    candidates.add("Mounted and ready for adventure. Giddy up!");
                    candidates.add("Horse friend is happy to see the " + (type == MessageType.DAY ? "sunrise" : "world") + " with you.");
                    candidates.add("Four hooves and full hearts. Let's ride!");
                } else if (vehicleType.contains("boat")) {
                    candidates.add((type == MessageType.DAY ? "Dawn breaks over the water. " : "") + "Peaceful sailing ahead.");
                    candidates.add("Captain of your vessel! The seas await.");
                    candidates.add("Boat life! Cruise the rivers and oceans.");
                    candidates.add("Setting sail" + timeRef + ". Adventure on the horizon.");
                } else if (vehicle.getType() == org.bukkit.entity.EntityType.PIG) {
                    candidates.add("Riding a pig" + timeRef + ". Living your best life.");
                    candidates.add("Pig jockey! Carrot on a stick at the ready?");
                    candidates.add("The pig goes where you want. Eventually. Maybe.");
                    candidates.add("Oink oink! Your noble steed awaits your command.");
                } else if (vehicle.getType() == org.bukkit.entity.EntityType.STRIDER) {
                    candidates.add("Your strider warbles happily. Well, probably happily.");
                    candidates.add("Lava surfing! The strider makes it possible.");
                    candidates.add("Nether transportation at its finest. Good strider.");
                    candidates.add("Walking on lava like it's nothing. Strider power!");
                } else if (vehicleType.contains("minecart")) {
                    candidates.add("Riding the rails" + timeRef + ". Efficient transportation.");
                    candidates.add("Minecart express! Where does this track go?");
                    candidates.add("Rail travel. Powered or gravity-fed?");
                    candidates.add("On the rails. The original fast travel.");
                } else if (vehicle.getType() == org.bukkit.entity.EntityType.DONKEY || vehicle.getType() == org.bukkit.entity.EntityType.MULE) {
                    candidates.add("Pack animal ready! Extra inventory on the go.");
                    candidates.add("Donkey or mule - mobile storage solution!");
                } else if (vehicle.getType() == org.bukkit.entity.EntityType.LLAMA) {
                    candidates.add("Llama taxi! These guys have personality.");
                    candidates.add("Riding a llama. It might spit at your enemies.");
                } else if (vehicle.getType() == org.bukkit.entity.EntityType.CAMEL) {
                    candidates.add("Camel ride! Two-seater desert transport.");
                    candidates.add("The camel saunters along. Desert vibes.");
                }
            }
        }
    }

    public static void addDayMilestoneMessages(World world, List<String> candidates) {
        long dayNumber = (world.getFullTime() / 24000) + 1;

        if (dayNumber == 1) {
            candidates.add("Day 1. Your journey begins!");
            candidates.add("The first day. Everything is possible.");
            candidates.add("Day one! Punch a tree, start the adventure.");
            candidates.add("The very first sunrise. Make it count.");
        } else if (dayNumber == 100) {
            candidates.add("Day 100! You've been here a while.");
            candidates.add("Triple digits! One hundred days survived.");
            candidates.add("Century mark reached. The world knows you now.");
            candidates.add("100 days! Remember that first night in a dirt hut?");
        } else if (dayNumber == 365) {
            candidates.add("Day 365. A full Minecraft year!");
            candidates.add("One year! Three hundred sixty-five days of blocks.");
            candidates.add("Anniversary day! You've lived a whole Minecraft year.");
            candidates.add("365 days. The seasons have cycled. Well, figuratively.");
        } else if (dayNumber == 1000) {
            candidates.add("Day 1,000. You're basically a local legend.");
            candidates.add("One thousand days! That's dedication.");
            candidates.add("Day 1000. The ancient texts speak of your adventures.");
            candidates.add("Millennium milestone! The world is forever changed by you.");
        } else if (dayNumber % 500 == 0) {
            candidates.add("Day " + dayNumber + "! Half-thousand milestone!");
            candidates.add(dayNumber + " days! The journey continues.");
        } else if (dayNumber % 100 == 0) {
            candidates.add("Day " + dayNumber + ". Another milestone!");
            candidates.add(dayNumber + " days survived. Keep going!");
        } else if (dayNumber <= 3) {
            candidates.add("Still early days. Survive, then thrive.");
            candidates.add("Day " + dayNumber + ". Still getting your bearings.");
            candidates.add("The beginning of a long adventure. Pace yourself.");
            candidates.add("Early days. The base is humble but growing.");
        } else if (dayNumber == 7) {
            candidates.add("One week! You've made it through seven days.");
            candidates.add("Day 7. A full week of survival!");
        } else if (dayNumber == 30) {
            candidates.add("Day 30! One month of adventures.");
            candidates.add("Thirty days! The world starts feeling like home.");
        }
    }

    public static void addDifficultyMessages(World world, List<String> candidates) {
        switch (world.getDifficulty()) {
            case PEACEFUL -> {
                candidates.add("Peaceful mode. Relax, no monsters today.");
                candidates.add("A calm world awaits. No hostile mobs here.");
                candidates.add("Serene and monster-free. Build without fear.");
                candidates.add("Peaceful vibes only. The mobs are on vacation.");
            }
            case EASY -> {
                candidates.add("Easy mode. Training wheels for the apocalypse.");
                candidates.add("Gentle difficulty. The mobs are holding back.");
            }
            case NORMAL -> {
                candidates.add("Normal mode. Fair challenge, fair rewards.");
                candidates.add("Standard difficulty. The classic Minecraft experience.");
            }
            case HARD -> {
                candidates.add("Hard mode. The mobs hit harder. So should you.");
                candidates.add("Hard difficulty. Every mistake costs more.");
                candidates.add("Maximum challenge. The world wants you dead.");
                candidates.add("Hard mode! Zombies break doors, hunger hurts, mobs are stronger.");
            }
        }
    }

    public static void addMoonPhaseMessages(World world, List<String> candidates) {
        int moonPhase = (int) ((world.getFullTime() / 24000) % 8);

        switch (moonPhase) {
            case 0 -> {
                candidates.add("Full moon tonight. The undead will be restless.");
                candidates.add("A full moon rises tonight. The slimes rejoice.");
                candidates.add("Full moon ahead. Extra mobs, extra loot. Probably.");
                candidates.add("Luna at maximum! Werewolves would approve.");
                candidates.add("The full moon calls to creatures of the night.");
                candidates.add("Bright full moon tonight. Even caves will feel it.");
            }
            case 4 -> {
                candidates.add("New moon tonight. Darkness will be absolute.");
                candidates.add("New moon rises. Even the stars seem brighter.");
                candidates.add("Tonight brings a new moon. The shadows grow bold.");
                candidates.add("Moonless night approaches. Total darkness awaits.");
                candidates.add("The sky will be empty tonight. Perfect for stargazing.");
                candidates.add("New moon means new beginnings. Or just really dark nights.");
            }
            case 1, 7 -> {
                candidates.add("A gibbous moon tonight. The tides of adventure flow.");
                candidates.add("Waxing gibbous rises. Almost full, almost dangerous.");
                candidates.add("The moon is nearly full. One more day until chaos.");
                candidates.add("Gibbous moon tonight. The night creatures stir eagerly.");
            }
            case 2, 6 -> {
                candidates.add("Half moon tonight. Balance in all things.");
                candidates.add("Quarter moon rises. Half light, half shadow.");
                candidates.add("The moon shows its profile. Perfectly balanced.");
                candidates.add("Half illuminated, half mysterious. The moon is mood lighting.");
            }
            case 3, 5 -> {
                candidates.add("A crescent moon tonight. Subtle, like the diamonds you seek.");
                candidates.add("Crescent moon rises. A sliver of light in the darkness.");
                candidates.add("The thin crescent smiles down. Or is it frowning?");
                candidates.add("Barely there moonlight. The stars take center stage.");
            }
        }
    }

    public static void addWeatherMessages(World world, List<String> candidates, MessageType type) {
        if (world.hasStorm()) {
            if (world.isThundering()) {
                if (type == MessageType.DAY) {
                    candidates.add("Thunder rumbles across the land. The charged creepers stir.");
                    candidates.add("A storm rages. Perhaps stay indoors today?");
                    candidates.add("Lightning crackles in the sky. Free mob heads, anyone?");
                    candidates.add("Epic thunderstorm! The sky lights up with each bolt.");
                    candidates.add("Nature's fireworks overhead. Stay away from tall trees.");
                    candidates.add("Thor is angry today. Or the weather system. Either way, lightning.");
                } else {
                    candidates.add("You arrive amid thunder! Dramatic entrance.");
                    candidates.add("Lightning crackles to herald your return!");
                    candidates.add("Arriving during a thunderstorm. Main character energy.");
                    candidates.add("The storm welcomes you back! Electrifying entrance.");
                    candidates.add("Thunder and lightning greet you. The sky celebrates!");
                    candidates.add("What an entrance! The thunder rolls for you.");
                }
            } else {
                if (type == MessageType.DAY) {
                    candidates.add("Rain falls gently. A good day to tend the crops.");
                    candidates.add("The rain begins. Perfect fishing weather!");
                    candidates.add("Gray skies today. The zombies won't burn, so stay alert.");
                    candidates.add("Pitter-patter of rain on the roof. Cozy building weather.");
                    candidates.add("Rain washes the land. The farm crops drink it up.");
                    candidates.add("Wet day ahead. Bring an umbrella. Oh wait, those don't exist.");
                } else {
                    candidates.add("Rain greets your arrival. Cozy vibes.");
                    candidates.add("You join during a storm. Perfect fishing weather!");
                    candidates.add("Rainy welcome! The world sounds different in the rain.");
                    candidates.add("Drip drop, you're back! The rain celebrates.");
                    candidates.add("Wet but wonderful. The rain makes everything shine.");
                    candidates.add("Arriving in the rain. Very cinematic.");
                }
            }
        } else if (type == MessageType.DAY) {
            candidates.add("Clear skies ahead. The sun will keep you safe.");
            candidates.add("Beautiful weather today. The mobs will burn nicely.");
            candidates.add("Sunny skies smile upon your endeavors.");
            candidates.add("Perfect weather for adventure. Not a cloud in the sky.");
            candidates.add("The sun shines bright. Hostile mobs in the open are toast.");
            candidates.add("Blue skies and sunshine. The world is yours today.");
        } else {
            candidates.add("Clear skies greet your arrival. Perfect visibility.");
            candidates.add("Sunny welcome! The weather cooperates today.");
            candidates.add("Not a cloud in sight. The world looks inviting.");
            candidates.add("Beautiful day to be here. The sun agrees.");
        }
    }

    public static void addBiomeMessages(Player player, World world, List<String> candidates) {
        var biome = world.getBiome(player.getLocation());
        String biomeName = biome.getKey().getKey().toLowerCase();

        // Overworld biomes
        if (biomeName.contains("desert")) {
            candidates.add("The desert sun beats down. Watch out for husks.");
            candidates.add("Sand stretches in every direction. Pyramids hide treasures.");
            candidates.add("Hot and dry. The desert tests your survival skills.");
            candidates.add("Cacti and sand. The desert has its own harsh beauty.");
        } else if (biomeName.contains("ocean")) {
            candidates.add("The sea stretches before you. Adventure awaits beneath the waves.");
            candidates.add("Ocean breeze fills the air. Perhaps a shipwreck hunt?");
            candidates.add("Endless blue. The ocean holds countless secrets.");
            candidates.add("Salt air and seagulls. The nautical life calls.");
        } else if (biomeName.contains("jungle")) {
            candidates.add("The jungle awakens. Parrots chatter, ocelots lurk.");
            candidates.add("Vines hang heavy with dew. A temple hides somewhere nearby.");
            candidates.add("Dense foliage everywhere. The jungle is alive with sound.");
            candidates.add("Tropical paradise or green maze? The jungle is both.");
        } else if (biomeName.contains("swamp")) {
            candidates.add("Mist rises from the swamp. Witches brew in their huts.");
            candidates.add("The swamp gurgles. Slimes bounce in the murky waters.");
            candidates.add("Murky waters and hanging vines. The swamp has atmosphere.");
            candidates.add("Frogs croak in the wetlands. The swamp teems with life.");
        } else if (biomeName.contains("mountain") || biomeName.contains("peak")) {
            candidates.add("The mountain air is crisp. Goats leap on the cliffs above.");
            candidates.add("High altitude today. Emeralds hide in these peaks.");
            candidates.add("Peaks pierce the clouds. The view from here is incredible.");
            candidates.add("Rocky cliffs and thin air. The mountains challenge you.");
        } else if (biomeName.contains("snow") || biomeName.contains("ice") || biomeName.contains("frozen")) {
            candidates.add("Frost clings to everything. Bundle up!");
            candidates.add("Snow blankets the land. Strays lurk in the cold.");
            candidates.add("Winter wonderland vibes. The cold never bothered you anyway.");
            candidates.add("Icy terrain ahead. Watch for powder snow traps.");
        } else if (biomeName.contains("mushroom")) {
            candidates.add("Mycelium squishes underfoot. No hostile mobs here, just vibes.");
            candidates.add("The mooshrooms watch you with knowing eyes.");
            candidates.add("Giant mushrooms tower above. This biome is trippy.");
            candidates.add("Safe haven from monsters. The mushroom island protects.");
        } else if (biomeName.contains("dark_forest")) {
            candidates.add("The dark forest looms. Woodland mansions hide the vindictive.");
            candidates.add("Little light penetrates the canopy. Mobs may spawn even now.");
            candidates.add("Thick canopy blocks the sky. The dark forest earned its name.");
            candidates.add("Massive oak and dark oak trees. A maze of wood and shadow.");
        } else if (biomeName.contains("cherry")) {
            candidates.add("Cherry blossoms drift on the breeze. Peaceful.");
            candidates.add("Pink petals carpet the ground. A beautiful day.");
            candidates.add("The cherry grove blooms. Nature's pink masterpiece.");
            candidates.add("Sakura vibes. This biome is pure aesthetic.");
        } else if (biomeName.contains("badlands") || biomeName.contains("mesa")) {
            candidates.add("Terracotta towers catch the light. Gold hides in these hills.");
            candidates.add("The badlands bake under the sun. Mineshafts thread through the terrain.");
            candidates.add("Striped terracotta canyons. The wild west of Minecraft.");
            candidates.add("Erosion carved this landscape. Red rock country.");
        } else if (biomeName.contains("deep_dark")) {
            candidates.add("Sculk spreads in the darkness. The Warden listens.");
            candidates.add("The silence here is deafening. Stay quiet. Stay alive.");
            candidates.add("Ancient cities lurk in the dark. Treasure or death awaits.");
            candidates.add("Sculk sensors everywhere. One wrong step and it's over.");
        } else if (biomeName.contains("lush")) {
            candidates.add("Axolotls splash in the pools. The lush caves welcome you.");
            candidates.add("Glow berries illuminate the cavern. Nature thrives below.");
            candidates.add("Underground paradise. The lush caves are beautiful.");
            candidates.add("Moss and vines drape everything. Life finds a way.");
        } else if (biomeName.contains("plains")) {
            candidates.add("Open plains stretch to the horizon. Classic terrain.");
            candidates.add("Flat grassland. Easy building, easy travel.");
        } else if (biomeName.contains("forest") && !biomeName.contains("dark")) {
            candidates.add("Trees and wildlife. The forest provides.");
            candidates.add("Woodland surrounds you. A classic biome.");
        } else if (biomeName.contains("taiga")) {
            candidates.add("Spruce forests and cool air. Taiga territory.");
            candidates.add("Pine trees as far as the eye can see.");
        } else if (biomeName.contains("savanna")) {
            candidates.add("Acacia trees dot the landscape. Savanna warmth.");
            candidates.add("African vibes. The savanna stretches wide.");
        } else if (biomeName.contains("birch")) {
            candidates.add("White bark everywhere. The birch forest glows.");
            candidates.add("Birch trees stand tall. Clean, bright aesthetic.");
        } else if (biomeName.contains("mangrove")) {
            candidates.add("Tangled roots and muddy waters. Mangrove swamp.");
            candidates.add("The mangroves are dense. Watch your step in the mud.");
        }

        // Nether biomes
        if (world.getEnvironment() == World.Environment.NETHER) {
            if (biomeName.contains("soul_sand") || biomeName.contains("soul sand")) {
                candidates.add("Soul sand slows your steps. Ghasts wail overhead.");
                candidates.add("The valley of souls. Blue fire flickers in the darkness.");
                candidates.add("Spooky blue flames. The soul sand valley is unsettling.");
                candidates.add("Skeletons and fossils. This place holds ancient death.");
            } else if (biomeName.contains("warped")) {
                candidates.add("The warped forest glows blue. Endermen lurk here.");
                candidates.add("Warped fungi tower overhead. An alien landscape.");
                candidates.add("Cyan and strange. The warped forest is peaceful, sort of.");
                candidates.add("Endermen paradise. Watch your eyes in the warped forest.");
            } else if (biomeName.contains("crimson")) {
                candidates.add("The crimson forest pulses with red light. Hoglins grunt nearby.");
                candidates.add("Red and hostile. The crimson forest doesn't welcome visitors.");
                candidates.add("Piglins and hoglins rule here. The crimson is dangerous.");
                candidates.add("Blood-red fungi. The crimson forest is unsettling.");
            } else if (biomeName.contains("basalt")) {
                candidates.add("The basalt deltas crackle. Watch your step.");
                candidates.add("Basalt pillars and magma cubes. Treacherous terrain.");
                candidates.add("Volcanic nightmare. The basalt deltas are brutal.");
                candidates.add("One wrong step means lava. The deltas are unforgiving.");
            } else if (biomeName.contains("wastes")) {
                candidates.add("Nether wastes stretch endlessly. Classic hellscape.");
                candidates.add("Netherrack as far as the eye can see. Welcome to hell.");
                candidates.add("The original Nether. Ghasts and zombified piglins abound.");
                candidates.add("Red rock and fire. The Nether wastes are harsh.");
            }
        }
    }

    public static void addDimensionMessages(Player player, World world, List<String> candidates, MessageType type) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            if (type == MessageType.JOIN) {
                candidates.add("You spawn in the Nether?! Brave choice.");
                candidates.add("Welcome to the hot place. Watch your step.");
                candidates.add("Logging in to hellfire. Bold strategy.");
                candidates.add("Nether spawn point. Living dangerously!");
                candidates.add("Hell awaits. Don't forget your fire resistance.");
                candidates.add("Welcome back to the dimension of pain and suffering!");
            } else {
                candidates.add("Another day in the Nether. Try not to catch fire.");
                candidates.add("The Nether's heat is unrelenting. At least ghasts provide ambiance.");
                candidates.add("Lava flows in rivers below. The Nether never sleeps.");
                candidates.add("Red and hot. The Nether starts another day of torment.");
                candidates.add("Sunrise doesn't reach here. Just eternal flame.");
                candidates.add("Good morning from the underworld. Fire and brimstone as usual.");
            }
        } else if (world.getEnvironment() == World.Environment.THE_END) {
            if (type == MessageType.JOIN) {
                candidates.add("You return to the End. The dragon remembers you.");
                candidates.add("The void greets you. Don't look down.");
                candidates.add("End spawn. The void stares back.");
                candidates.add("Welcome to the endgame. Literally.");
                candidates.add("The End welcomes you. Or does it?");
                candidates.add("Endless islands and purple sky. The End awaits.");
            } else {
                candidates.add("The void hums. The dragon remembers.");
                candidates.add("End stone beneath your feet. Don't look down.");
                candidates.add("Endermen wander aimlessly. Don't make eye contact.");
                candidates.add("Morning in the End. The sky is still purple.");
                candidates.add("Another cycle in the void. Time is meaningless here.");
                candidates.add("The End's eternal twilight greets you.");
            }

            int y = player.getLocation().getBlockY();
            if (y < 50) {
                candidates.add("Low in the End. The void is close.");
                candidates.add("Dangerously close to the void. One slip and you're gone.");
            }
            if (Math.abs(player.getLocation().getBlockX()) > 1000 || Math.abs(player.getLocation().getBlockZ()) > 1000) {
                candidates.add("The outer End islands. Far from the dragon's perch.");
                candidates.add("Out here, only chorus plants and shulkers keep you company.");
                candidates.add("Deep in the outer End. Elytra hunting grounds.");
                candidates.add("The distant islands. End cities await discovery.");
            }
        }
    }

    public static void addSpecialSituationMessages(Player player, List<String> candidates, MessageType type) {
        if (player.isGliding()) {
            candidates.add("Soaring through the sky! What a way to " + (type == MessageType.DAY ? "start the day" : "arrive") + ".");
            candidates.add("Gliding" + (type == MessageType.DAY ? " at dawn" : " in") + ". The world looks different from up here.");
            candidates.add("Wings spread, wind rushing by. Freedom!");
            candidates.add("Elytra active. The ground is optional.");
            candidates.add("Flying through the air with the greatest of ease!");
            candidates.add("Airborne and loving it. The sky is yours.");
        }

        if (player.getFireTicks() > 0) {
            candidates.add("You're on fire. This is fine. Everything is fine.");
            candidates.add("Burning" + (type == MessageType.DAY ? " at dawn" : "") + ". Not the warm start you wanted.");
            candidates.add("Hot hot hot! Find water or accept your crispy fate.");
            candidates.add("Currently combusting. As one does.");
            candidates.add("Fire hazard in progress. Stay calm. Panic a little.");
            candidates.add("Spontaneous combustion is not a lifestyle choice!");
        }

        if (player.isSneaking()) {
            candidates.add("Sneaking" + (type == MessageType.DAY ? " at sunrise" : " in") + ". Cautious start.");
            candidates.add("Crouching already. Stealth mode engaged.");
            candidates.add("Tip-toeing through the world. Quiet and careful.");
            candidates.add("Sneaky sneaky. The mobs can't see you. Well, they can.");
        }

        if (player.isSprinting()) {
            candidates.add("Already sprinting? Somewhere to be?");
            candidates.add("Running at full speed! The adventure can't wait.");
            candidates.add("Sprinting away. Escaping something or chasing something?");
            candidates.add("Fast feet! Hunger drains but speed gains.");
        }

        if (player.isSwimming()) {
            candidates.add("Swimming through the world. Dolphin mode!");
            candidates.add("Aquatic locomotion engaged. Swim swim swim.");
            candidates.add("Cutting through the water. Speed swimmer!");
            candidates.add("The swim animation is active. Fancy.");
        }

        if (player.isRiptiding()) {
            candidates.add("Riptide ACTIVATED! Flying through the rain!");
            candidates.add("Trident power! The storm is your friend.");
            candidates.add("Woooosh! Riptide carries you through the sky.");
        }
    }

    public static void addJoinSpecificMessages(Player player, World world, List<String> candidates, String displayName) {
        // First join ever
        if (!player.hasPlayedBefore()) {
            candidates.clear(); // Override everything
            candidates.add("Welcome to the server, " + displayName + "! Your adventure begins now!");
            candidates.add("A new player joins! Welcome, " + displayName + "!");
            candidates.add("First time here, " + displayName + "? Let the journey begin!");
            candidates.add("Fresh face alert! " + displayName + " enters the world!");
            return;
        }

        // Time-based messages
        long time = world.getTime();
        if (time >= 0 && time < 6000) {
            candidates.add("Good morning! The sun is young and so is your adventure.");
            candidates.add("You join us at dawn. Perfect timing!");
            candidates.add("Early bird! The morning air is fresh and full of promise.");
            candidates.add("Sunrise session! The world is waking up with you.");
            candidates.add("Morning arrival! Coffee not included, but adventure is.");
            candidates.add("Welcome to the dawn patrol! Early risers unite.");
        } else if (time >= 6000 && time < 12000) {
            candidates.add("Afternoon! The sun is high, perfect for building.");
            candidates.add("You've arrived mid-day. The mobs slumber for now.");
            candidates.add("Midday join! The sun watches over your adventures.");
            candidates.add("Perfect timing! The day is in full swing.");
            candidates.add("High noon arrival. The world is bright and safe.");
            candidates.add("Joining during the safest hours. Smart move.");
        } else if (time >= 12000 && time < 18000) {
            candidates.add("Evening approaches. The monsters stir...");
            candidates.add("Sunset welcomes you. Find shelter soon!");
            candidates.add("You join as dusk falls. Hope you brought torches.");
            candidates.add("Golden hour arrival. Beautiful but dangerous soon.");
            candidates.add("The sun sets on your arrival. Night approaches fast.");
            candidates.add("Twilight session! The day ends, the adventure doesn't.");
        } else {
            candidates.add("You brave the night! The monsters are active.");
            candidates.add("Joining at night? Bold. Very bold.");
            candidates.add("The moon watches your arrival. So do the mobs.");
            candidates.add("Midnight session! The darkness has teeth.");
            candidates.add("Night owl! The stars welcome you to danger.");
            candidates.add("Arriving under moonlight. Hope you packed weapons.");
        }
    }

    // ========================================
    // HIGH-VARIETY CONTEXT HELPERS (NEW)
    // ========================================

    public static void addNearbyEntityMessages(Player player, World world, List<String> candidates) {
        java.util.Collection<org.bukkit.entity.Entity> nearby = world.getNearbyEntities(
                player.getLocation(),
                30, 30, 30,
                entity -> entity instanceof org.bukkit.entity.LivingEntity && !(entity instanceof Player)
        );

        if (nearby.isEmpty()) {
            return;
        }

        // Count entities by type
        java.util.Map<org.bukkit.entity.EntityType, Integer> counts = new java.util.HashMap<>();
        for (org.bukkit.entity.Entity entity : nearby) {
            counts.merge(entity.getType(), 1, Integer::sum);
        }

        // Generate messages based on counts
        for (java.util.Map.Entry<org.bukkit.entity.EntityType, Integer> entry : counts.entrySet()) {
            int count = entry.getValue();
            org.bukkit.entity.EntityType type = entry.getKey();

            switch (type) {
                case CHICKEN -> {
                    if (count >= 20) {
                        candidates.add("The chickens are making quite the racket this morning.");
                        candidates.add("Your chicken farm is thriving!");
                        candidates.add(count + " chickens! That's a lot of eggs.");
                        candidates.add("Cluck cluck cluck! The chicken army assembles.");
                        candidates.add("Feathered friends everywhere. Egg production at maximum!");
                        candidates.add("The chicken population is thriving. Feathers for days.");
                    }
                }
                case COW -> {
                    if (count >= 10) {
                        candidates.add("The cows are mooing for their breakfast.");
                        candidates.add("The cattle lowing fills the air.");
                        candidates.add("Your herd of " + count + " cows grazes peacefully.");
                        candidates.add("Moo! The cows greet the day.");
                        candidates.add("Leather and beef on the hoof. The herd prospers.");
                        candidates.add("Happy cows make happy farmers. And steak.");
                    }
                }
                case SHEEP -> {
                    if (count >= 10) {
                        candidates.add("The sheep have multiplied overnight. Again.");
                        candidates.add("Your flock grazes peacefully nearby.");
                        candidates.add(count + " sheep! Time for a shearing session.");
                        candidates.add("Baaaa! The sheep chorus begins.");
                        candidates.add("Wool for days. The flock is flourishing.");
                        candidates.add("Colorful or plain, your sheep are everywhere.");
                    }
                }
                case PIG -> {
                    if (count >= 8) {
                        candidates.add("The pigs snuffle around looking for breakfast.");
                        candidates.add("Your pig farm oinks with life.");
                        candidates.add(count + " pigs nearby. Bacon for days.");
                        candidates.add("Oink oink! The pigs are thriving.");
                        candidates.add("Pink and plump. The pig population grows.");
                        candidates.add("Mud and contentment. The pigs live well.");
                    }
                }
                case VILLAGER -> {
                    if (count >= 3) {
                        candidates.add("The villagers are already up and trading.");
                        candidates.add("Your neighbors greet the dawn with their usual 'Hmm.'");
                        candidates.add(count + " villagers nearby. The trading hall is active.");
                        candidates.add("Hmm! Hrm! The villagers discuss their trades.");
                        candidates.add("A bustling village nearby. Commerce never sleeps.");
                        candidates.add("The trading district is alive with villagers.");
                    }
                }
                case CAT -> {
                    if (count >= 2) {
                        candidates.add("The cats are napping in the sun. Join them?");
                        candidates.add("Your cats watch you with knowing eyes.");
                        candidates.add("The cats have taken over. You just live here now.");
                        candidates.add("Meow! Your feline friends are everywhere.");
                        candidates.add("Cat cafe vibes. They rule this domain.");
                        candidates.add("Purrfect company. The cats approve of you.");
                    }
                }
                case WOLF -> {
                    if (count >= 2) {
                        candidates.add("Your dogs are happy to see you. As always.");
                        candidates.add("The pack greets the new day with tail wags.");
                        candidates.add(count + " loyal companions guard your home.");
                        candidates.add("Woof! Your dogs are excited to see you!");
                        candidates.add("Best friends with four legs. The wolves are loyal.");
                        candidates.add("A pack of goodest boys. Tail wags all around.");
                    }
                }
                case HORSE -> {
                    if (count >= 2) {
                        candidates.add("Your horses neigh a good morning.");
                        candidates.add("The stables are full and ready.");
                        candidates.add(count + " horses stand ready for adventure.");
                        candidates.add("Noble steeds await your command.");
                        candidates.add("The horse farm is thriving. Speed on demand.");
                        candidates.add("Hooves stamp impatiently. Your horses want to run.");
                    }
                }
                case IRON_GOLEM -> {
                    if (count >= 1) {
                        candidates.add("The iron golem stands watch. Silently. Judging.");
                        candidates.add("Your guardian watches over the morning.");
                        candidates.add("Metal protector nearby. The village is safe.");
                        candidates.add("The iron golem offers you a poppy. Wait, no it doesn't.");
                    }
                }
                case ZOMBIE -> {
                    if (count >= 1) {
                        candidates.add("Leftover mobs from the night lurk nearby. Clean up duty!");
                        candidates.add("Some zombies didn't burn in time. Weapon up.");
                        candidates.add("Groaning nearby. Zombies survived the dawn.");
                        candidates.add("Undead presence detected. Time to deal with it.");
                    }
                }
                case SKELETON -> {
                    if (count >= 1) {
                        candidates.add("A skeleton survived the sunrise. Deal with it before it deals with you.");
                        candidates.add("Skeletons nearby. Hope you brought a shield.");
                        candidates.add("Rattling bones nearby. The skeleton awaits.");
                        candidates.add("Bony archer in the vicinity. Watch your health.");
                    }
                }
                case CREEPER -> {
                    if (count >= 1) {
                        candidates.add("A creeper survived the night. Hunt it down before it hunts you.");
                        candidates.add("Creeper detected nearby. Stay alert.");
                        candidates.add("That hissing sound... is it real or imagined?");
                        candidates.add("Green and mean. A creeper lurks somewhere.");
                    }
                }
                case BEE -> {
                    if (count >= 3) {
                        candidates.add("Bzzz! The bees are busy as always.");
                        candidates.add("Your bee population is buzzing along nicely.");
                        candidates.add(count + " bees! Honey production is booming.");
                        candidates.add("The bees dance their waggle dance nearby.");
                    }
                }
                case PARROT -> {
                    if (count >= 1) {
                        candidates.add("Colorful parrots chatter nearby!");
                        candidates.add("Your parrot friends squawk a greeting.");
                        candidates.add("Tropical companions at your side.");
                    }
                }
                case AXOLOTL -> {
                    if (count >= 1) {
                        candidates.add("Axolotls splash in the water nearby!");
                        candidates.add("Cute aquatic friends detected. Must protect.");
                        candidates.add("The axolotls smile their eternal smile.");
                    }
                }
                case ENDERMAN -> {
                    if (count >= 1) {
                        candidates.add("An enderman lurks nearby. Don't. Make. Eye contact.");
                        candidates.add("Tall, dark, and teleporting. An enderman is close.");
                        candidates.add("Purple particles nearby. Enderman in the area.");
                    }
                }
            }
        }
    }

    public static void addInventoryStateMessages(Player player, List<String> candidates) {
        org.bukkit.inventory.PlayerInventory inventory = player.getInventory();

        // Count filled slots
        int filledSlots = 0;
        int foodItems = 0;
        int diamonds = 0;
        int emeralds = 0;
        int netheriteIngots = 0;
        int shulkerBoxes = 0;
        int enderPearls = 0;
        int rockets = 0;

        for (org.bukkit.inventory.ItemStack item : inventory.getContents()) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                filledSlots++;

                org.bukkit.Material type = item.getType();
                int amount = item.getAmount();

                // Count specific items
                if (type == org.bukkit.Material.DIAMOND) {
                    diamonds += amount;
                } else if (type == org.bukkit.Material.EMERALD) {
                    emeralds += amount;
                } else if (type == org.bukkit.Material.NETHERITE_INGOT) {
                    netheriteIngots += amount;
                } else if (type.name().contains("SHULKER_BOX")) {
                    shulkerBoxes += amount;
                } else if (type == org.bukkit.Material.ENDER_PEARL) {
                    enderPearls += amount;
                } else if (type == org.bukkit.Material.FIREWORK_ROCKET) {
                    rockets += amount;
                } else if (type.isEdible()) {
                    foodItems += amount;
                }
            }
        }

        // Full inventory messages
        if (filledSlots >= 33) {
            candidates.add("Your inventory is nearly full. Time to organize?");
            candidates.add("27 slots. 27 problems. Wait, 36. Wait, more with armor.");
            candidates.add("One day you'll organize. Today is not that day.");
            candidates.add("Inventory bursting at the seams. Shulker box time?");
            candidates.add("Packed to the brim. The hoarder life chose you.");
            candidates.add("So many items, so few slots. The eternal struggle.");
        }

        // Low food messages
        if (foodItems <= 2) {
            candidates.add("Running low on food. Time to raid the farm.");
            candidates.add("Your food supply is dangerously low.");
            candidates.add("Almost out of food! Emergency farming required.");
            candidates.add("Food stocks critical. Even rotten flesh is looking good.");
        }

        // Valuable items messages
        if (diamonds >= 16) {
            candidates.add("Your pockets are heavy with diamonds. Riches!");
            candidates.add(diamonds + " diamonds in your inventory. Enchanting time?");
            candidates.add("Diamond hoarder detected. Treat yourself to some gear.");
            candidates.add("Blue wealth jingles in your inventory. Fancy.");
        }

        if (emeralds >= 32) {
            candidates.add("All those emeralds. The villagers are waiting.");
            candidates.add(emeralds + " emeralds! The trading hall calls.");
            candidates.add("Green and gleaming. Villager money overflowing.");
            candidates.add("Trading currency at maximum. Go shopping!");
        }

        if (netheriteIngots >= 4) {
            candidates.add("Your netherite stash is impressive.");
            candidates.add(netheriteIngots + " netherite ingots. The endgame approaches.");
            candidates.add("Ancient debris paid off. Netherite ready.");
            candidates.add("The rarest ingots. Your mining dedication shows.");
        }

        if (shulkerBoxes >= 4) {
            candidates.add("Your shulker box collection grows. Organization level: expert.");
            candidates.add("Shulker boxes! Portable storage is the best storage.");
            candidates.add(shulkerBoxes + " shulker boxes. Mobile inventory champion.");
            candidates.add("Boxes within boxes. Inception-level storage.");
        }

        if (enderPearls >= 16) {
            candidates.add("Ready to teleport at a moment's notice. Nice pearl collection.");
            candidates.add(enderPearls + " ender pearls. The Enderman farm paid off.");
            candidates.add("Pearl-rich and ready for teleportation shenanigans.");
            candidates.add("Escape routes in your pocket. Pearl power!");
        }

        if (rockets >= 64) {
            candidates.add("Loaded up with rockets. Time to fly!");
            candidates.add(rockets + " rockets! The sky is calling.");
            candidates.add("Firework fuel for days. Elytra adventures await.");
            candidates.add("Rocket-rich! The skies are your highway.");
        }

        if (diamonds >= 64) {
            candidates.add("A full stack of diamonds! You're rich beyond measure.");
            candidates.add("Diamond millionaire status achieved.");
        }

        if (emeralds >= 128) {
            candidates.add("Emerald magnate! The villagers respect your wealth.");
            candidates.add("Over a hundred emeralds. Trading empire incoming.");
        }
    }

    public static void addNearbyBlockMessages(Player player, World world, List<String> candidates) {
        org.bukkit.Location playerLoc = player.getLocation();
        int radius = 20;
        int sampleRate = 3; // Check every 3rd block to avoid expensive full scan

        // Counters for different block types
        boolean hasFurnace = false;
        boolean hasBlastFurnace = false;
        boolean hasSmoker = false;
        boolean hasBrewingStand = false;
        boolean hasEnchantingTable = false;
        boolean hasAnvil = false;
        boolean hasBeacon = false;
        int bedCount = 0;
        int chestCount = 0;

        boolean hasWheat = false;
        boolean hasCarrots = false;
        boolean hasPotatoes = false;
        boolean hasBeetroot = false;
        boolean hasNetherWart = false;
        boolean hasMelon = false;
        boolean hasPumpkin = false;
        boolean hasSugarCane = false;
        boolean hasBamboo = false;
        boolean hasFullyGrownCrops = false;
        int farmlandCount = 0;

        // Sample blocks in radius
        for (int x = -radius; x <= radius; x += sampleRate) {
            for (int y = -radius / 2; y <= radius / 2; y += sampleRate) {
                for (int z = -radius; z <= radius; z += sampleRate) {
                    org.bukkit.Location loc = playerLoc.clone().add(x, y, z);
                    org.bukkit.block.Block block = world.getBlockAt(loc);
                    org.bukkit.Material type = block.getType();

                    // Functional blocks
                    switch (type) {
                        case FURNACE -> hasFurnace = true;
                        case BLAST_FURNACE -> hasBlastFurnace = true;
                        case SMOKER -> hasSmoker = true;
                        case BREWING_STAND -> hasBrewingStand = true;
                        case ENCHANTING_TABLE -> hasEnchantingTable = true;
                        case ANVIL, CHIPPED_ANVIL, DAMAGED_ANVIL -> hasAnvil = true;
                        case BEACON -> hasBeacon = true;
                        case CHEST, TRAPPED_CHEST -> chestCount++;
                    }

                    // Bed detection (any bed color)
                    if (type.name().contains("_BED")) {
                        bedCount++;
                    }

                    // Crops
                    switch (type) {
                        case WHEAT -> {
                            hasWheat = true;
                            if (block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
                                if (ageable.getAge() == ageable.getMaximumAge()) {
                                    hasFullyGrownCrops = true;
                                }
                            }
                        }
                        case CARROTS -> {
                            hasCarrots = true;
                            if (block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
                                if (ageable.getAge() == ageable.getMaximumAge()) {
                                    hasFullyGrownCrops = true;
                                }
                            }
                        }
                        case POTATOES -> {
                            hasPotatoes = true;
                            if (block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
                                if (ageable.getAge() == ageable.getMaximumAge()) {
                                    hasFullyGrownCrops = true;
                                }
                            }
                        }
                        case BEETROOTS -> {
                            hasBeetroot = true;
                            if (block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
                                if (ageable.getAge() == ageable.getMaximumAge()) {
                                    hasFullyGrownCrops = true;
                                }
                            }
                        }
                        case NETHER_WART -> hasNetherWart = true;
                        case MELON -> hasMelon = true;
                        case PUMPKIN, CARVED_PUMPKIN -> hasPumpkin = true;
                        case SUGAR_CANE -> hasSugarCane = true;
                        case BAMBOO -> hasBamboo = true;
                        case FARMLAND -> farmlandCount++;
                    }
                }
            }
        }

        // Generate messages for functional blocks
        if (hasFurnace) {
            candidates.add("The furnaces have cooled overnight.");
            candidates.add("Time to fire up the smelters.");
            candidates.add("Furnaces nearby. Got anything to smelt?");
            candidates.add("The smell of coal and progress. Smelting station ready.");
        }
        if (hasBlastFurnace) {
            candidates.add("The blast furnace awaits your ores.");
            candidates.add("Industrial smelting at your fingertips.");
            candidates.add("Double-speed ore processing. Efficiency!");
        }
        if (hasSmoker) {
            candidates.add("The smoker is ready for a fresh catch.");
            candidates.add("Food processing station online. Hungry?");
            candidates.add("Fast food Minecraft style. The smoker awaits.");
        }
        if (hasBrewingStand) {
            candidates.add("The brewing stand bubbles. Alchemy awaits.");
            candidates.add("Time to brew some potions?");
            candidates.add("Potions await creation. The brewing stand is ready.");
            candidates.add("Alchemy station detected. What will you concoct?");
        }
        if (hasEnchantingTable) {
            candidates.add("Your enchanting table is calling.");
            candidates.add("The enchantment table hums with magical energy.");
            candidates.add("Magical energies swirl around the enchanting table.");
            candidates.add("Bookshelves and power. Enchanting awaits.");
        }
        if (hasAnvil) {
            candidates.add("Your anvil is one repair closer to breaking.");
            candidates.add("The anvil stands ready for repairs.");
            candidates.add("Repairs and combinations await at the anvil.");
            candidates.add("Metal on metal. The anvil is your friend.");
        }
        if (hasBeacon) {
            candidates.add("The beacon's light shines bright. You've made it.");
            candidates.add("Your beacon pulses with power.");
            candidates.add("Beacon buffs active. You're in the zone.");
            candidates.add("The pyramid of power serves you well.");
        }
        if (bedCount >= 3) {
            candidates.add("Multiple beds nearby. Quite the sleeping quarters.");
            candidates.add("Your bedroom is well-stocked with backup beds.");
            candidates.add("Beds everywhere. Spawn point options galore.");
            candidates.add("A bed for every mood. Luxury sleeping arrangements.");
        }
        if (chestCount >= 20) {
            candidates.add("Your storage system is... extensive.");
            candidates.add("All those chests. Somewhere, an item waits to be found.");
            candidates.add("Storage empire detected. Where's that one item again?");
            candidates.add("Chests on chests on chests. Organization optional.");
        }

        // Generate messages for crops
        if (hasFullyGrownCrops) {
            candidates.add("The crops are fully grown. Harvest time!");
            candidates.add("Your farm is ready to harvest.");
            candidates.add("Golden fields await your harvesting hands.");
            candidates.add("Crop maturity achieved. Get harvesting!");
        }
        if (hasWheat) {
            candidates.add("The wheat looks ready to harvest.");
            candidates.add("Golden wheat waves in the breeze.");
            candidates.add("Bread ingredients growing nicely.");
            candidates.add("Wheat fields. The staple of Minecraft cuisine.");
        }
        if (hasCarrots) {
            candidates.add("The carrots are growing nicely.");
            candidates.add("Time to check on the carrot patch.");
            candidates.add("Orange goodness in the ground. Carrot time.");
            candidates.add("Rabbit food and golden carrot ingredients.");
        }
        if (hasPotatoes) {
            candidates.add("The potato farm thrives.");
            candidates.add("Your potatoes are coming along well.");
            candidates.add("Starchy goodness underground. Potato power!");
            candidates.add("Baked potatoes incoming. The spuds look great.");
        }
        if (hasBeetroot) {
            candidates.add("The beetroots add color to your farm.");
            candidates.add("Red roots in the earth. Beetroot harvest soon.");
            candidates.add("Soup ingredients growing. Beetroot is underrated.");
        }
        if (hasNetherWart) {
            candidates.add("Your nether wart farm is producing.");
            candidates.add("The nether wart grows in its crimson glory.");
            candidates.add("Potion base material thriving. Nether wart is essential.");
            candidates.add("Red and warty. The alchemy ingredients grow.");
        }
        if (hasMelon) {
            candidates.add("The melons are ripe for harvest.");
            candidates.add("Green and juicy. Melon slices await.");
            candidates.add("Glistering melon ingredients. Or just snacks.");
        }
        if (hasPumpkin) {
            candidates.add("Pumpkins dot your farm. Autumn vibes year-round.");
            candidates.add("Orange gourds await. Jack-o-lanterns? Pumpkin pie?");
            candidates.add("Spooky season is always in season here.");
        }
        if (hasSugarCane) {
            candidates.add("Sugar cane grows tall by the water.");
            candidates.add("Paper and sugar. The versatile cane thrives.");
            candidates.add("Tall stalks of productivity. Sugar cane is essential.");
        }
        if (hasBamboo) {
            candidates.add("The bamboo forest rustles nearby.");
            candidates.add("Green stalks everywhere. Scaffolding material galore.");
            candidates.add("Pandas would love it here. Bamboo paradise.");
        }
        if (farmlandCount >= 10) {
            candidates.add("Your farm stretches out. The farmer's life.");
            candidates.add("Tending the land. Honest work.");
            candidates.add("Acres of farmland. Agriculture at scale.");
            candidates.add("The farming operation is impressive. Well done.");
        }
    }

    // ========================================
    // NEW CONTEXT TYPES
    // ========================================

    public static void addPotionEffectMessages(Player player, List<String> candidates) {
        var activeEffects = player.getActivePotionEffects();

        for (var effect : activeEffects) {
            var type = effect.getType();
            int amplifier = effect.getAmplifier();
            String level = amplifier > 0 ? " " + (amplifier + 1) : "";

            if (type.equals(org.bukkit.potion.PotionEffectType.SPEED)) {
                candidates.add("Speed" + level + " courses through your veins. Zoom!");
                candidates.add("You're feeling speedy. The world blurs by.");
            } else if (type.equals(org.bukkit.potion.PotionEffectType.STRENGTH)) {
                candidates.add("Strength" + level + " empowers your swings. Mighty!");
                candidates.add("Your muscles surge with power. The mobs should worry.");
            } else if (type.equals(org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE)) {
                candidates.add("Fire resistance active. Lava is just a warm bath today.");
                candidates.add("Flames cannot touch you. Time to swim in the Nether?");
            } else if (type.equals(org.bukkit.potion.PotionEffectType.INVISIBILITY)) {
                candidates.add("You're invisible! The mobs can't see you. Usually.");
                candidates.add("Ghost mode activated. Don't forget to remove your armor.");
            } else if (type.equals(org.bukkit.potion.PotionEffectType.NIGHT_VISION)) {
                candidates.add("Night vision reveals all. The darkness holds no secrets.");
                candidates.add("You see clearly in the dark. The caves are your domain.");
            } else if (type.equals(org.bukkit.potion.PotionEffectType.WATER_BREATHING)) {
                candidates.add("You breathe underwater. The ocean is your playground.");
                candidates.add("Water breathing active. Time to explore the depths!");
            } else if (type.equals(org.bukkit.potion.PotionEffectType.SLOW_FALLING)) {
                candidates.add("Slow falling protects you from gravity's cruelty.");
                candidates.add("You float like a feather. Fall damage? Never heard of it.");
            } else if (type.equals(org.bukkit.potion.PotionEffectType.REGENERATION)) {
                candidates.add("Regeneration pulses through you. Wounds close on their own.");
                candidates.add("Your health regenerates rapidly. Practically immortal.");
            } else if (type.equals(org.bukkit.potion.PotionEffectType.RESISTANCE)) {
                candidates.add("Resistance hardens your skin. Attacks barely scratch you.");
                candidates.add("You're tougher than usual. Bring on the damage!");
            } else if (type.equals(org.bukkit.potion.PotionEffectType.JUMP_BOOST)) {
                candidates.add("Jump boost! The sky is closer than you think.");
                candidates.add("You spring like a rabbit. Wheee!");
            } else if (type.equals(org.bukkit.potion.PotionEffectType.HASTE)) {
                candidates.add("Haste makes your tools sing. Mining goes brrrrr.");
                candidates.add("Your hands move faster. Efficiency incarnate.");
            }
        }
    }

    public static void addDurabilityMessages(Player player, List<String> candidates) {
        var inventory = player.getInventory();

        // Check main hand
        var mainHand = inventory.getItemInMainHand();
        if (mainHand.getType().getMaxDurability() > 0) {
            var meta = mainHand.getItemMeta();
            if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                int maxDurability = mainHand.getType().getMaxDurability();
                int remaining = maxDurability - damageable.getDamage();
                double percentRemaining = (double) remaining / maxDurability;

                if (percentRemaining <= 0.1) {
                    candidates.add("Your " + formatItemName(mainHand.getType()) + " is about to break! Find an anvil fast.");
                    candidates.add("That tool has seen better days. One more use might be its last.");
                } else if (percentRemaining <= 0.25) {
                    candidates.add("Your " + formatItemName(mainHand.getType()) + " is looking worn. Repair it soon.");
                    candidates.add("Low durability warning on your tool. Mending or anvil time?");
                }
            }
        }

        // Check armor pieces
        org.bukkit.inventory.ItemStack[] armor = {
                inventory.getHelmet(), inventory.getChestplate(),
                inventory.getLeggings(), inventory.getBoots()
        };
        String[] armorNames = {"helmet", "chestplate", "leggings", "boots"};

        for (int i = 0; i < armor.length; i++) {
            var piece = armor[i];
            if (piece != null && piece.getType().getMaxDurability() > 0) {
                var meta = piece.getItemMeta();
                if (meta instanceof org.bukkit.inventory.meta.Damageable damageable) {
                    int maxDurability = piece.getType().getMaxDurability();
                    int remaining = maxDurability - damageable.getDamage();
                    double percentRemaining = (double) remaining / maxDurability;

                    if (percentRemaining <= 0.1) {
                        candidates.add("Your " + armorNames[i] + " is nearly destroyed! One hit could shatter it.");
                    } else if (percentRemaining <= 0.2) {
                        candidates.add("Your " + armorNames[i] + " needs repair. Don't let it break mid-fight.");
                    }
                }
            }
        }
    }

    public static void addBedStatusMessages(Player player, List<String> candidates) {
        var spawnLocation = player.getBedSpawnLocation();

        if (spawnLocation == null) {
            candidates.add("No spawn point set! Sleep in a bed before something goes wrong.");
            candidates.add("You're spawn point is at world spawn. Risky business.");
            candidates.add("Find a bed and claim it. Respawning at spawn is never fun.");
        } else if (player.getWorld().equals(spawnLocation.getWorld())) {
            // Only calculate distance if in the same world
            double distance = player.getLocation().distance(spawnLocation);
            if (distance > 5000) {
                candidates.add("You're " + (int) distance + " blocks from your bed. That's quite the journey back if you die.");
                candidates.add("Your spawn is far away. Consider setting a new one nearby.");
            } else if (distance > 1000) {
                candidates.add("Your bed is over a thousand blocks away. Long walk if things go wrong.");
            }
        }
    }

    public static void addSaturationMessages(Player player, List<String> candidates) {
        float saturation = player.getSaturation();
        int foodLevel = player.getFoodLevel();

        if (saturation <= 0 && foodLevel > 6) {
            candidates.add("No saturation left. You'll start getting hungry soon.");
            candidates.add("Running on empty calories. Eat something filling!");
        } else if (saturation >= 10 && foodLevel >= 18) {
            candidates.add("Well-fed with excellent saturation. You won't need food for a while.");
            candidates.add("Maximum saturation achieved. The steak was worth it.");
        }
    }

    public static void addBuildingMaterialMessages(Player player, List<String> candidates) {
        var inventory = player.getInventory();

        int cobblestone = 0;
        int wood = 0;
        int glass = 0;
        int stone = 0;
        int bricks = 0;
        int deepslate = 0;

        for (var item : inventory.getContents()) {
            if (item == null) continue;
            var type = item.getType();
            int amount = item.getAmount();

            if (type == org.bukkit.Material.COBBLESTONE) cobblestone += amount;
            else if (type == org.bukkit.Material.STONE) stone += amount;
            else if (type == org.bukkit.Material.DEEPSLATE || type == org.bukkit.Material.COBBLED_DEEPSLATE) deepslate += amount;
            else if (type == org.bukkit.Material.GLASS) glass += amount;
            else if (type == org.bukkit.Material.BRICKS) bricks += amount;
            else if (type.name().contains("_LOG") || type.name().contains("_PLANKS")) wood += amount;
        }

        if (cobblestone >= 256) {
            candidates.add("Loaded up with cobblestone. Time to build something massive!");
            candidates.add(cobblestone + " cobblestone in your bags. Castle time?");
        } else if (cobblestone >= 64) {
            candidates.add("Your pockets are full of cobblestone. The classic building block.");
        }

        if (wood >= 128) {
            candidates.add("Stocked up on wood. A builder's dream inventory.");
            candidates.add("All that lumber! Time to construct something cozy.");
        }

        if (glass >= 32) {
            candidates.add("Glass in hand. Windows for your builds await.");
            candidates.add("Plenty of glass. Let there be light in your structures!");
        }

        if (stone >= 128) {
            candidates.add("Smooth stone at the ready. Clean builds incoming.");
        }

        if (deepslate >= 64) {
            candidates.add("Deepslate for that modern dungeon aesthetic.");
        }

        if (bricks >= 64) {
            candidates.add("Bricks in your inventory. Classic building vibes.");
        }
    }

    public static void addRareDropMessages(Player player, List<String> candidates) {
        var inventory = player.getInventory();

        boolean hasWitherSkull = false;
        boolean hasDragonEgg = false;
        boolean hasHeartOfSea = false;
        boolean hasTotem = false;
        boolean hasNetherStar = false;
        boolean hasBeaconItem = false;
        boolean hasEnchantedGoldenApple = false;

        for (var item : inventory.getContents()) {
            if (item == null) continue;
            var type = item.getType();

            if (type == org.bukkit.Material.WITHER_SKELETON_SKULL) hasWitherSkull = true;
            else if (type == org.bukkit.Material.DRAGON_EGG) hasDragonEgg = true;
            else if (type == org.bukkit.Material.HEART_OF_THE_SEA) hasHeartOfSea = true;
            else if (type == org.bukkit.Material.TOTEM_OF_UNDYING) hasTotem = true;
            else if (type == org.bukkit.Material.NETHER_STAR) hasNetherStar = true;
            else if (type == org.bukkit.Material.BEACON) hasBeaconItem = true;
            else if (type == org.bukkit.Material.ENCHANTED_GOLDEN_APPLE) hasEnchantedGoldenApple = true;
        }

        if (hasWitherSkull) {
            candidates.add("A wither skull in your possession. Planning something dangerous?");
            candidates.add("Wither skull secured. Two more and you can summon doom.");
        }
        if (hasDragonEgg) {
            candidates.add("You carry the dragon egg. The rarest trophy in the game.");
            candidates.add("Dragon egg in your inventory! Guard it with your life.");
        }
        if (hasHeartOfSea) {
            candidates.add("Heart of the Sea pulses in your bag. Conduit power awaits.");
            candidates.add("You found a Heart of the Sea! The ocean's blessing.");
        }
        if (hasTotem) {
            candidates.add("Totem of Undying at the ready. Death isn't final today.");
            candidates.add("A totem could save your life. Keep it close.");
        }
        if (hasNetherStar) {
            candidates.add("A nether star gleams in your inventory. Beacon time!");
            candidates.add("You slayed the Wither! The star is your reward.");
        }
        if (hasBeaconItem) {
            candidates.add("Beacon in your bags. Where will you place it?");
        }
        if (hasEnchantedGoldenApple) {
            candidates.add("A Notch apple! Save it for emergencies. Or eat it now. Your call.");
            candidates.add("Enchanted golden apple. The ultimate panic button.");
        }
    }

    public static void addBrewingIngredientMessages(Player player, List<String> candidates) {
        var inventory = player.getInventory();

        int blazePowder = 0;
        int netherWart = 0;
        int ghastTears = 0;
        int magmaCream = 0;
        int spiderEyes = 0;
        int phantomMembrane = 0;
        int glowstoneDust = 0;
        int redstoneDust = 0;
        int dragonBreath = 0;

        for (var item : inventory.getContents()) {
            if (item == null) continue;
            var type = item.getType();
            int amount = item.getAmount();

            if (type == org.bukkit.Material.BLAZE_POWDER) blazePowder += amount;
            else if (type == org.bukkit.Material.NETHER_WART) netherWart += amount;
            else if (type == org.bukkit.Material.GHAST_TEAR) ghastTears += amount;
            else if (type == org.bukkit.Material.MAGMA_CREAM) magmaCream += amount;
            else if (type == org.bukkit.Material.SPIDER_EYE) spiderEyes += amount;
            else if (type == org.bukkit.Material.PHANTOM_MEMBRANE) phantomMembrane += amount;
            else if (type == org.bukkit.Material.GLOWSTONE_DUST) glowstoneDust += amount;
            else if (type == org.bukkit.Material.REDSTONE) redstoneDust += amount;
            else if (type == org.bukkit.Material.DRAGON_BREATH) dragonBreath += amount;
        }

        if (blazePowder >= 16 && netherWart >= 16) {
            candidates.add("Brewing supplies ready. Time to stock up on potions!");
            candidates.add("Blaze powder and nether wart. The alchemist is prepared.");
        }

        if (ghastTears >= 4) {
            candidates.add("Ghast tears for regeneration potions. Hard to get, worth the effort.");
        }

        if (phantomMembrane >= 8) {
            candidates.add("Phantom membranes for slow falling. The skies are safer now.");
        }

        if (dragonBreath >= 3) {
            candidates.add("Dragon's breath for lingering potions. Advanced alchemy.");
        }

        if (glowstoneDust >= 16 && redstoneDust >= 16) {
            candidates.add("Glowstone and redstone for enhanced potions. Quality brewing ahead.");
        }
    }

    public static void addMusicDiscMessages(Player player, List<String> candidates) {
        var inventory = player.getInventory();

        java.util.List<String> discs = new java.util.ArrayList<>();

        for (var item : inventory.getContents()) {
            if (item == null) continue;
            var type = item.getType();

            if (type.name().startsWith("MUSIC_DISC_")) {
                String discName = type.name().replace("MUSIC_DISC_", "").toLowerCase();
                discs.add(discName);
            }
        }

        if (!discs.isEmpty()) {
            if (discs.size() >= 3) {
                candidates.add("Music disc collection growing! " + discs.size() + " discs and counting.");
                candidates.add("A DJ in the making. Your jukebox awaits these treasures.");
            } else if (discs.contains("pigstep")) {
                candidates.add("Pigstep! The rarest disc. Absolute banger.");
                candidates.add("You have Pigstep. Your taste in music is impeccable.");
            } else if (discs.contains("otherside")) {
                candidates.add("Otherside disc in your possession. The vibes are immaculate.");
            } else {
                candidates.add("A music disc! Time to find a jukebox.");
                candidates.add("You're carrying some tunes. Pop it in a jukebox!");
            }
        }
    }

    public static void addNearLavaMessages(Player player, World world, List<String> candidates) {
        var loc = player.getLocation();
        int radius = 5;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    var block = world.getBlockAt(loc.clone().add(x, y, z));
                    if (block.getType() == org.bukkit.Material.LAVA) {
                        candidates.add("Lava nearby. One wrong step and your items are toast.");
                        candidates.add("The heat of lava radiates nearby. Stay alert.");
                        candidates.add("Molten rock bubbles close by. Watch your footing.");
                        return; // Only add once
                    }
                }
            }
        }
    }

    public static void addNearSpawnerMessages(Player player, World world, List<String> candidates) {
        var loc = player.getLocation();
        int radius = 10;

        for (int x = -radius; x <= radius; x += 2) {
            for (int y = -radius; y <= radius; y += 2) {
                for (int z = -radius; z <= radius; z += 2) {
                    var block = world.getBlockAt(loc.clone().add(x, y, z));
                    if (block.getType() == org.bukkit.Material.SPAWNER) {
                        if (block.getState() instanceof org.bukkit.block.CreatureSpawner spawner) {
                            var spawnedType = spawner.getSpawnedType();
                            String mobName = spawnedType != null ? spawnedType.name().toLowerCase().replace("_", " ") : "unknown mob";
                            candidates.add("A " + mobName + " spawner lurks nearby. Farm it or break it?");
                            candidates.add("Spawner detected! Free mob grinder material.");
                        } else {
                            candidates.add("A monster spawner nearby. Opportunity or danger?");
                        }
                        return;
                    }
                }
            }
        }
    }

    public static void addNearPortalMessages(Player player, World world, List<String> candidates) {
        var loc = player.getLocation();
        int radius = 15;

        boolean foundNetherPortal = false;
        boolean foundEndPortal = false;
        boolean foundEndGateway = false;

        for (int x = -radius; x <= radius; x += 3) {
            for (int y = -radius; y <= radius; y += 3) {
                for (int z = -radius; z <= radius; z += 3) {
                    var block = world.getBlockAt(loc.clone().add(x, y, z));
                    var type = block.getType();

                    if (type == org.bukkit.Material.NETHER_PORTAL) foundNetherPortal = true;
                    else if (type == org.bukkit.Material.END_PORTAL) foundEndPortal = true;
                    else if (type == org.bukkit.Material.END_GATEWAY) foundEndGateway = true;
                }
            }
        }

        if (foundNetherPortal) {
            candidates.add("The nether portal hums with energy. Another dimension awaits.");
            candidates.add("Purple swirls nearby. The Nether beckons.");
        }
        if (foundEndPortal) {
            candidates.add("The End portal is active. The dragon awaits your challenge.");
            candidates.add("Void energy emanates from the portal. Are you ready?");
        }
        if (foundEndGateway) {
            candidates.add("An End gateway glimmers. The outer islands call to you.");
            candidates.add("Gateway to the outer End detected. Shulkers and elytra await.");
        }
    }

    public static void addLightLevelMessages(Player player, World world, List<String> candidates) {
        var loc = player.getLocation();
        int lightLevel = loc.getBlock().getLightLevel();
        int skyLight = loc.getBlock().getLightFromSky();

        // Only check in overworld for mob spawning concerns
        if (world.getEnvironment() == World.Environment.NORMAL) {
            if (lightLevel == 0) {
                candidates.add("Complete darkness surrounds you. Mobs will spawn here.");
                candidates.add("Light level zero. Place a torch before something spawns on you.");
            } else if (lightLevel <= 7 && skyLight == 0) {
                candidates.add("It's dangerously dark here. Hostile mobs might spawn.");
                candidates.add("Low light warning. You might want some torches.");
            }
        }
    }

    public static void addFlowerFieldMessages(Player player, World world, List<String> candidates) {
        var loc = player.getLocation();
        int radius = 10;
        int flowerCount = 0;

        for (int x = -radius; x <= radius; x += 2) {
            for (int z = -radius; z <= radius; z += 2) {
                var block = world.getBlockAt(loc.clone().add(x, 0, z));
                // Check a couple Y levels
                for (int y = -2; y <= 2; y++) {
                    var checkBlock = world.getBlockAt(loc.clone().add(x, y, z));
                    var type = checkBlock.getType();
                    if (isFlower(type)) {
                        flowerCount++;
                    }
                }
            }
        }

        if (flowerCount >= 15) {
            candidates.add("Flowers bloom all around you. What a beautiful spot!");
            candidates.add("A field of flowers. The bees must love it here.");
            candidates.add("Nature's garden surrounds you. Take a moment to appreciate it.");
        } else if (flowerCount >= 8) {
            candidates.add("Wildflowers dot the landscape. Peaceful.");
            candidates.add("A lovely flower patch nearby. Good for dyes!");
        }
    }

    public static void addCombatReadyMessages(Player player, List<String> candidates) {
        var inventory = player.getInventory();
        var mainHand = inventory.getItemInMainHand();
        var offHand = inventory.getItemInOffHand();
        var helmet = inventory.getHelmet();
        var chestplate = inventory.getChestplate();
        var leggings = inventory.getLeggings();
        var boots = inventory.getBoots();

        boolean hasSword = mainHand.getType().name().contains("SWORD");
        boolean hasAxe = mainHand.getType().name().contains("_AXE");
        boolean hasShield = offHand.getType() == org.bukkit.Material.SHIELD;
        boolean hasFullArmor = helmet != null && chestplate != null && leggings != null && boots != null;

        boolean hasDiamondOrBetter = false;
        if (hasFullArmor) {
            for (var piece : new org.bukkit.inventory.ItemStack[]{helmet, chestplate, leggings, boots}) {
                String name = piece.getType().name();
                if (name.contains("DIAMOND") || name.contains("NETHERITE")) {
                    hasDiamondOrBetter = true;
                    break;
                }
            }
        }

        if ((hasSword || hasAxe) && hasShield && hasFullArmor) {
            candidates.add("Battle-ready! Weapon drawn, shield raised, armor equipped.");
            candidates.add("Full combat loadout. The mobs don't stand a chance.");
            candidates.add("Armed to the teeth. Looking for trouble?");
        } else if ((hasSword || hasAxe) && hasShield) {
            candidates.add("Sword and shield combo. Classic warrior setup.");
            candidates.add("Ready for a fight with your trusty shield.");
        } else if (hasFullArmor && hasDiamondOrBetter) {
            candidates.add("Diamond armor or better on all slots. Walking tank.");
            candidates.add("Fully armored and ready for whatever comes.");
        }
    }

    private static boolean isFlower(org.bukkit.Material type) {
        return switch (type) {
            case DANDELION, POPPY, BLUE_ORCHID, ALLIUM, AZURE_BLUET,
                 RED_TULIP, ORANGE_TULIP, WHITE_TULIP, PINK_TULIP,
                 OXEYE_DAISY, CORNFLOWER, LILY_OF_THE_VALLEY,
                 TORCHFLOWER, PINK_PETALS, SUNFLOWER, LILAC,
                 ROSE_BUSH, PEONY, PITCHER_PLANT, WITHER_ROSE -> true;
            default -> false;
        };
    }

    private static String formatItemName(org.bukkit.Material material) {
        return material.name().toLowerCase().replace("_", " ");
    }
}
