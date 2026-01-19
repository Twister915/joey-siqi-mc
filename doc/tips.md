# Tips System

Periodic gameplay tips to help players discover server features.

## How It Works

Tips are sent automatically:
- First tip: 5 seconds after joining
- Subsequent tips: Every 5 minutes

Tips cover server commands and features:
- World navigation (`/survival`, `/creative`, `/world`)
- Home system (`/home set`, `/home share`)
- Teleportation (`/tp`, `/back`, `/rtp`)
- Warps and spawn
- Utilities (`/list`, `/ontime`)
- And many more

### First-Time Players

New players receive special tips:
- RTP tip (5 seconds after join)
- Merit system tip (15 seconds after join)

## Example Tips

```
[Tip] Use /home set to save your current location!
[Tip] Try /rtp to find a new unexplored area.
[Tip] Ask @Steve any Minecraft question in chat!
```

## Configuration

```yaml
tips:
  enabled: true
```

Set `enabled: false` to disable all tips.

## Permissions

Tips are automatically filtered by permission. Players only see tips for features they can access.
