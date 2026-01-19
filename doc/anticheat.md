# Anti-Cheat System

Cheat detection with violation tracking and moderator alerts.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/violations [player]` | `smp.anticheat.violations` | View violation history |
| `/alerts` | `smp.anticheat.alerts` | Toggle real-time alerts |

## How It Works

### Detection Checks

The system runs multiple cheat detection checks:

| Check | Detects |
|-------|---------|
| **Fly** | Flying without creative, elytra, or effects |
| **Reach** | Hitting beyond normal attack range (3.5 blocks) |
| **NoFall** | Avoiding fall damage |
| **Timer** | Tick rate manipulation (speedhack) |
| **Scaffold** | Impossible block placement patterns |

### Violation Levels

Each detection adds to the player's violation level (VL):
- VL accumulates from repeated detections
- VL decays 10% per second
- Alert threshold: 10.0 VL

### Moderator Alerts

When a player exceeds the alert threshold:
- Moderators with alerts enabled receive notifications
- Shows player name, check type, and VL

Toggle alerts with `/alerts`.

### GrimAC Integration

If [GrimAC](https://github.com/GrimAnticheat/Grim) is installed:
- GrimAC detections are forwarded to the violation tracker
- Shows `[GrimAC]` tag in violation history
- Combines with built-in checks for comprehensive coverage

## Viewing Violations

Check recent violations with `/violations`:

```
[AC] Recent Violations:

  [1] PlayerName - Fly (VL: 12.5) - 3m ago [GrimAC]
  [2] OtherPlayer - Reach (VL: 8.3) - 1h ago
```

Filter by player: `/violations PlayerName`

## Data Retention

Violation data is retained for 30 days, then automatically cleaned up.

## Permissions

| Permission | Description |
|------------|-------------|
| `smp.anticheat.violations` | View violation history |
| `smp.anticheat.alerts` | Toggle real-time alerts |
