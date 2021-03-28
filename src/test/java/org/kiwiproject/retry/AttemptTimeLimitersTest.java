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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

class AttemptTimeLimitersTest {

    @Test
    void testFixedTimeLimitWithNoExecutorReusesThreads() throws Exception {
        Set<Long> threadsUsed = synchronizedSet();
        Callable<Void> callable = newTestCallable(threadsUsed);

        var iterations = 20;
        callMultipleTimesWithNewTimeLimiter(callable, 20, AttemptTimeLimitersTest::newFixedTimeLimiter);

        assertThat(threadsUsed)
                .describedAs("Should have used less than %d threads", iterations)
                .hasSizeLessThan(iterations);
    }

    @Test
    void testFixedTimeLimitWithProvidedExecutor() throws Exception {
        Set<Long> threadsUsed = synchronizedSet();
        Callable<Void> callable = newTestCallable(threadsUsed);

        var numThreads = 3;
        var executor = Executors.newFixedThreadPool(numThreads);

        var iterations = 25;
        callMultipleTimesWithNewTimeLimiter(callable, iterations, () -> newFixedTimeLimiter(executor));

        assertThat(threadsUsed)
                .describedAs("Expected to have used %d threads", numThreads)
                .hasSize(numThreads);
    }

    private static void callMultipleTimesWithNewTimeLimiter(Callable<Void> callable,
                                                            int numIterations,
                                                            Supplier<AttemptTimeLimiter> supplier) throws Exception {

        for (int i = 0; i < numIterations; i++) {
            var timeLimiter = supplier.get();
            timeLimiter.call(callable);
        }
    }

    private static AttemptTimeLimiter newFixedTimeLimiter() {
        return AttemptTimeLimiters.fixedTimeLimit(1, TimeUnit.SECONDS);
    }

    private static AttemptTimeLimiter newFixedTimeLimiter(ExecutorService executor) {
        return AttemptTimeLimiters.fixedTimeLimit(1, TimeUnit.SECONDS, executor);
    }

    private Set<Long> synchronizedSet() {
        return Collections.synchronizedSet(new HashSet<>());
    }

    private Callable<Void> newTestCallable(Set<Long> threadsUsed) {
        return () -> {
            var id = Thread.currentThread().getId();
            threadsUsed.add(id);
            return null;
        };
    }

}
