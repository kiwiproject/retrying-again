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
     * @apiNote This method returns {@code Attempt<?>} because the Java Language Specification does not
     * permit generic subclasses of Throwable. In section
     * <a href="https://docs.oracle.com/javase/specs/jls/se17/html/jls-8.html#jls-8.1.2">8.1.2, Generic Classes and Type Parameters</a>,
     * the (Java SE 17) specification states that <em>"It is a compile-time error if a generic class is a direct or
     * indirect subclassof Throwable"</em>. It further provides the reason, stating <em>"This restriction is needed
     * since the catch mechanism of the Java Virtual Machine works only with non-generic classes."</em> As a result,
     * this exception class has no (good) way to capture the {@code Attempt} type parameter. Callers of this
     * method must know the expected type and cast the returned value.
     */
    public Attempt<?> getLastFailedAttempt() {
        return lastFailedAttempt;
    }
}
