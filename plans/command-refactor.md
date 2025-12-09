# Command Handler Refactoring Plan

## 0. CmdExecutor Improvements

Before refactoring command handlers, fix these issues in the `CmdExecutor` class:

### 0.1 Add error handling to onCommand (line 62-65)

**Current:**
```java
@Override
public boolean onCommand(...) {
    handler.handle(plugin, sender, args).subscribe();
    return true;
}
```

**Fixed:**
```java
@Override
public boolean onCommand(...) {
    try {
        handler.handle(plugin, sender, args)
            .subscribe(
                () -> {},
                err -> plugin.getLogger().warning("Command error: " + err.getMessage())
            );
    } catch (Exception e) {
        plugin.getLogger().warning("Command exception: " + e.getMessage());
    }
    return true;
}
```

This handles both RxJava errors (via subscribe callback) and runtime exceptions (via try-catch).

### 0.2 Fix tab complete: edge case + error handling (lines 39-58)

**Problems:**
1. If user types `/home ` (trailing space), `startAt >= buffer.length()` and `tabComplete` is never called
2. If `tabComplete` throws or the Maybe errors, `blockingGet()` throws and tab completion breaks

**Current:**
```java
.subscribe(event -> {
    String buffer = event.getBuffer();
    int startAt = 0;
    while (buffer.length() > startAt && buffer.charAt(startAt) == '/') {
        startAt++;
    }

    startAt += prefix1.length();
    if (startAt < buffer.length()) {
        String[] args = buffer.substring(startAt).split(" ");
        Optional<List<AsyncTabCompleteEvent.Completion>> completions = handler.tabComplete(plugin, event.getSender(), args)
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .blockingGet();
        if (completions.isPresent()) {
            event.setHandled(true);
            event.completions(completions.get());
        }
    }
});
```

**Fixed:**
```java
.subscribe(event -> {
    try {
        String buffer = event.getBuffer();
        int startAt = 0;
        while (buffer.length() > startAt && buffer.charAt(startAt) == '/') {
            startAt++;
        }

        startAt += prefix1.length();
        String remainder = startAt < buffer.length() ? buffer.substring(startAt) : "";
        String[] args = remainder.isEmpty() ? new String[]{""} : remainder.split(" ", -1);

        handler.tabComplete(plugin, event.getSender(), args)
                .onErrorComplete()  // Swallow RxJava errors - tab complete should never crash
                .blockingSubscribe(completions -> {
                    event.setHandled(true);
                    event.completions(completions);
                });
    } catch (Exception e) {
        plugin.getLogger().warning("Tab complete exception: " + e.getMessage());
    }
});
```

Changes:
- Added `onErrorComplete()` to swallow RxJava errors gracefully (tab complete just won't show results)
- Added try-catch for runtime exceptions
- Fixed edge case: always parse args, handle empty remainder
- Using `split(" ", -1)` preserves trailing empty strings, so `/home set ` gives `["set", ""]`
- Keep blocking behavior (required - event expects completions set before handler returns)

### 0.3 Add null check for getCommand() (line 20)

**Current:**
```java
plugin.getCommand(handler.getName()).setExecutor(executor);
```

**Fixed:**
```java
var cmd = plugin.getCommand(handler.getName());
if (cmd == null) {
    throw new IllegalStateException("Command '" + handler.getName() + "' not registered in plugin.yml");
}
cmd.setExecutor(executor);
```

---

## Overview

Refactor all command handlers to use the new `CmdExecutor` / `Command` abstraction.

### The `Command` Interface

```java
public interface Command {
    Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args);

    default Maybe<List<AsyncTabCompleteEvent.Completion>> tabComplete(
            SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.empty();
    }

    String getName();
}
```

### Key Principles

1. **Cold observables**: `handle()` must not execute side effects until subscribed
2. **No `blockingGet()` in implementations**: Return Rx chains, let framework handle blocking
3. **Main thread for game state**: Use `.observeOn(plugin.mainScheduler())` before Bukkit API calls

---

## Commands to Refactor

| Command | Current Class | Tab Complete | DB Access |
|---------|--------------|--------------|-----------|
| `/home` | `HomeCommand` + `HomeTabCompleter` | Yes | Yes |
| `/back` | `BackCommand` | No | Yes |
| `/tp` | `TpCommand` | Yes | No |
| `/accept` | `ConfirmCommands` | No | No |
| `/decline` | `ConfirmCommands` | No | No |

---

## 1. HomeCommand (Most Complex)

### Current Structure
- `HomeCommand` implements `CommandExecutor`
- `HomeTabCompleter` is a separate `Disposable` class
- Multiple subcommands: set, delete, list, share, unshare, help, teleport

### New Structure
- Single class implementing `Command`
- Merge tab completion logic
- Each subcommand returns a `Completable`

### Handle Implementation Pattern

```java
@Override
public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
    return Completable.defer(() -> {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return Completable.complete();
        }

        if (args.length == 0) {
            return handleTeleport(player, "home");
        }

        String subcommand = args[0].toLowerCase();
        return switch (subcommand) {
            case "set" -> handleSet(player, args);
            case "delete" -> handleDelete(player, args);
            case "list" -> handleList(player);
            case "share" -> handleShare(player, args);
            case "unshare" -> handleUnshare(player, args);
            case "help" -> handleHelp(player);
            default -> handleTeleport(player, args[0]);
        };
    });
}
```

### Subcommand Completable Examples

Each helper method must also be cold - wrap in `Completable.defer()` so side effects only happen at subscription time.

**handleSet (simple async):**
```java
private Completable handleSet(Player player, String[] args) {
    return Completable.defer(() -> {
        String name = args.length < 2 ? "home" : HomeStorage.normalizeName(args[1]);
        if (RESERVED_NAMES.contains(name)) {
            error(player, "Cannot use '" + name + "' as a home name.");
            return Completable.complete();
        }

        Home home = new Home(name, player.getUniqueId(), player.getLocation());
        return storage.setHome(player.getUniqueId(), home)
                .observeOn(plugin.mainScheduler())
                .doOnComplete(() -> success(player, "Home '" + name + "' has been set!"))
                .doOnError(err -> logAndError(player, "Failed to set home", err))
                .onErrorComplete();
    });
}
```

**handleList (async with transformation):**
```java
private Completable handleList(Player player) {
    UUID playerId = player.getUniqueId();
    return storage.getHomes(playerId)
            .toList()
            .observeOn(plugin.mainScheduler())
            .doOnSuccess(homes -> displayHomeList(player, playerId, homes))
            .doOnError(err -> logAndError(player, "Failed to list homes", err))
            .onErrorComplete()
            .ignoreElement();
}
```
Note: `handleList` starts with an async operation, so no defer needed - the chain is already cold.

**handleDelete (async with confirmation):**
```java
private Completable handleDelete(Player player, String[] args) {
    return Completable.defer(() -> {
        if (args.length < 2) {
            error(player, "Usage: /home delete <name>");
            return Completable.complete();
        }

        String name = HomeStorage.normalizeName(args[1]);
        UUID playerId = player.getUniqueId();

        return storage.getHome(playerId, name)
                .filter(home -> home.isOwnedBy(playerId))
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(home -> requestDeleteConfirmation(player, name))
                .doOnComplete(() -> error(player, "Home '" + name + "' not found."))
                .doOnError(err -> logAndError(player, "Failed to check home", err))
                .onErrorComplete()
                .ignoreElement();
    });
}

// Note: requestDeleteConfirmation triggers confirmation flow but doesn't
// need to return a Completable - the confirmation callbacks handle the rest
private void requestDeleteConfirmation(Player player, String name) {
    confirmationManager.request(player, new ConfirmationRequest() {
        // ... confirmation request with onAccept doing the actual delete
    });
}
```

**handleUnshare (chained async):**
```java
private Completable handleUnshare(Player player, String[] args) {
    return Completable.defer(() -> {
        if (args.length < 3) {
            error(player, "Usage: /home unshare <name> <player>");
            return Completable.complete();
        }

        String name = HomeStorage.normalizeName(args[1]);
        String targetName = args[2];

        return sessionStorage.resolvePlayerId(targetName)
                .flatMapSingle(targetId -> storage.unshareHome(player.getUniqueId(), name, targetId))
                .observeOn(plugin.mainScheduler())
                .doOnSuccess(unshared -> onUnshareResult(player, targetName, name, unshared))
                .doOnComplete(() -> error(player, "Player '" + targetName + "' not found."))
                .doOnError(err -> logAndError(player, "Failed to unshare home", err))
                .onErrorComplete()
                .ignoreElement();
    });
}
```

**Pattern rule:** Use `Completable.defer()` when the method has early-return paths with side effects (like `error(player, ...)`). If the method immediately returns an async chain (like `storage.getHomes(...)`), no defer is needed since the chain is already cold.

### Tab Completion Implementation

The `CmdExecutor` calls `blockingGet()` on the returned `Maybe`, so implementations just need to return proper Rx chains. Must use `Maybe.defer()` to ensure coldness.

```java
private static final int MAX_COMPLETIONS = 20;

@Override
public Maybe<List<AsyncTabCompleteEvent.Completion>> tabComplete(
        SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
    return Maybe.defer(() -> {
        if (!(sender instanceof Player player)) {
            return Maybe.empty();
        }

        UUID playerId = player.getUniqueId();
        String partial = args.length > 0 ? args[args.length - 1].toLowerCase() : "";
        int argIndex = args.length;

        if (argIndex == 1) {
            return completeFirstArg(playerId, partial);
        } else if (argIndex == 2) {
            return completeSecondArg(playerId, args[0].toLowerCase(), partial);
        } else if (argIndex == 3) {
            return completeThirdArg(player, args[0].toLowerCase(), partial);
        }

        return Maybe.empty();
    });
}
```

**First arg (subcommands + home names):**
```java
private Maybe<List<AsyncTabCompleteEvent.Completion>> completeFirstArg(UUID playerId, String partial) {
    return storage.getHomes(playerId)
            .toList()
            .map(homes -> {
                List<AsyncTabCompleteEvent.Completion> completions = new ArrayList<>();

                // Add matching subcommands
                for (String cmd : RESERVED_NAMES) {
                    if (cmd.startsWith(partial)) {
                        completions.add(Completion.completion(cmd));
                    }
                }

                // Add matching home names
                for (Home home : homes) {
                    String name = formatHomeCompletion(home, playerId);
                    if (name.toLowerCase().startsWith(partial)) {
                        completions.add(Completion.completion(name));
                    }
                }

                return completions;
            })
            .toMaybe();
}
```

**Second arg (owned home names for delete/share/unshare):**
```java
private Maybe<List<AsyncTabCompleteEvent.Completion>> completeSecondArg(
        UUID playerId, String subcommand, String partial) {
    if (!Set.of("delete", "share", "unshare").contains(subcommand)) {
        return Maybe.empty();
    }

    return storage.getHomes(playerId)
            .filter(home -> home.isOwnedBy(playerId))
            .map(Home::name)
            .filter(name -> name.startsWith(partial))
            .toList()
            .map(names -> names.stream()
                    .map(Completion::completion)
                    .toList())
            .toMaybe();
}
```

**Third arg (player names for share/unshare):**
```java
private Maybe<List<AsyncTabCompleteEvent.Completion>> completeThirdArg(
        Player player, String subcommand, String partial) {
    if (!subcommand.equals("share") && !subcommand.equals("unshare")) {
        return Maybe.empty();
    }

    // Database lookup for player names
    return sessionStorage.findUsernamesByPrefix(partial, MAX_COMPLETIONS)
            .toList()
            .map(dbNames -> {
                Set<String> names = new HashSet<>();

                // Add online players (checked at subscription time due to toList())
                for (Player online : Bukkit.getOnlinePlayers()) {
                    String onlineName = online.getName();
                    if (!online.equals(player) &&
                            onlineName.toLowerCase().startsWith(partial.toLowerCase())) {
                        names.add(onlineName);
                    }
                }

                // Add database names (excludes self)
                for (String name : dbNames) {
                    if (!name.equalsIgnoreCase(player.getName())) {
                        names.add(name);
                    }
                }

                return names.stream()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .limit(MAX_COMPLETIONS)
                        .map(Completion::completion)
                        .toList();
            })
            .toMaybe();
}
```

---

## 2. BackCommand

### Current Structure
- Implements `CommandExecutor`
- Async database lookup for back location
- Calls `SafeTeleporter.teleport()`

### New Implementation

```java
public final class BackCommand implements Command {
    private final Logger logger;
    private final LocationTracker locationTracker;
    private final SafeTeleporter safeTeleporter;

    // constructor...

    @Override
    public String getName() {
        return "back";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.defer(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return Completable.complete();
            }

            if (safeTeleporter.hasPendingTeleport(player.getUniqueId())) {
                Messages.warning(player, "You already have a teleport in progress!");
                return Completable.complete();
            }

            return locationTracker.getBackLocation(player.getUniqueId())
                    .observeOn(plugin.mainScheduler())
                    .doOnSuccess(backLocation -> handleBackLocation(player, backLocation))
                    .doOnComplete(() -> Messages.error(player, "You don't have anywhere to go back to!"))
                    .doOnError(err -> {
                        logger.warning("Failed to get back location: " + err.getMessage());
                        Messages.error(player, "Failed to retrieve your back location.");
                    })
                    .onErrorComplete()
                    .ignoreElement();
        });
    }

    private void handleBackLocation(Player player, BackLocation backLocation) {
        // ... existing logic unchanged
    }
}
```

---

## 3. TpCommand

### Current Structure
- Implements `CommandExecutor` and `TabCompleter`
- No database access (online players only)
- Uses `ConfirmationManager` for requests

### New Implementation

```java
public final class TpCommand implements Command {
    // ... fields and constructor

    @Override
    public String getName() {
        return "tp";
    }

    @Override
    public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Completable.fromAction(() -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command can only be used by players.");
                return;
            }

            if (args.length != 1) {
                Messages.error(player, "Usage: /tp <player>");
                return;
            }

            String targetName = args[0];
            Player target = plugin.getServer().getPlayer(targetName);

            if (target == null) {
                Messages.error(player, "Player '" + targetName + "' is not online.");
                return;
            }

            if (target.equals(player)) {
                Messages.error(player, "You can't teleport to yourself!");
                return;
            }

            sendRequest(player, target);
        });
    }

    @Override
    public Maybe<List<AsyncTabCompleteEvent.Completion>> tabComplete(
            SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
        return Maybe.fromCallable(() -> {
            if (args.length != 1) {
                return null; // Becomes empty Maybe
            }

            String prefix = args[0].toLowerCase();
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .filter(name -> !(sender instanceof Player p) || !name.equals(p.getName()))
                    .map(Completion::completion)
                    .toList();
        });
    }

    // sendRequest() unchanged
}
```

---

## 4. ConfirmCommands

### Current Structure
- Factory methods returning `CommandExecutor` lambdas

### New Implementation

```java
public final class ConfirmCommands {
    private ConfirmCommands() {}

    public static Command accept(ConfirmationManager manager) {
        return new Command() {
            @Override
            public String getName() {
                return "accept";
            }

            @Override
            public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
                return Completable.fromAction(() -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("This command can only be used by players.");
                        return;
                    }
                    manager.accept(player);
                });
            }
        };
    }

    public static Command decline(ConfirmationManager manager) {
        return new Command() {
            @Override
            public String getName() {
                return "decline";
            }

            @Override
            public Completable handle(SiqiJoeyPlugin plugin, CommandSender sender, String[] args) {
                return Completable.fromAction(() -> {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("This command can only be used by players.");
                        return;
                    }
                    manager.decline(player);
                });
            }
        };
    }
}
```

---

## 5. SiqiJoeyPlugin Updates

### Current Registration
```java
var tpCommand = new TpCommand(this, config, safeTeleporter, confirmationManager);
getCommand("back").setExecutor(new BackCommand(this, locationTracker, safeTeleporter));
getCommand("tp").setExecutor(tpCommand);
getCommand("tp").setTabCompleter(tpCommand);
getCommand("accept").setExecutor(ConfirmCommands.accept(confirmationManager));
getCommand("decline").setExecutor(ConfirmCommands.decline(confirmationManager));

var homeCommand = new HomeCommand(this, homeStorage, playerSessionStorage, safeTeleporter, confirmationManager);
getCommand("home").setExecutor(homeCommand);

var homeTabCompleter = new HomeTabCompleter(this, homeStorage, playerSessionStorage);
components.add(homeTabCompleter);
```

### New Registration
```java
components.add(CmdExecutor.register(this,
    new BackCommand(this, locationTracker, safeTeleporter)));
components.add(CmdExecutor.register(this,
    new TpCommand(this, config, safeTeleporter, confirmationManager)));
components.add(CmdExecutor.register(this,
    ConfirmCommands.accept(confirmationManager)));
components.add(CmdExecutor.register(this,
    ConfirmCommands.decline(confirmationManager)));
components.add(CmdExecutor.register(this,
    new HomeCommand(this, homeStorage, playerSessionStorage, safeTeleporter, confirmationManager)));

// HomeTabCompleter deleted - merged into HomeCommand
```

---

## Files Changed

| File | Action |
|------|--------|
| `cmd/CmdExecutor.java` | Fix: error handling, tab complete edge case, null check |
| `home/HomeCommand.java` | Refactor: implement `Command`, merge tab completion |
| `home/HomeTabCompleter.java` | **Delete** |
| `teleport/commands/BackCommand.java` | Refactor: implement `Command` |
| `teleport/commands/TpCommand.java` | Refactor: implement `Command` |
| `confirm/ConfirmCommands.java` | Refactor: return `Command` instances |
| `SiqiJoeyPlugin.java` | Update registration to use `CmdExecutor.register()` |

---

## Implementation Order

0. `CmdExecutor` - fix error handling, edge cases, null check
1. `ConfirmCommands` - simplest, no DB, good warmup
2. `TpCommand` - sync only, has tab complete
3. `BackCommand` - simple DB access pattern
4. `HomeCommand` - most complex, do last
5. `SiqiJoeyPlugin` - update all registrations
6. Delete `HomeTabCompleter`
