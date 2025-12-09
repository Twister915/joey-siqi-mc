package sh.joey.mc.day;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Sends a themed message to players at the start of each Minecraft day.
 * Uses a mix of static messages, procedural templates, and context-aware messages.
 */
public final class DayMessageProvider implements Disposable {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.GOLD)
            .append(Component.text("\u2600").color(NamedTextColor.YELLOW)) // ☀
            .append(Component.text("] ").color(NamedTextColor.GOLD));

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Random random = ThreadLocalRandom.current();

    // Track which worlds we've already sent messages for this day
    private final Map<UUID, Long> lastDayMessageTime = new HashMap<>();

    public DayMessageProvider(SiqiJoeyPlugin plugin) {
        // Periodic day detection (every second)
        disposables.add(plugin.interval(1, TimeUnit.SECONDS)
                .subscribe(tick -> checkDayTransitions()));

        // World events
        disposables.add(plugin.watchEvent(PlayerChangedWorldEvent.class)
                .subscribe(this::handleWorldChange));

        disposables.add(plugin.watchEvent(WorldUnloadEvent.class)
                .subscribe(event -> lastDayMessageTime.remove(event.getWorld().getUID())));
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    private void checkDayTransitions() {
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
    }

    private void handleWorldChange(PlayerChangedWorldEvent event) {
        World world = event.getPlayer().getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return;

        // If entering a world during dawn (first 2000 ticks of day), send a message
        long time = world.getTime();
        if (time >= 0 && time < 2000) {
            Player player = event.getPlayer();
            String message = generateMessage(player);
            player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.WHITE)));
        }
    }

    private void sendDayMessages(World world) {
        for (Player player : world.getPlayers()) {
            String message = generateMessage(player);
            player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.WHITE)));
        }
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
    // STATIC MESSAGES (~100 messages)
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
            "Your dogs are happy to see you. As always."
    );

    private String getStaticMessage() {
        return STATIC_MESSAGES.get(random.nextInt(STATIC_MESSAGES.size()));
    }

    // ========================================
    // PROCEDURAL TEMPLATES (~30 templates with word banks)
    // ========================================

    private String getProceduralMessage() {
        int template = random.nextInt(30);

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
            case 21 -> "A " + pick(MOBS) + " dreams of " + pick(TREASURES) + ". Don't let it have it.";
            case 22 -> "The " + pick(ORES) + " calls from the depths. Answer wisely.";
            case 23 -> "Why " + pick(ACTIVITIES) + " when you could " + pick(ACTIVITIES) + " instead?";
            case 24 -> "News from the " + pick(BIOMES) + ": a " + pick(ADJECTIVES) + " " + pick(MOBS) + " was spotted.";
            case 25 -> "The universe suggests you try " + pick(ADVICE) + ".";
            case 26 -> "Prophecy: The " + pick(MOBS) + " and the " + pick(ITEMS) + " will meet today.";
            case 27 -> "Deep in the " + pick(BIOMES) + ", " + pick(TREASURES) + " awaits the brave.";
            case 28 -> "A " + pick(ADJECTIVES) + " feeling hangs in the air. Time to " + pick(ACTIVITIES) + ".";
            case 29 -> "The " + pick(SOUNDS) + " suggest it's time to " + pick(ACTIVITIES) + ".";
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
            "finally organize that storage room",
            "hunt some phantoms",
            "build a mob grinder",
            "go adventuring in the Nether",
            "plant a forest",
            "create a water feature",
            "challenge a pillager outpost",
            "search for ancient debris",
            "build a cozy cabin",
            "make a redstone contraption",
            "explore a stronghold",
            "hunt for a music disc",
            "breed villagers",
            "build an XP farm",
            "decorate your base",
            "collect all the coral types",
            "find an amethyst geode",
            "try to befriend an allay",
            "build a lighthouse",
            "create an aquarium",
            "hunt the Warden (brave choice)",
            "make a beacon pyramid",
            "explore a trial chamber",
            "tame a parrot",
            "build a treehouse"
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
            "carrying a spare pickaxe",
            "enchanting before exploring",
            "keeping milk for witch fights",
            "always having an exit strategy",
            "checking behind you periodically",
            "never mining directly above you",
            "keeping food in your hotbar",
            "building with fire-resistant materials",
            "lighting up mob spawners before breaking them",
            "trading with librarians first",
            "curing zombie villagers for discounts",
            "keeping ender pearls for emergencies",
            "sneaking near sculk sensors",
            "always carrying a boat",
            "building with a plan",
            "taking breaks from the grind",
            "appreciating the small victories"
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
            "explosive",
            "enchanted",
            "haunted",
            "peaceful",
            "treacherous",
            "abandoned",
            "overgrown",
            "pristine",
            "crumbling",
            "radiant",
            "shadowy",
            "vibrant",
            "desolate",
            "cozy",
            "ominous",
            "whimsical",
            "foreboding"
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
            "lush cave",
            "trial chamber",
            "witch hut",
            "igloo",
            "buried treasure",
            "woodland mansion",
            "ocean ruin",
            "spawner room",
            "fossil",
            "ravine",
            "bastion remnant",
            "nether fortress",
            "end city",
            "ancient city",
            "dripstone cave"
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
            "secrets beneath your feet",
            "an ancient city in the deep dark",
            "a village waiting to be found",
            "netherite hidden in the wastes",
            "a fortress beyond the portal",
            "elytra in the outer End",
            "a trial chamber underground",
            "rare coral in shallow waters",
            "a jungle temple overgrown",
            "a skeleton spawner waiting",
            "gold in the badlands"
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
            "music disc",
            "netherite ingot",
            "mending book",
            "nether star",
            "heart of the sea",
            "enchanted golden apple",
            "trial key",
            "sniffer egg",
            "amethyst cluster",
            "end crystal",
            "shulker shell"
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
            "cure a zombie villager",
            "defeat the Wither",
            "reach the outer End islands",
            "build an ender pearl stasis chamber",
            "create a full shulker box collection",
            "max out a villager's trades",
            "find every advancement",
            "build an efficient gold farm",
            "create a mob switch",
            "find a pink sheep naturally",
            "get every potion effect at once",
            "build your dream storage system",
            "create a working clock tower",
            "beat the game without dying"
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
            "bee",
            "ghast",
            "blaze",
            "piglin",
            "hoglin",
            "shulker",
            "guardian",
            "wither skeleton",
            "slime",
            "magma cube",
            "ravager",
            "evoker",
            "vex",
            "allay",
            "axolotl",
            "goat",
            "frog",
            "sniffer"
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
            "hoe",
            "elytra",
            "totem",
            "compass",
            "map",
            "spyglass",
            "ender pearl",
            "golden apple",
            "firework rocket",
            "lead",
            "bucket"
    );

    private static final List<String> TOOLS = List.of(
            "pickaxe",
            "shovel",
            "axe",
            "hoe",
            "shears",
            "flint and steel",
            "fishing rod",
            "brush",
            "torch",
            "bucket",
            "lead",
            "compass",
            "clock",
            "spyglass"
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
            "badlands",
            "taiga",
            "plains",
            "birch forest",
            "flower forest",
            "snowy peaks",
            "frozen ocean",
            "mangrove swamp",
            "meadow",
            "stony shore",
            "warm ocean",
            "bamboo jungle",
            "old growth forest",
            "dripstone caves"
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
            "the silence before a creeper",
            "ghasts crying in the Nether",
            "sculk shrieking warnings",
            "axolotls splashing",
            "noteblocks playing",
            "pistons pushing",
            "enchanting table whispers",
            "end crystals humming",
            "portal whooshing",
            "furnaces crackling",
            "anvils clanging"
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
            "coal",
            "netherite",
            "nether gold",
            "nether quartz",
            "deepslate diamond",
            "raw copper",
            "raw iron",
            "raw gold",
            "amethyst",
            "glowstone"
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

        // Weather messages
        if (world.hasStorm()) {
            if (world.isThundering()) {
                candidates.add("Thunder rumbles across the land. The charged creepers stir.");
                candidates.add("A storm rages. Perhaps stay indoors today?");
                candidates.add("Lightning crackles in the sky. Free mob heads, anyone?");
                candidates.add("The thunder shakes the earth. Nature is angry.");
                candidates.add("Storm clouds gather overhead. Dramatic. Very dramatic.");
                candidates.add("Lightning illuminates the land. Channel the chaos.");
            } else {
                candidates.add("Rain falls gently. A good day to tend the crops.");
                candidates.add("The rain begins. Perfect fishing weather!");
                candidates.add("Gray skies today. The zombies won't burn, so stay alert.");
                candidates.add("Raindrops patter on the roof. Cozy building weather.");
                candidates.add("The rain washes the world clean. Fresh start energy.");
                candidates.add("Overcast skies. The mobs will linger longer today.");
            }
        } else {
            candidates.add("Clear skies ahead. The sun will keep you safe.");
            candidates.add("Beautiful weather today. The mobs will burn nicely.");
            candidates.add("Sunny skies smile upon your endeavors.");
            candidates.add("Not a cloud in sight. Perfect adventuring conditions.");
        }

        // Biome-specific messages
        var biome = world.getBiome(player.getLocation());
        String biomeName = biome.getKey().getKey().toLowerCase();

        if (biomeName.contains("desert")) {
            candidates.add("Another scorching day in the desert. The cacti judge you silently.");
            candidates.add("Sand stretches in every direction. Watch out for husks.");
            candidates.add("The desert sun beats down. Hydrate. With potions.");
            candidates.add("Pyramids and temples hide in these dunes. Explore wisely.");
        } else if (biomeName.contains("ocean")) {
            candidates.add("The sea stretches before you. Adventure awaits beneath the waves.");
            candidates.add("Ocean breeze fills the air. Perhaps a shipwreck hunt?");
            candidates.add("Waves lap at the shore. The guardians await in the deep.");
            candidates.add("Salt air and endless horizons. A good day to sail.");
        } else if (biomeName.contains("jungle")) {
            candidates.add("The jungle awakens. Parrots chatter, ocelots lurk.");
            candidates.add("Vines hang heavy with dew. A temple hides somewhere nearby.");
            candidates.add("Bamboo sways in the breeze. Pandas munch contentedly.");
            candidates.add("The jungle canopy filters the light. Treasures hide here.");
        } else if (biomeName.contains("swamp")) {
            candidates.add("Mist rises from the swamp. Witches brew in their huts.");
            candidates.add("The swamp gurgles. Slimes bounce in the murky waters.");
            candidates.add("Lily pads dot the water. Frogs croak their morning songs.");
            candidates.add("Blue orchids bloom in the muck. Beauty in unlikely places.");
        } else if (biomeName.contains("mountain") || biomeName.contains("peak")) {
            candidates.add("The mountain air is crisp. Goats leap on the cliffs above.");
            candidates.add("High altitude today. Emeralds hide in these peaks.");
            candidates.add("Snow-capped peaks glitter in the sunrise. Majestic.");
            candidates.add("The view from up here is breathtaking. Worth the climb.");
        } else if (biomeName.contains("snow") || biomeName.contains("ice") || biomeName.contains("frozen")) {
            candidates.add("Frost clings to everything. Bundle up!");
            candidates.add("Snow blankets the land. Strays lurk in the cold.");
            candidates.add("Ice formations sparkle in the light. Cold but beautiful.");
            candidates.add("Your breath fogs in the air. The tundra is unforgiving.");
        } else if (biomeName.contains("mushroom")) {
            candidates.add("Mycelium squishes underfoot. No hostile mobs here, just vibes.");
            candidates.add("The mooshrooms watch you with knowing eyes.");
            candidates.add("Giant mushrooms tower overhead. A strange but peaceful place.");
            candidates.add("Safe from monsters, surrounded by fungi. Living the dream.");
        } else if (biomeName.contains("dark_forest")) {
            candidates.add("The dark forest looms. Woodland mansions hide the vindictive.");
            candidates.add("Little light penetrates the canopy. Mobs may spawn even now.");
            candidates.add("Giant mushrooms mingle with dark oaks. Eerie but intriguing.");
            candidates.add("The forest watches back. Tread carefully.");
        } else if (biomeName.contains("cherry")) {
            candidates.add("Cherry blossoms drift on the breeze. Peaceful.");
            candidates.add("Pink petals carpet the ground. A beautiful day.");
            candidates.add("The cherry grove is serene. Perfect for a quiet build.");
            candidates.add("Pigs frolic among the pink trees. Idyllic.");
        } else if (biomeName.contains("badlands") || biomeName.contains("mesa")) {
            candidates.add("Terracotta towers catch the morning light. Gold hides in these hills.");
            candidates.add("The badlands bake under the sun. Mineshafts thread through the terrain.");
            candidates.add("Red and orange layers stripe the cliffs. Geological art.");
            candidates.add("Dead bushes tumble past. The wild west of Minecraft.");
        } else if (biomeName.contains("deep_dark")) {
            candidates.add("Sculk spreads in the darkness. The Warden listens.");
            candidates.add("You wake in the deep dark. Question your life choices.");
            candidates.add("Ancient cities lurk nearby. Treasure and terror await.");
            candidates.add("The silence here is deafening. Stay quiet. Stay alive.");
        } else if (biomeName.contains("lush")) {
            candidates.add("Axolotls splash in the pools. The lush caves welcome you.");
            candidates.add("Glow berries illuminate the cavern. Nature thrives below.");
            candidates.add("Moss carpets the cave floor. Life finds a way.");
            candidates.add("Dripleaf and azalea bloom underground. A hidden paradise.");
        } else if (biomeName.contains("taiga") || biomeName.contains("grove")) {
            candidates.add("Spruce trees stand tall. Wolves and foxes roam these woods.");
            candidates.add("The taiga is peaceful. Sweet berries grow on every bush.");
        } else if (biomeName.contains("plains") || biomeName.contains("meadow")) {
            candidates.add("Rolling plains stretch before you. Villages dot the horizon.");
            candidates.add("Grass sways in the wind. Simple, peaceful beauty.");
        } else if (biomeName.contains("flower")) {
            candidates.add("Flowers of every color surround you. The bees are thriving.");
            candidates.add("A rainbow of petals greets the morning. Nature's garden.");
        } else if (biomeName.contains("mangrove")) {
            candidates.add("Mangrove roots twist through the water. Frogs hide among them.");
            candidates.add("Mud squelches underfoot. The mangrove swamp is alive.");
        } else if (biomeName.contains("savanna")) {
            candidates.add("Acacia trees dot the golden grass. The savanna awakens.");
            candidates.add("The savanna stretches endlessly. Llamas graze in the distance.");
        }

        // World-specific messages
        if (world.getEnvironment() == World.Environment.NETHER) {
            candidates.add("Another day in the Nether. Try not to catch fire.");
            candidates.add("The Nether's heat is unrelenting. At least ghasts provide ambiance.");
            candidates.add("Netherrack stretches endlessly. Ancient debris hides in the depths.");
            candidates.add("Lava flows in rivers below. The Nether never sleeps.");
            candidates.add("Piglins barter in the distance. What will they want today?");
            candidates.add("The basalt deltas crackle. Watch your step.");
        } else if (world.getEnvironment() == World.Environment.THE_END) {
            candidates.add("The void hums. The dragon remembers.");
            candidates.add("End stone beneath your feet. Don't look down.");
            candidates.add("Endermen wander aimlessly. Don't make eye contact.");
            candidates.add("Chorus fruit grows on the outer islands. Sweet freedom.");
            candidates.add("The End is vast and empty. And somehow, beautiful.");
            candidates.add("Shulker cities float in the distance. Adventure awaits.");
        }

        if (candidates.isEmpty()) {
            return null;
        }

        return candidates.get(random.nextInt(candidates.size()));
    }
}
