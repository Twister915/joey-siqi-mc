package sh.joey.mc.utility;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

import java.util.List;
import java.util.Map;

/**
 * /time <day|night|noon|midnight|sunrise|sunset|<ticks>> - changes the time in the current world.
 */
public final class TimeCommand implements Command {

    private static final Map<String, Long> TIME_PRESETS = Map.of(
            "day", 1000L,
            "noon", 6000L,
            "sunset", 12000L,
            "night", 13000L,
            "midnight", 18000L,
            "sunrise", 23000L
    );

    @Override
    public String getName() {
        return "time";
    }

    @Override
    public String getPermission() {
        return "smp.time";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return;
            }

            if (args.length < 1) {
                sender.sendMessage(Component.text("Usage: /time <day|night|noon|midnight|sunrise|sunset|<ticks>>")
                        .color(NamedTextColor.RED));
                return;
            }

            String timeArg = args[0].toLowerCase();
            long ticks;

            if (TIME_PRESETS.containsKey(timeArg)) {
                ticks = TIME_PRESETS.get(timeArg);
            } else {
                try {
                    ticks = Long.parseLong(timeArg);
                    if (ticks < 0 || ticks > 24000) {
                        sender.sendMessage(Component.text("Time must be between 0 and 24000 ticks.")
                                .color(NamedTextColor.RED));
                        return;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid time: " + timeArg + ". Use day, night, noon, midnight, sunrise, sunset, or a number.")
                            .color(NamedTextColor.RED));
                    return;
                }
            }

            World world = player.getWorld();
            world.setTime(ticks);

            String displayTime = TIME_PRESETS.entrySet().stream()
                    .filter(e -> e.getValue() == ticks)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(ticks + " ticks");

            sender.sendMessage(Component.text("Set time to " + displayTime + " in " + world.getName() + ".")
                    .color(NamedTextColor.GREEN));
        });
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.fromCallable(() -> {
            if (args.length != 1) {
                return null;
            }

            String prefix = args[0].toLowerCase();
            List<Completion> completions = TIME_PRESETS.keySet().stream()
                    .filter(name -> name.startsWith(prefix))
                    .sorted()
                    .map(Completion::completion)
                    .toList();

            return completions.isEmpty() ? null : completions;
        });
    }
}
