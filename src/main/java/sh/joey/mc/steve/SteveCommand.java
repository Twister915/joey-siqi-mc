package sh.joey.mc.steve;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

/**
 * /steve command - view Steve AI status information.
 * <p>
 * Requires smp.steve.admin permission.
 */
public final class SteveCommand implements Command {

    private static final String PERMISSION = "smp.steve.admin";

    private final SteveModel model;

    public SteveCommand(SteveModel model) {
        this.model = model;
    }

    @Override
    public String getName() {
        return "steve";
    }

    @Override
    public String getPermission() {
        return PERMISSION;
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> showStatus(sender));
    }

    private void showStatus(CommandSender sender) {
        SteveModelInfo info = model.info();

        sender.sendMessage(Messages.PREFIX.append(
                Component.text("Status").color(NamedTextColor.GOLD)));

        sender.sendMessage(line("Provider", info.providerName()));
        sender.sendMessage(line("Model ID", info.modelId()));
        sender.sendMessage(line("Display Name", info.displayName()));
    }

    private Component line(String label, String value) {
        return Component.text("  " + label + ": ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(value).color(NamedTextColor.WHITE));
    }
}
