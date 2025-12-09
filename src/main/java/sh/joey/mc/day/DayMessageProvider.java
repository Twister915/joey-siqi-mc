package sh.joey.mc.day;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.messages.MessageGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Sends a themed message to players at the start of each Minecraft day.
 * Uses the centralized MessageGenerator for rich message variety.
 */
public final class DayMessageProvider implements Disposable {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.GOLD)
            .append(Component.text("\u2600").color(NamedTextColor.YELLOW)) // ☀
            .append(Component.text("] ").color(NamedTextColor.GOLD));

    private final CompositeDisposable disposables = new CompositeDisposable();

    // Track which worlds we've already sent messages for this day
    private final Map<UUID, Long> lastDayMessageTime = new HashMap<>();

    public DayMessageProvider(SiqiJoeyPlugin plugin) {
        // Periodic day detection (every second)
        disposables.add(plugin.interval(1, TimeUnit.SECONDS)
                .subscribe(tick -> checkDayTransitions()));

        // World events
        disposables.add(plugin.watchEvent(PlayerChangedWorldEvent.class)
                .subscribe(this::handleWorldChange));

        disposables.add(plugin.watchEvent(WorldUnloadEvent.class)
                .subscribe(event -> lastDayMessageTime.remove(event.getWorld().getUID())));
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    private void checkDayTransitions() {
        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() != World.Environment.NORMAL) continue;

            long time = world.getTime();
            // Day starts at time 0, check if we're in the first 100 ticks of the day
            if (time >= 0 && time < 100) {
                long dayNumber = world.getFullTime() / 24000;
                Long lastDay = lastDayMessageTime.get(world.getUID());

                if (lastDay == null || lastDay < dayNumber) {
                    lastDayMessageTime.put(world.getUID(), dayNumber);
                    sendDayMessages(world);
                }
            }
        }
    }

    private void handleWorldChange(PlayerChangedWorldEvent event) {
        World world = event.getPlayer().getWorld();
        if (world.getEnvironment() != World.Environment.NORMAL) return;

        // If entering a world during dawn (first 2000 ticks of day), send a message
        long time = world.getTime();
        if (time >= 0 && time < 2000) {
            Player player = event.getPlayer();
            String message = MessageGenerator.generateDayMessage(player);
            player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.WHITE)));
        }
    }

    private void sendDayMessages(World world) {
        for (Player player : world.getPlayers()) {
            String message = MessageGenerator.generateDayMessage(player);
            player.sendMessage(PREFIX.append(Component.text(message).color(NamedTextColor.WHITE)));
        }
    }
}
