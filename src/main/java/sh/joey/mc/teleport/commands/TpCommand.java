package sh.joey.mc.teleport.commands;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.confirm.ConfirmationManager;
import sh.joey.mc.confirm.ConfirmationRequest;
import sh.joey.mc.player.PlayerResolver;
import sh.joey.mc.teleport.Messages;
import sh.joey.mc.teleport.PluginConfig;
import sh.joey.mc.teleport.SafeTeleporter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * /tp <player> command - sends a teleport request to another player.
 * Accepts both usernames and nicknames as player identifiers.
 */
public final class TpCommand implements Command {
    private final SiqiJoeyPlugin plugin;
    private final PluginConfig config;
    private final SafeTeleporter safeTeleporter;
    private final ConfirmationManager confirmationManager;
    private final PlayerResolver playerResolver;

    public TpCommand(SiqiJoeyPlugin plugin, PluginConfig config, SafeTeleporter safeTeleporter,
                     ConfirmationManager confirmationManager, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.config = config;
        this.safeTeleporter = safeTeleporter;
        this.confirmationManager = confirmationManager;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "tp";
    }

    @Override
    public String getPermission() {
        return "smp.tp";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return Completable.complete();
            }

            if (args.length != 1) {
                Messages.error(player, "Usage: /tp <player>");
                return Completable.complete();
            }

            String targetName = args[0];

            // Resolve player by username or nickname
            Optional<Player> targetOpt = playerResolver.resolveOnlinePlayer(targetName);

            if (targetOpt.isEmpty()) {
                Messages.error(player, "Player '" + targetName + "' is not online.");
                return Completable.complete();
            }

            Player target = targetOpt.get();

            if (target.equals(player)) {
                Messages.error(player, "You can't teleport to yourself!");
                return Completable.complete();
            }

            sendRequest(player, target);
            return Completable.complete();
        });
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (args.length != 1) {
            return Maybe.empty();
        }

        String prefix = args[0];
        String senderName = (sender instanceof Player p) ? p.getName() : null;

        return playerResolver.getCompletions(prefix, 20)
                .map(names -> names.stream()
                        .filter(name -> senderName == null || !name.equalsIgnoreCase(senderName))
                        .map(Completion::completion)
                        .toList())
                .filter(list -> !list.isEmpty());
    }

    private void sendRequest(Player requester, Player target) {
        UUID requesterId = requester.getUniqueId();
        UUID targetId = target.getUniqueId();

        confirmationManager.request(target, new ConfirmationRequest() {
            @Override
            public Component prefix() {
                return Messages.PREFIX;
            }

            @Override
            public String promptText() {
                return requester.getName() + " wants to teleport to you!";
            }

            @Override
            public void onAccept() {
                Player req = plugin.getServer().getPlayer(requesterId);
                Player tgt = plugin.getServer().getPlayer(targetId);
                if (req != null && tgt != null) {
                    Messages.success(tgt, "Accepted teleport from " + req.getName() + "!");
                    Messages.success(req, tgt.getName() + " accepted your request!");
                    safeTeleporter.teleport(req, tgt.getLocation(), s -> {});
                }
            }

            @Override
            public void onDecline() {
                Player req = plugin.getServer().getPlayer(requesterId);
                Player tgt = plugin.getServer().getPlayer(targetId);
                if (tgt != null) {
                    Messages.info(tgt, "Request declined.");
                }
                if (req != null) {
                    Messages.warning(req, target.getName() + " declined your request.");
                }
            }

            @Override
            public void onTimeout() {
                Player req = plugin.getServer().getPlayer(requesterId);
                Player tgt = plugin.getServer().getPlayer(targetId);
                if (req != null) {
                    Messages.warning(req, "Request to " + target.getName() + " expired.");
                }
                if (tgt != null) {
                    Messages.info(tgt, "Request from " + requester.getName() + " expired.");
                }
            }

            @Override
            public Completable invalidation() {
                // Invalidate if requester quits (target quit handled by ConfirmationManager)
                return plugin.watchEvent(PlayerQuitEvent.class)
                    .filter(e -> e.getPlayer().getUniqueId().equals(requesterId))
                    .take(1)
                    .ignoreElements();
            }

            @Override
            public int timeoutSeconds() {
                return config.requestTimeoutSeconds();
            }
        });

        Messages.success(requester, "Request sent to " + target.getName() + "!");
    }
}
