# Punishments System

Full punishment management with duration support and history tracking.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/ban <player> [reason]` | `smp.punish.ban` | Permanently ban a player |
| `/tempban <player> <duration> [reason]` | `smp.punish.tempban` | Temporarily ban a player |
| `/unban <player>` | `smp.punish.unban` | Remove a ban |
| `/ipban <player\|ip> [reason]` | `smp.punish.ipban` | Ban an IP address |
| `/unipban <player\|ip>` | `smp.punish.unipban` | Remove an IP ban |
| `/kick <player> [reason]` | `smp.punish.kick` | Kick a player |
| `/mute <player> [reason]` | `smp.punish.mute` | Permanently mute a player |
| `/tempmute <player> <duration> [reason]` | `smp.punish.tempmute` | Temporarily mute a player |
| `/unmute <player>` | `smp.punish.unmute` | Remove a mute |
| `/warn <player> <reason>` | `smp.punish.warn` | Issue a warning (reason required) |
| `/punishments <player> [page]` | `smp.punish.view` | View punishment history |

## Duration Format

Durations use a compact format combining multiple units:

| Unit | Meaning |
|------|---------|
| `d` | Days |
| `h` | Hours |
| `m` | Minutes |
| `s` | Seconds |

**Examples:**
- `1d` - 1 day
- `2h30m` - 2 hours 30 minutes
- `1d12h` - 1 day 12 hours
- `7d` - 1 week

## How It Works

### Bans

Bans prevent players from joining the server.

**Permanent ban:**
```
/ban PlayerName Griefing
```

**Temporary ban:**
```
/tempban PlayerName 7d Griefing
```

Players see a detailed kick message showing:
- Reason (if provided)
- Duration or "Permanent"
- Who banned them

**Unban:**
```
/unban PlayerName
```

### IP Bans

Ban by IP address to block multiple accounts:

**By player (looks up their last IP):**
```
/ipban PlayerName VPN abuse
```

**By IP directly:**
```
/ipban 192.168.1.100 Bot network
```

**Unban IP:**
```
/unipban PlayerName
/unipban 192.168.1.100
```

### Mutes

Mutes prevent players from chatting.

**Permanent mute:**
```
/mute PlayerName Spam
```

**Temporary mute:**
```
/tempmute PlayerName 1h Excessive caps
```

Muted players see remaining time when they try to chat.

### Warnings

Warnings are recorded in history but don't prevent any actions:

```
/warn PlayerName Please read the rules
```

The reason is required for warnings.

### Kicks

Kicks immediately remove players from the server:

```
/kick PlayerName AFK too long
```

Kicks are recorded in history for audit purposes.

## Viewing History

View a player's punishment history with `/punishments <player>`:

```
[Punish] Punishment History for PlayerName:

  BAN - Griefing - Jan 15, 2024 [Active]
  WARN - Minor rule violation - Jan 10, 2024
  KICK - AFK - Jan 5, 2024
```

**Status indicators:**
- `[Active]` (red) - Active permanent punishment
- `[Active]` (yellow) - Active temporary punishment
- `[Expired]` - Punishment has expired
- `[Revoked]` - Punishment was manually removed

Hover over entries for detailed information including who issued/revoked.

## Permissions

| Permission | Description |
|------------|-------------|
| `smp.punish.ban` | Ban players |
| `smp.punish.tempban` | Temporarily ban |
| `smp.punish.unban` | Remove bans |
| `smp.punish.ipban` | IP ban |
| `smp.punish.unipban` | Remove IP bans |
| `smp.punish.kick` | Kick players |
| `smp.punish.mute` | Mute players |
| `smp.punish.tempmute` | Temporarily mute |
| `smp.punish.unmute` | Remove mutes |
| `smp.punish.warn` | Issue warnings |
| `smp.punish.view` | View history |
