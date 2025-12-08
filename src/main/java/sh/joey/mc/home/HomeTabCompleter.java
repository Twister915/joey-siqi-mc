package sh.joey.mc.home;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Async tab completion for /home command using Paper's AsyncTabCompleteEvent.
 * Fetches home names from the database without blocking the main thread.
 */
public final class HomeTabCompleter implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final HomeStorage storage;

    public HomeTabCompleter(SiqiJoeyPlugin plugin, HomeStorage storage) {
        this.plugin = plugin;
        this.storage = storage;

        disposables.add(plugin.watchEvent(AsyncTabCompleteEvent.class)
                .filter(event -> event.getSender() instanceof Player)
                .filter(event -> isHomeCommand(event.getBuffer()))
                .subscribe(this::handleTabComplete));
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    private boolean isHomeCommand(String buffer) {
        return buffer.startsWith("/home ") || buffer.startsWith("/home\t");
    }

    private void handleTabComplete(AsyncTabCompleteEvent event) {
        event.setHandled(true);
        Player player = (Player) event.getSender();
        String buffer = event.getBuffer();

        // Parse the command: "/home arg1 arg2..."
        String[] parts = buffer.substring(1).split(" ", -1); // Remove leading /
        if (parts.length < 2) {
            return;
        }

        String partial = parts[parts.length - 1].toLowerCase();
        int argIndex = parts.length - 1; // 1 = first arg after "home"

        if (argIndex == 1) {
            // First argument: subcommands OR home names
            completeFirstArg(event, player.getUniqueId(), partial);
        } else if (argIndex == 2) {
            String subcommand = parts[1].toLowerCase();
            completeSecondArg(event, player, subcommand, partial);
        } else if (argIndex == 3) {
            String subcommand = parts[1].toLowerCase();
            completeThirdArg(event, player, subcommand, partial);
        }
    }

    private void completeFirstArg(AsyncTabCompleteEvent event, UUID playerId, String partial) {
        // Fetch accessible homes and combine with subcommands
        // Safe to block here - AsyncTabCompleteEvent runs on async thread
        try {
            List<Home> homes = storage.getHomes(playerId).toList().blockingGet();
            List<String> completions = new ArrayList<>();

            // Add matching subcommands
            for (String cmd : HomeCommand.RESERVED_NAMES) {
                if (cmd.startsWith(partial)) {
                    completions.add(cmd);
                }
            }

            // Add matching home names
            for (Home home : homes) {
                String completion = formatHomeCompletion(home, playerId);
                if (completion.toLowerCase().startsWith(partial)) {
                    completions.add(completion);
                }
            }

            event.setCompletions(completions);
        } catch (Exception e) {
            plugin.getLogger().warning("Tab complete failed: " + e.getMessage());
        }
    }

    private void completeSecondArg(AsyncTabCompleteEvent event, Player player, String subcommand, String partial) {
        if (subcommand.equals("delete") || subcommand.equals("share") || subcommand.equals("unshare")) {
            // These operate on owned homes only
            // Safe to block here - AsyncTabCompleteEvent runs on async thread
            UUID playerId = player.getUniqueId();
            try {
                List<String> completions = storage.getHomes(playerId)
                        .filter(home -> home.isOwnedBy(playerId))
                        .map(Home::name)
                        .filter(name -> name.startsWith(partial))
                        .toList()
                        .blockingGet();
                event.setCompletions(completions);
            } catch (Exception e) {
                plugin.getLogger().warning("Tab complete failed: " + e.getMessage());
            }
        }
        // "set" and "list" don't need completion for second arg
    }

    private void completeThirdArg(AsyncTabCompleteEvent event, Player player, String subcommand, String partial) {
        if (subcommand.equals("share") || subcommand.equals("unshare")) {
            // Complete with online player names
            List<String> completions = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (!online.equals(player) && online.getName().toLowerCase().startsWith(partial)) {
                    completions.add(online.getName());
                }
            }
            event.setCompletions(completions);
        }
    }

    private String formatHomeCompletion(Home home, UUID playerId) {
        if (home.isOwnedBy(playerId)) {
            return home.name();
        } else {
            String ownerName = getPlayerName(home.ownerId());
            return ownerName + ":" + home.name();
        }
    }

    private String getPlayerName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        var offline = Bukkit.getOfflinePlayer(playerId);
        String name = offline.getName();
        return name != null ? name : playerId.toString().substring(0, 8);
    }
}
