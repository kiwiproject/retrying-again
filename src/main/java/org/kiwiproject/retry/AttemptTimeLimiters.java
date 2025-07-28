package org.kiwiproject.retry;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.SimpleTimeLimiter;
import com.google.common.util.concurrent.TimeLimiter;
import com.google.errorprone.annotations.Immutable;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Factory class for instances of {@link AttemptTimeLimiter}
 */
@SuppressWarnings("WeakerAccess")
public class AttemptTimeLimiters {

    private AttemptTimeLimiters() {
    }

    /**
     * @return an {@link AttemptTimeLimiter} impl which has no time limit
     */
    public static AttemptTimeLimiter noTimeLimit() {
        return new NoAttemptTimeLimit();
    }

    /**
     * For control over thread management, it is preferable to offer an {@link ExecutorService}
     * through the other factory method, {@link #fixedTimeLimit(long, TimeUnit, ExecutorService)}.
     * All calls to this method use the same cached thread pool created by
     * {@link Executors#newCachedThreadPool()}. It is unbounded, meaning there is no limit to
     * the number of threads it will create. It will reuse idle threads if they are available,
     * and idle threads remain alive for 60 seconds.
     *
     * @param duration that an attempt may persist before being circumvented
     * @param timeUnit of the 'duration' arg
     * @return an {@link AttemptTimeLimiter} with a fixed time limit for each attempt
     */
    public static AttemptTimeLimiter fixedTimeLimit(long duration, @NonNull TimeUnit timeUnit) {
        Preconditions.checkNotNull(timeUnit);
        return new FixedAttemptTimeLimit(duration, timeUnit);
    }

    /**
     * @param duration        that an attempt may persist before being circumvented
     * @param timeUnit        of the 'duration' arg
     * @param executorService used to enforce time limit
     * @return an {@link AttemptTimeLimiter} with a fixed time limit for each attempt
     */
    public static AttemptTimeLimiter fixedTimeLimit(
            long duration, @NonNull TimeUnit timeUnit, @NonNull ExecutorService executorService) {
        Preconditions.checkNotNull(timeUnit);
        return new FixedAttemptTimeLimit(duration, timeUnit, executorService);
    }

    @Immutable
    private static final class NoAttemptTimeLimit implements AttemptTimeLimiter {
        @Override
        public <T> T call(Callable<T> callable) throws Exception {
            return callable.call();
        }
    }

    // Suppress API warnings about TimeLimiter in Guava which has been there since version 1.0
    @Immutable
    private static final class FixedAttemptTimeLimit implements AttemptTimeLimiter {

        /**
         * ExecutorService used when no ExecutorService is specified in the constructor
         */
        private static final ExecutorService defaultExecutorService = Executors.newCachedThreadPool();

        private final TimeLimiter timeLimiter;
        private final long duration;
        private final TimeUnit timeUnit;

        FixedAttemptTimeLimit(long duration, @NonNull TimeUnit timeUnit) {
            this(duration, timeUnit, defaultExecutorService);
        }

        FixedAttemptTimeLimit(long duration, @NonNull TimeUnit timeUnit, @NonNull ExecutorService executorService) {
            this(SimpleTimeLimiter.create(executorService), duration, timeUnit);
        }

        private FixedAttemptTimeLimit(@NonNull TimeLimiter timeLimiter, long duration, @NonNull TimeUnit timeUnit) {
            Preconditions.checkNotNull(timeLimiter);
            Preconditions.checkNotNull(timeUnit);
            this.timeLimiter = timeLimiter;
            this.duration = duration;
            this.timeUnit = timeUnit;
        }

        @Override
        public <T> T call(Callable<T> callable) throws Exception {
            return timeLimiter.callWithTimeout(callable, duration, timeUnit);
        }
    }
}
