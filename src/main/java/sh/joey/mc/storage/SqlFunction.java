package sh.joey.mc.storage;

import java.sql.SQLException;

/**
 * A function that takes an input and returns a result, potentially throwing SQLException.
 *
 * @param <T> the type of the input
 * @param <R> the type of the result
 */
@FunctionalInterface
public interface SqlFunction<T, R> {
    R apply(T t) throws SQLException;
}
