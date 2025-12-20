# Multi-World Feature Implementation Plan

## Overview

Add multi-world support with configurable worlds, automatic gamemode switching, and inventory groups that prevent item transfer between worlds.

**Architecture Note:** The inventory snapshot system is **truly decoupled**:
- Inventory module: Just captures and restores player state, returns snapshot IDs
- Multi-world module: Maintains its own pivot table mapping (player, group) → snapshot_id
- Future features (periodic backups, rollbacks) can use their own mappings

## Configuration Format

```yaml
# config.yml addition
worlds:
  creative:
    seed: 12345                    # optional, only for new worlds
    dimension: overworld           # overworld | nether | end
    gamemode: CREATIVE             # SURVIVAL | CREATIVE | ADVENTURE | SPECTATOR
    superflat: true                # optional, only for new worlds
    inventory_group: creative      # worlds with same group share inventory

  survival_nether:
    dimension: nether
    gamemode: SURVIVAL
    inventory_group: survival
```

Default Minecraft worlds (world, world_nether, world_the_end) use `"default"` inventory group.

## File Structure

```
src/main/java/sh/joey/mc/
├── inventory/                           # STANDALONE MODULE - no multi-world knowledge
│   ├── InventorySnapshot.java           # Record: capture(Player) → snapshot, applyTo(Player)
│   └── InventorySnapshotStorage.java    # CRUD: save() → UUID, getById(), listByPlayer()
│
└── multiworld/                          # MULTI-WORLD MODULE - uses inventory module
    ├── WorldConfig.java                 # Record for single world config
    ├── WorldsConfig.java                # Config loader for all worlds
    ├── WorldManager.java                # World loading and lookup
    ├── GamemodeManager.java             # Gamemode switching on world change
    ├── InventoryGroupStorage.java       # Pivot table: (player, group) → snapshot_id
    ├── InventoryGroupManager.java       # Orchestrates group switching
    └── WorldCommand.java                # /world [name] command
```

## Database Schema

### Migration: `006_create_inventory_snapshots.sql`

```sql
-- Pure inventory snapshot storage (no multi-world knowledge)
-- Just stores player state at a point in time

CREATE TABLE inventory_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL,

    -- Raw NBT bytes via Paper's ItemStack.serializeItemsAsBytes()
    inventory_data BYTEA NOT NULL,        -- main inventory (36 slots)
    armor_data BYTEA NOT NULL,            -- armor (4 slots)
    offhand_data BYTEA NOT NULL,          -- offhand (1 slot)
    ender_chest_data BYTEA NOT NULL,      -- ender chest (27 slots)

    -- Player state
    xp_level INT NOT NULL,
    xp_progress REAL NOT NULL,
    health DOUBLE PRECISION NOT NULL,
    max_health DOUBLE PRECISION NOT NULL,
    hunger INT NOT NULL,
    saturation REAL NOT NULL,

    -- Potion effects as JSON
    effects_json JSONB,

    -- Arbitrary metadata (caller-defined)
    labels JSONB NOT NULL DEFAULT '{}',

    -- When the snapshot was taken (used for effect decay)
    snapshot_at TIMESTAMPTZ NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- For listing player's snapshot history
CREATE INDEX idx_snapshots_player_time
    ON inventory_snapshots(player_id, snapshot_at DESC);

-- For querying by labels (e.g., labels->>'source' = 'periodic')
CREATE INDEX idx_snapshots_labels
    ON inventory_snapshots USING GIN (labels);
```

### Migration: `007_create_inventory_group_snapshots.sql`

```sql
-- Multi-world specific: maps (player, group) to current snapshot
-- This is a pivot table owned by the multiworld module

CREATE TABLE inventory_group_snapshots (
    player_id UUID NOT NULL,
    inventory_group VARCHAR(64) NOT NULL,
    snapshot_id UUID NOT NULL REFERENCES inventory_snapshots(id),

    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    PRIMARY KEY (player_id, inventory_group)
);

-- For finding a player's snapshot for a specific group
CREATE INDEX idx_group_snapshots_lookup
    ON inventory_group_snapshots(player_id, inventory_group);
```

## Key Design Decisions

### Inventory State Saved Per Snapshot
- Inventory (36 slots), armor (4), offhand (1)
- Ender chest contents (27 slots)
- XP level + progress
- Health, max health, hunger, saturation
- Active potion effects (with real-time decay on restore)
- Arbitrary labels (JSONB) for caller-defined metadata

### True Decoupling
The inventory snapshot system has **no knowledge** of multi-world, groups, or any specific use case:
- It just captures player state → returns snapshot ID
- It retrieves snapshots by ID → applies to player
- Callers (like multi-world) maintain their own mappings

### Labels JSONB
Callers can attach arbitrary metadata to snapshots:
```json
{"source": "group_switch", "from_group": "survival", "to_group": "creative"}
{"source": "periodic", "interval": 30}
{"source": "manual", "reason": "before boss fight"}
{"source": "admin", "admin": "joey", "reason": "griefing rollback"}
```

### Potion Effect Real-Time Decay
When restoring effects:
1. Calculate elapsed time since `snapshot_at`
2. Subtract elapsed time from each effect's remaining duration
3. Only apply effects with positive remaining time

This preserves "spirit of survival" - potions expire naturally.

### Error Handling Strategy
On database failure during inventory switch:
- Player keeps their current inventory (no items lost)
- Log warning and notify player
- This is the safest approach - old inventory is already saved

### First-Time Entry to Group
When no snapshot exists for a group:
- Clear inventory, ender chest
- Set XP to 0
- Set health to max, hunger to 20, saturation to 5
- No potion effects

## Component Responsibilities

### Inventory Module (standalone, reusable)

#### InventorySnapshot (record)
- Immutable representation of player state at a point in time
- Contains: id, playerId, inventory, armor, offhand, enderChest, XP, health, hunger, saturation, effects, labels, snapshotAt
- Static method: `capture(Player, Map<String, Object> labels)` → `InventorySnapshot`
- Instance method: `applyTo(Player, boolean decayEffects)`

#### InventorySnapshotStorage
- Pure PostgreSQL CRUD - no business logic
- `save(InventorySnapshot)` → `Single<UUID>` (returns snapshot ID)
- `getById(snapshotId)` → `Maybe<InventorySnapshot>`
- `listByPlayer(playerId, limit, offset)` → `Flowable<InventorySnapshot>`
- `deleteById(snapshotId)` → `Completable`

### Multi-World Module (uses inventory module)

#### InventoryGroupStorage
- Manages the `inventory_group_snapshots` pivot table
- `getSnapshotForGroup(playerId, group)` → `Maybe<UUID>` (snapshot ID)
- `setSnapshotForGroup(playerId, group, snapshotId)` → `Completable`
- `clearGroup(playerId, group)` → `Completable`

#### WorldManager
- Loads/creates configured worlds on plugin enable
- Provides world lookup by name
- Maps worlds to inventory groups (unconfigured worlds → "default")

#### GamemodeManager
- Watches `PlayerChangedWorldEvent`
- Sets player gamemode based on destination world config

#### InventoryGroupManager
- Watches `PlayerChangedWorldEvent`
- If group changed:
  1. Capture current inventory → save to inventory_snapshots → get snapshotId
  2. Update inventory_group_snapshots for source group
  3. Look up snapshot for target group
  4. If found: load and apply; if not: clear inventory
- Tracks pending transitions to handle rapid world changes

#### WorldCommand
- `/world` - lists available worlds with click-to-teleport
- `/world <name>` - teleports via SafeTeleporter (respects warmup)

## Event Flow

```
/world creative
    ↓
WorldCommand validates world exists
    ↓
SafeTeleporter.teleport(player, spawn, callback)
    ↓
Warmup countdown (boss bar)
    ↓
player.teleport(destination)
    ↓
PlayerChangedWorldEvent fires
    ↓
GamemodeManager: sets gamemode

InventoryGroupManager (if fromGroup != toGroup):
    ↓
1. Capture current inventory
    snapshot = InventorySnapshot.capture(player, {"source": "group_switch", ...})
    ↓
2. Save snapshot, get ID
    snapshotId = snapshotStorage.save(snapshot)  // → Single<UUID>
    ↓
3. Update pivot table for source group
    groupStorage.setSnapshotForGroup(playerId, fromGroup, snapshotId)
    ↓
4. Look up target group's snapshot
    targetSnapshotId = groupStorage.getSnapshotForGroup(playerId, toGroup)
    ↓
5a. If found: load and apply
    snapshot = snapshotStorage.getById(targetSnapshotId)
    snapshot.applyTo(player, decayEffects=true)
    ↓
5b. If not found: first time in group
    clearInventory(player)
```

## Integration Notes

### /back and /home
- Both use SafeTeleporter → trigger PlayerChangedWorldEvent
- Inventory switching happens automatically
- No special handling needed in teleport code

### /back Location Tracking
- LocationTracker already saves departure location before teleport
- Works correctly across inventory groups

## Implementation Order

### Phase 1: Inventory Module (standalone)
1. **Migration 006** - create inventory_snapshots table
2. **InventorySnapshot** - record with capture/apply methods
3. **InventorySnapshotStorage** - PostgreSQL CRUD (save → UUID, getById, etc.)

### Phase 2: Multi-World Module
4. **Migration 007** - create inventory_group_snapshots pivot table
5. **WorldConfig + WorldsConfig** - configuration records
6. **WorldManager** - world loading on plugin enable
7. **GamemodeManager** - gamemode switching
8. **InventoryGroupStorage** - pivot table CRUD
9. **InventoryGroupManager** - orchestrates group switching
10. **WorldCommand** - /world command

### Phase 3: Optional Enhancements (later)
- **PeriodicBackupManager** - scheduled backups using labels: `{"source": "periodic"}`
- **Rollback commands** - admin commands to list/restore snapshots

## Critical Files to Modify

- `src/main/java/sh/joey/mc/SiqiJoeyPlugin.java` - register new components
- `src/main/resources/config.yml` - add worlds section
- `src/main/resources/plugin.yml` - add /world command

## New Files to Create

### Inventory Module (2 files)
- `src/main/java/sh/joey/mc/inventory/InventorySnapshot.java`
- `src/main/java/sh/joey/mc/inventory/InventorySnapshotStorage.java`

### Multi-World Module (7 files)
- `src/main/java/sh/joey/mc/multiworld/WorldConfig.java`
- `src/main/java/sh/joey/mc/multiworld/WorldsConfig.java`
- `src/main/java/sh/joey/mc/multiworld/WorldManager.java`
- `src/main/java/sh/joey/mc/multiworld/GamemodeManager.java`
- `src/main/java/sh/joey/mc/multiworld/InventoryGroupStorage.java`
- `src/main/java/sh/joey/mc/multiworld/InventoryGroupManager.java`
- `src/main/java/sh/joey/mc/multiworld/WorldCommand.java`

### Migrations (2 files)
- `src/main/resources/migrations/006_create_inventory_snapshots.sql`
- `src/main/resources/migrations/007_create_inventory_group_snapshots.sql`

## Edge Cases Handled

| Scenario | Handling |
|----------|----------|
| Database failure | Player keeps current inventory, error logged |
| Player quits mid-transition | Pending transition map cleaned up on PlayerQuitEvent |
| Rapid world changes | Only latest transition applied (tracked in pending map) |
| First time in group | Empty inventory, full health, no effects |
| Server restart | Potion decay uses snapshot timestamp from database |
| Death in other group | Normal respawn mechanics, may trigger another group switch |

## Serialization Details

### ItemStack Serialization (Paper API)

Paper provides native NBT serialization that matches vanilla Minecraft's format:

```java
// Serialize inventory to raw NBT bytes
byte[] inventoryBytes = ItemStack.serializeItemsAsBytes(player.getInventory().getStorageContents());
byte[] armorBytes = ItemStack.serializeItemsAsBytes(player.getInventory().getArmorContents());
byte[] offhandBytes = ItemStack.serializeItemsAsBytes(new ItemStack[]{player.getInventory().getItemInOffHand()});
byte[] enderBytes = ItemStack.serializeItemsAsBytes(player.getEnderChest().getContents());

// Deserialize back to ItemStack arrays
ItemStack[] inventory = ItemStack.deserializeItemsFromBytes(inventoryBytes);
ItemStack[] armor = ItemStack.deserializeItemsFromBytes(armorBytes);
ItemStack[] offhand = ItemStack.deserializeItemsFromBytes(offhandBytes);
ItemStack[] enderChest = ItemStack.deserializeItemsFromBytes(enderBytes);
```

**Why this approach:**
- Uses Minecraft's native NBT format (same as player.dat files)
- Includes DataVersion for automatic data migration when Minecraft updates
- Handles null items correctly (serializes as empty)
- No custom serialization logic to maintain

### Potion Effect Serialization

Store as JSON array in PostgreSQL's JSONB column:

```java
record EffectData(String id, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {}

// Capture effects
List<EffectData> effects = player.getActivePotionEffects().stream()
    .map(e -> new EffectData(
        e.getType().getKey().toString(),  // e.g., "minecraft:speed"
        e.getDuration(),                   // ticks remaining
        e.getAmplifier(),                  // 0 = level I, 1 = level II
        e.isAmbient(),
        e.hasParticles(),
        e.hasIcon()
    ))
    .toList();

// Convert to JSON string for database
String effectsJson = new Gson().toJson(effects);
```

### Real-Time Effect Decay on Restore

```java
Instant snapshotAt = /* from database */;
long elapsedMs = Duration.between(snapshotAt, Instant.now()).toMillis();
int elapsedTicks = (int) (elapsedMs / 50);  // 50ms per tick

for (EffectData effect : effects) {
    int remainingDuration = effect.duration() - elapsedTicks;
    if (remainingDuration > 0) {
        player.addPotionEffect(new PotionEffect(
            PotionEffectType.getByKey(NamespacedKey.fromString(effect.id())),
            remainingDuration,
            effect.amplifier(),
            effect.ambient(),
            effect.particles(),
            effect.icon()
        ));
    }
    // Effects with non-positive duration are simply not applied (expired)
}
```

## Future: Rollback Commands

Once the inventory module is in place, adding rollback commands is straightforward:

```
/inventory history [player]        - List snapshots (query by labels if needed)
/inventory restore <snapshot-id>   - Restore to specific snapshot
/inventory backup [player]         - Create manual backup with labels: {"source": "manual"}
```

These would use the existing `InventorySnapshotStorage` methods directly.

## Sources

- [Paper API ItemStack docs](https://jd.papermc.io/paper/1.21.11/org/bukkit/inventory/ItemStack.html)
- [Minecraft Wiki player.dat format](https://minecraft.wiki/w/Player.dat_format)
- [Minecraft Wiki Effect command](https://minecraft.wiki/w/Commands/effect)
