package sh.joey.mc.multiworld;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.inventory.InventorySnapshot;
import sh.joey.mc.inventory.InventorySnapshotStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Manages inventory group switching when players change worlds.
 * <p>
 * When a player moves to a world with a different inventory group:
 * 1. Capture their current inventory and save it
 * 2. Update the pivot table for the source group
 * 3. Load the snapshot for the target group (if exists)
 * 4. Apply the snapshot or clear inventory (first time in group)
 */
public final class InventoryGroupManager implements Disposable {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("World").color(NamedTextColor.GOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final WorldManager worldManager;
    private final InventorySnapshotStorage snapshotStorage;
    private final InventoryGroupStorage groupStorage;
    private final Logger logger;

    // Track pending transitions to handle rapid world changes
    private final Map<UUID, String> pendingTransitions = new HashMap<>();

    public InventoryGroupManager(
            SiqiJoeyPlugin plugin,
            WorldManager worldManager,
            InventorySnapshotStorage snapshotStorage,
            InventoryGroupStorage groupStorage
    ) {
        this.plugin = plugin;
        this.worldManager = worldManager;
        this.snapshotStorage = snapshotStorage;
        this.groupStorage = groupStorage;
        this.logger = plugin.getLogger();

        // Watch for world changes
        disposables.add(plugin.watchEvent(PlayerChangedWorldEvent.class)
                .subscribe(this::handleWorldChange));

        // Clean up on player quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> pendingTransitions.remove(event.getPlayer().getUniqueId())));
    }

    private void handleWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        World fromWorld = event.getFrom();
        World toWorld = player.getWorld();

        String fromGroup = worldManager.getInventoryGroup(fromWorld);
        String toGroup = worldManager.getInventoryGroup(toWorld);

        // Same group - no inventory switch needed
        if (fromGroup.equals(toGroup)) {
            return;
        }

        // Mark player as transitioning to this group
        pendingTransitions.put(playerId, toGroup);

        // Capture current inventory before any changes
        Map<String, Object> labels = Map.of(
                "source", "group_switch",
                "from_group", fromGroup,
                "to_group", toGroup
        );
        InventorySnapshot currentSnapshot = InventorySnapshot.capture(player, labels);

        // Save current inventory, update pivot table, then load target group's inventory
        snapshotStorage.save(currentSnapshot)
                .flatMapCompletable(snapshotId ->
                        groupStorage.setSnapshotForGroup(playerId, fromGroup, snapshotId))
                .andThen(groupStorage.getSnapshotForGroup(playerId, toGroup))
                .flatMap(snapshotStorage::getById)
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        snapshot -> applySnapshot(player, snapshot, toGroup),
                        err -> handleTransitionError(player, err),
                        () -> applyEmptyInventory(player, toGroup)
                );
    }

    private void applySnapshot(Player player, InventorySnapshot snapshot, String targetGroup) {
        UUID playerId = player.getUniqueId();

        // Verify player is still transitioning to this group
        if (!targetGroup.equals(pendingTransitions.get(playerId))) {
            return; // Player changed worlds again - abort this apply
        }

        pendingTransitions.remove(playerId);
        snapshot.applyTo(player, true); // Decay potion effects based on elapsed time
    }

    private void applyEmptyInventory(Player player, String targetGroup) {
        UUID playerId = player.getUniqueId();

        // Verify player is still transitioning to this group
        if (!targetGroup.equals(pendingTransitions.get(playerId))) {
            return;
        }

        pendingTransitions.remove(playerId);
        InventorySnapshot.clearPlayer(player);
    }

    private void handleTransitionError(Player player, Throwable err) {
        logger.warning("Inventory transition failed for " + player.getName() + ": " + err.getMessage());
        pendingTransitions.remove(player.getUniqueId());

        // Error recovery: player keeps their current inventory (safest option)
        player.sendMessage(PREFIX.append(
                Component.text("Failed to switch inventory. Your items have been preserved.")
                        .color(NamedTextColor.RED)));
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
