package sh.joey.mc.steve;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent.Completion;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;
import sh.joey.mc.cmd.Command;
import sh.joey.mc.pagination.ChatPaginator;
import sh.joey.mc.pagination.PaginatedItem;
import sh.joey.mc.util.DurationFormat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * /steve command - view and manage Steve AI.
 * <p>
 * Subcommands:
 * <ul>
 *   <li>/steve - show current status (admin)</li>
 *   <li>/steve model [provider] - list or switch models (admin)</li>
 *   <li>/steve history [page] - view your question history (users)</li>
 * </ul>
 */
public final class SteveCommand implements Command {

    private static final String PERMISSION_ADMIN = "smp.steve.admin";
    private static final String PERMISSION_USER = "smp.steve";

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
        return null; // Permission checked per-subcommand
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!sender.hasPermission(PERMISSION_ADMIN)) {
                sender.sendMessage(Messages.PREFIX.append(
                        Component.text("Usage: /steve history [page]").color(NamedTextColor.GRAY)));
                return Completable.complete();
            }
            showStatus(sender);
            return Completable.complete();
        }

        String subcommand = args[0].toLowerCase();

        return switch (subcommand) {
            case "model" -> {
                if (!sender.hasPermission(PERMISSION_ADMIN)) {
                    sender.sendMessage(Messages.PREFIX.append(
                            Component.text("You don't have permission.").color(NamedTextColor.RED)));
                    yield Completable.complete();
                }
                if (args.length == 1) {
                    listModels(sender);
                } else {
                    switchModel(sender, args[1]);
                }
                yield Completable.complete();
            }
            case "history" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("This command can only be used by players.");
                    yield Completable.complete();
                }
                if (!sender.hasPermission(PERMISSION_USER)) {
                    sender.sendMessage(Messages.PREFIX.append(
                            Component.text("You don't have permission.").color(NamedTextColor.RED)));
                    yield Completable.complete();
                }
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        // Ignore, use default page 1
                    }
                }
                yield showHistory(plugin, player, page);
            }
            default -> {
                if (sender.hasPermission(PERMISSION_ADMIN)) {
                    sender.sendMessage(Messages.PREFIX.append(
                            Component.text("Usage: /steve [model <provider>] [history [page]]").color(NamedTextColor.RED)));
                } else {
                    sender.sendMessage(Messages.PREFIX.append(
                            Component.text("Usage: /steve history [page]").color(NamedTextColor.GRAY)));
                }
                yield Completable.complete();
            }
        };
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

    private Completable showHistory(SiqiJoeyPlugin plugin, Player player, int page) {
        return manager.getStorage().getHistory(player.getUniqueId())
                .toList()
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(entries -> {
                    if (entries.isEmpty()) {
                        player.sendMessage(Messages.PREFIX.append(
                                Component.text("You haven't asked Steve any questions yet.").color(NamedTextColor.GRAY)));
                        return;
                    }

                    ChatPaginator paginator = new ChatPaginator()
                            .title(Messages.PREFIX.append(Component.text("Your History").color(NamedTextColor.GOLD)))
                            .subtitle(Component.text(entries.size() + " questions asked").color(NamedTextColor.GRAY))
                            .command(p -> "/steve history " + p);

                    for (SteveHistoryEntry entry : entries) {
                        paginator.addAll(formatHistoryEntry(entry));
                    }

                    paginator.sendPage(player, page);
                })
                .doOnError(err -> {
                    plugin.getLogger().warning("Failed to load Steve history: " + err.getMessage());
                    player.sendMessage(Messages.PREFIX.append(
                            Component.text("Failed to load history.").color(NamedTextColor.RED)));
                })
                .ignoreElement();
    }

    private List<PaginatedItem> formatHistoryEntry(SteveHistoryEntry entry) {
        List<PaginatedItem> items = new ArrayList<>();

        // Time ago
        Duration ago = Duration.between(entry.askedAt(), Instant.now());
        String timeAgo = DurationFormat.formatShort(ago) + " ago";

        // Question header with timestamp
        Component questionHeader = Component.text("Q: ").color(NamedTextColor.AQUA)
                .append(Component.text(truncate(entry.question(), 50)).color(NamedTextColor.WHITE))
                .append(Component.text(" (" + timeAgo + ")").color(NamedTextColor.DARK_GRAY));
        items.add(PaginatedItem.wrapping(questionHeader, "Q: " + truncate(entry.question(), 50) + " (" + timeAgo + ")"));

        // Answer (may be long, truncate)
        String answerText = truncate(entry.answer(), 100);
        Component answerLine = Component.text("A: ").color(NamedTextColor.GREEN)
                .append(Component.text(answerText).color(NamedTextColor.GRAY));

        // Add hover with full answer if truncated
        if (entry.answer().length() > 100) {
            answerLine = answerLine.hoverEvent(HoverEvent.showText(
                    Component.text(truncate(entry.answer(), 256)).color(NamedTextColor.WHITE)));
        }
        items.add(PaginatedItem.wrapping(answerLine, "A: " + answerText));

        // Meta line (model, cost, sources)
        List<String> metaParts = new ArrayList<>();
        entry.modelName().ifPresent(m -> metaParts.add(m));
        entry.costCents().ifPresent(c -> metaParts.add(String.format("%.1f¢", c)));
        if (!entry.citations().isEmpty()) {
            metaParts.add(entry.citations().size() + " source" + (entry.citations().size() > 1 ? "s" : ""));
        }

        if (!metaParts.isEmpty()) {
            Component metaLine = Component.text("   ").append(
                    Component.text(String.join(" | ", metaParts)).color(NamedTextColor.DARK_GRAY));

            // Add clickable sources if present
            if (!entry.citations().isEmpty()) {
                metaLine = metaLine.append(Component.text(" "));
                for (int i = 0; i < entry.citations().size(); i++) {
                    SteveAnswer.Citation cite = entry.citations().get(i);
                    metaLine = metaLine.append(
                            Component.text("[" + (i + 1) + "]").color(NamedTextColor.AQUA)
                                    .clickEvent(ClickEvent.openUrl(cite.url()))
                                    .hoverEvent(HoverEvent.showText(Component.text(cite.title()).color(NamedTextColor.WHITE))));
                    if (i < entry.citations().size() - 1) {
                        metaLine = metaLine.append(Component.text(" "));
                    }
                }
            }
            items.add(PaginatedItem.simple(metaLine));
        }

        // Empty line separator
        items.add(PaginatedItem.empty());

        return items;
    }

    private String getCurrentProviderId() {
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

    private static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    @Override
    public Maybe<List<Completion>> tabComplete(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            List<Completion> completions = new ArrayList<>();

            if (sender.hasPermission(PERMISSION_ADMIN) && "model".startsWith(prefix)) {
                completions.add(Completion.completion("model"));
            }
            if (sender.hasPermission(PERMISSION_USER) && "history".startsWith(prefix)) {
                completions.add(Completion.completion("history"));
            }

            return completions.isEmpty() ? Maybe.empty() : Maybe.just(completions);
        }

        if (args.length == 2 && "model".equalsIgnoreCase(args[0]) && sender.hasPermission(PERMISSION_ADMIN)) {
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
