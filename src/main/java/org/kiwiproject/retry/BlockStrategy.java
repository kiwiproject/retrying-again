package org.kiwiproject.retry;

/**
 * This is a strategy used to decide how a retryer should block between retry
 * attempts. Normally this is just a Thread.sleep(), but implementations can be
 * something more elaborate if desired.
 */
public interface BlockStrategy {

    /**
     * Attempt to block for the designated amount of time. Implementations
     * that don't block or otherwise delay the processing from within this
     * method for the given sleep duration can significantly modify the behavior
     * of any configured {@link org.kiwiproject.retry.WaitStrategy}. Caution
     * is advised when generating your own implementations.
     *
     * @param sleepTime the computed sleep duration in milliseconds
     * @throws InterruptedException If the calling thread is interrupted
     */
    void block(long sleepTime) throws InterruptedException;
}
