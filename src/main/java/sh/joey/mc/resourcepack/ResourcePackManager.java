package sh.joey.mc.resourcepack;

import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.resourcepack.ResourcePackConfig.ResourcePackEntry;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Manages resource pack sending and tracking.
 * Handles status events to persist successful pack loads.
 */
public final class ResourcePackManager implements Disposable {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.DARK_GRAY)
            .append(Component.text("RP").color(NamedTextColor.GREEN))
            .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));

    private record PendingPackRequest(UUID playerId, String packId) {}

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final ResourcePackConfig config;
    private final ResourcePackStorage storage;

    // Key: resource pack UUID (from API), Value: pending request info
    private final Map<UUID, PendingPackRequest> pendingRequests = new ConcurrentHashMap<>();
    // Track players who have pending requests to clean up on quit
    private final Map<UUID, UUID> playerToPendingPack = new ConcurrentHashMap<>();

    public ResourcePackManager(SiqiJoeyPlugin plugin, ResourcePackConfig config, ResourcePackStorage storage) {
        this.plugin = plugin;
        this.config = config;
        this.storage = storage;

        // Watch resource pack status events
        disposables.add(plugin.watchEvent(PlayerResourcePackStatusEvent.class)
                .subscribe(this::handlePackStatus));

        // Send saved pack on join (with delay)
        disposables.add(plugin.watchEvent(PlayerJoinEvent.class)
                .flatMapCompletable(event -> {
                    Player player = event.getPlayer();
                    return plugin.timer(1500, TimeUnit.MILLISECONDS)
                            .flatMapCompletable(tick -> storage.getPlayerPack(player.getUniqueId())
                                    .observeOn(plugin.mainScheduler())
                                    .flatMapCompletable(packId -> {
                                        // Player might have disconnected during delay
                                        if (!player.isOnline()) {
                                            return io.reactivex.rxjava3.core.Completable.complete();
                                        }

                                        ResourcePackEntry pack = config.get(packId);
                                        if (pack == null) {
                                            // Pack no longer exists in config
                                            player.sendMessage(PREFIX.append(
                                                    Component.text("Your saved resource pack is no longer available.")
                                                            .color(NamedTextColor.YELLOW)));
                                            return storage.clearPlayerPack(player.getUniqueId());
                                        }

                                        // Send the pack (but don't track as pending since it's already saved)
                                        sendPackInternal(player, pack, false);
                                        return io.reactivex.rxjava3.core.Completable.complete();
                                    })
                                    .onErrorComplete())
                            .onErrorComplete();
                })
                .subscribe());

        // Cleanup on quit
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
                .subscribe(event -> {
                    UUID packUuid = playerToPendingPack.remove(event.getPlayer().getUniqueId());
                    if (packUuid != null) {
                        pendingRequests.remove(packUuid);
                    }
                }));
    }

    /**
     * Sends a resource pack to a player and tracks it for saving on success.
     *
     * @param player the player to send the pack to
     * @param pack the pack to send
     */
    public void sendPack(Player player, ResourcePackEntry pack) {
        sendPackInternal(player, pack, true);
    }

    private void sendPackInternal(Player player, ResourcePackEntry pack, boolean trackForSaving) {
        UUID packUuid = UUID.randomUUID();

        if (trackForSaving) {
            // Clean up any previous pending request for this player
            UUID oldPackUuid = playerToPendingPack.put(player.getUniqueId(), packUuid);
            if (oldPackUuid != null) {
                pendingRequests.remove(oldPackUuid);
            }

            pendingRequests.put(packUuid, new PendingPackRequest(player.getUniqueId(), pack.id()));
        }

        byte[] hashBytes = hexStringToBytes(pack.hash());

        player.setResourcePack(
                packUuid,
                pack.url(),
                hashBytes,
                Component.text("Resource pack: ").color(NamedTextColor.WHITE)
                        .append(Component.text(pack.name()).color(NamedTextColor.GREEN)),
                false
        );
    }

    private void handlePackStatus(PlayerResourcePackStatusEvent event) {
        UUID packUuid = event.getID();
        PendingPackRequest pending = pendingRequests.get(packUuid);

        if (pending == null) {
            // Not a tracked request (e.g., auto-sent on join for already-saved pack)
            return;
        }

        Player player = event.getPlayer();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();

        switch (status) {
            case SUCCESSFULLY_LOADED -> {
                // Success! Save to database
                pendingRequests.remove(packUuid);
                playerToPendingPack.remove(player.getUniqueId());

                ResourcePackEntry pack = config.get(pending.packId());
                String packName = pack != null ? pack.name() : pending.packId();

                disposables.add(storage.setPlayerPack(player.getUniqueId(), pending.packId())
                        .observeOn(plugin.mainScheduler())
                        .subscribe(
                                () -> player.sendMessage(PREFIX.append(
                                        Component.text("Resource pack ")
                                                .color(NamedTextColor.WHITE)
                                                .append(Component.text(packName).color(NamedTextColor.GREEN))
                                                .append(Component.text(" saved! It will load automatically when you join.")
                                                        .color(NamedTextColor.WHITE)))),
                                err -> plugin.getLogger().warning(
                                        "Failed to save resource pack for " + player.getName() + ": " + err.getMessage())
                        ));
            }
            case DECLINED -> {
                pendingRequests.remove(packUuid);
                playerToPendingPack.remove(player.getUniqueId());
                player.sendMessage(PREFIX.append(
                        Component.text("Resource pack declined.").color(NamedTextColor.YELLOW)));
            }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                pendingRequests.remove(packUuid);
                playerToPendingPack.remove(player.getUniqueId());
                player.sendMessage(PREFIX.append(
                        Component.text("Failed to load resource pack: " + status.name().toLowerCase().replace('_', ' '))
                                .color(NamedTextColor.RED)));
            }
            // ACCEPTED and DOWNLOADED are intermediate states, ignore them
            default -> {}
        }
    }

    /**
     * Converts a hex string to a byte array.
     */
    private static byte[] hexStringToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    public static Component prefix() {
        return PREFIX;
    }

    @Override
    public void dispose() {
        disposables.dispose();
        pendingRequests.clear();
        playerToPendingPack.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
