package sh.joey.mc.settings;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

import java.util.List;

/**
 * /settings command - manage player settings with clickable UI.
 */
public final class SettingsCommand implements Command {

    private static final String PERM_KEEP_INVENTORY = "smp.settings.keepinventory";
    private static final String PERM_DISPLAY_TIME = "smp.settings.displaytime";
    private static final String PERM_EASY_MODE = "smp.settings.easymode";

    private final SettingsManager manager;

    public SettingsCommand(SettingsManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "settings";
    }

    @Override
    public String getPermission() {
        return null; // Permission checked per-setting
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return;
            }

            // Check for toggle commands
            if (args.length >= 2) {
                String setting = args[0].toLowerCase();
                String value = args[1].toLowerCase();
                handleToggle(player, setting, value);
                return;
            }

            // Show settings menu
            showSettings(player);
        });
    }

    private void handleToggle(Player player, String setting, String value) {
        switch (setting) {
            case "keepinventory" -> {
                if (!player.hasPermission(PERM_KEEP_INVENTORY)) {
                    Messages.error(player, "You don't have permission to change this setting.");
                    return;
                }
                boolean enabled = value.equals("on");
                manager.setKeepInventory(player.getUniqueId(), enabled);
                Messages.success(player, "Keep Inventory " + (enabled ? "enabled" : "disabled") + ".");
                showSettings(player);
            }
            case "displaytime" -> {
                if (!player.hasPermission(PERM_DISPLAY_TIME)) {
                    Messages.error(player, "You don't have permission to change this setting.");
                    return;
                }
                DisplayTimeSetting displaySetting = switch (value) {
                    case "always" -> DisplayTimeSetting.ALWAYS;
                    case "clock" -> DisplayTimeSetting.HOLDING_CLOCK;
                    case "never" -> DisplayTimeSetting.NEVER;
                    default -> null;
                };
                if (displaySetting == null) {
                    Messages.error(player, "Invalid display time option.");
                    return;
                }
                manager.setDisplayTime(player.getUniqueId(), displaySetting);
                Messages.success(player, "Display Time set to " + displaySetting.displayName() + ".");
                showSettings(player);
            }
            case "easymode" -> {
                if (!player.hasPermission(PERM_EASY_MODE)) {
                    Messages.error(player, "You don't have permission to change this setting.");
                    return;
                }
                boolean enabled = value.equals("on");
                manager.setEasyMode(player.getUniqueId(), enabled);
                Messages.success(player, "Easy Mode " + (enabled ? "enabled" : "disabled") + ".");
                showSettings(player);
            }
            default -> Messages.error(player, "Unknown setting.");
        }
    }

    private void showSettings(Player player) {
        PlayerSettings settings = manager.getSettings(player.getUniqueId());

        boolean hasKeepInventory = player.hasPermission(PERM_KEEP_INVENTORY);
        boolean hasDisplayTime = player.hasPermission(PERM_DISPLAY_TIME);
        boolean hasEasyMode = player.hasPermission(PERM_EASY_MODE);

        if (!hasKeepInventory && !hasDisplayTime && !hasEasyMode) {
            Messages.error(player, "You don't have access to any settings.");
            return;
        }

        player.sendMessage(Messages.PREFIX.append(Component.text("Your Settings:", NamedTextColor.WHITE)));
        player.sendMessage(Component.empty());

        // Keep Inventory
        if (hasKeepInventory) {
            Component onButton = createToggleButton("On", settings.keepInventory(), "/settings keepinventory on");
            Component offButton = createToggleButton("Off", !settings.keepInventory(), "/settings keepinventory off");

            player.sendMessage(Component.text("  Keep Inventory: ", NamedTextColor.GRAY)
                    .append(onButton)
                    .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                    .append(offButton));
            player.sendMessage(Component.text("    Preserve items and XP when you die", NamedTextColor.DARK_GRAY));
            player.sendMessage(Component.empty());
        }

        // Display Time
        if (hasDisplayTime) {
            Component alwaysBtn = createToggleButton("Always",
                    settings.displayTime() == DisplayTimeSetting.ALWAYS,
                    "/settings displaytime always");
            Component clockBtn = createToggleButton("Clock",
                    settings.displayTime() == DisplayTimeSetting.HOLDING_CLOCK,
                    "/settings displaytime clock");
            Component neverBtn = createToggleButton("Never",
                    settings.displayTime() == DisplayTimeSetting.NEVER,
                    "/settings displaytime never");

            player.sendMessage(Component.text("  Display Time: ", NamedTextColor.GRAY)
                    .append(alwaysBtn)
                    .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                    .append(clockBtn)
                    .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                    .append(neverBtn));
            player.sendMessage(Component.text("    When to show the time in the boss bar", NamedTextColor.DARK_GRAY));
            player.sendMessage(Component.empty());
        }

        // Easy Mode
        if (hasEasyMode) {
            Component onButton = createToggleButton("On", settings.easyMode(), "/settings easymode on");
            Component offButton = createToggleButton("Off", !settings.easyMode(), "/settings easymode off");

            player.sendMessage(Component.text("  Easy Mode: ", NamedTextColor.GRAY)
                    .append(onButton)
                    .append(Component.text(" ", NamedTextColor.DARK_GRAY))
                    .append(offButton));
            player.sendMessage(Component.text("    Mobs deal 25% damage + 5% insta-kill chance", NamedTextColor.DARK_GRAY));
        }
    }

    private Component createToggleButton(String label, boolean isSelected, String command) {
        if (isSelected) {
            return Component.text("[" + label + "]", NamedTextColor.GREEN)
                    .hoverEvent(HoverEvent.showText(Component.text("Currently selected", NamedTextColor.GRAY)));
        } else {
            return Component.text("[" + label + "]", NamedTextColor.YELLOW)
                    .clickEvent(ClickEvent.runCommand(command))
                    .hoverEvent(HoverEvent.showText(Component.text("Click to select", NamedTextColor.GRAY)));
        }
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.defer(() -> {
            if (!(sender instanceof Player player)) {
                return Maybe.empty();
            }

            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                List<String> options = new java.util.ArrayList<>();

                if (player.hasPermission(PERM_KEEP_INVENTORY) && "keepinventory".startsWith(prefix)) {
                    options.add("keepinventory");
                }
                if (player.hasPermission(PERM_DISPLAY_TIME) && "displaytime".startsWith(prefix)) {
                    options.add("displaytime");
                }
                if (player.hasPermission(PERM_EASY_MODE) && "easymode".startsWith(prefix)) {
                    options.add("easymode");
                }

                return Maybe.just(options.stream()
                        .map(Completion::completion)
                        .toList());
            }

            if (args.length == 2) {
                String setting = args[0].toLowerCase();
                String prefix = args[1].toLowerCase();

                List<String> options = switch (setting) {
                    case "keepinventory", "easymode" -> List.of("on", "off");
                    case "displaytime" -> List.of("always", "clock", "never");
                    default -> List.of();
                };

                return Maybe.just(options.stream()
                        .filter(opt -> opt.startsWith(prefix))
                        .map(Completion::completion)
                        .toList());
            }

            return Maybe.empty();
        });
    }
}
