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
  creative:
    dimension: overworld
    gamemode: CREATIVE
    superflat: true
    generator_settings: '{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}'
    difficulty: peaceful
    game_rules:
      doMobSpawning: false
      doDaylightCycle: false
    inventory_group: creative
    teleport_warmup: false
    disable_advancements: true
```

### Config Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `dimension` | string | `overworld` | World dimension: `overworld`, `nether`, or `end` |
| `gamemode` | string | `SURVIVAL` | Player gamemode: `SURVIVAL`, `CREATIVE`, `ADVENTURE`, `SPECTATOR` |
| `seed` | number | (random) | World seed (only used when creating new worlds) |
| `superflat` | boolean | `false` | Use superflat terrain (only for new overworld) |
| `generator_settings` | string | (none) | Superflat JSON config (see below) |
| `structures` | boolean | `true` | Generate structures (villages, temples, etc.) - only for new worlds |
| `hidden` | boolean | `false` | If true, world is hidden from `/world` list and cannot be teleported to directly |
| `difficulty` | string | (server default) | World difficulty: `peaceful`, `easy`, `normal`, `hard` |
| `game_rules` | map | (none) | Game rule overrides (see below) |
| `inventory_group` | string | (world name) | Inventory group name |
| `teleport_warmup` | boolean | `true` | If false, teleports from this world are instant (no countdown) |
| `disable_advancements` | boolean | `false` | If true, players cannot earn advancements in this world |

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

## Game Rules

Configure per-world game rules under the `game_rules` section. Boolean rules use `true`/`false`, integer rules use numbers.

### Common Boolean Rules

| Rule | Default | Description |
|------|---------|-------------|
| `doMobSpawning` | true | Mobs spawn naturally |
| `doDaylightCycle` | true | Time advances |
| `doWeatherCycle` | true | Weather changes |
| `doFireTick` | true | Fire spreads |
| `mobGriefing` | true | Mobs can modify blocks |
| `keepInventory` | false | Keep inventory on death |
| `doTileDrops` | true | Blocks drop items |
| `doEntityDrops` | true | Entities drop items |
| `doImmediateRespawn` | false | Skip death screen |
| `naturalRegeneration` | true | Health regenerates |
| `pvp` | true | Players can damage each other |
| `fallDamage` | true | Fall damage enabled |
| `fireDamage` | true | Fire damage enabled |
| `drowningDamage` | true | Drowning damage enabled |
| `freezeDamage` | true | Powder snow damage enabled |

### Common Integer Rules

| Rule | Default | Description |
|------|---------|-------------|
| `randomTickSpeed` | 3 | Speed of random ticks (crop growth, etc.) |
| `spawnRadius` | 10 | Spawn protection radius |
| `maxEntityCramming` | 24 | Max entities before suffocation |

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
      doMobSpawning: false
      doDaylightCycle: false
    inventory_group: creative
    teleport_warmup: false
    disable_advancements: true
```

**Note:** Add `world` to config if you want gamemode to switch back to SURVIVAL when returning from creative worlds.

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
