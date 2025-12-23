package sh.joey.mc.permissions;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages display attributes (prefixes/suffixes) for players.
 * <p>
 * Uses Scoreboard Teams for tablist and nameplate display.
 * Note: Bukkit's Scoreboard Teams share prefix/suffix between tablist and nameplate.
 * <p>
 * For chat prefixes/suffixes, use {@link #getChatPrefix(Player)} and {@link #getChatSuffix(Player)}
 * to integrate with chat formatting.
 */
public final class DisplayManager implements Disposable {

    private static final String TEAM_PREFIX = "perm_";
    private static final int MAX_TEAM_NAME_LENGTH = 16;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final PermissionCache cache;
    private final Scoreboard scoreboard;
    private final Map<UUID, String> playerTeams = new ConcurrentHashMap<>();

    public DisplayManager(SiqiJoeyPlugin plugin, PermissionCache cache) {
        this.plugin = plugin;
        this.cache = cache;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Update display on join
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> updateDisplay(event.getPlayer())));

        // Clean up on quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> cleanupPlayer(event.getPlayer())));
    }

    /**
     * Update the display (scoreboard team) for a player.
     */
    public void updateDisplay(Player player) {
        UUID playerId = player.getUniqueId();
        UUID worldId = player.getWorld().getUID();

        cache.get(playerId, worldId)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        resolved -> applyDisplay(player, resolved.attributes()),
                        err -> plugin.getLogger().warning(
                                "Failed to update display for " + player.getName() + ": " + err.getMessage())
                );
    }

    private void applyDisplay(Player player, PermissibleAttributes attrs) {
        // Clean up old team
        cleanupPlayer(player);

        // Create unique team for this player
        String teamName = getTeamName(player.getUniqueId());
        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        // Apply prefix/suffix (using nameplate values for both tablist and nameplate)
        Component prefixComponent = attrs.nameplatePrefixComponent();
        Component suffixComponent = attrs.nameplateSuffixComponent();

        if (!prefixComponent.equals(Component.empty())) {
            team.prefix(prefixComponent);
        }

        if (!suffixComponent.equals(Component.empty())) {
            team.suffix(suffixComponent);
        }

        // Add player to team
        team.addEntry(player.getName());
        playerTeams.put(player.getUniqueId(), teamName);

        // Ensure player uses this scoreboard
        player.setScoreboard(scoreboard);
    }

    private String getTeamName(UUID playerId) {
        // Create a unique team name that fits within Bukkit's 16-character limit
        String uuidPart = playerId.toString().replace("-", "").substring(0, MAX_TEAM_NAME_LENGTH - TEAM_PREFIX.length());
        return TEAM_PREFIX + uuidPart;
    }

    private void cleanupPlayer(Player player) {
        String teamName = playerTeams.remove(player.getUniqueId());
        if (teamName != null) {
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.removeEntry(player.getName());
                // Only unregister if empty
                if (team.getEntries().isEmpty()) {
                    team.unregister();
                }
            }
        }
    }

    /**
     * Get the chat prefix component for a player.
     * Used by ChatMessageProvider.
     */
    public Component getChatPrefix(Player player) {
        PermissibleAttributes attrs = cache.getCachedAttributes(player.getUniqueId());
        return attrs.chatPrefixComponent();
    }

    /**
     * Get the chat suffix component for a player.
     * Used by ChatMessageProvider.
     */
    public Component getChatSuffix(Player player) {
        PermissibleAttributes attrs = cache.getCachedAttributes(player.getUniqueId());
        return attrs.chatSuffixComponent();
    }

    /**
     * Refresh display for all online players.
     */
    public void refreshAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            updateDisplay(player);
        }
    }

    @Override
    public void dispose() {
        disposables.dispose();

        // Clean up all teams
        for (String teamName : playerTeams.values()) {
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                team.unregister();
            }
        }
        playerTeams.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
