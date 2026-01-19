package sh.joey.mc.protection;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.confirm.ConfirmationManager;
import sh.joey.mc.confirm.ConfirmationRequest;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles lodestone-specific events for protection claims.
 */
public final class LodestoneListener implements Disposable {

    private final SiqiJoeyPlugin plugin;
    private final RegionManager manager;
    private final ConfirmationManager confirmationManager;
    private final ProtectionListener protectionListener;
    private final CompositeDisposable disposables = new CompositeDisposable();

    // Track pending claims from lodestone placement
    private final Map<UUID, PendingClaim> pendingClaims = new ConcurrentHashMap<>();

    private record PendingClaim(int x, int y, int z, UUID worldId, long timestamp) {}

    public LodestoneListener(SiqiJoeyPlugin plugin, RegionManager manager,
                             ConfirmationManager confirmationManager,
                             ProtectionListener protectionListener) {
        this.plugin = plugin;
        this.manager = manager;
        this.confirmationManager = confirmationManager;
        this.protectionListener = protectionListener;

        // Lodestone placement - prompt to claim
        disposables.add(plugin.watchEvent(EventPriority.MONITOR, BlockPlaceEvent.class)
                .filter(e -> !e.isCancelled())
                .filter(e -> e.getBlock().getType() == Material.LODESTONE)
                .subscribe(this::onLodestonePlaced));

        // Lodestone break - check if protected
        disposables.add(plugin.watchEvent(EventPriority.HIGH, BlockBreakEvent.class)
                .filter(e -> !e.isCancelled())
                .filter(e -> e.getBlock().getType() == Material.LODESTONE)
                .subscribe(this::onLodestoneBreak));

        // Left-click lodestone - show region info
        disposables.add(plugin.watchEvent(PlayerInteractEvent.class)
                .filter(e -> e.getAction() == Action.LEFT_CLICK_BLOCK)
                .filter(e -> e.getHand() == EquipmentSlot.HAND)
                .filter(e -> e.getClickedBlock() != null)
                .filter(e -> e.getClickedBlock().getType() == Material.LODESTONE)
                .subscribe(this::onLodestoneClick));
    }

    private void onLodestonePlaced(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Check if player is placing inside another player's region
        Region existing = manager.findContaining(block.getLocation(), player.getUniqueId());
        if (existing != null) {
            // Can't place lodestones in other players' regions
            event.setCancelled(true);
            Messages.error(player, "You cannot place protection stones inside other players' regions.");
            return;
        }

        // Check if this location is already claimed
        Region regionHere = manager.getRegionAt(block.getLocation());
        if (regionHere != null) {
            if (regionHere.isOwner(player.getUniqueId())) {
                Messages.info(player, "This lodestone is already protected as \"" + regionHere.name() + "\".");
            } else {
                // Should not happen since we checked above, but just in case
                event.setCancelled(true);
                Messages.error(player, "This area is already protected.");
            }
            return;
        }

        // Check if claim would intersect with any existing region
        int radius = manager.getConfig().defaultRadius();
        Region intersecting = manager.findIntersecting(
                block.getWorld().getUID(),
                block.getX(), block.getZ(),
                radius, null
        );

        if (intersecting != null) {
            Messages.warn(player, "This claim would overlap with \"" + intersecting.name() +
                    "\" (" + intersecting.ownerDisplayName() + "). Move further away to claim.");
            return;
        }

        // Check if player can create more regions
        if (!manager.canCreateRegion(player)) {
            int currentCount = manager.countOwnedRegions(player.getUniqueId());
            var limit = RegionLimitResolver.resolve(player, manager.getConfig());
            Messages.error(player, "You have reached your region limit (" +
                    currentCount + "/" + limit.orElse(0) + ").");
            return;
        }

        // Store pending claim and show prompt
        pendingClaims.put(player.getUniqueId(), new PendingClaim(
                block.getX(), block.getY(), block.getZ(),
                block.getWorld().getUID(),
                System.currentTimeMillis()
        ));

        Component message = Component.text("Claim this area? ")
                .color(NamedTextColor.GRAY)
                .append(Component.text("[Yes]")
                        .color(NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/protection claim"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to claim this lodestone"))))
                .append(Component.text(" "))
                .append(Component.text("[No]")
                        .color(NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/protection cancel"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to skip claiming"))));

        Messages.send(player, message);
    }

    private void onLodestoneBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();

        // Check if this lodestone is the center of a region
        Region region = manager.getRegionAt(block.getLocation());
        if (region == null) return;

        // Check if this is exactly the region center
        if (region.centerX() != block.getX() ||
            region.centerY() != block.getY() ||
            region.centerZ() != block.getZ()) {
            // Not the center lodestone - allow breaking if player can build
            if (!region.canBuild(player.getUniqueId()) && !protectionListener.isBypassing(player.getUniqueId())) {
                event.setCancelled(true);
                Messages.error(player, "You cannot break blocks in \"" + region.name() + "\".");
            }
            return;
        }

        // This is the region's center lodestone
        if (!region.isOwner(player.getUniqueId()) && !protectionListener.isBypassing(player.getUniqueId())) {
            event.setCancelled(true);
            Messages.error(player, "Only the owner can break this protection lodestone.");
            return;
        }

        // Owner breaking their own lodestone - warn about consequences
        event.setCancelled(true);
        Messages.warn(player, "Breaking this lodestone will remove the protection. " +
                "Use /protection unclaim " + region.name() + " to confirm.");
    }

    private void onLodestoneClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        // Check if this lodestone is part of a region
        Region region = manager.getRegionAt(block.getLocation());
        if (region == null) {
            Messages.info(player, "This lodestone is not protected.");
            return;
        }

        // Check if this is the region center
        boolean isCenter = region.centerX() == block.getX() &&
                           region.centerY() == block.getY() &&
                           region.centerZ() == block.getZ();

        if (!isCenter) {
            Messages.info(player, "Inside region \"" + region.name() + "\" (" +
                    region.ownerDisplayName() + ").");
            return;
        }

        // Show detailed info for region center
        showRegionInfo(player, region);
    }

    private void showRegionInfo(Player player, Region region) {
        boolean isOwner = region.isOwner(player.getUniqueId());
        boolean isMember = region.isMember(player.getUniqueId());

        Component header = Component.text("\"" + region.name() + "\"")
                .color(NamedTextColor.GOLD);

        if (!isOwner) {
            header = header.append(Component.text(" (" + region.ownerDisplayName() + ")")
                    .color(NamedTextColor.GRAY));
        }

        Messages.send(player, header);

        String accessStatus = isOwner ? "Owner" : (isMember ? "Member" : "No access");
        Component statusColor = isOwner ? Component.text(accessStatus).color(NamedTextColor.GREEN) :
                (isMember ? Component.text(accessStatus).color(NamedTextColor.YELLOW) :
                        Component.text(accessStatus).color(NamedTextColor.RED));

        player.sendMessage(Component.text("  Status: ").color(NamedTextColor.GRAY).append(statusColor));
        player.sendMessage(Component.text("  Radius: ").color(NamedTextColor.GRAY)
                .append(Component.text(region.radius() + " blocks").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Members: ").color(NamedTextColor.GRAY)
                .append(Component.text(region.members().size()).color(NamedTextColor.WHITE)));

        if (manager.isOrphaned(region.id())) {
            player.sendMessage(Component.text("  ").append(
                    Component.text("WARNING: Lodestone missing!").color(NamedTextColor.RED)));
        }

        if (isOwner) {
            player.sendMessage(Component.text("  Use ").color(NamedTextColor.GRAY)
                    .append(Component.text("/protection settings")
                            .color(NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/protection settings")))
                    .append(Component.text(" to manage.").color(NamedTextColor.GRAY)));
        }
    }

    /**
     * Process a pending claim from lodestone placement.
     *
     * @param player the player claiming
     * @param name the region name
     * @return true if claim was processed
     */
    public boolean processPendingClaim(Player player, String name) {
        PendingClaim pending = pendingClaims.remove(player.getUniqueId());
        if (pending == null) return false;

        // Check if claim is too old (5 minutes)
        if (System.currentTimeMillis() - pending.timestamp > 300_000) {
            return false;
        }

        // Verify lodestone still exists
        var world = plugin.getServer().getWorld(pending.worldId);
        if (world == null) {
            Messages.error(player, "World no longer loaded.");
            return false;
        }

        var block = world.getBlockAt(pending.x, pending.y, pending.z);
        if (block.getType() != Material.LODESTONE) {
            Messages.error(player, "Lodestone no longer exists at that location.");
            return false;
        }

        // Check for intersecting regions again (in case someone claimed nearby)
        int radius = manager.getConfig().defaultRadius();
        Region intersecting = manager.findIntersecting(
                pending.worldId, pending.x, pending.z, radius, null
        );

        if (intersecting != null) {
            Messages.error(player, "This claim would overlap with \"" + intersecting.name() +
                    "\" (" + intersecting.ownerDisplayName() + ").");
            return false;
        }

        // Check region limit again
        if (!manager.canCreateRegion(player)) {
            Messages.error(player, "You have reached your region limit.");
            return false;
        }

        // Create the region
        manager.createRegion(player.getUniqueId(), name, block.getLocation())
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        region -> Messages.success(player, "Created region \"" + name +
                                "\" with " + radius + " block radius."),
                        err -> {
                            plugin.getLogger().warning("[Protection] Failed to create region: " + err.getMessage());
                            Messages.error(player, "Failed to create region.");
                        }
                );

        return true;
    }

    /**
     * Cancel a pending claim.
     */
    public void cancelPendingClaim(Player player) {
        if (pendingClaims.remove(player.getUniqueId()) != null) {
            Messages.info(player, "Claim cancelled.");
        }
    }

    /**
     * Check if player has a pending claim.
     */
    public boolean hasPendingClaim(UUID playerId) {
        PendingClaim pending = pendingClaims.get(playerId);
        if (pending == null) return false;

        // Check if claim is too old
        if (System.currentTimeMillis() - pending.timestamp > 300_000) {
            pendingClaims.remove(playerId);
            return false;
        }

        return true;
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
