# Plan: Add Server Knowledge to Steve AI

## Summary

Enhance Steve AI to answer questions about server plugin features (homes, teleportation, RTP, settings, protection, etc.) by bundling condensed documentation in the JAR and including it in the cached system prompt.

## Files to Create

### 1. `src/main/resources/steve/server-knowledge.md`

Condensed player-facing documentation (~80 lines) covering:
- Homes (`/home set`, `/home list`, sharing)
- Teleportation (`/tp`, `/back`, warmup system)
- Warps & Spawn
- RTP (`/rtp`, cooldown, biome hints)
- Worlds (`/world`, `/survival`, `/creative`)
- Settings (keep inventory, easy mode, passive mode, display time)
- Protection (claiming with lodestone, trusting players)
- Cosmetics (elytra trails, nicknames)
- Merit & Challenges
- Private messages, utility commands

Example content structure:
```markdown
# Server Features Knowledge Base

## Homes
- `/home [name]` - Teleport to a saved home location (defaults to "home")
- `/home set [name]` - Save current location as a home
- `/home delete <name>` - Delete a home (requires confirmation)
- `/home list` - List all your homes with distances
- `/home share <name> <player>` - Let another player use your home
- Access shared homes with `/home owner:homename`
- First bed interaction auto-saves as "home"

## Teleportation
- `/tp <player>` - Request to teleport to another player (they must accept)
- `/tphere <player>` - Request a player to teleport to you
- `/accept` / `/decline` - Respond to teleport requests
- `/back` - Return to death location or where you teleported from
- Teleports have a 3-second warmup (movement cancels)

## Warps & Spawn
- `/warp` - List all server warps (click to teleport)
- `/warp <name>` - Teleport to a warp
- `/spawn` - Teleport to world spawn

## Random Teleport (RTP)
- `/rtp` - Get 5 random location options with biome hints
- Click a location or use `/rtp select <1-5>` to teleport
- 5-minute cooldown between uses
- Safe locations only (avoids oceans, lava, void)

## Worlds
- `/world` - List all accessible worlds
- `/world <name>` - Teleport to a world
- `/survival`, `/creative`, `/superflat` - Quick shortcuts
- Different worlds may have separate inventories

## Player Settings
- `/settings` - Open the settings menu
- **Keep Inventory** - Keep items/XP when you die
- **Display Time** - Control boss bar time display (Always/Clock/Never)
- **Easy Mode** - Mobs deal 25% damage + 5% instant kill chance
- **Passive Mode** - Disable all PvP combat

## Land Protection
- Place a lodestone and `/protection claim` to create a protected area
- Protection is a circle around each lodestone (default 16 blocks radius)
- `/protection trust <player>` - Allow someone to build in your region
- `/protection untrust <player>` - Remove build access
- `/protection settings` - Configure who can build, open containers, use doors
- `/protection visualize` - Show boundary particles

## Cosmetics
- `/trails elytra <effect>` - Set elytra trail particles (flame, soul, rainbow, etc.)
- `/trails elytra rgb:RRGGBB` - Custom hex color trail
- `/nick <name>` - Set your display name
- `/nick clear` - Remove nickname

## Merit & Challenges
- `/challenges` - View weekly challenges for merit rewards
- Complete challenges to level up
- Level shows as a colored prefix in chat

## Private Messages
- `/msg <player> <message>` - Send a private message
- `/reply <message>` or `/r` - Reply to the last message

## Other Useful Commands
- `/list` - See who's online
- `/map` - Get a link to the web map centered on you
- `/ping` - Check your connection latency
- `/seen <player>` - When a player was last online
- `/ontime` - View your total playtime

## Steve AI
- Mention @Steve in chat to ask Minecraft questions
- Follow-up questions within 60 seconds use conversation context
- Example: "@Steve how do I make a shield?"
```

### 2. `src/main/java/sh/joey/mc/steve/ServerKnowledgeLoader.java`

Simple class to load server knowledge from resources:

```java
package sh.joey.mc.steve;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Loads server-specific knowledge for Steve AI from bundled resources.
 */
public final class ServerKnowledgeLoader {

    private static final String RESOURCE_PATH = "steve/server-knowledge.md";

    private final @Nullable JavaPlugin plugin;
    private final Logger logger;

    public ServerKnowledgeLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public ServerKnowledgeLoader(Logger logger) {
        this.plugin = null;
        this.logger = logger;
    }

    public String load() {
        ClassLoader classLoader = plugin != null
                ? plugin.getClass().getClassLoader()
                : getClass().getClassLoader();

        try (InputStream is = classLoader.getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                throw new RuntimeException("Server knowledge resource not found: " + RESOURCE_PATH);
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            logger.info("Loaded server knowledge (" + content.length() + " chars)");
            return content;
        } catch (IOException e) {
            throw new RuntimeException("Failed to load server knowledge", e);
        }
    }
}
```

## Files to Modify

### 1. `src/main/java/sh/joey/mc/steve/SteveSystemPrompt.java`

Add static field and methods:

```java
/**
 * Server-specific knowledge loaded from resources.
 * Set by SteveManager during initialization.
 */
private static String serverKnowledge = "";

/**
 * Sets the server knowledge content (called during plugin initialization).
 */
public static void setServerKnowledge(String knowledge) {
    serverKnowledge = knowledge;
}

/**
 * Gets the combined knowledge base (Minecraft + Server) for caching.
 */
public static String getCombinedKnowledge() {
    if (serverKnowledge.isEmpty()) {
        return MINECRAFT_KNOWLEDGE;
    }
    return MINECRAFT_KNOWLEDGE + "\n\n" + serverKnowledge;
}
```

### 2. `src/main/java/sh/joey/mc/steve/provider/AnthropicSteveProvider.java`

Line 162 - change from:
```java
knowledgeBlock.addProperty("text", SteveSystemPrompt.MINECRAFT_KNOWLEDGE);
```

To:
```java
knowledgeBlock.addProperty("text", SteveSystemPrompt.getCombinedKnowledge());
```

### 3. `src/main/java/sh/joey/mc/steve/SteveManager.java`

Add initialization in constructor (before other setup):

```java
// Load server knowledge for Steve
ServerKnowledgeLoader loader = new ServerKnowledgeLoader(plugin);
SteveSystemPrompt.setServerKnowledge(loader.load());
```

## Design Notes

- **Single cached block**: Server knowledge is combined with `MINECRAFT_KNOWLEDGE` in one cached block for reliable cache prefix matching
- **Condensed content**: ~80 lines optimized for Q&A, omits config syntax and admin commands
- **Same loading pattern**: Follows `MigrationRunner` pattern for JAR resource loading
- **Graceful fallback**: If server knowledge fails to load, Steve still works with vanilla Minecraft knowledge only

## Verification

1. Build: `./gradlew shadowJar`
2. Deploy to test server
3. Ask Steve server-specific questions:
   - `@Steve how do I set a home?`
   - `@Steve what does easy mode do?`
   - `@Steve how do I protect my builds?`
   - `@Steve how do I use RTP?`
4. Verify prompt caching still works (check logs for `cache-read` tokens on second request)
5. Verify Steve still answers vanilla Minecraft questions correctly
