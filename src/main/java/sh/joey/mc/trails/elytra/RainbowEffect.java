package sh.joey.mc.trails.elytra;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import sh.joey.mc.trails.TrailEffect;
import sh.joey.mc.trails.TrailIntensity;

/**
 * Rainbow trail effect that cycles through colors.
 */
public final class RainbowEffect implements TrailEffect {

    public static final RainbowEffect INSTANCE = new RainbowEffect();

    private static final Color[] RAINBOW = {
            Color.fromRGB(255, 0, 0),     // Red
            Color.fromRGB(255, 127, 0),   // Orange
            Color.fromRGB(255, 255, 0),   // Yellow
            Color.fromRGB(0, 255, 0),     // Green
            Color.fromRGB(0, 255, 255),   // Cyan
            Color.fromRGB(0, 0, 255),     // Blue
            Color.fromRGB(139, 0, 255)    // Violet
    };

    private RainbowEffect() {
    }

    @Override
    public String id() {
        return "rainbow";
    }

    @Override
    public String displayName() {
        return "Rainbow";
    }

    @Override
    public void spawn(Location location, long tick, TrailIntensity intensity) {
        World world = location.getWorld();
        if (world == null) return;

        // Cycle through colors based on tick
        int index = (int) ((tick / 3) % RAINBOW.length);
        DustOptions dust = new DustOptions(RAINBOW[index], 1.2f);

        world.spawnParticle(Particle.DUST, location, intensity.particleCount(),
                0.1, 0.1, 0.1, 0.01, dust);
    }
}
