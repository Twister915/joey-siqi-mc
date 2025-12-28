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
import sh.joey.mc.punish.Punishment;
import sh.joey.mc.punish.PunishmentMessages;
import sh.joey.mc.punish.PunishmentStorage;
import sh.joey.mc.punish.PunishmentType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /ban <player> [reason] - Permanently ban a player.
 */
public final class BanCommand implements Command {

    private final SiqiJoeyPlugin plugin;
    private final PunishmentStorage storage;
    private final PlayerResolver playerResolver;

    public BanCommand(SiqiJoeyPlugin plugin, PunishmentStorage storage, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.storage = storage;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "ban";
    }

    @Override
    public String getPermission() {
        return "smp.punish.ban";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length < 1) {
                error(sender, "Usage: /ban <player> [reason]");
                return Completable.complete();
            }

            String targetName = args[0];
            String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
            UUID issuerId = sender instanceof Player p ? p.getUniqueId() : null;

            return playerResolver.resolvePlayerIdWithMojang(targetName)
                    .switchIfEmpty(Maybe.defer(() -> {
                        error(sender, "Player '" + targetName + "' not found (checked Mojang API).");
                        return Maybe.empty();
                    }))
                    .flatMapCompletable(targetId ->
                            storage.createBan(targetId, issuerId, reason, null)  // null = permanent
                                    .observeOn(plugin.mainScheduler())
                                    .doOnComplete(() -> {
                                        success(sender, "Banned " + targetName +
                                                (reason != null ? " for: " + reason : ""));
                                        kickIfOnline(targetId, reason);
                                    })
                    )
                    .doOnError(err -> logAndError(sender, "Failed to ban player", err))
                    .onErrorComplete();
        });
    }

    private void kickIfOnline(UUID targetId, String reason) {
        Player target = Bukkit.getPlayer(targetId);
        if (target != null) {
            // Create a temporary punishment object for message formatting
            Punishment ban = new Punishment(
                    UUID.randomUUID(), targetId, null, PunishmentType.BAN,
                    null, reason, null, java.time.Instant.now(), null, null
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
