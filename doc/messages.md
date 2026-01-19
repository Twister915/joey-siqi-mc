# Custom Messages

The plugin customizes Minecraft's default messages with a consistent visual style.

## Connection Messages

Join and leave broadcasts use a minimal, clean format.

| Event | Format | Colors |
|-------|--------|--------|
| Join | `[+] PlayerName` | `+` green, brackets/name gray |
| Leave | `[-] PlayerName` | `-` red, brackets/name gray |

## Death Messages

All vanilla death messages are replaced with custom variants (150+ total). Messages are randomly selected for each death type.

### Color Scheme

| Element | Color |
|---------|-------|
| Player name (victim) | Gray |
| Killer/entity name | Red |
| Message text | Dark gray |

### Categories

**Environmental Deaths:**
- Fall, lava, drowning, fire, suffocation, void
- Starvation, freeze, lightning, cactus, magma
- Cramming, elytra crash, world border
- Campfire, berry bush, falling block

**Entity Deaths:**
- Generic mob attacks
- Creeper explosions (special messages)
- Projectile kills (arrows, tridents, etc.)
- Thorns damage
- Warden sonic boom

**PvP Deaths:**
- Player vs player kills with competitive flavor

**Misc Deaths:**
- Poison, wither effect, magic damage
- `/kill` command

### Example Messages

| Death Type | Example |
|------------|---------|
| Fall | "took the express elevator down" |
| Lava | "became a crispy critter" |
| Creeper | "heard a hissss..." |
| PvP | "was outplayed by {killer}" |
| Drowning | "forgot gills aren't included" |
| Void | "went to the backrooms" |

## Chat Messages

Chat uses a clean format without the default `<Player>` angle brackets.

| Element | Color |
|---------|-------|
| Player name | Gray |
| Colon separator | Dark gray |
| Message text | White |

**Format:** `PlayerName: message`

Permission-based prefixes/suffixes from the [Permissions System](permissions.md) are applied before the name.

## Daily Messages

Themed messages at the start of each Minecraft day.

**Prefix:** `[☀]` (gold)

### Message Types

1. **Static messages** (55+)
   - Greetings, fortunes, wise sayings, humor

2. **Procedural messages** (15 templates)
   - Templates with word banks for variety
   - Example: "The {adjective} {noun} {verb} today."

3. **Context-aware messages**
   - Based on weather, biome, moon phase, dimension
   - Example: "The full moon casts long shadows..."

Messages are randomly selected with weighted distribution.

## Welcome Messages

Personalized messages when players join.

**Prefix:** `[★]` (gold)

### Context Awareness

- **Time of day**: "You join as dusk falls..."
- **Weather**: "The rain greets your return..."
- **First-time players**: Special welcome message
- **Health/hunger**: Warnings if low

## Server MOTD

Dynamic server list message (MOTD) with rotating content.

### Context Triggers

- Night time: Spooky messages
- Storms: Weather-themed
- Empty server: Inviting messages
- Player count: Activity-based

## Private Messages

Direct player-to-player messaging.

### Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/msg <player> <message>` | `smp.msg` | Send private message |
| `/reply <message>` | `smp.msg` | Reply to last message |

**Aliases:** `/m`, `/t`, `/tell`, `/whisper`, `/pm`, `/w`, `/r`

### Features

- Messages persist across sessions (can reply after relog)
- Queued delivery if recipient joins later
- Consistent formatting with sender/receiver indicators
