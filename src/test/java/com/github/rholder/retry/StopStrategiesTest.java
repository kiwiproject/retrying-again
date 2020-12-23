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

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

class StopStrategiesTest {

    @Test
    void testNeverStop() {
        Assertions.assertFalse(StopStrategies.neverStop().shouldStop(failedAttempt(3, 6546L)));
    }

    @Test
    void testStopAfterAttempt() {
        Assertions.assertFalse(StopStrategies.stopAfterAttempt(3).shouldStop(failedAttempt(2, 6546L)));
        Assertions.assertTrue(StopStrategies.stopAfterAttempt(3).shouldStop(failedAttempt(3, 6546L)));
        Assertions.assertTrue(StopStrategies.stopAfterAttempt(3).shouldStop(failedAttempt(4, 6546L)));
    }

    @Test
    void testStopAfterDelayWithMilliseconds() {
        Assertions.assertFalse(StopStrategies.stopAfterDelay(1000, MILLISECONDS)
                .shouldStop(failedAttempt(2, 999L)));
        Assertions.assertTrue(StopStrategies.stopAfterDelay(1000, MILLISECONDS)
                .shouldStop(failedAttempt(2, 1000L)));
        Assertions.assertTrue(StopStrategies.stopAfterDelay(1000, MILLISECONDS)
                .shouldStop(failedAttempt(2, 1001L)));
    }

    @Test
    void testStopAfterDelayWithTimeUnit() {
        Assertions.assertFalse(StopStrategies.stopAfterDelay(1, TimeUnit.SECONDS).shouldStop(failedAttempt(2, 999L)));
        Assertions.assertTrue(StopStrategies.stopAfterDelay(1, TimeUnit.SECONDS).shouldStop(failedAttempt(2, 1000L)));
        Assertions.assertTrue(StopStrategies.stopAfterDelay(1, TimeUnit.SECONDS).shouldStop(failedAttempt(2, 1001L)));
    }

    private Attempt<Boolean> failedAttempt(int attemptNumber, long delaySinceFirstAttempt) {
        return new Attempt<>(new RuntimeException(), attemptNumber, delaySinceFirstAttempt);
    }
}
