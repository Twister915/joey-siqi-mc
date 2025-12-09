package sh.joey.mc.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.List;

public final class CmdExecutor implements CommandExecutor {

    private final SiqiJoeyPlugin plugin;
    private final Command handler;

    public static Disposable register(SiqiJoeyPlugin plugin, Command handler) {
        CmdExecutor executor = new CmdExecutor(plugin, handler);
        var cmd = plugin.getCommand(handler.getName());
        if (cmd == null) {
            throw new IllegalStateException("Command '" + handler.getName() + "' not registered in plugin.yml");
        }
        cmd.setExecutor(executor);
        return executor.watchTabCompletes();
    }

    private CmdExecutor(SiqiJoeyPlugin plugin, Command handler) {
        this.plugin = plugin;
        this.handler = handler;
    }

    private Disposable watchTabCompletes() {
        String prefix1 = handler.getName().toLowerCase() + " ";
        String prefix2 = "/" + prefix1;

        return plugin.watchEvent(AsyncTabCompleteEvent.class)
                .filter(event -> {
                    if (event.isHandled()) return false;
                    String lowerBuffer = event.getBuffer().toLowerCase();
                    return lowerBuffer.startsWith(prefix1) || lowerBuffer.startsWith(prefix2);
                })
                .subscribe(event -> {
                    try {
                        String buffer = event.getBuffer();
                        int startAt = 0;
                        while (buffer.length() > startAt && buffer.charAt(startAt) == '/') {
                            startAt++;
                        }

                        startAt += prefix1.length();
                        String remainder = startAt < buffer.length() ? buffer.substring(startAt) : "";
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
