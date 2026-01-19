# Commands Reference

Complete list of all commands organized by system.

## Teleportation

| Command | Permission | Description |
|---------|------------|-------------|
| `/tp <player>` | `smp.tp` | Request teleport to another player |
| `/tphere <player>` | `smp.tphere` | Request to teleport a player to you |
| `/back` | `smp.back` | Return to death location or previous teleport |
| `/accept` | - | Accept a pending confirmation request |
| `/decline` | - | Decline a pending confirmation request |

**Special Permissions:**
- `smp.tp.instant` - Skip warmup countdown

## Homes

| Command | Permission | Description |
|---------|------------|-------------|
| `/home [name]` | `smp.home` | Teleport to a home (default: "home") |
| `/home set [name]` | `smp.home` | Set a home location |
| `/home delete <name>` | `smp.home` | Delete a home |
| `/home list` | `smp.home` | List all homes with distances |
| `/home share <name> <player>` | `smp.home` | Share a home with another player |
| `/home unshare <name> <player>` | `smp.home` | Revoke shared access |
| `/home help` | `smp.home` | Show home command help |

**Special Permissions:**
- `smp.home.<limit-name>` - Home limit based on config (e.g., `smp.home.donor`)

## Warps & Spawn

| Command | Permission | Description |
|---------|------------|-------------|
| `/warp` | `smp.warp` | List all warps |
| `/warp <name>` | `smp.warp` | Teleport to a warp |
| `/warp set <name>` | `smp.warp.set` | Create/update a warp |
| `/warp delete <name>` | `smp.warp.set` | Delete a warp |
| `/spawn` | `smp.spawn` | Teleport to world spawn |
| `/setspawn` | `smp.setspawn` | Set world spawn point |

## Random Teleport

| Command | Permission | Description |
|---------|------------|-------------|
| `/rtp` | `smp.rtp` | Generate 5 random location options |
| `/rtp select <1-5>` | `smp.rtp` | Teleport to a chosen location |

**Special Permissions:**
- `smp.rtp.bypass` - Bypass cooldown

## Multi-World

| Command | Permission | Description |
|---------|------------|-------------|
| `/world` | `smp.world` | List all worlds |
| `/world <name>` | `smp.world` | Teleport to a world |
| `/survival` | `smp.world` | Shortcut to survival world |
| `/creative` | `smp.world` | Shortcut to creative world |
| `/superflat` | `smp.world` | Shortcut to superflat world |

**Special Permissions:**
- `smp.world.<worldname>` - Access worlds with `access: permission`

## Private Messages

| Command | Permission | Description |
|---------|------------|-------------|
| `/msg <player> <message>` | `smp.msg` | Send a private message |
| `/reply <message>` | `smp.msg` | Reply to last message |

**Aliases:** `/m`, `/t`, `/tell`, `/whisper`, `/pm`, `/w`, `/r`

## Nicknames

| Command | Permission | Description |
|---------|------------|-------------|
| `/nick <name>` | `smp.nick` | Set your display name |
| `/nick clear` | `smp.nick` | Remove your nickname |
| `/nick <player> <name>` | `smp.nick.others` | Set another player's nickname |

## Player Settings

| Command | Permission | Description |
|---------|------------|-------------|
| `/settings` | - | Open settings menu |

**Setting Permissions:**
- `smp.settings.keepinventory` - Keep inventory on death
- `smp.settings.displaytime` - Configure boss bar time display
- `smp.settings.easymode` - Reduced mob damage + insta-kill

## Cosmetics

| Command | Permission | Description |
|---------|------------|-------------|
| `/trails` | - | Open trails menu |
| `/trails elytra` | `smp.trails.elytra` | Show elytra trail options |
| `/trails elytra <effect>` | `smp.trails.elytra` | Select a trail effect |
| `/trails elytra <effect> <intensity>` | `smp.trails.elytra` | Select with intensity |
| `/trails elytra off` | `smp.trails.elytra` | Disable elytra trails |
| `/resourcepack` | `smp.resourcepack` | List available resource packs |
| `/resourcepack select <pack>` | `smp.resourcepack` | Apply a resource pack |
| `/resourcepack clear` | `smp.resourcepack` | Remove resource pack |
| `/pet` | - | Manage companion pet |

**Aliases:** `/rp` for resourcepack

## Merit & Challenges

| Command | Permission | Description |
|---------|------------|-------------|
| `/challenges` | - | View weekly challenges and progress |
| `/meritadmin` | `smp.merit.admin` | Admin merit management |

**Aliases:** `/c`, `/level`, `/merit`

## Steve AI

| Command | Permission | Description |
|---------|------------|-------------|
| `/steve` | `smp.steve.admin` | View Steve status |
| `/steve model` | `smp.steve.admin` | List available models |
| `/steve model <provider>` | `smp.steve.admin` | Switch model provider |

**Chat Usage:** Mention `@Steve` in chat to ask questions (requires `smp.steve`)

## Permissions

| Command | Permission | Description |
|---------|------------|-------------|
| `/perm group list` | `smp.perm.admin` | List all groups |
| `/perm group <name> create [priority]` | `smp.perm.admin` | Create a group |
| `/perm group <name> delete` | `smp.perm.admin` | Delete a group |
| `/perm group <name> default true/false` | `smp.perm.admin` | Set default status |
| `/perm group <name> priority <int>` | `smp.perm.admin` | Set group priority |
| `/perm group <name> set <perm> [world] true/false` | `smp.perm.admin` | Grant/deny permission |
| `/perm group <name> unset <perm>` | `smp.perm.admin` | Remove permission |
| `/perm group <name> grants` | `smp.perm.admin` | List all grants |
| `/perm group <name> add <player>` | `smp.perm.admin` | Add player to group |
| `/perm group <name> remove <player>` | `smp.perm.admin` | Remove player from group |
| `/perm group <name> members` | `smp.perm.admin` | List group members |
| `/perm group <name> chat prefix/suffix <value>` | `smp.perm.admin` | Set chat prefix/suffix |
| `/perm group <name> nameplate prefix/suffix <value>` | `smp.perm.admin` | Set nameplate prefix/suffix |
| `/perm group <name> color <color\|clear>` | `smp.perm.admin` | Set name color |
| `/perm group <name> inspect` | `smp.perm.admin` | View group details |
| `/perm player <name> set <perm> [world] true/false` | `smp.perm.admin` | Set player permission |
| `/perm player <name> unset <perm>` | `smp.perm.admin` | Remove player permission |
| `/perm player <name> chat/nameplate prefix/suffix <value>` | `smp.perm.admin` | Set player display |
| `/perm player <name> color <color\|clear>` | `smp.perm.admin` | Set player color |
| `/perm player <name> inspect` | `smp.perm.admin` | View player permissions |
| `/perm reload` | `smp.perm.admin` | Refresh all caches |

## Punishments

| Command | Permission | Description |
|---------|------------|-------------|
| `/ban <player> [reason]` | `smp.punish.ban` | Permanently ban a player |
| `/tempban <player> <duration> [reason]` | `smp.punish.tempban` | Temporarily ban a player |
| `/unban <player>` | `smp.punish.unban` | Remove a ban |
| `/ipban <player> [reason]` | `smp.punish.ipban` | Ban by IP address |
| `/unipban <player>` | `smp.punish.unipban` | Remove an IP ban |
| `/kick <player> [reason]` | `smp.punish.kick` | Kick a player |
| `/mute <player> [reason]` | `smp.punish.mute` | Permanently mute a player |
| `/tempmute <player> <duration> [reason]` | `smp.punish.tempmute` | Temporarily mute a player |
| `/unmute <player>` | `smp.punish.unmute` | Remove a mute |
| `/warn <player> [reason]` | `smp.punish.warn` | Issue a warning |
| `/punishments [player]` | `smp.punish.view` | View punishment history |

**Duration format:** `1d` (days), `2w` (weeks), `3h` (hours), `30m` (minutes), `1mo` (months), `1y` (years)

## Whitelist

| Command | Permission | Description |
|---------|------------|-------------|
| `/invite <player>` | `smp.invite` | Invite a player to whitelist |
| `/whitelist add <player>` | `smp.whitelist` | Add a player to whitelist |
| `/whitelist remove <player>` | `smp.whitelist` | Remove from whitelist |
| `/whitelist list [page]` | `smp.whitelist` | List whitelisted players |
| `/whitelist audit [player] [page]` | `smp.whitelist` | View invite history |

## Protection

| Command | Permission | Description |
|---------|------------|-------------|
| `/protection claim [radius]` | `smp.protection` | Claim a region |
| `/protection unclaim` | `smp.protection` | Remove a region |
| `/protection info` | `smp.protection` | View region info |
| `/protection list` | `smp.protection` | List your regions |
| `/protection trust <player>` | `smp.protection` | Add trusted player |
| `/protection untrust <player>` | `smp.protection` | Remove trusted player |
| `/protection settings` | `smp.protection` | View region settings |
| `/protection access` | `smp.protection` | View access list |
| `/protection radius <blocks>` | `smp.protection` | Resize region |
| `/protection repair` | `smp.protection` | Repair damaged blocks |
| `/protection visualize` | `smp.protection` | Show region boundary |

**Aliases:** `/prot`, `/pr`

**Special Permissions:**
- `smp.protection.limit.<name>` - Max regions based on config
- `smp.protection.radius.<name>` - Max radius based on config

## Anti-Cheat

| Command | Permission | Description |
|---------|------------|-------------|
| `/violations [player]` | `smp.anticheat.violations` | View violation history |
| `/alerts` | `smp.anticheat.alerts` | Toggle real-time alerts |

## Admin Mode

| Command | Permission | Description |
|---------|------------|-------------|
| `/adminmode` | `smp.adminmode` | Toggle admin creative mode |

## Chunk Pre-Generation

| Command | Permission | Description |
|---------|------------|-------------|
| `/pregen` | `smp.pregen` | Show pre-generation status |
| `/pregen start` | `smp.pregen` | Start pre-generation |
| `/pregen stop` | `smp.pregen` | Stop and reset |
| `/pregen pause` | `smp.pregen` | Pause pre-generation |
| `/pregen force` | `smp.pregen` | Toggle forced mode |
| `/pregen monitor` | `smp.pregen` | Toggle boss bar display |

## Utility Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/clear [player]` | `smp.clear` | Clear inventory |
| `/item <material> [amount]` | `smp.item` | Give yourself an item |
| `/give <player> <material> [amount]` | `smp.give` | Give a player an item |
| `/time <value>` | `smp.time` | Set world time |
| `/weather <type>` | `smp.weather` | Set weather |
| `/list` | `smp.list` | List online players |
| `/suicide` | `smp.suicide` | Kill yourself to respawn |
| `/remove <type\|all> [radius]` | `smp.remove` | Remove entities |
| `/seed` | `smp.seed` | Show world seed |
| `/map` | `smp.map` | Get web map link |
| `/genstatue <player>` | `smp.statue` | Generate wool statue |
| `/uptime` | `smp.uptime` | Show server uptime |
| `/ping [player]` | `smp.ping` | Check connection latency |
| `/last` | `smp.last` | List recently joined players |
| `/seen <player>` | `smp.seen` | Check when player was last online |
| `/ontime [player]` | `smp.ontime` | View online time stats |
| `/opme` | `smp.opme` | Temporarily grant operator status |

**Aliases:** `/ci` (clear), `/i` (item)

**Special Permissions:**
- `smp.clear.others` - Clear other players' inventories

## Debug Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/daymsgdebug` | `smp.debug` | Debug day message system |
