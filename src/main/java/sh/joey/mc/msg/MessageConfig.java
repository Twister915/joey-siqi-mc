package sh.joey.mc.msg;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Configuration for the private messaging system.
 */
public record MessageConfig(
        int maxQueuedPerSender,
        int queuedDeliveryDelaySeconds
) {
    public static MessageConfig load(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        return new MessageConfig(
                config.getInt("messages.max-queued-per-sender", 5),
                config.getInt("messages.queued-delivery-delay-seconds", 3)
        );
    }
}
