package org.kiwiproject.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

@DisplayName("Attempt")
class AttemptTest {

    private ThreadLocalRandom rand;
    int attemptNumber;
    long delaySinceFirstAttempt;

    @BeforeEach
    void setUp() {
        rand = ThreadLocalRandom.current();
        attemptNumber = rand.nextInt(1, 5);
        delaySinceFirstAttempt = rand.nextLong(1_000, 5_000);
    }

    @Test
    void shouldCreateWithResult() {
        var result = rand.nextInt(0, 101);
        var attempt = Attempt.newResultAttempt(result, attemptNumber, delaySinceFirstAttempt);

        assertAll(
            () -> assertThat(attempt.hasResult()).isTrue(),
            () -> assertThat(attempt.hasException()).isFalse(),
            () -> assertThat(attempt.getResult()).isEqualTo(result),
            () -> assertThatIllegalStateException().isThrownBy(() -> attempt.getException()),
            () -> assertThat(attempt.getAttemptNumber()).isEqualTo(attemptNumber),
            () -> assertThat(attempt.getDelaySinceFirstAttempt()).isEqualTo(delaySinceFirstAttempt)
        );
    }

    @Test
    void shouldAllowNullResult() {
        var attempt = Attempt.<Integer>newResultAttempt(null, attemptNumber, delaySinceFirstAttempt);

        assertAll(
            () -> assertThat(attempt.hasResult()).isTrue(),
            () -> assertThat(attempt.hasException()).isFalse(),
            () -> assertThat(attempt.getResult()).isNull(),
            () -> assertThatIllegalStateException().isThrownBy(() -> attempt.getException()),
            () -> assertThat(attempt.getAttemptNumber()).isEqualTo(attemptNumber),
            () -> assertThat(attempt.getDelaySinceFirstAttempt()).isEqualTo(delaySinceFirstAttempt)
        );
    }

    @Test
    void shouldCreateWithException() {
        var exception = new IOException("I/O error - device not ready");
        var attempt = Attempt.newExceptionAttempt(exception, attemptNumber, delaySinceFirstAttempt);

        assertAll(
            () -> assertThat(attempt.hasResult()).isFalse(),
            () -> assertThat(attempt.hasException()).isTrue(),
            () -> assertThatIllegalStateException().isThrownBy(() -> attempt.getResult()),
            () -> assertThat(attempt.getException()).isSameAs(exception),
            () -> assertThat(attempt.getAttemptNumber()).isEqualTo(attemptNumber),
            () -> assertThat(attempt.getDelaySinceFirstAttempt()).isEqualTo(delaySinceFirstAttempt)
        );
    }

    @Test
    void shouldNotPermitExceptionAttempt_WithNullException() {
        assertThatNullPointerException()
                .isThrownBy(() -> Attempt.newExceptionAttempt(null, attemptNumber, delaySinceFirstAttempt))
                .withMessage("exception must not be null");
    }
}
