# Unified Confirmation System

## Overview

A centralized system for handling all yes/no confirmations in the plugin, replacing the current scattered implementations with a single, consistent approach.

**Current state**: Multiple separate confirmation systems exist:
- `HomeCommand` has `PendingDelete` with `/home confirm` and `/home cancel`
- `RequestManager` has `TeleportRequest` with `/accept` and `/decline`
- No confirmation for unsafe teleports (just a warning)

**Goal**: Unify all confirmations under a single `ConfirmationManager` with consistent `/accept` and `/decline` commands.

---

## Design

### ConfirmationRequest Interface

```java
package sh.joey.mc.confirm;

import io.reactivex.rxjava3.core.Completable;
import net.kyori.adventure.text.Component;

/**
 * Represents a confirmation request shown to a player.
 * Implement this interface to create custom confirmation prompts.
 */
public interface ConfirmationRequest {

    // --- Display ---

    /**
     * Prefix component shown before the prompt (e.g., [TP], [Home]).
     * Default: no prefix.
     */
    default Component prefix() {
        return Component.empty();
    }

    /**
     * The prompt message text shown to the player.
     */
    String promptText();

    /**
     * Text shown on the accept button.
     * Default: "Accept"
     */
    default String acceptText() {
        return "Accept";
    }

    /**
     * Text shown on the decline button.
     * Default: "Decline"
     */
    default String declineText() {
        return "Decline";
    }

    // --- Callbacks ---

    /**
     * Called when the player clicks accept.
     */
    void onAccept();

    /**
     * Called when the player clicks decline.
     * Default: no-op.
     */
    default void onDecline() {}

    /**
     * Called when the request times out.
     * Default: no-op.
     */
    default void onTimeout() {}

    /**
     * Called when the request is invalidated (e.g., a player quit).
     * Default: no-op.
     */
    default void onInvalidate() {}

    // --- Lifecycle ---

    /**
     * A Completable that, when it completes, invalidates this request.
     * Use this to cancel the request when external conditions change,
     * such as the request sender (not receiver) disconnecting.
     *
     * Note: The ConfirmationManager automatically tracks if the request
     * receiver disconnects - you don't need to handle that case here.
     *
     * Example for teleport requests (invalidate if requester quits):
     * <pre>
     * return plugin.watchEvent(PlayerQuitEvent.class)
     *     .filter(e -> e.getPlayer().getUniqueId().equals(requesterId))
     *     .take(1)
     *     .ignoreElements();
     * </pre>
     *
     * Default: Completable.never() (only timeout or receiver quit ends the request).
     */
    default Completable invalidation() {
        return Completable.never();
    }

    /**
     * How long the request remains valid, in seconds.
     * Default: 60 seconds.
     */
    default int timeoutSeconds() {
        return 60;
    }
}
```

### ConfirmationManager

```java
package sh.joey.mc.confirm;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import sh.joey.mc.SiqiJoeyPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Manages pending confirmation requests for players.
 * Each player can have at most one pending request at a time.
 */
public final class ConfirmationManager implements Disposable {

    private final CompositeDisposable disposables = new CompositeDisposable();
    private final SiqiJoeyPlugin plugin;
    private final Map<UUID, PendingRequest> pending = new HashMap<>();

    private record PendingRequest(
        ConfirmationRequest request,
        Disposable lifecycle
    ) {}

    public ConfirmationManager(SiqiJoeyPlugin plugin) {
        this.plugin = plugin;

        // Automatically invalidate requests when the receiver disconnects
        disposables.add(plugin.watchEvent(PlayerQuitEvent.class)
            .subscribe(event -> handleReceiverQuit(event.getPlayer().getUniqueId())));
    }

    private void handleReceiverQuit(UUID playerId) {
        PendingRequest req = pending.remove(playerId);
        if (req != null) {
            req.lifecycle().dispose();
            req.request().onInvalidate();
        }
    }

    /**
     * Sends a confirmation request to a player.
     * Any existing pending request for this player is invalidated.
     */
    public void request(Player player, ConfirmationRequest request) {
        UUID playerId = player.getUniqueId();

        // Cancel any existing request (triggers onInvalidate)
        invalidate(playerId);

        // Build lifecycle observable (timeout + custom invalidation)
        // Note: Receiver quit is handled separately via PlayerQuitEvent subscription
        Completable timeout = plugin.timer(request.timeoutSeconds(), TimeUnit.SECONDS)
            .ignoreElements()
            .doOnComplete(() -> handleTimeout(playerId));

        Completable customInvalidation = request.invalidation()
            .doOnComplete(() -> handleInvalidate(playerId));

        // Race between timeout and custom invalidation (Completable.never() won't complete)
        Completable lifecycle = Completable.ambArray(timeout, customInvalidation);

        Disposable lifecycleSubscription = lifecycle.subscribe(
            () -> {},  // Completed (handled in doOnComplete)
            err -> plugin.getLogger().warning("Confirmation lifecycle error: " + err.getMessage())
        );

        pending.put(playerId, new PendingRequest(request, lifecycleSubscription));

        // Send formatted message to player
        sendPrompt(player, request);
    }

    /**
     * Called by /accept command.
     */
    public void accept(Player player) {
        UUID playerId = player.getUniqueId();
        PendingRequest req = pending.remove(playerId);

        if (req == null) {
            player.sendMessage(Component.text("You don't have anything to accept.")
                .color(NamedTextColor.RED));
            return;
        }

        req.lifecycle().dispose();
        req.request().onAccept();
    }

    /**
     * Called by /decline command.
     */
    public void decline(Player player) {
        UUID playerId = player.getUniqueId();
        PendingRequest req = pending.remove(playerId);

        if (req == null) {
            player.sendMessage(Component.text("You don't have anything to decline.")
                .color(NamedTextColor.RED));
            return;
        }

        req.lifecycle().dispose();
        req.request().onDecline();
    }

    /**
     * Returns true if the player has a pending request.
     */
    public boolean hasPending(UUID playerId) {
        return pending.containsKey(playerId);
    }

    private void handleTimeout(UUID playerId) {
        PendingRequest req = pending.remove(playerId);
        if (req != null) {
            req.lifecycle().dispose();
            req.request().onTimeout();
        }
    }

    private void handleInvalidate(UUID playerId) {
        PendingRequest req = pending.remove(playerId);
        if (req != null) {
            req.lifecycle().dispose();
            req.request().onInvalidate();
        }
    }

    private void invalidate(UUID playerId) {
        PendingRequest req = pending.remove(playerId);
        if (req != null) {
            req.lifecycle().dispose();
            req.request().onInvalidate();
        }
    }

    private void sendPrompt(Player player, ConfirmationRequest request) {
        // Line 1: prefix + prompt text
        Component promptLine = request.prefix()
            .append(Component.text(request.promptText()).color(NamedTextColor.WHITE));

        // Line 2: buttons
        Component acceptButton = Component.text("[" + request.acceptText() + "]")
            .color(NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/accept"))
            .hoverEvent(HoverEvent.showText(
                Component.text("Click to " + request.acceptText().toLowerCase())
                    .color(NamedTextColor.GREEN)));

        Component declineButton = Component.text("[" + request.declineText() + "]")
            .color(NamedTextColor.RED)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/decline"))
            .hoverEvent(HoverEvent.showText(
                Component.text("Click to " + request.declineText().toLowerCase())
                    .color(NamedTextColor.RED)));

        Component buttonLine = request.prefix()
            .append(acceptButton)
            .append(Component.text(" "))
            .append(declineButton);

        player.sendMessage(promptLine);
        player.sendMessage(buttonLine);
    }

    @Override
    public void dispose() {
        disposables.dispose();
        // Invalidate all pending requests
        pending.values().forEach(req -> {
            req.lifecycle().dispose();
            req.request().onInvalidate();
        });
        pending.clear();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
```

### ConfirmCommands

```java
package sh.joey.mc.confirm;

import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;

/**
 * Command handlers for /accept and /decline.
 */
public final class ConfirmCommands {

    private ConfirmCommands() {}

    public static CommandExecutor accept(ConfirmationManager manager) {
        return (sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }
            manager.accept(player);
            return true;
        };
    }

    public static CommandExecutor decline(ConfirmationManager manager) {
        return (sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return true;
            }
            manager.decline(player);
            return true;
        };
    }
}
```

---

## Message Format

All confirmations display consistently:

```
{prefix} {promptText}
{prefix} [{acceptText}] [{declineText}]
```

- Prefix: colored component (e.g., `[TP]` in aqua, `[Home]` in light purple)
- Prompt text: white
- Accept button: green, bold, clickable
- Decline button: red, bold, clickable
- Hover text: "Click to {action}" in matching color

---

## Usage Examples

### Home Deletion

```java
confirmationManager.request(player, new ConfirmationRequest() {
    @Override
    public Component prefix() {
        return HOME_PREFIX;
    }

    @Override
    public String promptText() {
        return "Delete home '" + name + "'? This cannot be undone!";
    }

    @Override
    public String acceptText() {
        return "Delete";
    }

    @Override
    public String declineText() {
        return "Cancel";
    }

    @Override
    public void onAccept() {
        storage.deleteHome(playerId, name)
            .subscribe(
                deleted -> { if (deleted) success(player, "Home deleted."); },
                err -> logAndError(player, "Failed to delete", err)
            );
    }

    @Override
    public void onDecline() {
        info(player, "Deletion cancelled.");
    }

    @Override
    public void onTimeout() {
        info(player, "Delete confirmation expired.");
    }

    @Override
    public int timeoutSeconds() {
        return 30;
    }
});
```

**Output:**
```
[Home] Delete home 'base'? This cannot be undone!
[Home] [Delete] [Cancel]
```

### Teleport Request

```java
confirmationManager.request(target, new ConfirmationRequest() {
    @Override
    public Component prefix() {
        return TP_PREFIX;
    }

    @Override
    public String promptText() {
        return requester.getName() + " wants to teleport to you!";
    }

    // Uses default [Accept] [Decline]

    @Override
    public void onAccept() {
        Messages.success(target, "Accepted teleport from " + requester.getName() + "!");
        Messages.success(requester, target.getName() + " accepted your request!");
        safeTeleporter.teleport(requester, target.getLocation(), s -> {});
    }

    @Override
    public void onDecline() {
        Messages.info(target, "Request declined.");
        Messages.warning(requester, target.getName() + " declined your request.");
    }

    @Override
    public void onTimeout() {
        Messages.info(target, "Request from " + requester.getName() + " expired.");
        Messages.warning(requester, "Request to " + target.getName() + " expired.");
    }

    @Override
    public Completable invalidation() {
        // Invalidate if requester quits (target quit is handled automatically by ConfirmationManager)
        return plugin.watchEvent(PlayerQuitEvent.class)
            .filter(e -> e.getPlayer().getUniqueId().equals(requester.getUniqueId()))
            .take(1)
            .ignoreElements();
    }

    @Override
    public int timeoutSeconds() {
        return config.requestTimeoutSeconds();
    }
});

Messages.success(requester, "Request sent to " + target.getName() + "!");
```

**Output:**
```
[TP] Joey wants to teleport to you!
[TP] [Accept] [Decline]
```

### Unsafe Teleport Confirmation

```java
confirmationManager.request(player, new ConfirmationRequest() {
    @Override
    public Component prefix() {
        return TP_PREFIX;
    }

    @Override
    public String promptText() {
        return "Destination may be unsafe (water/void). Teleport anyway?";
    }

    @Override
    public String acceptText() {
        return "Teleport";
    }

    @Override
    public String declineText() {
        return "Cancel";
    }

    @Override
    public void onAccept() {
        // Bypass safety check, teleport directly
        executeTeleportUnsafe(player, destination, onComplete);
    }

    @Override
    public void onDecline() {
        Messages.info(player, "Teleport cancelled.");
        if (onComplete != null) onComplete.accept(false);
    }

    @Override
    public void onTimeout() {
        Messages.info(player, "Teleport confirmation expired.");
        if (onComplete != null) onComplete.accept(false);
    }

    @Override
    public int timeoutSeconds() {
        return 15;  // Shorter timeout for immediate actions
    }
});
```

**Output:**
```
[TP] Destination may be unsafe (water/void). Teleport anyway?
[TP] [Teleport] [Cancel]
```

---

## File Structure

```
src/main/java/sh/joey/mc/
├── confirm/
│   ├── ConfirmationRequest.java     # Interface
│   ├── ConfirmationManager.java     # Core manager
│   └── ConfirmCommands.java         # /accept /decline handlers
├── home/
│   └── HomeCommand.java             # MODIFY: use ConfirmationManager
├── teleport/
│   ├── RequestManager.java          # MODIFY: use ConfirmationManager
│   ├── SafeTeleporter.java          # MODIFY: add unsafe confirmation
│   └── commands/
│       └── YesNoCommands.java       # DELETE: replaced by ConfirmCommands
```

---

## Implementation Steps

### Step 1: Create confirm package

Create the three new files:
- `ConfirmationRequest.java` - interface as shown above
- `ConfirmationManager.java` - manager as shown above
- `ConfirmCommands.java` - command handlers as shown above

### Step 2: Wire up in SiqiJoeyPlugin

```java
// In onEnable(), after schedulers init but before other components:

// Confirmation system
var confirmationManager = new ConfirmationManager(this);
components.add(confirmationManager);

// Register commands (replaces YesNoCommands registration)
getCommand("accept").setExecutor(ConfirmCommands.accept(confirmationManager));
getCommand("decline").setExecutor(ConfirmCommands.decline(confirmationManager));
```

Pass `confirmationManager` to components that need it:
- `RequestManager` (or inline in `TpCommand`)
- `HomeCommand`
- `SafeTeleporter`

### Step 3: Refactor HomeCommand

**Remove:**
- `PendingDelete` record
- `pendingDeletes` map
- `handleConfirm()` method
- `handleCancel()` method
- `cancelPendingDelete()` method
- `RESERVED_NAMES` entries for "confirm" and "cancel"
- Switch cases for "confirm" and "cancel"

**Add:**
- `ConfirmationManager` field (passed via constructor)
- Use `confirmationManager.request()` in delete flow

**Change `onDeleteHomeFound()`:**
```java
private void onDeleteHomeFound(Player player, String name) {
    confirmationManager.request(player, new ConfirmationRequest() {
        @Override
        public Component prefix() { return PREFIX; }

        @Override
        public String promptText() {
            return "Delete home '" + name + "'? This cannot be undone!";
        }

        @Override
        public String acceptText() { return "Delete"; }

        @Override
        public String declineText() { return "Cancel"; }

        @Override
        public void onAccept() {
            storage.deleteHome(player.getUniqueId(), name)
                .subscribe(
                    deleted -> onDeleteResult(player, name, deleted),
                    err -> logAndError(player, "Failed to delete home", err)
                );
        }

        @Override
        public void onDecline() {
            info(player, "Home deletion cancelled.");
        }

        @Override
        public void onTimeout() {
            info(player, "Delete confirmation expired.");
        }

        @Override
        public int timeoutSeconds() { return 30; }
    });
}
```

### Step 4: Refactor RequestManager

**Remove:**
- `TeleportRequest` record
- `pendingRequests` map
- All request tracking logic
- Player quit handling (ConfirmationManager handles receiver quit automatically; requester quit via `invalidation()`)

**Simplify to just the request creation logic:**
```java
public final class RequestManager {
    private final SiqiJoeyPlugin plugin;
    private final PluginConfig config;
    private final SafeTeleporter safeTeleporter;
    private final ConfirmationManager confirmationManager;

    public RequestManager(SiqiJoeyPlugin plugin, PluginConfig config,
                          SafeTeleporter safeTeleporter,
                          ConfirmationManager confirmationManager) {
        this.plugin = plugin;
        this.config = config;
        this.safeTeleporter = safeTeleporter;
        this.confirmationManager = confirmationManager;
    }

    public void sendRequest(Player requester, Player target) {
        UUID requesterId = requester.getUniqueId();
        UUID targetId = target.getUniqueId();

        confirmationManager.request(target, new ConfirmationRequest() {
            @Override
            public Component prefix() { return Messages.PREFIX; }

            @Override
            public String promptText() {
                return requester.getName() + " wants to teleport to you!";
            }

            @Override
            public void onAccept() {
                Player req = plugin.getServer().getPlayer(requesterId);
                Player tgt = plugin.getServer().getPlayer(targetId);
                if (req != null && tgt != null) {
                    Messages.success(tgt, "Accepted teleport from " + req.getName() + "!");
                    Messages.success(req, tgt.getName() + " accepted your request!");
                    safeTeleporter.teleport(req, tgt.getLocation(), s -> {});
                }
            }

            @Override
            public void onDecline() {
                Player req = plugin.getServer().getPlayer(requesterId);
                Messages.info(target, "Request declined.");
                if (req != null) {
                    Messages.warning(req, target.getName() + " declined your request.");
                }
            }

            @Override
            public void onTimeout() {
                Player req = plugin.getServer().getPlayer(requesterId);
                Player tgt = plugin.getServer().getPlayer(targetId);
                if (req != null) {
                    Messages.warning(req, "Request to " + target.getName() + " expired.");
                }
                if (tgt != null) {
                    Messages.info(tgt, "Request from " + requester.getName() + " expired.");
                }
            }

            @Override
            public Completable invalidation() {
                // Invalidate if requester quits (target quit is handled automatically by ConfirmationManager)
                return plugin.watchEvent(PlayerQuitEvent.class)
                    .filter(e -> e.getPlayer().getUniqueId().equals(requesterId))
                    .take(1)
                    .ignoreElements();
            }

            @Override
            public int timeoutSeconds() {
                return config.requestTimeoutSeconds();
            }
        });

        Messages.success(requester, "Request sent to " + target.getName() + "!");
    }
}
```

**Note:** RequestManager no longer needs to implement `Disposable` since it doesn't track state. Player quit handling for the request receiver is automatic via ConfirmationManager, and requester quit is handled via the `invalidation()` Completable.

### Step 5: Delete YesNoCommands

Delete `src/main/java/sh/joey/mc/teleport/commands/YesNoCommands.java` - replaced by `ConfirmCommands`.

### Step 6: Add unsafe teleport confirmation in SafeTeleporter

**Add field:**
```java
private final ConfirmationManager confirmationManager;
```

**Update constructor:**
```java
public SafeTeleporter(SiqiJoeyPlugin plugin, PluginConfig config,
                      LocationTracker locationTracker,
                      ConfirmationManager confirmationManager) {
    // ...
    this.confirmationManager = confirmationManager;
}
```

**Modify `teleport()` method to check safety first:**
```java
public void teleport(Player player, Location destination, Consumer<Boolean> onComplete) {
    // ... existing vehicle check ...

    // Check if destination is safe
    Optional<Location> safeLocation = findSafeLocation(destination);

    if (safeLocation.isEmpty()) {
        // No safe location found - ask for confirmation
        requestUnsafeTeleportConfirmation(player, destination, onComplete);
        return;
    }

    // Safe location found - proceed with warmup
    startWarmup(player, safeLocation.get(), onComplete);
}

private void requestUnsafeTeleportConfirmation(Player player, Location destination,
                                                Consumer<Boolean> onComplete) {
    confirmationManager.request(player, new ConfirmationRequest() {
        @Override
        public Component prefix() { return Messages.PREFIX; }

        @Override
        public String promptText() {
            return "Destination may be unsafe. Teleport anyway?";
        }

        @Override
        public String acceptText() { return "Teleport"; }

        @Override
        public String declineText() { return "Cancel"; }

        @Override
        public void onAccept() {
            // Bypass safety check, teleport with warmup
            startWarmup(player, destination, onComplete);
        }

        @Override
        public void onDecline() {
            Messages.info(player, "Teleport cancelled.");
            if (onComplete != null) onComplete.accept(false);
        }

        @Override
        public void onTimeout() {
            Messages.info(player, "Teleport confirmation expired.");
            if (onComplete != null) onComplete.accept(false);
        }

        @Override
        public int timeoutSeconds() { return 15; }
    });
}

// Rename current teleport logic to startWarmup()
private void startWarmup(Player player, Location destination, Consumer<Boolean> onComplete) {
    // ... existing warmup countdown logic ...
}
```

### Step 7: Update SiqiJoeyPlugin initialization order

```java
@Override
public void onEnable() {
    schedulers = new BukkitSchedulers(this);

    // ... database setup ...

    // Confirmation system (early - other components depend on it)
    var confirmationManager = new ConfirmationManager(this);
    components.add(confirmationManager);

    // ... boss bar setup ...

    // Teleport system
    var backLocationStorage = new BackLocationStorage(storageService);
    var locationTracker = new LocationTracker(this, backLocationStorage);
    components.add(locationTracker);

    var safeTeleporter = new SafeTeleporter(this, config, locationTracker, confirmationManager);
    components.add(safeTeleporter);

    var requestManager = new RequestManager(this, config, safeTeleporter, confirmationManager);
    // Note: RequestManager may no longer need to be in components if it doesn't implement Disposable

    // ... boss bar provider registration ...

    // Commands
    getCommand("back").setExecutor(new BackCommand(this, locationTracker, safeTeleporter));
    getCommand("tp").setExecutor(new TpCommand(this, requestManager));
    getCommand("accept").setExecutor(ConfirmCommands.accept(confirmationManager));
    getCommand("decline").setExecutor(ConfirmCommands.decline(confirmationManager));

    // Home system
    var homeStorage = new HomeStorage(storageService);
    var homeCommand = new HomeCommand(this, homeStorage, safeTeleporter, confirmationManager);
    getCommand("home").setExecutor(homeCommand);

    // ... rest of setup ...
}
```

### Step 8: Expose PREFIX in Messages class

Make the PREFIX component accessible for other classes:

```java
// In Messages.java
public static final Component PREFIX = Component.text("[")
        .color(NamedTextColor.DARK_GRAY)
        .append(Component.text("TP").color(NamedTextColor.AQUA).decorate(TextDecoration.BOLD))
        .append(Component.text("] ").color(NamedTextColor.DARK_GRAY));
```

### Step 9: Test build

```bash
./gradlew clean shadowJar
```

---

## Summary of Changes

| File | Action | Description |
|------|--------|-------------|
| `confirm/ConfirmationRequest.java` | CREATE | Interface for confirmation requests |
| `confirm/ConfirmationManager.java` | CREATE | Core manager with lifecycle handling |
| `confirm/ConfirmCommands.java` | CREATE | /accept and /decline handlers |
| `teleport/commands/YesNoCommands.java` | DELETE | Replaced by ConfirmCommands |
| `teleport/RequestManager.java` | MODIFY | Use ConfirmationManager, remove state tracking |
| `teleport/SafeTeleporter.java` | MODIFY | Add unsafe teleport confirmation |
| `teleport/Messages.java` | MODIFY | Make PREFIX public |
| `home/HomeCommand.java` | MODIFY | Use ConfirmationManager, remove PendingDelete |
| `SiqiJoeyPlugin.java` | MODIFY | Wire up ConfirmationManager |

---

## Future Considerations

1. **Multiple pending requests per player**: Current design allows only one. Could extend to keyed requests if needed.

2. **Sound effects**: Could add optional sound when confirmation appears.

3. **Boss bar integration**: Could show confirmation timeout in boss bar (low priority).

4. **Confirmation categories**: Could add categories to prevent certain confirmations from replacing others (e.g., teleport request shouldn't cancel home delete confirmation).
