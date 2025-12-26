package sh.joey.mc.welcome;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.nickname.NicknameManager;
import sh.joey.mc.permissions.DisplayManager;

/**
 * Customizes chat message format to match the plugin's style.
 * Format: [prefix]DisplayName[suffix]: message
 * <p>
 * When a DisplayManager is provided, prefixes and suffixes from the
 * permissions system are included in chat messages.
 * <p>
 * When a NicknameManager is provided, uses the player's display name
 * (nickname if set, otherwise username).
 */
public final class ChatMessageProvider implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable
    private final DisplayManager displayManager;
    @Nullable
    private final NicknameManager nicknameManager;

    public ChatMessageProvider(SiqiJoeyPlugin plugin) {
        this(plugin, null, null);
    }

    public ChatMessageProvider(SiqiJoeyPlugin plugin, @Nullable DisplayManager displayManager,
                               @Nullable NicknameManager nicknameManager) {
        this.displayManager = displayManager;
        this.nicknameManager = nicknameManager;

        disposables.add(plugin.watchEvent(AsyncChatEvent.class)
                .subscribe(event -> {
                    // Use display name (nickname if set, otherwise username)
                    String playerName = nicknameManager != null
                            ? nicknameManager.getDisplayName(event.getPlayer())
                            : event.getPlayer().getName();
                    Component message = event.message();

                    // Get prefix/suffix/color from DisplayManager if available
                    Component prefix = displayManager != null
                            ? displayManager.getChatPrefix(event.getPlayer())
                            : Component.empty();
                    Component suffix = displayManager != null
                            ? displayManager.getChatSuffix(event.getPlayer())
                            : Component.empty();
                    TextColor nameColor = displayManager != null
                            ? displayManager.getNameColor(event.getPlayer())
                            : DisplayManager.DEFAULT_NAME_COLOR;

                    Component formatted = prefix
                            .append(Component.text(playerName).color(nameColor))
                            .append(suffix)
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
