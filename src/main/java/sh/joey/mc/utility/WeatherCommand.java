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
import java.util.Set;

/**
 * /weather <clear|rain|thunder> - changes the weather in the current world.
 */
public final class WeatherCommand implements Command {

    private static final Set<String> WEATHER_TYPES = Set.of("clear", "rain", "thunder", "storm");

    @Override
    public String getName() {
        return "weather";
    }

    @Override
    public String getPermission() {
        return "smp.weather";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return;
            }

            if (args.length < 1) {
                sender.sendMessage(Component.text("Usage: /weather <clear|rain|thunder>")
                        .color(NamedTextColor.RED));
                return;
            }

            String weatherArg = args[0].toLowerCase();
            World world = player.getWorld();

            switch (weatherArg) {
                case "clear", "sun" -> {
                    world.setStorm(false);
                    world.setThundering(false);
                    sender.sendMessage(Component.text("Weather set to clear in " + world.getName() + ".")
                            .color(NamedTextColor.GREEN));
                }
                case "rain" -> {
                    world.setStorm(true);
                    world.setThundering(false);
                    sender.sendMessage(Component.text("Weather set to rain in " + world.getName() + ".")
                            .color(NamedTextColor.GREEN));
                }
                case "thunder", "storm" -> {
                    world.setStorm(true);
                    world.setThundering(true);
                    sender.sendMessage(Component.text("Weather set to thunder in " + world.getName() + ".")
                            .color(NamedTextColor.GREEN));
                }
                default -> sender.sendMessage(Component.text("Invalid weather: " + weatherArg + ". Use clear, rain, or thunder.")
                        .color(NamedTextColor.RED));
            }
        });
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.fromCallable(() -> {
            if (args.length != 1) {
                return null;
            }

            String prefix = args[0].toLowerCase();
            List<Completion> completions = List.of("clear", "rain", "thunder").stream()
                    .filter(name -> name.startsWith(prefix))
                    .map(Completion::completion)
                    .toList();

            return completions.isEmpty() ? null : completions;
        });
    }
}
