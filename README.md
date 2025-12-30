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
- `/accept` / `/decline` - Respond to confirmation requests (clickable buttons)
- `/back` - Return to your death location or previous teleport location (persists across restarts)
- Warmup countdown with movement cancellation
- Safe teleportation: prevents suffocation by finding safe landing spots
- Unsafe destination confirmation: prompts before teleporting to potentially dangerous locations
- Cannot teleport while in a vehicle
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

### Multi-World System
Create and manage custom worlds with separate configurations. See [doc/multiworld.md](doc/multiworld.md) for details.
- `/world` - List all worlds (click to teleport)
- `/world <name>` - Teleport to a specific world
- **Separate inventories** - Different inventory groups per world
- **Per-world gamemodes** - Survival in main world, creative in build world
- **Position memory** - Returns you to where you were in each world

### Private Messages
- `/msg <player> <message>` - Send a private message (aliases: `/m`, `/t`, `/tell`, `/whisper`, `/pm`, `/w`)
- `/reply <message>` - Reply to the last person who messaged you (alias: `/r`)
- Messages persist across sessions - you can reply even after relogging

### Nickname System
- `/nick <name>` - Set your display name
- `/nick clear` - Remove your nickname
- `/nick <player> <name>` - Set another player's nickname (requires `smp.nick.others`)
- Supports color codes and formatting
- Nicknames appear in chat, tab list, and above player heads

### Wool Statue Generator
- `/genstatue <player>` - Generate a massive wool statue of any player
- Downloads the player's skin from Mojang
- 4x scale (128 blocks tall)
- Supports both modern (64x64) and legacy (64x32) skins from 2012+
- Integrates with FastAsyncWorldEdit/WorldEdit for `//undo` support
- Confirmation prompt before building

### Admin Mode
Toggle creative mode while preserving your survival inventory. See [doc/adminmode.md](doc/adminmode.md) for details.
- `/adminmode` - Toggle admin mode on/off
- Saves and restores inventory, XP, health, hunger, and effects
- **Configurable permissions** - Grant WorldEdit, WorldGuard, etc. only while in admin mode
- Blocked from portals and cross-world teleports while active

### Chunk Pre-generation
Pre-generate world chunks to reduce lag when players explore new areas:
- `/pregen` or `/pregen status` - Show current pre-generation status
- `/pregen start` - Start pre-generation
- `/pregen stop` - Stop and reset pre-generation
- `/pregen pause` - Pause pre-generation (resumes with `/pregen start`)
- `/pregen force` - Toggle forced mode (runs at slow speed even with players online)
- `/pregen monitor` - Toggle boss bar progress display

**Features:**
- **Adaptive rate** - Faster when server is empty, slower with players online
- **Progress persistence** - Resumes from where it left off after server restart
- **Multiple worlds** - Configures which worlds to pre-generate in `config.yml`
- **Size estimation** - Shows estimated map file size in status output
- **Spiral pattern** - Generates chunks outward from world spawn

### Custom Messages
All server messages styled to match the plugin's visual theme:
- **Join/Leave**: `[+] PlayerName` (green plus) / `[-] PlayerName` (red minus)
- **Death**: 150+ humorous variants for all death types
- **Chat**: `PlayerName: message` format with gray name

### Majority Sleep
- When a majority of players in a world are sleeping, the night is skipped
- Shows a boss bar with sleep progress
- Excludes AFK players from the count

### Warp System
- `/warp` - List all warps (click to teleport)
- `/warp <name>` - Teleport to a warp point
- `/warp set <name>` - Create or update a warp point (requires permission)
- `/warp delete <name>` - Delete a warp point (requires permission)

### Spawn System
- `/spawn` - Teleport to the world's spawn point
- `/setspawn` - Set the spawn point for the current world (requires permission)

### Random Teleport
Find new unexplored areas for resource gathering:
- `/rtp` - Generate 5 random location options in the overworld
- `/rtp select <1-5>` - Teleport to a chosen location
- Each option shows biome name, distance from spawn, cardinal direction, and a vague hint
- Clickable list - just click to teleport!
- **Safety**: Excludes oceans, mushroom fields, deep dark, and checks for nearby lava
- **Scalable**: Works with search radius up to 25,000+ blocks using async chunk loading
- **Cooldown**: 5-minute cooldown between uses (admins can bypass with `smp.rtp.bypass`)
- **First-join tip**: New players automatically receive a tip about `/rtp`

### Player Settings
Per-player gameplay customization:
- `/settings` - Open the settings menu with clickable options

**Available Settings:**
| Setting | Options | Description |
|---------|---------|-------------|
| Keep Inventory | On/Off | Preserve items and XP when you die |
| Display Time | Always/Clock/Never | When to show the time in the boss bar |
| Easy Mode | On/Off | Mobs deal 25% damage + 5% chance to insta-kill mobs |

- Each setting requires a permission (`smp.settings.keepinventory`, `smp.settings.displaytime`, `smp.settings.easymode`)
- Settings you don't have permission for are hidden from the menu
- **Easy Mode** includes fun effects: heart particles, level-up sound, and action bar messages like "Critical hit!" and "One-shot!"
- Easy Mode only affects mob combat - PvP damage is unchanged

### Steve - AI Minecraft Expert
An AI chatbot powered by Claude that answers Minecraft questions:
- Mention `@Steve` anywhere in chat to ask a question
- Steve searches the Minecraft Wiki for accurate, up-to-date answers
- Responses appear in chat like a regular player message
- Clickable source links let you read more on the wiki
- Shows cost per query (typically 1-3¢)

**Example:**
```
Joey: Hey @Steve, how do I find pandas?
Steve: thinking...
Steve: Pandas spawn in jungle biomes on grass blocks with 2+ blocks of space above them, more common in bamboo jungles. ([1] [2] | 1.2¢)
```

**Configuration** in `config.yml`:
```yaml
steve:
  enabled: true
  api-key: "sk-ant-..."  # Your Anthropic API key
  cooldown-seconds: 30    # Per-player cooldown
  max-searches: 3         # Web searches per question
```

### Resource Pack System
- `/resourcepack` - List available resource packs
- `/resourcepack select <pack>` - Apply a resource pack (alias: `/rp`)
- `/resourcepack clear` - Remove your current resource pack
- Server remembers your preference across sessions

### Elytra Trail Effects
Particle effects that spawn behind you while flying with elytra:
- `/trails` - Open the trails menu
- `/trails elytra` - Show elytra trail options
- `/trails elytra <effect>` - Select a trail effect
- `/trails elytra <effect> <intensity>` - Select with intensity (low/medium/high)
- `/trails elytra off` - Disable elytra trails
- **12 built-in effects**: flame, soul fire, end sparkles, enchantment, hearts, notes, totem, cherry petals, dragon breath, firework sparks, snowflakes, witch magic
- **Rainbow mode**: Cycles through rainbow colors
- **Custom RGB**: Use any hex color with `rgb:RRGGBB` (e.g., `rgb:ff5500`)
- **Intensity levels**: Low (subtle), medium (default), high (dense)

### BlueMap Integration
If [BlueMap](https://bluemap.bluecolored.de/) is installed:
- `/map` - Get a clickable link to the web map centered on your location
- Player markers show on the map in real-time

### Utility Commands
- `/tphere <player>` - Request to teleport a player to you
- `/clear [player]` - Clear your inventory (with confirmation in survival mode)
- `/ci` - Alias for `/clear`
- `/item <material> [amount]` - Give yourself an item
- `/i` - Alias for `/item`
- `/give <player> <material> [amount]` - Give a player an item
- `/time <day|night|noon|midnight|sunrise|sunset|ticks>` - Set the world time
- `/weather <clear|rain|thunder>` - Set the weather
- `/list` - Show online players
- `/suicide` - Kill yourself to respawn (with confirmation in survival mode)
- `/remove <type|all> [radius]` - Remove entities around you
- `/seed` - Show the world seed
- `/whois <player>` - Look up player information (UUID, first/last seen, online time)

Item commands support 100+ aliases for common items (e.g., `dpick` for diamond pickaxe, `gapple` for golden apple).

### World Shortcuts
Quick teleport commands for configured worlds:
- `/survival` - Teleport to the survival world
- `/creative` - Teleport to the creative world
- `/superflat` - Teleport to the superflat world

### Player Stats
- `/ontime` - View your current session time and total playtime

### Permissions System
- `/perm group <name> create` - Create a permission group
- `/perm group <name> set <permission> true/false` - Grant/deny permissions
- `/perm group <name> add <player>` - Add player to group
- `/perm player <name> set <permission> true/false` - Set player-specific permissions
- `/perm reload` - Reload permission cache

Permission-based prefix/suffix display in chat and above player names. Groups support priority ordering for inheritance.

### Punishments System
Full punishment management with duration support and history tracking:
- `/ban <player> [reason]` - Permanently ban a player
- `/tempban <player> <duration> [reason]` - Temporarily ban a player (e.g., `1d`, `2w`, `3h`)
- `/unban <player>` - Remove a player's ban
- `/ipban <player> [reason]` - Ban a player by IP address (also bans their account)
- `/unipban <player>` - Remove an IP ban
- `/kick <player> [reason]` - Kick a player from the server
- `/mute <player> [reason]` - Permanently mute a player
- `/tempmute <player> <duration> [reason]` - Temporarily mute a player
- `/unmute <player>` - Remove a player's mute
- `/warn <player> [reason]` - Issue a warning to a player
- `/punishments [player]` - View punishment history (paginated)

Duration format: `1d` (days), `2w` (weeks), `3h` (hours), `30m` (minutes), `1mo` (months), `1y` (years)

Banned players see a detailed kick message showing reason, duration, and who banned them.

### Whitelist System
Custom whitelist that replaces vanilla whitelist with invite tracking:
- `/invite <player>` - Add a player to the whitelist
- `/whitelist add <player>` - Admin: add a player
- `/whitelist remove <player>` - Admin: remove a player
- `/whitelist list [page]` - Admin: list all whitelisted players
- `/whitelist audit [player] [page]` - Admin: view who invited whom

**Invite Broadcasts**: When a non-whitelisted player tries to join, all players with `smp.invite` permission see a clickable message with `[Invite]` and `[Ignore]` buttons. Rate limited to 5 broadcasts per player per 5 minutes.

**Configuration**: Set `whitelist.enabled: false` in `config.yml` to disable whitelist enforcement (allow all players).

## Permissions

All commands use the `smp.` permission prefix. Key permissions:

| Permission | Description | Default |
|------------|-------------|---------|
| `smp.*` | All SMP permissions | op |
| `smp.tp` | Send teleport requests | everyone |
| `smp.tp.instant` | Teleport instantly without warmup | op |
| `smp.tphere` | Request to teleport players to you | everyone |
| `smp.back` | Return to death/teleport location | everyone |
| `smp.home` | Use home commands | everyone |
| `smp.world` | Navigate between worlds | everyone |
| `smp.msg` | Send and receive private messages | everyone |
| `smp.nick` | Set your own nickname | everyone |
| `smp.nick.others` | Set other players' nicknames | op |
| `smp.whois` | Look up basic player information | everyone |
| `smp.whois.admin` | Look up detailed player info (IP, history) | op |
| `smp.clear` | Clear your own inventory | everyone |
| `smp.clear.others` | Clear other players' inventories | op |
| `smp.item` | Give yourself items | op |
| `smp.give` | Give items to others | op |
| `smp.time` | Change world time | op |
| `smp.weather` | Change weather | op |
| `smp.warp` | Teleport to warps | everyone |
| `smp.warp.set` | Create/delete warps | op |
| `smp.spawn` | Teleport to spawn | everyone |
| `smp.setspawn` | Set spawn point | op |
| `smp.rtp` | Use random teleport | everyone |
| `smp.rtp.bypass` | Bypass RTP cooldown | op |
| `smp.settings.keepinventory` | Toggle keep inventory on death | op |
| `smp.settings.displaytime` | Configure time display in boss bar | everyone |
| `smp.settings.easymode` | Enable easy mode (reduced damage, insta-kills) | op |
| `smp.steve` | Ask Steve questions in chat | everyone |
| `smp.suicide` | Kill yourself to respawn | everyone |
| `smp.remove` | Remove entities | op |
| `smp.seed` | View world seed | op |
| `smp.map` | View the web map link | everyone |
| `smp.resourcepack` | Manage resource pack preferences | everyone |
| `smp.trails.elytra` | Use elytra trail particle effects | op |
| `smp.adminmode` | Toggle admin creative mode | op |
| `smp.statue` | Generate wool statues | op |
| `smp.perm.admin` | Manage permissions | op |
| `smp.pregen` | Control chunk pre-generation | op |
| `smp.ban` | Ban/unban players | op |
| `smp.ipban` | IP ban/unban players | op |
| `smp.kick` | Kick players | op |
| `smp.mute` | Mute/unmute players | op |
| `smp.warn` | Warn players | op |
| `smp.punishments` | View punishment history | op |
| `smp.invite` | Invite players to the whitelist | everyone |
| `smp.whitelist` | Manage whitelist (add/remove/list/audit) | op |

## Requirements

- Paper/Spigot 1.21+
- Java 21+
- PostgreSQL 14+

### Optional Dependencies
- **[BlueMap](https://bluemap.bluecolored.de/)** - Enables `/map` command and player markers
- **[FastAsyncWorldEdit](https://www.spigotmc.org/resources/fast-async-worldedit.13932/) or WorldEdit** - Enables `//undo` for statues
- **[Anthropic API Key](https://console.anthropic.com/)** - Enables Steve AI chatbot (costs ~1-3¢ per query)

## Building

```bash
./gradlew shadowJar
```

The compiled JAR will be in `build/libs/` (use the `-all.jar` file which includes all dependencies).

## Installation

1. Build the plugin or download from releases
2. Place the JAR in your server's `plugins/` folder
3. Restart the server

## Configuration

Configuration is stored in `plugins/SiqiJoeyPlugin/config.yml`:

```yaml
database:
  host: localhost
  port: 5432
  database: minecraft
  username: minecraft
  password: secret
  pool-size: 3

teleport:
  warmup-seconds: 5
  movement-tolerance-blocks: 0.5

requests:
  timeout-seconds: 60

rtp:
  cooldown-minutes: 5
  search-radius: 25000
  min-distance: 500

whitelist:
  enabled: true  # Set to false to allow all players

adminmode:
  permissions:  # Permissions granted while in admin mode
    - worldedit.*
    - worldguard.*
```

## Data Storage

All persistent data is stored in PostgreSQL:
- **homes** - Player home locations with sharing support
- **home_shares** - Tracks which homes are shared with which players
- **back_locations** - Death and teleport-from locations for `/back`
- **player_sessions** - Player connection history for ID lookups and online time tracking
- **inventory_snapshots** - Player inventory, health, XP saves per inventory group
- **inventory_group_snapshots** - Tracks which inventory group each player is in
- **player_world_positions** - Last position in each world for position memory
- **player_last_worlds** - Tracks last world each player was in
- **perm_groups** - Permission groups with priority, default flag, and display attributes
- **group_permissions** - Permission grants per group (global or world-scoped)
- **player_permissions** - Player-specific permission overrides
- **perm_players** - Player display attributes (prefix/suffix)
- **player_groups** - Player-to-group membership
- **warps** - Named warp locations
- **world_spawns** - Per-world spawn points
- **rtp_cooldowns** - Random teleport cooldown tracking
- **player_settings** - Per-player settings (keep inventory, display time, easy mode)
- **player_resource_packs** - Player resource pack preferences
- **nicknames** - Player display name customizations
- **private_messages** - Private message history for `/reply`
- **admin_mode_state** - Preserved inventory state for admin mode
- **punishments** - Unified table for bans, IP bans, mutes, kicks, and warns with duration tracking
- **whitelist** - Custom whitelist with invite tracking
- **migration_state** - Tracks applied database migrations

Database migrations run automatically on startup.

## License

[MIT License](LICENSE) - Copyright (c) 2025 Joseph Sacchini

---

*Made with love for Siqi, and with a lot of help from Claude.*
