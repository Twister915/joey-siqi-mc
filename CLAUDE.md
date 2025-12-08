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
├── rx/                       # RxJava integration (schedulers, event observables)
├── storage/                  # PostgreSQL database layer
├── bossbar/                  # Priority-based boss bar system
├── teleport/                 # Teleportation with warmup/requests
├── home/                     # Home saving and teleportation
├── day/                      # Daily message system
├── welcome/                  # Join messages and MOTD
└── world/                    # World monitoring utilities
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
- **Internal state**: `BiomeState`, `PendingDelete`

### Optional for Nullable Returns
Methods that may not have a result return `Optional<T>`:
```java
public Optional<Location> getBackLocation(UUID playerId)
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

### 2. Teleportation System (`teleport/`)

Handles teleport requests between players, warmup countdowns, and location tracking.

**Key Classes:**
- `SafeTeleporter` - Warmup countdown, movement detection, particle/sound effects
- `RequestManager` - Player-to-player teleport requests with expiry
- `LocationTracker` - Tracks death and teleport-from locations for `/back`
- `Messages` - Formatted message utilities with clickable buttons
- `PluginConfig` - Typed config record loaded from `config.yml`

**Commands:** `/tp`, `/accept`, `/decline`, `/back`

**Flow:**
1. Player A runs `/tp PlayerB`
2. `RequestManager` stores pending request, sends clickable accept/decline to Player B
3. Player B clicks `[Accept]` → runs `/accept`
4. `SafeTeleporter.teleport()` starts warmup countdown
5. If player moves beyond tolerance, teleport cancels (shown in boss bar)
6. On success, plays particle effects and records location for `/back`

**State Records:**
- `TeleportState` - Progress calculation for active teleport
- `CancelledState` - Progress calculation for cancellation display (fades over 3 seconds)

### 3. Home System (`home/`)

Persistent home locations with sharing support, stored in PostgreSQL.

**Key Classes:**
- `Home` - Record with location data and `Set<UUID> sharedWith`
- `HomeStorage` - Async PostgreSQL operations via `StorageService`
- `HomeCommand` - All `/home` subcommands (async)
- `BedHomeListener` - Auto-saves first bed interaction as "home"

**Commands:** `/home [name]`, `/home set [name]`, `/home delete <name>`, `/home list`, `/home share`, `/home unshare`, `/home help`

**Features:**
- Defaults: `/home` → teleport to "home", `/home set` → set "home"
- Delete confirmation with clickable `[Confirm]` / `[Cancel]` buttons
- Handles unloaded worlds gracefully (shows "world not loaded")
- Shared homes accessible via `owner:homename` syntax
- All database operations are async (return `Single<T>` or `Completable`)

### 4. Message Systems (`day/`, `welcome/`)

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
// Query that returns a result
public Single<Optional<Home>> getHome(UUID playerId, String name) {
    return storage.query(conn -> {
        // Use PreparedStatement, return result
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
002_add_indexes.sql
003_create_warps.sql
```

- Files are sorted by numeric prefix and run in order
- Tracked in `migration_state` table with filename and SHA-256 checksum
- Each migration runs in a transaction (rollback on failure)
- Run synchronously at plugin startup before any storage components initialize

### Table Conventions

Always include timestamp columns:
```sql
CREATE TABLE example (
    id BIGSERIAL PRIMARY KEY,
    -- ... other columns ...
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### Async Patterns

Since database operations are async, command handlers use `.subscribe()`:

```java
// Bad - trying to use sync API
Optional<Home> home = storage.getHome(playerId, name); // Doesn't compile

// Good - async with subscribe
storage.getHome(playerId, name)
    .subscribe(
        homeOpt -> {
            if (homeOpt.isPresent()) {
                teleportToHome(player, homeOpt.get());
            } else {
                error(player, "Home not found.");
            }
        },
        err -> {
            plugin.getLogger().warning("Database error: " + err.getMessage());
            error(player, "Failed to load home.");
        }
    );
```

For long subscribe bodies, extract to instance methods:
```java
storage.getHome(playerId, name)
    .subscribe(
        homeOpt -> handleHomeResult(player, homeOpt),
        err -> handleDatabaseError(player, err)
    );
```
