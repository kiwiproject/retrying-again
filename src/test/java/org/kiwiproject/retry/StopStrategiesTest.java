package org.kiwiproject.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.concurrent.TimeUnit;

class StopStrategiesTest {

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 42, 65_535, Integer.MAX_VALUE})
    void testNeverStop(int attemptNumber) {
        var stopStrategy = StopStrategies.neverStop();
        assertThat(stopStrategy.shouldStop(failedAttempt(attemptNumber, 6546L)))
                .isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "2, false",
            "3, true",
            "4, true"
    })
    void testStopAfterAttempt(int attemptNumber, boolean expectedShouldStop) {
        var stopStrategy = StopStrategies.stopAfterAttempt(3);
        assertThat(stopStrategy.shouldStop(failedAttempt(attemptNumber, 6546L)))
                .isEqualTo(expectedShouldStop);
    }

    @Test
    void testStopAfterAttempt_ShouldNotAllowNegativeAttemptNumber() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> StopStrategies.stopAfterAttempt(-5))
                .withMessage("maxAttemptNumber must be >= 1 but is -5");
    }

    @ParameterizedTest
    @CsvSource({
            "999, false",
            "1000, true",
            "1001, true"
    })
    void testStopAfterDelayWithTimeUnit(long delaySinceFirstAttempt, boolean expectedShouldStop) {
        var stopStrategy = StopStrategies.stopAfterDelay(1, TimeUnit.SECONDS);
        assertThat(stopStrategy.shouldStop(failedAttempt(2, delaySinceFirstAttempt)))
                .isEqualTo(expectedShouldStop);
    }

    @Test
    void testStopAfterDelay_ShouldNotAllowNegativeDuration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> StopStrategies.stopAfterDelay(-750, TimeUnit.MILLISECONDS))
                .withMessage("maxDelay must be >= 0 but is -750");
    }

    private Attempt<Boolean> failedAttempt(int attemptNumber, long delaySinceFirstAttempt) {
        return Attempt.newExceptionAttempt(new RuntimeException(), attemptNumber, delaySinceFirstAttempt);
    }
}
