package sh.joey.mc.protection;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event listener for block and entity protection.
 * Handles all protection checks for regions.
 */
public final class ProtectionListener implements Disposable {

    private static final Set<Material> CONTAINERS = Set.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL,
            Material.SHULKER_BOX, Material.WHITE_SHULKER_BOX, Material.ORANGE_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX, Material.LIGHT_BLUE_SHULKER_BOX, Material.YELLOW_SHULKER_BOX,
            Material.LIME_SHULKER_BOX, Material.PINK_SHULKER_BOX, Material.GRAY_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.PURPLE_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX, Material.BROWN_SHULKER_BOX, Material.GREEN_SHULKER_BOX,
            Material.RED_SHULKER_BOX, Material.BLACK_SHULKER_BOX,
            Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.HOPPER, Material.DROPPER, Material.DISPENSER,
            Material.BREWING_STAND, Material.ENDER_CHEST
    );

    private static final Set<Material> INTERACTABLE_ENTITIES = Set.of(
            Material.ITEM_FRAME, Material.GLOW_ITEM_FRAME, Material.ARMOR_STAND
    );

    private final RegionManager manager;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // Players in bypass mode (admins)
    private final Set<UUID> bypassPlayers = ConcurrentHashMap.newKeySet();

    public ProtectionListener(SiqiJoeyPlugin plugin, RegionManager manager) {
        this.manager = manager;

        // Block break
        disposables.add(plugin.watchEvent(EventPriority.HIGH, BlockBreakEvent.class)
                .subscribe(this::onBlockBreak));

        // Block place
        disposables.add(plugin.watchEvent(EventPriority.HIGH, BlockPlaceEvent.class)
                .subscribe(this::onBlockPlace));

        // Entity explosion (creepers, TNT entities, etc.)
        disposables.add(plugin.watchEvent(EventPriority.HIGH, EntityExplodeEvent.class)
                .subscribe(this::onEntityExplode));

        // Block explosion (beds, respawn anchors)
        disposables.add(plugin.watchEvent(EventPriority.HIGH, BlockExplodeEvent.class)
                .subscribe(this::onBlockExplode));

        // Enderman pickup, silverfish breaking
        disposables.add(plugin.watchEvent(EventPriority.HIGH, EntityChangeBlockEvent.class)
                .subscribe(this::onEntityChangeBlock));

        // Player interaction (containers, doors)
        disposables.add(plugin.watchEvent(EventPriority.HIGH, PlayerInteractEvent.class)
                .subscribe(this::onPlayerInteract));

        // Player interact with entity (item frames, armor stands)
        disposables.add(plugin.watchEvent(EventPriority.HIGH, PlayerInteractEntityEvent.class)
                .subscribe(this::onPlayerInteractEntity));

        // Hanging entity break (paintings, item frames)
        disposables.add(plugin.watchEvent(EventPriority.HIGH, HangingBreakByEntityEvent.class)
                .subscribe(this::onHangingBreak));

        // PvP damage
        disposables.add(plugin.watchEvent(EventPriority.HIGH, EntityDamageByEntityEvent.class)
                .subscribe(this::onEntityDamage));
    }

    /**
     * Toggle bypass mode for a player.
     */
    public boolean toggleBypass(UUID playerId) {
        if (bypassPlayers.contains(playerId)) {
            bypassPlayers.remove(playerId);
            return false;
        } else {
            bypassPlayers.add(playerId);
            return true;
        }
    }

    /**
     * Check if a player is in bypass mode.
     */
    public boolean isBypassing(UUID playerId) {
        return bypassPlayers.contains(playerId);
    }

    private void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (isBypassing(player.getUniqueId())) return;

        Region region = manager.getRegionAt(event.getBlock().getLocation());
        if (region == null) return;

        if (!region.canBuild(player.getUniqueId())) {
            event.setCancelled(true);
            Messages.error(player, "You cannot break blocks in \"" + region.name() + "\".");
        }
    }

    private void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (isBypassing(player.getUniqueId())) return;

        Region region = manager.getRegionAt(event.getBlock().getLocation());
        if (region == null) return;

        if (!region.canBuild(player.getUniqueId())) {
            event.setCancelled(true);
            Messages.error(player, "You cannot place blocks in \"" + region.name() + "\".");
        }
    }

    private void onEntityExplode(EntityExplodeEvent event) {
        // Remove blocks that are inside protected regions
        event.blockList().removeIf(block -> {
            Region region = manager.getRegionAt(block.getLocation());
            return region != null; // Protected regions block explosions
        });
    }

    private void onBlockExplode(BlockExplodeEvent event) {
        // Remove blocks that are inside protected regions
        event.blockList().removeIf(block -> {
            Region region = manager.getRegionAt(block.getLocation());
            return region != null;
        });
    }

    private void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.isCancelled()) return;

        Region region = manager.getRegionAt(event.getBlock().getLocation());
        if (region == null) return;

        // Block enderman pickup, silverfish breaking, etc.
        event.setCancelled(true);
    }

    private void onPlayerInteract(PlayerInteractEvent event) {
        if (event.isCancelled()) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        if (isBypassing(player.getUniqueId())) return;

        Region region = manager.getRegionAt(block.getLocation());
        if (region == null) return;

        Material type = block.getType();

        // Check container access
        if (CONTAINERS.contains(type)) {
            if (!region.canAccessContainers(player.getUniqueId())) {
                event.setCancelled(true);
                Messages.error(player, "You cannot access containers in \"" + region.name() + "\".");
            }
            return;
        }

        // Check door access
        if (Tag.DOORS.isTagged(type) || Tag.FENCE_GATES.isTagged(type) || Tag.TRAPDOORS.isTagged(type)) {
            if (!region.canUseDoors(player.getUniqueId())) {
                event.setCancelled(true);
                Messages.error(player, "You cannot use doors in \"" + region.name() + "\".");
            }
            return;
        }

        // Check buttons, levers, pressure plates (treat as doors)
        if (Tag.BUTTONS.isTagged(type) || type == Material.LEVER) {
            if (!region.canUseDoors(player.getUniqueId())) {
                event.setCancelled(true);
                Messages.error(player, "You cannot use this in \"" + region.name() + "\".");
            }
        }
    }

    private void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();
        if (isBypassing(player.getUniqueId())) return;

        Entity entity = event.getRightClicked();
        Region region = manager.getRegionAt(entity.getLocation());
        if (region == null) return;

        // Item frames and armor stands require container access
        String entityType = entity.getType().name();
        if (entityType.contains("ITEM_FRAME") || entityType.equals("ARMOR_STAND")) {
            if (!region.canAccessContainers(player.getUniqueId())) {
                event.setCancelled(true);
                Messages.error(player, "You cannot interact with this in \"" + region.name() + "\".");
            }
        }
    }

    private void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.isCancelled()) return;

        Hanging hanging = event.getEntity();
        Region region = manager.getRegionAt(hanging.getLocation());
        if (region == null) return;

        Entity remover = event.getRemover();
        if (remover instanceof Player player) {
            if (isBypassing(player.getUniqueId())) return;

            if (!region.canBuild(player.getUniqueId())) {
                event.setCancelled(true);
                Messages.error(player, "You cannot break this in \"" + region.name() + "\".");
            }
        } else if (remover instanceof Projectile projectile) {
            // Projectile shot by player
            if (projectile.getShooter() instanceof Player shooter) {
                if (isBypassing(shooter.getUniqueId())) return;

                if (!region.canBuild(shooter.getUniqueId())) {
                    event.setCancelled(true);
                }
            } else {
                // Non-player projectile (skeleton arrow, etc.)
                event.setCancelled(true);
            }
        } else {
            // Explosion or other entity
            event.setCancelled(true);
        }
    }

    private void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;

        // Only handle PvP
        Entity victim = event.getEntity();
        if (!(victim instanceof Player playerVictim)) return;

        Player attacker = getPlayerAttacker(event);
        if (attacker == null) return;

        // Check bypass mode
        if (isBypassing(attacker.getUniqueId())) return;

        // Check if PvP is in a protected region
        Region region = manager.getRegionAt(playerVictim.getLocation());
        if (region == null) return;

        // Check if attacker has PvP access in this region
        if (!region.canPvp(attacker.getUniqueId())) {
            event.setCancelled(true);
            Messages.error(attacker, "PvP is disabled in \"" + region.name() + "\".");
        }
    }

    private Player getPlayerAttacker(EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();

        if (damager instanceof Player player) {
            return player;
        }

        if (damager instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player shooter) {
                return shooter;
            }
        }

        return null;
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
