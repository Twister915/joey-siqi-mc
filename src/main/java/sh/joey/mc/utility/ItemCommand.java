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
 * /item <material> [amount] - gives an item to yourself.
 * /i is an alias.
 */
public final class ItemCommand implements Command {

    private final String name;

    public ItemCommand(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPermission() {
        return "smp.item";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return;
            }

            if (args.length < 1) {
                sender.sendMessage(Component.text("Usage: /item <material> [amount]")
                        .color(NamedTextColor.RED));
                return;
            }

            String materialArg = args[0];
            int amount = 1;

            if (args.length >= 2) {
                try {
                    amount = Integer.parseInt(args[1]);
                    if (amount < 1 || amount > 64 * 36) {
                        sender.sendMessage(Component.text("Amount must be between 1 and " + (64 * 36) + ".")
                                .color(NamedTextColor.RED));
                        return;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid amount: " + args[1])
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
            player.getInventory().addItem(item);

            String itemName = material.name().toLowerCase().replace("_", " ");
            sender.sendMessage(Component.text("Gave " + amount + "x " + itemName + ".")
                    .color(NamedTextColor.GREEN));
        });
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.fromCallable(() -> {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();

                // Combine aliases and material names
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

            if (args.length == 2) {
                String prefix = args[1].toLowerCase();
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
