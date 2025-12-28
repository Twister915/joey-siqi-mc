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
import sh.joey.mc.session.PlayerSessionStorage;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * /ipban <player|ip> [reason] - Ban an IP address.
 * Accepts either a player name (looks up their IP) or a raw IP address.
 */
public final class IpBanCommand implements Command {

    private final SiqiJoeyPlugin plugin;
    private final PunishmentStorage storage;
    private final PlayerResolver playerResolver;
    private final PlayerSessionStorage sessionStorage;

    public IpBanCommand(SiqiJoeyPlugin plugin, PunishmentStorage storage,
                        PlayerResolver playerResolver, PlayerSessionStorage sessionStorage) {
        this.plugin = plugin;
        this.storage = storage;
        this.playerResolver = playerResolver;
        this.sessionStorage = sessionStorage;
    }

    @Override
    public String getName() {
        return "ipban";
    }

    @Override
    public String getPermission() {
        return "smp.punish.ipban";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length < 1) {
                error(sender, "Usage: /ipban <player|ip> [reason]");
                return Completable.complete();
            }

            String input = args[0];
            String reason = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
            UUID issuerId = sender instanceof Player p ? p.getUniqueId() : null;

            if (isIpAddress(input)) {
                // Direct IP ban
                return storage.createIpBan(input, null, issuerId, reason)
                        .observeOn(plugin.mainScheduler())
                        .doOnComplete(() -> {
                            success(sender, "IP banned " + input +
                                    (reason != null ? " for: " + reason : ""));
                            kickPlayersWithIp(input, reason);
                        })
                        .doOnError(err -> logAndError(sender, "Failed to IP ban", err))
                        .onErrorComplete();
            } else {
                // Look up player's IP
                return playerResolver.resolvePlayerId(input)
                        .switchIfEmpty(Maybe.defer(() -> {
                            error(sender, "Player '" + input + "' not found.");
                            return Maybe.empty();
                        }))
                        .flatMap(playerId -> sessionStorage.getLastIpAddress(playerId)
                                .switchIfEmpty(Maybe.defer(() -> {
                                    error(sender, "No IP address found for '" + input + "'.");
                                    return Maybe.empty();
                                }))
                                .map(ip -> new IpLookupResult(playerId, ip)))
                        .flatMapCompletable(result ->
                                storage.createIpBan(result.ip, result.playerId, issuerId, reason)
                                        .observeOn(plugin.mainScheduler())
                                        .doOnComplete(() -> {
                                            success(sender, "IP banned " + input + " (" + result.ip + ")" +
                                                    (reason != null ? " for: " + reason : ""));
                                            kickPlayersWithIp(result.ip, reason);
                                        })
                        )
                        .doOnError(err -> logAndError(sender, "Failed to IP ban", err))
                        .onErrorComplete();
            }
        });
    }

    private void kickPlayersWithIp(String ip, String reason) {
        Punishment ipBan = new Punishment(
                UUID.randomUUID(), null, ip, PunishmentType.IP_BAN,
                null, reason, null, Instant.now(), null, null
        );
        Component kickMessage = PunishmentMessages.formatIpBanKickMessage(ipBan);

        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerIp = player.getAddress() != null ?
                    player.getAddress().getAddress().getHostAddress() : null;
            if (ip.equals(playerIp)) {
                player.kick(kickMessage);
            }
        }
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

    private void error(CommandSender sender, String message) {
        sender.sendMessage(PunishmentMessages.PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }

    private void logAndError(CommandSender sender, String context, Throwable err) {
        plugin.getLogger().warning(context + ": " + err.getMessage());
        error(sender, context + ".");
    }

    private record IpLookupResult(UUID playerId, String ip) {
    }
}
