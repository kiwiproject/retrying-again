/*
 * Copyright 2020-2021 Kiwi Project
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.kiwiproject.test.assertj.KiwiAssertJ.assertIsExactType;

import org.assertj.core.api.ThrowableAssert;

@SuppressWarnings({"unused", "WeakerAccess"})
class RetryExceptionAssert {

    private final RetryException retryException;

    RetryExceptionAssert(RetryException retryException) {
        this.retryException = retryException;
    }

    static RetryExceptionAssert assertThatRetryExceptionThrownBy(ThrowableAssert.ThrowingCallable throwingCallable) {
        var thrown = catchThrowable(throwingCallable);
        var retryException = assertIsExactType(thrown, RetryException.class);
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
