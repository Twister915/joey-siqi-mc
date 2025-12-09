package sh.joey.mc.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.List;
import java.util.Optional;

public final class CmdExecutor implements CommandExecutor {

    private final SiqiJoeyPlugin plugin;
    private final Command handler;

    public static Disposable register(SiqiJoeyPlugin plugin, Command handler) {
        CmdExecutor executor = new CmdExecutor(plugin, handler);
        plugin.getCommand(handler.getName()).setExecutor(executor);
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
                    String buffer = event.getBuffer();
                    int startAt = 0;
                    while (buffer.length() > startAt && buffer.charAt(startAt) == '/') {
                        startAt++;
                    }

                    startAt += prefix1.length();
                    if (startAt < buffer.length()) {
                        String[] args = buffer.substring(startAt).split(" ");
                        Optional<List<AsyncTabCompleteEvent.Completion>> completions = handler.tabComplete(event.getSender(), args)
                                .map(Optional::of)
                                .defaultIfEmpty(Optional.empty())
                                .blockingGet();
                        if (completions.isPresent()) {
                            event.setHandled(true);
                            event.completions(completions.get());
                        }
                    }
                });
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, org.bukkit.command.@NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        handler.handle(plugin, sender, args).subscribe();
        return true;
    }
}
