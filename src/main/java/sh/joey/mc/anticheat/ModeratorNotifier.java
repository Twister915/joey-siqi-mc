package sh.joey.mc.anticheat;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ModeratorNotifier implements Disposable {

    private static final String PERMISSION = "smp.anticheat.alerts";
    private static final Component PREFIX = Component.text("[AC] ", NamedTextColor.RED);

    private final Set<UUID> alertsMuted = ConcurrentHashMap.newKeySet();
    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;

    public ModeratorNotifier(SiqiJoeyPlugin plugin) {
        this.plugin = plugin;

        // Clean up muted state on quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(e -> alertsMuted.remove(e.getPlayer().getUniqueId())));
    }

    public void alert(UUID targetId, String check, double vl, Map<String, Object> data) {
        String playerName = getPlayerName(targetId);

        Component message = PREFIX
                .append(Component.text(playerName, NamedTextColor.GRAY))
                .append(Component.text(" flagged: ", NamedTextColor.DARK_GRAY))
                .append(Component.text(check, NamedTextColor.YELLOW))
                .append(Component.text(" (VL: " + String.format("%.1f", vl) + ")", NamedTextColor.GRAY))
                .hoverEvent(HoverEvent.showText(formatData(data)))
                .clickEvent(ClickEvent.runCommand("/violations " + playerName));

        for (Player mod : Bukkit.getOnlinePlayers()) {
            if (mod.hasPermission(PERMISSION) && !alertsMuted.contains(mod.getUniqueId())) {
                mod.sendMessage(message);
                mod.playSound(mod.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2f);
            }
        }
    }

    private Component formatData(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return Component.text("No additional data", NamedTextColor.GRAY);
        }

        Component result = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (!first) {
                result = result.append(Component.newline());
            }
            first = false;

            String value = formatValue(entry.getValue());
            result = result
                    .append(Component.text(entry.getKey() + ": ", NamedTextColor.GRAY))
                    .append(Component.text(value, NamedTextColor.WHITE));
        }
        return result;
    }

    private String formatValue(Object value) {
        if (value instanceof Double d) {
            return String.format("%.2f", d);
        } else if (value instanceof Float f) {
            return String.format("%.2f", f);
        }
        return String.valueOf(value);
    }

    private String getPlayerName(UUID playerId) {
        Player online = Bukkit.getPlayer(playerId);
        if (online != null) {
            return online.getName();
        }
        return playerId.toString().substring(0, 8);
    }

    public void toggleAlerts(UUID playerId) {
        if (alertsMuted.contains(playerId)) {
            alertsMuted.remove(playerId);
        } else {
            alertsMuted.add(playerId);
        }
    }

    public boolean hasAlertsMuted(UUID playerId) {
        return alertsMuted.contains(playerId);
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
