# Admin Mode

Toggle creative mode while preserving your survival inventory. Useful for building or fixing issues without losing your items.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/adminmode` | `smp.adminmode` | Toggle admin mode on/off |

## How It Works

When you enter admin mode:
1. Your survival inventory, XP, health, hunger, and effects are saved to the database
2. Your inventory is cleared
3. You're switched to creative mode
4. Configured permissions are temporarily granted

When you exit admin mode:
1. Temporary permissions are removed
2. Your saved inventory and state are restored
3. You're switched back to survival mode

## Restrictions

While in admin mode:
- **No portals** - Nether/End portal usage is blocked
- **No cross-world teleports** - You must exit admin mode before teleporting to other worlds
- **Survival worlds only** - Can only be activated in worlds with SURVIVAL gamemode

## Configuration

Configure additional permissions granted while in admin mode in `config.yml`:

```yaml
adminmode:
  # Additional permissions granted while in admin mode
  permissions:
    - worldedit.*
    - worldguard.*
```

### Config Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `permissions` | list | `[]` | Permission nodes to grant while in admin mode |

### Example: WorldEdit + WorldGuard

```yaml
adminmode:
  permissions:
    - worldedit.*
    - worldguard.*
    - coreprotect.inspect
    - coreprotect.rollback
```

This grants full WorldEdit, WorldGuard, and CoreProtect access only while in admin mode.

## Server Restart Behavior

If the server restarts while a player is in admin mode:
- The admin mode state is persisted in the database
- On next login, the player is automatically exited from admin mode
- Their saved inventory is restored

This ensures players never lose their survival inventory due to server crashes.

## Database

Admin mode state is stored in the `admin_mode_state` table:
- `player_id` - Player UUID
- `world_id` - World where admin mode was entered
- `snapshot_id` - Reference to saved inventory snapshot
- `entered_at` - Timestamp

Inventory snapshots are stored separately and include full inventory, ender chest, XP, health, hunger, saturation, and potion effects.
