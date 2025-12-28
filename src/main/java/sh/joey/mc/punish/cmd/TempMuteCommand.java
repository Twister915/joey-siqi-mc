package sh.joey.mc.punish.cmd;

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
import sh.joey.mc.punish.DurationParser;
import sh.joey.mc.punish.PunishmentMessages;
import sh.joey.mc.punish.PunishmentStorage;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /tempmute <player> <duration> [reason] - Temporarily mute a player.
 */
public final class TempMuteCommand implements Command {

    private final SiqiJoeyPlugin plugin;
    private final PunishmentStorage storage;
    private final PlayerResolver playerResolver;

    public TempMuteCommand(SiqiJoeyPlugin plugin, PunishmentStorage storage, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.storage = storage;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "tempmute";
    }

    @Override
    public String getPermission() {
        return "smp.punish.tempmute";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length < 2) {
                error(sender, "Usage: /tempmute <player> <duration> [reason]");
                error(sender, "Duration format: 1d2h3m4s (days, hours, minutes, seconds)");
                return Completable.complete();
            }

            String targetName = args[0];
            String durationStr = args[1];
            String reason = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
            UUID issuerId = sender instanceof Player p ? p.getUniqueId() : null;

            // Parse duration
            var durationOpt = DurationParser.parse(durationStr);
            if (durationOpt.isEmpty()) {
                error(sender, "Invalid duration: " + durationStr);
                error(sender, "Format: 1d2h3m4s (days, hours, minutes, seconds)");
                return Completable.complete();
            }

            Duration duration = durationOpt.get();
            Instant expiresAt = Instant.now().plus(duration);
            String durationDisplay = DurationParser.formatHumanReadable(duration);

            return playerResolver.resolvePlayerId(targetName)
                    .switchIfEmpty(Maybe.defer(() -> {
                        error(sender, "Player '" + targetName + "' not found.");
                        return Maybe.empty();
                    }))
                    .flatMapCompletable(targetId ->
                            storage.createMute(targetId, issuerId, reason, expiresAt)
                                    .observeOn(plugin.mainScheduler())
                                    .doOnComplete(() -> {
                                        success(sender, "Temporarily muted " + targetName + " for " + durationDisplay +
                                                (reason != null ? " - " + reason : ""));
                                        notifyIfOnline(targetId, durationDisplay, reason);
                                    })
                    )
                    .doOnError(err -> logAndError(sender, "Failed to tempmute player", err))
                    .onErrorComplete();
        });
    }

    private void notifyIfOnline(UUID targetId, String duration, String reason) {
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            Component message = Component.text("[")
                    .color(NamedTextColor.DARK_GRAY)
                    .append(Component.text("Muted").color(NamedTextColor.RED))
                    .append(Component.text("] ").color(NamedTextColor.DARK_GRAY))
                    .append(Component.text("You have been muted for " + duration +
                            (reason != null ? ": " + reason : ".")).color(NamedTextColor.GRAY));
            target.sendMessage(message);
        }
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            return playerResolver.getCompletions(args[0], 20)
                    .map(names -> names.stream()
                            .map(Completion::completion)
                            .toList())
                    .filter(list -> !list.isEmpty());
        }
        if (args.length == 2) {
            // Suggest common durations
            List<String> suggestions = List.of("5m", "15m", "30m", "1h", "6h", "12h", "1d", "3d");
            String prefix = args[1].toLowerCase();
            List<Completion> completions = suggestions.stream()
                    .filter(s -> s.startsWith(prefix))
                    .map(Completion::completion)
                    .toList();
            return completions.isEmpty() ? Maybe.empty() : Maybe.just(completions);
        }
        return Maybe.empty();
    }

    private void success(CommandSender sender, String message) {
        sender.sendMessage(PunishmentMessages.PREFIX.append(Component.text(message).color(NamedTextColor.GREEN)));
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(PunishmentMessages.PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }

    private void logAndError(CommandSender sender, String context, Throwable err) {
        plugin.getLogger().warning(context + ": " + err.getMessage());
        error(sender, context + ".");
    }
}
