# Session Tracking

Player session and online time tracking.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/ontime` | `smp.ontime` | View your online time |
| `/ontime <player>` | `smp.ontime` | View another player's time |
| `/ontime top [duration]` | `smp.ontime` | Leaderboard (default: 7 days) |
| `/whois <player>` | `smp.whois` | Basic player info |
| `/whois <player>` | `smp.whois.admin` | Extended player info |

## Online Time

View your playtime statistics:

```
/ontime
```

Output:
```
[⏱] Your Online Time
[⏱] This session: 2h 15m 30s
[⏱] Lifetime: 48d 3h 42m
```

### Leaderboard

View top players by online time:

```
/ontime top        # Past 7 days (default)
/ontime top 30d    # Past 30 days
/ontime top 1d     # Past 24 hours
```

Output:
```
[⏱] Top 5 Online Time (past 7d)

[⏱] 1. Player1 - 50h 30m
[⏱] 2. Player2 - 45h 15m
[⏱] 3. Player3 - 42h
```

## Player Info

Look up player information with `/whois`:

### Basic Info (smp.whois)

```
[Whois] ──────────────────
 Username: PlayerName
 Nickname: CustomName
 Status: Online
```

### Extended Info (smp.whois.admin)

```
[Whois] ──────────────────
 Username: PlayerName
 Nickname: CustomName
 Status: Online
 UUID: 550e8400-e29b-41d4-a716-446655440000
 IP: 192.168.1.100
 Location: United States, California
 First joined: Jan 15, 2024
 Last seen: Now (online)
 Total playtime: 48d 3h 42m

 Username History:
  - PlayerName (Jan 15, 2024 - present)
  - OldName (Jan 1, 2024 - Jan 15, 2024)
```

## How It Works

### Session Tracking

The system tracks:
- Join time and disconnect time
- IP address and online mode
- Periodic heartbeat (every 30 seconds)

### Crash Recovery

If the server crashes, sessions are recovered on next startup:
- Orphaned sessions detected by server session ID
- Disconnect time set to last heartbeat

### Data Persistence

All session data persists to PostgreSQL:
- Lifetime online time calculation
- Username history across name changes
- First/last seen timestamps

## Permissions

| Permission | Description |
|------------|-------------|
| `smp.ontime` | View online time stats |
| `smp.whois` | Basic player info |
| `smp.whois.admin` | Extended info (IP, history) |
