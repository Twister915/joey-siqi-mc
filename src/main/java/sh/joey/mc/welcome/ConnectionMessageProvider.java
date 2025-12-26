package sh.joey.mc.welcome;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.nickname.NicknameManager;

/**
 * Replaces vanilla join/leave messages with styled versions:
 * - Join: [+] DisplayName (green plus)
 * - Leave: [-] DisplayName (red minus)
 * <p>
 * Uses display name (nickname if set, otherwise username) when NicknameManager is provided.
 */
public final class ConnectionMessageProvider implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable
    private final NicknameManager nicknameManager;

    public ConnectionMessageProvider(SiqiJoeyPlugin plugin, @Nullable NicknameManager nicknameManager) {
        this.nicknameManager = nicknameManager;

        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> event.joinMessage(formatJoin(event.getPlayer()))));

        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> event.quitMessage(formatLeave(event.getPlayer()))));
    }

    private Component formatJoin(Player player) {
        return Component.text("[").color(NamedTextColor.DARK_GRAY)
                .append(Component.text("+").color(NamedTextColor.GREEN))
                .append(Component.text("] ").color(NamedTextColor.DARK_GRAY))
                .append(formatPlayerName(player));
    }

    private Component formatLeave(Player player) {
        return Component.text("[").color(NamedTextColor.DARK_GRAY)
                .append(Component.text("-").color(NamedTextColor.RED))
                .append(Component.text("] ").color(NamedTextColor.DARK_GRAY))
                .append(formatPlayerName(player));
    }

    private Component formatPlayerName(Player player) {
        String username = player.getName();
        String displayName = nicknameManager != null
                ? nicknameManager.getDisplayName(player)
                : username;

        Component nameComponent = Component.text(displayName).color(NamedTextColor.GRAY);

        // Add hover tooltip showing real username if player has a nickname
        if (!displayName.equals(username)) {
            nameComponent = nameComponent.hoverEvent(HoverEvent.showText(
                    Component.text(username).color(NamedTextColor.WHITE)));
        }

        return nameComponent;
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
