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

import java.util.List;
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

    private record PendingClaim(int x, int y, int z, UUID worldId, long timestamp, UUID expandRegionId) {}

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
        UUID playerId = player.getUniqueId();

        // Check if player is placing inside another player's region
        Region existing = manager.findContaining(block.getLocation(), playerId);
        if (existing != null) {
            // Can't place lodestones in other players' regions
            event.setCancelled(true);
            Messages.error(player, "You cannot place protection stones inside other players' regions.");
            return;
        }

        // Check if this location is already an anchor
        Region regionWithAnchor = manager.getRegionByAnchor(
                block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ()
        );
        if (regionWithAnchor != null) {
            Messages.info(player, "This lodestone is already an anchor for \"" + regionWithAnchor.name() + "\".");
            return;
        }

        // Check if this is inside the player's own region
        Region ownRegion = manager.getRegionAt(block.getLocation());
        if (ownRegion != null && ownRegion.isOwner(playerId)) {
            // Player placing lodestone inside their own region - offer to add as anchor
            offerAddAnchor(player, block, ownRegion);
            return;
        }

        // Check for nearby owned regions that could be expanded
        List<Region> nearbyRegions = manager.findNearbyOwnedRegions(
                playerId,
                block.getWorld().getUID(),
                block.getX(), block.getZ()
        );

        // Check if claim would intersect with any OTHER player's region
        int radius = manager.getConfig().defaultRadius();
        Region intersecting = manager.findIntersecting(
                block.getWorld().getUID(),
                block.getX(), block.getZ(),
                radius, null
        );

        // Filter out own regions from intersection check
        if (intersecting != null && intersecting.isOwner(playerId)) {
            // Would intersect own region - this is expected for expansion
            intersecting = null;
        }

        if (intersecting != null) {
            Messages.warn(player, "This claim would overlap with \"" + intersecting.name() +
                    "\" (" + intersecting.ownerDisplayName() + "). Move further away to claim.");
            return;
        }

        // If there are nearby owned regions, offer to expand one of them
        if (!nearbyRegions.isEmpty()) {
            offerExpandOrNewClaim(player, block, nearbyRegions);
            return;
        }

        // Otherwise, offer to create a new region
        offerNewClaim(player, block);
    }

    private void offerAddAnchor(Player player, Block block, Region region) {
        // Store pending claim for expansion
        pendingClaims.put(player.getUniqueId(), new PendingClaim(
                block.getX(), block.getY(), block.getZ(),
                block.getWorld().getUID(),
                System.currentTimeMillis(),
                region.id()
        ));

        Component message = Component.text("Add anchor to \"" + region.name() + "\"? ")
                .color(NamedTextColor.GRAY)
                .append(Component.text("[Yes]")
                        .color(NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/protection expand"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to add anchor"))))
                .append(Component.text(" "))
                .append(Component.text("[No]")
                        .color(NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/protection cancel"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to skip"))));

        Messages.send(player, message);
    }

    private void offerExpandOrNewClaim(Player player, Block block, List<Region> nearbyRegions) {
        Region closest = nearbyRegions.getFirst(); // Use the first (closest) region

        // Store pending claim - user can choose to expand or create new
        pendingClaims.put(player.getUniqueId(), new PendingClaim(
                block.getX(), block.getY(), block.getZ(),
                block.getWorld().getUID(),
                System.currentTimeMillis(),
                closest.id()
        ));

        // Check if player can create more regions
        boolean canCreateNew = manager.canCreateRegion(player);

        Component message = Component.text("Extend \"" + closest.name() + "\" with this lodestone? ")
                .color(NamedTextColor.GRAY)
                .append(Component.text("[Extend]")
                        .color(NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/protection expand"))
                        .hoverEvent(HoverEvent.showText(Component.text("Add anchor to " + closest.name()))));

        if (canCreateNew) {
            message = message.append(Component.text(" "))
                    .append(Component.text("[New Claim]")
                            .color(NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/protection claim"))
                            .hoverEvent(HoverEvent.showText(Component.text("Create a new region instead"))));
        }

        message = message.append(Component.text(" "))
                .append(Component.text("[Cancel]")
                        .color(NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/protection cancel"))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to skip"))));

        Messages.send(player, message);
    }

    private void offerNewClaim(Player player, Block block) {
        // Check if player can create more regions
        if (!manager.canCreateRegion(player)) {
            int currentCount = manager.countOwnedRegions(player.getUniqueId());
            var limit = RegionLimitResolver.resolve(player, manager.getConfig());
            Messages.error(player, "You have reached your region limit (" +
                    currentCount + "/" + limit.orElse(0) + ").");
            return;
        }

        // Store pending claim
        pendingClaims.put(player.getUniqueId(), new PendingClaim(
                block.getX(), block.getY(), block.getZ(),
                block.getWorld().getUID(),
                System.currentTimeMillis(),
                null // No region to expand
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

        // Check if this lodestone is an anchor
        Region region = manager.getRegionByAnchor(
                block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ()
        );
        if (region == null) {
            // Not an anchor - check if inside a region for normal protection
            region = manager.getRegionAt(block.getLocation());
            if (region != null && !region.canBuild(player.getUniqueId()) &&
                    !protectionListener.isBypassing(player.getUniqueId())) {
                event.setCancelled(true);
                Messages.error(player, "You cannot break blocks in \"" + region.name() + "\".");
            }
            return;
        }

        // This is an anchor lodestone
        if (!region.isOwner(player.getUniqueId()) && !protectionListener.isBypassing(player.getUniqueId())) {
            event.setCancelled(true);
            Messages.error(player, "Only the owner can break protection anchors.");
            return;
        }

        // Owner breaking their own anchor
        boolean isLastAnchor = region.anchors().size() == 1;

        event.setCancelled(true);
        if (isLastAnchor) {
            Messages.warn(player, "This is the last anchor. Breaking it will remove the entire region. " +
                    "Use /protection unclaim " + region.name() + " to confirm.");
        } else {
            // Offer to remove just this anchor
            Anchor anchor = region.getAnchorAt(block.getX(), block.getY(), block.getZ());
            if (anchor != null) {
                offerRemoveAnchor(player, region, anchor);
            }
        }
    }

    private void offerRemoveAnchor(Player player, Region region, Anchor anchor) {
        confirmationManager.request(player, new ConfirmationRequest() {
            @Override
            public Component prefix() {
                return Messages.PREFIX;
            }

            @Override
            public String promptText() {
                return "Remove this anchor from \"" + region.name() + "\"? (" +
                        (region.anchors().size() - 1) + " anchors remaining)";
            }

            @Override
            public String acceptText() {
                return "Remove";
            }

            @Override
            public String declineText() {
                return "Cancel";
            }

            @Override
            public void onAccept() {
                manager.removeAnchor(anchor.id())
                        .observeOn(plugin.mainScheduler())
                        .subscribe(
                                removed -> {
                                    if (removed) {
                                        // Break the block
                                        var world = plugin.getServer().getWorld(region.worldId());
                                        if (world != null) {
                                            var block = world.getBlockAt(anchor.x(), anchor.y(), anchor.z());
                                            block.breakNaturally();
                                        }
                                        Messages.success(player, "Removed anchor from \"" + region.name() + "\".");
                                    }
                                },
                                err -> Messages.error(player, "Failed to remove anchor.")
                        );
            }

            @Override
            public void onDecline() {
                Messages.info(player, "Anchor removal cancelled.");
            }

            @Override
            public int timeoutSeconds() {
                return 30;
            }
        });
    }

    private void onLodestoneClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        if (block == null) return;

        // Check if this lodestone is an anchor
        Region region = manager.getRegionByAnchor(
                block.getWorld().getUID(),
                block.getX(), block.getY(), block.getZ()
        );

        if (region == null) {
            // Check if inside a region but not at an anchor
            region = manager.getRegionAt(block.getLocation());
            if (region == null) {
                Messages.info(player, "This lodestone is not protected.");
            } else {
                Messages.info(player, "Inside region \"" + region.name() + "\" (" +
                        region.ownerDisplayName() + ").");
            }
            return;
        }

        // Show detailed info for anchor
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
        player.sendMessage(Component.text("  Anchors: ").color(NamedTextColor.GRAY)
                .append(Component.text(region.anchors().size()).color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Members: ").color(NamedTextColor.GRAY)
                .append(Component.text(region.members().size()).color(NamedTextColor.WHITE)));

        if (manager.isOrphaned(region.id())) {
            player.sendMessage(Component.text("  ").append(
                    Component.text("WARNING: Some anchors missing!").color(NamedTextColor.RED)));
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
     * Process a pending claim to expand an existing region.
     *
     * @param player the player claiming
     * @return true if expansion was processed
     */
    public boolean processExpand(Player player) {
        PendingClaim pending = pendingClaims.remove(player.getUniqueId());
        if (pending == null || pending.expandRegionId == null) {
            Messages.error(player, "No pending expansion. Place a lodestone first.");
            return false;
        }

        // Check if claim is too old (5 minutes)
        if (System.currentTimeMillis() - pending.timestamp > 300_000) {
            Messages.error(player, "Expansion request expired. Place a new lodestone.");
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

        // Get the region to expand
        var regionOpt = manager.getRegion(pending.expandRegionId);
        if (regionOpt.isEmpty()) {
            Messages.error(player, "Region no longer exists.");
            return false;
        }

        Region region = regionOpt.get();

        // Verify player still owns it
        if (!region.isOwner(player.getUniqueId())) {
            Messages.error(player, "You no longer own this region.");
            return false;
        }

        // Add the anchor
        manager.addAnchor(pending.expandRegionId, block.getLocation())
                .observeOn(plugin.mainScheduler())
                .subscribe(
                        anchor -> Messages.success(player, "Added anchor to \"" + region.name() +
                                "\". Now has " + (region.anchors().size() + 1) + " anchors."),
                        err -> {
                            plugin.getLogger().warning("[Protection] Failed to add anchor: " + err.getMessage());
                            Messages.error(player, "Failed to add anchor.");
                        }
                );

        return true;
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

        // If this was meant to be an expansion, don't allow creating new region
        if (pending.expandRegionId != null) {
            Messages.info(player, "Use /protection expand to add to existing region, or place a lodestone further away for a new claim.");
            pendingClaims.put(player.getUniqueId(), pending); // Put it back
            return false;
        }

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

        if (intersecting != null && !intersecting.isOwner(player.getUniqueId())) {
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

    /**
     * Check if player has a pending expansion (not a new claim).
     */
    public boolean hasPendingExpansion(UUID playerId) {
        PendingClaim pending = pendingClaims.get(playerId);
        if (pending == null) return false;

        if (System.currentTimeMillis() - pending.timestamp > 300_000) {
            pendingClaims.remove(playerId);
            return false;
        }

        return pending.expandRegionId != null;
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
