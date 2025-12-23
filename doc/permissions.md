# Permissions System

A group-based permissions system with player overrides, world-specific grants, and display attributes (prefix/suffix).

## Overview

Permissions are resolved using a layered approach:
1. **Player permissions** - Direct grants to individual players (highest priority)
2. **Group permissions** - Inherited from groups the player belongs to
3. **Default groups** - Groups automatically applied to all players

Higher priority values win when there are conflicts. More specific permission nodes override wildcards.

## Commands

All commands can be run from console or by players.

### Group Commands

| Command | Description |
|---------|-------------|
| `/perm group list` | List all groups (paginated) |
| `/perm group <name> create [priority]` | Create a new group |
| `/perm group <name> delete` | Delete a group |
| `/perm group <name> default true/false` | Set whether group applies to all players |
| `/perm group <name> priority <int>` | Set group priority (higher = more important) |
| `/perm group <name> set <perm> true/false` | Grant/deny a permission globally |
| `/perm group <name> set <perm> <world> true/false` | Grant/deny a permission in a specific world |
| `/perm group <name> unset <perm>` | Remove a permission grant |
| `/perm group <name> grants` | List all permission grants (paginated) |
| `/perm group <name> add <player>` | Add a player to the group |
| `/perm group <name> remove <player>` | Remove a player from the group |
| `/perm group <name> chat prefix <value>` | Set chat prefix |
| `/perm group <name> chat suffix <value>` | Set chat suffix |
| `/perm group <name> nameplate prefix <value>` | Set nameplate/tablist prefix |
| `/perm group <name> nameplate suffix <value>` | Set nameplate/tablist suffix |
| `/perm group <name> inspect` | View group details (paginated) |

### Player Commands

| Command | Description |
|---------|-------------|
| `/perm player <name> set <perm> true/false` | Grant/deny a permission globally |
| `/perm player <name> set <perm> <world> true/false` | Grant/deny a permission in a specific world |
| `/perm player <name> unset <perm>` | Remove a permission grant |
| `/perm player <name> chat prefix <value>` | Set chat prefix override |
| `/perm player <name> chat suffix <value>` | Set chat suffix override |
| `/perm player <name> nameplate prefix <value>` | Set nameplate/tablist prefix override |
| `/perm player <name> nameplate suffix <value>` | Set nameplate/tablist suffix override |
| `/perm player <name> inspect` | View player's permissions and groups (paginated) |

### Admin Commands

| Command | Description |
|---------|-------------|
| `/perm reload` | Refresh permissions for all online players |
| `/perm help` | Show command help |

## Permission Syntax

Permissions use a dot-separated hierarchy with optional wildcards.

### Valid Formats

| Pattern | Description |
|---------|-------------|
| `minecraft.command.tp` | Specific permission |
| `minecraft.command.*` | Wildcard (matches all under `minecraft.command`) |
| `*` | Global wildcard (matches everything) |

### Rules

- Nodes are alphanumeric with underscores and hyphens
- No leading/trailing dots
- No double dots (`..`)
- Wildcards (`*`) only allowed at the end after a dot

### Specificity

When multiple grants match a permission check, the most specific one wins:
1. Exact match (`minecraft.command.tp`) beats wildcard (`minecraft.command.*`)
2. Longer wildcards (`minecraft.command.*`) beat shorter (`minecraft.*`)
3. Same specificity: higher priority source wins

## Resolution Algorithm

When checking if a player has a permission:

1. **Collect all grants** in priority order:
   - Player's direct grants (priority = MAX_INT)
   - Each group's grants, sorted by group priority (descending)
   - Default groups included automatically

2. **Filter by world**:
   - Global grants (no world) always apply
   - World-specific grants only apply in that world

3. **Resolve conflicts**:
   - More specific permission wins
   - Same specificity: higher priority wins
   - First match in priority order wins

### Example

```
Player has:
  - Direct grant: siqi.home.* = true (priority MAX)

Member of "admin" group (priority 100):
  - minecraft.command.* = true
  - siqi.* = true

Member of "default" group (priority 0, is_default=true):
  - minecraft.command.help = true
  - siqi.home.set = false

Resolution for "siqi.home.set":
  1. Player grant: siqi.home.* = true (matches, specificity 2)
  2. Admin grant: siqi.* = true (matches, specificity 1)
  3. Default grant: siqi.home.set = false (matches, specificity 3)

  Winner: siqi.home.set = false (highest specificity)

  But wait - player's siqi.home.* has same specificity level (2) as default's
  exact match (3)? No - exact match (3) beats wildcard (2).

  Actually: specificity = number of non-wildcard segments
  - siqi.home.* has 2 segments before wildcard
  - siqi.home.set has 3 exact segments

  Result: siqi.home.set = false wins (most specific)
```

## Display Attributes

### Prefix/Suffix Types

| Type | Where Shown |
|------|-------------|
| `chat prefix/suffix` | Before/after name in chat messages |
| `nameplate prefix/suffix` | Above player's head and in tab list |

### Resolution

Player overrides take precedence. If a player has no override, the highest-priority group with a value is used.

```
Player has: chat_prefix = null
Admin group (priority 100): chat_prefix = "[Admin] "
VIP group (priority 50): chat_prefix = "[VIP] "

Result: "[Admin] " (highest priority group with a value)
```

### Color Codes

Both legacy and MiniMessage formats are supported:

| Format | Example |
|--------|---------|
| Legacy | `&c[Admin] &r` (red "[Admin] " then reset) |
| MiniMessage | `<red>[Admin]</red> ` |

Auto-detection: if the string contains `<`, MiniMessage is used; otherwise legacy.

## World-Specific Permissions

Permissions can be scoped to specific worlds:

```
/perm group builder set worldedit.* creative true
/perm group builder set worldedit.* false
```

This grants WorldEdit in the `creative` world only. The global `false` ensures it's denied elsewhere.

### Resolution with Worlds

When checking permissions in a world:
1. World-specific grants for that world are included
2. Global grants (no world) are always included
3. Normal priority/specificity rules apply

## Bootstrapping

Since permissions control who can run `/perm`, you need a way to set up initial groups. Options:

1. **Console** - All `/perm` commands work from console
2. **Op players** - Ops bypass permission checks by default

### Example Bootstrap

```
# From console:
perm group admin create 100
perm group admin set * true
perm group admin add YourName
perm group admin nameplate prefix &c[Admin] &r

perm group default create 0
perm group default default true
perm group default set minecraft.command.help true
```

## Caching

Permissions are cached per-player per-world for performance:

- **Pre-populated** on player login (before they spawn)
- **Invalidated** on world change
- **Cleared** on player quit
- **Refreshed** via `/perm reload`

Attribute cache is kept separately for synchronous access (e.g., chat formatting).

## Database Schema

Permissions are stored in PostgreSQL:

### Tables

| Table | Purpose |
|-------|---------|
| `perm_groups` | Group definitions (name, priority, default flag, display attributes) |
| `group_permissions` | Permission grants for groups |
| `player_permissions` | Permission grants for individual players |
| `perm_players` | Player display attribute overrides |
| `player_groups` | Player-to-group membership |

### Key Columns

**perm_groups:**
- `canonical_name` - Lowercase normalized name (primary key)
- `display_name` - Original casing for display
- `priority` - Higher = more important
- `is_default` - If true, all players inherit this group

**group_permissions / player_permissions:**
- `permission` - The permission string
- `world_id` - NULL for global, UUID for world-specific
- `state` - true = allow, false = deny

## Implementation

| File | Purpose |
|------|---------|
| `permissions/ParsedPermission.java` | Permission parsing and matching |
| `permissions/PermissionGrant.java` | Single permission grant record |
| `permissions/PermissibleAttributes.java` | Prefix/suffix attributes |
| `permissions/Group.java` | Group record with grants and attributes |
| `permissions/PermissionStorage.java` | Database operations |
| `permissions/PermissionResolver.java` | Resolution algorithm |
| `permissions/PermissionCache.java` | Per-player per-world caching |
| `permissions/PermissionAttacher.java` | Bukkit PermissionAttachment management |
| `permissions/DisplayManager.java` | Nameplate display via Scoreboard Teams |
| `permissions/cmd/PermCommand.java` | Command router |
| `permissions/cmd/group/GroupSubcommand.java` | Group subcommand handlers |
| `permissions/cmd/player/PlayerSubcommand.java` | Player subcommand handlers |
