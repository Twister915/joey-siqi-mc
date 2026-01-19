package sh.joey.mc.protection.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.confirm.ConfirmationManager;
import sh.joey.mc.confirm.ConfirmationRequest;
import sh.joey.mc.player.PlayerResolver;
import sh.joey.mc.protection.AccessLevel;
import sh.joey.mc.protection.Anchor;
import sh.joey.mc.protection.LodestoneListener;
import sh.joey.mc.protection.Messages;
import sh.joey.mc.protection.ProtectionListener;
import sh.joey.mc.protection.RadiusLimitResolver;
import sh.joey.mc.protection.Region;
import sh.joey.mc.protection.RegionLimitResolver;
import sh.joey.mc.protection.RegionManager;
import sh.joey.mc.protection.RegionVisualizer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main protection command with subcommands.
 */
public final class ProtectionCommand implements Command {

    private static final List<String> SUBCOMMANDS = List.of(
            "claim", "unclaim", "info", "list", "trust", "untrust",
            "settings", "access", "radius", "repair", "visualize", "help",
            "cancel", "expand", "anchors", "bypass", "cleanup", "forceunclaim", "forcerepair"
    );

    private static final List<String> ACCESS_SETTINGS = List.of("building", "containers", "doors");
    private static final List<String> ACCESS_LEVELS = List.of("everybody", "members", "owner");

    private final SiqiJoeyPlugin plugin;
    private final RegionManager manager;
    private final LodestoneListener lodestoneListener;
    private final ProtectionListener protectionListener;
    private final RegionVisualizer visualizer;
    private final ConfirmationManager confirmationManager;
    private final PlayerResolver playerResolver;

    public ProtectionCommand(SiqiJoeyPlugin plugin, RegionManager manager,
                             LodestoneListener lodestoneListener,
                             ProtectionListener protectionListener,
                             RegionVisualizer visualizer,
                             ConfirmationManager confirmationManager,
                             PlayerResolver playerResolver) {
        this.plugin = plugin;
        this.manager = manager;
        this.lodestoneListener = lodestoneListener;
        this.protectionListener = protectionListener;
        this.visualizer = visualizer;
        this.confirmationManager = confirmationManager;
        this.playerResolver = playerResolver;
    }

    @Override
    public String getName() {
        return "protection";
    }

    @Override
    public @Nullable String getPermission() {
        return "smp.protection";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be used by players.").color(NamedTextColor.RED));
            return Completable.complete();
        }

        if (args.length == 0) {
            return handleList(player);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        return switch (subcommand) {
            case "claim" -> handleClaim(player, subArgs);
            case "unclaim" -> handleUnclaim(player, subArgs);
            case "info" -> handleInfo(player);
            case "list" -> handleList(player);
            case "trust" -> handleTrust(player, subArgs);
            case "untrust" -> handleUntrust(player, subArgs);
            case "settings" -> handleSettings(player);
            case "access" -> handleAccess(player, subArgs);
            case "radius" -> handleRadius(player, subArgs);
            case "repair" -> handleRepair(player, subArgs);
            case "visualize" -> handleVisualize(player);
            case "help" -> handleHelp(player);
            case "cancel" -> handleCancel(player);
            case "expand" -> handleExpand(player);
            case "anchors" -> handleAnchors(player, subArgs);
            case "bypass" -> handleBypass(player);
            case "cleanup" -> handleCleanup(player);
            case "forceunclaim" -> handleForceUnclaim(player, subArgs);
            case "forcerepair" -> handleForceRepair(player, subArgs);
            default -> {
                Messages.error(player, "Unknown subcommand. Use /protection help for usage.");
                yield Completable.complete();
            }
        };
    }

    private Completable handleClaim(Player player, String[] args) {
        // Check for pending claim from lodestone placement
        if (lodestoneListener.hasPendingClaim(player.getUniqueId())) {
            String name = args.length > 0 ? String.join(" ", args) : "base";
            if (lodestoneListener.processPendingClaim(player, name)) {
                return Completable.complete();
            }
        }

        // Otherwise, try to claim lodestone player is looking at
        Block target = player.getTargetBlockExact(5);
        if (target == null || target.getType() != Material.LODESTONE) {
            Messages.error(player, "Look at a lodestone to claim it, or place a new lodestone.");
            return Completable.complete();
        }

        // Check if already claimed
        Region existing = manager.getRegionAt(target.getLocation());
        if (existing != null) {
            if (existing.isOwner(player.getUniqueId())) {
                Messages.info(player, "This lodestone is already protected as \"" + existing.name() + "\".");
            } else {
                Messages.error(player, "This lodestone is already protected by " + existing.ownerDisplayName() + ".");
            }
            return Completable.complete();
        }

        // Check for intersection
        int radius = manager.getConfig().defaultRadius();
        Region intersecting = manager.findIntersecting(
                target.getWorld().getUID(),
                target.getX(), target.getZ(),
                radius, null
        );

        if (intersecting != null) {
            Messages.error(player, "This claim would overlap with \"" + intersecting.name() +
                    "\" (" + intersecting.ownerDisplayName() + ").");
            return Completable.complete();
        }

        // Check region limit
        if (!manager.canCreateRegion(player)) {
            int count = manager.countOwnedRegions(player.getUniqueId());
            var limit = RegionLimitResolver.resolve(player, manager.getConfig());
            Messages.error(player, "You have reached your region limit (" +
                    count + "/" + limit.orElse(0) + ").");
            return Completable.complete();
        }

        String name = args.length > 0 ? String.join(" ", args) : "base";

        return manager.createRegion(player.getUniqueId(), name, target.getLocation())
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(region -> Messages.success(player, "Created region \"" + name +
                        "\" with " + radius + " block radius."))
                .doOnError(err -> {
                    plugin.getLogger().warning("[Protection] Failed to create region: " + err.getMessage());
                    Messages.error(player, "Failed to create region.");
                })
                .ignoreElement()
                .onErrorComplete();
    }

    private Completable handleUnclaim(Player player, String[] args) {
        if (args.length == 0) {
            Messages.error(player, "Usage: /protection unclaim <name>");
            return Completable.complete();
        }

        String name = String.join(" ", args);
        Optional<Region> regionOpt = manager.getRegion(player.getUniqueId(), name);

        if (regionOpt.isEmpty()) {
            Messages.error(player, "You don't have a region named \"" + name + "\".");
            return Completable.complete();
        }

        Region region = regionOpt.get();

        // Confirm deletion
        confirmationManager.request(player, new ConfirmationRequest() {
            @Override
            public Component prefix() {
                return Messages.PREFIX;
            }

            @Override
            public String promptText() {
                return "Delete region \"" + region.name() + "\"? This cannot be undone.";
            }

            @Override
            public String acceptText() {
                return "Delete";
            }

            @Override
            public String declineText() {
                return "Cancel";
            }

            @Override
            public void onAccept() {
                manager.deleteRegion(region.id())
                        .observeOn(plugin.mainScheduler())
                        .subscribe(
                                deleted -> {
                                    if (deleted) {
                                        Messages.success(player, "Deleted region \"" + region.name() + "\".");
                                    } else {
                                        Messages.error(player, "Region not found.");
                                    }
                                },
                                err -> Messages.error(player, "Failed to delete region.")
                        );
            }

            @Override
            public void onDecline() {
                Messages.info(player, "Deletion cancelled.");
            }

            @Override
            public int timeoutSeconds() {
                return 30;
            }
        });

        return Completable.complete();
    }

    private Completable handleInfo(Player player) {
        Region region = manager.getRegionAt(player.getLocation());
        if (region == null) {
            Messages.info(player, "You are not in a protected region.");
            return Completable.complete();
        }

        boolean isOwner = region.isOwner(player.getUniqueId());
        boolean isMember = region.isMember(player.getUniqueId());

        Component header = Component.text("\"" + region.name() + "\"").color(NamedTextColor.GOLD);
        if (!isOwner) {
            header = header.append(Component.text(" (" + region.ownerDisplayName() + ")")
                    .color(NamedTextColor.GRAY));
        }
        Messages.send(player, header);

        String status = isOwner ? "Owner" : (isMember ? "Member" : "No access");
        NamedTextColor statusColor = isOwner ? NamedTextColor.GREEN :
                (isMember ? NamedTextColor.YELLOW : NamedTextColor.RED);

        player.sendMessage(Component.text("  Status: ").color(NamedTextColor.GRAY)
                .append(Component.text(status).color(statusColor)));
        player.sendMessage(Component.text("  Radius: ").color(NamedTextColor.GRAY)
                .append(Component.text(region.radius() + " blocks").color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Members: ").color(NamedTextColor.GRAY)
                .append(Component.text(region.members().size()).color(NamedTextColor.WHITE)));

        player.sendMessage(Component.text("  Building: ").color(NamedTextColor.GRAY)
                .append(Component.text(region.buildingAccess().name().toLowerCase())
                        .color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Containers: ").color(NamedTextColor.GRAY)
                .append(Component.text(region.containerAccess().name().toLowerCase())
                        .color(NamedTextColor.WHITE)));
        player.sendMessage(Component.text("  Doors: ").color(NamedTextColor.GRAY)
                .append(Component.text(region.doorAccess().name().toLowerCase())
                        .color(NamedTextColor.WHITE)));

        if (manager.isOrphaned(region.id())) {
            player.sendMessage(Component.text("  ").append(
                    Component.text("WARNING: Lodestone missing!").color(NamedTextColor.RED)));
        }

        return Completable.complete();
    }

    private Completable handleList(Player player) {
        List<Region> owned = manager.getOwnedRegions(player.getUniqueId());
        List<Region> member = manager.getMemberRegions(player.getUniqueId());

        if (owned.isEmpty() && member.isEmpty()) {
            Messages.info(player, "You don't have any protected regions.");
            Messages.info(player, "Place a lodestone to protect an area.");
            return Completable.complete();
        }

        var limit = RegionLimitResolver.resolve(player, manager.getConfig());
        String limitStr = limit.isEmpty() ? "unlimited" : String.valueOf(limit.getAsInt());

        Messages.send(player, Component.text("Your Regions (" + owned.size() + "/" + limitStr + "):")
                .color(NamedTextColor.GOLD));

        for (Region region : owned) {
            Component line = Component.text("  \u2022 ").color(NamedTextColor.GRAY)
                    .append(Component.text(region.name()).color(NamedTextColor.WHITE))
                    .append(Component.text(" (" + region.radius() + " blocks)")
                            .color(NamedTextColor.DARK_GRAY));

            if (manager.isOrphaned(region.id())) {
                line = line.append(Component.text(" [ORPHANED]").color(NamedTextColor.RED));
            }

            player.sendMessage(line);
        }

        if (!member.isEmpty()) {
            player.sendMessage(Component.text("Shared with you:").color(NamedTextColor.GOLD));
            for (Region region : member) {
                player.sendMessage(Component.text("  \u2022 ").color(NamedTextColor.GRAY)
                        .append(Component.text(region.name()).color(NamedTextColor.WHITE))
                        .append(Component.text(" (" + region.ownerDisplayName() + ")")
                                .color(NamedTextColor.DARK_GRAY)));
            }
        }

        return Completable.complete();
    }

    private Completable handleTrust(Player player, String[] args) {
        if (args.length == 0) {
            Messages.error(player, "Usage: /protection trust <player>");
            return Completable.complete();
        }

        Region region = manager.getRegionAt(player.getLocation());
        if (region == null) {
            Messages.error(player, "You must be standing in a region you own.");
            return Completable.complete();
        }

        if (!region.isOwner(player.getUniqueId())) {
            Messages.error(player, "Only the owner can trust players.");
            return Completable.complete();
        }

        if (manager.isOrphaned(region.id())) {
            Messages.error(player, "Repair this region first (lodestone missing).");
            return Completable.complete();
        }

        String targetName = args[0];
        return playerResolver.resolvePlayerId(targetName)
                .observeOn(plugin.mainScheduler())
                .switchIfEmpty(Maybe.defer(() -> {
                    Messages.error(player, "Player '" + targetName + "' not found.");
                    return Maybe.empty();
                }))
                .flatMapCompletable(targetId -> {
                    if (targetId.equals(player.getUniqueId())) {
                        Messages.error(player, "You can't trust yourself.");
                        return Completable.complete();
                    }

                    if (region.isMember(targetId)) {
                        Messages.error(player, targetName + " is already trusted.");
                        return Completable.complete();
                    }

                    return manager.addMember(region.id(), targetId)
                            .observeOn(plugin.mainScheduler())
                            .doOnSuccess(added -> {
                                if (added) {
                                    Messages.success(player, "Added " + targetName +
                                            " to \"" + region.name() + "\".");
                                }
                            })
                            .ignoreElement();
                })
                .onErrorComplete();
    }

    private Completable handleUntrust(Player player, String[] args) {
        if (args.length == 0) {
            Messages.error(player, "Usage: /protection untrust <player>");
            return Completable.complete();
        }

        Region region = manager.getRegionAt(player.getLocation());
        if (region == null) {
            Messages.error(player, "You must be standing in a region you own.");
            return Completable.complete();
        }

        if (!region.isOwner(player.getUniqueId())) {
            Messages.error(player, "Only the owner can untrust players.");
            return Completable.complete();
        }

        String targetName = args[0];
        return playerResolver.resolvePlayerId(targetName)
                .observeOn(plugin.mainScheduler())
                .switchIfEmpty(Maybe.defer(() -> {
                    Messages.error(player, "Player '" + targetName + "' not found.");
                    return Maybe.empty();
                }))
                .flatMapCompletable(targetId -> {
                    if (!region.isMember(targetId)) {
                        Messages.error(player, targetName + " is not trusted.");
                        return Completable.complete();
                    }

                    return manager.removeMember(region.id(), targetId)
                            .observeOn(plugin.mainScheduler())
                            .doOnSuccess(removed -> {
                                if (removed) {
                                    Messages.success(player, "Removed " + targetName +
                                            " from \"" + region.name() + "\".");
                                }
                            })
                            .ignoreElement();
                })
                .onErrorComplete();
    }

    private Completable handleSettings(Player player) {
        Region region = manager.getRegionAt(player.getLocation());
        if (region == null) {
            Messages.error(player, "You must be standing in a region you own.");
            return Completable.complete();
        }

        if (!region.isOwner(player.getUniqueId())) {
            Messages.error(player, "Only the owner can change settings.");
            return Completable.complete();
        }

        Messages.send(player, Component.text("\"" + region.name() + "\" Access Settings:")
                .color(NamedTextColor.GOLD));

        // Building
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  Building: ").color(NamedTextColor.GRAY)
                .append(buildAccessOptions("building", region.buildingAccess())));
        player.sendMessage(Component.text("    Who can place and break blocks").color(NamedTextColor.DARK_GRAY));

        // Containers
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  Containers: ").color(NamedTextColor.GRAY)
                .append(buildAccessOptions("containers", region.containerAccess())));
        player.sendMessage(Component.text("    Who can access chests, barrels, etc.").color(NamedTextColor.DARK_GRAY));

        // Doors
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  Doors: ").color(NamedTextColor.GRAY)
                .append(buildAccessOptions("doors", region.doorAccess())));
        player.sendMessage(Component.text("    Who can open doors and fence gates").color(NamedTextColor.DARK_GRAY));

        return Completable.complete();
    }

    private Component buildAccessOptions(String setting, AccessLevel current) {
        Component result = Component.empty();

        for (AccessLevel level : AccessLevel.values()) {
            if (result != Component.empty()) {
                result = result.append(Component.text(" "));
            }

            boolean isCurrent = level == current;
            String label = "[" + level.name().charAt(0) + level.name().substring(1).toLowerCase() + "]";

            Component option = Component.text(label)
                    .color(isCurrent ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                    .hoverEvent(HoverEvent.showText(Component.text(
                            isCurrent ? "Currently selected" : "Click to change")));

            if (!isCurrent) {
                option = option.clickEvent(ClickEvent.runCommand(
                        "/protection access " + setting + " " + level.name().toLowerCase()));
            }

            result = result.append(option);
        }

        return result;
    }

    private Completable handleAccess(Player player, String[] args) {
        if (args.length < 2) {
            Messages.error(player, "Usage: /protection access <building|containers|doors> <everybody|members|owner>");
            return Completable.complete();
        }

        Region region = manager.getRegionAt(player.getLocation());
        if (region == null) {
            Messages.error(player, "You must be standing in a region you own.");
            return Completable.complete();
        }

        if (!region.isOwner(player.getUniqueId())) {
            Messages.error(player, "Only the owner can change access settings.");
            return Completable.complete();
        }

        String setting = args[0].toLowerCase();
        String levelStr = args[1].toUpperCase();

        AccessLevel level;
        try {
            level = AccessLevel.valueOf(levelStr);
        } catch (IllegalArgumentException e) {
            Messages.error(player, "Invalid access level. Use: everybody, members, or owner.");
            return Completable.complete();
        }

        AccessLevel building = region.buildingAccess();
        AccessLevel containers = region.containerAccess();
        AccessLevel doors = region.doorAccess();

        switch (setting) {
            case "building" -> building = level;
            case "containers" -> containers = level;
            case "doors" -> doors = level;
            default -> {
                Messages.error(player, "Invalid setting. Use: building, containers, or doors.");
                return Completable.complete();
            }
        }

        return manager.updateAccess(region.id(), building, containers, doors)
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> Messages.success(player, "Updated " + setting + " access to " +
                        level.name().toLowerCase() + "."))
                .doOnError(err -> Messages.error(player, "Failed to update access."))
                .onErrorComplete();
    }

    private Completable handleRadius(Player player, String[] args) {
        if (args.length < 2) {
            Messages.error(player, "Usage: /protection radius <name> <blocks>");
            return Completable.complete();
        }

        String name = args[0];
        int newRadius;
        try {
            newRadius = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            Messages.error(player, "Invalid radius. Must be a number.");
            return Completable.complete();
        }

        Optional<Region> regionOpt = manager.getRegion(player.getUniqueId(), name);
        if (regionOpt.isEmpty()) {
            Messages.error(player, "You don't have a region named \"" + name + "\".");
            return Completable.complete();
        }

        Region region = regionOpt.get();

        // Check bounds
        int minRadius = manager.getConfig().minRadius();
        int maxRadius = RadiusLimitResolver.resolve(player, manager.getConfig());

        if (newRadius < minRadius) {
            Messages.error(player, "Minimum radius is " + minRadius + " blocks.");
            return Completable.complete();
        }

        if (newRadius > maxRadius) {
            Messages.error(player, "Maximum radius is " + maxRadius + " blocks.");
            return Completable.complete();
        }

        // Check for intersection with new radius - check each anchor
        Region intersecting = null;
        for (Anchor anchor : region.anchors()) {
            intersecting = manager.findIntersecting(
                    region.worldId(), anchor.x(), anchor.z(),
                    newRadius, region.id()
            );
            if (intersecting != null) break;
        }

        if (intersecting != null) {
            Messages.error(player, "New radius would overlap with \"" + intersecting.name() + "\".");
            return Completable.complete();
        }

        return manager.updateRadius(region.id(), newRadius)
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> Messages.success(player, "Updated radius to " + newRadius + " blocks."))
                .doOnError(err -> Messages.error(player, "Failed to update radius."))
                .onErrorComplete();
    }

    private Completable handleRepair(Player player, String[] args) {
        String name = args.length > 0 ? String.join(" ", args) : null;

        Region region;
        if (name != null) {
            Optional<Region> regionOpt = manager.getRegion(player.getUniqueId(), name);
            if (regionOpt.isEmpty()) {
                Messages.error(player, "You don't have a region named \"" + name + "\".");
                return Completable.complete();
            }
            region = regionOpt.get();
        } else {
            region = manager.getRegionAt(player.getLocation());
            if (region == null) {
                Messages.error(player, "You must be standing in a region or specify a name.");
                return Completable.complete();
            }
            if (!region.isOwner(player.getUniqueId())) {
                Messages.error(player, "You don't own this region.");
                return Completable.complete();
            }
        }

        if (!manager.isOrphaned(region.id())) {
            Messages.info(player, "This region's lodestone is intact.");
            return Completable.complete();
        }

        // Check if player has a lodestone in inventory
        if (!player.getInventory().contains(Material.LODESTONE)) {
            Messages.error(player, "You need a lodestone in your inventory to repair.");
            return Completable.complete();
        }

        // Place the lodestone
        Location center = region.getPrimaryLocation();
        if (center == null) {
            Messages.error(player, "Cannot repair - world not loaded.");
            return Completable.complete();
        }

        // Remove lodestone from inventory
        player.getInventory().removeItem(new ItemStack(Material.LODESTONE, 1));

        // Place the lodestone block
        center.getBlock().setType(Material.LODESTONE);

        // Clear orphaned status
        manager.markRepaired(region.id());

        Messages.success(player, "Repaired region \"" + region.name() + "\".");
        return Completable.complete();
    }

    private Completable handleVisualize(Player player) {
        boolean enabled = visualizer.toggle(player.getUniqueId());
        if (enabled) {
            Messages.success(player, "Region borders are now visible.");
        } else {
            Messages.info(player, "Region borders hidden.");
        }
        return Completable.complete();
    }

    private Completable handleHelp(Player player) {
        Messages.send(player, Component.text("Protection Commands:").color(NamedTextColor.GOLD));

        helpLine(player, "/protection", "List your regions");
        helpLine(player, "/protection claim [name]", "Claim lodestone you're looking at");
        helpLine(player, "/protection unclaim <name>", "Remove protection");
        helpLine(player, "/protection info", "Info for region you're standing in");
        helpLine(player, "/protection trust <player>", "Add member to current region");
        helpLine(player, "/protection untrust <player>", "Remove member");
        helpLine(player, "/protection settings", "Show access settings");
        helpLine(player, "/protection access <setting> <level>", "Change access level");
        helpLine(player, "/protection radius <name> <blocks>", "Adjust region radius");
        helpLine(player, "/protection repair [name]", "Repair orphaned region");
        helpLine(player, "/protection visualize", "Toggle border particles");

        if (player.hasPermission("smp.protection.admin")) {
            player.sendMessage(Component.empty());
            player.sendMessage(Component.text("Admin Commands:").color(NamedTextColor.RED));
            helpLine(player, "/protection bypass", "Toggle protection bypass");
            helpLine(player, "/protection cleanup", "List/delete orphaned regions");
            helpLine(player, "/protection forceunclaim <player> <name>", "Force delete region");
            helpLine(player, "/protection forcerepair <player> <name>", "Repair without lodestone");
        }

        return Completable.complete();
    }

    private void helpLine(Player player, String command, String description) {
        player.sendMessage(Component.text("  " + command).color(NamedTextColor.YELLOW)
                .append(Component.text(" - " + description).color(NamedTextColor.GRAY)));
    }

    private Completable handleCancel(Player player) {
        lodestoneListener.cancelPendingClaim(player);
        return Completable.complete();
    }

    private Completable handleExpand(Player player) {
        if (!lodestoneListener.hasPendingExpansion(player.getUniqueId())) {
            Messages.error(player, "No pending expansion. Place a lodestone near an existing region first.");
            return Completable.complete();
        }

        lodestoneListener.processExpand(player);
        return Completable.complete();
    }

    private Completable handleAnchors(Player player, String[] args) {
        // Get region - either by name or current location
        Region region;
        if (args.length > 0) {
            String name = String.join(" ", args);
            Optional<Region> regionOpt = manager.getRegion(player.getUniqueId(), name);
            if (regionOpt.isEmpty()) {
                Messages.error(player, "You don't have a region named \"" + name + "\".");
                return Completable.complete();
            }
            region = regionOpt.get();
        } else {
            region = manager.getRegionAt(player.getLocation());
            if (region == null) {
                Messages.error(player, "You must be standing in a region or specify a name.");
                return Completable.complete();
            }
            if (!region.isOwner(player.getUniqueId())) {
                Messages.error(player, "You don't own this region.");
                return Completable.complete();
            }
        }

        // List anchors
        Messages.send(player, Component.text("Anchors for \"" + region.name() + "\":")
                .color(NamedTextColor.GOLD));

        int i = 1;
        for (Anchor anchor : region.anchors()) {
            String coords = anchor.x() + ", " + anchor.y() + ", " + anchor.z();
            player.sendMessage(Component.text("  " + i + ". ")
                    .color(NamedTextColor.GRAY)
                    .append(Component.text(coords)
                            .color(NamedTextColor.WHITE)));
            i++;
        }

        player.sendMessage(Component.text("  Radius: " + region.radius() + " blocks per anchor")
                .color(NamedTextColor.GRAY));

        return Completable.complete();
    }

    private Completable handleBypass(Player player) {
        if (!player.hasPermission("smp.protection.admin")) {
            Messages.error(player, "You don't have permission to use bypass mode.");
            return Completable.complete();
        }

        boolean enabled = protectionListener.toggleBypass(player.getUniqueId());
        if (enabled) {
            Messages.warn(player, "Protection bypass ENABLED. You can now modify all regions.");
        } else {
            Messages.success(player, "Protection bypass disabled.");
        }
        return Completable.complete();
    }

    private Completable handleCleanup(Player player) {
        if (!player.hasPermission("smp.protection.admin")) {
            Messages.error(player, "You don't have permission to cleanup regions.");
            return Completable.complete();
        }

        var orphanedIds = manager.getCache().getOrphanedRegions();
        if (orphanedIds.isEmpty()) {
            Messages.info(player, "No orphaned regions found.");
            return Completable.complete();
        }

        Messages.send(player, Component.text("Orphaned Regions (" + orphanedIds.size() + "):")
                .color(NamedTextColor.GOLD));

        for (UUID regionId : orphanedIds) {
            manager.getRegion(regionId).ifPresent(region -> {
                player.sendMessage(Component.text("  \u2022 ").color(NamedTextColor.GRAY)
                        .append(Component.text(region.name()).color(NamedTextColor.WHITE))
                        .append(Component.text(" (" + region.ownerDisplayName() + ")")
                                .color(NamedTextColor.DARK_GRAY))
                        .append(Component.text(" [Delete]").color(NamedTextColor.RED)
                                .clickEvent(ClickEvent.runCommand(
                                        "/protection forceunclaim " + region.ownerDisplayName() +
                                                " " + region.name()))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to delete")))));
            });
        }

        return Completable.complete();
    }

    private Completable handleForceUnclaim(Player player, String[] args) {
        if (!player.hasPermission("smp.protection.admin")) {
            Messages.error(player, "You don't have permission to force unclaim.");
            return Completable.complete();
        }

        if (args.length < 2) {
            Messages.error(player, "Usage: /protection forceunclaim <player> <name>");
            return Completable.complete();
        }

        String ownerName = args[0];
        String regionName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        return playerResolver.resolvePlayerId(ownerName)
                .observeOn(plugin.mainScheduler())
                .switchIfEmpty(Maybe.defer(() -> {
                    Messages.error(player, "Player '" + ownerName + "' not found.");
                    return Maybe.empty();
                }))
                .flatMapCompletable(ownerId -> {
                    Optional<Region> regionOpt = manager.getRegion(ownerId, regionName);
                    if (regionOpt.isEmpty()) {
                        Messages.error(player, "Region not found.");
                        return Completable.complete();
                    }

                    Region region = regionOpt.get();
                    return manager.deleteRegion(region.id())
                            .observeOn(plugin.mainScheduler())
                            .doOnSuccess(deleted -> {
                                if (deleted) {
                                    Messages.success(player, "Force deleted \"" + region.name() +
                                            "\" owned by " + ownerName + ".");
                                }
                            })
                            .ignoreElement();
                })
                .onErrorComplete();
    }

    private Completable handleForceRepair(Player player, String[] args) {
        if (!player.hasPermission("smp.protection.admin")) {
            Messages.error(player, "You don't have permission to force repair.");
            return Completable.complete();
        }

        if (args.length < 2) {
            Messages.error(player, "Usage: /protection forcerepair <player> <name>");
            return Completable.complete();
        }

        String ownerName = args[0];
        String regionName = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        return playerResolver.resolvePlayerId(ownerName)
                .observeOn(plugin.mainScheduler())
                .switchIfEmpty(Maybe.defer(() -> {
                    Messages.error(player, "Player '" + ownerName + "' not found.");
                    return Maybe.empty();
                }))
                .flatMapCompletable(ownerId -> {
                    Optional<Region> regionOpt = manager.getRegion(ownerId, regionName);
                    if (regionOpt.isEmpty()) {
                        Messages.error(player, "Region not found.");
                        return Completable.complete();
                    }

                    Region region = regionOpt.get();

                    if (!manager.isOrphaned(region.id())) {
                        Messages.info(player, "This region's lodestone is intact.");
                        return Completable.complete();
                    }

                    Location center = region.getPrimaryLocation();
                    if (center == null) {
                        Messages.error(player, "Cannot repair - world not loaded.");
                        return Completable.complete();
                    }

                    center.getBlock().setType(Material.LODESTONE);
                    manager.markRepaired(region.id());

                    Messages.success(player, "Force repaired \"" + region.name() +
                            "\" owned by " + ownerName + ".");
                    return Completable.complete();
                })
                .onErrorComplete();
    }

    @Override
    public Maybe<List<AsyncTabCompleteEvent.Completion>> tabComplete(SiqiJoeyPlugin plugin,
                                                                     CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return Maybe.empty();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            completions.addAll(SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(partial))
                    .toList());
        } else if (args.length == 2) {
            String subcommand = args[0].toLowerCase();
            String partial = args[1].toLowerCase();

            switch (subcommand) {
                case "unclaim", "radius", "repair" -> {
                    // Complete with owned region names
                    completions.addAll(manager.getOwnedRegions(player.getUniqueId()).stream()
                            .map(Region::name)
                            .filter(n -> n.toLowerCase().startsWith(partial))
                            .toList());
                }
                case "access" -> {
                    completions.addAll(ACCESS_SETTINGS.stream()
                            .filter(s -> s.startsWith(partial))
                            .toList());
                }
                case "trust", "untrust", "forceunclaim", "forcerepair" -> {
                    // Complete with online player names
                    completions.addAll(plugin.getServer().getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(partial))
                            .toList());
                }
            }
        } else if (args.length == 3) {
            String subcommand = args[0].toLowerCase();
            String partial = args[2].toLowerCase();

            if (subcommand.equals("access")) {
                completions.addAll(ACCESS_LEVELS.stream()
                        .filter(s -> s.startsWith(partial))
                        .toList());
            } else if (subcommand.equals("forceunclaim") || subcommand.equals("forcerepair")) {
                // Try to resolve player and show their regions
                // For simplicity, just return empty for now
            }
        }

        return Maybe.just(completions.stream()
                .map(AsyncTabCompleteEvent.Completion::completion)
                .collect(Collectors.toList()));
    }
}
