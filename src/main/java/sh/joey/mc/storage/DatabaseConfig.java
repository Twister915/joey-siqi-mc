package sh.joey.mc.storage;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Configuration for PostgreSQL database connection.
 */
public record DatabaseConfig(
        String host,
        int port,
        String database,
        String username,
        String password,
        int poolSize,
        boolean logQueries
) {
    public static DatabaseConfig load(JavaPlugin plugin) {
        plugin.saveDefaultConfig();
        FileConfiguration config = plugin.getConfig();

        return new DatabaseConfig(
                config.getString("database.host", "localhost"),
                config.getInt("database.port", 5432),
                config.getString("database.database", "minecraft"),
                config.getString("database.username", "minecraft"),
                config.getString("database.password", ""),
                config.getInt("database.pool-size", 3),
                config.getBoolean("database.log-queries", false)
        );
    }

    /**
     * Returns the JDBC URL for this database configuration.
     */
    public String jdbcUrl() {
        return "jdbc:postgresql://" + host + ":" + port + "/" + database;
    }
}
