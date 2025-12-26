package sh.joey.mc.msg;

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

import java.util.List;

/**
 * /msg command - Send a private message to another player.
 * <p>
 * Aliases: /m, /t, /tell, /whisper, /pm, /w
 */
public final class MsgCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("MSG").color(NamedTextColor.LIGHT_PURPLE))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private final SiqiJoeyPlugin plugin;
    private final PlayerResolver playerResolver;
    private final PrivateMessageManager messageManager;

    public MsgCommand(SiqiJoeyPlugin plugin, PlayerResolver playerResolver, PrivateMessageManager messageManager) {
        this.plugin = plugin;
        this.playerResolver = playerResolver;
        this.messageManager = messageManager;
    }

    @Override
    public String getName() {
        return "msg";
    }

    @Override
    public String getPermission() {
        return "smp.msg";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PREFIX.append(Component.text("This command can only be used by players.").color(NamedTextColor.RED)));
                return Completable.complete();
            }

            if (args.length < 2) {
                sender.sendMessage(PREFIX.append(Component.text("Usage: /msg <player> <message>").color(NamedTextColor.RED)));
                return Completable.complete();
            }

            String targetName = args[0];
            String message = joinArgs(args, 1);

            if (message.isBlank()) {
                sender.sendMessage(PREFIX.append(Component.text("Message cannot be empty.").color(NamedTextColor.RED)));
                return Completable.complete();
            }

            return playerResolver.resolvePlayerId(targetName)
                    .switchIfEmpty(Maybe.defer(() -> {
                        player.sendMessage(PREFIX.append(Component.text("Player not found: " + targetName).color(NamedTextColor.RED)));
                        return Maybe.empty();
                    }))
                    .observeOn(plugin.mainScheduler())
                    .flatMapCompletable(targetId -> {
                        if (targetId.equals(player.getUniqueId())) {
                            player.sendMessage(PREFIX.append(Component.text("You can't message yourself.").color(NamedTextColor.RED)));
                            return Completable.complete();
                        }
                        return messageManager.sendMessage(player, targetId, message);
                    })
                    .onErrorComplete(err -> {
                        player.sendMessage(PREFIX.append(Component.text("Failed to send message.").color(NamedTextColor.RED)));
                        plugin.getLogger().warning("Failed to send private message: " + err.getMessage());
                        return true;
                    });
        });
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (args.length != 1 || !(sender instanceof Player player)) {
            return Maybe.empty();
        }

        String prefix = args[0];
        return playerResolver.getCompletions(prefix, 20)
                .map(names -> names.stream()
                        .filter(name -> !name.equalsIgnoreCase(player.getName()))
                        .map(Completion::completion)
                        .toList())
                .filter(list -> !list.isEmpty());
    }

    private String joinArgs(String[] args, int startIndex) {
        if (startIndex >= args.length) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = startIndex; i < args.length; i++) {
            if (i > startIndex) {
                sb.append(" ");
            }
            sb.append(args[i]);
        }
        return sb.toString();
    }
}
