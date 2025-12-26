package sh.joey.mc.adminmode;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.inventory.InventorySnapshot;
import sh.joey.mc.inventory.InventorySnapshotStorage;
import sh.joey.mc.multiworld.WorldManager;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Manages admin creative mode - allows admins to temporarily enter creative mode
 * in survival worlds while preserving their inventory.
 */
public final class AdminModeManager implements Disposable {

    private static final Component PREFIX = Component.text("[Admin] ", NamedTextColor.GOLD);

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final AdminModeStorage storage;
    private final InventorySnapshotStorage snapshotStorage;
    private final WorldManager worldManager;
    private final Logger logger;

    // In-memory cache for fast isInAdminMode checks
    private final Set<UUID> playersInAdminMode = ConcurrentHashMap.newKeySet();

    public AdminModeManager(SiqiJoeyPlugin plugin, AdminModeStorage storage,
                            InventorySnapshotStorage snapshotStorage, WorldManager worldManager) {
        this.plugin = plugin;
        this.storage = storage;
        this.snapshotStorage = snapshotStorage;
        this.worldManager = worldManager;
        this.logger = plugin.getLogger();

        // Load existing admin mode states on startup
        disposables.add(storage.getAllInAdminMode()
                .toList()
                .subscribe(
                        states -> {
                            for (var state : states) {
                                playersInAdminMode.add(state.playerId());
                            }
                            if (!states.isEmpty()) {
                                logger.info("Loaded " + states.size() + " admin mode state(s)");
                            }
                        },
                        err -> logger.warning("Failed to load admin mode states: " + err.getMessage())
                ));

        // Watch PlayerJoinEvent - auto-exit if in admin mode
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .filter(e -> playersInAdminMode.contains(e.getPlayer().getUniqueId()))
                .subscribe(this::handleJoinWhileInAdminMode));

        // Block portal usage
        disposables.add(plugin.watchEvent(PlayerPortalEvent.class)
                .filter(e -> playersInAdminMode.contains(e.getPlayer().getUniqueId()))
                .subscribe(this::blockPortal));
    }

    /**
     * Checks if a player is currently in admin mode.
     */
    public boolean isInAdminMode(UUID playerId) {
        return playersInAdminMode.contains(playerId);
    }

    /**
     * Toggles admin mode for a player.
     */
    public void toggleAdminMode(Player player, Consumer<Boolean> callback) {
        if (isInAdminMode(player.getUniqueId())) {
            exitAdminMode(player, callback);
        } else {
            enterAdminMode(player, callback);
        }
    }

    private void enterAdminMode(Player player, Consumer<Boolean> callback) {
        UUID playerId = player.getUniqueId();

        // Check if world is a survival gamemode world
        var configOpt = worldManager.getConfig(player.getWorld());
        if (configOpt.isEmpty() || configOpt.get().gamemode() != GameMode.SURVIVAL) {
            error(player, "Admin mode can only be used in survival worlds.");
            callback.accept(false);
            return;
        }

        // Capture inventory
        Map<String, Object> labels = Map.of("source", "admin_mode");
        InventorySnapshot snapshot = InventorySnapshot.capture(player, labels);

        // Save snapshot and state
        disposables.add(snapshotStorage.save(snapshot)
                .flatMapCompletable(snapshotId ->
                        storage.enterAdminMode(playerId, player.getWorld().getUID(), snapshotId))
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        () -> {
                            playersInAdminMode.add(playerId);
                            InventorySnapshot.clearPlayer(player);
                            player.setGameMode(GameMode.CREATIVE);
                            success(player, "Entered admin mode. Your inventory has been saved.");
                            info(player, "Use /adminmode again to exit and restore your inventory.");
                            callback.accept(true);
                        },
                        err -> {
                            logger.warning("Failed to enter admin mode for " + player.getName() + ": " + err.getMessage());
                            error(player, "Failed to enter admin mode.");
                            callback.accept(false);
                        }
                ));
    }

    private void exitAdminMode(Player player, Consumer<Boolean> callback) {
        UUID playerId = player.getUniqueId();

        disposables.add(storage.getState(playerId)
                .flatMap(state -> snapshotStorage.getById(state.snapshotId()))
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        snapshot -> applySnapshotAndCleanup(player, snapshot, callback),
                        err -> {
                            logger.warning("Failed to exit admin mode for " + player.getName() + ": " + err.getMessage());
                            error(player, "Failed to exit admin mode.");
                            callback.accept(false);
                        },
                        () -> {
                            // State exists in cache but not in DB - just clean up
                            playersInAdminMode.remove(playerId);
                            player.setGameMode(GameMode.SURVIVAL);
                            info(player, "Admin mode state not found. Resetting to survival.");
                            callback.accept(true);
                        }
                ));
    }

    private void applySnapshotAndCleanup(Player player, InventorySnapshot snapshot, Consumer<Boolean> callback) {
        UUID playerId = player.getUniqueId();

        // Apply snapshot (no effect decay - we want exact restore)
        snapshot.applyTo(player, false);
        player.setGameMode(GameMode.SURVIVAL);

        // Clean up database state
        disposables.add(storage.exitAdminMode(playerId)
                .subscribe(
                        () -> {
                            playersInAdminMode.remove(playerId);
                            success(player, "Exited admin mode. Your inventory has been restored.");
                            callback.accept(true);
                        },
                        err -> {
                            // Snapshot was applied, just warn about cleanup failure
                            logger.warning("Failed to clean up admin mode state for " + player.getName() + ": " + err.getMessage());
                            playersInAdminMode.remove(playerId);
                            success(player, "Exited admin mode. Your inventory has been restored.");
                            callback.accept(true);
                        }
                ));
    }

    private void handleJoinWhileInAdminMode(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        info(player, "Exiting admin mode from previous session...");
        exitAdminMode(player, success -> {
            if (!success) {
                logger.warning("Failed to auto-exit admin mode for " + player.getName());
            }
        });
    }

    private void blockPortal(PlayerPortalEvent event) {
        event.setCancelled(true);
        error(event.getPlayer(), "You cannot use portals while in admin mode.");
        info(event.getPlayer(), "Use /adminmode to exit first.");
    }

    private void success(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.GREEN)));
    }

    private void error(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.RED)));
    }

    private void info(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.GRAY)));
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
