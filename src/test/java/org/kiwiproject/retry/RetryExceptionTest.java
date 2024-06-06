package org.kiwiproject.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

@DisplayName("RetryException")
class RetryExceptionTest {

    @Test
    void shouldReturnLastAttempt_ContainingResult() {
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
    void shouldReturnLastAttempt_ContainingResult_WithGenericType() {
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
    void shouldThrowIllegalStateException_WhenGivenIncorrectResultType() {
        var attempt = Attempt.newResultAttempt(42, 3, 625);
        var e = new RetryException(attempt);

        assertThatIllegalStateException()
                .isThrownBy(() -> e.getLastFailedAttempt(String.class))
                .withMessage("Attempt.result is not an instance of java.lang.String");
    }

    @Test
    void shouldReturnLastAttempt_ContainingException() {
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

    @Test
    void shouldReturnLastFailedAttempt_ContainingException_AndIgnoreSpecificResultTypes() {
        var cause = new UncheckedIOException(new IOException("Disk full"));
        var attempt = Attempt.newExceptionAttempt(cause, 2, 150);
        var e = new RetryException(attempt);

        // Define a custom result type
        record User(int id, String username, String password, List<String> roleNames) {}

        // These calls should all succeed, since the last Attempt does not have a result
        Attempt<String> lastFailedAttempt1 = e.getLastFailedAttempt(String.class);
        Attempt<Integer> lastFailedAttempt2 = e.getLastFailedAttempt(Integer.class);
        Attempt<Double> lastFailedAttempt3 = e.getLastFailedAttempt(Double.class);
        Attempt<User> lastFailedAttempt4 = e.getLastFailedAttempt(User.class);

        assertThat(lastFailedAttempt1)
                .describedAs("The exact same Attempt instance should be returned")
                .isSameAs(lastFailedAttempt2)
                .isSameAs(lastFailedAttempt3)
                .isSameAs(lastFailedAttempt4);
    }

    @ParameterizedTest
    @ValueSource(classes = {
        String.class, Integer.class, Long.class, Double.class
    })
    void shouldReturnLastFailedAttempt_ContainingException_AndIgnoreResultType(Class<?> resultType) {
        var cause = new UncheckedIOException(new IOException("File not found"));
        var attempt = Attempt.newExceptionAttempt(cause, 3, 500);
        var e = new RetryException(attempt);

        Attempt<?> lastFailedAttempt = e.getLastFailedAttempt(resultType);
        assertAll(
            () -> assertThat(lastFailedAttempt.hasResult()).isFalse(),
            () -> assertThat(lastFailedAttempt.hasException()).isTrue(),
            () -> assertThat(lastFailedAttempt.getException()).isSameAs(cause),
            () -> assertThat(lastFailedAttempt.getAttemptNumber()).isEqualTo(3),
            () -> assertThat(lastFailedAttempt.getDelaySinceFirstAttempt()).isEqualTo(500)
        );
    }
}
