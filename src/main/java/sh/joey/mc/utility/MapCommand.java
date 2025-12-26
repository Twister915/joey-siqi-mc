package sh.joey.mc.utility;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

/**
 * /map - shows a clickable link to the web map centered on the player's position.
 */
public final class MapCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Map").color(NamedTextColor.GREEN))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private final MapConfig config;

    public MapCommand(MapConfig config) {
        this.config = config;
    }

    @Override
    public String getName() {
        return "map";
    }

    @Override
    public String getPermission() {
        return "smp.map";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            String url;
            String displayText;

            if (sender instanceof Player player) {
                url = config.buildUrl(player.getLocation());
                displayText = "Click to view your location";
            } else {
                url = config.url();
                displayText = "Click to open in browser";
            }

            Component message = PREFIX
                    .append(Component.text("View the server map: ").color(NamedTextColor.GRAY))
                    .append(Component.text("[Open Map]")
                            .color(NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.openUrl(url))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text(displayText).color(NamedTextColor.GRAY))));

            sender.sendMessage(message);
        });
    }
}
