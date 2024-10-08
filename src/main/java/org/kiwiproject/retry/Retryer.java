package org.kiwiproject.retry;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.kiwiproject.retry.Attempt.newExceptionAttempt;
import static org.kiwiproject.retry.Attempt.newResultAttempt;

import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * A retryer, which executes a call, and retries it until it succeeds, or
 * a stop strategy decides to stop retrying. A wait strategy is used to sleep
 * between attempts. The strategy to decide if the call succeeds or not is
 * also configurable.
 * <p>
 * A retryer can also wrap the callable into a RetryerCallable, which can be submitted to an executor.
 * <p>
 * Retryer instances are better constructed with a {@link RetryerBuilder}. A retryer
 * is thread-safe, provided the arguments passed to its constructor are thread-safe.
 */
public final class Retryer {
    private final StopStrategy stopStrategy;
    private final WaitStrategy waitStrategy;
    private final BlockStrategy blockStrategy;
    private final AttemptTimeLimiter attemptTimeLimiter;
    private final List<Predicate<Attempt<?>>> retryPredicates;
    private final Collection<RetryListener> listeners;

    /**
     * @param attemptTimeLimiter to prevent from any single attempt from spinning infinitely
     * @param stopStrategy       the strategy used to decide when the retryer must stop retrying
     * @param waitStrategy       the strategy used to decide how much time to sleep between attempts
     * @param blockStrategy      the strategy used to decide how to block between retry attempts;
     *                           e.g., Thread#sleep(), latches, etc.
     * @param retryPredicates    the predicates used to decide if the attempt must be retried (without
     *                           regard to the StopStrategy).
     * @param listeners          collection of retry listeners
     */
    Retryer(@NonNull AttemptTimeLimiter attemptTimeLimiter,
            @NonNull StopStrategy stopStrategy,
            @NonNull WaitStrategy waitStrategy,
            @NonNull BlockStrategy blockStrategy,
            @NonNull List<Predicate<Attempt<?>>> retryPredicates,
            @NonNull Collection<RetryListener> listeners) {

        checkNotNull(attemptTimeLimiter, "timeLimiter may not be null");
        checkNotNull(stopStrategy, "stopStrategy may not be null");
        checkNotNull(waitStrategy, "waitStrategy may not be null");
        checkNotNull(blockStrategy, "blockStrategy may not be null");
        checkNotNull(retryPredicates, "retryPredicates may not be null");
        checkNotNull(listeners, "listeners may not null");

        this.attemptTimeLimiter = attemptTimeLimiter;
        this.stopStrategy = stopStrategy;
        this.waitStrategy = waitStrategy;
        this.blockStrategy = blockStrategy;
        this.retryPredicates = retryPredicates;
        this.listeners = listeners;
    }

    /**
     * Executes the given callable, retrying if necessary. If the retry predicate
     * accepts the attempt, the stop strategy is used to decide if a new attempt
     * must be made. Then the wait strategy is used to decide how much time to sleep,
     * and a new attempt is made.
     *
     * @param callable the callable task to be executed
     * @param <T>      the return type of the Callable
     * @return the computed result of the given callable
     * @throws RetryException       if all the attempts failed before the stop strategy decided to abort
     * @throws InterruptedException If this thread is interrupted. This can happen because
     *                              {@link Thread#sleep} is invoked between attempts
     */
    public <T> T call(Callable<T> callable) throws RetryException, InterruptedException {
        long startTimeNanos = System.nanoTime();
        for (var attemptNumber = 1; ; attemptNumber++) {
            var attempt = call(callable, startTimeNanos, attemptNumber);

            listeners.forEach(listener -> safeInvokeListener(listener, attempt));

            if (!shouldRetry(attempt)) {
                return getOrThrow(attempt);
            }

            if (stopStrategy.shouldStop(attempt)) {
                throw new RetryException(attempt);
            } else {
                long sleepTime = waitStrategy.computeSleepTime(attempt);
                blockStrategy.block(sleepTime);
            }
        }
    }

    private <T> Attempt<T> call(Callable<T> callable, long startTimeNanos, int attemptNumber)
            throws InterruptedException {

        try {
            T result = attemptTimeLimiter.call(callable);
            return newResultAttempt(result, attemptNumber, computeMillisSince(startTimeNanos));
        } catch (InterruptedException e) {
            throw e;
        } catch (Exception e) {
            return newExceptionAttempt(e, attemptNumber, computeMillisSince(startTimeNanos));
        }
    }

    private static long computeMillisSince(long startTimeNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
    }

    private static <T> void safeInvokeListener(RetryListener listener, Attempt<T> attempt) {
        try {
            listener.onRetry(attempt);
        } catch (Exception exception) {
            // intentionally ignored per the API Note in RetryListener#onRetry
        }
    }

    /**
     * Executes the given runnable, retrying if necessary. If the retry predicate
     * accepts the attempt, the stop strategy is used to decide if a new attempt
     * must be made. Then the wait strategy is used to decide how much time to sleep,
     * and a new attempt is made.
     *
     * @param runnable the runnable task to be executed
     * @throws RetryException       if all the attempts failed before the stop strategy decided
     *                              to abort
     * @throws InterruptedException If this thread is interrupted. This can happen because
     *                              {@link Thread#sleep} is invoked between attempts
     */
    public void run(Runnable runnable) throws RetryException, InterruptedException {
        call(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Throw the Attempt's exception, if it has one, wrapped in a RetryException. Otherwise,
     * return the attempt's result.
     *
     * @param attempt An attempt that was made by invoking the call
     * @param <T>     The type of the attempt
     * @return The result of the attempt
     * @throws RetryException If the attempt has an exception
     */
    private <T> T getOrThrow(Attempt<T> attempt) throws RetryException {
        if (attempt.hasException()) {
            throw new RetryException(attempt);
        }
        return attempt.getResult();
    }

    /**
     * Applies the retry predicates to the attempt, in order, until any
     * predicate returns true or all predicates return false.
     *
     * @param attempt The attempt made by invoking the call
     */
    private boolean shouldRetry(Attempt<?> attempt) {
        for (Predicate<Attempt<?>> predicate : retryPredicates) {
            if (predicate.test(attempt)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Wraps the given {@link Callable} in a {@link RetryerCallable}, which can
     * be submitted to an executor. The returned {@link RetryerCallable} uses
     * this {@link Retryer} instance to call the given {@link Callable}.
     *
     * @param callable the callable to wrap
     * @param <T>      the return type of the Callable
     * @return a {@link RetryerCallable} that behaves like the given {@link Callable} with retry behavior defined by this {@link Retryer}
     */
    public <T> RetryerCallable<T> wrap(Callable<T> callable) {
        return new RetryerCallable<>(this, callable);
    }

    /**
     * A {@link Callable} which wraps another {@link Callable} in order to add
     * retrying behavior from a given {@link Retryer} instance.
     */
    public static class RetryerCallable<T> implements Callable<T> {
        private final Retryer retryer;
        private final Callable<T> callable;

        private RetryerCallable(Retryer retryer, Callable<T> callable) {
            this.retryer = retryer;
            this.callable = callable;
        }

        /**
         * Makes the enclosing retryer call the wrapped callable.
         *
         * @see Retryer#call(Callable)
         */
        @Override
        public T call() throws Exception {
            return retryer.call(callable);
        }
    }
}
