package sh.joey.mc.trails.elytra;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import sh.joey.mc.trails.TrailEffect;
import sh.joey.mc.trails.TrailIntensity;

/**
 * Built-in particle effects for elytra trails.
 */
public enum ElytraTrailEffect implements TrailEffect {
    FLAME("flame", "Fire Trail", Particle.FLAME, 0.1, 0.1, 0.1, 0.02),
    SOUL("soul", "Soul Fire", Particle.SOUL_FIRE_FLAME, 0.1, 0.1, 0.1, 0.02),
    END("end", "End Sparkles", Particle.END_ROD, 0.1, 0.1, 0.1, 0.01),
    ENCHANT("enchant", "Enchantment", Particle.ENCHANT, 0.2, 0.2, 0.2, 0.5),
    HEART("heart", "Hearts", Particle.HEART, 0.2, 0.2, 0.2, 0.0),
    NOTE("note", "Music Notes", Particle.NOTE, 0.2, 0.2, 0.2, 1.0),
    TOTEM("totem", "Golden Sparkles", Particle.TOTEM_OF_UNDYING, 0.1, 0.1, 0.1, 0.3),
    CHERRY("cherry", "Cherry Petals", Particle.CHERRY_LEAVES, 0.2, 0.2, 0.2, 0.05),
    DRAGON("dragon", "Dragon Breath", Particle.DRAGON_BREATH, 0.15, 0.15, 0.15, 0.01),
    SPARK("spark", "Firework Sparks", Particle.FIREWORK, 0.1, 0.1, 0.1, 0.05),
    SNOW("snow", "Snowflakes", Particle.SNOWFLAKE, 0.2, 0.2, 0.2, 0.02),
    WITCH("witch", "Witch Magic", Particle.WITCH, 0.15, 0.15, 0.15, 0.0),
    CHICKEN("chicken", "Chicken", Particle.ITEM, 0.2, 0.2, 0.2, 0.05),
    ICE("ice", "Ice Cubes", Particle.BLOCK, 0.2, 0.2, 0.2, 0.1);

    private static final ItemStack FEATHER = new ItemStack(Material.FEATHER);

    private final String id;
    private final String displayName;
    private final Particle particle;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double speed;

    ElytraTrailEffect(String id, String displayName, Particle particle,
                      double offsetX, double offsetY, double offsetZ, double speed) {
        this.id = id;
        this.displayName = displayName;
        this.particle = particle;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.speed = speed;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String displayName() {
        return displayName;
    }

    @Override
    public void spawn(Location location, long tick, TrailIntensity intensity) {
        World world = location.getWorld();
        if (world == null) return;

        // Special handling for particles that need data
        if (this == CHICKEN) {
            world.spawnParticle(particle, location, intensity.particleCount(),
                    offsetX, offsetY, offsetZ, speed, FEATHER);
        } else if (this == ICE) {
            world.spawnParticle(particle, location, intensity.particleCount(),
                    offsetX, offsetY, offsetZ, speed, Material.BLUE_ICE.createBlockData());
        } else {
            // Check if particle requires data
            Class<?> dataType = particle.getDataType();
            if (dataType == Float.class) {
                // Particles like NOTE, DUST_COLOR_TRANSITION, etc. need Float
                world.spawnParticle(particle, location, intensity.particleCount(),
                        offsetX, offsetY, offsetZ, speed, 1.0f);
            } else if (dataType == Integer.class) {
                // Particles like SHRIEK need Integer
                world.spawnParticle(particle, location, intensity.particleCount(),
                        offsetX, offsetY, offsetZ, speed, 0);
            } else {
                world.spawnParticle(particle, location, intensity.particleCount(),
                        offsetX, offsetY, offsetZ, speed);
            }
        }
    }

    /**
     * Find an effect by its ID.
     */
    public static ElytraTrailEffect fromId(String id) {
        for (ElytraTrailEffect effect : values()) {
            if (effect.id.equalsIgnoreCase(id)) {
                return effect;
            }
        }
        return null;
    }
}
