package sh.joey.mc.confirm;

import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

/**
 * Command handlers for /accept and /decline.
 */
public final class ConfirmCommands {

    private ConfirmCommands() {}

    public static CommandExecutor accept(ConfirmationManager manager) {
        return (sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }
            manager.accept(player);
            return true;
        };
    }

    public static CommandExecutor decline(ConfirmationManager manager) {
        return (sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }
            manager.decline(player);
            return true;
        };
    }
}
