package sh.joey.mc.home;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.UUID;

/**
 * Automatically saves a player's first bed interaction as their default home.
 * Only triggers if the player has no existing homes.
 */
public final class BedHomeListener implements Disposable {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("Home").color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final HomeStorage storage;

    public BedHomeListener(SiqiJoeyPlugin plugin, HomeStorage storage) {
        this.plugin = plugin;
        this.storage = storage;

        disposables.add(plugin.watchEvent(EventPriority.MONITOR, PlayerInteractEvent.class)
                .filter(event -> event.getAction().isRightClick())
                .filter(event -> event.getClickedBlock() != null)
                .filter(event -> isBed(event.getClickedBlock().getType()))
                .flatMapCompletable(this::saveFirstHomeIfNeeded)
                .subscribe());
    }

    Completable saveFirstHomeIfNeeded(PlayerInteractEvent event) {
        return storage.hasAnyHomes(event.getPlayer().getUniqueId())
                .filter(hasHomes -> !hasHomes)
                .flatMapCompletable(ignored -> saveFirstHome(event));
    }

    Completable saveFirstHome(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = event.getPlayer().getUniqueId();
        Location location = player.getLocation();

        return storage.setHome(playerId, new Home("home", playerId, location))
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> notifyHomeSaved(player))
                .doOnError(err -> plugin.getLogger().warning("Failed to save first home: " + err.getMessage()))
                .onErrorComplete();
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }

    private void notifyHomeSaved(Player player) {
        player.sendMessage(PREFIX.append(
                Component.text("Your first home has been set! Use ")
                        .color(NamedTextColor.GREEN)
                        .append(Component.text("/home").color(NamedTextColor.AQUA))
                        .append(Component.text(" to return here.").color(NamedTextColor.GREEN))
        ));
    }

    private boolean isBed(Material material) {
        return switch (material) {
            case WHITE_BED, ORANGE_BED, MAGENTA_BED, LIGHT_BLUE_BED,
                 YELLOW_BED, LIME_BED, PINK_BED, GRAY_BED,
                 LIGHT_GRAY_BED, CYAN_BED, PURPLE_BED, BLUE_BED,
                 BROWN_BED, GREEN_BED, RED_BED, BLACK_BED -> true;
            default -> false;
        };
    }
}
