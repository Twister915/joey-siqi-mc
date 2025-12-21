package sh.joey.mc.death;

import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Contains all custom death message variants organized by death type.
 * Messages use placeholders: {player} for victim, {killer} for attacker.
 */
public final class DeathMessages {
    private DeathMessages() {}

    private static final Random RANDOM = new Random();

    // Environmental deaths (no killer)
    private static final List<String> FALL = List.of(
            "{player} took the express elevator down",
            "{player} forgot about gravity",
            "{player} learned to fly (briefly)",
            "{player} didn't bounce",
            "{player} made a crater",
            "{player} discovered terminal velocity",
            "{player} went splat",
            "{player} trusted a water bucket too much"
    );

    private static final List<String> FALL_HIGH = List.of(
            "{player} fell from a great height",
            "{player} experienced rapid unplanned disassembly",
            "{player} found out how high is too high"
    );

    private static final List<String> LAVA = List.of(
            "{player} became a crispy critter",
            "{player} took a hot bath",
            "{player} found the floor was lava",
            "{player} went for a swim in the wrong liquid",
            "{player} is now extra toasty",
            "{player} tried to befriend the lava",
            "{player} forgot lava isn't orange water"
    );

    private static final List<String> DROWNING = List.of(
            "{player} forgot gills aren't included",
            "{player} became one with the fish",
            "{player} held their breath too long",
            "{player} found out water is not air",
            "{player} went for a permanent swim",
            "{player} should've brought a helmet"
    );

    private static final List<String> FIRE = List.of(
            "{player} spontaneously combusted",
            "{player} is now extra crispy",
            "{player} got too close to the fire",
            "{player} played with fire and lost",
            "{player} forgot stop, drop, and roll",
            "{player} lit themselves up"
    );

    private static final List<String> SUFFOCATION = List.of(
            "{player} tried to become one with the wall",
            "{player} got too cozy with blocks",
            "{player} forgot they need air",
            "{player} was squished",
            "{player} found a tight squeeze"
    );

    private static final List<String> VOID = List.of(
            "{player} fell out of the world",
            "{player} found the bottom of the map",
            "{player} went to the backrooms",
            "{player} discovered the void",
            "{player} took a one-way trip to nowhere",
            "{player} fell into the abyss"
    );

    private static final List<String> STARVATION = List.of(
            "{player} forgot to eat",
            "{player} should have packed snacks",
            "{player} starved to death",
            "{player} ran out of food stamps",
            "{player} didn't meal prep"
    );

    private static final List<String> FREEZE = List.of(
            "{player} became a popsicle",
            "{player} caught a permanent cold",
            "{player} froze solid",
            "{player} should've brought a jacket",
            "{player} became an ice sculpture"
    );

    private static final List<String> LIGHTNING = List.of(
            "{player} became a lightning rod",
            "{player} was smited",
            "{player} got struck down",
            "{player} angered the sky",
            "{player} conducted electricity poorly"
    );

    private static final List<String> CACTUS = List.of(
            "{player} hugged a cactus",
            "{player} found a prickly situation",
            "{player} was pricked to death",
            "{player} forgot cacti aren't friendly",
            "{player} got the point"
    );

    private static final List<String> MAGMA = List.of(
            "{player} discovered the floor is lava was not a game",
            "{player} walked on hot coals and lost",
            "{player} should've worn shoes",
            "{player} got their feet burned"
    );

    private static final List<String> CRAMMING = List.of(
            "{player} was loved to death",
            "{player} got too popular",
            "{player} was in a crowded space",
            "{player} learned about personal space"
    );

    private static final List<String> FLY_INTO_WALL = List.of(
            "{player} didn't stick the landing",
            "{player} discovered kinetic energy",
            "{player} hit a wall at high speed",
            "{player} forgot how to stop",
            "{player} experienced an elytra malfunction"
    );

    private static final List<String> WORLD_BORDER = List.of(
            "{player} went too far",
            "{player} tried to escape the simulation",
            "{player} hit the edge of the world",
            "{player} found the boundary"
    );

    private static final List<String> POISON = List.of(
            "{player} couldn't find the antidote",
            "{player} should've brought milk",
            "{player} was poisoned",
            "{player} drank something sketchy"
    );

    private static final List<String> WITHER = List.of(
            "{player} withered away",
            "{player} caught a bad case of wither",
            "{player} decayed",
            "{player} rotted"
    );

    private static final List<String> MAGIC = List.of(
            "{player} got potioned",
            "{player} failed their saving throw",
            "{player} was magicked to death",
            "{player} was cursed"
    );

    private static final List<String> KILL_COMMAND = List.of(
            "{player} was removed from existence",
            "{player} /kill'd themselves",
            "{player} used the forbidden command",
            "{player} took the easy way out"
    );

    private static final List<String> DRYOUT = List.of(
            "{player} dried out",
            "{player} needed more water",
            "{player} became a fish out of water"
    );

    private static final List<String> CAMPFIRE = List.of(
            "{player} sat too close to the campfire",
            "{player} roasted marshmallows too aggressively",
            "{player} got toasted"
    );

    private static final List<String> BERRY_BUSH = List.of(
            "{player} picked the wrong berries",
            "{player} got thorned",
            "{player} was pricked by a sweet berry bush"
    );

    private static final List<String> FALLING_BLOCK = List.of(
            "{player} was squashed by a falling block",
            "{player} didn't look up",
            "{player} got crushed"
    );

    // Entity deaths (with killer name)
    private static final List<String> ENTITY_ATTACK = List.of(
            "{player} was slain by {killer}",
            "{player} met their end at the hands of {killer}",
            "{player} was no match for {killer}",
            "{player} was defeated by {killer}",
            "{player} fell to {killer}",
            "{player} was killed by {killer}",
            "{player} couldn't escape {killer}",
            "{player} was taken out by {killer}"
    );

    private static final List<String> ENTITY_ATTACK_CONTEXT = List.of(
            "{player} was slain by {killer} whilst trying to escape",
            "{player} was killed by {killer} whilst fighting back",
            "{player} was no match for {killer} in combat"
    );

    private static final List<String> EXPLOSION = List.of(
            "{player} was blown up by {killer}",
            "{player} got too close to {killer}",
            "{player} exploded thanks to {killer}",
            "{player} heard a hissss... courtesy of {killer}",
            "{player} was blasted by {killer}"
    );

    private static final List<String> CREEPER = List.of(
            "{player} was blown up by a Creeper",
            "{player} heard a hissss...",
            "{player} hugged a Creeper",
            "{player} didn't run fast enough",
            "{player} met an explosive friend",
            "{player} got creepered"
    );

    private static final List<String> PROJECTILE = List.of(
            "{player} was shot by {killer}",
            "{player} took an arrow from {killer}",
            "{player} couldn't dodge {killer}'s shot",
            "{player} was sniped by {killer}",
            "{player} was riddled with projectiles from {killer}"
    );

    private static final List<String> THORNS = List.of(
            "{player} was pricked fighting {killer}",
            "{player} hurt themselves on {killer}",
            "{player} learned {killer} is spiky"
    );

    private static final List<String> SONIC_BOOM = List.of(
            "{player} couldn't handle the bass from {killer}",
            "{player} was obliterated by {killer}'s sonic attack",
            "{player} got warden'd"
    );

    // PvP deaths (player killer)
    private static final List<String> PVP = List.of(
            "{player} was eliminated by {killer}",
            "{player} was outplayed by {killer}",
            "{player} got rekt by {killer}",
            "{player} lost a duel to {killer}",
            "{player} was owned by {killer}",
            "{player} was no match for {killer}",
            "{player} was clapped by {killer}",
            "{player} got destroyed by {killer}"
    );

    private static final List<String> PVP_CONTEXT = List.of(
            "{player} was slain by {killer} whilst trying to escape",
            "{player} was finished off by {killer}",
            "{player} was taken down by {killer} in combat"
    );

    // Generic fallback
    private static final List<String> GENERIC = List.of(
            "{player} died",
            "{player} perished",
            "{player} is no more",
            "{player} met their demise",
            "{player} kicked the bucket"
    );

    private static final List<String> GENERIC_KILLER = List.of(
            "{player} was killed by {killer}",
            "{player} died to {killer}",
            "{player} was slain by {killer}"
    );

    /**
     * Gets a random death message for the given cause without a killer.
     */
    public static String getMessage(DamageCause cause) {
        List<String> messages = getMessagesForCause(cause);
        return messages.get(RANDOM.nextInt(messages.size()));
    }

    /**
     * Gets a random death message for entity attacks with a killer.
     */
    public static String getEntityMessage(String killerType, boolean isCreeper) {
        if (isCreeper) {
            return CREEPER.get(RANDOM.nextInt(CREEPER.size()));
        }
        // 30% chance of context message
        if (RANDOM.nextInt(100) < 30) {
            return ENTITY_ATTACK_CONTEXT.get(RANDOM.nextInt(ENTITY_ATTACK_CONTEXT.size()));
        }
        return ENTITY_ATTACK.get(RANDOM.nextInt(ENTITY_ATTACK.size()));
    }

    /**
     * Gets a random death message for PvP kills.
     */
    public static String getPvPMessage() {
        // 25% chance of context message
        if (RANDOM.nextInt(100) < 25) {
            return PVP_CONTEXT.get(RANDOM.nextInt(PVP_CONTEXT.size()));
        }
        return PVP.get(RANDOM.nextInt(PVP.size()));
    }

    /**
     * Gets a random death message for explosions.
     */
    public static String getExplosionMessage() {
        return EXPLOSION.get(RANDOM.nextInt(EXPLOSION.size()));
    }

    /**
     * Gets a random death message for projectile deaths.
     */
    public static String getProjectileMessage() {
        return PROJECTILE.get(RANDOM.nextInt(PROJECTILE.size()));
    }

    /**
     * Gets a random death message for thorns damage.
     */
    public static String getThornsMessage() {
        return THORNS.get(RANDOM.nextInt(THORNS.size()));
    }

    /**
     * Gets a random death message for sonic boom (Warden).
     */
    public static String getSonicBoomMessage() {
        return SONIC_BOOM.get(RANDOM.nextInt(SONIC_BOOM.size()));
    }

    /**
     * Gets a generic message with a killer.
     */
    public static String getGenericKillerMessage() {
        return GENERIC_KILLER.get(RANDOM.nextInt(GENERIC_KILLER.size()));
    }

    private static List<String> getMessagesForCause(DamageCause cause) {
        return switch (cause) {
            case FALL -> RANDOM.nextBoolean() ? FALL : FALL_HIGH;
            case LAVA -> LAVA;
            case DROWNING -> DROWNING;
            case FIRE, FIRE_TICK -> FIRE;
            case SUFFOCATION -> SUFFOCATION;
            case VOID -> VOID;
            case STARVATION -> STARVATION;
            case FREEZE -> FREEZE;
            case LIGHTNING -> LIGHTNING;
            case CONTACT -> RANDOM.nextBoolean() ? CACTUS : BERRY_BUSH;
            case HOT_FLOOR -> MAGMA;
            case CRAMMING -> CRAMMING;
            case FLY_INTO_WALL -> FLY_INTO_WALL;
            case WORLD_BORDER -> WORLD_BORDER;
            case POISON -> POISON;
            case WITHER -> WITHER;
            case MAGIC -> MAGIC;
            case KILL -> KILL_COMMAND;
            case DRYOUT -> DRYOUT;
            case CAMPFIRE -> CAMPFIRE;
            case FALLING_BLOCK -> FALLING_BLOCK;
            default -> GENERIC;
        };
    }
}
