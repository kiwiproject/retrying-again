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
import static org.assertj.core.api.Assertions.fail;
import static org.kiwiproject.test.assertj.KiwiAssertJ.assertIsExactType;

import java.util.concurrent.Callable;

class RetryerAssert {

    private final Retryer retryer;
    private Object result;

    private RetryerAssert(Retryer retryer) {
        this.retryer = retryer;
    }

    static RetryerAssert assertThatRetryer(Retryer retryer) {
        return new RetryerAssert(retryer);
    }

    <T> RetryExceptionAssert throwsRetryExceptionCalling(Callable<T> callable) {
        var thrown = catchThrowable(() -> retryer.call(callable));
        var retryException = assertIsExactType(thrown, RetryException.class);
        return new RetryExceptionAssert(retryException);
    }

    <T> RetryerAssert completesSuccessfullyCalling(Callable<T> callable) {
        try {
            result = retryer.call(callable);
        } catch (RetryException e) {
            fail("Received unexpected RetryException");
        } catch (InterruptedException e) {
            fail("Received unexpected InterruptedException");
        }
        return this;
    }

    @SuppressWarnings({"UnusedReturnValue", "SameParameterValue"})
    <T> RetryerAssert hasResult(T expected) {
        assertThat(result).isEqualTo(expected);
        return this;
    }
}
