package sh.joey.mc.bossbar;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;

import java.util.Optional;

/**
 * Boss bar provider that shows distance to lodestone when holding a lodestone compass.
 * Medium priority - overrides time of day when active.
 */
public final class LodestoneCompassProvider implements BossBarProvider {

    private static final int PRIORITY = 100;
    private static final double MAX_DISPLAY_DISTANCE = 10_000.0; // 10km for progress bar scaling

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Optional<BossBarState> getState(Player player) {
        // Check main hand first, then off hand
        Location lodestoneLocation = getLodestoneLocation(player.getInventory().getItemInMainHand());
        if (lodestoneLocation == null) {
            lodestoneLocation = getLodestoneLocation(player.getInventory().getItemInOffHand());
            if (lodestoneLocation == null) {
                return Optional.empty();
            }
        }

        // Check if in same dimension
        if (!player.getWorld().equals(lodestoneLocation.getWorld())) {
            String title = ChatColor.translateAlternateColorCodes('&',
                    "&5&l⬥ Lodestone &7:: &dDifferent Dimension");
            return Optional.of(new BossBarState(title, BarColor.PURPLE, 1.0f, BarStyle.SOLID));
        }

        // Calculate distance
        double distance = player.getLocation().distance(lodestoneLocation);
        String distanceStr = formatDistance(distance);

        // Progress bar: closer = more full (inverted, capped at MAX_DISPLAY_DISTANCE)
        float progress = (float) Math.max(0, 1.0 - (distance / MAX_DISPLAY_DISTANCE));

        // Color based on distance
        BarColor color;
        String colorCode;
        if (distance < 100) {
            color = BarColor.GREEN;
            colorCode = "&a";
        } else if (distance < 500) {
            color = BarColor.YELLOW;
            colorCode = "&e";
        } else if (distance < 1000) {
            color = BarColor.PINK;
            colorCode = "&d";
        } else {
            color = BarColor.PURPLE;
            colorCode = "&5";
        }

        String title = ChatColor.translateAlternateColorCodes('&',
                String.format("&5&l⬥ Lodestone &7:: %s&l%s", colorCode, distanceStr));

        return Optional.of(new BossBarState(title, color, progress, BarStyle.SOLID));
    }

    private Location getLodestoneLocation(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS) {
            return null;
        }
        if (!(item.getItemMeta() instanceof CompassMeta meta)) {
            return null;
        }
        if (!meta.hasLodestone()) {
            return null;
        }
        return meta.getLodestone();
    }

    private String formatDistance(double meters) {
        if (meters >= 1000) {
            return String.format("%.1f km", meters / 1000.0);
        } else {
            return String.format("%.0f m", meters);
        }
    }
}
