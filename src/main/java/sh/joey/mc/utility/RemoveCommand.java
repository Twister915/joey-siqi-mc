package sh.joey.mc.utility;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * /remove <type|all> [radius] - removes entities around the player.
 * Examples:
 * - /remove all - removes all non-player entities within 50 blocks
 * - /remove zombie 100 - removes all zombies within 100 blocks
 * - /remove items 20 - removes dropped items within 20 blocks
 */
public final class RemoveCommand implements Command {

    private static final int DEFAULT_RADIUS = 50;
    private static final int MAX_RADIUS = 500;

    // Special categories
    private static final Set<String> SPECIAL_TYPES = Set.of("all", "items", "mobs", "animals", "monsters", "vehicles");

    @Override
    public String getName() {
        return "remove";
    }

    @Override
    public String getPermission() {
        return "smp.remove";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return;
            }

            if (args.length < 1) {
                sender.sendMessage(Component.text("Usage: /remove <type|all|items|mobs|animals|monsters|vehicles> [radius]")
                        .color(NamedTextColor.RED));
                return;
            }

            String typeArg = args[0].toLowerCase(Locale.ROOT);
            int radius = DEFAULT_RADIUS;

            if (args.length >= 2) {
                try {
                    radius = Integer.parseInt(args[1]);
                    if (radius < 1 || radius > MAX_RADIUS) {
                        sender.sendMessage(Component.text("Radius must be between 1 and " + MAX_RADIUS + ".")
                                .color(NamedTextColor.RED));
                        return;
                    }
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid radius: " + args[1])
                            .color(NamedTextColor.RED));
                    return;
                }
            }

            int removed = removeEntities(player, typeArg, radius);

            if (removed == -1) {
                sender.sendMessage(Component.text("Unknown entity type: " + typeArg)
                        .color(NamedTextColor.RED));
                return;
            }

            String typeName = typeArg.equals("all") ? "entities" : typeArg;
            sender.sendMessage(Component.text("Removed " + removed + " " + typeName + " within " + radius + " blocks.")
                    .color(NamedTextColor.GREEN));
        });
    }

    private int removeEntities(Player player, String typeArg, int radius) {
        int removed = 0;
        double radiusSq = radius * radius;

        for (Entity entity : player.getWorld().getEntities()) {
            if (entity instanceof Player) continue;
            if (entity.getLocation().distanceSquared(player.getLocation()) > radiusSq) continue;

            if (matchesType(entity, typeArg)) {
                entity.remove();
                removed++;
            }
        }

        return removed;
    }

    private boolean matchesType(Entity entity, String typeArg) {
        return switch (typeArg) {
            case "all" -> true;
            case "items" -> entity.getType() == EntityType.ITEM;
            case "mobs" -> entity.getType().isAlive();
            case "animals" -> isAnimal(entity);
            case "monsters" -> isMonster(entity);
            case "vehicles" -> isVehicle(entity);
            default -> {
                try {
                    EntityType type = EntityType.valueOf(typeArg.toUpperCase(Locale.ROOT));
                    yield entity.getType() == type;
                } catch (IllegalArgumentException e) {
                    yield false;
                }
            }
        };
    }

    private boolean isAnimal(Entity entity) {
        return switch (entity.getType()) {
            case PIG, COW, SHEEP, CHICKEN, HORSE, DONKEY, MULE, RABBIT, CAT, WOLF,
                 PARROT, FOX, BEE, GOAT, FROG, CAMEL, SNIFFER, ARMADILLO -> true;
            default -> false;
        };
    }

    private boolean isMonster(Entity entity) {
        return switch (entity.getType()) {
            case ZOMBIE, SKELETON, CREEPER, SPIDER, CAVE_SPIDER, ENDERMAN, SLIME,
                 WITCH, PHANTOM, DROWNED, HUSK, STRAY, PILLAGER, VINDICATOR, RAVAGER,
                 EVOKER, VEX, HOGLIN, PIGLIN, PIGLIN_BRUTE, ZOGLIN, WARDEN, BREEZE -> true;
            default -> false;
        };
    }

    private boolean isVehicle(Entity entity) {
        return switch (entity.getType()) {
            case MINECART, CHEST_MINECART, FURNACE_MINECART, TNT_MINECART, HOPPER_MINECART,
                 SPAWNER_MINECART, COMMAND_BLOCK_MINECART, OAK_BOAT, SPRUCE_BOAT, BIRCH_BOAT,
                 JUNGLE_BOAT, ACACIA_BOAT, CHERRY_BOAT, DARK_OAK_BOAT, MANGROVE_BOAT,
                 BAMBOO_RAFT, OAK_CHEST_BOAT, SPRUCE_CHEST_BOAT, BIRCH_CHEST_BOAT,
                 JUNGLE_CHEST_BOAT, ACACIA_CHEST_BOAT, CHERRY_CHEST_BOAT, DARK_OAK_CHEST_BOAT,
                 MANGROVE_CHEST_BOAT, BAMBOO_CHEST_RAFT -> true;
            default -> false;
        };
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.fromCallable(() -> {
            if (args.length == 1) {
                String prefix = args[0].toLowerCase();

                // Combine special types with entity types
                List<String> types = SPECIAL_TYPES.stream()
                        .filter(t -> t.startsWith(prefix))
                        .collect(Collectors.toList());

                // Add matching entity types
                Arrays.stream(EntityType.values())
                        .filter(t -> t != EntityType.PLAYER)
                        .map(t -> t.name().toLowerCase())
                        .filter(t -> t.startsWith(prefix))
                        .limit(20 - types.size())
                        .forEach(types::add);

                return types.isEmpty() ? null : types.stream()
                        .map(Completion::completion)
                        .toList();
            }

            if (args.length == 2) {
                String prefix = args[1].toLowerCase();
                List<String> radiuses = List.of("10", "25", "50", "100", "200");
                return radiuses.stream()
                        .filter(r -> r.startsWith(prefix))
                        .map(Completion::completion)
                        .toList();
            }

            return null;
        });
    }
}
