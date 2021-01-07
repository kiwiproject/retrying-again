/*
 * Copyright 2012-2015 Ray Holder
 * Modifications copyright 2017-2018 Robert Huffman
 * Modifications copyright 2020-2021 Kiwi Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kiwiproject.retry;

import com.google.common.base.Preconditions;

import javax.annotation.Nonnull;
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
     *                           eg, Thread#sleep(), latches, etc.
     * @param retryPredicates    the predicates used to decide if the attempt must be retried (without
     *                           regard to the StopStrategy).
     * @param listeners          collection of retry listeners
     */
    Retryer(@Nonnull AttemptTimeLimiter attemptTimeLimiter,
            @Nonnull StopStrategy stopStrategy,
            @Nonnull WaitStrategy waitStrategy,
            @Nonnull BlockStrategy blockStrategy,
            @Nonnull List<Predicate<Attempt<?>>> retryPredicates,
            @Nonnull Collection<RetryListener> listeners) {
        Preconditions.checkNotNull(attemptTimeLimiter, "timeLimiter may not be null");
        Preconditions.checkNotNull(stopStrategy, "stopStrategy may not be null");
        Preconditions.checkNotNull(waitStrategy, "waitStrategy may not be null");
        Preconditions.checkNotNull(blockStrategy, "blockStrategy may not be null");
        Preconditions.checkNotNull(retryPredicates, "retryPredicates may not be null");
        Preconditions.checkNotNull(listeners, "listeners may not null");

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
     * must be made. Then the wait strategy is used to decide how much time to sleep
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
        for (int attemptNumber = 1; ; attemptNumber++) {
            Attempt<T> attempt;
            try {
                T result = attemptTimeLimiter.call(callable);
                attempt = new Attempt<>(result, attemptNumber, computeMillisSince(startTimeNanos));
            } catch (InterruptedException e) {
                throw e;
            } catch (Throwable t) {
                attempt = new Attempt<>(t, attemptNumber, computeMillisSince(startTimeNanos));
            }

            for (RetryListener listener : listeners) {
                listener.onRetry(attempt);
            }

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

    private static long computeMillisSince(long startTimeNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTimeNanos);
    }

    /**
     * Executes the given runnable, retrying if necessary. If the retry predicate
     * accepts the attempt, the stop strategy is used to decide if a new attempt
     * must be made. Then the wait strategy is used to decide how much time to sleep
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
        return attempt.get();
    }

    /**
     * Applies the retry predicates to the attempt, in order, until either one
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
