package sh.joey.mc.storage;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runs SQL migrations from the resources/migrations folder.
 * Migrations must follow the naming pattern: %03d_descriptive_name.sql
 */
public final class MigrationRunner {

    private static final String MIGRATIONS_PATH = "migrations";
    private static final Pattern MIGRATION_PATTERN = Pattern.compile("^(\\d{3})_.*\\.sql$");

    private final JavaPlugin plugin;
    private final DatabaseService database;
    private final Logger logger;

    public MigrationRunner(JavaPlugin plugin, DatabaseService database) {
        this.plugin = plugin;
        this.database = database;
        this.logger = plugin.getLogger();
    }

    /**
     * Run all pending migrations. Blocks until complete.
     * Should be called during plugin startup before any storage components initialize.
     *
     * @throws RuntimeException if migrations fail
     */
    public void run() {
        try (Connection conn = database.getConnection()) {
            createMigrationStateTable(conn);
            Map<String, String> appliedMigrations = getAppliedMigrations(conn);
            List<Migration> pendingMigrations = loadPendingMigrations(appliedMigrations);

            if (pendingMigrations.isEmpty()) {
                logger.info("No pending migrations");
                return;
            }

            logger.info("Running " + pendingMigrations.size() + " migration(s)...");

            for (Migration migration : pendingMigrations) {
                runMigration(conn, migration);
            }

            logger.info("All migrations completed successfully");
        } catch (SQLException e) {
            throw new RuntimeException("Migration failed", e);
        }
    }

    private void createMigrationStateTable(Connection conn) throws SQLException {
        String sql = """
            CREATE TABLE IF NOT EXISTS migration_state (
                id SERIAL PRIMARY KEY,
                filename VARCHAR(255) NOT NULL UNIQUE,
                checksum VARCHAR(64) NOT NULL,
                applied_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
            )
            """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private Map<String, String> getAppliedMigrations(Connection conn) throws SQLException {
        Map<String, String> applied = new HashMap<>();
        String sql = "SELECT filename, checksum FROM migration_state";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                applied.put(rs.getString("filename"), rs.getString("checksum"));
            }
        }

        return applied;
    }

    private List<Migration> loadPendingMigrations(Map<String, String> appliedMigrations) {
        List<Migration> pending = new ArrayList<>();

        try {
            URL migrationsUrl = plugin.getClass().getClassLoader().getResource(MIGRATIONS_PATH);
            if (migrationsUrl == null) {
                logger.info("No migrations folder found");
                return pending;
            }

            URI uri = migrationsUrl.toURI();

            if (uri.getScheme().equals("jar")) {
                // Running from JAR file
                try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.emptyMap())) {
                    Path migrationsPath = fs.getPath(MIGRATIONS_PATH);
                    loadMigrationsFromPath(migrationsPath, appliedMigrations, pending);
                }
            } else {
                // Running from filesystem (IDE/development)
                Path migrationsPath = Path.of(uri);
                loadMigrationsFromPath(migrationsPath, appliedMigrations, pending);
            }
        } catch (URISyntaxException | IOException e) {
            throw new RuntimeException("Failed to load migrations", e);
        }

        // Sort by sequence number
        pending.sort(Comparator.comparingInt(Migration::sequence));
        return pending;
    }

    private void loadMigrationsFromPath(Path migrationsPath, Map<String, String> appliedMigrations,
                                        List<Migration> pending) throws IOException {
        try (Stream<Path> stream = Files.list(migrationsPath)) {
            stream.forEach(path -> {
                String filename = path.getFileName().toString();
                Matcher matcher = MIGRATION_PATTERN.matcher(filename);

                if (!matcher.matches()) {
                    return;
                }

                int sequence = Integer.parseInt(matcher.group(1));
                try {
                    String content = Files.readString(path, StandardCharsets.UTF_8);
                    String checksum = sha256(content);

                    // Verify checksum for already-applied migrations
                    String appliedChecksum = appliedMigrations.get(filename);
                    if (appliedChecksum != null) {
                        if (!appliedChecksum.equals(checksum)) {
                            throw new RuntimeException(
                                    "Migration file '" + filename + "' has been modified after it was applied! " +
                                    "Expected checksum: " + appliedChecksum + ", actual: " + checksum);
                        }
                        return; // Already applied and checksum matches
                    }

                    pending.add(new Migration(sequence, filename, content, checksum));
                } catch (IOException e) {
                    throw new RuntimeException("Failed to read migration: " + filename, e);
                }
            });
        }
    }

    private void runMigration(Connection conn, Migration migration) throws SQLException {
        logger.info("Running migration: " + migration.filename());

        boolean originalAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);

        try {
            // Execute the migration SQL
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(migration.content());
            }

            // Record the migration
            String recordSql = "INSERT INTO migration_state (filename, checksum) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(recordSql)) {
                pstmt.setString(1, migration.filename());
                pstmt.setString(2, migration.checksum());
                pstmt.executeUpdate();
            }

            conn.commit();
            logger.info("Migration completed: " + migration.filename());
        } catch (SQLException e) {
            conn.rollback();
            throw new SQLException("Migration failed: " + migration.filename(), e);
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    private static String sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private record Migration(int sequence, String filename, String content, String checksum) {}
}
