package sh.joey.mc.teleport.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import sh.joey.mc.teleport.RequestManager;

/**
 * /accept and /decline commands for responding to teleport requests.
 */
public final class YesNoCommands {

    public static CommandExecutor accept(RequestManager requestManager) {
        return (sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }
            requestManager.acceptRequest(player);
            return true;
        };
    }

    public static CommandExecutor decline(RequestManager requestManager) {
        return (sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }
            requestManager.declineRequest(player);
            return true;
        };
    }
}
