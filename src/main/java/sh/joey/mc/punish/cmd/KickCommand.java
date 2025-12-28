package sh.joey.mc.punish.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.player.PlayerResolver;
import sh.joey.mc.punish.PunishmentMessages;
import sh.joey.mc.punish.PunishmentStorage;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /kick <player> [reason] - Kick a player from the server.
 */
public final class KickCommand implements Command {

    private final SiqiJoeyPlugin plugin;
    private final PunishmentStorage storage;
    private final PlayerResolver playerResolver;

    public KickCommand(SiqiJoeyPlugin plugin, PunishmentStorage storage, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.storage = storage;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "kick";
    }

    @Override
    public String getPermission() {
        return "smp.punish.kick";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length < 1) {
                error(sender, "Usage: /kick <player> [reason]");
                return Completable.complete();
            }

            String targetName = args[0];
            String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
            UUID issuerId = sender instanceof Player p ? p.getUniqueId() : null;

            // Only online players can be kicked
            return Maybe.defer(() -> {
                        var target = playerResolver.resolveOnlinePlayer(targetName);
                        return target.map(Maybe::just).orElse(Maybe.empty());
                    })
                    .switchIfEmpty(Maybe.defer(() -> {
                        error(sender, "Player '" + targetName + "' is not online.");
                        return Maybe.empty();
                    }))
                    .flatMapCompletable(target -> {
                        UUID targetId = target.getUniqueId();
                        return storage.createKick(targetId, issuerId, reason)
                                .observeOn(plugin.mainScheduler())
                                .doOnComplete(() -> {
                                    // Kick the player
                                    target.kick(PunishmentMessages.formatKickMessage(reason));
                                    success(sender, "Kicked " + target.getName() +
                                            (reason != null ? " for: " + reason : ""));
                                });
                    })
                    .doOnError(err -> logAndError(sender, "Failed to kick player", err))
                    .onErrorComplete();
        });
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
