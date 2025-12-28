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
import sh.joey.mc.punish.Punishment;
import sh.joey.mc.punish.PunishmentMessages;
import sh.joey.mc.punish.PunishmentStorage;
import sh.joey.mc.punish.PunishmentType;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /tempban <player> <duration> [reason] - Temporarily ban a player.
 */
public final class TempBanCommand implements Command {

    private final SiqiJoeyPlugin plugin;
    private final PunishmentStorage storage;
    private final PlayerResolver playerResolver;

    public TempBanCommand(SiqiJoeyPlugin plugin, PunishmentStorage storage, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.storage = storage;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "tempban";
    }

    @Override
    public String getPermission() {
        return "smp.punish.tempban";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length < 2) {
                error(sender, "Usage: /tempban <player> <duration> [reason]");
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

            return playerResolver.resolvePlayerIdWithMojang(targetName)
                    .switchIfEmpty(Maybe.defer(() -> {
                        error(sender, "Player '" + targetName + "' not found (checked Mojang API).");
                        return Maybe.empty();
                    }))
                    .flatMapCompletable(targetId ->
                            storage.createBan(targetId, issuerId, reason, expiresAt)
                                    .observeOn(plugin.mainScheduler())
                                    .doOnComplete(() -> {
                                        success(sender, "Temporarily banned " + targetName + " for " + durationDisplay +
                                                (reason != null ? " - " + reason : ""));
                                        kickIfOnline(targetId, reason, expiresAt);
                                    })
                    )
                    .doOnError(err -> logAndError(sender, "Failed to tempban player", err))
                    .onErrorComplete();
        });
    }

    private void kickIfOnline(UUID targetId, String reason, Instant expiresAt) {
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            Punishment ban = new Punishment(
                    UUID.randomUUID(), targetId, null, PunishmentType.BAN,
                    null, reason, expiresAt, java.time.Instant.now(), null, null
            );
            target.kick(PunishmentMessages.formatBanKickMessage(ban));
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
            List<String> suggestions = List.of("1h", "6h", "12h", "1d", "3d", "7d", "14d", "30d");
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
