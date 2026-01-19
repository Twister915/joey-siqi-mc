# Siqi & Joey's Minecraft Plugin

<img src="server-icon.png" alt="Siqi's Minecraft Avatar" width="128" align="right">

A cozy Paper/Spigot plugin with quality-of-life features for a small private Minecraft server. Built for my wife Siqi and me to enjoy our adventures together.

**This plugin was almost entirely coded with [Claude Code](https://claude.ai/claude-code).**

## Features

### Teleportation & Navigation
- **Player Teleport** - `/tp`, `/tphere` with warmup and safe landing
- **Homes** - Save locations with `/home set`, share with friends
- **Warps & Spawn** - Server-wide warp points and per-world spawns
- **Random Teleport** - `/rtp` for exploring new areas
- **Back** - Return to death or previous location

[Full documentation](doc/teleportation.md) | [Home system](doc/homes.md)

### Multi-World System
Separate worlds with independent inventories, gamemodes, and game rules.

[Full documentation](doc/multiworld.md)

### Player Customization
- **Settings** - Keep inventory, easy mode, display preferences
- **Cosmetics** - Elytra trails, resource packs, nicknames
- **Merit System** - Levels and progression

[Settings](doc/settings.md) | [Cosmetics](doc/cosmetics.md) | [Merit](doc/merit.md)

### Communication
- **Steve AI** - Ask `@Steve` Minecraft questions in chat
- **Private Messages** - `/msg` and `/reply`
- **Custom Messages** - Styled death, join/leave, and chat messages

[Steve](doc/steve.md) | [Messages](doc/messages.md)

### Administration
- **Permissions** - Groups, player overrides, display attributes
- **Punishments** - Ban, kick, mute, warn with duration support
- **Whitelist** - Custom whitelist with invite tracking
- **Admin Mode** - Creative mode while preserving survival inventory

[Permissions](doc/permissions.md) | [Punishments](doc/punishments.md) | [Admin Mode](doc/adminmode.md)

### Quality of Life
- **Boss Bar** - Time, biome, weather, teleport countdown
- **Majority Sleep** - Skip night when most players sleep
- **Utility Commands** - `/clear`, `/item`, `/time`, `/weather`, etc.
- **BlueMap Integration** - `/map` for web map links

[Utility commands](doc/utility.md) | [All commands](doc/commands.md)

## Top Commands

| Command | Description |
|---------|-------------|
| `/tp <player>` | Request teleport to player |
| `/home [name]` | Teleport to home |
| `/back` | Return to previous location |
| `/rtp` | Random teleport |
| `/settings` | Player settings menu |
| `/world [name]` | Switch worlds |
| `/msg <player> <message>` | Private message |

[Full command reference](doc/commands.md)

## Requirements

- Paper/Spigot 1.21+
- Java 21+
- PostgreSQL 14+

### Optional
- [BlueMap](https://bluemap.bluecolored.de/) - Web map integration
- [FastAsyncWorldEdit](https://www.spigotmc.org/resources/fast-async-worldedit.13932/) - Undo support for statues
- [Anthropic API Key](https://console.anthropic.com/) - Steve AI with web search
- [LM Studio](https://lmstudio.ai/) - Local LLM for Steve AI

## Quick Start

```bash
# Build
./gradlew shadowJar

# Install
cp build/libs/*-all.jar /path/to/server/plugins/
```

Configure `plugins/SiqiJoeyPlugin/config.yml`:

```yaml
database:
  host: localhost
  port: 5432
  database: minecraft
  username: minecraft
  password: secret
```

[Full configuration reference](doc/configuration.md)

## Documentation

- [Documentation Index](doc/README.md) - All feature documentation
- [Commands Reference](doc/commands.md) - Complete command list
- [Configuration Reference](doc/configuration.md) - All config options
- [CLAUDE.md](CLAUDE.md) - Architecture guide for developers

## License

[MIT License](LICENSE) - Copyright (c) 2025 Joseph Sacchini

---

*Made with love for Siqi, and with a lot of help from Claude.*
