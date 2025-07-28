package org.kiwiproject.retry;

import com.google.common.base.Preconditions;
import com.google.errorprone.annotations.Immutable;
import org.jspecify.annotations.NonNull;

import java.util.concurrent.TimeUnit;

/**
 * Factory class for {@link StopStrategy} instances.
 */
public final class StopStrategies {
    private static final StopStrategy NEVER_STOP = new NeverStopStrategy();

    private StopStrategies() {
    }

    /**
     * Returns a stop strategy which never stops retrying. It might be best to
     * try not to abuse services with this kind of behavior when small wait
     * intervals between retry attempts are being used.
     *
     * @return a stop strategy which never stops
     */
    public static StopStrategy neverStop() {
        return NEVER_STOP;
    }

    /**
     * Returns a stop strategy which stops after N failed attempts.
     *
     * @param attemptNumber the number of failed attempts before stopping
     * @return a stop strategy which stops after {@code attemptNumber} attempts
     */
    public static StopStrategy stopAfterAttempt(int attemptNumber) {
        return new StopAfterAttemptStrategy(attemptNumber);
    }

    /**
     * Returns a stop strategy which stops after a given delay. If an
     * unsuccessful attempt is made, this {@link StopStrategy} will check if the
     * amount of time that's passed from the first attempt has exceeded the
     * given delay amount. If it has exceeded this delay, then using this
     * strategy causes the retrying to stop.
     *
     * @param duration the delay, starting from the first attempt
     * @param timeUnit the unit of the duration
     * @return a stop strategy which stops after {@code duration} time in the given {@code timeUnit}
     */
    public static StopStrategy stopAfterDelay(long duration, @NonNull TimeUnit timeUnit) {
        Preconditions.checkNotNull(timeUnit, "The time unit may not be null");
        return new StopAfterDelayStrategy(timeUnit.toMillis(duration));
    }

    @Immutable
    private static final class NeverStopStrategy implements StopStrategy {
        @Override
        public boolean shouldStop(Attempt<?> failedAttempt) {
            return false;
        }
    }

    @Immutable
    private static final class StopAfterAttemptStrategy implements StopStrategy {
        private final int maxAttemptNumber;

        StopAfterAttemptStrategy(int maxAttemptNumber) {
            Preconditions.checkArgument(maxAttemptNumber >= 1, "maxAttemptNumber must be >= 1 but is %s", maxAttemptNumber);
            this.maxAttemptNumber = maxAttemptNumber;
        }

        @Override
        public boolean shouldStop(Attempt<?> failedAttempt) {
            return failedAttempt.getAttemptNumber() >= maxAttemptNumber;
        }
    }

    @Immutable
    private static final class StopAfterDelayStrategy implements StopStrategy {
        private final long maxDelay;

        StopAfterDelayStrategy(long maxDelay) {
            Preconditions.checkArgument(maxDelay >= 0L, "maxDelay must be >= 0 but is %s", maxDelay);
            this.maxDelay = maxDelay;
        }

        @Override
        public boolean shouldStop(Attempt<?> failedAttempt) {
            return failedAttempt.getDelaySinceFirstAttempt() >= maxDelay;
        }
    }
}
