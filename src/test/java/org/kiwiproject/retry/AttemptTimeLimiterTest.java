package org.kiwiproject.retry;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

class AttemptTimeLimiterTest {

    private final Retryer r = RetryerBuilder.newBuilder()
            .withAttemptTimeLimiter(AttemptTimeLimiters.fixedTimeLimit(1, TimeUnit.SECONDS))
            .build();

    @Test
    void testAttemptTimeLimitWhenShouldNotTimeOut() {
        assertThatCode(() -> r.call(new SleepyOut(0L)))
                .describedAs("Should not timeout")
                .doesNotThrowAnyException();
    }

    @Test
    void testAttemptTimeLimitWhenShouldTimeOut() {
        assertThatThrownBy(() -> r.call(new SleepyOut(10 * 1000L)))
                .describedAs("Expected timeout exception")
                .isExactlyInstanceOf(RetryException.class);
    }

    static class SleepyOut implements Callable<Void> {

        final long sleepMs;

        SleepyOut(long sleepMs) {
            this.sleepMs = sleepMs;
        }

        @Override
        public Void call() throws Exception {
            Thread.sleep(sleepMs);
            return null;
        }
    }
}
