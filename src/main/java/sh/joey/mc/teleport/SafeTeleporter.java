package sh.joey.mc.teleport;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.confirm.ConfirmationManager;
import sh.joey.mc.confirm.ConfirmationRequest;
import sh.joey.mc.multiworld.PlayerWorldPositionStorage;
import sh.joey.mc.multiworld.WorldsConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Handles safe teleportation with warmup countdown and movement detection.
 * Players must stand still during the warmup period to teleport.
 */
public final class SafeTeleporter implements Disposable {
    private static final long CANCELLED_DISPLAY_MS = 3000; // Show cancelled for 3 seconds
    private static final int SAFE_LOCATION_SEARCH_RADIUS = 10; // Max blocks to search for safe spot
    private static final int UNSAFE_CONFIRM_TIMEOUT_SECONDS = 15;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final PluginConfig config;
    private final LocationTracker locationTracker;
    private final ConfirmationManager confirmationManager;
    private final PlayerWorldPositionStorage worldPositionStorage;
    private final WorldsConfig worldsConfig;
    private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
    private final Map<UUID, Long> cancelledTeleports = new HashMap<>();

    private record PendingTeleport(
            Location startLocation,
            Location destination,
            Disposable countdownTask,
            Consumer<Boolean> onComplete,
            long startTimeMs,
            int totalSeconds
    ) {}

    /**
     * State of a pending teleport for external display (e.g., boss bar).
     */
    public record TeleportState(long startTimeMs, int totalSeconds) {
        public float getProgress() {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            long totalMs = totalSeconds * 1000L;
            return Math.max(0, Math.min(1, 1.0f - (float) elapsed / totalMs));
        }

        public int getRemainingSeconds() {
            long elapsed = System.currentTimeMillis() - startTimeMs;
            long totalMs = totalSeconds * 1000L;
            long remaining = totalMs - elapsed;
            return (int) Math.max(0, Math.ceil(remaining / 1000.0));
        }
    }

    /**
     * State of a cancelled teleport for external display.
     */
    public record CancelledState(long cancelledAtMs) {
        public float getProgress() {
            long elapsed = System.currentTimeMillis() - cancelledAtMs;
            return Math.max(0, 1.0f - (float) elapsed / CANCELLED_DISPLAY_MS);
        }

        public boolean isExpired() {
            return System.currentTimeMillis() - cancelledAtMs > CANCELLED_DISPLAY_MS;
        }
    }

    public SafeTeleporter(SiqiJoeyPlugin plugin, PluginConfig config, LocationTracker locationTracker,
                          ConfirmationManager confirmationManager,
                          PlayerWorldPositionStorage worldPositionStorage,
                          WorldsConfig worldsConfig) {
        this.plugin = plugin;
        this.config = config;
        this.locationTracker = locationTracker;
        this.confirmationManager = confirmationManager;
        this.worldPositionStorage = worldPositionStorage;
        this.worldsConfig = worldsConfig;

        // Movement detection
        disposables.add(plugin.watchEvent(EventPriority.MONITOR, PlayerMoveEvent.class)
                .subscribe(this::handlePlayerMove));

        // Player quit cleanup
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> {
                    UUID playerId = event.getPlayer().getUniqueId();
                    cancelTeleport(playerId, false);
                    cancelledTeleports.remove(playerId);
                }));
    }

    @Override
    public void dispose() {
        disposables.dispose();
        // Cancel all pending teleports
        pendingTeleports.values().forEach(pending -> pending.countdownTask().dispose());
        pendingTeleports.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    /**
     * Initiates a safe teleport with warmup. Player must not move during countdown.
     * If the destination is unsafe, prompts the player for confirmation first.
     * If the player's current world has teleport_warmup: false, teleports instantly.
     *
     * @param player      The player to teleport
     * @param destination Where to teleport
     * @param onComplete  Callback with true if teleport succeeded, false if cancelled
     */
    public void teleport(Player player, Location destination, Consumer<Boolean> onComplete) {
        UUID playerId = player.getUniqueId();

        // Reject if player is in a vehicle
        if (player.isInsideVehicle()) {
            Messages.error(player, "You cannot teleport while in a vehicle!");
            if (onComplete != null) {
                onComplete.accept(false);
            }
            return;
        }

        // Reject if player is sleeping
        if (player.isSleeping()) {
            Messages.error(player, "You cannot teleport while sleeping!");
            if (onComplete != null) {
                onComplete.accept(false);
            }
            return;
        }

        // Check if destination is safe
        Optional<Location> safeLocation = findSafeLocation(destination);

        if (safeLocation.isEmpty()) {
            // No safe location found - ask for confirmation
            requestUnsafeTeleportConfirmation(player, destination, onComplete);
            return;
        }

        // Check if current world has warmup disabled
        if (shouldSkipWarmup(player)) {
            executeTeleportUnsafe(player, safeLocation.get(), onComplete);
            return;
        }

        // Safe location found - proceed with warmup
        startWarmup(player, safeLocation.get(), onComplete);
    }

    /**
     * Checks if teleport warmup should be skipped for the player's current world.
     */
    private boolean shouldSkipWarmup(Player player) {
        String worldName = player.getWorld().getName().toLowerCase();
        var worldConfig = worldsConfig.worlds().get(worldName);
        return worldConfig != null && !worldConfig.teleportWarmup();
    }

    private void requestUnsafeTeleportConfirmation(Player player, Location destination,
                                                   Consumer<Boolean> onComplete) {
        confirmationManager.request(player, new ConfirmationRequest() {
            @Override
            public Component prefix() {
                return Messages.PREFIX;
            }

            @Override
            public String promptText() {
                return "Destination may be unsafe. Teleport anyway?";
            }

            @Override
            public String acceptText() {
                return "Teleport";
            }

            @Override
            public String declineText() {
                return "Cancel";
            }

            @Override
            public void onAccept() {
                // Bypass safety check, teleport with warmup
                startWarmup(player, destination, onComplete);
            }

            @Override
            public void onDecline() {
                Messages.info(player, "Teleport cancelled.");
                if (onComplete != null) onComplete.accept(false);
            }

            @Override
            public void onTimeout() {
                Messages.info(player, "Teleport confirmation expired.");
                if (onComplete != null) onComplete.accept(false);
            }

            @Override
            public int timeoutSeconds() {
                return UNSAFE_CONFIRM_TIMEOUT_SECONDS;
            }
        });
    }

    private void startWarmup(Player player, Location destination, Consumer<Boolean> onComplete) {
        UUID playerId = player.getUniqueId();

        // Cancel any existing pending teleport
        cancelTeleport(playerId, false);

        // Clear any cancelled display state
        cancelledTeleports.remove(playerId);

        Location startLocation = player.getLocation().clone();
        int totalSeconds = config.teleportWarmupSeconds();

        Messages.countdown(player, totalSeconds);

        // Create countdown using interval
        Disposable countdownTask = plugin.interval(1, TimeUnit.SECONDS)
                .take(totalSeconds)
                .subscribe(
                        tick -> {
                            int secondsRemaining = totalSeconds - tick.intValue() - 1;
                            if (secondsRemaining > 0) {
                                Messages.countdown(player, secondsRemaining);
                            } else {
                                // Time's up - execute teleport
                                executeTeleportUnsafe(player, destination, onComplete);
                            }
                        },
                        error -> plugin.getLogger().warning("Teleport countdown error: " + error.getMessage())
                );

        pendingTeleports.put(playerId, new PendingTeleport(
                startLocation, destination, countdownTask, onComplete,
                System.currentTimeMillis(), totalSeconds
        ));
    }

    private void handlePlayerMove(PlayerMoveEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        PendingTeleport pending = pendingTeleports.get(playerId);

        if (pending == null) {
            return;
        }

        // Calculate horizontal distance moved (ignore Y for small jumps/falls)
        Location from = pending.startLocation();
        Location to = event.getTo();

        double horizontalDistance = Math.sqrt(
                Math.pow(to.getX() - from.getX(), 2) +
                Math.pow(to.getZ() - from.getZ(), 2)
        );

        // Also check vertical but with more tolerance (for jumping)
        double verticalDistance = Math.abs(to.getY() - from.getY());

        // Cancel if moved beyond tolerance
        if (horizontalDistance > config.movementToleranceBlocks() ||
            verticalDistance > config.movementToleranceBlocks() * 2) {
            cancelTeleport(playerId, true);
        }
    }

    /**
     * Executes teleport without additional safety checks.
     * The destination has already been validated or the player confirmed unsafe teleport.
     */
    private void executeTeleportUnsafe(Player player, Location destination, Consumer<Boolean> onComplete) {
        UUID playerId = player.getUniqueId();
        PendingTeleport pending = pendingTeleports.remove(playerId);

        if (pending != null) {
            pending.countdownTask().dispose();
        }

        // Record current location (async) before teleporting (for /back)
        Location departureLocation = player.getLocation().clone();
        locationTracker.recordTeleportFrom(playerId, departureLocation).subscribe();

        // Save world position before cross-world teleport (for /world return-to-position)
        if (destination.getWorld() != null && !destination.getWorld().equals(departureLocation.getWorld())) {
            worldPositionStorage.savePosition(playerId, departureLocation)
                    .subscribe(() -> {}, err -> plugin.getLogger().warning(
                            "Failed to save world position for " + player.getName() + ": " + err.getMessage()));
        }

        // Play departure effects
        playTeleportEffects(departureLocation, true);

        player.teleport(destination);

        // Play arrival effects
        playTeleportEffects(destination, false);

        Messages.success(player, "Teleported!");

        if (onComplete != null) {
            onComplete.accept(true);
        }
    }

    /**
     * Finds a safe location near the destination where the player won't die.
     * A safe location has 2 blocks of non-solid, non-lethal space for feet and head.
     * Water is considered safe (player can swim). Air is safe (player will fall but survive).
     *
     * @param destination The target location
     * @return A safe location, or empty if none found within search radius
     */
    private Optional<Location> findSafeLocation(Location destination) {
        World world = destination.getWorld();
        if (world == null) {
            return Optional.empty();
        }

        // First check if the original destination is safe
        if (isSafeLocation(destination)) {
            return Optional.of(destination);
        }

        // Search in expanding layers: first vertical, then horizontal
        // This prioritizes staying close to the original destination

        // Phase 1: Search vertically (most common fix for being in ground)
        for (int dy = 1; dy <= SAFE_LOCATION_SEARCH_RADIUS; dy++) {
            Location up = destination.clone().add(0, dy, 0);
            if (isSafeLocation(up)) {
                return Optional.of(up);
            }
            Location down = destination.clone().subtract(0, dy, 0);
            if (isSafeLocation(down)) {
                return Optional.of(down);
            }
        }

        // Phase 2: Search horizontally (for wall teleportation issues)
        // Check adjacent blocks in cardinal directions first, then diagonals
        int[][] horizontalOffsets = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},  // Cardinal
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1} // Diagonal
        };

        for (int radius = 1; radius <= SAFE_LOCATION_SEARCH_RADIUS / 2; radius++) {
            for (int[] offset : horizontalOffsets) {
                int dx = offset[0] * radius;
                int dz = offset[1] * radius;

                // Check at same Y, then above, then below
                for (int dy = 0; dy <= SAFE_LOCATION_SEARCH_RADIUS; dy++) {
                    Location candidate = destination.clone().add(dx, dy, dz);
                    if (isSafeLocation(candidate)) {
                        return Optional.of(candidate);
                    }
                    if (dy > 0) {
                        Location candidateDown = destination.clone().add(dx, -dy, dz);
                        if (isSafeLocation(candidateDown)) {
                            return Optional.of(candidateDown);
                        }
                    }
                }
            }
        }

        return Optional.empty();
    }

    /**
     * Checks if a location is safe for a player to stand at.
     * Safe means: feet and head blocks won't kill the player (not solid, not lethal).
     * Water is safe (player can swim). Air is safe (player will fall but usually survive).
     */
    private boolean isSafeLocation(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }

        Block feetBlock = location.getBlock();
        Block headBlock = feetBlock.getRelative(0, 1, 0);

        // Check for lethal materials (instant damage)
        if (isLethalBlock(feetBlock) || isLethalBlock(headBlock)) {
            return false;
        }

        // Feet and head must not be solid (would cause suffocation)
        // Note: water, air, grass, etc. are all passable and safe
        return feetBlock.isPassable() && headBlock.isPassable();
    }

    /**
     * Checks if a block contains a lethal material that would kill the player.
     */
    private boolean isLethalBlock(Block block) {
        Material type = block.getType();
        return type == Material.LAVA
            || type == Material.FIRE
            || type == Material.SOUL_FIRE;
    }

    /**
     * Plays teleport particle and sound effects at a location.
     *
     * @param location   The location to play effects at
     * @param isDeparture True if this is the departure location, false for arrival
     */
    private void playTeleportEffects(Location location, boolean isDeparture) {
        World world = location.getWorld();
        if (world == null) return;

        // Center the effects at player eye level
        Location effectLocation = location.clone().add(0, 1, 0);

        // Smoke particles that linger and rise
        world.spawnParticle(
                Particle.CAMPFIRE_SIGNAL_SMOKE,
                effectLocation,
                15,           // count
                0.3,          // offsetX
                0.5,          // offsetY
                0.3,          // offsetZ
                0.01          // speed (slow rise)
        );

        // Portal particles for a magical effect
        world.spawnParticle(
                Particle.PORTAL,
                effectLocation,
                30,           // count
                0.4,          // offsetX
                0.8,          // offsetY
                0.4,          // offsetZ
                0.5           // speed
        );

        // Play teleport sound for nearby players
        world.playSound(
                location,
                isDeparture ? Sound.ENTITY_ENDERMAN_TELEPORT : Sound.ITEM_CHORUS_FRUIT_TELEPORT,
                1.0f,         // volume
                isDeparture ? 0.8f : 1.2f  // pitch (lower for departure, higher for arrival)
        );
    }

    private void cancelTeleport(UUID playerId, boolean notify) {
        PendingTeleport pending = pendingTeleports.remove(playerId);
        if (pending != null) {
            pending.countdownTask().dispose();
            if (notify) {
                // Record cancellation for boss bar display
                cancelledTeleports.put(playerId, System.currentTimeMillis());
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    Messages.teleportCancelled(player);
                }
            }
            if (pending.onComplete() != null) {
                pending.onComplete().accept(false);
            }
        }
    }

    public boolean hasPendingTeleport(UUID playerId) {
        return pendingTeleports.containsKey(playerId);
    }

    /**
     * Gets the teleport state for a player, if they have a pending teleport.
     */
    public TeleportState getTeleportState(UUID playerId) {
        PendingTeleport pending = pendingTeleports.get(playerId);
        if (pending == null) {
            return null;
        }
        return new TeleportState(pending.startTimeMs(), pending.totalSeconds());
    }

    /**
     * Gets the cancelled state for a player, if their teleport was recently cancelled.
     */
    public CancelledState getCancelledState(UUID playerId) {
        Long cancelledAt = cancelledTeleports.get(playerId);
        if (cancelledAt == null) {
            return null;
        }
        CancelledState state = new CancelledState(cancelledAt);
        if (state.isExpired()) {
            cancelledTeleports.remove(playerId);
            return null;
        }
        return state;
    }
}
