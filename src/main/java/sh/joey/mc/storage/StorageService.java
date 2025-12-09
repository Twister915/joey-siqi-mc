package sh.joey.mc.storage;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;

import java.sql.Connection;
import java.util.List;

/**
 * Provides async database operations using RxJava.
 * Operations run on an IO thread pool and results are observed on the main thread.
 */
public final class StorageService {

    private final DatabaseService database;

    public StorageService(DatabaseService database) {
        this.database = database;
    }

    /**
     * Execute a database query that returns a result.
     * Runs on IO thread pool.
     *
     * @param operation the database operation to execute
     * @param <T> the type of the result
     * @return a Single that emits the result on the main thread
     */
    public <T> Single<T> query(SqlFunction<Connection, T> operation) {
        return Single.<T>fromCallable(() -> {
            try (Connection conn = database.getConnection()) {
                return operation.apply(conn);
            }
        })
        .subscribeOn(Schedulers.io());
    }

    /**
     * Execute a database query that may or may not return a result.
     * Runs on IO thread pool.
     *
     * @param operation the database operation to execute (returns null if no result)
     * @param <T> the type of the result
     * @return a Maybe that emits the result or completes empty if null
     */
    public <T> Maybe<T> queryMaybe(SqlFunction<Connection, T> operation) {
        return Maybe.<T>fromCallable(() -> {
            try (Connection conn = database.getConnection()) {
                return operation.apply(conn);
            }
        })
        .subscribeOn(Schedulers.io());
    }

    /**
     * Execute a database query that returns multiple results.
     * Runs on IO thread pool.
     *
     * @param operation the database operation to execute
     * @param <T> the type of each result item
     * @return a Flowable that emits each result item on the main thread
     */
    public <T> Flowable<T> queryFlowable(SqlFunction<Connection, List<T>> operation) {
        return Single.fromCallable(() -> {
            try (Connection conn = database.getConnection()) {
                return operation.apply(conn);
            }
        })
        .subscribeOn(Schedulers.io())
        .flattenAsFlowable(list -> list);
    }

    /**
     * Execute a database operation that doesn't return a value.
     * Runs on IO thread pool.
     *
     * @param operation the database operation to execute
     * @return a Completable that completes on the main thread
     */
    public Completable execute(SqlConsumer<Connection> operation) {
        return Completable.fromAction(() -> {
            try (Connection conn = database.getConnection()) {
                operation.accept(conn);
            }
        })
        .subscribeOn(Schedulers.io());
    }
}
