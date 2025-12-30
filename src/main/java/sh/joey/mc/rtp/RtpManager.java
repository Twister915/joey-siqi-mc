package sh.joey.mc.rtp;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Core logic for random teleport location generation and management.
 */
public final class RtpManager implements Disposable {

    private static final Set<Biome> BLACKLISTED_BIOMES = Set.of(
            // Oceans
            Biome.OCEAN, Biome.DEEP_OCEAN,
            Biome.FROZEN_OCEAN, Biome.DEEP_FROZEN_OCEAN,
            Biome.COLD_OCEAN, Biome.DEEP_COLD_OCEAN,
            Biome.LUKEWARM_OCEAN, Biome.DEEP_LUKEWARM_OCEAN,
            Biome.WARM_OCEAN,
            // Rare/Dangerous
            Biome.MUSHROOM_FIELDS,
            Biome.DEEP_DARK,
            Biome.THE_VOID
    );

    private static final int LAVA_CHECK_RADIUS = 5;
    private static final int MAX_GENERATION_ATTEMPTS = 15;

    private final SiqiJoeyPlugin plugin;
    private final RtpConfig config;
    private final RtpStorage storage;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Random random = new Random();

    // Pending candidates per player
    private final Map<UUID, List<RtpCandidate>> pendingCandidates = new HashMap<>();
    private final Map<UUID, Disposable> candidateTimeouts = new HashMap<>();

    // In-memory cooldowns for fast checks
    private final Map<UUID, Instant> cooldownEndTimes = new HashMap<>();

    public RtpManager(SiqiJoeyPlugin plugin, RtpConfig config, RtpStorage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;

        // Clean up candidates on player quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> clearCandidates(event.getPlayer().getUniqueId())));

        // Restore cooldowns from database on startup
        restoreCooldownsFromDatabase();
    }

    private void restoreCooldownsFromDatabase() {
        Duration cooldownDuration = Duration.ofMinutes(config.cooldownMinutes());
        storage.getActiveCooldowns(cooldownDuration)
                .toList()
                .observeOn(plugin.mainScheduler())
                .subscribe(entries -> {
                    for (var entry : entries) {
                        Instant endTime = entry.lastUsedAt().plus(cooldownDuration);
                        if (endTime.isAfter(Instant.now())) {
                            cooldownEndTimes.put(entry.playerId(), endTime);
                        }
                    }
                    plugin.getLogger().info("Restored " + cooldownEndTimes.size() + " RTP cooldowns from database");
                }, err -> plugin.getLogger().warning("Failed to restore RTP cooldowns: " + err.getMessage()));
    }

    /**
     * Generate candidate locations for a player.
     */
    public Single<List<RtpCandidate>> generateCandidates(World world) {
        Location spawn = world.getSpawnLocation();

        // Generate more attempts than needed to account for failures
        return Observable.range(1, MAX_GENERATION_ATTEMPTS)
                .flatMap(attemptIndex ->
                                generateSingleCandidate(world, spawn, attemptIndex)
                                        .timeout(config.chunkTimeoutSeconds(), TimeUnit.SECONDS)
                                        .onErrorResumeNext(err -> Observable.empty())
                                        .observeOn(plugin.mainScheduler()),
                        5) // Max 5 concurrent
                .take(config.candidateCount())
                .toList()
                .map(candidates -> {
                    // Re-index candidates to be 1-5
                    for (int i = 0; i < candidates.size(); i++) {
                        RtpCandidate old = candidates.get(i);
                        candidates.set(i, new RtpCandidate(
                                i + 1,
                                old.location(),
                                old.biome(),
                                old.biomeName(),
                                old.distanceFromSpawn(),
                                old.direction(),
                                old.hint()
                        ));
                    }
                    return candidates;
                });
    }

    private Observable<RtpCandidate> generateSingleCandidate(World world, Location spawn, int attemptIndex) {
        return Observable.fromCallable(() -> {
                    // Generate random coordinates
                    double angle = random.nextDouble() * 2 * Math.PI;
                    int distance = config.minDistance() + random.nextInt(config.searchRadius() - config.minDistance());
                    int x = spawn.getBlockX() + (int) (Math.cos(angle) * distance);
                    int z = spawn.getBlockZ() + (int) (Math.sin(angle) * distance);
                    return new int[]{x, z, distance};
                })
                .flatMap(coords -> {
                    int chunkX = coords[0] >> 4;
                    int chunkZ = coords[1] >> 4;

                    // Use Paper's async chunk loading
                    return Observable.<RtpCandidate>create(emitter -> {
                        world.getChunkAtAsync(chunkX, chunkZ, true).thenAccept(chunk -> {
                            if (emitter.isDisposed()) return;

                            try {
                                int x = coords[0];
                                int z = coords[1];
                                int dist = coords[2];

                                // Find surface Y, avoiding leaves and other undesirable blocks
                                int y = findSafeGroundY(world, x, z);
                                if (y < 0) {
                                    emitter.onComplete(); // No safe ground found
                                    return;
                                }

                                Location location = new Location(world, x + 0.5, y + 1, z + 0.5);

                                // Check biome at actual surface level (not underground)
                                Biome biome = world.getBiome(location);

                                if (isBiomeBlacklisted(biome)) {
                                    emitter.onComplete(); // Skip this candidate
                                    return;
                                }

                                // Safety checks
                                if (!isSafeLocation(location)) {
                                    emitter.onComplete(); // Skip this candidate
                                    return;
                                }

                                // Calculate direction from spawn
                                double dx = x - spawn.getBlockX();
                                double dz = z - spawn.getBlockZ();
                                String direction = getCardinalDirection(dx, dz);

                                // Create candidate
                                RtpCandidate candidate = new RtpCandidate(
                                        attemptIndex, // Will be re-indexed later
                                        location,
                                        biome,
                                        BiomeHints.formatBiomeName(biome),
                                        dist,
                                        direction,
                                        BiomeHints.getHint(biome)
                                );

                                emitter.onNext(candidate);
                                emitter.onComplete();
                            } catch (Exception e) {
                                emitter.onError(e);
                            }
                        }).exceptionally(ex -> {
                            if (!emitter.isDisposed()) {
                                emitter.onError(ex);
                            }
                            return null;
                        });
                    });
                });
    }

    private boolean isBiomeBlacklisted(Biome biome) {
        return BLACKLISTED_BIOMES.contains(biome);
    }

    /**
     * Find a safe ground Y level, avoiding leaves and other undesirable landing spots.
     * Returns -1 if no safe ground is found.
     */
    private int findSafeGroundY(World world, int x, int z) {
        int highestY = world.getHighestBlockYAt(x, z);

        // Search downward from highest block to find solid non-leaf ground
        for (int y = highestY; y >= world.getMinHeight() + 5; y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();

            // Skip air and passable blocks
            if (block.isPassable()) {
                continue;
            }

            // Skip leaves - we don't want to land on trees
            if (Tag.LEAVES.isTagged(type)) {
                continue;
            }

            // Skip other undesirable landing blocks
            if (isUndesirableLanding(type)) {
                continue;
            }

            // Found solid ground - check if there's space above
            Block above = block.getRelative(0, 1, 0);
            Block aboveHead = block.getRelative(0, 2, 0);
            if (above.isPassable() && aboveHead.isPassable()) {
                return y;
            }
        }

        return -1; // No safe ground found
    }

    /**
     * Check if a material is an undesirable landing spot.
     */
    private boolean isUndesirableLanding(Material type) {
        return type == Material.CACTUS
                || type == Material.SWEET_BERRY_BUSH
                || type == Material.POWDER_SNOW
                || type == Material.MAGMA_BLOCK
                || type == Material.CAMPFIRE
                || type == Material.SOUL_CAMPFIRE
                || type == Material.POINTED_DRIPSTONE
                || type == Material.SCAFFOLDING
                || Tag.FENCES.isTagged(type)
                || Tag.FENCE_GATES.isTagged(type);
    }

    private boolean isSafeLocation(Location location) {
        World world = location.getWorld();
        if (world == null) return false;

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        // Y bounds check
        if (y < world.getMinHeight() + 5 || y > world.getMaxHeight() - 10) {
            return false;
        }

        // Check for nearby surface lava
        for (int dx = -LAVA_CHECK_RADIUS; dx <= LAVA_CHECK_RADIUS; dx++) {
            for (int dz = -LAVA_CHECK_RADIUS; dz <= LAVA_CHECK_RADIUS; dz++) {
                Block block = world.getBlockAt(x + dx, y - 1, z + dz);
                if (block.getType() == Material.LAVA) {
                    return false;
                }
            }
        }

        // Safe landing (passable feet/head, solid ground)
        Block feet = location.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);

        return feet.isPassable() && head.isPassable() && ground.isSolid();
    }

    private String getCardinalDirection(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        if (angle < 0) angle += 360;
        String[] directions = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};
        return directions[(int) Math.round(angle / 45) % 8];
    }

    /**
     * Store candidates for a player with automatic expiration.
     */
    public void storeCandidates(UUID playerId, List<RtpCandidate> candidates) {
        clearCandidates(playerId); // Clear any existing
        pendingCandidates.put(playerId, candidates);

        // Set timeout to clear candidates
        Disposable timeout = plugin.timer(config.candidateTimeoutSeconds(), TimeUnit.SECONDS)
                .subscribe(tick -> clearCandidates(playerId));
        candidateTimeouts.put(playerId, timeout);
    }

    /**
     * Get a stored candidate by index.
     */
    public Optional<RtpCandidate> getCandidate(UUID playerId, int index) {
        List<RtpCandidate> candidates = pendingCandidates.get(playerId);
        if (candidates == null) return Optional.empty();

        return candidates.stream()
                .filter(c -> c.index() == index)
                .findFirst();
    }

    /**
     * Clear stored candidates for a player.
     */
    public void clearCandidates(UUID playerId) {
        pendingCandidates.remove(playerId);
        Disposable timeout = candidateTimeouts.remove(playerId);
        if (timeout != null) {
            timeout.dispose();
        }
    }

    /**
     * Check if a player is on cooldown.
     */
    public boolean isOnCooldown(UUID playerId) {
        Instant endTime = cooldownEndTimes.get(playerId);
        return endTime != null && endTime.isAfter(Instant.now());
    }

    /**
     * Get the remaining cooldown duration for a player.
     */
    public Duration getRemainingCooldown(UUID playerId) {
        Instant endTime = cooldownEndTimes.get(playerId);
        if (endTime == null || endTime.isBefore(Instant.now())) {
            return Duration.ZERO;
        }
        return Duration.between(Instant.now(), endTime);
    }

    /**
     * Start the cooldown for a player.
     */
    public void startCooldown(UUID playerId) {
        Instant endTime = Instant.now().plus(Duration.ofMinutes(config.cooldownMinutes()));
        cooldownEndTimes.put(playerId, endTime);

        // Persist to database
        storage.recordRtpUsage(playerId).subscribe(
                () -> {},
                err -> plugin.getLogger().warning("Failed to record RTP usage: " + err.getMessage())
        );
    }

    /**
     * Format remaining cooldown as a human-readable string.
     */
    public String formatRemainingCooldown(UUID playerId) {
        Duration remaining = getRemainingCooldown(playerId);
        long minutes = remaining.toMinutes();
        long seconds = remaining.minusMinutes(minutes).getSeconds();

        if (minutes > 0) {
            return minutes + " minute" + (minutes != 1 ? "s" : "") +
                    (seconds > 0 ? " " + seconds + " second" + (seconds != 1 ? "s" : "") : "");
        } else {
            return seconds + " second" + (seconds != 1 ? "s" : "");
        }
    }

    @Override
    public void dispose() {
        disposables.dispose();
        candidateTimeouts.values().forEach(Disposable::dispose);
        candidateTimeouts.clear();
        pendingCandidates.clear();
        cooldownEndTimes.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
