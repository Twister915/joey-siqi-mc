# Whitelist System

Custom whitelist with invite tracking and audit capabilities.

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/invite <player>` | `smp.invite` | Invite a player to the whitelist |
| `/whitelist add <player>` | `smp.whitelist` | Admin: add a player |
| `/whitelist remove <player>` | `smp.whitelist` | Admin: remove a player |
| `/whitelist list [page]` | `smp.whitelist` | Admin: list all whitelisted players |
| `/whitelist audit [player] [page]` | `smp.whitelist` | Admin: view invite history |

## How It Works

### Inviting Players

Players with `smp.invite` permission can invite others:

```
/invite FriendName
```

The system:
1. Looks up the player via Mojang API
2. Adds them to the whitelist
3. Records who invited them

### Join Attempt Broadcasts

When a non-whitelisted player tries to join, all players with `smp.invite` see:

```
[Whitelist] PlayerName is trying to join! [Invite] [Ignore]
```

Click `[Invite]` to whitelist them instantly.

Broadcasts are rate-limited to prevent spam (5 per player per 5 minutes).

### Admin Management

**Add directly (no inviter recorded):**
```
/whitelist add PlayerName
```

**Remove from whitelist:**
```
/whitelist remove PlayerName
```

**View all whitelisted players:**
```
/whitelist list
```

Shows each player with who invited them and when.

### Audit Trail

View who invited whom:

```
/whitelist audit           # All invites
/whitelist audit Joey      # Players invited by Joey
```

Useful for tracking invite chains and accountability.

## Non-Whitelisted Kick Message

Players not on the whitelist see:

```
You are not whitelisted on this server.

Ask a member to invite you using /invite YourName
```

## Configuration

```yaml
whitelist:
  enabled: true  # Set to false to disable whitelist
```

When disabled, all players can join regardless of whitelist status.

## Permissions

| Permission | Description |
|------------|-------------|
| `smp.invite` | Invite players to whitelist |
| `smp.whitelist` | Admin whitelist management |
