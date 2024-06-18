package org.kiwiproject.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import org.assertj.core.api.ThrowableAssert;

@SuppressWarnings({"unused", "WeakerAccess"})
class RetryExceptionAssert {

    private final RetryException retryException;

    RetryExceptionAssert(RetryException retryException) {
        this.retryException = retryException;
    }

    static RetryExceptionAssert assertThatRetryExceptionThrownBy(ThrowableAssert.ThrowingCallable throwingCallable) {
        var retryException = catchThrowableOfType(RetryException.class, throwingCallable);
        return assertThatRetryException(retryException);
    }

    static RetryExceptionAssert assertThatRetryException(RetryException retryException) {
        return new RetryExceptionAssert(retryException);
    }

    RetryExceptionAssert hasNoCause() {
        assertThat(retryException).hasNoCause();
        return this;
    }

    RetryExceptionAssert hasCauseExactlyInstanceOf(Class<? extends Exception> type) {
        assertThat(retryException).hasCauseExactlyInstanceOf(type);
        return this;
    }

    RetryExceptionAssert hasNumberOfFailedAttempts(int expected) {
        assertThat(retryException.getNumberOfFailedAttempts()).isEqualTo(expected);
        return this;
    }

    RetryExceptionAssert hasResultOnLastAttempt() {
        assertThat(retryException.getLastFailedAttempt().hasResult()).isTrue();
        assertThat(retryException.getLastFailedAttempt().hasException()).isFalse();
        return this;
    }

    @SuppressWarnings({"SameParameterValue", "UnusedReturnValue"})
    <T> RetryExceptionAssert hasResultOnLastAttempt(T expected) {
        assertThat(retryException.getLastFailedAttempt().hasResult()).isTrue();
        assertThat(retryException.getLastFailedAttempt().getResult()).isEqualTo(expected);
        assertThat(retryException.getLastFailedAttempt().hasException()).isFalse();
        return this;
    }

    @SuppressWarnings("UnusedReturnValue")
    RetryExceptionAssert hasExceptionOnLastAttempt() {
        assertThat(retryException.getLastFailedAttempt().hasResult()).isFalse();
        assertThat(retryException.getLastFailedAttempt().hasException()).isTrue();
        return this;
    }

    RetryException getRetryException() {
        return retryException;
    }

}
