package sh.joey.mc.pregen;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Manages chunk pre-generation when no players are online.
 * Generates chunks in a spiral pattern from world spawn outward.
 */
public final class PregenManager implements Disposable {

    public enum State {
        IDLE,       // Not running, no work pending
        RUNNING,    // Actively generating chunks
        PAUSED,     // Manually paused by admin
        WAITING     // Waiting for players to leave
    }

    /**
     * Progress tracking for a single world.
     */
    public record WorldProgress(
            String worldName,
            long generatedChunks,
            long skippedChunks,
            long totalChunks,
            long startTimeMillis,
            boolean complete
    ) {
        public double getProgressPercent() {
            return totalChunks > 0 ? getProcessedChunks() * 100.0 / totalChunks : 100.0;
        }

        public long getProcessedChunks() {
            return generatedChunks + skippedChunks;
        }

        public String getEtaFormatted() {
            if (generatedChunks == 0) return "calculating...";
            long elapsed = System.currentTimeMillis() - startTimeMillis;
            double chunksPerMs = generatedChunks / (double) elapsed;
            if (chunksPerMs <= 0) return "calculating...";
            long remaining = totalChunks - getProcessedChunks();
            long etaMs = (long) (remaining / chunksPerMs);
            return Messages.formatDuration(etaMs);
        }
    }

    private final SiqiJoeyPlugin plugin;
    private final PregenConfig config;
    private final Logger logger;
    private final CompositeDisposable disposables = new CompositeDisposable();

    private State state = State.IDLE;
    private final Map<String, WorldProgress> worldProgress = new LinkedHashMap<>();
    private final Map<String, SpiralIterator> worldIterators = new HashMap<>();
    private String currentWorld = null;
    private int currentWorldIndex = 0;

    // In-flight async chunk requests
    private final AtomicInteger inFlightRequests = new AtomicInteger(0);
    private Disposable tickSubscription = null;
    private Disposable progressLogSubscription = null;

    // Forced mode: runs at SLOW speed even with players online (for testing)
    private boolean forcedMode = false;

    public PregenManager(SiqiJoeyPlugin plugin, PregenConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.logger = plugin.getLogger();

        if (!config.enabled()) {
            logger.info("[Pregen] Disabled in config");
            return;
        }

        if (config.worlds().isEmpty()) {
            logger.info("[Pregen] No worlds configured");
            return;
        }

        // Watch for player joins - pause generation (unless in forced mode)
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> {
                    if (state == State.RUNNING && !forcedMode) {
                        logger.info("[Pregen] Player joined, pausing generation");
                        state = State.WAITING;
                        stopTicking();
                    }
                }));

        // Watch for player quits - resume if server empty
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> {
                    // Check after a short delay (player hasn't fully left yet)
                    disposables.add(plugin.timer(1, TimeUnit.SECONDS)
                            .subscribe(tick -> {
                                if (state == State.WAITING && Bukkit.getOnlinePlayers().isEmpty()) {
                                    logger.info("[Pregen] Server empty, resuming generation");
                                    state = State.RUNNING;
                                    startTicking();
                                }
                            }));
                }));

        // Auto-start if server is empty
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            initializeWorlds();
            if (!worldIterators.isEmpty()) {
                state = State.RUNNING;
                startTicking();
                logger.info("[Pregen] Started automatically (server empty)");
            }
        } else {
            state = State.WAITING;
            logger.info("[Pregen] Waiting for players to leave before starting");
        }
    }

    private void initializeWorlds() {
        worldIterators.clear();
        worldProgress.clear();
        currentWorldIndex = 0;

        for (String worldName : config.worlds()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                logger.warning("[Pregen] World not found: " + worldName);
                continue;
            }

            int spawnChunkX = world.getSpawnLocation().getBlockX() >> 4;
            int spawnChunkZ = world.getSpawnLocation().getBlockZ() >> 4;

            SpiralIterator iterator = new SpiralIterator(
                    spawnChunkX, spawnChunkZ, config.sideChunks()
            );
            worldIterators.put(worldName, iterator);
            worldProgress.put(worldName, new WorldProgress(
                    worldName, 0, 0, config.totalChunks(), System.currentTimeMillis(), false
            ));
        }

        if (!worldIterators.isEmpty()) {
            // Find first non-empty world
            for (String worldName : config.worlds()) {
                if (worldIterators.containsKey(worldName)) {
                    currentWorld = worldName;
                    break;
                }
            }
        }
    }

    private void startTicking() {
        if (tickSubscription != null) return;

        // Tick every game tick (50ms = 1 tick)
        tickSubscription = plugin.interval(50, TimeUnit.MILLISECONDS)
                .subscribe(tick -> processTick());

        // Progress logging
        progressLogSubscription = plugin.interval(
                        config.progressLogIntervalSeconds(), TimeUnit.SECONDS)
                .subscribe(tick -> logProgress());
    }

    private void stopTicking() {
        if (tickSubscription != null) {
            tickSubscription.dispose();
            tickSubscription = null;
        }
        if (progressLogSubscription != null) {
            progressLogSubscription.dispose();
            progressLogSubscription = null;
        }
    }

    private void processTick() {
        if (state != State.RUNNING || currentWorld == null) return;

        World world = Bukkit.getWorld(currentWorld);
        if (world == null) {
            advanceToNextWorld();
            return;
        }

        SpiralIterator iterator = worldIterators.get(currentWorld);
        if (iterator == null || !iterator.hasNext()) {
            markWorldComplete(currentWorld);
            advanceToNextWorld();
            return;
        }

        // Rate limiting: don't exceed max concurrent requests
        // Use SLOW rate when in forced mode (players online) to minimize impact
        PregenRate effectiveRate = forcedMode ? PregenRate.SLOW : config.rate();
        int maxConcurrent = effectiveRate.getMaxChunksPerTick();
        int canRequest = maxConcurrent - inFlightRequests.get();

        for (int i = 0; i < canRequest && iterator.hasNext(); i++) {
            int[] coords = iterator.next();
            int chunkX = coords[0];
            int chunkZ = coords[1];

            // Check if already generated (fast, synchronous)
            if (world.isChunkGenerated(chunkX, chunkZ)) {
                updateProgress(currentWorld, false, true);  // skipped
                continue;
            }

            // Request async chunk generation
            inFlightRequests.incrementAndGet();
            String worldName = currentWorld;  // Capture for lambda

            world.getChunkAtAsync(chunkX, chunkZ, true)
                    .thenAccept(chunk -> {
                        inFlightRequests.decrementAndGet();
                        // Update progress on main thread
                        Bukkit.getScheduler().runTask(plugin, () ->
                                updateProgress(worldName, true, false));
                    })
                    .exceptionally(ex -> {
                        inFlightRequests.decrementAndGet();
                        logger.warning("[Pregen] Chunk generation failed at " +
                                chunkX + "," + chunkZ + ": " + ex.getMessage());
                        return null;
                    });
        }
    }

    private void updateProgress(String worldName, boolean generated, boolean skipped) {
        WorldProgress current = worldProgress.get(worldName);
        if (current == null) return;

        worldProgress.put(worldName, new WorldProgress(
                worldName,
                current.generatedChunks() + (generated ? 1 : 0),
                current.skippedChunks() + (skipped ? 1 : 0),
                current.totalChunks(),
                current.startTimeMillis(),
                current.complete()
        ));
    }

    private void markWorldComplete(String worldName) {
        WorldProgress current = worldProgress.get(worldName);
        if (current != null) {
            worldProgress.put(worldName, new WorldProgress(
                    worldName, current.generatedChunks(), current.skippedChunks(),
                    current.totalChunks(), current.startTimeMillis(), true
            ));
        }
        logger.info("[Pregen] Completed generation for world: " + worldName);
    }

    private void advanceToNextWorld() {
        List<String> worlds = config.worlds();

        // Find next valid world
        currentWorldIndex++;
        while (currentWorldIndex < worlds.size()) {
            String worldName = worlds.get(currentWorldIndex);
            if (worldIterators.containsKey(worldName)) {
                SpiralIterator iterator = worldIterators.get(worldName);
                if (iterator.hasNext()) {
                    currentWorld = worldName;
                    // Reset start time for accurate ETA
                    WorldProgress current = worldProgress.get(worldName);
                    if (current != null) {
                        worldProgress.put(worldName, new WorldProgress(
                                worldName, current.generatedChunks(), current.skippedChunks(),
                                current.totalChunks(), System.currentTimeMillis(), false
                        ));
                    }
                    logger.info("[Pregen] Moving to world: " + currentWorld);
                    return;
                }
            }
            currentWorldIndex++;
        }

        // All done
        currentWorld = null;
        state = State.IDLE;
        stopTicking();
        logger.info("[Pregen] All worlds complete!");
    }

    private void logProgress() {
        if (currentWorld == null) return;
        WorldProgress progress = worldProgress.get(currentWorld);
        if (progress == null) return;

        logger.info(String.format("[Pregen] %s: %.1f%% (%,d/%,d chunks) - Gen: %,d, Skip: %,d - ETA: %s",
                currentWorld,
                progress.getProgressPercent(),
                progress.getProcessedChunks(),
                progress.totalChunks(),
                progress.generatedChunks(),
                progress.skippedChunks(),
                progress.getEtaFormatted()
        ));
    }

    // === Admin control methods ===

    public void start() {
        if (state == State.PAUSED || state == State.IDLE) {
            if (worldIterators.isEmpty()) {
                initializeWorlds();
            }
            if (worldIterators.isEmpty()) {
                logger.warning("[Pregen] Cannot start: no valid worlds configured");
                return;
            }
            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                state = State.WAITING;
                logger.info("[Pregen] Will start when server is empty");
            } else {
                state = State.RUNNING;
                startTicking();
                logger.info("[Pregen] Started");
            }
        }
    }

    public void stop() {
        state = State.IDLE;
        stopTicking();
        worldIterators.clear();
        worldProgress.clear();
        currentWorld = null;
        currentWorldIndex = 0;
        forcedMode = false;  // Exit forced mode on stop
        logger.info("[Pregen] Stopped and reset");
    }

    public void pause() {
        if (state == State.RUNNING) {
            state = State.PAUSED;
            stopTicking();
            forcedMode = false;  // Exit forced mode on pause
            logger.info("[Pregen] Paused");
        }
    }

    /**
     * Toggle forced mode - runs at SLOW speed even with players online.
     * Useful for testing the system while playing.
     */
    public void toggleForce() {
        if (forcedMode) {
            // Disable forced mode
            forcedMode = false;
            if (state == State.RUNNING && !Bukkit.getOnlinePlayers().isEmpty()) {
                state = State.WAITING;
                stopTicking();
                logger.info("[Pregen] Forced mode disabled, waiting for players to leave");
            } else {
                logger.info("[Pregen] Forced mode disabled");
            }
        } else {
            // Enable forced mode
            forcedMode = true;
            if (worldIterators.isEmpty()) {
                initializeWorlds();
            }
            if (worldIterators.isEmpty()) {
                forcedMode = false;
                logger.warning("[Pregen] Cannot force: no valid worlds configured");
                return;
            }
            if (state != State.RUNNING) {
                state = State.RUNNING;
                startTicking();
            }
            logger.info("[Pregen] Forced mode enabled (running at SLOW speed)");
        }
    }

    public boolean isForced() {
        return forcedMode;
    }

    public State getState() {
        return state;
    }

    public String getCurrentWorld() {
        return currentWorld;
    }

    public Map<String, WorldProgress> getWorldProgress() {
        return Collections.unmodifiableMap(worldProgress);
    }

    public PregenConfig getConfig() {
        return config;
    }

    @Override
    public void dispose() {
        stopTicking();
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
