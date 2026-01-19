# Cosmetics

Elytra trails, resource packs, and nicknames.

## Elytra Trails

Particle effects that spawn behind you while flying.

### Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/trails` | - | Show trail categories |
| `/trails elytra` | `smp.trails.elytra` | Show elytra trail options |
| `/trails elytra <effect>` | `smp.trails.elytra` | Select an effect |
| `/trails elytra <effect> <intensity>` | `smp.trails.elytra` | Effect with intensity |
| `/trails elytra off` | `smp.trails.elytra` | Disable trails |

### Available Effects

| Effect | Description |
|--------|-------------|
| `flame` | Fire particles |
| `soul` | Soul fire particles |
| `end` | End rod sparkles |
| `enchant` | Enchantment glyphs |
| `heart` | Heart particles |
| `note` | Musical notes |
| `totem` | Golden sparkles |
| `cherry` | Cherry petals |
| `dragon` | Dragon breath |
| `spark` | Firework sparks |
| `snow` | Snowflakes |
| `witch` | Witch magic |
| `rainbow` | Cycling colors |
| `rgb:RRGGBB` | Custom hex color |

### Intensity Levels

| Level | Particles | Rate |
|-------|-----------|------|
| `low` | 2 | Every 5 ticks |
| `medium` | 4 | Every 3 ticks |
| `high` | 6 | Every 2 ticks |

### Custom Colors

Use any hex color with the `rgb:` prefix:

```
/trails elytra rgb:ff5500     # Orange
/trails elytra rgb:00ff88     # Mint green
/trails elytra rgb:ff00ff     # Magenta
```

### Other Trail Types

Additional trail types may be available:
- **Ghast trails** (`smp.trails.ghast`) - While riding happy ghasts
- **Walk trails** (`smp.trails.walk`) - While walking

## Resource Packs

Manage server resource pack preferences.

### Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/resourcepack` | `smp.resourcepack` | List available packs |
| `/resourcepack select <pack>` | `smp.resourcepack` | Apply a pack |
| `/resourcepack clear` | `smp.resourcepack` | Remove pack |

**Alias:** `/rp`

### Features

- Automatically applies your preferred pack on join
- Saves preference across sessions
- Waits until you stop gliding to send pack

## Nicknames

Customize your display name.

### Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/nick` | `smp.nick` | Show current nickname |
| `/nick <name>` | `smp.nick` | Set your nickname |
| `/nick clear` | `smp.nick` | Remove nickname |
| `/nick <player> <name>` | `smp.nick.others` | Set another's nickname |

### Validation Rules

Nicknames must:
- Be 3-16 characters
- Use only letters, numbers, and underscores
- Not match existing usernames
- Not match another player's nickname

### Display Integration

Your nickname appears in:
- Chat messages
- Tab list
- Above your head (nameplate)

## Tab List

The tab list automatically shows:

**Header:**
```
✦ play.example.com ✦
Welcome to the server!
```

**Footer:**
```
TPS: 20.0 │ 5 players │ Ping: 45ms
```

TPS color indicates server health (green/yellow/red).

## Permissions

| Permission | Description |
|------------|-------------|
| `smp.trails.elytra` | Use elytra trails |
| `smp.trails.ghast` | Use ghast trails |
| `smp.trails.walk` | Use walk trails |
| `smp.resourcepack` | Manage resource packs |
| `smp.nick` | Set own nickname |
| `smp.nick.others` | Set others' nicknames |
