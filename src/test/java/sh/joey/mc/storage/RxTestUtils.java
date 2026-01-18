package sh.joey.mc.storage;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods for blocking on RxJava types in tests.
 * Uses 10 second timeout to prevent tests from hanging forever.
 */
public final class RxTestUtils {

    private static final long TIMEOUT_SECONDS = 10;

    private RxTestUtils() {}

    /**
     * Block and get the value from a Single.
     */
    public static <T> T blockingGet(Single<T> single) {
        return single.timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).blockingGet();
    }

    /**
     * Block and get the value from a Maybe.
     */
    public static <T> Optional<T> blockingGet(Maybe<T> maybe) {
        return Optional.ofNullable(maybe.timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).blockingGet());
    }

    /**
     * Block and collect all values from a Flowable into a List.
     */
    public static <T> List<T> blockingList(Flowable<T> flowable) {
        return flowable.timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).toList().blockingGet();
    }

    /**
     * Block until a Completable completes.
     */
    public static void blockingAwait(Completable completable) {
        completable.timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).blockingAwait();
    }
}
