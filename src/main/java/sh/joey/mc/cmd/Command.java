package sh.joey.mc.cmd;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import org.bukkit.command.CommandSender;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.List;

public interface Command {

    Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args);

    default Maybe<List<AsyncTabCompleteEvent.Completion>> tabComplete(CommandSender sender, String[] args) {
        return Maybe.empty();
    }

    String getName();
}
