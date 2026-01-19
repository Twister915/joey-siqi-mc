# Home System

Save personal locations and share them with friends.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/home [name]` | `smp.home` | Teleport to a home (default: "home") |
| `/home set [name]` | `smp.home` | Set a home at your location |
| `/home delete <name>` | `smp.home` | Delete a home (requires confirmation) |
| `/home list` | `smp.home` | List all your homes with distances |
| `/home share <name> <player>` | `smp.home` | Share a home with another player |
| `/home unshare <name> <player>` | `smp.home` | Revoke shared access |
| `/home help` | `smp.home` | Show help |

## How It Works

### Setting Homes

Use `/home set [name]` to save your current location. If no name is given, it defaults to "home".

Home names are normalized to lowercase. Setting a home with an existing name replaces the old location.

### Teleporting

- `/home` - Teleport to your default "home"
- `/home <name>` - Teleport to a specific home

Uses the warmup system (see [Teleportation](teleportation.md)).

### Auto-Home

When you right-click a bed for the **first time ever**, your location is automatically saved as "home". This only happens once - subsequent bed interactions don't change your home.

### Sharing Homes

Share your homes with other players:

```
/home share fishing_spot Joey
```

Joey can now teleport to your home using:

```
/home YourName:fishing_spot
```

Revoke access with `/home unshare <name> <player>`.

### Home List

`/home list` shows all homes you own and homes shared with you:

- **Owned homes**: Shows name and distance (if in same world)
- **Shared homes**: Shows `owner:name` format with owner name

## Home Limits

Admins can configure home limits per permission class in `config.yml`:

```yaml
homes:
  limits:
    player: 5          # smp.home.player = 5 homes
    donor: 12          # smp.home.donor = 12 homes
    vip: unlimited     # smp.home.vip = unlimited
```

Players without any limit permission have unlimited homes.

The highest matching limit applies. `/home set` shows your current count and limit.

## Configuration

```yaml
homes:
  limits:
    player: 5
    donor: 12
    vip: unlimited
```

Each entry creates a permission `smp.home.<name>` granting that many homes.

## Permissions

| Permission | Description |
|------------|-------------|
| `smp.home` | Use home commands |
| `smp.home.<limit>` | Home limit from config |
