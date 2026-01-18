package sh.joey.mc.utility;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.player.PlayerResolver;

import java.util.List;
import java.util.Optional;

/**
 * /ping [player] - shows connection latency.
 * Without args: shows sender's own ping (requires smp.ping)
 * With player arg: shows another player's ping (requires smp.ping.others)
 */
public final class PingCommand implements Command {

    private static final Component PREFIX = Component.text("[Ping] ").color(NamedTextColor.AQUA);

    private final PlayerResolver playerResolver;

    public PingCommand(PlayerResolver playerResolver) {
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public String getPermission() {
        return "smp.ping";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (args.length == 0) {
                // Show sender's own ping
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Console doesn't have a ping. Specify a player.");
                    return;
                }
                showPing(sender, player);
            } else if (args.length == 1) {
                // Show another player's ping
                if (!sender.hasPermission("smp.ping.others")) {
                    sender.sendMessage(PREFIX.append(
                            Component.text("You don't have permission to check other players' ping.")
                                    .color(NamedTextColor.RED)));
                    return;
                }

                String targetName = args[0];
                Optional<Player> targetOpt = playerResolver.resolveOnlinePlayer(targetName);

                if (targetOpt.isEmpty()) {
                    sender.sendMessage(PREFIX.append(
                            Component.text("Player '" + targetName + "' is not online.")
                                    .color(NamedTextColor.RED)));
                    return;
                }

                showPing(sender, targetOpt.get());
            } else {
                sender.sendMessage(PREFIX.append(
                        Component.text("Usage: /ping [player]").color(NamedTextColor.GRAY)));
            }
        });
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        // Only show completions if sender has permission to check others
        if (!sender.hasPermission("smp.ping.others")) {
            return Maybe.empty();
        }

        if (args.length != 1) {
            return Maybe.empty();
        }

        String prefix = args[0];
        String senderName = (sender instanceof Player p) ? p.getName() : null;

        // Only show online players since ping only works for online players
        return Maybe.fromCallable(() -> Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(prefix.toLowerCase()))
                .filter(name -> senderName == null || !name.equalsIgnoreCase(senderName))
                .map(Completion::completion)
                .toList())
                .filter(list -> !list.isEmpty());
    }

    private void showPing(CommandSender sender, Player target) {
        int ping = target.getPing();
        NamedTextColor color = getPingColor(ping);

        boolean isSelf = sender instanceof Player player && player.getUniqueId().equals(target.getUniqueId());

        Component message;
        if (isSelf) {
            message = PREFIX.append(Component.text("Your ping: ").color(NamedTextColor.GRAY))
                    .append(Component.text(ping + "ms").color(color));
        } else {
            message = PREFIX.append(Component.text(target.getName() + "'s ping: ").color(NamedTextColor.GRAY))
                    .append(Component.text(ping + "ms").color(color));
        }

        sender.sendMessage(message);
    }

    private NamedTextColor getPingColor(int ping) {
        if (ping < 50) {
            return NamedTextColor.GREEN;
        } else if (ping < 100) {
            return NamedTextColor.YELLOW;
        } else if (ping < 200) {
            return NamedTextColor.GOLD;
        } else {
            return NamedTextColor.RED;
        }
    }
}
