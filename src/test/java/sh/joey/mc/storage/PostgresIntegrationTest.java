package sh.joey.mc.storage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Base class for PostgreSQL integration tests using Testcontainers.
 * Provides a shared PostgreSQL container and handles setup/teardown.
 */
@Tag("integration")
@Testcontainers
public abstract class PostgresIntegrationTest {

    private static final Logger LOGGER = Logger.getLogger(PostgresIntegrationTest.class.getName());

    @Container
    protected static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    protected static DatabaseService database;
    protected static StorageService storage;

    @BeforeAll
    static void setUpDatabase() {
        database = new DatabaseService(LOGGER);

        DatabaseConfig config = new DatabaseConfig(
                postgres.getHost(),
                postgres.getFirstMappedPort(),
                postgres.getDatabaseName(),
                postgres.getUsername(),
                postgres.getPassword(),
                3,
                false
        );

        database.initialize(config);

        // Run migrations
        MigrationRunner migrationRunner = new MigrationRunner(database, LOGGER);
        migrationRunner.run();

        storage = new StorageService(database);
    }

    @AfterAll
    static void tearDownDatabase() {
        if (database != null) {
            database.dispose();
        }
    }

    @BeforeEach
    void truncateTables() throws SQLException {
        try (Connection conn = database.getConnection();
             Statement stmt = conn.createStatement()) {
            // Get all tables except migration_state and system tables
            var rs = stmt.executeQuery("""
                    SELECT tablename FROM pg_tables
                    WHERE schemaname = 'public'
                      AND tablename != 'migration_state'
                    """);

            StringBuilder tables = new StringBuilder();
            while (rs.next()) {
                if (!tables.isEmpty()) {
                    tables.append(", ");
                }
                tables.append(rs.getString("tablename"));
            }
            rs.close();

            if (!tables.isEmpty()) {
                stmt.execute("TRUNCATE TABLE " + tables + " CASCADE");
            }
        }
    }

    /**
     * Execute a raw SQL query and return the count of rows affected or matching.
     * Useful for verifying database state in tests.
     */
    protected int countRows(String sql) throws SQLException {
        try (Connection conn = database.getConnection();
             Statement stmt = conn.createStatement();
             var rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }
}
