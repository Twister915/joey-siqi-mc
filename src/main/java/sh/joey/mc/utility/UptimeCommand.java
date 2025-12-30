package sh.joey.mc.utility;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

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
            String formatted = formatUptime(uptimeMs);

            sender.sendMessage(Component.text("[Uptime] ", NamedTextColor.GOLD)
                    .append(Component.text("Server has been running for ", NamedTextColor.GRAY))
                    .append(Component.text(formatted, NamedTextColor.WHITE)));
        });
    }

    private String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        seconds %= 60;
        minutes %= 60;
        hours %= 24;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0 || days > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0 || hours > 0 || days > 0) {
            sb.append(minutes).append("m ");
        }
        sb.append(seconds).append("s");

        return sb.toString();
    }
}
