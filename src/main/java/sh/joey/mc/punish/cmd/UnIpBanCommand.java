package sh.joey.mc.punish.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.player.PlayerResolver;
import sh.joey.mc.punish.PunishmentMessages;
import sh.joey.mc.punish.PunishmentStorage;
import sh.joey.mc.session.PlayerSessionStorage;

import java.util.List;
import java.util.UUID;

/**
 * /unipban <player|ip> - Revoke an IP ban.
 * Accepts either a player name (looks up their IP) or a raw IP address.
 */
public final class UnIpBanCommand implements Command {

    private final SiqiJoeyPlugin plugin;
    private final PunishmentStorage storage;
    private final PlayerResolver playerResolver;
    private final PlayerSessionStorage sessionStorage;

    public UnIpBanCommand(SiqiJoeyPlugin plugin, PunishmentStorage storage,
                          PlayerResolver playerResolver, PlayerSessionStorage sessionStorage) {
        this.plugin = plugin;
        this.storage = storage;
        this.playerResolver = playerResolver;
        this.sessionStorage = sessionStorage;
    }

    @Override
    public String getName() {
        return "unipban";
    }

    @Override
    public String getPermission() {
        return "smp.punish.unipban";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length < 1) {
                error(sender, "Usage: /unipban <player|ip>");
                return Completable.complete();
            }

            String input = args[0];
            UUID revokedById = sender instanceof Player p ? p.getUniqueId() : null;

            if (isIpAddress(input)) {
                // Direct IP unban
                return storage.revokeIpBans(input, revokedById)
                        .observeOn(plugin.mainScheduler())
                        .doOnSuccess(count -> {
                            if (count > 0) {
                                success(sender, "Unbanned IP " + input + ".");
                            } else {
                                warn(sender, "IP " + input + " is not banned.");
                            }
                        })
                        .ignoreElement()
                        .doOnError(err -> logAndError(sender, "Failed to unban IP", err))
                        .onErrorComplete();
            } else {
                // Look up player and unban both their IP and any IP bans associated with their UUID
                return playerResolver.resolvePlayerId(input)
                        .switchIfEmpty(Maybe.defer(() -> {
                            error(sender, "Player '" + input + "' not found.");
                            return Maybe.empty();
                        }))
                        .flatMapSingle(playerId -> {
                            // Try to unban by player's IP
                            Single<Integer> ipUnban = sessionStorage.getLastIpAddress(playerId)
                                    .flatMapSingle(ip -> storage.revokeIpBans(ip, revokedById))
                                    .defaultIfEmpty(0);

                            // Also unban any IP bans associated with the player's UUID
                            Single<Integer> playerUnban = storage.revokeIpBansByPlayer(playerId, revokedById);

                            return Single.zip(ipUnban, playerUnban, Integer::sum);
                        })
                        .observeOn(plugin.mainScheduler())
                        .doOnSuccess(count -> {
                            if (count > 0) {
                                success(sender, "Unbanned IP for " + input + ".");
                            } else {
                                warn(sender, input + " has no active IP bans.");
                            }
                        })
                        .ignoreElement()
                        .doOnError(err -> logAndError(sender, "Failed to unban IP", err))
                        .onErrorComplete();
            }
        });
    }

    private boolean isIpAddress(String input) {
        // Check for IPv4: digits and dots only, with 3 dots
        if (input.chars().allMatch(c -> Character.isDigit(c) || c == '.')) {
            long dotCount = input.chars().filter(c -> c == '.').count();
            return dotCount == 3;
        }
        // Check for IPv6: contains colon
        return input.contains(":");
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
