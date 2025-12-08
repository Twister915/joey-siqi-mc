package sh.joey.mc.teleport.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.teleport.Messages;
import sh.joey.mc.teleport.RequestManager;

import java.util.List;

/**
 * /tp <player> command - sends a teleport request to another player.
 */
public final class TpCommand implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final RequestManager requestManager;

    public TpCommand(JavaPlugin plugin, RequestManager requestManager) {
        this.plugin = plugin;
        this.requestManager = requestManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length != 1) {
            Messages.error(player, "Usage: /tp <player>");
            return true;
        }

        String targetName = args[0];
        Player target = plugin.getServer().getPlayer(targetName);

        if (target == null) {
            Messages.error(player, "Player '" + targetName + "' is not online.");
            return true;
        }

        if (target.equals(player)) {
            Messages.error(player, "You can't teleport to yourself!");
            return true;
        }

        requestManager.sendRequest(player, target);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .filter(name -> !(sender instanceof Player p) || !name.equals(p.getName()))
                    .toList();
        }
        return List.of();
    }
}
