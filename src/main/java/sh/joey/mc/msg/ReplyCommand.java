package sh.joey.mc.msg;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

import java.util.List;
import java.util.UUID;

/**
 * /reply command - Reply to the last person who messaged you.
 * <p>
 * Aliases: /r
 */
public final class ReplyCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("MSG").color(NamedTextColor.LIGHT_PURPLE))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private final SiqiJoeyPlugin plugin;
    private final PrivateMessageManager messageManager;

    public ReplyCommand(SiqiJoeyPlugin plugin, PrivateMessageManager messageManager) {
        this.plugin = plugin;
        this.messageManager = messageManager;
    }

    @Override
    public String getName() {
        return "reply";
    }

    @Override
    public String getPermission() {
        return "smp.msg";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PREFIX.append(Component.text("This command can only be used by players.").color(NamedTextColor.RED)));
                return;
            }

            if (args.length < 1) {
                sender.sendMessage(PREFIX.append(Component.text("Usage: /reply <message>").color(NamedTextColor.RED)));
                return;
            }

            String message = joinArgs(args, 0);
            if (message.isBlank()) {
                sender.sendMessage(PREFIX.append(Component.text("Message cannot be empty.").color(NamedTextColor.RED)));
                return;
            }

            UUID lastSenderId = messageManager.getLastSender(player.getUniqueId()).orElse(null);
            if (lastSenderId == null) {
                player.sendMessage(PREFIX.append(Component.text("No one to reply to.").color(NamedTextColor.RED)));
                return;
            }

            messageManager.sendMessage(player, lastSenderId, message)
                    .observeOn(plugin.mainScheduler())
                    .subscribe(
                            () -> {},
                            err -> {
                                player.sendMessage(PREFIX.append(Component.text("Failed to send reply.").color(NamedTextColor.RED)));
                                plugin.getLogger().warning("Failed to send reply: " + err.getMessage());
                            }
                    );
        });
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        // No tab completion for reply - it's just a message
        return Maybe.empty();
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
