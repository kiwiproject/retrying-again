package org.kiwiproject.retry;

import com.google.errorprone.annotations.Immutable;

/**
 * Factory class for {@link BlockStrategy} instances.
 */
@SuppressWarnings("WeakerAccess")
public final class BlockStrategies {

    private static final BlockStrategy THREAD_SLEEP_STRATEGY = new ThreadSleepStrategy();

    private BlockStrategies() {
    }

    /**
     * Returns a block strategy that puts the current thread to sleep between
     * retries.
     *
     * @return a block strategy that puts the current thread to sleep between retries
     */
    public static BlockStrategy threadSleepStrategy() {
        return THREAD_SLEEP_STRATEGY;
    }

    @Immutable
    private static class ThreadSleepStrategy implements BlockStrategy {

        @Override
        public void block(long sleepTime) throws InterruptedException {
            Thread.sleep(sleepTime);
        }
    }
}
