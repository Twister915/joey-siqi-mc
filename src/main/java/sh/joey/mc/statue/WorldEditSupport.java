package sh.joey.mc.statue;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Optional WorldEdit integration for undo support.
 * Falls back gracefully when WorldEdit is not available.
 */
public final class WorldEditSupport {

    private static Boolean available;

    private WorldEditSupport() {}

    /**
     * Checks if WorldEdit or FAWE is available.
     */
    public static boolean isAvailable() {
        if (available == null) {
            available = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null
                    || Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
        }
        return available;
    }

    /**
     * Creates a new block placer that records changes to WorldEdit history.
     * Must be closed after use.
     */
    public static BlockPlacer createPlacer(Player player, World world) {
        if (!isAvailable()) {
            return new DirectPlacer(world);
        }
        return new WorldEditPlacer(player, world);
    }

    /**
     * Interface for placing blocks, with optional undo support.
     */
    public interface BlockPlacer extends AutoCloseable {
        void setBlock(int x, int y, int z, Material material);

        @Override
        void close();
    }

    /**
     * Direct Bukkit block placement (no undo support).
     */
    private static final class DirectPlacer implements BlockPlacer {
        private final World world;

        DirectPlacer(World world) {
            this.world = world;
        }

        @Override
        public void setBlock(int x, int y, int z, Material material) {
            world.getBlockAt(x, y, z).setType(material);
        }

        @Override
        public void close() {
            // Nothing to clean up
        }
    }

    /**
     * WorldEdit-backed block placement with undo support.
     */
    private static final class WorldEditPlacer implements BlockPlacer {
        private final Player player;
        private final EditSession editSession;
        private final LocalSession localSession;

        WorldEditPlacer(Player player, World world) {
            this.player = player;

            var wePlayer = BukkitAdapter.adapt(player);
            var weWorld = BukkitAdapter.adapt(world);

            this.localSession = WorldEdit.getInstance().getSessionManager().get(wePlayer);
            this.editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(weWorld)
                    .actor(wePlayer)
                    .build();
        }

        @Override
        public void setBlock(int x, int y, int z, Material material) {
            BlockVector3 pos = BlockVector3.at(x, y, z);
            BlockState state = BukkitAdapter.adapt(material.createBlockData());
            try {
                editSession.setBlock(pos, state);
            } catch (Exception e) {
                // MaxChangedBlocksException or other - ignore and continue
            }
        }

        @Override
        public void close() {
            editSession.close();
            localSession.remember(editSession);
        }
    }
}
