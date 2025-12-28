package sh.joey.mc.trails.elytra;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import sh.joey.mc.trails.TrailEffect;
import sh.joey.mc.trails.TrailIntensity;

/**
 * Custom RGB color trail effect.
 * Created from hex color codes like "ff5500".
 */
public final class CustomColorEffect implements TrailEffect {

    private static final String ID_PREFIX = "rgb:";

    private final String hexCode;
    private final Color color;

    public CustomColorEffect(String hexCode) {
        this.hexCode = hexCode.toLowerCase();
        this.color = parseColor(hexCode);
    }

    private static Color parseColor(String hex) {
        // Remove # prefix if present
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        // Remove rgb: prefix if present
        if (hex.toLowerCase().startsWith("rgb:")) {
            hex = hex.substring(4);
        }

        try {
            int rgb = Integer.parseInt(hex, 16);
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;
            return Color.fromRGB(r, g, b);
        } catch (NumberFormatException e) {
            return Color.WHITE;
        }
    }

    @Override
    public String id() {
        return ID_PREFIX + hexCode;
    }

    @Override
    public String displayName() {
        return "Custom (#" + hexCode.toUpperCase() + ")";
    }

    @Override
    public void spawn(Location location, long tick, TrailIntensity intensity) {
        World world = location.getWorld();
        if (world == null) return;

        DustOptions dust = new DustOptions(color, 1.2f);
        world.spawnParticle(Particle.DUST, location, intensity.particleCount(),
                0.1, 0.1, 0.1, 0.01, dust);
    }

    public String hexCode() {
        return hexCode;
    }

    /**
     * Check if an effect ID represents a custom color.
     */
    public static boolean isCustomColor(String effectId) {
        return effectId != null && effectId.toLowerCase().startsWith(ID_PREFIX);
    }

    /**
     * Parse a custom color effect from an effect ID.
     * Returns null if the ID is not a valid custom color.
     */
    public static CustomColorEffect fromId(String effectId) {
        if (!isCustomColor(effectId)) {
            return null;
        }

        String hex = effectId.substring(ID_PREFIX.length());
        if (!isValidHex(hex)) {
            return null;
        }

        return new CustomColorEffect(hex);
    }

    /**
     * Check if a hex string is valid (6 hex characters).
     */
    public static boolean isValidHex(String hex) {
        if (hex == null || hex.length() != 6) {
            return false;
        }
        try {
            Integer.parseInt(hex, 16);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
