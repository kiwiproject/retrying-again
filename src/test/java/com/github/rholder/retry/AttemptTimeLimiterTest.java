/*
 * Copyright 2012-2015 Ray Holder
 * Modifications copyright 2017-2018 Robert Huffman
 * Modifications copyright 2020-2021 Kiwi Project
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

package com.github.rholder.retry;

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
