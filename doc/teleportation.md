# Teleportation System

Complete teleportation features including player-to-player teleport, warps, spawn, random teleport, and back locations.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/tp <player>` | `smp.tp` | Request to teleport to another player |
| `/tphere <player>` | `smp.tphere` | Request a player to teleport to you |
| `/accept` | - | Accept a pending request |
| `/decline` | - | Decline a pending request |
| `/back` | `smp.back` | Return to death or previous teleport location |
| `/warp [name]` | `smp.warp` | List warps or teleport to a warp |
| `/warp set <name>` | `smp.warp.set` | Create or update a warp |
| `/warp delete <name>` | `smp.warp.set` | Delete a warp |
| `/spawn` | `smp.spawn` | Teleport to world spawn |
| `/setspawn` | `smp.setspawn` | Set world spawn point |
| `/rtp` | `smp.rtp` | Generate 5 random location options |
| `/rtp select <1-5>` | `smp.rtp` | Teleport to selected location |

## How It Works

### Warmup System

All teleports have a warmup countdown (default 3 seconds):
- Movement cancels the teleport
- Horizontal tolerance: 0.5 blocks
- Vertical tolerance: 1.0 blocks (allows jumping)
- Cancellation displays for 3 seconds then fades

**Skip warmup with:**
- `smp.tp.instant` permission
- World config with `teleport_warmup: false`

### Safe Teleportation

Before teleporting, the system checks if the destination is safe:
- Searches up to 10 blocks vertically for safe ground
- Avoids lava, fire, and soul fire
- If no safe spot found, prompts for confirmation

### Back Locations

The `/back` command returns you to either:
- **Death location** - Saved automatically when you die
- **Teleport-from location** - Saved before each teleport

Going back creates a new back location, so you can return to where you came from.

## Random Teleport (RTP)

Find new unexplored areas for resource gathering.

### How It Works

1. Run `/rtp` to generate 5 random locations
2. Each location shows:
   - Biome name
   - Distance from spawn (e.g., "2.4km NE")
   - Vague hint about the area
3. Click a location or use `/rtp select <1-5>` to teleport

### Safety Features

Excludes dangerous biomes:
- All ocean variants
- Mushroom fields
- Deep dark
- The void

Additional checks:
- No nearby surface lava (5-block radius)
- Safe landing spot (solid ground, passable feet/head)
- Valid Y-level bounds

### Cooldown

Default 5-minute cooldown between uses. Bypass with `smp.rtp.bypass` permission.

## Warps

Server-wide teleport points accessible to all players.

### Managing Warps

Create warps with `/warp set <name>` at your current location. Names are:
- Case-insensitive (stored lowercase)
- Cannot be `set`, `delete`, or `list` (reserved)

Delete warps with `/warp delete <name>`.

### Using Warps

- `/warp` - Shows clickable list of all warps
- `/warp <name>` - Teleport to the warp

## Spawn

Each world can have a custom spawn point.

- `/spawn` - Teleport to current world's spawn
- `/setspawn` - Set spawn at your location

Custom spawns preserve exact rotation (yaw/pitch), unlike vanilla spawn which only saves position.

## Configuration

```yaml
teleport:
  warmup-seconds: 3
  movement-tolerance-blocks: 0.5

requests:
  timeout-seconds: 60

rtp:
  cooldown-minutes: 5
  search-radius: 25000
  min-distance: 500
  candidate-count: 5
  candidate-timeout-seconds: 120
  chunk-timeout-seconds: 3
```

## Permissions

| Permission | Description |
|------------|-------------|
| `smp.tp` | Send teleport requests |
| `smp.tp.instant` | Skip warmup countdown |
| `smp.tphere` | Request players teleport to you |
| `smp.back` | Use back command |
| `smp.warp` | Use warps |
| `smp.warp.set` | Create and delete warps |
| `smp.spawn` | Teleport to spawn |
| `smp.setspawn` | Set world spawn |
| `smp.rtp` | Use random teleport |
| `smp.rtp.bypass` | Bypass RTP cooldown |
