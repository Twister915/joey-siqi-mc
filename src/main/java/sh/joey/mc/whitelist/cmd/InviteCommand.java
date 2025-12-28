package sh.joey.mc.whitelist.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.player.PlayerResolver;
import sh.joey.mc.whitelist.WhitelistMessages;
import sh.joey.mc.whitelist.WhitelistStorage;

import java.util.List;
import java.util.UUID;

/**
 * /invite <player> - Add a player to the whitelist.
 */
public final class InviteCommand implements Command {

    private final SiqiJoeyPlugin plugin;
    private final WhitelistStorage storage;
    private final PlayerResolver playerResolver;

    public InviteCommand(SiqiJoeyPlugin plugin, WhitelistStorage storage, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.storage = storage;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "invite";
    }

    @Override
    public String getPermission() {
        return "smp.invite";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length < 1) {
                error(sender, "Usage: /invite <player>");
                return Completable.complete();
            }

            String targetName = args[0];
            UUID invitedBy = sender instanceof Player p ? p.getUniqueId() : null;

            return playerResolver.resolvePlayerIdWithMojang(targetName)
                    .switchIfEmpty(Maybe.defer(() -> {
                        error(sender, "Player '" + targetName + "' not found (checked Mojang API).");
                        return Maybe.empty();
                    }))
                    .flatMapCompletable(targetId ->
                            storage.isWhitelisted(targetId)
                                    .flatMapCompletable(alreadyWhitelisted -> {
                                        if (alreadyWhitelisted) {
                                            warn(sender, targetName + " is already whitelisted.");
                                            return Completable.complete();
                                        }
                                        return storage.addPlayer(targetId, targetName, invitedBy)
                                                .observeOn(plugin.mainScheduler())
                                                .doOnComplete(() -> success(sender, "You invited " + targetName + " to the server!"));
                                    })
                    )
                    .doOnError(err -> logAndError(sender, "Failed to invite player", err))
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
        sender.sendMessage(WhitelistMessages.PREFIX.append(Component.text(message).color(NamedTextColor.GREEN)));
    }

    private void warn(CommandSender sender, String message) {
        sender.sendMessage(WhitelistMessages.PREFIX.append(Component.text(message).color(NamedTextColor.YELLOW)));
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(WhitelistMessages.PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }

    private void logAndError(CommandSender sender, String context, Throwable err) {
        plugin.getLogger().warning(context + ": " + err.getMessage());
        error(sender, context + ".");
    }
}
