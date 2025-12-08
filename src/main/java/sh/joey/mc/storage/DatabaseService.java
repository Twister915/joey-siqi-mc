package sh.joey.mc.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.reactivex.rxjava3.disposables.Disposable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

/**
 * Manages the HikariCP connection pool for PostgreSQL.
 * Reload-safe: will close existing pool before creating a new one.
 */
public final class DatabaseService implements Disposable {

    static {
        // Explicitly load the PostgreSQL driver.
        // ServiceLoader doesn't work reliably in shaded JARs.
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("PostgreSQL driver not found", e);
        }
    }

    private final Logger logger;
    private HikariDataSource dataSource;
    private boolean disposed = false;
    private boolean logQueries = false;

    public DatabaseService(Logger logger) {
        this.logger = logger;
    }

    /**
     * Initialize the connection pool with the given configuration.
     * If a pool already exists, it will be closed first.
     */
    public void initialize(DatabaseConfig config) {
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing existing database connection pool");
            dataSource.close();
        }

        this.logQueries = config.logQueries();

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.jdbcUrl());
        hikariConfig.setUsername(config.username());
        hikariConfig.setPassword(config.password());
        hikariConfig.setMaximumPoolSize(config.poolSize());

        // Connection validation
        hikariConfig.setConnectionTestQuery("SELECT 1");

        // Pool name for debugging
        hikariConfig.setPoolName("SiqiJoey-PostgreSQL");

        dataSource = new HikariDataSource(hikariConfig);
        logger.info("Database connection pool initialized: " + config.jdbcUrl());
        if (logQueries) {
            logger.info("SQL query logging enabled");
        }
    }

    /**
     * Get a connection from the pool.
     * If query logging is enabled, returns a wrapped connection that logs SQL.
     *
     * @return a database connection
     * @throws SQLException if a connection cannot be obtained
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized or has been closed");
        }
        Connection conn = dataSource.getConnection();
        if (logQueries) {
            return new LoggingConnection(conn, logger);
        }
        return conn;
    }

    @Override
    public void dispose() {
        disposed = true;
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database connection pool closed");
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
