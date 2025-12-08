# Siqi & Joey's Minecraft Plugin

<img src="server-icon.png" alt="Siqi's Minecraft Avatar" width="128" align="right">

A cozy Paper/Spigot plugin with quality-of-life features for a small private Minecraft server. Built for my wife Siqi and me to enjoy our adventures together.

**This plugin was almost entirely coded with [Claude Code](https://claude.ai/claude-code).**

## Features

### Boss Bar System
A priority-based boss bar that displays contextual information:
- **Time of Day** - Shows current Minecraft time with sun/moon icons
- **Biome Changes** - Displays biome name when entering new areas (with debounce to prevent spam at borders)
- **Weather Changes** - Announces rain and thunderstorms
- **Lodestone Compass** - Shows direction and distance when holding a lodestone compass
- **Teleport Countdown** - Progress bar during teleport warmup

### Teleportation System
- `/tp <player>` - Request to teleport to another player
- `/accept` / `/decline` - Respond to teleport requests (clickable buttons)
- `/back` - Return to your previous location after teleporting
- Warmup countdown with movement cancellation
- Particle effects (smoke + portal) and sound effects on teleport

### Home System
- `/home` - Teleport to your default home
- `/home <name>` - Teleport to a named home
- `/home set [name]` - Set a home (defaults to "home")
- `/home delete <name>` - Delete a home (with clickable confirmation)
- `/home list` - List all homes with distances
- `/home share <name> <player>` - Share a home with another player
- `/home unshare <name> <player>` - Revoke shared access
- **Auto-home**: Right-clicking a bed for the first time automatically sets your default home

### Daily Messages
Themed messages at the start of each Minecraft day:
- 55+ static messages (greetings, fortunes, wise sayings, humor)
- 15 procedural templates with word banks for variety
- Context-aware messages based on weather, biome, moon phase, and dimension

### Welcome System
**Player Join Messages** - Personalized greetings when players log in:
- Time-of-day aware ("You join as dusk falls...")
- Weather commentary
- First-time player detection
- Health/hunger warnings

**Dynamic MOTD** - Server list shows rotating messages:
- Context-aware (night, storms, empty server)
- Mix of inviting, humorous, and mysterious messages

## Requirements

- Paper/Spigot 1.21+
- Java 21+

## Building

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## Installation

1. Build the plugin or download from releases
2. Place the JAR in your server's `plugins/` folder
3. Restart the server

## Configuration

Configuration is stored in `plugins/SiqiJoeyPlugin/config.yml`:

```yaml
teleport:
  warmup-seconds: 5        # Countdown before teleporting
  request-timeout-seconds: 60  # How long teleport requests last
  movement-tolerance-blocks: 0.5  # How far you can move during warmup
```

## Data Storage

Player data is stored in per-player directories:
```
plugins/JoeySiqi-MC/data/player-{uuid}/homes.json
```

This structure:
- Prevents race conditions when multiple players modify their homes
- Allows easy backup/restore of individual player data
- Scales well as more features are added

## License

[MIT License](LICENSE) - Copyright (c) 2025 Joseph Sacchini

---

*Made with love for Siqi, and with a lot of help from Claude.*
