package sh.joey.mc.welcome;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.messages.MessageGenerator;

/**
 * Sends a themed welcome message to players when they join the server.
 * Uses the centralized MessageGenerator for rich message variety.
 */
public final class JoinMessageProvider implements Disposable {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.GOLD)
            .append(Component.text("\u2605").color(NamedTextColor.YELLOW)) // ★
            .append(Component.text("] ").color(NamedTextColor.GOLD));

    private final CompositeDisposable disposables = new CompositeDisposable();

    public JoinMessageProvider(SiqiJoeyPlugin plugin) {
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> {
                    Player player = event.getPlayer();
                    String message = MessageGenerator.generateJoinMessage(player);
                    player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.WHITE)));
                }));
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
