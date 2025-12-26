package sh.joey.mc.tips;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.utility.MapConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Sends tips about plugin commands and features to individual players.
 * Tips start 5 seconds after join and repeat every 5 minutes.
 */
public final class TipsProvider implements Disposable {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Tip").color(NamedTextColor.GREEN))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private static final long INITIAL_DELAY_SECONDS = 5;
    private static final long INTERVAL_MINUTES = 5;

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final Map<UUID, Disposable> playerTimers = new HashMap<>();
    private final List<Component> tips;
    private final List<Function<Player, Component>> dynamicTips;
    private final Random random = new Random();
    private final SiqiJoeyPlugin plugin;
    private final MapConfig mapConfig;
    private final boolean enabled;

    public TipsProvider(SiqiJoeyPlugin plugin, TipsConfig config, MapConfig mapConfig) {
        this.plugin = plugin;
        this.mapConfig = mapConfig;
        this.enabled = config.enabled();
        this.tips = buildTips();
        this.dynamicTips = buildDynamicTips();

        if (!enabled) {
            return;
        }

        // Start tips for already-online players
        plugin.getServer().getOnlinePlayers().forEach(this::startTipsForPlayer);

        // Watch for joins/quits
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> startTipsForPlayer(event.getPlayer())));

        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> stopTipsForPlayer(event.getPlayer().getUniqueId())));
    }

    private void startTipsForPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        stopTipsForPlayer(playerId); // Clean up any existing timer

        Disposable timer = plugin.interval(INITIAL_DELAY_SECONDS, INTERVAL_MINUTES * 60, TimeUnit.SECONDS)
                .subscribe(tick -> sendTip(playerId));

        playerTimers.put(playerId, timer);
    }

    private void stopTipsForPlayer(UUID playerId) {
        Disposable timer = playerTimers.remove(playerId);
        if (timer != null) {
            timer.dispose();
        }
    }

    private void sendTip(UUID playerId) {
        Player player = plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            stopTipsForPlayer(playerId);
            return;
        }

        int totalTips = tips.size() + dynamicTips.size();
        int index = random.nextInt(totalTips);

        Component tip;
        if (index < tips.size()) {
            tip = tips.get(index);
        } else {
            tip = dynamicTips.get(index - tips.size()).apply(player);
        }

        player.sendMessage(PREFIX.append(tip));
    }

    private List<Component> buildTips() {
        List<Component> tipList = new ArrayList<>();

        // === WORLD NAVIGATION TIPS ===
        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/survival"))
                .append(Component.text(" to teleport to the survival world!", NamedTextColor.GRAY)));

        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/creative"))
                .append(Component.text(" to teleport to the creative world!", NamedTextColor.GRAY)));

        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/superflat"))
                .append(Component.text(" to teleport to the superflat building world!", NamedTextColor.GRAY)));

        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/world"))
                .append(Component.text(" to see all available worlds and teleport between them.", NamedTextColor.GRAY)));

        tipList.add(Component.text("Each world has its own inventory! Your survival items stay separate from creative.", NamedTextColor.GRAY));

        tipList.add(Component.text("When you return to a world, you'll appear where you last were in that world.", NamedTextColor.GRAY));

        // === HOME TIPS ===
        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/home set"))
                .append(Component.text(" to save your current location as home.", NamedTextColor.GRAY)));

        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/home"))
                .append(Component.text(" to teleport to your saved home.", NamedTextColor.GRAY)));

        tipList.add(Component.text("You can have multiple homes! Try ", NamedTextColor.GRAY)
                .append(cmd("/home set base"))
                .append(Component.text(" or ", NamedTextColor.GRAY))
                .append(cmd("/home set farm"))
                .append(Component.text(".", NamedTextColor.GRAY)));

        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/home list"))
                .append(Component.text(" to see all your saved homes.", NamedTextColor.GRAY)));

        tipList.add(Component.text("Share a home with a friend using ", NamedTextColor.GRAY)
                .append(cmd("/home share <name> <player>"))
                .append(Component.text("!", NamedTextColor.GRAY)));

        tipList.add(Component.text("Sleeping in a bed for the first time automatically saves it as your 'home'.", NamedTextColor.GRAY));

        // === TELEPORT TIPS ===
        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/tp <player>"))
                .append(Component.text(" to request to teleport to someone.", NamedTextColor.GRAY)));

        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/tphere <player>"))
                .append(Component.text(" to request that someone teleport to you.", NamedTextColor.GRAY)));

        tipList.add(Component.text("Teleport requests can be accepted with ", NamedTextColor.GRAY)
                .append(cmd("/accept"))
                .append(Component.text(" or declined with ", NamedTextColor.GRAY))
                .append(cmd("/decline"))
                .append(Component.text(".", NamedTextColor.GRAY)));

        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/back"))
                .append(Component.text(" to return to where you died or your last teleport location.", NamedTextColor.GRAY)));

        tipList.add(Component.text("Don't move during teleport warmup, or it will be cancelled!", NamedTextColor.GRAY));

        // === WARP & SPAWN TIPS ===
        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/warp"))
                .append(Component.text(" to see available warp points set by admins.", NamedTextColor.GRAY)));

        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/spawn"))
                .append(Component.text(" to teleport to the world's spawn point.", NamedTextColor.GRAY)));

        // === UTILITY TIPS ===
        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/list"))
                .append(Component.text(" to see who's online.", NamedTextColor.GRAY)));

        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/ontime"))
                .append(Component.text(" to see how long you've played on the server.", NamedTextColor.GRAY)));

        // === RESOURCE PACK TIPS ===
        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/rp"))
                .append(Component.text(" to browse and select a custom resource pack!", NamedTextColor.GRAY)));

        // === MAP TIPS ===
        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/map"))
                .append(Component.text(" to get a link to our web map!", NamedTextColor.GRAY)));

        // === PRIVATE MESSAGING TIPS ===
        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/msg <player> <message>"))
                .append(Component.text(" to send a private message!", NamedTextColor.GRAY)));

        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/r <message>"))
                .append(Component.text(" to quickly reply to the last message you received.", NamedTextColor.GRAY)));

        tipList.add(Component.text("Offline players can receive your messages when they join!", NamedTextColor.GRAY));

        // === NICKNAME TIPS ===
        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/nick <name>"))
                .append(Component.text(" to set a custom display name!", NamedTextColor.GRAY)));

        tipList.add(Component.text("Your nickname appears in chat, the tab list, and death messages.", NamedTextColor.GRAY));

        tipList.add(Component.text("Use ", NamedTextColor.GRAY)
                .append(cmd("/nick clear"))
                .append(Component.text(" to remove your nickname.", NamedTextColor.GRAY)));

        // === GENERAL TIPS ===
        tipList.add(Component.text("The boss bar at the top shows the time of day and other useful info.", NamedTextColor.GRAY));

        tipList.add(Component.text("Hold a lodestone compass to see the direction and distance to your target.", NamedTextColor.GRAY));

        tipList.add(Component.text("Entering a new biome? Watch for the biome name in the boss bar!", NamedTextColor.GRAY));

        return tipList;
    }

    private List<Function<Player, Component>> buildDynamicTips() {
        List<Function<Player, Component>> tipList = new ArrayList<>();

        // Dynamic map tip - links directly to player's current position
        tipList.add(player -> {
            String url = mapConfig.buildUrl(player.getLocation());
            return Component.text("View your location on the web map! ", NamedTextColor.GRAY)
                    .append(Component.text("[Open Map]")
                            .color(NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.openUrl(url))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("Click to open in browser").color(NamedTextColor.GRAY))));
        });

        return tipList;
    }

    /**
     * Creates a clickable command component.
     */
    private Component cmd(String command) {
        String displayText = command.startsWith("/") ? command : "/" + command;
        String runCommand = command.startsWith("/") ? command : "/" + command;

        return Component.text(displayText)
                .color(NamedTextColor.AQUA)
                .clickEvent(ClickEvent.suggestCommand(runCommand))
                .hoverEvent(HoverEvent.showText(
                        Component.text("Click to put in chat: ", NamedTextColor.GRAY)
                                .append(Component.text(displayText, NamedTextColor.WHITE))));
    }

    @Override
    public void dispose() {
        disposables.dispose();
        playerTimers.values().forEach(Disposable::dispose);
        playerTimers.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
