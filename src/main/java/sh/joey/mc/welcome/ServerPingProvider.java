package sh.joey.mc.welcome;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.World;
import org.bukkit.event.server.ServerListPingEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.messages.MessageGenerator;

/**
 * Provides dynamic MOTD messages when the server is pinged.
 * Uses the centralized MessageGenerator for message variety.
 */
public final class ServerPingProvider implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;

    public ServerPingProvider(SiqiJoeyPlugin plugin) {
        this.plugin = plugin;

        disposables.add(plugin.watchEvent(ServerListPingEvent.class)
                .subscribe(event -> event.motd(generateMotd())));
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    private Component generateMotd() {
        // First line: Server name/branding
        Component line1 = Component.text("Siqi & Joey's Minecraft")
                .color(NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD);

        // Get overworld for context (may be null)
        World overworld = plugin.getServer().getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElse(null);

        int playerCount = plugin.getServer().getOnlinePlayers().size();

        // Second line: Dynamic message from MessageGenerator
        String message = MessageGenerator.generateMotdMessage(overworld, playerCount);
        Component line2 = Component.text(message).color(NamedTextColor.GRAY);

        return line1.append(Component.newline()).append(line2);
    }
}
