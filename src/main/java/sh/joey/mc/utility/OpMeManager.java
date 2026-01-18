package sh.joey.mc.utility;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * /opme - temporarily grants operator status.
 * Automatically revokes op when the player disconnects or when the plugin is disabled.
 * Running /opme again while opped will deop the player (toggle behavior).
 */
public final class OpMeManager implements Command, Disposable {

    private static final Component PREFIX = Component.text("[Op] ").color(NamedTextColor.GOLD);

    private final SiqiJoeyPlugin plugin;
    private final Set<UUID> oppedPlayers = ConcurrentHashMap.newKeySet();
    private final CompositeDisposable disposables = new CompositeDisposable();

    public OpMeManager(SiqiJoeyPlugin plugin) {
        this.plugin = plugin;

        // Watch for player quit to deop tracked players
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .filter(event -> oppedPlayers.contains(event.getPlayer().getUniqueId()))
                .subscribe(event -> deopPlayer(event.getPlayer())));
    }

    @Override
    public String getName() {
        return "opme";
    }

    @Override
    public String getPermission() {
        return "smp.opme";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return;
            }

            UUID playerId = player.getUniqueId();

            if (oppedPlayers.contains(playerId)) {
                // Toggle off - deop the player
                deopPlayer(player);
                player.sendMessage(PREFIX.append(
                        Component.text("Operator status revoked.").color(NamedTextColor.YELLOW)));
            } else {
                // Toggle on - op the player
                opPlayer(player);
                player.sendMessage(PREFIX.append(
                        Component.text("Operator status granted. Run ").color(NamedTextColor.GREEN)
                                .append(Component.text("/opme").color(NamedTextColor.YELLOW))
                                .append(Component.text(" again to revoke.").color(NamedTextColor.GREEN))));
            }
        });
    }

    private void opPlayer(Player player) {
        oppedPlayers.add(player.getUniqueId());
        player.setOp(true);
        plugin.getLogger().info("OpMe: " + player.getName() + " granted temporary op");
    }

    private void deopPlayer(Player player) {
        if (oppedPlayers.remove(player.getUniqueId())) {
            player.setOp(false);
            plugin.getLogger().info("OpMe: " + player.getName() + " op revoked");
        }
    }

    @Override
    public void dispose() {
        disposables.dispose();

        // Deop all tracked players on shutdown
        for (UUID playerId : oppedPlayers) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                player.setOp(false);
                plugin.getLogger().info("OpMe: " + player.getName() + " op revoked (shutdown)");
            }
        }
        oppedPlayers.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
