package sh.joey.mc.utility;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.nickname.NicknameManager;

import java.util.Collection;

/**
 * /list - shows online players.
 */
public final class ListCommand implements Command {

    private final NicknameManager nicknameManager;

    public ListCommand(NicknameManager nicknameManager) {
        this.nicknameManager = nicknameManager;
    }

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getPermission() {
        return "smp.list";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            Collection<? extends Player> players = plugin.getServer().getOnlinePlayers();
            int count = players.size();
            int max = plugin.getServer().getMaxPlayers();

            sender.sendMessage(Component.text("Online Players (" + count + "/" + max + "):")
                    .color(NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD));

            if (players.isEmpty()) {
                sender.sendMessage(Component.text("  No players online.").color(NamedTextColor.GRAY));
                return;
            }

            Component playerList = Component.empty();
            boolean first = true;
            for (Player player : players) {
                if (!first) {
                    playerList = playerList.append(Component.text(", ").color(NamedTextColor.GRAY));
                }
                first = false;
                playerList = playerList.append(Component.text(nicknameManager.getDisplayName(player))
                        .color(NamedTextColor.WHITE));
            }

            sender.sendMessage(Component.text("  ").append(playerList));
        });
    }
}
