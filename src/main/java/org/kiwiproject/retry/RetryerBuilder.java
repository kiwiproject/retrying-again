package org.kiwiproject.retry;

import com.google.common.base.Preconditions;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A builder used to configure and create a {@link Retryer}.
 */
public class RetryerBuilder {
    private AttemptTimeLimiter attemptTimeLimiter;
    private StopStrategy stopStrategy;
    private WaitStrategy waitStrategy;
    private BlockStrategy blockStrategy;
    private final List<Predicate<Attempt<?>>> retryPredicates = new ArrayList<>();
    private final List<RetryListener> listeners = new ArrayList<>();

    private RetryerBuilder() {
    }

    public static RetryerBuilder newBuilder() {
        return new RetryerBuilder();
    }

    /**
     * Adds a listener that will be notified of each attempt that is made
     *
     * @param listener Listener to add
     * @return <code>this</code>
     */
    public RetryerBuilder withRetryListener(@NonNull RetryListener listener) {
        Preconditions.checkNotNull(listener, "listener may not be null");
        listeners.add(listener);
        return this;
    }

    /**
     * Sets the wait strategy used to decide how long to sleep between failed attempts.
     * The default strategy is to retry immediately after a failed attempt.
     *
     * @param waitStrategy the strategy used to sleep between failed attempts
     * @return <code>this</code>
     * @throws IllegalStateException if a wait strategy has already been set.
     */
    public RetryerBuilder withWaitStrategy(@NonNull WaitStrategy waitStrategy) {
        Preconditions.checkNotNull(waitStrategy, "waitStrategy may not be null");
        Preconditions.checkState(this.waitStrategy == null,
                "a wait strategy has already been set: %s", this.waitStrategy);
        this.waitStrategy = waitStrategy;
        return this;
    }

    /**
     * Sets the stop strategy used to decide when to stop retrying. The default strategy
     * is to not stop at all .
     *
     * @param stopStrategy the strategy used to decide when to stop retrying
     * @return <code>this</code>
     * @throws IllegalStateException if a stop strategy has already been set.
     */
    public RetryerBuilder withStopStrategy(@NonNull StopStrategy stopStrategy) {
        Preconditions.checkNotNull(stopStrategy, "stopStrategy may not be null");
        Preconditions.checkState(this.stopStrategy == null, "a stop strategy has already been set: %s", this.stopStrategy);
        this.stopStrategy = stopStrategy;
        return this;
    }

    /**
     * Sets the block strategy used to decide how to block between retry attempts. The default strategy is to use Thread#sleep().
     *
     * @param blockStrategy the strategy used to decide how to block between retry attempts
     * @return <code>this</code>
     * @throws IllegalStateException if a block strategy has already been set.
     */
    public RetryerBuilder withBlockStrategy(@NonNull BlockStrategy blockStrategy) {
        Preconditions.checkNotNull(blockStrategy, "blockStrategy may not be null");
        Preconditions.checkState(this.blockStrategy == null,
                "a block strategy has already been set: %s", this.blockStrategy);
        this.blockStrategy = blockStrategy;
        return this;
    }


    /**
     * Configures the retryer to limit the duration of any particular attempt by the given duration.
     *
     * @param attemptTimeLimiter to apply to each attempt
     * @return <code>this</code>
     */
    public RetryerBuilder withAttemptTimeLimiter(@NonNull AttemptTimeLimiter attemptTimeLimiter) {
        Preconditions.checkNotNull(attemptTimeLimiter);
        this.attemptTimeLimiter = attemptTimeLimiter;
        return this;
    }

    /**
     * Configures the retryer to retry if an exception (i.e. any <code>Exception</code> or subclass
     * of <code>Exception</code>) is thrown by the call.
     *
     * @return <code>this</code>
     */
    public RetryerBuilder retryIfException() {
        retryPredicates.add(new ExceptionClassPredicate(Exception.class));
        return this;
    }

    /**
     * Configures the retryer to retry if a runtime exception (i.e. any <code>RuntimeException</code> or subclass
     * of <code>RuntimeException</code>) is thrown by the call.
     *
     * @return <code>this</code>
     */
    public RetryerBuilder retryIfRuntimeException() {
        retryPredicates.add(new ExceptionClassPredicate(RuntimeException.class));
        return this;
    }

    /**
     * Configures the retryer to retry if an exception is thrown by the call and the thrown exception's type
     * is the given class (or a subclass of the given class).
     *
     * @param exceptionClass the type of the exception which should cause the retryer to retry
     * @return <code>this</code>
     */
    public RetryerBuilder retryIfExceptionOfType(@NonNull Class<? extends Exception> exceptionClass) {
        Preconditions.checkNotNull(exceptionClass, "exceptionClass may not be null");
        retryPredicates.add(new ExceptionClassPredicate(exceptionClass));
        return this;
    }

    /**
     * Configures the retryer to retry if an exception satisfying the given predicate is
     * thrown by the call.
     *
     * @param exceptionPredicate the predicate which causes a retry if satisfied
     * @return <code>this</code>
     */
    public RetryerBuilder retryIfException(@NonNull Predicate<Exception> exceptionPredicate) {
        Preconditions.checkNotNull(exceptionPredicate, "exceptionPredicate may not be null");
        retryPredicates.add(new ExceptionPredicate(exceptionPredicate));
        return this;
    }

    /**
     * Configures the retryer to retry if the result satisfies the given predicate.
     *
     * @param <T>             The type of object tested by the predicate
     * @param resultPredicate a predicate applied to the result, and which causes the retryer
     *                        to retry if the predicate is satisfied
     * @return <code>this</code>
     */
    public <T> RetryerBuilder retryIfResult(@NonNull Predicate<T> resultPredicate) {
        Preconditions.checkNotNull(resultPredicate, "resultPredicate may not be null");
        retryPredicates.add(new ResultPredicate<>(resultPredicate));
        return this;
    }

    /**
     * Builds the retryer.
     *
     * @return the built retryer.
     */
    public Retryer build() {
        AttemptTimeLimiter theAttemptTimeLimiter = attemptTimeLimiter == null ? AttemptTimeLimiters.noTimeLimit() : attemptTimeLimiter;
        StopStrategy theStopStrategy = stopStrategy == null ? StopStrategies.neverStop() : stopStrategy;
        WaitStrategy theWaitStrategy = waitStrategy == null ? WaitStrategies.noWait() : waitStrategy;
        BlockStrategy theBlockStrategy = blockStrategy == null ? BlockStrategies.threadSleepStrategy() : blockStrategy;

        return new Retryer(
                theAttemptTimeLimiter,
                theStopStrategy,
                theWaitStrategy,
                theBlockStrategy,
                retryPredicates,
                listeners);
    }

    private static final class ExceptionClassPredicate implements Predicate<Attempt<?>> {

        private final Class<? extends Exception> exceptionClass;

        ExceptionClassPredicate(Class<? extends Exception> exceptionClass) {
            this.exceptionClass = exceptionClass;
        }

        @Override
        public boolean test(Attempt<?> attempt) {
            return attempt.hasException() &&
                    exceptionClass.isAssignableFrom(attempt.getException().getClass());
        }
    }

    private static final class ResultPredicate<T> implements Predicate<Attempt<?>> {

        private final Predicate<T> delegate;

        ResultPredicate(Predicate<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean test(Attempt<?> attempt) {
            if (!attempt.hasResult()) {
                return false;
            }
            try {
                @SuppressWarnings("unchecked")
                var result = (T) attempt.getResult();
                return delegate.test(result);
            } catch (ClassCastException e) {
                return false;
            }
        }
    }

    private static final class ExceptionPredicate implements Predicate<Attempt<?>> {

        private final Predicate<Exception> delegate;

        ExceptionPredicate(Predicate<Exception> delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean test(Attempt<?> attempt) {
            return attempt.hasException() && delegate.test(attempt.getException());
        }
    }
}
