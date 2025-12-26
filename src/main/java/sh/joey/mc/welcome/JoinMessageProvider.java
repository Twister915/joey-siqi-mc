package sh.joey.mc.welcome;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.messages.MessageGenerator;
import sh.joey.mc.nickname.NicknameManager;

/**
 * Sends a themed welcome message to players when they join the server.
 * Uses the centralized MessageGenerator for rich message variety.
 * <p>
 * Uses display name (nickname if set, otherwise username) when NicknameManager is provided.
 */
public final class JoinMessageProvider implements Disposable {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.GOLD)
            .append(Component.text("\u2605").color(NamedTextColor.YELLOW)) // ★
            .append(Component.text("] ").color(NamedTextColor.GOLD));

    private final CompositeDisposable disposables = new CompositeDisposable();
    @Nullable
    private final NicknameManager nicknameManager;

    public JoinMessageProvider(SiqiJoeyPlugin plugin) {
        this(plugin, null);
    }

    public JoinMessageProvider(SiqiJoeyPlugin plugin, @Nullable NicknameManager nicknameManager) {
        this.nicknameManager = nicknameManager;

        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .subscribe(event -> {
                    Player player = event.getPlayer();
                    String displayName = getDisplayName(player);
                    String message = MessageGenerator.generateJoinMessage(player, displayName);
                    player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.WHITE)));
                }));
    }

    private String getDisplayName(Player player) {
        return nicknameManager != null
                ? nicknameManager.getDisplayName(player)
                : player.getName();
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
