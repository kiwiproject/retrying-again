package org.kiwiproject.retry;

/**
 * This listener provides callbacks for several events that occur when running
 * code through a {@link Retryer} instance.
 */
public interface RetryListener {

    /**
     * This method with fire no matter what the result is and before the
     * retry predicate and stop strategies are applied.
     *
     * @param attempt the current {@link Attempt}
     * @apiNote No exceptions should be thrown from this method. But if an exception is thrown by an
     * implementation, it will be silently ignored so that it does not halt processing of a {@link Retryer}.
     */
    void onRetry(Attempt<?> attempt);
}
