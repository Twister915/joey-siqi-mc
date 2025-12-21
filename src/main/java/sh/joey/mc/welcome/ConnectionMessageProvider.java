package sh.joey.mc.welcome;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

/**
 * Replaces vanilla join/leave messages with styled versions:
 * - Join: [+] PlayerName (green plus)
 * - Leave: [-] PlayerName (red minus)
 */
public final class ConnectionMessageProvider implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();

    public ConnectionMessageProvider(SiqiJoeyPlugin plugin) {
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> {
                    String name = event.getPlayer().getName();
                    event.joinMessage(formatJoin(name));
                }));

        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> {
                    String name = event.getPlayer().getName();
                    event.quitMessage(formatLeave(name));
                }));
    }

    private Component formatJoin(String playerName) {
        return Component.text("[").color(NamedTextColor.DARK_GRAY)
                .append(Component.text("+").color(NamedTextColor.GREEN))
                .append(Component.text("] ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(playerName).color(NamedTextColor.GRAY));
    }

    private Component formatLeave(String playerName) {
        return Component.text("[").color(NamedTextColor.DARK_GRAY)
                .append(Component.text("-").color(NamedTextColor.RED))
                .append(Component.text("] ").color(NamedTextColor.DARK_GRAY))
                .append(Component.text(playerName).color(NamedTextColor.GRAY));
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
