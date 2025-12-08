# Siqi Minecraft Plugin

A Bukkit/Paper Spigot plugin for Minecraft 1.21+. This is a small personal plugin with quality-of-life modifications for Joey and Siqi's private server.

## Build & Run

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## Project Structure

```
src/main/java/sh/joey/mc/
├── SiqiJoeyPlugin.java      # Main plugin entry point
├── bossbar/                  # Priority-based boss bar system
├── teleport/                 # Teleportation with warmup/requests
├── home/                     # Home saving and teleportation
├── day/                      # Daily message system
└── welcome/                  # Join messages and MOTD
```

## Architecture & Patterns

### Self-Registering Components
Most components register their own event listeners in their constructor:
```java
public MyComponent(JavaPlugin plugin) {
    plugin.getServer().getPluginManager().registerEvents(this, plugin);
}
```
This keeps registration logic close to the component and simplifies `SiqiJoeyPlugin.onEnable()`.

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

Persistent home locations with sharing support, using per-player file storage.

**Key Classes:**
- `Home` - Record with location data and `Set<UUID> sharedWith`
- `HomeStorage` - Per-player JSON persistence to `data/player-{uuid}/homes.json`
- `HomeCommand` - All `/home` subcommands with tab completion
- `BedHomeListener` - Auto-saves first bed interaction as "home"

**Commands:** `/home [name]`, `/home set [name]`, `/home delete <name>`, `/home list`, `/home share`, `/home unshare`, `/home help`

**Features:**
- Defaults: `/home` → teleport to "home", `/home set` → set "home"
- Delete confirmation with clickable `[Confirm]` / `[Cancel]` buttons
- Handles unloaded worlds gracefully (shows "world not loaded")
- Shared homes accessible via `owner:homename` syntax

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

### Scheduled Tasks
```java
// Delayed task (20 ticks = 1 second)
plugin.getServer().getScheduler().runTaskLater(plugin, () -> { ... }, 20L);

// Repeating task
plugin.getServer().getScheduler().runTaskTimer(plugin, () -> { ... }, 20L, 20L);
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
teleport:
  warmup-seconds: 3
  movement-tolerance-blocks: 0.5
requests:
  timeout-seconds: 60
```

Loaded via `PluginConfig.load(plugin)` into an immutable record.

## Data Files

Player data is stored in per-player directories under `data/`:
```
plugins/JoeySiqi-MC/data/player-{uuid}/
├── homes.json    # Player's saved homes
└── (future files as features are added)
```

**Directory Naming Convention:**
- `player-{uuid}` - Data scoped to a specific player
- Future: `world-{name}`, `biome-{name}`, etc. for different scopes
