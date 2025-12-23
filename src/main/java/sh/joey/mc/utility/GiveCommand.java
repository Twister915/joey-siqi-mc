package sh.joey.mc.utility;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * /give <player> <material> [amount] - gives an item to a player.
 */
public final class GiveCommand implements Command {

    @Override
    public String getName() {
        return "give";
    }

    @Override
    public String getPermission() {
        return "smp.give";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /give <player> <material> [amount]")
                        .color(NamedTextColor.RED));
                return;
            }

            Player target = plugin.getServer().getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text("Player '" + args[0] + "' is not online.")
                        .color(NamedTextColor.RED));
                return;
            }

            String materialArg = args[1];
            int amount = 1;

            if (args.length >= 3) {
                try {
                    amount = Integer.parseInt(args[2]);
                    if (amount < 1 || amount > 64 * 36) {
                        sender.sendMessage(Component.text("Amount must be between 1 and " + (64 * 36) + ".")
                                .color(NamedTextColor.RED));
                        return;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid amount: " + args[2])
                            .color(NamedTextColor.RED));
                    return;
                }
            }

            Optional<Material> materialOpt = ItemAliases.resolve(materialArg);
            if (materialOpt.isEmpty() || !materialOpt.get().isItem()) {
                sender.sendMessage(Component.text("Unknown item: " + materialArg)
                        .color(NamedTextColor.RED));
                return;
            }

            Material material = materialOpt.get();
            ItemStack item = new ItemStack(material, amount);
            target.getInventory().addItem(item);

            String itemName = material.name().toLowerCase().replace("_", " ");
            sender.sendMessage(Component.text("Gave " + amount + "x " + itemName + " to " + target.getName() + ".")
                    .color(NamedTextColor.GREEN));

            if (!target.equals(sender)) {
                target.sendMessage(Component.text("You received " + amount + "x " + itemName + ".")
                        .color(NamedTextColor.YELLOW));
            }
        });
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.fromCallable(() -> {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                List<Completion> completions = plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(prefix))
                        .map(Completion::completion)
                        .toList();
                return completions.isEmpty() ? null : completions;
            }

            if (args.length == 2) {
                String prefix = args[1].toLowerCase();

                List<Completion> completions = Stream.concat(
                        ItemAliases.getAliasNames().stream(),
                        Arrays.stream(Material.values())
                                .filter(Material::isItem)
                                .map(m -> m.name().toLowerCase())
                )
                        .filter(name -> name.startsWith(prefix))
                        .distinct()
                        .sorted()
                        .limit(30)
                        .map(Completion::completion)
                        .collect(Collectors.toList());

                return completions.isEmpty() ? null : completions;
            }

            if (args.length == 3) {
                String prefix = args[2].toLowerCase();
                List<String> amounts = List.of("1", "16", "32", "64", "128", "256", "512");
                return amounts.stream()
                        .filter(a -> a.startsWith(prefix))
                        .map(Completion::completion)
                        .toList();
            }

            return null;
        });
    }
}
