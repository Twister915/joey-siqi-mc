package sh.joey.mc.utility;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.util.DurationFormat;

import java.lang.management.ManagementFactory;

/**
 * /uptime - shows server uptime.
 */
public final class UptimeCommand implements Command {

    @Override
    public String getName() {
        return "uptime";
    }

    @Override
    public String getPermission() {
        return "smp.uptime";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
            String formatted = DurationFormat.formatShortMillis(uptimeMs);

            sender.sendMessage(Component.text("[Uptime] ", NamedTextColor.GOLD)
                    .append(Component.text("Server has been running for ", NamedTextColor.GRAY))
                    .append(Component.text(formatted, NamedTextColor.WHITE)));
        });
    }
}
