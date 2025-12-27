package sh.joey.mc.statue;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.confirm.ConfirmationManager;
import sh.joey.mc.confirm.ConfirmationRequest;

import java.text.NumberFormat;

/**
 * Command to generate a wool statue of a player.
 * Usage: /genstatue <username>
 */
public final class StatueCommand implements Command {

    private final SiqiJoeyPlugin plugin;
    private final ConfirmationManager confirmationManager;
    private final MojangSkinApi mojangApi;
    private final StatueBuilder statueBuilder;

    public StatueCommand(SiqiJoeyPlugin plugin, ConfirmationManager confirmationManager) {
        this.plugin = plugin;
        this.confirmationManager = confirmationManager;
        this.mojangApi = new MojangSkinApi();
        this.statueBuilder = new StatueBuilder(new WoolColorMapper());
    }

    @Override
    public String getName() {
        return "genstatue";
    }

    @Override
    public String getPermission() {
        return "smp.statue";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return Completable.complete();
            }

            if (args.length < 1) {
                Messages.error(player, "Usage: /genstatue <username>");
                return Completable.complete();
            }

            String username = args[0];

            // Capture position and direction now (before async work)
            Location center = player.getLocation().clone();
            CardinalDirection facing = CardinalDirection.fromYaw(player.getLocation().getYaw());
            World world = player.getWorld();

            // Check height limit
            int maxY = world.getMaxHeight();
            if (center.getBlockY() + StatueGeometry.TOTAL_HEIGHT > maxY) {
                Messages.error(player, "Not enough vertical space. Statue is " +
                        StatueGeometry.TOTAL_HEIGHT + " blocks tall.");
                return Completable.complete();
            }

            Messages.info(player, "Fetching skin for " + username + "...");

            return mojangApi.fetchSkin(username)
                    .observeOn(plugin.mainScheduler())
                    .doOnSuccess(skin -> requestConfirmation(player, username, skin, world, center, facing))
                    .doOnError(err -> handleError(player, username, err))
                    .onErrorComplete()
                    .ignoreElement();
        });
    }

    private void requestConfirmation(Player player, String username, SkinTexture skin,
                                      World world, Location center, CardinalDirection facing) {
        int blockCount = statueBuilder.estimateBlockCount(skin);
        String formattedCount = NumberFormat.getInstance().format(blockCount);

        confirmationManager.request(player, new ConfirmationRequest() {
            @Override
            public Component prefix() {
                return Messages.PREFIX;
            }

            @Override
            public String promptText() {
                return "Build a " + StatueGeometry.TOTAL_HEIGHT + "-block tall statue of " +
                        username + "? (~" + formattedCount + " blocks)";
            }

            @Override
            public String acceptText() {
                return "Build";
            }

            @Override
            public String declineText() {
                return "Cancel";
            }

            @Override
            public void onAccept() {
                buildStatue(player, username, skin, world, center, facing);
            }

            @Override
            public void onDecline() {
                Messages.info(player, "Statue building cancelled.");
            }

            @Override
            public void onTimeout() {
                Messages.info(player, "Statue request expired.");
            }

            @Override
            public int timeoutSeconds() {
                return 30;
            }
        });
    }

    private void buildStatue(Player player, String username, SkinTexture skin,
                              World world, Location center, CardinalDirection facing) {
        Messages.info(player, "Building statue...");

        long startTime = System.currentTimeMillis();
        int blocksPlaced = statueBuilder.build(player, world, center, facing, skin);
        long elapsed = System.currentTimeMillis() - startTime;

        String formattedBlocks = NumberFormat.getInstance().format(blocksPlaced);
        Messages.success(player, "Statue of " + username + " complete! Placed " +
                formattedBlocks + " blocks in " + elapsed + "ms.");
    }

    private void handleError(Player player, String username, Throwable err) {
        if (err instanceof MojangSkinApi.PlayerNotFoundException) {
            Messages.error(player, "Player '" + username + "' not found.");
        } else if (err instanceof MojangSkinApi.NoSkinException) {
            Messages.error(player, "Player '" + username + "' has no custom skin.");
        } else {
            plugin.getLogger().warning("Failed to fetch skin for " + username + ": " + err.getMessage());
            Messages.error(player, "Failed to fetch skin. Try again later.");
        }
    }
}
