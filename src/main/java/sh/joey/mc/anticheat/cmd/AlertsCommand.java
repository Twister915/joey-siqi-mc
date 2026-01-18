package sh.joey.mc.anticheat.cmd;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.anticheat.ModeratorNotifier;
import sh.joey.mc.cmd.Command;

public final class AlertsCommand implements Command {

    private static final Component PREFIX = Component.text("[AC] ", NamedTextColor.RED);

    private final ModeratorNotifier notifier;

    public AlertsCommand(ModeratorNotifier notifier) {
        this.notifier = notifier;
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PREFIX.append(Component.text("This command can only be used by players.", NamedTextColor.RED)));
                return;
            }

            notifier.toggleAlerts(player.getUniqueId());
            boolean muted = notifier.hasAlertsMuted(player.getUniqueId());

            if (muted) {
                sender.sendMessage(PREFIX.append(Component.text("Anti-cheat alerts muted.", NamedTextColor.YELLOW)));
            } else {
                sender.sendMessage(PREFIX.append(Component.text("Anti-cheat alerts enabled.", NamedTextColor.GREEN)));
            }
        });
    }

    @Override
    public String getName() {
        return "alerts";
    }

    @Override
    public String getPermission() {
        return "smp.anticheat.alerts";
    }
}
