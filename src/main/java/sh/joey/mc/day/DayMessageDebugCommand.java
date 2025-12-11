package sh.joey.mc.day;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.messages.MessageGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * Debug command to show all possible contextual messages for the current player state.
 */
public final class DayMessageDebugCommand implements Command {

    private static final Component PREFIX = Component.text("[")
            .color(NamedTextColor.GOLD)
            .append(Component.text("DEBUG").color(NamedTextColor.RED))
            .append(Component.text("] ").color(NamedTextColor.GOLD));

    @Override
    public String getName() {
        return "daymsgdebug";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(PREFIX.append(Component.text("This command can only be used by players.").color(NamedTextColor.RED)));
                return;
            }

            World world = player.getWorld();
            List<String> allCandidates = new ArrayList<>();
            List<String> biomeCandidates = new ArrayList<>();

            // Gather all context messages using the same logic as MessageGenerator
            List<String> entityMessages = new ArrayList<>();
            List<String> inventoryMessages = new ArrayList<>();
            List<String> blockMessages = new ArrayList<>();
            List<String> healthMessages = new ArrayList<>();
            List<String> hungerMessages = new ArrayList<>();
            List<String> xpMessages = new ArrayList<>();
            List<String> armorMessages = new ArrayList<>();
            List<String> itemMessages = new ArrayList<>();
            List<String> yLevelMessages = new ArrayList<>();
            List<String> undergroundMessages = new ArrayList<>();
            List<String> waterMessages = new ArrayList<>();
            List<String> vehicleMessages = new ArrayList<>();
            List<String> dayMilestoneMessages = new ArrayList<>();
            List<String> difficultyMessages = new ArrayList<>();
            List<String> moonPhaseMessages = new ArrayList<>();
            List<String> weatherMessages = new ArrayList<>();
            List<String> dimensionMessages = new ArrayList<>();
            List<String> specialMessages = new ArrayList<>();

            // Call all context detection methods
            MessageGenerator.addNearbyEntityMessages(player, world, entityMessages);
            MessageGenerator.addInventoryStateMessages(player, inventoryMessages);
            MessageGenerator.addNearbyBlockMessages(player, world, blockMessages);
            MessageGenerator.addHealthMessages(player, healthMessages);
            MessageGenerator.addHungerMessages(player, hungerMessages);
            MessageGenerator.addExperienceMessages(player, xpMessages);
            MessageGenerator.addArmorMessages(player, armorMessages);
            MessageGenerator.addHeldItemMessages(player, itemMessages, MessageGenerator.MessageType.DAY);
            MessageGenerator.addYLevelMessages(player, yLevelMessages);
            MessageGenerator.addUndergroundMessages(player, world, undergroundMessages, MessageGenerator.MessageType.DAY);
            MessageGenerator.addWaterMessages(player, waterMessages, MessageGenerator.MessageType.DAY);
            MessageGenerator.addVehicleMessages(player, vehicleMessages, MessageGenerator.MessageType.DAY);
            MessageGenerator.addDayMilestoneMessages(world, dayMilestoneMessages);
            MessageGenerator.addDifficultyMessages(world, difficultyMessages);
            MessageGenerator.addMoonPhaseMessages(world, moonPhaseMessages);
            MessageGenerator.addWeatherMessages(world, weatherMessages, MessageGenerator.MessageType.DAY);
            MessageGenerator.addBiomeMessages(player, world, biomeCandidates);
            MessageGenerator.addDimensionMessages(player, world, dimensionMessages, MessageGenerator.MessageType.DAY);
            MessageGenerator.addSpecialSituationMessages(player, specialMessages, MessageGenerator.MessageType.DAY);

            // Combine all into final list
            allCandidates.addAll(entityMessages);
            allCandidates.addAll(inventoryMessages);
            allCandidates.addAll(blockMessages);
            allCandidates.addAll(healthMessages);
            allCandidates.addAll(hungerMessages);
            allCandidates.addAll(xpMessages);
            allCandidates.addAll(armorMessages);
            allCandidates.addAll(itemMessages);
            allCandidates.addAll(yLevelMessages);
            allCandidates.addAll(undergroundMessages);
            allCandidates.addAll(waterMessages);
            allCandidates.addAll(vehicleMessages);
            allCandidates.addAll(dayMilestoneMessages);
            allCandidates.addAll(difficultyMessages);
            allCandidates.addAll(moonPhaseMessages);
            allCandidates.addAll(weatherMessages);
            allCandidates.addAll(dimensionMessages);
            allCandidates.addAll(specialMessages);

            // Display results
            player.sendMessage(PREFIX.append(Component.text("Contextual Message Debug").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD)));
            player.sendMessage(Component.empty());

            displayCategory(player, "High-Variety Contexts (Prioritized)", entityMessages, inventoryMessages, blockMessages);
            displayCategory(player, "Player State", healthMessages, hungerMessages, xpMessages, armorMessages, itemMessages);
            displayCategory(player, "Location State", yLevelMessages, undergroundMessages, waterMessages, vehicleMessages);
            displayCategory(player, "World State", dayMilestoneMessages, difficultyMessages, moonPhaseMessages, weatherMessages);
            displayCategory(player, "Biome (Fallback Only)", biomeCandidates);
            displayCategory(player, "Dimension", dimensionMessages);
            displayCategory(player, "Special Situations", specialMessages);

            player.sendMessage(Component.empty());
            player.sendMessage(PREFIX.append(
                    Component.text("Total Candidates: ").color(NamedTextColor.YELLOW)
                            .append(Component.text(allCandidates.size()).color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD))
            ));
            player.sendMessage(PREFIX.append(
                    Component.text("Biome Candidates: ").color(NamedTextColor.YELLOW)
                            .append(Component.text(biomeCandidates.size()).color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
            ));

            if (allCandidates.isEmpty()) {
                player.sendMessage(PREFIX.append(Component.text("No contextual messages available - would fall back to static/procedural.").color(NamedTextColor.GRAY)));
            } else {
                player.sendMessage(PREFIX.append(Component.text("Biome messages " +
                        (allCandidates.isEmpty() ? "WOULD" : "would NOT") + " be used.").color(NamedTextColor.GRAY)));
            }
        });
    }

    @SafeVarargs
    private void displayCategory(Player player, String categoryName, List<String>... messageLists) {
        List<String> combined = new ArrayList<>();
        for (List<String> list : messageLists) {
            combined.addAll(list);
        }

        if (combined.isEmpty()) {
            return;
        }

        player.sendMessage(Component.text("▸ " + categoryName + " (" + combined.size() + ")").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        for (String message : combined) {
            player.sendMessage(Component.text("  • ").color(NamedTextColor.DARK_GRAY)
                    .append(Component.text(message).color(NamedTextColor.WHITE)));
        }
        player.sendMessage(Component.empty());
    }
}
