package sh.joey.mc.world;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.World;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Monitors world time to detect if time is passing while the server is empty.
 * Logs warnings if time advances with no players online (up to 3 warnings per world).
 * Logs confirmation when time is confirmed to be paused.
 * This helps verify that pause-when-empty-seconds is working correctly.
 *
 * Includes a 90-second grace period after server becomes empty to account for
 * the pause-when-empty-seconds setting (typically 60 seconds).
 */
public final class TimePassingMonitor implements Disposable {

    private static final int MAX_WARNINGS = 3;
    private static final long GRACE_PERIOD_MS = 90_000L; // 90 seconds grace period

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final Map<UUID, WorldState> worldStates = new HashMap<>();
    private long emptyServerSince = -1;

    private static class WorldState {
        long lastFullTime = -1;
        int warningCount = 0;
        boolean confirmedPaused = false;
    }

    public TimePassingMonitor(SiqiJoeyPlugin plugin) {
        this.plugin = plugin;

        // Periodic time checking (every 5 seconds)
        disposables.add(plugin.interval(5, TimeUnit.SECONDS)
                .subscribe(tick -> checkTime()));

        // Player join resets tracking
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> {
                    boolean hadWarnings = worldStates.values().stream().anyMatch(s -> s.warningCount > 0);
                    if (hadWarnings) {
                        plugin.getLogger().info("[TimeMonitor] Player joined - resetting time monitor.");
                    }
                    worldStates.clear();
                    emptyServerSince = -1;
                }));

        // World unload cleanup
        disposables.add(plugin.watchEvent(WorldUnloadEvent.class)
                .subscribe(event -> worldStates.remove(event.getWorld().getUID())));
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    private void checkTime() {
        boolean isEmpty = plugin.getServer().getOnlinePlayers().isEmpty();

        // Track when server became empty
        if (!isEmpty) {
            emptyServerSince = -1;
            return;
        }

        // Server is empty - record when it became empty
        if (emptyServerSince == -1) {
            emptyServerSince = System.currentTimeMillis();
            worldStates.clear(); // Reset all world tracking
            return;
        }

        // Wait for grace period before checking (server should have paused by now)
        if (System.currentTimeMillis() - emptyServerSince < GRACE_PERIOD_MS) {
            return;
        }

        // Check each world
        for (World world : plugin.getServer().getWorlds()) {
            checkWorld(world);
        }
    }

    private void checkWorld(World world) {
        UUID worldId = world.getUID();
        WorldState state = worldStates.computeIfAbsent(worldId, k -> new WorldState());

        long currentFullTime = world.getFullTime();

        // First check - just record the time
        if (state.lastFullTime == -1) {
            state.lastFullTime = currentFullTime;
            return;
        }

        // Check if time has advanced
        if (currentFullTime > state.lastFullTime) {
            long ticksPassed = currentFullTime - state.lastFullTime;
            long daysPassed = ticksPassed / 24000;

            // Reset confirmed status since time is moving
            state.confirmedPaused = false;

            if (state.warningCount < MAX_WARNINGS) {
                state.warningCount++;

                String worldName = world.getName();
                long currentDay = currentFullTime / 24000;

                if (daysPassed > 0) {
                    plugin.getLogger().warning(
                        "[TimeMonitor] [" + worldName + "] Time is passing while server is empty! " +
                        ticksPassed + " ticks (" + daysPassed + " days) passed. " +
                        "Current day: " + currentDay + ". " +
                        "Warning " + state.warningCount + "/" + MAX_WARNINGS
                    );
                } else {
                    plugin.getLogger().warning(
                        "[TimeMonitor] [" + worldName + "] Time is passing while server is empty! " +
                        ticksPassed + " ticks passed. " +
                        "Current day: " + currentDay + ". " +
                        "Warning " + state.warningCount + "/" + MAX_WARNINGS
                    );
                }

                if (state.warningCount == MAX_WARNINGS) {
                    plugin.getLogger().warning(
                        "[TimeMonitor] [" + worldName + "] Max warnings reached. " +
                        "Check your pause-when-empty-seconds setting in server.properties."
                    );
                }
            }
        } else if (!state.confirmedPaused) {
            // Time has NOT advanced - confirm paused (only log once per world)
            state.confirmedPaused = true;
            long currentDay = currentFullTime / 24000;
            plugin.getLogger().info(
                "[TimeMonitor] [" + world.getName() + "] Confirmed, world is paused at day " + currentDay + "."
            );
        }

        state.lastFullTime = currentFullTime;
    }
}
