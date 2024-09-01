package org.kiwiproject.retry;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.requireNonNull;

/**
 * An attempt of a call, which resulted either in a result returned by the call,
 * or in an Exception thrown by the call.
 *
 * @param <T> The type returned by the wrapped callable.
 */
@SuppressWarnings("WeakerAccess")
public class Attempt<T> {

    private final T result;

    private final Exception exception;

    private final int attemptNumber;

    private final long delaySinceFirstAttempt;

    private Attempt(T result, int attemptNumber, long delaySinceFirstAttempt) {
        this(result, null, attemptNumber, delaySinceFirstAttempt);
    }

    private Attempt(Exception exception, int attemptNumber, long delaySinceFirstAttempt) {
        this(
            null,
            requireNonNull(exception, "exception must not be null"),
            attemptNumber,
            delaySinceFirstAttempt
        );
    }

    private Attempt(T result, Exception exception, int attemptNumber, long delaySinceFirstAttempt) {
        this.result = result;
        this.exception = exception;
        this.attemptNumber = attemptNumber;
        this.delaySinceFirstAttempt = delaySinceFirstAttempt;
    }

    /**
     * Create a new {@link Attempt} that has a result.
     *
     * @param result                 the result of the attempt; it may be null
     * @param attemptNumber          the number of this attempt
     * @param delaySinceFirstAttempt the delay in milliseconds since the first attempt was made
     * @param <T>                    the type of result
     * @return a new Attempt instance
     */
    static <T> Attempt<T> newResultAttempt(T result, int attemptNumber, long delaySinceFirstAttempt) {
        return new Attempt<>(result, attemptNumber, delaySinceFirstAttempt);
    }

    /**
     * Create a new {@link Attempt} that failed with an exception.
     *
     * @param exception              the exception thrown by this attempt
     * @param attemptNumber          the number of this attempt
     * @param delaySinceFirstAttempt the delay in milliseconds since the first attempt was made
     * @param <T>                    the type of result the attempt would have returned if it had not thrown an exception
     * @return a new Attempt instance
     */
    static <T> Attempt<T> newExceptionAttempt(Exception exception, int attemptNumber, long delaySinceFirstAttempt) {
        return new Attempt<>(exception, attemptNumber, delaySinceFirstAttempt);
    }

    /**
     * Tells if the call returned a result or not
     *
     * @return <code>true</code> if the call returned a result, <code>false</code>
     * if it threw an exception
     */
    public boolean hasResult() {
        // Check the exception field, because the Callable may have succeeded and returned null.
        // In that case, both exception and result will be null.
        return exception == null;
    }

    /**
     * Tells if the call threw an exception or not
     *
     * @return <code>true</code> if the call threw an exception, <code>false</code>
     * if it returned a result
     */
    public boolean hasException() {
        return exception != null;
    }

    /**
     * Gets the result of the call
     *
     * @return the result of the call (can be {@code null})
     * @throws IllegalStateException if the call didn't return a result, but threw an exception,
     *                               as indicated by {@link #hasResult()}
     */
    public T getResult() {
        checkState(hasResult(), "The attempt resulted in an exception, not in a result");
        return result;
    }

    /**
     * Gets the exception thrown by the call
     *
     * @return the exception thrown by the call
     * @throws IllegalStateException if the call didn't throw an exception,
     *                               as indicated by {@link #hasException()}
     */
    public Exception getException() {
        checkState(hasException(), "The attempt resulted in a result, not in an exception");
        return exception;
    }

    /**
     * The number, starting from 1, of this attempt.
     *
     * @return the attempt number
     */
    public int getAttemptNumber() {
        return attemptNumber;
    }

    /**
     * The delay since the start of the first attempt, in milliseconds.
     *
     * @return the delay since the start of the first attempt, in milliseconds
     */
    public long getDelaySinceFirstAttempt() {
        return delaySinceFirstAttempt;
    }
}
