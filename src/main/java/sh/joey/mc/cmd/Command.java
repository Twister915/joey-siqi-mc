package sh.joey.mc.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.Nullable;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.List;

public interface Command {

    Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args);

    default Maybe<List<AsyncTabCompleteEvent.Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.empty();
    }

    String getName();

    /**
     * Returns the permission required to use this command, or null if no permission is required.
     * Permission is checked before handle() and before tabComplete().
     */
    default @Nullable String getPermission() {
        return null;
    }
}
