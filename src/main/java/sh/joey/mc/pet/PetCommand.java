package sh.joey.mc.pet;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static sh.joey.mc.pet.Messages.*;

/**
 * /pet - manage companion pets
 *
 * Commands:
 * - /pet - show pet menu
 * - /pet spawn <type> - spawn a pet
 * - /pet despawn - remove your pet
 * - /pet sit - toggle sit/follow mode
 * - /pet list - show available pet types
 */
public final class PetCommand implements Command {

    private final PetManager manager;

    public PetCommand(PetManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "pet";
    }

    @Override
    public String getPermission() {
        return null; // Per-pet permissions checked
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return Completable.complete();
            }

            if (args.length == 0) {
                return showMenu(player);
            }

            String subcommand = args[0].toLowerCase();

            return switch (subcommand) {
                case "spawn", "summon" -> handleSpawn(player, args);
                case "despawn", "remove" -> handleDespawn(player);
                case "sit" -> handleSit(player);
                case "list" -> handleList(player);
                default -> handleSpawn(player, args); // Treat as pet type
            };
        });
    }

    private Completable showMenu(Player player) {
        player.sendMessage(PREFIX.append(Component.text("Pet Menu:", NamedTextColor.WHITE)));
        player.sendMessage(Component.empty());

        // Show current pet status
        Optional<Pet> currentPet = manager.getPet(player.getUniqueId());
        if (currentPet.isPresent()) {
            Pet pet = currentPet.get();
            String stateText = pet.getState() == PetState.SITTING ? "sitting" : "following";
            player.sendMessage(Component.text("  Current: ", NamedTextColor.GRAY)
                    .append(Component.text(pet.getType().displayName(), NamedTextColor.GREEN)
                            .decorate(TextDecoration.BOLD))
                    .append(Component.text(" (" + stateText + ")", NamedTextColor.GRAY)));
            player.sendMessage(Component.empty());
        }

        // List available pets
        player.sendMessage(Component.text("  Available Pets:", NamedTextColor.WHITE));

        boolean hasAnyPet = false;
        for (PetType type : PetType.values()) {
            if (player.hasPermission(type.permission())) {
                hasAnyPet = true;
                boolean isActive = currentPet.isPresent() && currentPet.get().getType() == type;

                Component button = Component.text("    ")
                        .append(Component.text("[" + type.displayName() + "]",
                                isActive ? NamedTextColor.GREEN : NamedTextColor.GOLD)
                                .decorate(isActive ? TextDecoration.BOLD : TextDecoration.ITALIC)
                                .clickEvent(ClickEvent.runCommand("/pet spawn " + type.id()))
                                .hoverEvent(HoverEvent.showText(
                                        Component.text("Click to summon", NamedTextColor.GRAY))));

                if (isActive) {
                    button = button.append(Component.text(" (active)", NamedTextColor.GRAY));
                }

                player.sendMessage(button);
            }
        }

        if (!hasAnyPet) {
            player.sendMessage(Component.text("    ", NamedTextColor.GRAY)
                    .append(Component.text("No pets available", NamedTextColor.DARK_GRAY)));
        }

        // Action buttons
        if (currentPet.isPresent()) {
            player.sendMessage(Component.empty());

            Pet pet = currentPet.get();
            String sitLabel = pet.getState() == PetState.SITTING ? "Follow" : "Sit";
            String sitHover = pet.getState() == PetState.SITTING
                    ? "Make your pet follow you"
                    : "Make your pet sit and stay";

            player.sendMessage(Component.text("  ")
                    .append(Component.text("[" + sitLabel + "]", NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/pet sit"))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text(sitHover, NamedTextColor.GRAY))))
                    .append(Component.text(" "))
                    .append(Component.text("[Despawn]", NamedTextColor.RED)
                            .clickEvent(ClickEvent.runCommand("/pet despawn"))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("Remove your pet", NamedTextColor.GRAY)))));
        }

        return Completable.complete();
    }

    private Completable handleSpawn(Player player, String[] args) {
        // Get pet type from args
        String typeArg = args.length > 1 ? args[1] : args[0];
        PetType type = PetType.fromId(typeArg.toLowerCase());

        if (type == null) {
            error(player, "Unknown pet type. Use /pet list to see available pets.");
            return Completable.complete();
        }

        if (!player.hasPermission(type.permission())) {
            error(player, "You don't have permission to summon this pet.");
            return Completable.complete();
        }

        manager.spawnPet(player, type);
        success(player, "Summoned your " + type.displayName() + "!");
        return Completable.complete();
    }

    private Completable handleDespawn(Player player) {
        if (!manager.hasPet(player.getUniqueId())) {
            error(player, "You don't have an active pet.");
            return Completable.complete();
        }

        manager.despawnPet(player);
        success(player, "Pet despawned.");
        return Completable.complete();
    }

    private Completable handleSit(Player player) {
        Optional<Pet> pet = manager.getPet(player.getUniqueId());
        if (pet.isEmpty()) {
            error(player, "You don't have an active pet.");
            return Completable.complete();
        }

        manager.toggleSit(player);

        // Re-fetch state after toggle
        PetState newState = pet.get().getState();
        if (newState == PetState.SITTING) {
            success(player, "Your pet is now sitting.");
        } else {
            success(player, "Your pet is now following you.");
        }
        return Completable.complete();
    }

    private Completable handleList(Player player) {
        player.sendMessage(PREFIX.append(Component.text("Available Pets:", NamedTextColor.WHITE)));
        player.sendMessage(Component.empty());

        for (PetType type : PetType.values()) {
            boolean hasPermission = player.hasPermission(type.permission());
            Component line = Component.text("  - ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(type.displayName(),
                            hasPermission ? NamedTextColor.GREEN : NamedTextColor.GRAY))
                    .append(Component.text(" (" + type.permission() + ")",
                            NamedTextColor.DARK_GRAY));

            if (hasPermission) {
                line = line.append(Component.text(" ")
                        .append(Component.text("[Summon]", NamedTextColor.GOLD)
                                .clickEvent(ClickEvent.runCommand("/pet spawn " + type.id()))
                                .hoverEvent(HoverEvent.showText(
                                        Component.text("Click to summon", NamedTextColor.GRAY)))));
            }

            player.sendMessage(line);
        }

        return Completable.complete();
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.defer(() -> {
            if (!(sender instanceof Player player)) {
                return Maybe.empty();
            }

            if (args.length == 1) {
                String prefix = args[0].toLowerCase();
                List<String> options = new ArrayList<>();
                options.add("spawn");
                options.add("despawn");
                options.add("sit");
                options.add("list");

                // Also add pet types directly
                for (PetType type : PetType.values()) {
                    if (player.hasPermission(type.permission())) {
                        options.add(type.id());
                    }
                }

                return Maybe.just(options.stream()
                        .filter(opt -> opt.startsWith(prefix))
                        .map(Completion::completion)
                        .toList());
            }

            if (args.length == 2 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("summon"))) {
                String prefix = args[1].toLowerCase();
                List<Completion> completions = new ArrayList<>();

                for (PetType type : PetType.values()) {
                    if (player.hasPermission(type.permission()) && type.id().startsWith(prefix)) {
                        completions.add(Completion.completion(
                                type.id(),
                                Component.text(type.displayName(), NamedTextColor.GREEN)));
                    }
                }

                return Maybe.just(completions);
            }

            return Maybe.empty();
        });
    }
}
