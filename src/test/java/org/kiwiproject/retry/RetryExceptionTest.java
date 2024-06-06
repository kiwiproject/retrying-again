package org.kiwiproject.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;

@DisplayName("RetryException")
class RetryExceptionTest {

    @Test
    void shouldReturnLastAttempt() {
        var attempt = Attempt.newResultAttempt(42, 5, 2_000);
        var e = new RetryException(attempt);

        Attempt<?> lastFailedAttempt = e.getLastFailedAttempt();
        assertAll(
            () -> assertThat(lastFailedAttempt.hasResult()).isTrue(),
            () -> assertThat(lastFailedAttempt.hasException()).isFalse(),
            () -> assertThat(lastFailedAttempt.getAttemptNumber()).isEqualTo(5),
            () -> assertThat(lastFailedAttempt.getDelaySinceFirstAttempt()).isEqualTo(2_000)
        );

        Object result = lastFailedAttempt.getResult();
        assertThat(result).isEqualTo(42);
    }

    @Test
    void shouldReturnLastAttempt_WithGenericType() {
        var attempt = Attempt.newResultAttempt(42, 3, 625);
        var e = new RetryException(attempt);

        Attempt<Integer> lastFailedAttempt = e.getLastFailedAttempt(Integer.class);
        assertAll(
            () -> assertThat(lastFailedAttempt.hasResult()).isTrue(),
            () -> assertThat(lastFailedAttempt.hasException()).isFalse(),
            () -> assertThat(lastFailedAttempt.getAttemptNumber()).isEqualTo(3),
            () -> assertThat(lastFailedAttempt.getDelaySinceFirstAttempt()).isEqualTo(625)
        );

        Integer result = lastFailedAttempt.getResult();
        assertThat(result).isEqualTo(42);
    }

    @Test
    void shouldThrowClassCastException_WhenGivenIncorrectType() {
        var attempt = Attempt.newResultAttempt(42, 3, 625);
        var e = new RetryException(attempt);

        Attempt<String> lastFailedAttempt = e.getLastFailedAttempt(String.class);

        assertThatExceptionOfType(ClassCastException.class)
                .isThrownBy(() -> {
                    // The variable must be declared with its expected type
                    // to cause the exception
                    @SuppressWarnings("unused")
                    String result = lastFailedAttempt.getResult();
                });
    }

    @Test
    void shouldReturnLastAttempt_WithException() {
        var cause = new UncheckedIOException(new IOException("I/O error"));
        var attempt = Attempt.newExceptionAttempt(cause, 2, 250);
        var e = new RetryException(attempt);

        Attempt<?> lastFailedAttempt = e.getLastFailedAttempt();
        assertAll(
            () -> assertThat(lastFailedAttempt.hasResult()).isFalse(),
            () -> assertThat(lastFailedAttempt.hasException()).isTrue(),
            () -> assertThat(lastFailedAttempt.getException()).isSameAs(cause),
            () -> assertThat(lastFailedAttempt.getAttemptNumber()).isEqualTo(2),
            () -> assertThat(lastFailedAttempt.getDelaySinceFirstAttempt()).isEqualTo(250)
        );
    }
}
