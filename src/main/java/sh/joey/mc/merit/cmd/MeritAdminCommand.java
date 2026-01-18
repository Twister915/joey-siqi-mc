package sh.joey.mc.merit.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.merit.LevelCalculator;
import sh.joey.mc.merit.MeritManager;
import sh.joey.mc.merit.Messages;
import sh.joey.mc.player.PlayerResolver;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Admin command for managing merit.
 * Usage: /meritadmin grant|set|reset <player> <amount>
 */
public final class MeritAdminCommand implements Command {

    private final SiqiJoeyPlugin plugin;
    private final MeritManager meritManager;
    private final PlayerResolver playerResolver;

    public MeritAdminCommand(SiqiJoeyPlugin plugin, MeritManager meritManager, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.meritManager = meritManager;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "meritadmin";
    }

    @Override
    public @Nullable String getPermission() {
        return "smp.merit.admin";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (args.length < 2) {
                showUsage(sender);
                return;
            }

            String subcommand = args[0].toLowerCase();
            String playerName = args[1];

            switch (subcommand) {
                case "grant" -> {
                    if (args.length < 3) {
                        error(sender, "Usage: /meritadmin grant <player> <amount>");
                        return;
                    }
                    handleGrant(sender, playerName, args[2]);
                }
                case "set" -> {
                    if (args.length < 3) {
                        error(sender, "Usage: /meritadmin set <player> <amount>");
                        return;
                    }
                    handleSet(sender, playerName, args[2]);
                }
                case "reset" -> handleReset(sender, playerName);
                case "reload" -> handleReload(sender);
                default -> showUsage(sender);
            }
        });
    }

    private void showUsage(CommandSender sender) {
        sender.sendMessage(Messages.PREFIX.append(Component.text("Merit Admin Commands:", NamedTextColor.GOLD)));
        sender.sendMessage(Component.text("  /meritadmin grant <player> <amount>", NamedTextColor.AQUA)
                .append(Component.text(" - Add merit", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /meritadmin set <player> <amount>", NamedTextColor.AQUA)
                .append(Component.text(" - Set total merit", NamedTextColor.GRAY)));
        sender.sendMessage(Component.text("  /meritadmin reset <player>", NamedTextColor.AQUA)
                .append(Component.text(" - Reset to 0", NamedTextColor.GRAY)));
    }

    private void handleGrant(CommandSender sender, String playerName, String amountStr) {
        long amount;
        try {
            amount = Long.parseLong(amountStr);
        } catch (NumberFormatException e) {
            error(sender, "Invalid amount: " + amountStr);
            return;
        }

        if (amount <= 0) {
            error(sender, "Amount must be positive.");
            return;
        }

        playerResolver.resolvePlayerId(playerName)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        playerId -> {
                            meritManager.getProgressTracker().awardMerit(playerId, amount);
                            success(sender, "Granted " + amount + " merit to " + playerName);
                        },
                        err -> error(sender, "Failed to resolve player: " + err.getMessage()),
                        () -> error(sender, "Player not found: " + playerName)
                );
    }

    private void handleSet(CommandSender sender, String playerName, String amountStr) {
        long amount;
        try {
            amount = Long.parseLong(amountStr);
        } catch (NumberFormatException e) {
            error(sender, "Invalid amount: " + amountStr);
            return;
        }

        if (amount < 0) {
            error(sender, "Amount cannot be negative.");
            return;
        }

        LevelCalculator calc = meritManager.getLevelCalculator();
        int level = calc.levelForMerit(amount);

        playerResolver.resolvePlayerId(playerName)
                .flatMapCompletable(playerId ->
                        meritManager.getStorage().setMerit(playerId, amount, level))
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        () -> success(sender, "Set " + playerName + "'s merit to " + amount + " (Level " + level + ")"),
                        err -> error(sender, "Failed: " + err.getMessage())
                );
    }

    private void handleReset(CommandSender sender, String playerName) {
        playerResolver.resolvePlayerId(playerName)
                .flatMapCompletable(playerId ->
                        meritManager.getStorage().setMerit(playerId, 0, 1))
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        () -> success(sender, "Reset " + playerName + "'s merit to 0"),
                        err -> error(sender, "Failed: " + err.getMessage())
                );
    }

    private void handleReload(CommandSender sender) {
        success(sender, "Merit system reloaded.");
    }

    private void error(CommandSender sender, String message) {
        sender.sendMessage(Messages.PREFIX.append(Component.text(message, NamedTextColor.RED)));
    }

    private void success(CommandSender sender, String message) {
        sender.sendMessage(Messages.PREFIX.append(Component.text(message, NamedTextColor.GREEN)));
    }

    @Override
    public Maybe<List<AsyncTabCompleteEvent.Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<AsyncTabCompleteEvent.Completion> completions = List.of(
                    AsyncTabCompleteEvent.Completion.completion("grant"),
                    AsyncTabCompleteEvent.Completion.completion("set"),
                    AsyncTabCompleteEvent.Completion.completion("reset")
            );
            return Maybe.just(completions);
        }
        if (args.length == 2) {
            String partial = args[1].toLowerCase();
            List<AsyncTabCompleteEvent.Completion> completions = plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partial))
                    .map(AsyncTabCompleteEvent.Completion::completion)
                    .collect(Collectors.toList());
            return Maybe.just(completions);
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("grant") || args[0].equalsIgnoreCase("set"))) {
            List<AsyncTabCompleteEvent.Completion> completions = List.of(
                    AsyncTabCompleteEvent.Completion.completion("100"),
                    AsyncTabCompleteEvent.Completion.completion("500"),
                    AsyncTabCompleteEvent.Completion.completion("1000"),
                    AsyncTabCompleteEvent.Completion.completion("5000"),
                    AsyncTabCompleteEvent.Completion.completion("10000")
            );
            return Maybe.just(completions);
        }
        return Maybe.empty();
    }
}
