package sh.joey.mc.utility;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

/**
 * /seed - shows the seed for the current world.
 * Useful for WorldEdit //regen which may not read the seed correctly for custom worlds.
 */
public final class SeedCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Seed").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    @Override
    public String getName() {
        return "seed";
    }

    @Override
    public String getPermission() {
        return "smp.seed";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return;
            }

            World world = player.getWorld();
            long seed = world.getSeed();
            String seedStr = String.valueOf(seed);

            // Build clickable seed component
            Component seedComponent = Component.text(seedStr)
                    .color(NamedTextColor.AQUA)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.copyToClipboard(seedStr))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to copy")));

            Component message = PREFIX
                    .append(Component.text("World ").color(NamedTextColor.GRAY))
                    .append(Component.text(world.getName()).color(NamedTextColor.WHITE))
                    .append(Component.text(" seed: ").color(NamedTextColor.GRAY))
                    .append(seedComponent);

            player.sendMessage(message);

            // Also show the regen hint
            Component hint = Component.text("  ")
                    .append(Component.text("Tip: ").color(NamedTextColor.GRAY))
                    .append(Component.text("//regen " + seedStr)
                            .color(NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.copyToClipboard("//regen " + seedStr))
                            .hoverEvent(HoverEvent.showText(Component.text("Click to copy regen command"))));

            player.sendMessage(hint);
        });
    }
}
