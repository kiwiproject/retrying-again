/*
 * Copyright 2017-2018 Robert Huffman
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

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

class AttemptTimeLimitersTest {

    @Test
    void testFixedTimeLimitWithNoExecutorReusesThreads() throws Exception {
        Set<Long> threadsUsed = Collections.synchronizedSet(new HashSet<>());
        Callable<Void> callable = () -> {
            threadsUsed.add(Thread.currentThread().getId());
            return null;
        };

        int iterations = 20;
        for (int i = 0; i < iterations; i++) {
            AttemptTimeLimiter timeLimiter =
                    AttemptTimeLimiters.fixedTimeLimit(1, TimeUnit.SECONDS);
            timeLimiter.call(callable);
        }

        assertThat(threadsUsed.size())
                .describedAs("Should have used less than %d threads", iterations)
                .isLessThan(iterations);
    }

}
