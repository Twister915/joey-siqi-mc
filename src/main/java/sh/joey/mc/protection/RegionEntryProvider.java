package sh.joey.mc.protection;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.bossbar.BossBarProvider;
import sh.joey.mc.bossbar.BossBarState;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Boss bar provider that shows region entry notifications.
 * Displays a message when entering a protected region.
 */
public final class RegionEntryProvider implements BossBarProvider, Disposable {

    private static final int PRIORITY = 120;
    private static final long DISPLAY_DURATION_MS = 3000;
    private static final long DEBOUNCE_MS = 500;

    private final RegionManager manager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // Per-player state
    private final Map<UUID, PlayerState> playerStates = new ConcurrentHashMap<>();

    private record PlayerState(
            @Nullable UUID currentRegionId,
            @Nullable UUID pendingRegionId,
            long pendingTimestamp,
            @Nullable EntryDisplay display
    ) {}

    private record EntryDisplay(
            String regionName,
            String ownerName,
            boolean canBuild,
            long showTimestamp
    ) {}

    public RegionEntryProvider(SiqiJoeyPlugin plugin, RegionManager manager) {
        this.manager = manager;

        // Track player movement
        disposables.add(plugin.watchEvent(PlayerMoveEvent.class)
                .filter(e -> e.hasChangedBlock())
                .subscribe(this::onMove));

        // Track teleports
        disposables.add(plugin.watchEvent(PlayerTeleportEvent.class)
                .subscribe(this::onTeleport));

        // Clean up on quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(e -> playerStates.remove(e.getPlayer().getUniqueId())));
    }

    private void onMove(PlayerMoveEvent event) {
        updatePlayerRegion(event.getPlayer());
    }

    private void onTeleport(PlayerTeleportEvent event) {
        // Clear pending state on teleport
        UUID playerId = event.getPlayer().getUniqueId();
        PlayerState state = playerStates.get(playerId);
        if (state != null) {
            playerStates.put(playerId, new PlayerState(
                    state.currentRegionId, null, 0, state.display
            ));
        }

        // Will be updated on next move
    }

    private void updatePlayerRegion(Player player) {
        UUID playerId = player.getUniqueId();
        Region region = manager.getRegionAt(player.getLocation());
        UUID newRegionId = region != null ? region.id() : null;

        PlayerState state = playerStates.getOrDefault(playerId,
                new PlayerState(null, null, 0, null));

        // If same as current confirmed region, nothing to do
        if (equals(state.currentRegionId, newRegionId)) {
            // Clear any pending change
            if (state.pendingRegionId != null) {
                playerStates.put(playerId, new PlayerState(
                        state.currentRegionId, null, 0, state.display
                ));
            }
            return;
        }

        // If same as pending region, check if debounce time has passed
        if (equals(state.pendingRegionId, newRegionId)) {
            long elapsed = System.currentTimeMillis() - state.pendingTimestamp;
            if (elapsed >= DEBOUNCE_MS) {
                // Confirm the region change
                confirmRegionChange(player, region, state);
            }
            return;
        }

        // New pending region
        playerStates.put(playerId, new PlayerState(
                state.currentRegionId,
                newRegionId,
                System.currentTimeMillis(),
                state.display
        ));
    }

    private void confirmRegionChange(Player player, @Nullable Region region, PlayerState state) {
        UUID playerId = player.getUniqueId();
        UUID newRegionId = region != null ? region.id() : null;

        EntryDisplay display = null;
        if (region != null) {
            // Entering a region - show notification
            boolean canBuild = region.canBuild(playerId);
            String ownerName = region.isOwner(playerId) ? null : region.ownerDisplayName();
            display = new EntryDisplay(
                    region.name(),
                    ownerName,
                    canBuild,
                    System.currentTimeMillis()
            );
        }

        playerStates.put(playerId, new PlayerState(
                newRegionId,
                null,
                0,
                display
        ));
    }

    private static boolean equals(@Nullable UUID a, @Nullable UUID b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }

    @Override
    public int getPriority() {
        return PRIORITY;
    }

    @Override
    public Optional<BossBarState> getState(Player player) {
        PlayerState state = playerStates.get(player.getUniqueId());
        if (state == null || state.display == null) {
            return Optional.empty();
        }

        // Check if display has expired
        long elapsed = System.currentTimeMillis() - state.display.showTimestamp;
        if (elapsed >= DISPLAY_DURATION_MS) {
            // Clear the display
            playerStates.put(player.getUniqueId(), new PlayerState(
                    state.currentRegionId, state.pendingRegionId, state.pendingTimestamp, null
            ));
            return Optional.empty();
        }

        // Build the boss bar state
        EntryDisplay display = state.display;
        StringBuilder title = new StringBuilder("\u26e8 \"").append(display.regionName).append("\"");
        if (display.ownerName != null) {
            title.append(" (").append(display.ownerName).append(")");
        }

        BarColor color = display.canBuild ? BarColor.GREEN : BarColor.YELLOW;

        // Calculate progress for fade out effect
        float progress = 1.0f - (float) elapsed / DISPLAY_DURATION_MS;

        return Optional.of(new BossBarState(
                title.toString(),
                color,
                progress,
                BarStyle.SOLID
        ));
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
