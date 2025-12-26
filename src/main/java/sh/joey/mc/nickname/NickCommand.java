package sh.joey.mc.nickname;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.session.PlayerSessionStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * /nick command for setting and managing player nicknames.
 * <p>
 * Usage:
 * <ul>
 *   <li>/nick - Show current nickname</li>
 *   <li>/nick &lt;name&gt; - Set own nickname</li>
 *   <li>/nick clear - Clear own nickname</li>
 *   <li>/nick &lt;username&gt; &lt;nickname&gt; - Set another player's nickname (admin)</li>
 *   <li>/nick &lt;username&gt; clear - Clear another player's nickname (admin)</li>
 * </ul>
 */
public final class NickCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Nick").color(NamedTextColor.LIGHT_PURPLE))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private static final String PERMISSION = "smp.nick";
    private static final String PERMISSION_OTHERS = "smp.nick.others";

    private final SiqiJoeyPlugin plugin;
    private final PlayerSessionStorage sessionStorage;
    private final NicknameValidator validator;
    private final NicknameManager nicknameManager;

    public NickCommand(SiqiJoeyPlugin plugin, PlayerSessionStorage sessionStorage,
                       NicknameValidator validator, NicknameManager nicknameManager) {
        this.plugin = plugin;
        this.sessionStorage = sessionStorage;
        this.validator = validator;
        this.nicknameManager = nicknameManager;
    }

    @Override
    public String getName() {
        return "nick";
    }

    @Override
    public String getPermission() {
        return PERMISSION;
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (args.length == 0) {
                return handleShowNickname(sender);
            } else if (args.length == 1) {
                return handleOneArg(sender, args[0]);
            } else if (args.length == 2) {
                return handleTwoArgs(sender, args[0], args[1]);
            } else {
                sender.sendMessage(PREFIX.append(Component.text("Usage: /nick [name] or /nick <player> <name>")
                        .color(NamedTextColor.RED)));
                return Completable.complete();
            }
        });
    }

    /**
     * /nick - Show current nickname
     */
    private Completable handleShowNickname(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Usage: /nick <player> <name>");
            return Completable.complete();
        }

        String nickname = nicknameManager.getNickname(player.getUniqueId());
        if (nickname != null) {
            sender.sendMessage(PREFIX.append(Component.text("Your nickname is ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(nickname).color(NamedTextColor.WHITE))
                    .append(Component.text(".").color(NamedTextColor.GRAY))));
        } else {
            sender.sendMessage(PREFIX.append(Component.text("You don't have a nickname set.")
                    .color(NamedTextColor.GRAY)));
        }
        return Completable.complete();
    }

    /**
     * /nick <name> or /nick clear
     */
    private Completable handleOneArg(CommandSender sender, String arg) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Usage: /nick <player> <name>");
            return Completable.complete();
        }

        if (arg.equalsIgnoreCase("clear") || arg.equalsIgnoreCase("reset")) {
            return handleClearOwnNickname(player);
        } else {
            return handleSetOwnNickname(player, arg);
        }
    }

    /**
     * /nick clear - Clear own nickname
     */
    private Completable handleClearOwnNickname(Player player) {
        if (!nicknameManager.hasNickname(player.getUniqueId())) {
            player.sendMessage(PREFIX.append(Component.text("You don't have a nickname set.")
                    .color(NamedTextColor.GRAY)));
            return Completable.complete();
        }

        return nicknameManager.clearNickname(player.getUniqueId())
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> player.sendMessage(PREFIX.append(
                        Component.text("Your nickname has been cleared.").color(NamedTextColor.GREEN))))
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to clear nickname for " + player.getName() + ": " + err.getMessage());
                    player.sendMessage(PREFIX.append(Component.text("Failed to clear nickname.")
                            .color(NamedTextColor.RED)));
                })
                .onErrorComplete();
    }

    /**
     * /nick <name> - Set own nickname
     */
    private Completable handleSetOwnNickname(Player player, String nickname) {
        return validator.validate(player.getUniqueId(), nickname)
                .observeOn(plugin.mainScheduler())
                .flatMapCompletable(result -> {
                    if (!result.valid()) {
                        player.sendMessage(PREFIX.append(Component.text(result.errorMessage())
                                .color(NamedTextColor.RED)));
                        return Completable.complete();
                    }

                    return nicknameManager.setNickname(player.getUniqueId(), nickname)
                            .observeOn(plugin.mainScheduler())
                            .doOnComplete(() -> player.sendMessage(PREFIX.append(
                                    Component.text("Your nickname is now ")
                                            .color(NamedTextColor.GREEN)
                                            .append(Component.text(nickname).color(NamedTextColor.WHITE))
                                            .append(Component.text(".").color(NamedTextColor.GREEN)))));
                })
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to set nickname for " + player.getName() + ": " + err.getMessage());
                    player.sendMessage(PREFIX.append(Component.text("Failed to set nickname.")
                            .color(NamedTextColor.RED)));
                })
                .onErrorComplete();
    }

    /**
     * /nick <username> <nickname> or /nick <username> clear
     */
    private Completable handleTwoArgs(CommandSender sender, String targetName, String nicknameOrClear) {
        if (!sender.hasPermission(PERMISSION_OTHERS)) {
            sender.sendMessage(PREFIX.append(Component.text("You don't have permission to change other players' nicknames.")
                    .color(NamedTextColor.RED)));
            return Completable.complete();
        }

        // Resolve target by username only (not nickname) to avoid ambiguity
        return sessionStorage.resolvePlayerId(targetName)
                .observeOn(plugin.mainScheduler())
                .switchIfEmpty(Maybe.defer(() -> {
                    sender.sendMessage(PREFIX.append(Component.text("Player '" + targetName + "' not found.")
                            .color(NamedTextColor.RED)));
                    return Maybe.empty();
                }))
                .flatMapCompletable(targetId -> {
                    if (nicknameOrClear.equalsIgnoreCase("clear") || nicknameOrClear.equalsIgnoreCase("reset")) {
                        return handleClearOtherNickname(sender, targetId, targetName);
                    } else {
                        return handleSetOtherNickname(sender, targetId, targetName, nicknameOrClear);
                    }
                })
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to modify nickname: " + err.getMessage());
                    sender.sendMessage(PREFIX.append(Component.text("Failed to modify nickname.")
                            .color(NamedTextColor.RED)));
                })
                .onErrorComplete();
    }

    /**
     * /nick <username> clear - Clear another player's nickname
     */
    private Completable handleClearOtherNickname(CommandSender sender, UUID targetId, String targetName) {
        if (!nicknameManager.hasNickname(targetId)) {
            sender.sendMessage(PREFIX.append(Component.text(targetName + " doesn't have a nickname set.")
                    .color(NamedTextColor.GRAY)));
            return Completable.complete();
        }

        return nicknameManager.clearNickname(targetId)
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> sender.sendMessage(PREFIX.append(
                        Component.text("Cleared " + targetName + "'s nickname.").color(NamedTextColor.GREEN))));
    }

    /**
     * /nick <username> <nickname> - Set another player's nickname
     */
    private Completable handleSetOtherNickname(CommandSender sender, UUID targetId, String targetName, String nickname) {
        return validator.validate(targetId, nickname)
                .observeOn(plugin.mainScheduler())
                .flatMapCompletable(result -> {
                    if (!result.valid()) {
                        sender.sendMessage(PREFIX.append(Component.text(result.errorMessage())
                                .color(NamedTextColor.RED)));
                        return Completable.complete();
                    }

                    return nicknameManager.setNickname(targetId, nickname)
                            .observeOn(plugin.mainScheduler())
                            .doOnComplete(() -> sender.sendMessage(PREFIX.append(
                                    Component.text("Set " + targetName + "'s nickname to ")
                                            .color(NamedTextColor.GREEN)
                                            .append(Component.text(nickname).color(NamedTextColor.WHITE))
                                            .append(Component.text(".").color(NamedTextColor.GREEN)))));
                });
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.fromCallable(() -> {
            if (args.length == 1) {
                // First arg: suggest "clear" or player names if admin
                String prefix = args[0].toLowerCase();
                List<Completion> completions = new ArrayList<>();

                // Suggest "clear" for self
                if (sender instanceof Player && "clear".startsWith(prefix)) {
                    completions.add(Completion.completion("clear"));
                }

                // Suggest player names if admin
                if (sender.hasPermission(PERMISSION_OTHERS)) {
                    plugin.getServer().getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(name -> name.toLowerCase().startsWith(prefix))
                            .map(Completion::completion)
                            .forEach(completions::add);
                }

                return completions.isEmpty() ? null : completions;
            } else if (args.length == 2 && sender.hasPermission(PERMISSION_OTHERS)) {
                // Second arg for admin: suggest "clear"
                String prefix = args[1].toLowerCase();
                if ("clear".startsWith(prefix)) {
                    return List.of(Completion.completion("clear"));
                }
            }

            return null;
        });
    }
}
