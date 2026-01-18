package sh.joey.mc.anticheat.cmd;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.anticheat.CheatViolationStorage;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.player.PlayerResolver;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class ViolationsCommand implements Command {

    private static final Component PREFIX = Component.text("[AC] ", NamedTextColor.RED);
    private static final int PAGE_SIZE = 10;

    private final SiqiJoeyPlugin plugin;
    private final CheatViolationStorage storage;
    private final PlayerResolver playerResolver;

    public ViolationsCommand(SiqiJoeyPlugin plugin, CheatViolationStorage storage, PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.storage = storage;
        this.playerResolver = playerResolver;
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 0) {
            return showRecentViolations(sender);
        } else {
            return showPlayerViolations(sender, args[0]);
        }
    }

    private Completable showRecentViolations(CommandSender sender) {
        return storage.getRecentViolations(PAGE_SIZE)
                .toList()
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(violations -> displayViolations(sender, violations, null))
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to fetch violations: " + err.getMessage());
                    sender.sendMessage(PREFIX.append(Component.text("Failed to fetch violations.", NamedTextColor.RED)));
                })
                .ignoreElement()
                .onErrorComplete();
    }

    private Completable showPlayerViolations(CommandSender sender, String playerName) {
        return playerResolver.resolvePlayerId(playerName)
                .flatMap(playerId -> storage.getPlayerViolations(playerId, PAGE_SIZE)
                        .toList()
                        .toMaybe())
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(violations -> displayViolations(sender, violations, playerName))
                .doOnComplete(() -> sender.sendMessage(PREFIX.append(Component.text("Player not found: " + playerName, NamedTextColor.RED))))
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to fetch violations: " + err.getMessage());
                    sender.sendMessage(PREFIX.append(Component.text("Failed to fetch violations.", NamedTextColor.RED)));
                })
                .ignoreElement()
                .onErrorComplete();
    }

    private void displayViolations(CommandSender sender, List<CheatViolationStorage.ViolationEntry> violations, String playerFilter) {
        if (violations.isEmpty()) {
            if (playerFilter != null) {
                sender.sendMessage(PREFIX.append(Component.text("No violations found for " + playerFilter, NamedTextColor.GRAY)));
            } else {
                sender.sendMessage(PREFIX.append(Component.text("No recent violations found.", NamedTextColor.GRAY)));
            }
            return;
        }

        Component header;
        if (playerFilter != null) {
            header = PREFIX.append(Component.text("Violations for " + playerFilter + ":", NamedTextColor.YELLOW));
        } else {
            header = PREFIX.append(Component.text("Recent Violations:", NamedTextColor.YELLOW));
        }
        sender.sendMessage(header);
        sender.sendMessage(Component.empty());

        int index = 1;
        for (CheatViolationStorage.ViolationEntry entry : violations) {
            String playerName = getPlayerName(entry.playerId());
            String timeAgo = formatTimeAgo(entry.detectedAt());

            Component line = Component.text("  [" + index + "] ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(playerName, NamedTextColor.GRAY))
                    .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(entry.checkName(), NamedTextColor.YELLOW))
                    .append(Component.text(" (VL: " + String.format("%.1f", entry.violationLevel()) + ")", NamedTextColor.GRAY))
                    .append(Component.text(" - " + timeAgo, NamedTextColor.DARK_GRAY));

            // Show source tag
            String sourceTag = "grim".equals(entry.source()) ? "[GrimAC]" : "[Custom]";
            NamedTextColor sourceColor = "grim".equals(entry.source()) ? NamedTextColor.AQUA : NamedTextColor.LIGHT_PURPLE;
            line = line.append(Component.text(" " + sourceTag, sourceColor));

            if (entry.violationDataJson() != null) {
                line = line.hoverEvent(HoverEvent.showText(Component.text(entry.violationDataJson(), NamedTextColor.GRAY)));
            }

            if (entry.reviewed()) {
                line = line.append(Component.text(" [Reviewed]", NamedTextColor.GREEN));
            }

            sender.sendMessage(line);
            index++;
        }

        sender.sendMessage(Component.empty());

        if (sender instanceof Player) {
            Component hint = PREFIX.append(Component.text("Click a player name above to view their violations.", NamedTextColor.GRAY));
            sender.sendMessage(hint);
        }
    }

    private String getPlayerName(java.util.UUID playerId) {
        var online = plugin.getServer().getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        return playerId.toString().substring(0, 8);
    }

    private String formatTimeAgo(Instant time) {
        Duration duration = Duration.between(time, Instant.now());

        if (duration.toMinutes() < 1) {
            return "just now";
        } else if (duration.toMinutes() < 60) {
            return duration.toMinutes() + "m ago";
        } else if (duration.toHours() < 24) {
            return duration.toHours() + "h ago";
        } else {
            return duration.toDays() + "d ago";
        }
    }

    @Override
    public String getName() {
        return "violations";
    }

    @Override
    public String getPermission() {
        return "smp.anticheat.violations";
    }
}
