package sh.joey.mc.home;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.session.PlayerSessionStorage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Async tab completion for /home command using Paper's AsyncTabCompleteEvent.
 * Fetches home names from the database without blocking the main thread.
 */
public final class HomeTabCompleter implements Disposable {

    private static final int MAX_COMPLETIONS = 20;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final HomeStorage storage;
    private final PlayerSessionStorage sessionStorage;

    public HomeTabCompleter(SiqiJoeyPlugin plugin, HomeStorage storage, PlayerSessionStorage sessionStorage) {
        this.plugin = plugin;
        this.storage = storage;
        this.sessionStorage = sessionStorage;

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
            try {
                // Use a Set to deduplicate online + database results
                Set<String> completions = new HashSet<>();

                // Add matching online player names
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.equals(player) && online.getName().toLowerCase().startsWith(partial.toLowerCase())) {
                        completions.add(online.getName());
                    }
                }

                // Add matching names from database (safe to block on async thread)
                List<String> dbNames = sessionStorage.findUsernamesByPrefix(partial, MAX_COMPLETIONS)
                        .toList()
                        .blockingGet();

                for (String name : dbNames) {
                    // Exclude the player themselves
                    if (!name.equalsIgnoreCase(player.getName())) {
                        completions.add(name);
                    }
                }

                // Sort and limit
                List<String> sorted = completions.stream()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .limit(MAX_COMPLETIONS)
                        .toList();

                event.setCompletions(sorted);
            } catch (Exception e) {
                plugin.getLogger().warning("Tab complete failed: " + e.getMessage());
            }
        }
    }

    private String formatHomeCompletion(Home home, UUID playerId) {
        if (home.isOwnedBy(playerId)) {
            return home.name();
        } else {
            return home.ownerDisplayName() + ":" + home.name();
        }
    }
}
