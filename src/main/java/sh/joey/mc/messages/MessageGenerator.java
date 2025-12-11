package sh.joey.mc.messages;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import static sh.joey.mc.messages.WordBanks.*;

/**
 * Centralized message generation system used by DayMessageProvider, JoinMessageProvider,
 * and ServerPingProvider. Combines static messages, procedural templates, and context-aware
 * generation with shared word banks.
 */
public final class MessageGenerator {

    private static final Random random = ThreadLocalRandom.current();

    private MessageGenerator() {}

    // ========================================
    // MAIN GENERATION METHODS
    // ========================================

    /**
     * Generates a message for the start of a Minecraft day.
     * Uses full context (player state, location, world state).
     */
    public static String generateDayMessage(Player player) {
        int roll = random.nextInt(100);

        if (roll < 15) {
            String contextMessage = getPlayerContextMessage(player, MessageType.DAY);
            if (contextMessage != null) {
                return contextMessage;
            }
            return pickDayMessage();
        } else if (roll < 45) {
            return getProceduralMessage(MessageType.DAY);
        } else {
            return pickDayMessage();
        }
    }

    /**
     * Generates a welcome message for a player joining the server.
     * Uses full context but with a welcoming tone.
     */
    public static String generateJoinMessage(Player player) {
        int roll = random.nextInt(100);

        if (roll < 30) {
            String contextMessage = getPlayerContextMessage(player, MessageType.JOIN);
            if (contextMessage != null) {
                return contextMessage;
            }
            return pickJoinMessage(player);
        } else if (roll < 60) {
            return getProceduralMessage(MessageType.JOIN);
        } else {
            return pickJoinMessage(player);
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

    private static String pickJoinMessage(Player player) {
        // 30% chance of personalized message
        if (random.nextInt(100) < 30) {
            return getPersonalizedGreeting(player);
        }
        return pick(JOIN_MESSAGES);
    }

    private static String pickMotdMessage() {
        return pick(MOTD_MESSAGES);
    }

    private static String getPersonalizedGreeting(Player player) {
        String name = player.getName();
        List<String> personalized = List.of(
                "Welcome, " + name + "! The server has been expecting you.",
                name + " has arrived! Let the adventures begin.",
                "All hail " + name + ", builder of worlds!",
                name + "! Your presence graces us once more.",
                "The legendary " + name + " returns!",
                name + " joins the fray!",
                "Look who's here! It's " + name + "!",
                name + ", the blocks await your command."
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
        World world = player.getWorld();
        List<String> candidates = new ArrayList<>();
        List<String> biomeCandidates = new ArrayList<>();  // Separate list for biome (lower priority)

        // ===== HIGH-VARIETY CONTEXTS (prioritized) =====
        addNearbyEntityMessages(player, world, candidates);
        addInventoryStateMessages(player, candidates);
        addNearbyBlockMessages(player, world, candidates);

        // ===== PLAYER STATE =====
        addHealthMessages(player, candidates);
        addHungerMessages(player, candidates);
        addExperienceMessages(player, candidates);
        addArmorMessages(player, candidates);
        addHeldItemMessages(player, candidates, type);

        // ===== LOCATION STATE =====
        addYLevelMessages(player, candidates);
        addUndergroundMessages(player, world, candidates, type);
        addWaterMessages(player, candidates, type);
        addVehicleMessages(player, candidates, type);

        // ===== WORLD STATE =====
        if (type == MessageType.DAY) {
            addDayMilestoneMessages(world, candidates);
            addDifficultyMessages(world, candidates);
            addMoonPhaseMessages(world, candidates);
        }
        addWeatherMessages(world, candidates, type);

        // ===== BIOME-SPECIFIC (lower priority - fallback only) =====
        addBiomeMessages(player, world, biomeCandidates);

        // ===== DIMENSION-SPECIFIC =====
        addDimensionMessages(player, world, candidates, type);

        // ===== SPECIAL SITUATIONS =====
        addSpecialSituationMessages(player, candidates, type);

        // ===== JOIN-SPECIFIC =====
        if (type == MessageType.JOIN) {
            addJoinSpecificMessages(player, world, candidates);
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

    private static void addHealthMessages(Player player, List<String> candidates) {
        double health = player.getHealth();
        double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();

        if (health <= 4) {
            candidates.add("You're barely alive. Maybe eat something?");
            candidates.add("Your health is critical. Today might be short.");
            candidates.add("Two hearts left. Living dangerously, I see.");
        } else if (health >= maxHealth) {
            candidates.add("Full health! You're ready for anything.");
            candidates.add("Peak condition. The mobs should be worried.");
        }
    }

    private static void addHungerMessages(Player player, List<String> candidates) {
        int foodLevel = player.getFoodLevel();

        if (foodLevel <= 6) {
            candidates.add("Your stomach growls. Feed yourself, adventurer.");
            candidates.add("Hunger gnaws at you. Time to eat.");
            candidates.add("Running on empty. Find food before the sprint fails.");
        } else if (foodLevel >= 20) {
            candidates.add("Well fed and ready to go!");
        }
    }

    private static void addExperienceMessages(Player player, List<String> candidates) {
        int level = player.getLevel();

        if (level >= 30) {
            candidates.add("Level " + level + "! Time to enchant something.");
            candidates.add("All that XP is burning a hole in your pocket.");
            candidates.add("You're glowing with experience. Literally.");
        } else if (level == 0) {
            candidates.add("Starting fresh with zero XP. The grind begins.");
        }
    }

    private static void addArmorMessages(Player player, List<String> candidates) {
        var helmet = player.getInventory().getHelmet();
        var chestplate = player.getInventory().getChestplate();

        if (helmet == null && chestplate == null) {
            candidates.add("No armor? Bold strategy. Let's see if it pays off.");
            candidates.add("Unarmored and unafraid. Or just unprepared.");
        } else if (chestplate != null) {
            String type = chestplate.getType().name();
            if (type.contains("NETHERITE")) {
                candidates.add("Netherite armor gleams. You've made it.");
                candidates.add("Decked in netherite. The endgame is now.");
            } else if (type.contains("DIAMOND")) {
                candidates.add("Diamond armor shines in the morning light.");
            } else if (type.contains("IRON")) {
                candidates.add("Iron armor. Reliable. Respectable.");
            } else if (type.contains("LEATHER")) {
                candidates.add("Leather armor. It's a start!");
            } else if (type.contains("GOLD")) {
                candidates.add("Gold armor. Stylish but fragile. Very fragile.");
            } else if (chestplate.getType() == org.bukkit.Material.ELYTRA) {
                candidates.add("Elytra equipped. The sky is yours today.");
                candidates.add("Wings ready. Where will you fly?");
            }
        }
    }

    private static void addHeldItemMessages(Player player, List<String> candidates, MessageType type) {
        var mainHand = player.getInventory().getItemInMainHand();
        String itemType = mainHand.getType().name();

        if (itemType.equals("DIAMOND_PICKAXE")) {
            candidates.add("Diamond pickaxe in hand. The caves await.");
        } else if (itemType.equals("NETHERITE_PICKAXE")) {
            candidates.add("Netherite pickaxe ready. Nothing can stop you.");
        } else if (itemType.equals("FISHING_ROD")) {
            candidates.add("Fishing rod at the ready. A peaceful " + (type == MessageType.DAY ? "day" : "session") + " ahead?");
        } else if (itemType.contains("SWORD")) {
            candidates.add("Sword drawn" + (type == MessageType.DAY ? " at dawn" : "") + ". Expecting trouble?");
        } else if (itemType.equals("BOW") || itemType.equals("CROSSBOW")) {
            candidates.add("Ranged weapon ready. Smart choice.");
        } else if (itemType.equals("SHIELD")) {
            candidates.add("Shield up. A defensive start" + (type == MessageType.DAY ? " to the day" : "") + ".");
        } else if (itemType.equals("TRIDENT")) {
            candidates.add("Trident in hand. Poseidon would be proud.");
        }
    }

    private static void addYLevelMessages(Player player, List<String> candidates) {
        int y = player.getLocation().getBlockY();

        if (y < -50) {
            candidates.add("Deep in the world. Diamonds lurk at this depth.");
            candidates.add("Y level " + y + ". The deep dark isn't far.");
            candidates.add("This far down, even the stone changes.");
        } else if (y < 0) {
            candidates.add("Below sea level. The deepslate zone.");
            candidates.add("Negative Y coordinates. The depths welcome you.");
        } else if (y > 200) {
            candidates.add("High in the sky! Don't look down.");
            candidates.add("Y level " + y + ". The clouds are below you.");
            candidates.add("Mountain peak living. The air is thin up here.");
        } else if (y > 100) {
            candidates.add("Above the clouds. The view must be incredible.");
        }
    }

    private static void addUndergroundMessages(Player player, World world, List<String> candidates, MessageType type) {
        int highestBlockY = world.getHighestBlockYAt(player.getLocation());

        if (player.getLocation().getBlockY() < highestBlockY - 10) {
            if (type == MessageType.DAY) {
                candidates.add("Underground at dawn. The sun rises without you.");
                candidates.add("Waking up in a cave. Classic miner lifestyle.");
                candidates.add("No sunrise for you today. Just stone and ore.");
            } else {
                candidates.add("Underground already? Efficient.");
                candidates.add("You're deep below. The surface is overrated anyway.");
            }
        }
    }

    private static void addWaterMessages(Player player, List<String> candidates, MessageType type) {
        if (player.isInWater()) {
            if (type == MessageType.DAY) {
                candidates.add("Starting the day in water. Refreshing!");
                candidates.add("You're soaking wet. Interesting sleeping arrangements.");
            } else {
                candidates.add("You're... in the water. Interesting spawn point.");
            }
        }
    }

    private static void addVehicleMessages(Player player, List<String> candidates, MessageType type) {
        if (player.isInsideVehicle()) {
            var vehicle = player.getVehicle();
            if (vehicle != null) {
                String vehicleType = vehicle.getType().name().toLowerCase().replace("_", " ");
                String timeRef = type == MessageType.DAY ? " at dawn" : "";

                candidates.add("Good " + (type == MessageType.DAY ? "morning" : "to see you") + " from your " + vehicleType + "!");

                if (vehicle.getType() == org.bukkit.entity.EntityType.HORSE) {
                    candidates.add("Your horse greets the " + (type == MessageType.DAY ? "dawn" : "server") + " with you.");
                } else if (vehicleType.contains("boat")) {
                    candidates.add((type == MessageType.DAY ? "Dawn breaks over the water. " : "") + "Peaceful sailing ahead.");
                } else if (vehicle.getType() == org.bukkit.entity.EntityType.PIG) {
                    candidates.add("Riding a pig" + timeRef + ". Living your best life.");
                } else if (vehicle.getType() == org.bukkit.entity.EntityType.STRIDER) {
                    candidates.add("Your strider warbles happily. Well, probably happily.");
                } else if (vehicleType.contains("minecart")) {
                    candidates.add("Riding the rails" + timeRef + ". Efficient transportation.");
                }
            }
        }
    }

    private static void addDayMilestoneMessages(World world, List<String> candidates) {
        long dayNumber = (world.getFullTime() / 24000) + 1;

        if (dayNumber == 1) {
            candidates.add("Day 1. Your journey begins!");
            candidates.add("The first day. Everything is possible.");
        } else if (dayNumber == 100) {
            candidates.add("Day 100! You've been here a while.");
        } else if (dayNumber == 365) {
            candidates.add("Day 365. A full Minecraft year!");
        } else if (dayNumber == 1000) {
            candidates.add("Day 1,000. You're basically a local legend.");
        } else if (dayNumber % 100 == 0) {
            candidates.add("Day " + dayNumber + ". Another milestone!");
        } else if (dayNumber <= 3) {
            candidates.add("Still early days. Survive, then thrive.");
        }
    }

    private static void addDifficultyMessages(World world, List<String> candidates) {
        switch (world.getDifficulty()) {
            case PEACEFUL -> {
                candidates.add("Peaceful mode. Relax, no monsters today.");
                candidates.add("A calm world awaits. No hostile mobs here.");
            }
            case HARD -> {
                candidates.add("Hard mode. The mobs hit harder. So should you.");
                candidates.add("Hard difficulty. Every mistake costs more.");
            }
        }
    }

    private static void addMoonPhaseMessages(World world, List<String> candidates) {
        int moonPhase = (int) ((world.getFullTime() / 24000) % 8);

        switch (moonPhase) {
            case 0 -> {
                candidates.add("Full moon tonight. The undead will be restless.");
                candidates.add("A full moon rises tonight. The slimes rejoice.");
                candidates.add("Full moon ahead. Extra mobs, extra loot. Probably.");
            }
            case 4 -> {
                candidates.add("New moon tonight. Darkness will be absolute.");
                candidates.add("New moon rises. Even the stars seem brighter.");
                candidates.add("Tonight brings a new moon. The shadows grow bold.");
            }
            case 1, 7 -> candidates.add("A gibbous moon tonight. The tides of adventure flow.");
            case 2, 6 -> candidates.add("Half moon tonight. Balance in all things.");
            case 3, 5 -> candidates.add("A crescent moon tonight. Subtle, like the diamonds you seek.");
        }
    }

    private static void addWeatherMessages(World world, List<String> candidates, MessageType type) {
        if (world.hasStorm()) {
            if (world.isThundering()) {
                if (type == MessageType.DAY) {
                    candidates.add("Thunder rumbles across the land. The charged creepers stir.");
                    candidates.add("A storm rages. Perhaps stay indoors today?");
                    candidates.add("Lightning crackles in the sky. Free mob heads, anyone?");
                } else {
                    candidates.add("You arrive amid thunder! Dramatic entrance.");
                    candidates.add("Lightning crackles to herald your return!");
                }
            } else {
                if (type == MessageType.DAY) {
                    candidates.add("Rain falls gently. A good day to tend the crops.");
                    candidates.add("The rain begins. Perfect fishing weather!");
                    candidates.add("Gray skies today. The zombies won't burn, so stay alert.");
                } else {
                    candidates.add("Rain greets your arrival. Cozy vibes.");
                    candidates.add("You join during a storm. Perfect fishing weather!");
                }
            }
        } else if (type == MessageType.DAY) {
            candidates.add("Clear skies ahead. The sun will keep you safe.");
            candidates.add("Beautiful weather today. The mobs will burn nicely.");
            candidates.add("Sunny skies smile upon your endeavors.");
        }
    }

    private static void addBiomeMessages(Player player, World world, List<String> candidates) {
        var biome = world.getBiome(player.getLocation());
        String biomeName = biome.getKey().getKey().toLowerCase();

        // Overworld biomes
        if (biomeName.contains("desert")) {
            candidates.add("The desert sun beats down. Watch out for husks.");
            candidates.add("Sand stretches in every direction. Pyramids hide treasures.");
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
            candidates.add("Terracotta towers catch the light. Gold hides in these hills.");
            candidates.add("The badlands bake under the sun. Mineshafts thread through the terrain.");
        } else if (biomeName.contains("deep_dark")) {
            candidates.add("Sculk spreads in the darkness. The Warden listens.");
            candidates.add("The silence here is deafening. Stay quiet. Stay alive.");
        } else if (biomeName.contains("lush")) {
            candidates.add("Axolotls splash in the pools. The lush caves welcome you.");
            candidates.add("Glow berries illuminate the cavern. Nature thrives below.");
        }

        // Nether biomes
        if (world.getEnvironment() == World.Environment.NETHER) {
            if (biomeName.contains("soul_sand") || biomeName.contains("soul sand")) {
                candidates.add("Soul sand slows your steps. Ghasts wail overhead.");
                candidates.add("The valley of souls. Blue fire flickers in the darkness.");
            } else if (biomeName.contains("warped")) {
                candidates.add("The warped forest glows blue. Endermen lurk here.");
                candidates.add("Warped fungi tower overhead. An alien landscape.");
            } else if (biomeName.contains("crimson")) {
                candidates.add("The crimson forest pulses with red light. Hoglins grunt nearby.");
                candidates.add("Red and hostile. The crimson forest doesn't welcome visitors.");
            } else if (biomeName.contains("basalt")) {
                candidates.add("The basalt deltas crackle. Watch your step.");
                candidates.add("Basalt pillars and magma cubes. Treacherous terrain.");
            } else if (biomeName.contains("wastes")) {
                candidates.add("Nether wastes stretch endlessly. Classic hellscape.");
            }
        }
    }

    private static void addDimensionMessages(Player player, World world, List<String> candidates, MessageType type) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            if (type == MessageType.JOIN) {
                candidates.add("You spawn in the Nether?! Brave choice.");
                candidates.add("Welcome to the hot place. Watch your step.");
            } else {
                candidates.add("Another day in the Nether. Try not to catch fire.");
                candidates.add("The Nether's heat is unrelenting. At least ghasts provide ambiance.");
                candidates.add("Lava flows in rivers below. The Nether never sleeps.");
            }
        } else if (world.getEnvironment() == World.Environment.THE_END) {
            if (type == MessageType.JOIN) {
                candidates.add("You return to the End. The dragon remembers you.");
                candidates.add("The void greets you. Don't look down.");
            } else {
                candidates.add("The void hums. The dragon remembers.");
                candidates.add("End stone beneath your feet. Don't look down.");
                candidates.add("Endermen wander aimlessly. Don't make eye contact.");
            }

            int y = player.getLocation().getBlockY();
            if (y < 50) {
                candidates.add("Low in the End. The void is close.");
            }
            if (Math.abs(player.getLocation().getBlockX()) > 1000 || Math.abs(player.getLocation().getBlockZ()) > 1000) {
                candidates.add("The outer End islands. Far from the dragon's perch.");
                candidates.add("Out here, only chorus plants and shulkers keep you company.");
            }
        }
    }

    private static void addSpecialSituationMessages(Player player, List<String> candidates, MessageType type) {
        if (player.isGliding()) {
            candidates.add("Soaring through the sky! What a way to " + (type == MessageType.DAY ? "start the day" : "arrive") + ".");
            candidates.add("Gliding" + (type == MessageType.DAY ? " at dawn" : " in") + ". The world looks different from up here.");
        }

        if (player.getFireTicks() > 0) {
            candidates.add("You're on fire. This is fine. Everything is fine.");
            candidates.add("Burning" + (type == MessageType.DAY ? " at dawn" : "") + ". Not the warm start you wanted.");
        }

        if (player.isSneaking()) {
            candidates.add("Sneaking" + (type == MessageType.DAY ? " at sunrise" : " in") + ". Cautious start.");
        }

        if (player.isSprinting()) {
            candidates.add("Already sprinting? Somewhere to be?");
        }
    }

    private static void addJoinSpecificMessages(Player player, World world, List<String> candidates) {
        // First join ever
        if (!player.hasPlayedBefore()) {
            candidates.clear(); // Override everything
            candidates.add("Welcome to the server, " + player.getName() + "! Your adventure begins now!");
            return;
        }

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
        }
    }

    // ========================================
    // HIGH-VARIETY CONTEXT HELPERS (NEW)
    // ========================================

    private static void addNearbyEntityMessages(Player player, World world, List<String> candidates) {
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
                    }
                }
                case COW -> {
                    if (count >= 10) {
                        candidates.add("The cows are mooing for their breakfast.");
                        candidates.add("The cattle lowing fills the air.");
                        candidates.add("Your herd of " + count + " cows grazes peacefully.");
                    }
                }
                case SHEEP -> {
                    if (count >= 10) {
                        candidates.add("The sheep have multiplied overnight. Again.");
                        candidates.add("Your flock grazes peacefully nearby.");
                        candidates.add(count + " sheep! Time for a shearing session.");
                    }
                }
                case PIG -> {
                    if (count >= 8) {
                        candidates.add("The pigs snuffle around looking for breakfast.");
                        candidates.add("Your pig farm oinks with life.");
                        candidates.add(count + " pigs nearby. Bacon for days.");
                    }
                }
                case VILLAGER -> {
                    if (count >= 3) {
                        candidates.add("The villagers are already up and trading.");
                        candidates.add("Your neighbors greet the dawn with their usual 'Hmm.'");
                        candidates.add(count + " villagers nearby. The trading hall is active.");
                    }
                }
                case CAT -> {
                    if (count >= 2) {
                        candidates.add("The cats are napping in the sun. Join them?");
                        candidates.add("Your cats watch you with knowing eyes.");
                        candidates.add("The cats have taken over. You just live here now.");
                    }
                }
                case WOLF -> {
                    if (count >= 2) {
                        candidates.add("Your dogs are happy to see you. As always.");
                        candidates.add("The pack greets the new day with tail wags.");
                        candidates.add(count + " loyal companions guard your home.");
                    }
                }
                case HORSE -> {
                    if (count >= 2) {
                        candidates.add("Your horses neigh a good morning.");
                        candidates.add("The stables are full and ready.");
                        candidates.add(count + " horses stand ready for adventure.");
                    }
                }
                case IRON_GOLEM -> {
                    if (count >= 1) {
                        candidates.add("The iron golem stands watch. Silently. Judging.");
                        candidates.add("Your guardian watches over the morning.");
                    }
                }
                case ZOMBIE -> {
                    if (count >= 1) {
                        candidates.add("Leftover mobs from the night lurk nearby. Clean up duty!");
                        candidates.add("Some zombies didn't burn in time. Weapon up.");
                    }
                }
                case SKELETON -> {
                    if (count >= 1) {
                        candidates.add("A skeleton survived the sunrise. Deal with it before it deals with you.");
                        candidates.add("Skeletons nearby. Hope you brought a shield.");
                    }
                }
                case CREEPER -> {
                    if (count >= 1) {
                        candidates.add("A creeper survived the night. Hunt it down before it hunts you.");
                        candidates.add("Creeper detected nearby. Stay alert.");
                    }
                }
            }
        }
    }

    private static void addInventoryStateMessages(Player player, List<String> candidates) {
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
        }

        // Low food messages
        if (foodItems <= 2) {
            candidates.add("Running low on food. Time to raid the farm.");
            candidates.add("Your food supply is dangerously low.");
        }

        // Valuable items messages
        if (diamonds >= 16) {
            candidates.add("Your pockets are heavy with diamonds. Riches!");
            candidates.add(diamonds + " diamonds in your inventory. Enchanting time?");
        }

        if (emeralds >= 32) {
            candidates.add("All those emeralds. The villagers are waiting.");
            candidates.add(emeralds + " emeralds! The trading hall calls.");
        }

        if (netheriteIngots >= 4) {
            candidates.add("Your netherite stash is impressive.");
            candidates.add(netheriteIngots + " netherite ingots. The endgame approaches.");
        }

        if (shulkerBoxes >= 4) {
            candidates.add("Your shulker box collection grows. Organization level: expert.");
        }

        if (enderPearls >= 16) {
            candidates.add("Ready to teleport at a moment's notice. Nice pearl collection.");
            candidates.add(enderPearls + " ender pearls. The Enderman farm paid off.");
        }

        if (rockets >= 64) {
            candidates.add("Loaded up with rockets. Time to fly!");
            candidates.add(rockets + " rockets! The sky is calling.");
        }
    }

    private static void addNearbyBlockMessages(Player player, World world, List<String> candidates) {
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
        }
        if (hasBlastFurnace) {
            candidates.add("The blast furnace awaits your ores.");
        }
        if (hasSmoker) {
            candidates.add("The smoker is ready for a fresh catch.");
        }
        if (hasBrewingStand) {
            candidates.add("The brewing stand bubbles. Alchemy awaits.");
            candidates.add("Time to brew some potions?");
        }
        if (hasEnchantingTable) {
            candidates.add("Your enchanting table is calling.");
            candidates.add("The enchantment table hums with magical energy.");
        }
        if (hasAnvil) {
            candidates.add("Your anvil is one repair closer to breaking.");
            candidates.add("The anvil stands ready for repairs.");
        }
        if (hasBeacon) {
            candidates.add("The beacon's light shines bright. You've made it.");
            candidates.add("Your beacon pulses with power.");
        }
        if (bedCount >= 3) {
            candidates.add("Multiple beds nearby. Quite the sleeping quarters.");
            candidates.add("Your bedroom is well-stocked with backup beds.");
        }
        if (chestCount >= 20) {
            candidates.add("Your storage system is... extensive.");
            candidates.add("All those chests. Somewhere, an item waits to be found.");
        }

        // Generate messages for crops
        if (hasFullyGrownCrops) {
            candidates.add("The crops are fully grown. Harvest time!");
            candidates.add("Your farm is ready to harvest.");
        }
        if (hasWheat) {
            candidates.add("The wheat looks ready to harvest.");
            candidates.add("Golden wheat waves in the breeze.");
        }
        if (hasCarrots) {
            candidates.add("The carrots are growing nicely.");
            candidates.add("Time to check on the carrot patch.");
        }
        if (hasPotatoes) {
            candidates.add("The potato farm thrives.");
            candidates.add("Your potatoes are coming along well.");
        }
        if (hasBeetroot) {
            candidates.add("The beetroots add color to your farm.");
        }
        if (hasNetherWart) {
            candidates.add("Your nether wart farm is producing.");
            candidates.add("The nether wart grows in its crimson glory.");
        }
        if (hasMelon) {
            candidates.add("The melons are ripe for harvest.");
        }
        if (hasPumpkin) {
            candidates.add("Pumpkins dot your farm. Autumn vibes year-round.");
        }
        if (hasSugarCane) {
            candidates.add("Sugar cane grows tall by the water.");
        }
        if (hasBamboo) {
            candidates.add("The bamboo forest rustles nearby.");
        }
        if (farmlandCount >= 10) {
            candidates.add("Your farm stretches out. The farmer's life.");
            candidates.add("Tending the land. Honest work.");
        }
    }
}
