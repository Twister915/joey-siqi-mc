package sh.joey.mc.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.jetbrains.annotations.NotNull;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.ArrayList;
import java.util.List;

public final class CmdExecutor implements CommandExecutor {

    private final SiqiJoeyPlugin plugin;
    private final Command handler;
    private final List<String> commandNames;

    public static Disposable register(SiqiJoeyPlugin plugin, Command handler) {
        CmdExecutor executor = new CmdExecutor(plugin, handler);
        PluginCommand cmd = plugin.getCommand(handler.getName());
        if (cmd == null) {
            throw new IllegalStateException("Command '" + handler.getName() + "' not registered in plugin.yml");
        }
        cmd.setExecutor(executor);

        // Collect main command name and all aliases for tab completion
        executor.commandNames.add(handler.getName().toLowerCase());
        executor.commandNames.addAll(cmd.getAliases().stream()
                .map(String::toLowerCase)
                .toList());

        return executor.watchTabCompletes();
    }

    private CmdExecutor(SiqiJoeyPlugin plugin, Command handler) {
        this.plugin = plugin;
        this.handler = handler;
        this.commandNames = new ArrayList<>();
    }

    private boolean hasPermission(CommandSender sender) {
        String permission = handler.getPermission();
        return permission == null || sender.hasPermission(permission);
    }

    private Disposable watchTabCompletes() {
        return plugin.watchEvent(AsyncTabCompleteEvent.class)
                .filter(event -> {
                    if (event.isHandled()) return false;
                    String lowerBuffer = event.getBuffer().toLowerCase();
                    // Check if buffer matches any command name or alias
                    for (String cmdName : commandNames) {
                        String prefix1 = cmdName + " ";
                        String prefix2 = "/" + prefix1;
                        if (lowerBuffer.startsWith(prefix1) || lowerBuffer.startsWith(prefix2)) {
                            return true;
                        }
                    }
                    return false;
                })
                .filter(event -> hasPermission(event.getSender()))
                .subscribe(event -> {
                    try {
                        String buffer = event.getBuffer();
                        String lowerBuffer = buffer.toLowerCase();

                        // Find which command name matched
                        String matchedPrefix = null;
                        for (String cmdName : commandNames) {
                            String prefix = cmdName + " ";
                            String slashPrefix = "/" + prefix;
                            if (lowerBuffer.startsWith(slashPrefix)) {
                                matchedPrefix = slashPrefix;
                                break;
                            } else if (lowerBuffer.startsWith(prefix)) {
                                matchedPrefix = prefix;
                                break;
                            }
                        }

                        if (matchedPrefix == null) return;

                        String remainder = buffer.substring(matchedPrefix.length());
                        String[] args = remainder.isEmpty() ? new String[]{""} : remainder.split(" ", -1);

                        handler.tabComplete(plugin, event.getSender(), args)
                                .onErrorComplete()
                                .blockingSubscribe(completions -> {
                                    event.setHandled(true);
                                    event.completions(completions);
                                });
                    } catch (Exception e) {
                        plugin.getLogger().warning("Tab complete exception: " + e.getMessage());
                    }
                });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if (!hasPermission(sender)) {
            sender.sendMessage(Component.text("You don't have permission to use this command.")
                    .color(NamedTextColor.RED));
            return true;
        }

        try {
            handler.handle(plugin, sender, args)
                    .subscribe(
                            () -> {},
                            err -> plugin.getLogger().warning("Command error: " + err.getMessage())
                    );
        } catch (Exception e) {
            plugin.getLogger().warning("Command exception: " + e.getMessage());
        }
        return true;
    }
}
