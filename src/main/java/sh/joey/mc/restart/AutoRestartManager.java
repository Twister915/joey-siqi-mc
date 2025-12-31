package sh.joey.mc.restart;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.concurrent.TimeUnit;

/**
 * Automatically restarts the server every 24 hours, waiting for all players to leave first.
 */
public final class AutoRestartManager implements Disposable {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Server").color(NamedTextColor.GOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private static final long RESTART_INTERVAL_HOURS = 24;

    private final SiqiJoeyPlugin plugin;
    private final CompositeDisposable disposables = new CompositeDisposable();
    private volatile boolean restartPending = false;

    public AutoRestartManager(SiqiJoeyPlugin plugin) {
        this.plugin = plugin;

        // Schedule restart check every 24 hours
        disposables.add(plugin.timer(RESTART_INTERVAL_HOURS, TimeUnit.HOURS)
                .subscribe(tick -> onRestartTimerFired()));

        // Watch for players leaving
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(this::onPlayerQuit));

        plugin.getLogger().info("Auto-restart scheduled for " + RESTART_INTERVAL_HOURS + " hours from now");
    }

    private void onRestartTimerFired() {
        restartPending = true;
        plugin.getLogger().info("Restart timer fired - waiting for server to be empty");

        if (plugin.getServer().getOnlinePlayers().isEmpty()) {
            performRestart();
        } else {
            // Notify players that restart is pending
            plugin.getServer().broadcast(PREFIX.append(
                    Component.text("A restart is scheduled. The server will restart when empty.")
                            .color(NamedTextColor.YELLOW)));
        }
    }

    private void onPlayerQuit(PlayerQuitEvent event) {
        if (!restartPending) {
            return;
        }

        // Check if server will be empty after this player leaves
        // (getOnlinePlayers still includes the quitting player at this point)
        if (plugin.getServer().getOnlinePlayers().size() <= 1) {
            // Schedule restart for next tick to ensure quit processing completes
            plugin.timer(1, TimeUnit.SECONDS).subscribe(tick -> performRestart());
        }
    }

    private void performRestart() {
        plugin.getLogger().info("Server is empty - performing scheduled restart");
        plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), "stop");
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
