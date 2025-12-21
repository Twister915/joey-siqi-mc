package sh.joey.mc.welcome;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import sh.joey.mc.SiqiJoeyPlugin;

/**
 * Customizes chat message format to match the plugin's style.
 * Format: PlayerName: message (gray name, dark gray colon, white message)
 */
public final class ChatMessageProvider implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();

    public ChatMessageProvider(SiqiJoeyPlugin plugin) {
        disposables.add(plugin.watchEvent(AsyncChatEvent.class)
                .subscribe(event -> {
                    String playerName = event.getPlayer().getName();
                    Component message = event.message();

                    Component formatted = Component.text(playerName).color(NamedTextColor.GRAY)
                            .append(Component.text(": ").color(NamedTextColor.DARK_GRAY))
                            .append(message.color(NamedTextColor.WHITE));

                    event.renderer((source, sourceDisplayName, msg, viewer) -> formatted);
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
