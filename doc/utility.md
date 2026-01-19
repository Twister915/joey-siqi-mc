# Utility Commands

Collection of useful server administration and player commands.

## Commands

### Inventory Management

| Command | Permission | Description |
|---------|------------|-------------|
| `/clear [player]` | `smp.clear` | Clear inventory |
| `/item <material> [amount]` | `smp.item` | Give yourself an item |
| `/give <player> <material> [amount]` | `smp.give` | Give item to player |

**Aliases:** `/ci` (clear), `/i` (item)

**Item Aliases:** 100+ shortcuts supported:
- `dpick` - Diamond Pickaxe
- `gapple` - Golden Apple
- `dhelm`, `dchest`, `dlegs`, `dboots` - Diamond Armor
- And many more

### World Control

| Command | Permission | Description |
|---------|------------|-------------|
| `/time <value>` | `smp.time` | Set world time |
| `/weather <type>` | `smp.weather` | Set weather |
| `/seed` | `smp.seed` | Show world seed |

**Time values:** `day`, `night`, `noon`, `midnight`, `sunrise`, `sunset`, or ticks (0-24000)

**Weather types:** `clear`, `rain`, `thunder`

### Entity Management

| Command | Permission | Description |
|---------|------------|-------------|
| `/remove <type\|all> [radius]` | `smp.remove` | Remove entities |

Entity types: `all`, `items`, `mobs`, `animals`, `monsters`, `xp`

Default radius: 50 blocks

### Player Utilities

| Command | Permission | Description |
|---------|------------|-------------|
| `/list` | `smp.list` | List online players |
| `/suicide` | `smp.suicide` | Kill yourself to respawn |
| `/ping [player]` | `smp.ping` | Check connection latency |

### Information

| Command | Permission | Description |
|---------|------------|-------------|
| `/map` | `smp.map` | Get BlueMap web map link |
| `/uptime` | `smp.uptime` | Show server uptime |
| `/last` | `smp.last` | List recently joined players |
| `/seen <player>` | `smp.seen` | When player was last online |

### Special

| Command | Permission | Description |
|---------|------------|-------------|
| `/genstatue <player>` | `smp.statue` | Generate wool statue |
| `/opme` | `smp.opme` | Temporary operator status |

## Confirmation

Commands that destroy items require confirmation in Survival/Adventure mode:
- `/clear`
- `/suicide`

Creative and Spectator modes execute immediately.

## BlueMap Integration

If [BlueMap](https://bluemap.bluecolored.de/) is installed:
- `/map` generates a clickable link centered on your location
- Player markers appear on the web map

Configure the map URL in `config.yml`:

```yaml
map:
  url: "http://localhost:8090"
  view-height: 700
```

## Wool Statues

Generate a massive wool statue of any player:

```
/genstatue PlayerName
```

Features:
- Downloads player skin from Mojang
- 4x scale (128 blocks tall)
- Supports modern and legacy skins
- Integrates with WorldEdit for `//undo`
- Requires confirmation before building

## Permissions

| Permission | Description |
|------------|-------------|
| `smp.clear` | Clear own inventory |
| `smp.clear.others` | Clear others' inventories |
| `smp.item` | Give yourself items |
| `smp.give` | Give items to others |
| `smp.time` | Change world time |
| `smp.weather` | Change weather |
| `smp.list` | List players |
| `smp.suicide` | Respawn command |
| `smp.remove` | Remove entities |
| `smp.seed` | View world seed |
| `smp.map` | View map link |
| `smp.statue` | Generate statues |
| `smp.uptime` | View server uptime |
| `smp.ping` | Check latency |
| `smp.last` | View recent players |
| `smp.seen` | Check last online |
| `smp.opme` | Temporary op |
