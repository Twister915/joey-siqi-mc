package sh.joey.mc.bossbar;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Biome;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Boss bar provider that shows biome name when player enters a new biome.
 * Display lasts for 5 seconds after entering the biome.
 * Includes debounce to prevent spam when walking along biome borders.
 */
public final class BiomeChangeProvider implements BossBarProvider, Listener {

    private static final int PRIORITY = 150;
    private static final long DISPLAY_DURATION_MS = 5000;

    private final long debounceMs;
    private final Map<UUID, BiomeState> playerStates = new HashMap<>();

    /**
     * Tracks both the confirmed biome (for display) and the pending biome (for debounce).
     * A biome change is only confirmed after staying in the new biome for the debounce period.
     */
    private record BiomeState(
            NamespacedKey confirmedBiomeKey,
            long confirmedAt,
            NamespacedKey pendingBiomeKey,
            long pendingStartedAt
    ) {
        BiomeState withPending(NamespacedKey newPending, long startedAt) {
            return new BiomeState(confirmedBiomeKey, confirmedAt, newPending, startedAt);
        }

        BiomeState withConfirmed(NamespacedKey newConfirmed, long at) {
            return new BiomeState(newConfirmed, at, newConfirmed, at);
        }
    }

    public BiomeChangeProvider(JavaPlugin plugin) {
        this(plugin, 30); // Default 30 ticks = 1.5 seconds
    }

    public BiomeChangeProvider(JavaPlugin plugin, int debounceTicks) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        this.debounceMs = debounceTicks * 50L; // 1 tick = 50ms
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    @SuppressWarnings("deprecation")
    public Optional<BossBarState> getState(Player player) {
        UUID playerId = player.getUniqueId();
        Location location = player.getLocation();
        Biome currentBiome = location.getWorld().getBiome(location);
        NamespacedKey currentKey = currentBiome.getKey();

        BiomeState state = playerStates.get(playerId);
        long now = System.currentTimeMillis();

        // Initialize state if first time
        if (state == null) {
            playerStates.put(playerId, new BiomeState(currentKey, 0, currentKey, now));
            return Optional.empty(); // Don't show notification on first detection
        }

        // Check if current biome differs from pending - reset pending timer
        if (!currentKey.equals(state.pendingBiomeKey())) {
            state = state.withPending(currentKey, now);
            playerStates.put(playerId, state);
        }

        // Check if pending biome differs from confirmed and debounce period passed
        if (!currentKey.equals(state.confirmedBiomeKey())) {
            long pendingDuration = now - state.pendingStartedAt();
            if (pendingDuration >= debounceMs) {
                // Debounce passed - confirm the new biome
                state = state.withConfirmed(currentKey, now);
                playerStates.put(playerId, state);
            }
        }

        // Check if still within display duration of confirmed biome
        long elapsed = now - state.confirmedAt();
        if (elapsed > DISPLAY_DURATION_MS || state.confirmedAt() == 0) {
            return Optional.empty();
        }

        // Calculate progress (drains over time)
        float progress = 1.0f - (float) elapsed / DISPLAY_DURATION_MS;

        String friendlyName = formatBiomeName(state.confirmedBiomeKey());
        BarColor color = getBiomeColor(state.confirmedBiomeKey());

        String title = ChatColor.translateAlternateColorCodes('&',
                "&7Entering &f&l" + friendlyName);

        return Optional.of(new BossBarState(title, color, progress, BarStyle.SOLID));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playerStates.remove(event.getPlayer().getUniqueId());
    }

    private String formatBiomeName(NamespacedKey key) {
        // Convert "dark_forest" to "Dark Forest"
        String name = key.getKey();
        StringBuilder result = new StringBuilder();
        boolean capitalizeNext = true;

        for (char c : name.toCharArray()) {
            if (c == '_') {
                result.append(' ');
                capitalizeNext = true;
            } else if (capitalizeNext) {
                result.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                result.append(Character.toLowerCase(c));
            }
        }

        return result.toString();
    }

    private BarColor getBiomeColor(NamespacedKey key) {
        String name = key.getKey();

        if (name.contains("ocean") || name.contains("river") || name.contains("beach")) {
            return BarColor.BLUE;
        }
        if (name.contains("desert") || name.contains("badlands") || name.contains("savanna")) {
            return BarColor.YELLOW;
        }
        if (name.contains("snow") || name.contains("ice") || name.contains("frozen")) {
            return BarColor.WHITE;
        }
        if (name.contains("swamp") || name.contains("mangrove")) {
            return BarColor.PURPLE;
        }
        if (name.contains("jungle")) {
            return BarColor.GREEN;
        }
        if (name.contains("mushroom")) {
            return BarColor.PINK;
        }
        if (name.contains("nether") || name.contains("basalt") || name.contains("crimson") || name.contains("warped") || name.contains("soul")) {
            return BarColor.RED;
        }
        if (name.contains("end")) {
            return BarColor.PURPLE;
        }
        if (name.contains("forest") || name.contains("taiga") || name.contains("grove")) {
            return BarColor.GREEN;
        }
        if (name.contains("mountain") || name.contains("peak") || name.contains("slope") || name.contains("stone")) {
            return BarColor.WHITE;
        }
        if (name.contains("cave") || name.contains("deep_dark") || name.contains("dripstone") || name.contains("lush")) {
            return BarColor.PURPLE;
        }

        return BarColor.GREEN; // Default for plains, meadows, etc.
    }
}
