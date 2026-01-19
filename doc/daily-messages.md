# Daily Message System

The daily message system sends themed messages to players at the start of each Minecraft day. Messages are a mix of static greetings, procedurally-generated content, and context-aware observations about the player's current situation.

## When Messages Are Sent

### Start of Minecraft Day

Every Minecraft day cycle (approximately 20 minutes in real time), when the in-game time reaches dawn (0-100 ticks), all online players receive a day message. Each world tracks its own day cycle independently, so players in different worlds receive messages at different times.

Messages are only sent in the overworld (NORMAL dimension). Players in the Nether or End do not receive day messages.

### World Entry During Dawn

When a player enters a world during the dawn period (time 0-2000 ticks), they receive a personalized day message immediately. This ensures players who just logged in or changed worlds during dawn don't miss the daily greeting.

## Message Format

All day messages are prefixed with a gold sun icon:

```
[☀] Good morning! The sun rises on a new day of adventure.
```

The prefix is gold/yellow, and the message content is white.

## Types of Messages

Messages are selected using a weighted distribution:
- **35%** - Context-aware messages (based on player/world state)
- **25%** - Procedural messages (generated from templates and word banks)
- **40%** - Static messages (pre-written greetings)

### Static Messages

The system includes approximately 150 pre-written messages covering:

- **Classic greetings**: "Rise and shine!", "Good morning!", "A new day dawns."
- **Minecraft-themed**: "The sun rises. Time to punch some trees.", "Rise and shine! The creepers are sleeping... for now."
- **Advice and warnings**: "Remember to eat breakfast before mining.", "Check your pickaxe durability before heading down."
- **Philosophical**: "Another day, another diamond... hopefully.", "The world continues to generate."

### Procedural Messages

Procedural messages are built from templates combined with word banks. The system has 45+ templates and 800+ word bank entries across categories like:

| Category | Examples |
|----------|----------|
| Activities | "explore some caves", "hunt for diamonds", "build something ridiculous" |
| Advice | "bringing extra torches", "never trusting gravel", "keeping a water bucket handy" |
| Mobs | "creeper", "enderman", "warden", "guardian" |
| Biomes | "dark forest", "desert", "jungle", "cherry grove" |
| Discoveries | "treasure", "cave system", "village", "ancient city" |
| Treasures | "diamond vein", "enchanted book", "elytra", "mending book" |
| Adjectives | "mysterious", "ancient", "dangerous", "cursed", "blessed" |

**Example templates:**
- "Today feels like a good day to [activity]."
- "The spirits whisper of [whisper]."
- "Legends speak of a [adjective] [treasure] nearby."
- "Somewhere, a [mob] is thinking about you."

**Example generated messages:**
- "Today feels like a good day to hunt for diamonds."
- "The spirits whisper of emeralds in the mountains."
- "Legends speak of a mysterious enchanted book nearby."
- "Somewhere, a creeper is thinking about you."

### Context-Aware Messages

The most dynamic messages observe the player's current state and environment. Context is evaluated in priority order, with higher-priority conditions checked first.

#### Player State

| Condition | Example Messages |
|-----------|------------------|
| Low health | "Looking a little rough there. Maybe eat something?" |
| Full health | "Full health! You're ready for anything." |
| Hungry | "Your stomach rumbles. Time for breakfast?" |
| High XP | "All those levels... time to enchant something?" |
| No armor | "Feeling brave without armor today?" |
| Full diamond/netherite | "Fully armored and dangerous." |

#### Equipment State

| Condition | Example Messages |
|-----------|------------------|
| Tool nearly broken | "Your pickaxe is on its last legs." |
| Holding diamond pickaxe | "Diamond pickaxe in hand. Diamonds await." |
| Combat ready | "Sword drawn at dawn. Looking for trouble?" |
| Elytra equipped | "Wings ready. The sky awaits." |

#### Inventory Contents

| Condition | Example Messages |
|-----------|------------------|
| Has diamonds | "Diamonds in your pockets. Don't lose them!" |
| Has building materials | "Lots of building materials. Big project planned?" |
| Has music discs | "A music collection! Time for a listening party?" |
| Has brewing ingredients | "Brewing something special?" |

#### Location State

| Condition | Example Messages |
|-----------|------------------|
| Underground | "Deep in the earth. Diamonds lurk at this depth." |
| At high Y-level | "High up in the world. Watch your step." |
| In water/boat | "On the water at dawn. Peaceful." |
| Near bed | "Just woke up? The day awaits." |

#### Environmental Hazards

| Condition | Example Messages |
|-----------|------------------|
| Near lava | "Lava nearby. Careful!" |
| Near spawner | "A spawner nearby. Opportunity or danger?" |
| Low light level | "It's dark here. Mobs could spawn." |
| Near portal | "The portal hums with energy." |

#### World State

| Condition | Example Messages |
|-----------|------------------|
| Day milestone (100, 500, etc.) | "Day 100! You've survived this long." |
| Hard difficulty | "Hard mode. The mobs hit harder today." |
| Full moon | "Full moon last night. The mobs were restless." |
| Raining | "Rain falls on a new day." |
| Thunderstorm | "Thunder rolls across the land." |

#### Nearby Entities

| Condition | Example Messages |
|-----------|------------------|
| Hostile mob nearby | "A skeleton lurks nearby. Stay alert." |
| Villager nearby | "The villagers are waking up too." |
| Wolf nearby | "Your loyal wolf greets the dawn with you." |
| Cat nearby | "Your cat stretches in the morning light." |

#### Biome-Specific (Fallback)

When no other context applies, biome-specific messages provide variety:

| Biome | Example Messages |
|-------|------------------|
| Jungle | "The jungle awakens. Parrots chatter in the trees." |
| Desert | "The desert sun rises hot and bright." |
| Taiga | "Pine trees stretch toward the morning sky." |
| Deep Dark | "Even the sculk sensors seem quieter at dawn." |
| Cherry Grove | "Cherry blossoms drift in the morning breeze." |

## Debug Command

Server operators can use `/daymsgdebug` (requires `smp.debug` permission) to:

- View all message categories with counts
- Browse messages by category
- See all available messages with pagination

This helps verify message variety and troubleshoot the system.
