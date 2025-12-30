package sh.joey.mc.pregen;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    // Progress file name stored in world folder
    private static final String PROGRESS_FILE_NAME = "pregen-progress.txt";

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

        // Watch for player quits - resume if server empty, or exit forced mode
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> {
                    // Check after a short delay (player hasn't fully left yet)
                    disposables.add(plugin.timer(1, TimeUnit.SECONDS)
                            .subscribe(tick -> {
                                if (Bukkit.getOnlinePlayers().isEmpty()) {
                                    if (state == State.WAITING) {
                                        logger.info("[Pregen] Server empty, resuming generation");
                                        state = State.RUNNING;
                                        startTicking();
                                    } else if (state == State.RUNNING && forcedMode) {
                                        // Server empty while in forced mode - switch back to normal speed
                                        forcedMode = false;
                                        logger.info("[Pregen] Server empty, switching from SLOW to " + config.rate() + " speed");
                                    }
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

            // Load saved progress and skip to resume position
            long savedProgress = loadProgress(world);
            if (savedProgress > 0) {
                iterator.skip(savedProgress);
                logger.info("[Pregen] Resuming " + worldName + " from chunk " + savedProgress);
            }

            worldIterators.put(worldName, iterator);
            worldProgress.put(worldName, new WorldProgress(
                    worldName, 0, savedProgress, config.totalChunks(), System.currentTimeMillis(), false
            ));
        }

        if (!worldIterators.isEmpty()) {
            // Find first non-complete world
            for (String worldName : config.worlds()) {
                if (worldIterators.containsKey(worldName)) {
                    SpiralIterator iterator = worldIterators.get(worldName);
                    if (iterator.hasNext()) {
                        currentWorld = worldName;
                        break;
                    }
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

        // Rate limiting: only limit actual chunk generation, not existence checks
        // Use SLOW rate when in forced mode (players online) to minimize impact
        PregenRate effectiveRate = forcedMode ? PregenRate.SLOW : config.rate();
        int maxConcurrent = effectiveRate.getMaxChunksPerTick();
        int canRequest = maxConcurrent - inFlightRequests.get();

        // Check many chunks quickly, but only generate up to canRequest new ones
        // Limit checks per tick to avoid blocking the main thread too long
        int maxChecksPerTick = 1000;
        int checksThisTick = 0;
        int generatedThisTick = 0;

        while (iterator.hasNext() && generatedThisTick < canRequest && checksThisTick < maxChecksPerTick) {
            int[] coords = iterator.next();
            int chunkX = coords[0];
            int chunkZ = coords[1];
            checksThisTick++;

            // Check if already generated (fast, synchronous)
            if (world.isChunkGenerated(chunkX, chunkZ)) {
                updateProgress(currentWorld, false, true);  // skipped
                continue;
            }

            // Request async chunk generation
            generatedThisTick++;
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
        // Clear progress file since world is complete
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            clearProgress(world);
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

        // Save progress to file
        saveProgress(currentWorld);
    }

    // === Progress persistence ===

    private Path getProgressFile(World world) {
        return world.getWorldFolder().toPath().resolve(PROGRESS_FILE_NAME);
    }

    private void saveProgress(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        SpiralIterator iterator = worldIterators.get(worldName);
        if (iterator == null) return;

        Path file = getProgressFile(world);
        try {
            Files.writeString(file, String.valueOf(iterator.getIndex()));
        } catch (IOException e) {
            logger.warning("[Pregen] Failed to save progress for " + worldName + ": " + e.getMessage());
        }
    }

    private long loadProgress(World world) {
        Path file = getProgressFile(world);
        if (!Files.exists(file)) {
            return 0;
        }
        try {
            String content = Files.readString(file).trim();
            return Long.parseLong(content);
        } catch (IOException | NumberFormatException e) {
            logger.warning("[Pregen] Failed to load progress for " + world.getName() + ": " + e.getMessage());
            return 0;
        }
    }

    private void clearProgress(World world) {
        Path file = getProgressFile(world);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            logger.warning("[Pregen] Failed to clear progress file for " + world.getName() + ": " + e.getMessage());
        }
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
        // Save progress before stopping
        for (String worldName : worldIterators.keySet()) {
            saveProgress(worldName);
        }
        state = State.IDLE;
        stopTicking();
        worldIterators.clear();
        worldProgress.clear();
        currentWorld = null;
        currentWorldIndex = 0;
        forcedMode = false;  // Exit forced mode on stop
        logger.info("[Pregen] Stopped (progress saved)");
    }

    public void pause() {
        if (state == State.RUNNING) {
            state = State.PAUSED;
            stopTicking();
            forcedMode = false;  // Exit forced mode on pause
            // Save progress for all worlds
            for (String worldName : worldIterators.keySet()) {
                saveProgress(worldName);
            }
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
        // Save progress before shutdown
        for (String worldName : worldIterators.keySet()) {
            saveProgress(worldName);
        }
        stopTicking();
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
