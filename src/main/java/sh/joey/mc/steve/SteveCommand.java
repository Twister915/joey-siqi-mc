package sh.joey.mc.steve;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;

import java.util.List;

/**
 * /steve command - view and manage Steve AI.
 * <p>
 * Subcommands:
 * <ul>
 *   <li>/steve - show current status</li>
 *   <li>/steve model [provider] - list or switch models</li>
 * </ul>
 * <p>
 * Requires smp.steve.admin permission.
 */
public final class SteveCommand implements Command {

    private static final String PERMISSION = "smp.steve.admin";

    private final SteveManager manager;
    private final SteveModelRegistry registry;
    private final String systemPrompt;

    public SteveCommand(SteveManager manager, SteveModelRegistry registry, String systemPrompt) {
        this.manager = manager;
        this.registry = registry;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String getName() {
        return "steve";
    }

    @Override
    public String getPermission() {
        return PERMISSION;
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (args.length == 0) {
                showStatus(sender);
                return;
            }

            String subcommand = args[0].toLowerCase();
            if ("model".equals(subcommand)) {
                if (args.length == 1) {
                    listModels(sender);
                } else {
                    switchModel(sender, args[1]);
                }
            } else {
                sender.sendMessage(Messages.PREFIX.append(
                        Component.text("Usage: /steve [model <provider>]").color(NamedTextColor.RED)));
            }
        });
    }

    private void showStatus(CommandSender sender) {
        SteveModelInfo info = manager.getModel().info();

        sender.sendMessage(Messages.PREFIX.append(
                Component.text("Status").color(NamedTextColor.GOLD)));

        sender.sendMessage(line("Provider", info.providerName()));
        sender.sendMessage(line("Model ID", info.modelId()));
        sender.sendMessage(line("Display Name", info.displayName()));
    }

    private void listModels(CommandSender sender) {
        String currentId = getCurrentProviderId();

        sender.sendMessage(Messages.PREFIX.append(
                Component.text("Available Models:").color(NamedTextColor.GOLD)));

        for (SteveModelProvider provider : registry.all()) {
            boolean isCurrent = provider.id().equals(currentId);
            Component status = isCurrent
                    ? Component.text(" (active)").color(NamedTextColor.GREEN)
                    : Component.empty();

            sender.sendMessage(Component.text("  " + provider.id())
                    .color(isCurrent ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                    .append(Component.text(" - " + provider.info().displayName()).color(NamedTextColor.GRAY))
                    .append(status));
        }

        sender.sendMessage(Component.text("  Use /steve model <id> to switch").color(NamedTextColor.DARK_GRAY));
    }

    private void switchModel(CommandSender sender, String providerId) {
        var providerOpt = registry.get(providerId.toLowerCase());
        if (providerOpt.isEmpty()) {
            sender.sendMessage(Messages.PREFIX.append(
                    Component.text("Unknown provider: " + providerId).color(NamedTextColor.RED)));
            listModels(sender);
            return;
        }

        SteveModelProvider provider = providerOpt.get();
        SteveModel newModel = provider.create(systemPrompt);
        manager.setModel(newModel);

        sender.sendMessage(Messages.PREFIX.append(
                Component.text("Switched to ").color(NamedTextColor.GREEN)
                        .append(Component.text(provider.info().displayName()).color(NamedTextColor.WHITE))));
    }

    private String getCurrentProviderId() {
        // Match current model's provider name to registry
        String currentProviderName = manager.getModel().info().providerName();
        for (SteveModelProvider provider : registry.all()) {
            if (provider.info().providerName().equals(currentProviderName)) {
                return provider.id();
            }
        }
        return "";
    }

    private Component line(String label, String value) {
        return Component.text("  " + label + ": ")
                .color(NamedTextColor.GRAY)
                .append(Component.text(value).color(NamedTextColor.WHITE));
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            if ("model".startsWith(prefix)) {
                return Maybe.just(List.of(Completion.completion("model")));
            }
            return Maybe.empty();
        }

        if (args.length == 2 && "model".equalsIgnoreCase(args[0])) {
            String prefix = args[1].toLowerCase();
            List<Completion> completions = registry.all().stream()
                    .map(SteveModelProvider::id)
                    .filter(id -> id.startsWith(prefix))
                    .map(Completion::completion)
                    .toList();
            return completions.isEmpty() ? Maybe.empty() : Maybe.just(completions);
        }

        return Maybe.empty();
    }
}
