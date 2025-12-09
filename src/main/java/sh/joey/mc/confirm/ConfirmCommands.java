package sh.joey.mc.confirm;

import io.reactivex.rxjava3.core.Completable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

/**
 * Command handlers for /accept and /decline.
 */
public final class ConfirmCommands {

    private ConfirmCommands() {}

    public static Command accept(ConfirmationManager manager) {
        return new Command() {
            @Override
            public String getName() {
                return "accept";
            }

            @Override
            public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
                return Completable.fromAction(() -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("This command can only be used by players.");
                        return;
                    }
                    manager.accept(player);
                });
            }
        };
    }

    public static Command decline(ConfirmationManager manager) {
        return new Command() {
            @Override
            public String getName() {
                return "decline";
            }

            @Override
            public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
                return Completable.fromAction(() -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("This command can only be used by players.");
                        return;
                    }
                    manager.decline(player);
                });
            }
        };
    }
}
