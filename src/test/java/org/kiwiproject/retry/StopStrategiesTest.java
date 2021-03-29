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

package org.kiwiproject.retry;

import static org.assertj.core.api.Assertions.assertThat;

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

    private Attempt<Boolean> failedAttempt(int attemptNumber, long delaySinceFirstAttempt) {
        return Attempt.newExceptionAttempt(new RuntimeException(), attemptNumber, delaySinceFirstAttempt);
    }
}
