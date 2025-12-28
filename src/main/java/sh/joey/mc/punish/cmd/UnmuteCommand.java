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
import sh.joey.mc.punish.PunishmentMessages;
import sh.joey.mc.punish.PunishmentStorage;

import java.util.List;
import java.util.UUID;

/**
 * /unmute <player> - Revoke a player's mute.
 */
public final class UnmuteCommand implements Command {

    private final SiqiJoeyPlugin plugin;
    private final PunishmentStorage storage;
    private final PlayerResolver playerResolver;

    public UnmuteCommand(SiqiJoeyPlugin plugin, PunishmentStorage storage, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.storage = storage;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "unmute";
    }

    @Override
    public String getPermission() {
        return "smp.punish.unmute";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length < 1) {
                error(sender, "Usage: /unmute <player>");
                return Completable.complete();
            }

            String targetName = args[0];
            UUID revokedById = sender instanceof Player p ? p.getUniqueId() : null;

            return playerResolver.resolvePlayerId(targetName)
                    .switchIfEmpty(Maybe.defer(() -> {
                        error(sender, "Player '" + targetName + "' not found.");
                        return Maybe.empty();
                    }))
                    .flatMapSingle(targetId -> storage.revokeMutes(targetId, revokedById)
                            .doOnSuccess(count -> {
                                // Notify the player if online
                                if (count > 0) {
                                    Player target = Bukkit.getPlayer(targetId);
                                    if (target != null) {
                                        target.sendMessage(Component.text("[")
                                                .color(NamedTextColor.DARK_GRAY)
                                                .append(Component.text("Unmuted").color(NamedTextColor.GREEN))
                                                .append(Component.text("] ").color(NamedTextColor.DARK_GRAY))
                                                .append(Component.text("You have been unmuted.").color(NamedTextColor.GRAY)));
                                    }
                                }
                            }))
                    .observeOn(plugin.mainScheduler())
                    .doOnSuccess(count -> {
                        if (count > 0) {
                            success(sender, "Unmuted " + targetName + ".");
                        } else {
                            warn(sender, targetName + " is not muted.");
                        }
                    })
                    .ignoreElement()
                    .doOnError(err -> logAndError(sender, "Failed to unmute player", err))
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

    private void warn(CommandSender sender, String message) {
        sender.sendMessage(PunishmentMessages.PREFIX.append(Component.text(message).color(NamedTextColor.YELLOW)));
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(PunishmentMessages.PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }

    private void logAndError(CommandSender sender, String context, Throwable err) {
        plugin.getLogger().warning(context + ": " + err.getMessage());
        error(sender, context + ".");
    }
}
