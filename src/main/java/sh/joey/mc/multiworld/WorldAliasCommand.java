package sh.joey.mc.multiworld;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import org.bukkit.command.CommandSender;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

import java.util.List;

/**
 * Provides shortcut commands for common worlds (e.g., /survival, /creative, /superflat).
 * Delegates to WorldCommand with the target world name.
 */
public final class WorldAliasCommand implements Command {

    private final String commandName;
    private final String targetWorldName;
    private final WorldCommand worldCommand;

    public WorldAliasCommand(String commandName, String targetWorldName, WorldCommand worldCommand) {
        this.commandName = commandName;
        this.targetWorldName = targetWorldName;
        this.worldCommand = worldCommand;
    }

    @Override
    public String getName() {
        return commandName;
    }

    @Override
    public String getPermission() {
        return "smp.world";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        // Delegate to WorldCommand with the target world name
        return worldCommand.handle(plugin, sender, new String[]{targetWorldName});
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.empty();
    }
}
