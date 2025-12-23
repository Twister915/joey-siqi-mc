package sh.joey.mc.utility;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
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

import java.util.List;

/**
 * /clear [player] - clears inventory.
 * /ci is an alias that works the same way.
 */
public final class ClearCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Clear").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private static final int CONFIRM_TIMEOUT_SECONDS = 15;

    private final String name;
    private final ConfirmationManager confirmationManager;

    public ClearCommand(String name, ConfirmationManager confirmationManager) {
        this.name = name;
        this.confirmationManager = confirmationManager;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPermission() {
        return "smp.clear";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            Player target;
            boolean clearingOther = false;

            if (args.length >= 1) {
                // Clear another player's inventory
                if (!sender.hasPermission("smp.clear.others")) {
                    sender.sendMessage(Component.text("You don't have permission to clear other players' inventories.")
                            .color(NamedTextColor.RED));
                    return;
                }
                target = plugin.getServer().getPlayer(args[0]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player '" + args[0] + "' is not online.")
                            .color(NamedTextColor.RED));
                    return;
                }
                clearingOther = !target.equals(sender);
            } else {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Usage: /clear <player>");
                    return;
                }
                target = player;
            }

            // Require confirmation in survival mode (items would be lost)
            if (target.getGameMode() == GameMode.SURVIVAL || target.getGameMode() == GameMode.ADVENTURE) {
                requestConfirmation(sender, target, clearingOther);
            } else {
                // Creative/Spectator - just clear immediately
                doClear(sender, target, clearingOther);
            }
        });
    }

    private void requestConfirmation(CommandSender sender, Player target, boolean clearingOther) {
        // If clearing someone else, the confirmation goes to the sender
        // If clearing self, confirmation goes to self
        Player confirmee = (sender instanceof Player p) ? p : target;

        String prompt = clearingOther
                ? "Clear " + target.getName() + "'s inventory? This cannot be undone!"
                : "Clear your inventory? This cannot be undone!";

        confirmationManager.request(confirmee, new ConfirmationRequest() {
            @Override
            public Component prefix() {
                return PREFIX;
            }

            @Override
            public String promptText() {
                return prompt;
            }

            @Override
            public String acceptText() {
                return "Clear";
            }

            @Override
            public String declineText() {
                return "Cancel";
            }

            @Override
            public void onAccept() {
                doClear(sender, target, clearingOther);
            }

            @Override
            public void onDecline() {
                confirmee.sendMessage(PREFIX.append(Component.text("Cancelled.").color(NamedTextColor.GRAY)));
            }

            @Override
            public int timeoutSeconds() {
                return CONFIRM_TIMEOUT_SECONDS;
            }
        });
    }

    private void doClear(CommandSender sender, Player target, boolean clearingOther) {
        target.getInventory().clear();
        target.sendMessage(PREFIX.append(Component.text("Your inventory has been cleared.").color(NamedTextColor.YELLOW)));

        if (clearingOther) {
            sender.sendMessage(PREFIX.append(Component.text("Cleared " + target.getName() + "'s inventory.")
                    .color(NamedTextColor.GREEN)));
        }
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.fromCallable(() -> {
            if (args.length != 1 || !sender.hasPermission("smp.clear.others")) {
                return null;
            }

            String prefix = args[0].toLowerCase();
            List<Completion> completions = plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .map(Completion::completion)
                    .toList();

            return completions.isEmpty() ? null : completions;
        });
    }
}
