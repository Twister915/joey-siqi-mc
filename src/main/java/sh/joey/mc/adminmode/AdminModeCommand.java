package sh.joey.mc.adminmode;

import io.reactivex.rxjava3.core.Completable;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

/**
 * /adminmode - toggles admin creative mode.
 */
public final class AdminModeCommand implements Command {

    private final AdminModeManager adminModeManager;

    public AdminModeCommand(AdminModeManager adminModeManager) {
        this.adminModeManager = adminModeManager;
    }

    @Override
    public String getName() {
        return "adminmode";
    }

    @Override
    public String getPermission() {
        return "smp.adminmode";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return;
            }

            adminModeManager.toggleAdminMode(player, success -> {
                // Callback is handled by AdminModeManager with appropriate messages
            });
        });
    }
}
