package sh.joey.mc.punish;

import io.papermc.paper.event.player.AsyncChatEvent;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.UUID;

/**
 * Enforces punishments by listening to login and chat events.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Block banned players from logging in</li>
 *   <li>Block IP-banned connections from logging in</li>
 *   <li>Block muted players from chatting</li>
 * </ul>
 */
public final class PunishmentEnforcer implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final PunishmentStorage storage;

    public PunishmentEnforcer(SiqiJoeyPlugin plugin, PunishmentStorage storage) {
        this.plugin = plugin;
        this.storage = storage;

        // Enforce bans at login (async event, safe to block)
        disposables.add(plugin.watchEvent(AsyncPlayerPreLoginEvent.class)
                .subscribe(this::checkBanOnLogin));

        // Enforce mutes in chat (async event, safe to block)
        disposables.add(plugin.watchEvent(AsyncChatEvent.class)
                .subscribe(this::checkMuteOnChat));
    }

    /**
     * Check for bans and IP bans when a player attempts to login.
     * This runs on an async thread, so blocking database queries are safe.
     */
    private void checkBanOnLogin(AsyncPlayerPreLoginEvent event) {
        // Skip if already denied by another system
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }

        UUID playerId = event.getUniqueId();
        String ip = event.getAddress().getHostAddress();

        try {
            // Check player ban first
            Punishment ban = storage.getActiveBan(playerId).blockingGet();
            if (ban != null && ban.isActive()) {
                Component kickMessage = PunishmentMessages.formatBanKickMessage(ban);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
                return;
            }

            // Check IP ban
            Punishment ipBan = storage.getActiveIpBan(ip).blockingGet();
            if (ipBan != null && ipBan.isActive()) {
                Component kickMessage = PunishmentMessages.formatIpBanKickMessage(ipBan);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage);
                return;
            }
        } catch (Exception e) {
            // Fail open - allow login if database error (rather than blocking legitimate players)
            plugin.getLogger().warning("Error checking bans for " + event.getName() + ": " + e.getMessage());
        }
    }

    /**
     * Check for mutes when a player attempts to chat.
     * This runs on an async thread, so blocking database queries are safe.
     */
    private void checkMuteOnChat(AsyncChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();

        try {
            Punishment mute = storage.getActiveMute(playerId).blockingGet();
            if (mute != null && mute.isActive()) {
                event.setCancelled(true);

                // Send notification to the muted player on main thread
                Component message = PunishmentMessages.formatMuteNotification(mute);
                plugin.getServer().getScheduler().runTask(plugin, () ->
                        event.getPlayer().sendMessage(message));
            }
        } catch (Exception e) {
            // Fail open - allow message if database error
            plugin.getLogger().warning("Error checking mute for " + event.getPlayer().getName() + ": " + e.getMessage());
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
