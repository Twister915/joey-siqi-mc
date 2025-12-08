package sh.joey.mc.home;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Automatically saves a player's first bed interaction as their default home.
 * Only triggers if the player has no existing homes.
 */
public final class BedHomeListener implements Listener {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Home").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private final HomeStorage storage;

    public BedHomeListener(JavaPlugin plugin, HomeStorage storage) {
        this.storage = storage;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        if (!isBed(clickedBlock.getType())) {
            return;
        }

        Player player = event.getPlayer();
        if (storage.hasAnyHomes(player.getUniqueId())) {
            return;
        }

        // Player has no homes and right-clicked a bed - save as "home"
        Location location = player.getLocation();
        Home home = new Home("home", location);
        storage.setHome(player.getUniqueId(), home);

        player.sendMessage(PREFIX.append(
                Component.text("Your first home has been set! Use ")
                        .color(NamedTextColor.GREEN)
                        .append(Component.text("/home").color(NamedTextColor.AQUA))
                        .append(Component.text(" to return here.").color(NamedTextColor.GREEN))
        ));
    }

    private boolean isBed(Material material) {
        return switch (material) {
            case WHITE_BED, ORANGE_BED, MAGENTA_BED, LIGHT_BLUE_BED,
                 YELLOW_BED, LIME_BED, PINK_BED, GRAY_BED,
                 LIGHT_GRAY_BED, CYAN_BED, PURPLE_BED, BLUE_BED,
                 BROWN_BED, GREEN_BED, RED_BED, BLACK_BED -> true;
            default -> false;
        };
    }
}
