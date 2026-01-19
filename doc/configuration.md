# Configuration Reference

Full documentation for `plugins/SiqiJoeyPlugin/config.yml`.

## Database

```yaml
database:
  host: localhost
  port: 5432
  database: minecraft
  username: minecraft
  password: secret
  pool-size: 3
  log-queries: false
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `host` | String | localhost | PostgreSQL host |
| `port` | Integer | 5432 | PostgreSQL port |
| `database` | String | minecraft | Database name |
| `username` | String | minecraft | Database username |
| `password` | String | (empty) | Database password |
| `pool-size` | Integer | 3 | Connection pool size |
| `log-queries` | Boolean | false | Log SQL queries (debug) |

## Branding

```yaml
branding:
  server-name: "Siqi & Joey's Server"
  server-ip: "play.example.com"
  tagline: "Welcome!"
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `server-name` | String | Minecraft Server | Server name in MOTD |
| `server-ip` | String | play.example.com | Server IP in tab list |
| `tagline` | String | Welcome to the server! | Tagline in tab list |

## Teleportation

```yaml
teleport:
  warmup-seconds: 3
  movement-tolerance-blocks: 0.5

requests:
  timeout-seconds: 60
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `teleport.warmup-seconds` | Integer | 3 | Warmup countdown duration |
| `teleport.movement-tolerance-blocks` | Double | 0.5 | Movement tolerance before cancel |
| `requests.timeout-seconds` | Integer | 60 | Teleport request expiration |

## Homes

```yaml
homes:
  limits:
    player: 5
    donor: 12
    vip: unlimited
```

Each entry under `limits` creates a permission `smp.home.<name>` with the specified home limit.

| Value | Meaning |
|-------|---------|
| Integer | Maximum number of homes |
| `unlimited` | No home limit |

Players without any limit permission have unlimited homes.

## Random Teleport

```yaml
rtp:
  cooldown-minutes: 5
  search-radius: 25000
  min-distance: 500
  candidate-count: 5
  candidate-timeout-seconds: 120
  chunk-timeout-seconds: 3
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `cooldown-minutes` | Integer | 5 | Cooldown between uses |
| `search-radius` | Integer | 25000 | Max distance from spawn |
| `min-distance` | Integer | 500 | Min distance from spawn |
| `candidate-count` | Integer | 5 | Locations to generate |
| `candidate-timeout-seconds` | Integer | 120 | Candidate validity period |
| `chunk-timeout-seconds` | Integer | 3 | Per-candidate chunk load timeout |

## Protection

```yaml
protection:
  default-radius: 16
  min-radius: 8
  limits:
    player: 3
    donor: 10
    vip: unlimited
  max-radius:
    player: 32
    donor: 64
    vip: 128
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `default-radius` | Integer | 16 | Default claim radius |
| `min-radius` | Integer | 8 | Minimum allowed radius |

**limits**: Creates `smp.protection.limit.<name>` permissions for max region count.

**max-radius**: Creates `smp.protection.radius.<name>` permissions for max radius.

## Merit System

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

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | Boolean | true | Enable merit system |
| `level-base-xp` | Integer | 100 | Base XP for level calculation |
| `level-exponent` | Double | 1.8 | Level curve exponent |
| `level-soft-cap` | Integer | 20 | Early level soft cap |
| `level-early-discount` | Double | 0.7 | Early level discount |
| `online-time-reward` | Integer | 10 | Merit per time interval |
| `online-time-interval-minutes` | Integer | 30 | Minutes between rewards |
| `online-time-weekly-cap` | Integer | 500 | Weekly cap from online time |
| `flush-interval-seconds` | Integer | 30 | Database save interval |
| `weekly-challenge-count` | Integer | 8 | Challenges per player |

## Tips

```yaml
tips:
  enabled: true
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | Boolean | true | Enable periodic gameplay tips |

## Steve AI

```yaml
steve:
  enabled: true
  model: anthropic
  cooldown-seconds: 30

  anthropic:
    api-key: "sk-ant-..."
    max-searches: 3

  lmstudio:
    endpoint: "http://localhost:1234"
    model: "your-model-name"
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | Boolean | true | Enable Steve chatbot |
| `model` | String | anthropic | Provider: anthropic or lmstudio |
| `cooldown-seconds` | Integer | 30 | Per-player cooldown |

**Anthropic:**
| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `api-key` | String | (required) | Anthropic API key |
| `max-searches` | Integer | 3 | Web searches per question |

**LM Studio:**
| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `endpoint` | String | http://localhost:1234 | LM Studio server URL |
| `model` | String | (required) | Model identifier |

## Admin Mode

```yaml
adminmode:
  permissions:
    - worldedit.*
    - worldguard.*
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `permissions` | List | [] | Permissions granted in admin mode |

## Whitelist

```yaml
whitelist:
  enabled: true
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | Boolean | true | Enable custom whitelist |

## Chunk Pre-Generation

```yaml
pregen:
  enabled: false
  rate: FAST
  progress-log-interval-seconds: 60
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | Boolean | false | Enable pre-generation |
| `rate` | Enum | FAST | Speed: SLOW, FAST, FASTEST |
| `progress-log-interval-seconds` | Integer | 60 | Log interval |

Per-world pregen size is set in `worlds.<name>.pregen_size`.

## GeoIP

```yaml
geoip:
  enabled: true
  database-path: "GeoLite2-City.mmdb"
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `enabled` | Boolean | true | Enable GeoIP lookups |
| `database-path` | String | GeoLite2-City.mmdb | Path to database file |

## Resource Packs

```yaml
resource_packs:
  default:
    name: "Server Pack"
    url: "https://example.com/pack.zip"
    hash: "abc123..."
    description: "Our custom textures"
```

Each pack entry:
| Option | Type | Description |
|--------|------|-------------|
| `name` | String | Display name |
| `url` | String | Download URL |
| `hash` | String | SHA-1 hash |
| `description` | String | Description shown to players |

## Web Map

```yaml
map:
  url: "http://localhost:8090"
  view-height: 700
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `url` | String | http://localhost:8090 | BlueMap server URL |
| `view-height` | Integer | 700 | View height parameter |

## Private Messages

```yaml
messages:
  max-queued-per-sender: 5
  queued-delivery-delay-seconds: 3
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `max-queued-per-sender` | Integer | 5 | Max queued messages |
| `queued-delivery-delay-seconds` | Integer | 3 | Delivery delay |

## Multi-World System

```yaml
worlds:
  creative:
    dimension: overworld
    gamemode: CREATIVE
    superflat: true
    generator_settings: '{"layers":[...]}'
    difficulty: peaceful
    inventory_group: creative
    teleport_warmup: false
    disable_advancements: true
    game_rules:
      doMobSpawning: false
      doDaylightCycle: false
```

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `dimension` | Enum | overworld | overworld, nether, or end |
| `gamemode` | Enum | SURVIVAL | SURVIVAL, CREATIVE, ADVENTURE, SPECTATOR |
| `seed` | Long | (random) | World seed (new worlds only) |
| `superflat` | Boolean | false | Use superflat terrain |
| `generator_settings` | String | (default) | Superflat JSON config |
| `generator` | Enum | DEFAULT | DEFAULT or VOID |
| `structures` | Boolean | true | Generate structures |
| `access` | Enum | all | all, permission, or hidden |
| `difficulty` | Enum | (server) | PEACEFUL, EASY, NORMAL, HARD |
| `inventory_group` | String | (world name) | Inventory group |
| `teleport_warmup` | Boolean | true | Use warmup countdown |
| `time` | Long | (dynamic) | Fixed time (0-24000) |
| `weather` | Enum | (dynamic) | CLEAR, RAIN, THUNDER |
| `disable_advancements` | Boolean | false | Prevent advancements |
| `pregen_size` | Integer | (none) | Pre-gen radius in blocks |
| `game_rules` | Map | {} | Game rule overrides |

### Access Modes

| Mode | Description |
|------|-------------|
| `all` | Everyone with `smp.world` can access |
| `permission` | Requires `smp.world.<worldname>` |
| `hidden` | Not accessible via `/world` |

### Game Rules

Common rules:
```yaml
game_rules:
  doMobSpawning: false
  doDaylightCycle: false
  doWeatherCycle: false
  keepInventory: true
  mobGriefing: false
  pvp: false
```

See [Minecraft Wiki - Game Rules](https://minecraft.wiki/w/Game_rule) for full list.

### Superflat Generator Settings

JSON format for superflat worlds:
```json
{"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains"}
```

See [doc/multiworld.md](multiworld.md) for detailed examples.
