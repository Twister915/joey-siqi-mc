package sh.joey.mc.storage;

import java.sql.SQLException;

/**
 * A consumer that accepts an input and may throw SQLException.
 *
 * @param <T> the type of the input
 */
@FunctionalInterface
public interface SqlConsumer<T> {
    void accept(T t) throws SQLException;
}
