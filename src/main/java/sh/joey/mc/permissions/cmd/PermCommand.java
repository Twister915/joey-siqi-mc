package sh.joey.mc.permissions.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.permissions.DisplayManager;
import sh.joey.mc.permissions.PermissionAttacher;
import sh.joey.mc.permissions.PermissionCache;
import sh.joey.mc.permissions.PermissionStorage;
import sh.joey.mc.permissions.cmd.group.GroupSubcommand;
import sh.joey.mc.permissions.cmd.player.PlayerSubcommand;
import sh.joey.mc.session.PlayerSessionStorage;

import java.util.ArrayList;
import java.util.List;

/**
 * Main router for /perm command.
 * Routes to group, player, or reload subcommands.
 */
public final class PermCommand implements Command {

    public static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Perm").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private final SiqiJoeyPlugin plugin;
    private final GroupSubcommand groupHandler;
    private final PlayerSubcommand playerHandler;
    private final PermEffects effects;

    public PermCommand(
            SiqiJoeyPlugin plugin,
            PermissionStorage storage,
            PlayerSessionStorage sessionStorage,
            PermissionCache cache,
            PermissionAttacher attacher,
            DisplayManager displayManager
    ) {
        this.plugin = plugin;
        this.effects = new PermEffects(cache, attacher, displayManager);
        this.groupHandler = new GroupSubcommand(plugin, storage, sessionStorage, effects);
        this.playerHandler = new PlayerSubcommand(plugin, storage, sessionStorage, effects);
    }

    @Override
    public String getName() {
        return "perm";
    }

    @Override
    public String getPermission() {
        return "smp.perm.admin";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length == 0) {
                return showHelp(sender);
            }

            String subcommand = args[0].toLowerCase();
            String[] remaining = dropFirst(args);

            return switch (subcommand) {
                case "group" -> groupHandler.execute(sender, remaining);
                case "player" -> playerHandler.execute(sender, remaining);
                case "reload" -> handleReload(sender);
                case "help" -> showHelp(sender);
                default -> showHelp(sender);
            };
        });
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.defer(() -> {
            if (args.length <= 1) {
                String partial = args.length == 1 ? args[0].toLowerCase() : "";
                List<Completion> completions = new ArrayList<>();
                for (String cmd : List.of("group", "player", "reload", "help")) {
                    if (cmd.startsWith(partial)) {
                        completions.add(Completion.completion(cmd));
                    }
                }
                return Maybe.just(completions);
            }

            String subcommand = args[0].toLowerCase();
            String[] remaining = dropFirst(args);

            return switch (subcommand) {
                case "group" -> groupHandler.tabComplete(sender, remaining);
                case "player" -> playerHandler.tabComplete(sender, remaining);
                default -> Maybe.empty();
            };
        });
    }

    private Completable handleReload(CommandSender sender) {
        return effects.onReload()
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> success(sender, "Permissions reloaded for all online players."))
                .doOnError(err -> error(sender, "Reload failed: " + err.getMessage()))
                .onErrorComplete();
    }

    private Completable showHelp(CommandSender sender) {
        return Completable.fromAction(() -> {
            sender.sendMessage(PREFIX.append(Component.text("Permission Commands:").color(NamedTextColor.WHITE)));
            sender.sendMessage(Component.empty());
            sender.sendMessage(Component.text("  /perm group list").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  /perm group <name> create [priority]").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  /perm group <name> delete").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  /perm group <name> default true/false").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  /perm group <name> priority <int>").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  /perm group <name> set <perm> [world] true/false").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  /perm group <name> unset <perm>").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  /perm group <name> grants").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  /perm group <name> add <player>").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  /perm group <name> remove <player>").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  /perm group <name> chat|nameplate prefix|suffix <value>").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  /perm group <name> inspect").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.empty());
            sender.sendMessage(Component.text("  /perm player <name> set <perm> [world] true/false").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  /perm player <name> unset <perm>").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  /perm player <name> chat|nameplate prefix|suffix <value>").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.text("  /perm player <name> inspect").color(NamedTextColor.AQUA));
            sender.sendMessage(Component.empty());
            sender.sendMessage(Component.text("  /perm reload").color(NamedTextColor.AQUA));
        });
    }

    private static void success(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.GREEN)));
    }

    private static void error(CommandSender sender, String message) {
        sender.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.RED)));
    }

    private static String[] dropFirst(String[] args) {
        if (args.length <= 1) return new String[0];
        String[] result = new String[args.length - 1];
        System.arraycopy(args, 1, result, 0, result.length);
        return result;
    }
}
