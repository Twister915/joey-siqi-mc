# Merit System

Levels and progression through weekly challenges and activities.

## Command

| Command | Permission | Description |
|---------|------------|-------------|
| `/challenges` | - | View weekly challenges and progress |
| `/meritadmin` | `smp.merit.admin` | Admin merit management |

**Aliases:** `/c`, `/level`, `/merit`

## How It Works

### Weekly Challenges

Each week, you receive 8 random challenges from different categories:
- Mining
- Farming
- Building
- PvP Combat
- PvE Combat
- Progression
- Crafting
- Smelting
- Exploration
- Time

Complete challenges to earn merit. Challenges reset at the start of each week.

### Merit & Levels

Merit is your cumulative progression currency:
- Earned by completing challenges
- Earned passively for online time
- Never resets (permanent progression)

Levels are calculated from total merit using a power-law curve. Higher levels require exponentially more merit.

### Online Time Rewards

You earn merit just for being online:
- Default: 10 merit every 30 minutes
- Weekly cap: 500 merit from online time

### Challenge Progress

View your challenges with `/challenges`:

```
[Merit] Weekly Challenges (5/8 complete)

  Mining Stone (250/500) - 50%
    Mine stone, cobblestone, or deepslate

  Harvest Wheat (Complete!) - 80 merit

  Kill Zombies (45/100) - 45%
    Slay the undead
```

Progress milestones (10%, 25%, 50%, 100%) show boss bar notifications.

### Level Display

Your level appears:
- In chat as a colored prefix
- Above your nameplate
- In the tab list

Level colors:
- Gray: 1-9
- White: 10-24
- Yellow: 25-49
- Gold: 50-74
- Aqua: 75-99
- Light Purple: 100+

## Challenge Categories

### Mining
Mine blocks: stone, ores, deepslate variants

### Farming
Harvest crops: wheat, carrots, potatoes, beetroot, sugar cane, nether wart

### Building
Place blocks: wood, stone, glass, redstone components

### PvP Combat
Player kills, damage dealt, weapon-specific kills

### PvE Combat
Mob kills by type, boss kills (Dragon, Wither)

### Progression
Enchanting, brewing, trading with villagers, gaining XP

### Crafting
Craft items: tools, armor, food, potions

### Smelting
Smelt in furnaces: metals, glass, food

### Exploration
Travel distance: walking, sprinting, swimming, elytra, boat

### Time
Day cycles survived, sunrises witnessed

## Configuration

```yaml
merit:
  enabled: true
  level-base-xp: 100
  level-exponent: 1.8
  level-soft-cap: 20
  level-early-discount: 0.7
  online-time-reward: 10
  online-time-interval-minutes: 30
  online-time-weekly-cap: 500
  flush-interval-seconds: 30
  weekly-challenge-count: 8
```

| Option | Description |
|--------|-------------|
| `level-base-xp` | Base XP for level calculation |
| `level-exponent` | Curve steepness (higher = harder) |
| `level-soft-cap` | Level where early discount ends |
| `level-early-discount` | Discount for early levels (0.7 = 70% easier) |
| `online-time-reward` | Merit per online interval |
| `online-time-interval-minutes` | Minutes between rewards |
| `online-time-weekly-cap` | Max merit from online time per week |
| `weekly-challenge-count` | Challenges per player per week |

## Permissions

| Permission | Description |
|------------|-------------|
| `smp.merit.admin` | Admin merit commands |
