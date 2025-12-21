# Custom Message System

The plugin customizes Minecraft's default messages to match a consistent visual style.

## Connection Messages

Join and leave broadcasts use a minimal, clean format.

| Event | Format | Colors |
|-------|--------|--------|
| Join | `[+] PlayerName` | `+` green, brackets/name gray |
| Leave | `[-] PlayerName` | `-` red, brackets/name gray |

**Implementation:** `welcome/ConnectionMessageProvider.java`

## Death Messages

All vanilla death messages are replaced with custom variants (150+ total). Messages are randomly selected from a pool for each death type.

### Color Scheme

| Element | Color |
|---------|-------|
| Player name (victim) | `GRAY` |
| Killer/entity name | `RED` |
| Message text | `DARK_GRAY` |

### Categories

**Environmental Deaths** (no killer):
- Fall, lava, drowning, fire, suffocation, void
- Starvation, freeze, lightning, cactus, magma
- Cramming, elytra crash, world border
- Campfire, berry bush, falling block

**Entity Deaths** (with killer name):
- Generic mob attacks
- Creeper explosions (special messages)
- Projectile kills (arrows, tridents, etc.)
- Thorns damage
- Warden sonic boom

**PvP Deaths** (player killer):
- Player vs player kills with competitive flavor

**Misc Deaths**:
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

### Placeholders

- `{player}` - Victim's name
- `{killer}` - Attacker's name (mob, player, or entity)

**Implementation:**
- `death/DeathMessageProvider.java` - Event handler, death cause detection
- `death/DeathMessages.java` - All message variants organized by `DamageCause`

## Chat Messages

Chat uses a clean format without the default `<Player>` angle brackets.

| Element | Color |
|---------|-------|
| Player name | `GRAY` |
| Colon separator | `DARK_GRAY` |
| Message text | `WHITE` |

**Format:** `PlayerName: message`

**Implementation:** `welcome/ChatMessageProvider.java`

Uses Paper's `AsyncChatEvent` with a custom renderer to format messages.
