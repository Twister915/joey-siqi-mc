package sh.joey.mc.utility;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.confirm.ConfirmationManager;
import sh.joey.mc.confirm.ConfirmationRequest;

/**
 * /suicide - kills the player (for respawning).
 */
public final class SuicideCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("!").color(NamedTextColor.RED).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private static final int CONFIRM_TIMEOUT_SECONDS = 10;

    private final ConfirmationManager confirmationManager;

    public SuicideCommand(ConfirmationManager confirmationManager) {
        this.confirmationManager = confirmationManager;
    }

    @Override
    public String getName() {
        return "suicide";
    }

    @Override
    public String getPermission() {
        return "smp.suicide";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return;
            }

            if (player.isDead()) {
                sender.sendMessage(Component.text("You are already dead.").color(NamedTextColor.RED));
                return;
            }

            // Require confirmation in survival mode (items would be dropped)
            if (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE) {
                requestConfirmation(player);
            } else {
                player.setHealth(0);
            }
        });
    }

    private void requestConfirmation(Player player) {
        confirmationManager.request(player, new ConfirmationRequest() {
            @Override
            public Component prefix() {
                return PREFIX;
            }

            @Override
            public String promptText() {
                return "Kill yourself? You will drop your items!";
            }

            @Override
            public String acceptText() {
                return "Die";
            }

            @Override
            public String declineText() {
                return "Cancel";
            }

            @Override
            public void onAccept() {
                if (!player.isDead()) {
                    player.setHealth(0);
                }
            }

            @Override
            public void onDecline() {
                player.sendMessage(PREFIX.append(Component.text("Cancelled.").color(NamedTextColor.GRAY)));
            }

            @Override
            public int timeoutSeconds() {
                return CONFIRM_TIMEOUT_SECONDS;
            }
        });
    }
}
