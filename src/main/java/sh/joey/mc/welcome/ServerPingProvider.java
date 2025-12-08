package sh.joey.mc.welcome;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Provides dynamic MOTD messages when the server is pinged.
 */
public final class ServerPingProvider implements Listener {

    private final JavaPlugin plugin;
    private final Random random = ThreadLocalRandom.current();

    public ServerPingProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onServerPing(ServerListPingEvent event) {
        Component motd = generateMotd();
        event.motd(motd);
    }

    private Component generateMotd() {
        // First line: Server name/branding
        Component line1 = Component.text("Siqi & Joey's Minecraft")
                .color(NamedTextColor.LIGHT_PURPLE)
                .decorate(TextDecoration.BOLD);

        // Second line: Random message
        String message = getMessage();
        Component line2 = Component.text(message).color(NamedTextColor.GRAY);

        return line1.append(Component.newline()).append(line2);
    }

    private String getMessage() {
        // 30% context-aware, 70% static
        if (random.nextInt(100) < 30) {
            String contextMsg = getContextMessage();
            if (contextMsg != null) {
                return contextMsg;
            }
        }
        return getStaticMessage();
    }

    private String getContextMessage() {
        // Check overworld time/weather if available
        World overworld = plugin.getServer().getWorlds().stream()
                .filter(w -> w.getEnvironment() == World.Environment.NORMAL)
                .findFirst()
                .orElse(null);

        if (overworld == null) {
            return null;
        }

        long time = overworld.getTime();
        boolean isNight = time >= 13000 && time < 23000;
        boolean isStorm = overworld.hasStorm();
        boolean isThunder = overworld.isThundering();
        int playerCount = plugin.getServer().getOnlinePlayers().size();

        // Context-based messages
        if (isThunder) {
            return pick(THUNDER_MESSAGES);
        }
        if (isStorm) {
            return pick(RAIN_MESSAGES);
        }
        if (isNight) {
            return pick(NIGHT_MESSAGES);
        }
        if (playerCount == 0) {
            return pick(EMPTY_SERVER_MESSAGES);
        }
        if (playerCount >= 2) {
            return "Friends are online! Join the fun.";
        }

        return null;
    }

    private String getStaticMessage() {
        return pick(STATIC_MESSAGES);
    }

    private String pick(List<String> list) {
        return list.get(random.nextInt(list.size()));
    }

    private static final List<String> STATIC_MESSAGES = List.of(
            // Inviting
            "Come build something amazing!",
            "Adventure awaits within...",
            "Your next masterpiece starts here.",
            "Diamonds are waiting. Are you?",
            "A world of possibilities.",
            "Where creativity meets survival.",
            "Join us! The creepers are friendly. Mostly.",
            "Building dreams, one block at a time.",
            "Your adventure continues here.",
            "Home of epic builds and good vibes.",

            // Humorous
            "Now with 100% more blocks!",
            "Creeper-tested, Steve-approved.",
            "Warning: May cause excessive mining.",
            "Side effects include fun.",
            "No refunds on lost diamonds.",
            "Certified lava-free login area.",
            "Where beds actually let you sleep.",
            "Gravel-free since... well, nevermind.",
            "Scientifically proven to contain blocks.",
            "The villagers are only mildly suspicious.",

            // Mysterious
            "Secrets hide in the depths...",
            "The void whispers... 'join us'",
            "Ancient treasures await discovery.",
            "What will you uncover today?",
            "Legends are made here.",

            // Casual
            "Come hang out!",
            "Good times ahead.",
            "Grab your pickaxe!",
            "Let's build something cool.",
            "Ready when you are!",

            // Minecraft references
            "Punch trees, get wood, ???, profit.",
            "Fortune III recommended.",
            "Torches sold separately.",
            "Achievement Get: Reading the MOTD",
            "Press any key to... wait, wrong game."
    );

    private static final List<String> NIGHT_MESSAGES = List.of(
            "It's nighttime... if you dare.",
            "The mobs are active. Join anyway!",
            "Monsters roam the overworld. Perfect.",
            "Night has fallen. Adventure calls!",
            "Dark outside, but the server's cozy."
    );

    private static final List<String> RAIN_MESSAGES = List.of(
            "Rainy day vibes. Perfect for building!",
            "It's raining! Great fishing weather.",
            "Storm's rolling in. Bring an umbrella.",
            "Cozy rain sounds await you.",
            "Wet outside, warm inside."
    );

    private static final List<String> THUNDER_MESSAGES = List.of(
            "Thunder rumbles! Epic timing.",
            "Lightning strikes! Dramatic entry awaits.",
            "Storm raging! Charged creepers possible.",
            "Thunder and lightning! Very exciting!",
            "Epic storm in progress. Join the drama!"
    );

    private static final List<String> EMPTY_SERVER_MESSAGES = List.of(
            "It's quiet... too quiet. Fix that!",
            "The server feels lonely. Help!",
            "Empty server = all the resources!",
            "First one here gets bragging rights.",
            "Be the first to join today!",
            "The mobs outnumber the players. Change that!",
            "Your friends aren't here yet. Beat them!"
    );
}
