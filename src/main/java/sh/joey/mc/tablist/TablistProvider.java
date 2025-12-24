package sh.joey.mc.tablist;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.concurrent.TimeUnit;

/**
 * Manages the player list (tab) header and footer display.
 * Shows server IP, TPS, player count, and player's latency.
 */
public final class TablistProvider implements Disposable {

    private static final String SERVER_IP = "joey.sh";

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;

    public TablistProvider(SiqiJoeyPlugin plugin) {
        this.plugin = plugin;

        // Update for already-online players
        plugin.getServer().getOnlinePlayers().forEach(this::updateTablist);

        // Update on player join
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> updateTablist(event.getPlayer())));

        // Update every second for TPS and ping updates
        disposables.add(plugin.interval(1, TimeUnit.SECONDS)
                .subscribe(tick -> updateAllTablists()));
    }

    private void updateAllTablists() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updateTablist(player);
        }
    }

    private void updateTablist(Player player) {
        Component header = buildHeader();
        Component footer = buildFooter(player);
        player.sendPlayerListHeaderAndFooter(header, footer);
    }

    private Component buildHeader() {
        return Component.empty()
                .append(Component.newline())
                .append(Component.text("✦ ", NamedTextColor.GOLD))
                .append(Component.text(SERVER_IP, NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.text(" ✦", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("Welcome to the server!", NamedTextColor.GRAY))
                .append(Component.newline());
    }

    private Component buildFooter(Player player) {
        double tps = Bukkit.getServer().getTPS()[0]; // 1-minute average
        int playerCount = Bukkit.getOnlinePlayers().size();
        int ping = player.getPing();

        return Component.empty()
                .append(Component.newline())
                .append(buildTpsComponent(tps))
                .append(Component.text(" │ ", NamedTextColor.DARK_GRAY))
                .append(buildPlayerCountComponent(playerCount))
                .append(Component.text(" │ ", NamedTextColor.DARK_GRAY))
                .append(buildPingComponent(ping))
                .append(Component.newline());
    }

    private Component buildTpsComponent(double tps) {
        // Cap display at 20 (TPS can go slightly over)
        double displayTps = Math.min(tps, 20.0);
        NamedTextColor color = getTpsColor(tps);

        return Component.text("TPS: ", NamedTextColor.GRAY)
                .append(Component.text(String.format("%.1f", displayTps), color));
    }

    private Component buildPlayerCountComponent(int count) {
        String label = count == 1 ? "player" : "players";
        return Component.text(count + " " + label, NamedTextColor.AQUA);
    }

    private Component buildPingComponent(int ping) {
        NamedTextColor color = getPingColor(ping);
        return Component.text("Ping: ", NamedTextColor.GRAY)
                .append(Component.text(ping + "ms", color));
    }

    private NamedTextColor getTpsColor(double tps) {
        if (tps >= 19.0) {
            return NamedTextColor.GREEN;
        } else if (tps >= 15.0) {
            return NamedTextColor.YELLOW;
        } else {
            return NamedTextColor.RED;
        }
    }

    private NamedTextColor getPingColor(int ping) {
        if (ping <= 50) {
            return NamedTextColor.GREEN;
        } else if (ping <= 100) {
            return NamedTextColor.YELLOW;
        } else if (ping <= 200) {
            return NamedTextColor.GOLD;
        } else {
            return NamedTextColor.RED;
        }
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
