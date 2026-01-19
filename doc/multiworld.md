# Multi-World System

Create and manage custom worlds with separate inventories, gamemodes, and game rules.

## Commands

| Command | Description |
|---------|-------------|
| `/world` | List all configured worlds (click to teleport) |
| `/world <name>` | Teleport to the specified world |

## Configuration

Worlds are configured in `config.yml` under the `worlds` section:

```yaml
worlds:
  world:
    dimension: overworld
    gamemode: SURVIVAL
    difficulty: hard
    inventory_group: survival
    pregen_size: 40000
  world_nether:
    dimension: nether
    gamemode: SURVIVAL
    difficulty: hard
    inventory_group: survival
    access: hidden
  world_the_end:
    dimension: end
    gamemode: SURVIVAL
    difficulty: hard
    inventory_group: survival
    access: hidden
    pregen_size: 3000
  superflat:
    dimension: overworld
    gamemode: CREATIVE
    superflat: true
    structures: false
    disable_advancements: true
    time: 6000
    weather: clear
    generator_settings: '{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}'
    difficulty: normal
    game_rules:
      spawn_mobs: false
      advance_time: false
      block_drops: false
    inventory_group: creative
    teleport_warmup: false
```

### Config Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `dimension` | string | `overworld` | World dimension: `overworld`, `nether`, or `end` |
| `gamemode` | string | `SURVIVAL` | Player gamemode: `SURVIVAL`, `CREATIVE`, `ADVENTURE`, `SPECTATOR` |
| `seed` | number | (random) | World seed (only used when creating new worlds) |
| `generator` | string | `default` | World generator: `default` (vanilla) or `void` (empty void world) |
| `superflat` | boolean | `false` | Use superflat terrain (only for new overworld, ignored if `generator` is set) |
| `generator_settings` | string | (none) | Superflat JSON config (see below) |
| `structures` | boolean | `true` | Generate structures (villages, temples, etc.) - only for new worlds |
| `access` | string | `all` | Access control: `all` (anyone with `smp.world`), `permission` (requires `smp.world.<worldname>`), `hidden` (not accessible via `/world`) |
| `difficulty` | string | (server default) | World difficulty: `peaceful`, `easy`, `normal`, `hard` |
| `game_rules` | map | (none) | Game rule overrides (see below) |
| `inventory_group` | string | (world name) | Inventory group name |
| `teleport_warmup` | boolean | `true` | If false, teleports from this world are instant (no countdown) |
| `disable_advancements` | boolean | `false` | If true, players cannot earn advancements in this world |
| `pregen_size` | number | (none) | Pre-generate chunks in a square area centered on spawn (blocks per side) |

## Access Control

The `access` option controls who can use `/world` to teleport to a world:

| Value | Description |
|-------|-------------|
| `all` | Anyone with `smp.world` permission can teleport (default) |
| `permission` | Requires `smp.world.<worldname>` permission |
| `hidden` | World is completely hidden from `/world` list and cannot be teleported to |

**Use cases:**
- `all` - Public worlds everyone can visit (creative, minigames)
- `permission` - Admin-only worlds, VIP areas, or worlds under construction
- `hidden` - Vanilla dimension worlds (nether, end) that players access through portals

**Example:**
```yaml
worlds:
  world_nether:
    dimension: nether
    access: hidden          # Access via portals only
  admin_area:
    access: permission      # Requires smp.world.admin_area
  creative:
    access: all             # Anyone can /world creative
```

## Superflat Generator Settings

The `generator_settings` option requires JSON format (Minecraft 1.18.2+):

```json
{"layers":[{"block":"<block_id>","height":<n>},...], "biome":"<biome_id>"}
```

### JSON Structure

| Field | Description |
|-------|-------------|
| `layers` | Array of layer objects, ordered from bottom to top |
| `layers[].block` | Block ID (e.g., `minecraft:stone`) |
| `layers[].height` | Number of layers of this block |
| `biome` | Biome ID (e.g., `minecraft:plains`) |

### Examples

**Classic Flat** (grass, 2 dirt, bedrock):
```json
{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
```

**Redstone Ready** (sandstone platform):
```json
{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:stone","height":3},{"block":"minecraft:sandstone","height":52}],"biome":"minecraft:desert"}
```

**Void World** (single stone block):
```json
{"layers":[{"block":"minecraft:stone","height":1}],"biome":"minecraft:the_void"}
```

**Water World**:
```json
{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":5},{"block":"minecraft:gravel","height":5},{"block":"minecraft:water","height":90}],"biome":"minecraft:ocean"}
```

### In config.yml

Use single quotes to wrap the JSON string:

```yaml
worlds:
  creative:
    superflat: true
    generator_settings: '{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}'
```

### Online Generators

- [Minecraft Wiki - Superflat](https://minecraft.wiki/w/Superflat)
- [Chunkbase Superflat Generator](https://chunkbase.com/apps/superflat-generator)
- [Misode's Generator](https://misode.github.io/worldgen/flat-world-preset/)

## Void Generator

The `generator: void` option creates a completely empty void world with just a 3x3 bedrock platform at spawn. This works with any dimension, including End (for black sky void worlds).

### Features

- Empty world (all air except spawn platform)
- 3x3 bedrock platform at y=64
- Fixed spawn location on the platform
- Works with all dimensions (overworld, nether, end)
- No structures, caves, or terrain generation

### Example: Admin Void World

```yaml
worlds:
  admin_void:
    dimension: end
    gamemode: CREATIVE
    access: permission
    generator: void
    structures: false
    teleport_warmup: false
    inventory_group: admin_void
    game_rules:
      spawn_mobs: false
      advance_time: false
```

This creates a void world with End dimension (black sky), restricted to players with `smp.world.admin_void` permission.

### Example: Build Competition World

```yaml
worlds:
  build_contest:
    dimension: overworld
    gamemode: CREATIVE
    generator: void
    structures: false
    inventory_group: build_contest
    game_rules:
      spawn_mobs: false
      advance_weather: false
      advance_time: false
    time: 6000
```

A void world with blue sky (overworld dimension), fixed at noon, for building competitions.

## Game Rules

Configure per-world game rules under the `game_rules` section. Boolean rules use `true`/`false`, integer rules use numbers. Game rule names use snake_case (e.g., `spawn_mobs`, `advance_time`).

### Common Boolean Rules

| Rule | Default | Description |
|------|---------|-------------|
| `spawn_mobs` | true | Mobs spawn naturally |
| `advance_time` | true | Time advances (daylight cycle) |
| `advance_weather` | true | Weather changes |
| `mob_griefing` | true | Mobs can modify blocks |
| `keep_inventory` | false | Keep inventory on death |
| `block_drops` | true | Blocks drop items |
| `entity_drops` | true | Entities drop items |
| `immediate_respawn` | false | Skip death screen |
| `natural_health_regeneration` | true | Health regenerates |
| `pvp` | true | Players can damage each other |
| `fall_damage` | true | Fall damage enabled |
| `fire_damage` | true | Fire damage enabled |
| `drowning_damage` | true | Drowning damage enabled |
| `freeze_damage` | true | Powder snow damage enabled |

### Common Integer Rules

| Rule | Default | Description |
|------|---------|-------------|
| `random_tick_speed` | 3 | Speed of random ticks (crop growth, etc.) |
| `respawn_radius` | 10 | Spawn protection radius |
| `max_entity_cramming` | 24 | Max entities before suffocation |

For a complete list, see the [Minecraft Wiki - Game Rules](https://minecraft.wiki/w/Game_rule).

## Inventory Groups

Worlds with the same `inventory_group` share player inventory, ender chest, XP, health, hunger, saturation, and potion effects.

- Default Minecraft worlds (`world`, `world_nether`, `world_the_end`) use the `"default"` group
- If `inventory_group` is not specified, it defaults to the world name (separate inventory)
- When switching between groups, player state is saved and restored
- Potion effects decay in real-time (a 5-minute effect becomes 4 minutes if you return 1 minute later)

### Example: Separate Creative World

```yaml
worlds:
  # Add default world so gamemode switches back when returning
  world:
    gamemode: SURVIVAL
    inventory_group: default

  creative:
    gamemode: CREATIVE
    superflat: true
    structures: false
    generator_settings: '{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}'
    difficulty: peaceful
    game_rules:
      spawn_mobs: false
      advance_time: false
    inventory_group: creative
    teleport_warmup: false
    disable_advancements: true
```

**Note:** Add `world` to config if you want gamemode to switch back to SURVIVAL when returning from creative worlds.

### Example: Pre-generated Worlds

```yaml
worlds:
  # Overworld with 30km pre-generation
  world:
    gamemode: SURVIVAL
    inventory_group: default
    pregen_size: 30000

  # Nether with 5km pre-generation (smaller due to 1:8 scale)
  world_nether:
    gamemode: SURVIVAL
    inventory_group: default
    access: hidden
    pregen_size: 5000

  # Creative world - no pre-generation needed
  creative:
    superflat: true
    gamemode: CREATIVE
    inventory_group: creative
```

When `pregen.enabled: true` in config.yml, chunks will be pre-generated when the server is empty.

### Example: Linked Survival Worlds

```yaml
worlds:
  mining:
    seed: 12345
    difficulty: normal
    inventory_group: survival  # Same group as 'adventure'

  adventure:
    seed: 67890
    difficulty: hard
    inventory_group: survival  # Same group as 'mining'
```

Players moving between `mining` and `adventure` keep their inventory.

## World Creation

- Worlds are created on first server start if they don't exist
- Once created, `seed`, `superflat`, and `generator_settings` have no effect
- `difficulty`, `game_rules`, and `gamemode` are applied every server start
- World files are stored in the server root directory (e.g., `./creative/`)

## Teleportation

The `/world` command uses the safe teleporter system:
- 3-second warmup countdown (shown in boss bar)
- Movement cancels the teleport
- **Remembers your position** - teleports you back to where you were in that world
- Falls back to world spawn if you've never been there
- Creates a `/back` location at departure point

Position is tracked when you:
- Use `/world` to leave a world
- Join the server
- Respawn after death
