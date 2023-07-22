package org.kiwiproject.retry;

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.nonNull;

import com.google.errorprone.annotations.Immutable;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * An exception indicating that none of the attempts of the {@link Retryer}
 * succeeded. If the last {@link Attempt} resulted in an Exception, it is set as
 * the cause of the {@link RetryException}.
 */
@Immutable
public final class RetryException extends Exception {

    private final transient Attempt<?> lastFailedAttempt;

    /**
     * If the last {@link Attempt} had an Exception, ensure it is available in
     * the stack trace.
     *
     * @param attempt what happened the last time we failed
     */
    RetryException(@NonNull Attempt<?> attempt) {
        this(errorMessageFor(attempt), attempt);
    }

    private static String errorMessageFor(Attempt<?> attempt) {
        return "Retrying failed to complete successfully after " + attempt.getAttemptNumber() + " attempts.";
    }

    /**
     * If the last {@link Attempt} had an Exception, ensure it is available in
     * the stack trace.
     *
     * @param message Exception description to be added to the stack trace
     * @param attempt what happened the last time we failed
     */
    private RetryException(String message, Attempt<?> attempt) {
        super(message, attempt.hasException() ? attempt.getException() : null);
        this.lastFailedAttempt = attempt;
    }

    /**
     * Returns the number of failed attempts
     *
     * @return the number of failed attempts
     */
    public int getNumberOfFailedAttempts() {
        checkState(nonNull(lastFailedAttempt), "lastFailedAttempt is null; cannot get attempt number");
        return lastFailedAttempt.getAttemptNumber();
    }

    /**
     * Returns the last failed attempt
     *
     * @return the last failed attempt
     */
    public Attempt<?> getLastFailedAttempt() {
        return lastFailedAttempt;
    }
}
