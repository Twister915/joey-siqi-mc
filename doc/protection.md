# Protection System

Claim and protect regions using lodestone anchors.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/protection claim [name]` | `smp.protection` | Claim a lodestone |
| `/protection unclaim [name]` | `smp.protection` | Delete a region |
| `/protection info` | `smp.protection` | View region info where you're standing |
| `/protection list` | `smp.protection` | List your regions |
| `/protection trust <player>` | `smp.protection` | Add a trusted member |
| `/protection untrust <player>` | `smp.protection` | Remove a trusted member |
| `/protection settings` | `smp.protection` | View access settings UI |
| `/protection access <setting> <level>` | `smp.protection` | Change access level |
| `/protection radius [name] <blocks>` | `smp.protection` | Adjust region radius |
| `/protection rename [name] <new>` | `smp.protection` | Rename a region |
| `/protection visualize` | `smp.protection` | Show region boundary particles |
| `/protection repair [name]` | `smp.protection` | Repair orphaned region |

**Aliases:** `/prot`, `/pr`

## How It Works

### Creating Regions

1. Craft and place a **lodestone**
2. The system detects your placement and offers to claim
3. Confirm with `/protection claim [name]` or click the prompt
4. A circular region is created around the lodestone

### Region Shape

Each region is defined by:
- **Anchors**: Lodestone blocks that mark the center points
- **Radius**: Distance from each anchor (default 16 blocks)
- **Protection**: Extends infinitely vertical (all Y levels)

A location is protected if it falls within **any** anchor's radius circle.

### Expanding Regions

Add multiple lodestones to expand coverage:

1. Place another lodestone near your existing region
2. Choose "Extend" when prompted
3. The new lodestone becomes an additional anchor

This creates connected regions without needing separate claims.

### Access Levels

Four independent settings control different actions:

| Setting | Default | Controls |
|---------|---------|----------|
| **Building** | Members | Block place/break, mining |
| **Containers** | Members | Chests, furnaces, item frames |
| **Doors** | Everybody | Doors, buttons, levers, pressure plates |
| **PvP** | Owner | Player combat |

### Access Level Values

| Level | Who Can Access |
|-------|----------------|
| **Owner** | Only the region owner |
| **Members** | Owner + trusted players |
| **Everybody** | Anyone (no protection) |

### Changing Settings

Use `/protection settings` to see a clickable UI, or:

```
/protection access building owner
/protection access containers members
/protection access doors everybody
/protection access pvp owner
```

### Trusting Players

Add members with `/protection trust <player>` while standing in your region. Members get access based on your configured levels.

### Orphaned Regions

If all lodestone anchors are broken/missing, the region becomes **orphaned**:
- You're notified on login
- Protection still applies
- Repair with `/protection repair` (requires a lodestone in inventory)

### Visualization

Toggle `/protection visualize` to see particle boundaries:
- **Green particles**: You have building access
- **Red particles**: You're blocked

## Configuration

```yaml
protection:
  default-radius: 16
  min-radius: 8
  limits:
    default: 5
    vip: 10
    unlimited: unlimited
  max-radius:
    default: 32
    vip: 64
    unlimited: 128
```

### Limits

Each entry under `limits` creates `smp.protection.limit.<name>` permission.

### Max Radius

Each entry under `max-radius` creates `smp.protection.radius.<name>` permission.

## Admin Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/protection bypass` | `smp.protection.admin` | Toggle bypass mode |
| `/protection cleanup` | `smp.protection.admin` | List orphaned regions |
| `/protection forceunclaim <player> <name>` | `smp.protection.admin` | Delete any region |
| `/protection forcerepair <player> <name>` | `smp.protection.admin` | Repair without lodestone |

## Permissions

| Permission | Description |
|------------|-------------|
| `smp.protection` | Use protection system |
| `smp.protection.limit.<name>` | Max regions from config |
| `smp.protection.radius.<name>` | Max radius from config |
| `smp.protection.admin` | Admin commands and bypass |
