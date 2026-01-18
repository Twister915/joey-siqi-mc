package sh.joey.mc.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static sh.joey.mc.storage.RxTestUtils.*;

/**
 * Tests for StorageService RxJava operations.
 * Verifies that database operations work correctly with different patterns.
 */
class StorageServiceDiagnosticTest extends PostgresIntegrationTest {

    @Test
    @DisplayName("query (Single) returns value")
    void querySingle() {
        Integer result = blockingGet(storage.query(conn -> {
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT 1 AS val")) {
                return rs.next() ? rs.getInt("val") : null;
            }
        }));

        assertThat(result).isEqualTo(1);
    }

    @Test
    @DisplayName("queryMaybe returns value when present")
    void queryMaybeWithValue() {
        Optional<Integer> result = blockingGet(storage.queryMaybe(conn -> {
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT 42 AS val")) {
                return rs.next() ? rs.getInt("val") : null;
            }
        }));

        assertThat(result).isPresent().contains(42);
    }

    @Test
    @DisplayName("queryMaybe returns empty when null")
    void queryMaybeWithNull() {
        Optional<Integer> result = blockingGet(storage.queryMaybe(conn -> {
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT 1 WHERE FALSE")) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("execute (Completable) completes")
    void execute() {
        blockingAwait(storage.execute(conn -> {
            try (var stmt = conn.createStatement()) {
                stmt.execute("SELECT 1");
            }
        }));
        // No exception = success
    }

    @Test
    @DisplayName("queryFlowable returns multiple values")
    void queryFlowable() {
        var result = blockingList(storage.queryFlowable(conn -> {
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT generate_series(1,3) AS val")) {
                var list = new java.util.ArrayList<Integer>();
                while (rs.next()) {
                    list.add(rs.getInt("val"));
                }
                return list;
            }
        }));

        assertThat(result).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("Multiple queries on same connection works")
    void multipleQueriesOnSameConnection() {
        Optional<Integer> result = blockingGet(storage.queryMaybe(conn -> {
            int firstVal;
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT 10 AS val")) {
                firstVal = rs.next() ? rs.getInt("val") : 0;
            }

            int secondVal;
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery("SELECT 20 AS val")) {
                secondVal = rs.next() ? rs.getInt("val") : 0;
            }

            return firstVal + secondVal;
        }));

        assertThat(result).isPresent().contains(30);
    }

    @Test
    @DisplayName("Multiple PreparedStatements on same connection works")
    void multiplePreparedStatementsOnSameConnection() {
        Optional<Integer> result = blockingGet(storage.queryMaybe(conn -> {
            int firstVal;
            try (var stmt = conn.prepareStatement("SELECT ? AS val")) {
                stmt.setInt(1, 100);
                try (var rs = stmt.executeQuery()) {
                    firstVal = rs.next() ? rs.getInt("val") : 0;
                }
            }

            int secondVal;
            try (var stmt = conn.prepareStatement("SELECT ? AS val")) {
                stmt.setInt(1, 200);
                try (var rs = stmt.executeQuery()) {
                    secondVal = rs.next() ? rs.getInt("val") : 0;
                }
            }

            return firstVal + secondVal;
        }));

        assertThat(result).isPresent().contains(300);
    }

    @Test
    @DisplayName("Nested query calls on same connection works")
    void nestedQueryCallsOnSameConnection() {
        Optional<Integer> result = blockingGet(storage.queryMaybe(conn -> {
            int firstVal;
            try (var stmt = conn.prepareStatement("SELECT ? AS val")) {
                stmt.setInt(1, 1);
                try (var rs = stmt.executeQuery()) {
                    firstVal = rs.next() ? rs.getInt("val") : 0;
                }
            }

            int nestedVal = runNestedQuery(conn, 5);
            return firstVal + nestedVal;
        }));

        assertThat(result).isPresent().contains(6);
    }

    private int runNestedQuery(java.sql.Connection conn, int value) throws java.sql.SQLException {
        try (var stmt = conn.prepareStatement("SELECT ? AS val")) {
            stmt.setInt(1, value);
            try (var rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("val") : 0;
            }
        }
    }
}
