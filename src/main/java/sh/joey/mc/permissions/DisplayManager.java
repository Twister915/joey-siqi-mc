package sh.joey.mc.permissions;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.merit.MeritManager;

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

    /** Default name color when no color is set. */
    public static final TextColor DEFAULT_NAME_COLOR = NamedTextColor.GRAY;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final PermissionCache cache;
    private final PermissionResolver resolver;
    private final Scoreboard scoreboard;
    private final Map<UUID, String> playerTeams = new ConcurrentHashMap<>();

    // Cached default name color (resolved from default groups)
    private volatile TextColor defaultNameColor = DEFAULT_NAME_COLOR;

    // Optional merit manager for level display
    @Nullable
    private MeritManager meritManager;

    public DisplayManager(SiqiJoeyPlugin plugin, PermissionCache cache, PermissionResolver resolver) {
        this.plugin = plugin;
        this.cache = cache;
        this.resolver = resolver;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        // Load default attributes on startup
        loadDefaultAttributes();

        // Update display on join
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> updateDisplay(event.getPlayer())));

        // Clean up on quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> cleanupPlayer(event.getPlayer())));
    }

    /**
     * Load default attributes from the permission system.
     * Called on startup and when refreshing.
     */
    private void loadDefaultAttributes() {
        disposables.add(resolver.resolveDefaultAttributes()
                .subscribe(
                        attrs -> {
                            TextColor color = PermissibleAttributes.parseColor(attrs.nameColor());
                            if (color != null) {
                                defaultNameColor = color;
                            }
                        },
                        err -> plugin.getLogger().warning("Failed to load default attributes: " + err.getMessage())
                ));
    }

    /**
     * Get the default name color that applies to entities without explicit permissions.
     * <p>
     * This is the name color from the resolved default groups (is_default = true).
     * Use this for non-player entities like NPCs or bots that should match player styling.
     *
     * @return the default name color, or {@link #DEFAULT_NAME_COLOR} if not set
     */
    public TextColor getDefaultNameColor() {
        return defaultNameColor;
    }

    /**
     * Set the merit manager for level display.
     * This allows late initialization to avoid circular dependencies.
     */
    public void setMeritManager(@Nullable MeritManager meritManager) {
        this.meritManager = meritManager;
        // Refresh all displays to include level
        refreshAll();
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

        // Create unique team for this player (always fresh to reset all properties)
        String teamName = getTeamName(player.getUniqueId());
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }
        team = scoreboard.registerNewTeam(teamName);

        // Get level prefix from MeritManager if available
        Component levelPrefix = meritManager != null
                ? meritManager.getLevelPrefix(player)
                : Component.empty();

        // Apply prefix/suffix (using nameplate values for both tablist and nameplate)
        Component prefixComponent = attrs.nameplatePrefixComponent();
        Component suffixComponent = attrs.nameplateSuffixComponent();

        // Apply name color by appending to prefix (trailing color bleeds into player name)
        // Note: We don't use team.color() as it affects XP radar dots but not tab list names
        TextColor nameColor = PermissibleAttributes.parseColor(attrs.nameColor());
        if (nameColor == null) {
            nameColor = DEFAULT_NAME_COLOR;
        }

        // Combine level prefix + permission prefix + name color
        Component coloredPrefix = levelPrefix.append(prefixComponent).append(Component.empty().color(nameColor));

        team.prefix(coloredPrefix);
        team.suffix(suffixComponent);

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
     * Get the name color for a player.
     * Used by ChatMessageProvider.
     *
     * @return the player's name color, or GRAY as default
     */
    public TextColor getNameColor(Player player) {
        PermissibleAttributes attrs = cache.getCachedAttributes(player.getUniqueId());
        TextColor color = PermissibleAttributes.parseColor(attrs.nameColor());
        return color != null ? color : DEFAULT_NAME_COLOR;
    }

    /**
     * Refresh display for all online players and reload default attributes.
     */
    public void refreshAll() {
        loadDefaultAttributes();
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
