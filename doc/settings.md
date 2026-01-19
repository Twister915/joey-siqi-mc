# Player Settings

Per-player gameplay customization with permission-gated features.

## Command

| Command | Permission | Description |
|---------|------------|-------------|
| `/settings` | - | Open settings menu |

## Available Settings

### Keep Inventory

**Permission:** `smp.settings.keepinventory`

When enabled:
- Items stay in inventory on death
- Experience levels preserved
- No item drops

Toggle in the menu or use `/settings keepinventory on|off`.

### Display Time

**Permission:** `smp.settings.displaytime`

Controls when the time appears in the boss bar:

| Value | Behavior |
|-------|----------|
| **Always** | Time always shown in overworld (default) |
| **Clock** | Time only shown when holding a clock |
| **Never** | Time never shown |

### Easy Mode

**Permission:** `smp.settings.easymode`

Makes survival easier:

**Damage Reduction:**
- Mobs deal only 25% of normal damage to you
- PvP damage is unaffected

**Insta-Kill Chance:**
- 5% chance to instantly kill hostile mobs when you attack
- Triggers heart particles and level-up sound
- Shows action bar messages like "Critical hit!" and "One-shot!"

### Passive Mode

**Permission:** `smp.settings.passivemode`

Disables PvP entirely:
- You cannot damage other players
- Other players cannot damage you
- Works for both melee and projectile attacks

## Settings Menu

Run `/settings` to see a clickable interface:

```
[Settings] Your Settings:

  Keep Inventory: [On] [Off]
    > Preserve items and XP when you die

  Display Time: [Always] [Clock] [Never]
    > When to show the time in the boss bar

  Easy Mode: [On] [Off]
    > Mobs deal 25% damage + 5% insta-kill chance

  Passive Mode: [On] [Off]
    > Disable all PvP combat
```

- Current value highlighted in green
- Other options in yellow (clickable)
- Settings without permission are hidden

## Permissions

| Permission | Description |
|------------|-------------|
| `smp.settings.keepinventory` | Access keep inventory setting |
| `smp.settings.displaytime` | Access display time setting |
| `smp.settings.easymode` | Access easy mode setting |
| `smp.settings.passivemode` | Access passive mode setting |
