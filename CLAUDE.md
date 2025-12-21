# Siqi Minecraft Plugin

A Bukkit/Paper Spigot plugin for Minecraft 1.21+. This is a small personal plugin with quality-of-life modifications for Joey and Siqi's private server.

## Build & Run

```bash
./gradlew shadowJar
```

The compiled JAR will be in `build/libs/` (use the `-all.jar` file which includes all dependencies).

## Project Structure

```
src/main/java/sh/joey/mc/
├── SiqiJoeyPlugin.java      # Main plugin entry point
├── Json.java                 # Shared Gson instance
├── rx/                       # RxJava integration (schedulers, event observables)
├── storage/                  # PostgreSQL database layer
├── cmd/                      # Command abstraction utilities
├── bossbar/                  # Priority-based boss bar system
├── confirm/                  # Unified confirmation system for yes/no prompts
├── teleport/                 # Teleportation with warmup/requests
├── home/                     # Home saving and teleportation
├── session/                  # Player session tracking
├── day/                      # Daily message system
├── welcome/                  # Join/leave/chat messages, MOTD
├── death/                    # Custom death messages
├── world/                    # World monitoring utilities
├── multiworld/               # Multi-world management
├── inventory/                # Inventory snapshot storage
├── pagination/               # Chat pagination utilities
└── messages/                 # Shared message generation (word banks)
```

## Architecture & Patterns

### RxJava Event System (PREFERRED)

**Always use the RxJava event system instead of Bukkit's Listener/EventHandler pattern.**

The plugin provides reactive wrappers around Bukkit's event and scheduler systems via `SiqiJoeyPlugin`. These should always be preferred over direct Bukkit APIs.

#### Watching Events
```java
// Single event type
plugin.watchEvent(PlayerJoinEvent.class)
    .subscribe(event -> handleJoin(event));

// With event priority
plugin.watchEvent(EventPriority.MONITOR, PlayerMoveEvent.class)
    .filter(event -> isRelevant(event))
    .subscribe(this::handleMove);

// Multiple event types
plugin.watchEvent(PlayerJoinEvent.class, PlayerQuitEvent.class)
    .subscribe(event -> handlePlayerChange(event));
```

#### Scheduled Tasks
```java
// Periodic task (runs on main thread)
plugin.interval(1, TimeUnit.SECONDS)
    .subscribe(tick -> checkSomething());

// One-shot delayed task
plugin.timer(5, TimeUnit.SECONDS)
    .subscribe(tick -> doSomethingLater());
```

**Why prefer RxJava over Bukkit APIs:**
- Automatic cleanup on plugin disable
- Composable with operators (filter, map, debounce, etc.)
- Type-safe disposal via `CompositeDisposable`
- Consistent patterns across the codebase
- `plugin.interval()` and `plugin.timer()` run directly on Bukkit's scheduler without thread switching overhead

#### Component Pattern
Components should implement `Disposable` and track their subscriptions:
```java
public final class MyComponent implements Disposable {
    private final CompositeDisposable disposables = new CompositeDisposable();

    public MyComponent(SiqiJoeyPlugin plugin) {
        disposables.add(plugin.watchEvent(SomeEvent.class)
            .subscribe(this::handle));

        disposables.add(plugin.interval(5, TimeUnit.SECONDS)
            .subscribe(tick -> periodicCheck()));
    }

    @Override
    public void dispose() {
        disposables.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposables.isDisposed();
    }
}
```

The plugin tracks all components in a root `CompositeDisposable` and disposes them on disable.

### Records for Immutable Data
Java records are used extensively for:
- **Configuration**: `PluginConfig`, `BossBarState`
- **Data models**: `Home`, `PendingTeleport`, `TeleportState`
- **Internal state**: `BiomeState`, `PendingRequest`

### RxJava Types for Async Results
Database operations return RxJava types:
- `Maybe<T>` - Zero or one result (replaces `Optional<T>` for async)
- `Single<T>` - Exactly one result
- `Flowable<T>` - Multiple results (replaces `List<T>` for async)
- `Completable` - No result, just success/failure

```java
public Maybe<Home> getHome(UUID playerId, String name)      // 0 or 1
public Single<Boolean> hasAnyHomes(UUID playerId)           // exactly 1
public Flowable<Home> getHomes(UUID playerId)               // 0 to many
public Completable setHome(UUID playerId, Home home)        // no result
```

### Optional for Sync Nullable Returns
Synchronous methods that may not have a result return `Optional<T>`:
```java
public Optional<Location> findSafeLocation(Location destination)
public Optional<BossBarState> getState(Player player)
```

### UUID-Keyed Maps for Player State
Player-specific state is stored in `Map<UUID, T>` rather than using Player objects:
```java
private final Map<UUID, PendingTeleport> pendingTeleports = new HashMap<>();
private final Map<UUID, Long> cancelledTeleports = new HashMap<>();
```

### Cleanup on Player Quit / World Unload
Components clean up player state in `PlayerQuitEvent` handlers and world state in `WorldUnloadEvent` handlers to prevent memory leaks.

---

## Major Systems

### 1. Boss Bar System (`bossbar/`)

A priority-based system where multiple providers can supply boss bar content, and the highest-priority provider with content wins.

**Key Classes:**
- `BossBarProvider` - Interface with `getPriority()` and `getState(Player)`
- `BossBarState` - Immutable record: `(title, color, progress, style)`
- `BossBarManager` - Polls providers each tick, updates per-player boss bars

**Priority Ranges:**
- `0-99`: Background/ambient (TimeOfDayProvider)
- `100-199`: Context-aware (BiomeChangeProvider, WeatherChangeProvider, LodestoneCompassProvider)
- `200+`: Important notifications (TeleportCountdownProvider)

**Providers:**
| Provider | Priority | Description |
|----------|----------|-------------|
| `TimeOfDayProvider` | 10 | Shows time with sun/moon icons |
| `LodestoneCompassProvider` | 100 | Direction/distance when holding lodestone compass |
| `BiomeChangeProvider` | 150 | Shows biome name on change (with debounce) |
| `WeatherChangeProvider` | 150 | Announces weather changes |
| `TeleportCountdownProvider` | 200 | Teleport warmup progress + cancellation |

**Debounce Pattern** (BiomeChangeProvider):
Tracks both "confirmed" and "pending" biome to prevent spam when walking along biome borders. Only confirms after staying in new biome for debounce period.

### 2. Confirmation System (`confirm/`)

A unified system for handling all yes/no confirmations with consistent UI and lifecycle management.

**Key Classes:**
- `ConfirmationRequest` - Interface for confirmation prompts with display methods and callbacks
- `ConfirmationManager` - Manages pending requests per player, handles timeouts and player quit
- `ConfirmCommands` - `/accept` and `/decline` command handlers

**Features:**
- One pending request per player (new requests replace old ones)
- Automatic receiver quit tracking (built into ConfirmationManager)
- Custom sender invalidation via `Completable invalidation()`
- Exception guardrails - callbacks wrapped in try/catch to prevent crashes
- Consistent message format with prefix, clickable buttons, and hover text

**Lifecycle Callbacks:**
- `onAccept()` - Player clicked accept
- `onDecline()` - Player clicked decline
- `onTimeout()` - Request expired
- `onInvalidate()` - External condition ended request (e.g., sender quit)
- `onReplaced()` - New request replaced this one

**Usage Example:**
```java
confirmationManager.request(player, new ConfirmationRequest() {
    @Override
    public Component prefix() { return PREFIX; }

    @Override
    public String promptText() { return "Delete this item?"; }

    @Override
    public String acceptText() { return "Delete"; }

    @Override
    public String declineText() { return "Cancel"; }

    @Override
    public void onAccept() { deleteItem(); }

    @Override
    public void onDecline() { info(player, "Cancelled."); }

    @Override
    public int timeoutSeconds() { return 30; }
});
```

### 3. Teleportation System (`teleport/`)

Handles teleport requests between players, warmup countdowns, location tracking, and safe teleportation.

**Key Classes:**
- `SafeTeleporter` - Warmup countdown, movement detection, safe location finding, unsafe teleport confirmation, particle/sound effects
- `TpCommand` - `/tp <player>` command with teleport request handling via ConfirmationManager
- `LocationTracker` - Tracks death and teleport-from locations for `/back` (persisted to PostgreSQL)
- `BackLocationStorage` - Async PostgreSQL operations for back locations
- `BackLocation` - Record with location type (DEATH or TELEPORT)
- `Messages` - Formatted message utilities with PREFIX constant
- `PluginConfig` - Typed config record loaded from `config.yml`

**Commands:** `/tp`, `/accept`, `/decline`, `/back`

**Safety Features:**
- Prevents teleporting while in a vehicle
- Finds safe landing spots to prevent suffocation (searches up to 10 blocks vertically)
- If no safe spot found, prompts for confirmation before teleporting

**Flow:**
1. Player A runs `/tp PlayerB`
2. `TpCommand` sends confirmation request to Player B via `ConfirmationManager`
3. Player B clicks `[Accept]` → runs `/accept`
4. `SafeTeleporter.teleport()` checks for safe location
5. If unsafe, prompts Player A to confirm; if safe, starts warmup countdown
6. If player moves beyond tolerance during warmup, teleport cancels (shown in boss bar)
7. On success, plays particle effects and records departure location for `/back`

**Back Location Behavior:**
- Death locations are automatically saved
- Teleport departure locations are saved before each teleport
- `/back` is symmetric: going back sets a new back location to where you came from
- Back locations persist to PostgreSQL across server restarts

**State Records:**
- `TeleportState` - Progress calculation for active teleport
- `CancelledState` - Progress calculation for cancellation display (fades over 3 seconds)

### 4. Home System (`home/`)

Persistent home locations with sharing support, stored in PostgreSQL with soft delete.

**Key Classes:**
- `Home` - Record with `id` (UUID), location data, and `Set<UUID> sharedWith`
- `HomeStorage` - Async PostgreSQL operations via `StorageService`
- `HomeCommand` - All `/home` subcommands (async)
- `BedHomeListener` - Auto-saves first bed interaction as "home"
- `HomeTabCompleter` - Tab completion for home names

**Commands:** `/home [name]`, `/home set [name]`, `/home delete <name>`, `/home list`, `/home share`, `/home unshare`, `/home help`

**Features:**
- Defaults: `/home` → teleport to "home", `/home set` → set "home"
- Home names are normalized (lowercase + trimmed) via `HomeStorage.normalizeName()`
- Delete confirmation via unified ConfirmationManager with `[Delete]` / `[Cancel]` buttons
- Handles unloaded worlds gracefully (shows "world not loaded")
- Shared homes accessible via `owner:homename` syntax
- All database operations are async (return `Maybe<T>`, `Flowable<T>`, or `Completable`)

**Soft Delete & UUID Identity:**
- Each home has a UUID `id` (primary key) rather than `(player_id, name)`
- Deleting a home sets `deleted_at` timestamp rather than removing the row
- Replacing a home (same name, new location) soft-deletes the old one and inserts a new row
- Shares reference `home_id`, so they're scoped to a specific home version
- Partial unique index ensures only one active home per `(player_id, name)`

### 5. Player Session System (`session/`)

Tracks player connections for player ID lookups, online time calculation, and crash recovery.

**Key Classes:**
- `PlayerSessionStorage` - Async PostgreSQL operations for session data
- `PlayerSessionTracker` - Manages session lifecycle: joins, disconnects, heartbeat

**Features:**
- Records player joins with UUID, username, IP, online mode, and timestamps
- Periodic heartbeat (30 seconds) updates `last_seen_at` for active sessions
- Graceful handling of server crashes via orphan session cleanup on startup
- Player ID lookup by username (case-insensitive) without `Bukkit.getOfflinePlayer()`
- Username lookup by player ID for display purposes

**Server Session ID:**
Each server run generates a unique `serverSessionId` UUID. This identifies which sessions belong to the current run vs. previous runs that may have crashed.

**Orphan Session Cleanup:**
On plugin init, any sessions from previous server runs (different `server_session_id`) that weren't properly closed have their `disconnected_at` set to `last_seen_at`. This runs as a blocking operation using `.blockingGet()` to ensure cleanup completes before the plugin accepts player joins.

**Database Views:**
- `player_names` - Current username for each player (most recent session)
- `player_name_history` - All usernames a player has used with date ranges (handles A→B→A)
- `player_online_time` - Total online time per player

**Lookup Methods:**
```java
// Find player UUID by username (case-insensitive)
storage.findPlayerIdByName("SomePlayer")
    .subscribe(
        playerId -> { ... },
        err -> { ... },
        () -> { /* not found */ }
    );

// Find username by player UUID
storage.findUsernameById(playerId)
    .subscribe(
        username -> { ... },
        err -> { ... },
        () -> { /* not found */ }
    );
```

### 6. Message Systems (`day/`, `welcome/`)

Themed messages using a mix of static, procedural, and context-aware generation.

**DayMessageProvider** (`day/`):
- Sends message at start of each Minecraft day (time 0)
- Also triggers when entering a world during dawn
- Tracks per-world to avoid duplicate messages

**JoinMessageProvider** (`welcome/`):
- Sends personalized message on player join
- Context: time of day, weather, dimension, health, first-time players

**ServerPingProvider** (`welcome/`):
- Dynamic MOTD in server list
- Context: night, storms, empty server, player count

**Message Generation Pattern:**
```java
int roll = random.nextInt(100);
if (roll < 30) {
    return getContextMessage(player);  // Context-aware
} else if (roll < 60) {
    return getProceduralMessage();     // Template + word banks
} else {
    return getStaticMessage();         // Pre-written
}
```

### 7. Command System (`cmd/`)

A minimal abstraction for command registration with automatic disposal.

**Key Classes:**
- `Command` - Interface with `execute(sender, args)`, `name()`, and `tabComplete()`
- `CmdExecutor` - Registers commands and returns `Disposable` for cleanup

**Usage:**
```java
components.add(CmdExecutor.register(plugin, new MyCommand(plugin)));
```

**Pattern:** One class per command, implements `Command` interface. Commands are disposable and cleaned up on plugin disable.

### 8. Multi-World System (`multiworld/`)

Create and manage custom worlds with separate inventories, gamemodes, and game rules. See [doc/multiworld.md](doc/multiworld.md) for configuration.

**Key Classes:**
- `WorldManager` - Creates/loads worlds from config, manages world lifecycle
- `WorldConfig` - Per-world settings record (dimension, gamemode, seed, rules)
- `WorldsConfig` - Loads all world configs from `config.yml`
- `InventoryGroupManager` - Saves/restores player inventory when switching groups
- `GamemodeManager` - Sets player gamemode on world change
- `WorldPositionTracker` - Tracks player positions for world return
- `PlayerWorldPositionStorage` - PostgreSQL storage for positions
- `WorldCommand` - `/world` command for listing and teleporting

**Features:**
- Separate inventory groups per world
- Per-world gamemode enforcement
- Position memory (return to where you were)
- Custom game rules per world
- Instant teleport option (skip warmup)

### 9. Custom Message Providers

Components that customize Minecraft's default messages to match the plugin's visual style.

**ConnectionMessageProvider** (`welcome/`):
- Replaces join/leave broadcasts with `[+] PlayerName` / `[-] PlayerName`
- Uses green plus, red minus, gray brackets and name

**DeathMessageProvider** (`death/`):
- Replaces all vanilla death messages with 150+ custom variants
- Color scheme: victim `GRAY`, killer `RED`, message text `DARK_GRAY`
- Categories: environmental, entity attacks, PvP, explosions, projectiles, misc
- `DeathMessages` class contains all variant lists organized by `DamageCause`

**ChatMessageProvider** (`welcome/`):
- Formats chat as `PlayerName: message`
- Uses Paper's `AsyncChatEvent` with custom renderer
- Colors: name `GRAY`, colon `DARK_GRAY`, message `WHITE`

---

## Common Conventions

### Text Formatting
Uses Adventure API (`net.kyori.adventure.text`):
```java
Component.text("Hello").color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD)
```

Legacy color codes for boss bars (Bukkit API uses String):
```java
ChatColor.translateAlternateColorCodes('&', "&b&lTeleporting...")
```

### Clickable Messages
```java
Component.text("[Accept]")
    .color(NamedTextColor.GREEN)
    .clickEvent(ClickEvent.runCommand("/accept"))
    .hoverEvent(HoverEvent.showText(Component.text("Click to accept")))
```

### Scheduled Tasks (PREFER RxJava)
Use `plugin.interval()` and `plugin.timer()` from the RxJava event system (see above) instead of direct Bukkit scheduler calls.

```java
// PREFERRED: Use RxJava
plugin.interval(1, TimeUnit.SECONDS).subscribe(tick -> { ... });
plugin.timer(5, TimeUnit.SECONDS).subscribe(tick -> { ... });

// AVOID: Direct Bukkit scheduler (use only in rx package internals)
// plugin.getServer().getScheduler().runTaskLater(plugin, () -> { ... }, 20L);
```

### Message Prefixes
Each system has a consistent prefix:
- Teleport: `[TP]` (aqua)
- Home: `[Home]` (light purple)
- Day: `[☀]` (gold/yellow)
- Join: `[★]` (gold/yellow)
- World: `[World]` (gold)

---

## Configuration

`config.yml`:
```yaml
database:
  host: localhost
  port: 5432
  database: minecraft
  username: minecraft
  password: secret
  pool-size: 3

teleport:
  warmup-seconds: 3
  movement-tolerance-blocks: 0.5
requests:
  timeout-seconds: 60
```

Loaded via `DatabaseConfig.load(plugin)` and `PluginConfig.load(plugin)` into immutable records.

---

## PostgreSQL Storage (`storage/`)

All persistent data is stored in PostgreSQL using async operations.

### Key Classes

- `DatabaseConfig` - Connection settings record loaded from `config.yml`
- `DatabaseService` - HikariCP connection pool management
- `MigrationRunner` - Runs SQL migrations from `resources/migrations/`
- `StorageService` - RxJava async wrapper for database operations
- `SqlFunction<T, R>` - Functional interface that throws `SQLException`
- `SqlConsumer<T>` - Functional interface for void operations

### StorageService Usage

```java
// Query that returns 0 or 1 result
public Maybe<Home> getHome(UUID playerId, String name) {
    return storage.queryMaybe(conn -> {
        // Return result or null (null becomes empty Maybe)
    });
}

// Query that returns exactly 1 result
public Single<Boolean> hasAnyHomes(UUID playerId) {
    return storage.query(conn -> {
        // Use PreparedStatement, return result
    });
}

// Query that returns multiple results
public Flowable<Home> getHomes(UUID playerId) {
    return storage.queryFlowable(conn -> {
        // Return List<T>
    });
}

// Operation that doesn't return a value
public Completable setHome(UUID playerId, Home home) {
    return storage.execute(conn -> {
        // Use PreparedStatement
    });
}
```

Operations run on `Schedulers.io()` and results are observed on the main thread.

### Migrations

SQL migrations live in `src/main/resources/migrations/` and follow the pattern:
```
001_create_homes.sql
002_create_back_locations.sql
003_homes_composite_primary_key.sql
004_home_soft_delete.sql
005_create_player_sessions.sql
006_create_inventory_snapshots.sql
007_create_inventory_group_snapshots.sql
008_create_player_world_positions.sql
009_world_positions_use_uuid.sql
```

- Files are sorted by numeric prefix and run in order
- Tracked in `migration_state` table with filename and SHA-256 checksum
- Each migration runs in a transaction (rollback on failure)
- Run synchronously at plugin startup before any storage components initialize
- **Checksum verification**: If a migration file is modified after being applied, startup fails with an error

### Table Conventions

Include timestamp columns where useful:
```sql
CREATE TABLE example (
    -- Use composite primary key when natural key exists
    player_id UUID NOT NULL,
    name VARCHAR(64) NOT NULL,
    PRIMARY KEY (player_id, name),
    -- ... other columns ...
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

Prefer composite primary keys (e.g., `(player_id, name)`) over surrogate IDs when the natural key is stable and meaningful.

### Async Patterns

Since database operations are async, command handlers use `.subscribe()`:

```java
// Maybe<T> has 3 callbacks: onSuccess, onError, onComplete (empty)
storage.getHome(playerId, name)
    .subscribe(
        home -> teleportToHome(player, home),           // onSuccess - home found
        err -> {                                         // onError
            plugin.getLogger().warning("Database error: " + err.getMessage());
            error(player, "Failed to load home.");
        },
        () -> error(player, "Home not found.")          // onComplete - no home
    );
```

For long subscribe bodies, extract to instance methods:
```java
storage.getHome(playerId, name)
    .subscribe(
        home -> handleHomeFound(player, home),
        err -> handleDatabaseError(player, err),
        () -> handleHomeNotFound(player)
    );
```

For `Flowable<T>`, collect to list if needed:
```java
storage.getHomes(playerId)
    .toList()
    .subscribe(
        homes -> displayHomeList(player, homes),
        err -> handleDatabaseError(player, err)
    );
```
- Can you stop doing this pattern where you call .subscribe inside the handler of other .subscribe? You should almost never do this.
- I have changed the database code to not always go back to the main thread. It is up to the caller to observe on the main thread when doing database operations. You will need to go back to the main thread (`observeOn(plugin.mainThread()`) when you need to affect things happening in the game as a result of an async operation.