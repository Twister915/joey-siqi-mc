package sh.joey.mc.trails;

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
import sh.joey.mc.trails.elytra.CustomColorEffect;
import sh.joey.mc.trails.elytra.ElytraTrailEffect;
import sh.joey.mc.trails.elytra.RainbowEffect;

import java.util.ArrayList;
import java.util.List;

/**
 * /trails - manage particle trail effects
 *
 * Commands:
 * - /trails - show trail categories menu
 * - /trails off - disable all trails
 * - /trails elytra - show elytra trail options
 * - /trails elytra <effect> - select an elytra trail effect
 * - /trails elytra <effect> <intensity> - select effect with intensity
 * - /trails elytra intensity <level> - change intensity only
 * - /trails elytra off - disable elytra trails
 */
public final class TrailsCommand implements Command {

    private static final Component PREFIX = Component.text("[Trails] ", NamedTextColor.LIGHT_PURPLE);

    private final SiqiJoeyPlugin plugin;
    private final TrailManager manager;

    public TrailsCommand(SiqiJoeyPlugin plugin, TrailManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "trails";
    }

    @Override
    public String getPermission() {
        return null; // Permission checked per-trail-type
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return Completable.complete();
            }

            if (args.length == 0) {
                return showCategoryMenu(player);
            }

            String first = args[0].toLowerCase();

            // /trails off - disable all trails
            if (first.equals("off")) {
                return handleOffAll(player);
            }

            // /trails elytra ...
            if (first.equals("elytra")) {
                if (!player.hasPermission(TrailType.ELYTRA.permission())) {
                    error(player, "You don't have permission to use elytra trails.");
                    return Completable.complete();
                }
                return handleElytra(player, args);
            }

            error(player, "Unknown trail category. Use /trails for help.");
            return Completable.complete();
        });
    }

    private Completable showCategoryMenu(Player player) {
        player.sendMessage(PREFIX.append(Component.text("Trail Categories:", NamedTextColor.WHITE)));
        player.sendMessage(Component.empty());

        // Elytra trails
        if (player.hasPermission(TrailType.ELYTRA.permission())) {
            TrailSetting current = manager.getSetting(player.getUniqueId(), TrailType.ELYTRA);
            Component elytraButton = Component.text("  ")
                    .append(Component.text("[Elytra Trails]", NamedTextColor.AQUA)
                            .decorate(TextDecoration.BOLD)
                            .clickEvent(ClickEvent.runCommand("/trails elytra"))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("Particle trails while flying with elytra", NamedTextColor.GRAY))));

            if (current != null) {
                elytraButton = elytraButton.append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(current.effect().displayName(), NamedTextColor.GREEN))
                        .append(Component.text(" (" + current.intensity().id() + ")", NamedTextColor.GRAY));
            }

            player.sendMessage(elytraButton);
        }

        // Future trail types would go here

        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  ")
                .append(Component.text("[Disable All Trails]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/trails off"))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Disable all active trails", NamedTextColor.GRAY)))));

        return Completable.complete();
    }

    private Completable handleOffAll(Player player) {
        manager.clearAllTrails(player);
        success(player, "All trails disabled.");
        return Completable.complete();
    }

    private Completable handleElytra(Player player, String[] args) {
        // /trails elytra - show menu
        if (args.length == 1) {
            return showElytraMenu(player);
        }

        String second = args[1].toLowerCase();

        // /trails elytra off
        if (second.equals("off")) {
            manager.clearTrail(player, TrailType.ELYTRA);
            success(player, "Elytra trail disabled.");
            return Completable.complete();
        }

        // /trails elytra intensity <level>
        if (second.equals("intensity")) {
            if (args.length < 3) {
                error(player, "Usage: /trails elytra intensity <low|medium|high>");
                return Completable.complete();
            }
            return handleElytraIntensity(player, args[2]);
        }

        // /trails elytra <effect> [intensity]
        return handleElytraSelect(player, second, args.length > 2 ? args[2] : null);
    }

    private Completable showElytraMenu(Player player) {
        TrailSetting current = manager.getSetting(player.getUniqueId(), TrailType.ELYTRA);

        player.sendMessage(PREFIX.append(Component.text("Elytra Trail Effects:", NamedTextColor.WHITE)));

        if (current != null) {
            player.sendMessage(Component.text("  Current: ", NamedTextColor.GRAY)
                    .append(Component.text(current.effect().displayName(), NamedTextColor.GREEN))
                    .append(Component.text(" (" + current.intensity().id() + ")", NamedTextColor.GRAY)));
        }

        player.sendMessage(Component.empty());

        // Built-in effects (in a grid-like layout)
        for (ElytraTrailEffect effect : ElytraTrailEffect.values()) {
            boolean isSelected = current != null && current.effect().id().equals(effect.id());
            Component button = Component.text("  ")
                    .append(Component.text("[" + effect.displayName() + "]",
                            isSelected ? NamedTextColor.GREEN : NamedTextColor.GOLD)
                            .clickEvent(ClickEvent.runCommand("/trails elytra " + effect.id()))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("Click to select", NamedTextColor.GRAY))));
            player.sendMessage(button);
        }

        // Rainbow
        boolean rainbowSelected = current != null && current.effect().id().equals("rainbow");
        player.sendMessage(Component.text("  ")
                .append(Component.text("[Rainbow]",
                        rainbowSelected ? NamedTextColor.GREEN : NamedTextColor.GOLD)
                        .clickEvent(ClickEvent.runCommand("/trails elytra rainbow"))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Cycles through rainbow colors", NamedTextColor.GRAY)))));

        // Custom RGB hint
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  Custom color: ", NamedTextColor.GRAY)
                .append(Component.text("/trails elytra rgb:RRGGBB", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("    Example: ", NamedTextColor.DARK_GRAY)
                .append(Component.text("/trails elytra rgb:ff5500", NamedTextColor.GOLD)
                        .clickEvent(ClickEvent.suggestCommand("/trails elytra rgb:"))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Click to start typing custom color", NamedTextColor.GRAY)))));

        // Intensity options
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  Intensity: ", NamedTextColor.GRAY));
        Component intensityLine = Component.text("    ");
        for (TrailIntensity intensity : TrailIntensity.values()) {
            boolean isSelected = current != null && current.intensity() == intensity;
            intensityLine = intensityLine.append(
                    Component.text("[" + capitalize(intensity.id()) + "]",
                            isSelected ? NamedTextColor.GREEN : NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/trails elytra intensity " + intensity.id()))
                            .hoverEvent(HoverEvent.showText(
                                    Component.text("Particles: " + intensity.particleCount() +
                                            ", Rate: every " + intensity.tickInterval() + " ticks", NamedTextColor.GRAY))))
                    .append(Component.text(" "));
        }
        player.sendMessage(intensityLine);

        // Off button
        player.sendMessage(Component.empty());
        player.sendMessage(Component.text("  ")
                .append(Component.text("[Disable Elytra Trail]", NamedTextColor.RED)
                        .clickEvent(ClickEvent.runCommand("/trails elytra off"))
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Turn off elytra trails", NamedTextColor.GRAY)))));

        return Completable.complete();
    }

    private Completable handleElytraSelect(Player player, String effectId, String intensityArg) {
        TrailEffect effect = parseElytraEffect(effectId);
        if (effect == null) {
            error(player, "Unknown effect '" + effectId + "'. Use /trails elytra for options.");
            return Completable.complete();
        }

        TrailIntensity intensity = null;
        if (intensityArg != null) {
            intensity = TrailIntensity.fromId(intensityArg);
            if (intensity == null) {
                error(player, "Unknown intensity '" + intensityArg + "'. Use low, medium, or high.");
                return Completable.complete();
            }
        }

        if (intensity != null) {
            manager.setSetting(player, TrailType.ELYTRA, effect, intensity);
            success(player, "Elytra trail set to " + effect.displayName() + " (" + intensity.id() + ").");
        } else {
            manager.setEffect(player, TrailType.ELYTRA, effect);
            success(player, "Elytra trail set to " + effect.displayName() + ".");
        }

        return Completable.complete();
    }

    private Completable handleElytraIntensity(Player player, String intensityArg) {
        TrailSetting current = manager.getSetting(player.getUniqueId(), TrailType.ELYTRA);
        if (current == null) {
            error(player, "You don't have an elytra trail set. Select one first!");
            return Completable.complete();
        }

        TrailIntensity intensity = TrailIntensity.fromId(intensityArg);
        if (intensity == null) {
            error(player, "Unknown intensity '" + intensityArg + "'. Use low, medium, or high.");
            return Completable.complete();
        }

        manager.setIntensity(player, TrailType.ELYTRA, intensity);
        success(player, "Elytra trail intensity set to " + intensity.id() + ".");
        return Completable.complete();
    }

    private TrailEffect parseElytraEffect(String effectId) {
        // Check for rainbow
        if (effectId.equalsIgnoreCase("rainbow")) {
            return RainbowEffect.INSTANCE;
        }

        // Check for custom color
        if (CustomColorEffect.isCustomColor(effectId)) {
            CustomColorEffect custom = CustomColorEffect.fromId(effectId);
            if (custom != null) {
                return custom;
            }
            return null;
        }

        // Check for built-in effects
        return ElytraTrailEffect.fromId(effectId);
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
                options.add("off");

                if (player.hasPermission(TrailType.ELYTRA.permission())) {
                    options.add("elytra");
                }

                return Maybe.just(options.stream()
                        .filter(opt -> opt.startsWith(prefix))
                        .map(Completion::completion)
                        .toList());
            }

            if (args.length >= 2 && args[0].equalsIgnoreCase("elytra")) {
                if (!player.hasPermission(TrailType.ELYTRA.permission())) {
                    return Maybe.empty();
                }

                if (args.length == 2) {
                    String prefix = args[1].toLowerCase();
                    List<Completion> completions = new ArrayList<>();

                    // Built-in effects
                    for (ElytraTrailEffect effect : ElytraTrailEffect.values()) {
                        if (effect.id().startsWith(prefix)) {
                            completions.add(Completion.completion(
                                    effect.id(),
                                    Component.text(effect.displayName(), NamedTextColor.GREEN)));
                        }
                    }

                    // Rainbow
                    if ("rainbow".startsWith(prefix)) {
                        completions.add(Completion.completion(
                                "rainbow",
                                Component.text("Rainbow", NamedTextColor.LIGHT_PURPLE)));
                    }

                    // Special options
                    if ("off".startsWith(prefix)) {
                        completions.add(Completion.completion("off"));
                    }
                    if ("intensity".startsWith(prefix)) {
                        completions.add(Completion.completion("intensity"));
                    }

                    // Custom color hint
                    if ("rgb:".startsWith(prefix) || prefix.startsWith("rgb:")) {
                        completions.add(Completion.completion(
                                "rgb:",
                                Component.text("Custom RGB color (e.g., rgb:ff5500)", NamedTextColor.GRAY)));
                    }

                    return Maybe.just(completions);
                }

                if (args.length == 3) {
                    String prefix = args[2].toLowerCase();

                    // If second arg is "intensity", complete with intensity levels
                    if (args[1].equalsIgnoreCase("intensity")) {
                        List<Completion> completions = new ArrayList<>();
                        for (TrailIntensity intensity : TrailIntensity.values()) {
                            if (intensity.id().startsWith(prefix)) {
                                completions.add(Completion.completion(intensity.id()));
                            }
                        }
                        return Maybe.just(completions);
                    }

                    // Otherwise, complete with intensity for effect selection
                    List<Completion> completions = new ArrayList<>();
                    for (TrailIntensity intensity : TrailIntensity.values()) {
                        if (intensity.id().startsWith(prefix)) {
                            completions.add(Completion.completion(intensity.id()));
                        }
                    }
                    return Maybe.just(completions);
                }
            }

            return Maybe.empty();
        });
    }

    private void info(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.GRAY)));
    }

    private void success(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.GREEN)));
    }

    private void error(Player player, String message) {
        player.sendMessage(PREFIX.append(Component.text(message, NamedTextColor.RED)));
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}
