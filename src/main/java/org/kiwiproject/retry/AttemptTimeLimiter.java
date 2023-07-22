package org.kiwiproject.retry;

import java.util.concurrent.Callable;

/**
 * A rule to wrap any single attempt in a time limit, where it will possibly be interrupted if the limit is exceeded.
 */
public interface AttemptTimeLimiter {
    /**
     * @param callable to subject to the time limit
     * @param <T>      The return type of the Callable's call method
     * @return the return of the given callable
     * @throws Exception any exception from this invocation
     */
    <T> T call(Callable<T> callable) throws Exception;
}
