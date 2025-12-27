package sh.joey.mc.sleep;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Implements majority sleep: when 50% or more of eligible players in an overworld
 * are sleeping for at least 3 seconds, skip the night.
 */
public final class MajoritySleepManager implements Disposable {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.GOLD)
            .append(Component.text("☾").color(NamedTextColor.YELLOW))
            .append(Component.text("] ").color(NamedTextColor.GOLD));

    private static final long REQUIRED_DURATION_SECONDS = 3;
    private static final long REQUIRED_DURATION_MS = REQUIRED_DURATION_SECONDS * 1000;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final Map<UUID, Set<UUID>> sleepingByWorld = new HashMap<>();
    private final Map<UUID, Disposable> pendingSkips = new HashMap<>();
    private final Map<UUID, Long> countdownStartTime = new HashMap<>();

    public MajoritySleepManager(SiqiJoeyPlugin plugin) {
        this.plugin = plugin;

        // Track players entering beds
        disposables.add(plugin.watchEvent(PlayerBedEnterEvent.class)
                .subscribe(this::handleBedEnter));

        // Track players leaving beds
        disposables.add(plugin.watchEvent(PlayerBedLeaveEvent.class)
                .subscribe(this::handleBedLeave));

        // Clean up when player quits
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> removeSleepingPlayer(event.getPlayer())));

        // Clean up when world unloads
        disposables.add(plugin.watchEvent(WorldUnloadEvent.class)
                .subscribe(event -> {
                    UUID worldId = event.getWorld().getUID();
                    sleepingByWorld.remove(worldId);
                    cancelPendingSkip(worldId);
                }));
    }

    @Override
    public void dispose() {
        disposables.dispose();
        pendingSkips.values().forEach(Disposable::dispose);
        pendingSkips.clear();
        countdownStartTime.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    /**
     * Get the sleep countdown state for a player's world.
     * Returns null if no countdown is active.
     */
    @Nullable
    public SleepCountdownState getCountdownState(Player player) {
        World world = player.getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return null;
        }

        UUID worldId = world.getUID();
        Long startTime = countdownStartTime.get(worldId);
        if (startTime == null) {
            return null;
        }

        Set<UUID> sleeping = sleepingByWorld.getOrDefault(worldId, Set.of());
        List<Player> eligible = world.getPlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .toList();

        long elapsed = System.currentTimeMillis() - startTime;
        float progress = Math.min(1.0f, elapsed / (float) REQUIRED_DURATION_MS);
        int remainingSeconds = (int) Math.ceil((REQUIRED_DURATION_MS - elapsed) / 1000.0);

        return new SleepCountdownState(sleeping.size(), eligible.size(), progress, remainingSeconds);
    }

    /**
     * State of an active sleep countdown.
     */
    public record SleepCountdownState(int sleepingCount, int totalCount, float progress, int remainingSeconds) {}

    private void handleBedEnter(PlayerBedEnterEvent event) {
        // Only count successful bed entries
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) {
            return;
        }

        Player player = event.getPlayer();
        World world = player.getWorld();

        // Only track overworld
        if (world.getEnvironment() != World.Environment.NORMAL) {
            return;
        }

        // Add player to sleeping set
        sleepingByWorld
                .computeIfAbsent(world.getUID(), k -> new HashSet<>())
                .add(player.getUniqueId());

        updateThresholdState(world);
    }

    private void handleBedLeave(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        Set<UUID> sleeping = sleepingByWorld.get(world.getUID());
        if (sleeping != null) {
            sleeping.remove(player.getUniqueId());
        }

        updateThresholdState(world);
    }

    private void removeSleepingPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        for (Map.Entry<UUID, Set<UUID>> entry : sleepingByWorld.entrySet()) {
            if (entry.getValue().remove(playerId)) {
                World world = plugin.getServer().getWorld(entry.getKey());
                if (world != null) {
                    updateThresholdState(world);
                }
            }
        }
    }

    private void updateThresholdState(World world) {
        UUID worldId = world.getUID();

        if (isThresholdMet(world)) {
            // Schedule skip if not already pending
            if (!pendingSkips.containsKey(worldId)) {
                countdownStartTime.put(worldId, System.currentTimeMillis());
                Disposable timer = plugin.timer(REQUIRED_DURATION_SECONDS, TimeUnit.SECONDS)
                        .subscribe(tick -> trySkipNight(world));
                pendingSkips.put(worldId, timer);
            }
        } else {
            // Threshold no longer met, cancel pending skip
            cancelPendingSkip(worldId);
        }
    }

    private void cancelPendingSkip(UUID worldId) {
        Disposable timer = pendingSkips.remove(worldId);
        if (timer != null) {
            timer.dispose();
        }
        countdownStartTime.remove(worldId);
    }

    private boolean isThresholdMet(World world) {
        Set<UUID> sleeping = sleepingByWorld.getOrDefault(world.getUID(), Set.of());

        // Get eligible players (exclude spectators)
        List<Player> eligible = world.getPlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .toList();

        if (eligible.isEmpty()) {
            return false;
        }

        int required = (int) Math.ceil(eligible.size() / 2.0);
        return sleeping.size() >= required;
    }

    private void trySkipNight(World world) {
        UUID worldId = world.getUID();
        pendingSkips.remove(worldId);
        countdownStartTime.remove(worldId);

        // Re-verify threshold is still met (in case of race conditions)
        if (!isThresholdMet(world)) {
            return;
        }

        Set<UUID> sleeping = sleepingByWorld.getOrDefault(worldId, Set.of());
        List<Player> eligible = world.getPlayers().stream()
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                .toList();

        skipNight(world, sleeping.size(), eligible.size());
    }

    private void skipNight(World world, int sleepingCount, int totalCount) {
        UUID worldId = world.getUID();

        // Clear state
        sleepingByWorld.remove(worldId);
        cancelPendingSkip(worldId);

        // Skip to morning
        world.setTime(0);

        // Clear weather
        world.setStorm(false);
        world.setThundering(false);

        // Broadcast message
        Component message = PREFIX.append(
                Component.text("Night skipped! (" + sleepingCount + "/" + totalCount + " sleeping)")
                        .color(NamedTextColor.GOLD)
        );
        for (Player player : world.getPlayers()) {
            player.sendMessage(message);
        }
    }
}
