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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.retry.RetryerAssert.assertThatRetryer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kiwiproject.retry.Retryer.RetryerCallable;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

class RetryerBuilderTest {

    @Test
    void testWithWaitStrategy() throws Exception {
        Callable<Boolean> callable = notNullAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .withWaitStrategy(WaitStrategies.fixedWait(50L, TimeUnit.MILLISECONDS))
                .retryIfResult(Objects::isNull)
                .build();
        long start = System.currentTimeMillis();
        boolean result = retryer.call(callable);

        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(250L);
        assertThat(result).isTrue();
    }

    @Test
    void testWithMoreThanOneWaitStrategyOneBeingFixed() throws Exception {
        Callable<Boolean> callable = notNullAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .withWaitStrategy(WaitStrategies.join(
                        WaitStrategies.fixedWait(50L, TimeUnit.MILLISECONDS),
                        WaitStrategies.fibonacciWait(10, Long.MAX_VALUE, TimeUnit.MILLISECONDS)))
                .retryIfResult(Objects::isNull)
                .build();
        long start = System.currentTimeMillis();
        boolean result = retryer.call(callable);

        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(370L);
        assertThat(result).isTrue();
    }

    @Test
    void testWithMoreThanOneWaitStrategyOneBeingIncremental() throws Exception {
        Callable<Boolean> callable = notNullAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .withWaitStrategy(WaitStrategies.join(
                        WaitStrategies.incrementingWait(10L, TimeUnit.MILLISECONDS, 10L, TimeUnit.MILLISECONDS),
                        WaitStrategies.fibonacciWait(10, Long.MAX_VALUE, TimeUnit.MILLISECONDS)))
                .retryIfResult(Objects::isNull)
                .build();
        long start = System.currentTimeMillis();
        boolean result = retryer.call(callable);

        assertThat(System.currentTimeMillis() - start).isGreaterThanOrEqualTo(270L);
        assertThat(result).isTrue();
    }

    // TODO Consider parameterizing this
    private Callable<Boolean> notNullAfter5Attempts() {
        return new Callable<>() {
            int counter = 0;

            @Override
            public Boolean call() {
                if (counter < 5) {
                    counter++;
                    return null;
                }
                return true;
            }
        };
    }

    @Test
    void testWithStopStrategy() {
        Callable<Boolean> callable = notNullAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .retryIfResult(Objects::isNull)
                .build();

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(callable)
                .hasNoCause()
                .hasNumberOfFailedAttempts(3)
                .hasResultOnLastAttempt(null);
    }

    @Test
    @Disabled("Empty test needs to be implemented or deleted!")
    void testRetryIfNotOfExceptionType() {
        // TODO Found this blank test. Delete it? Or try to determine what the original
        //  author meant and implement it?
    }

    @Test
    void testWithBlockStrategy() {
        Callable<Boolean> callable = notNullAfter5Attempts();
        var counter = new AtomicInteger();
        BlockStrategy blockStrategy = sleepTime -> counter.incrementAndGet();

        var retryer = RetryerBuilder.newBuilder()
                .withBlockStrategy(blockStrategy)
                .retryIfResult(Objects::isNull)
                .build();
        final int retryCount = 5;
        assertThatRetryer(retryer)
                .completesSuccessfullyCalling(callable)
                .hasResult(true);

        assertThat(counter).hasValue(retryCount);
    }

    @Test
    void testRetryIfException_WhenCompletesSuccessfully() throws Exception {
        Callable<Boolean> callable = noIOExceptionAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfException()
                .build();
        boolean result = retryer.call(callable);
        assertThat(result).isTrue();
    }

    @Test
    void testRetryIfException_WhenFailsToComplete_DueToCheckedException() {
        Callable<Boolean> callable = noIOExceptionAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(callable)
                .hasCauseExactlyInstanceOf(IOException.class)
                .hasNumberOfFailedAttempts(3)
                .hasExceptionOnLastAttempt();
    }

    @Test
    void testRetryIfException_WhenFailsToComplete_DueToRuntimeException() {
        Callable<Boolean> callable = noIllegalStateExceptionAfter5Attempts();
        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(callable)
                .hasCauseExactlyInstanceOf(IllegalStateException.class)
                .hasNumberOfFailedAttempts(3)
                .hasExceptionOnLastAttempt();
    }

    // TODO Consider parameterizing this
    private Callable<Boolean> noIllegalStateExceptionAfter5Attempts() {
        return new Callable<>() {
            int counter = 0;

            @Override
            public Boolean call() {
                if (counter < 5) {
                    counter++;
                    throw new IllegalStateException();
                }
                return true;
            }
        };
    }

    // TODO Consider parameterizing this
    private Callable<Boolean> noIOExceptionAfter5Attempts() {
        return new Callable<>() {
            int counter = 0;

            @Override
            public Boolean call() throws IOException {
                if (counter < 5) {
                    counter++;
                    throw new IOException();
                }
                return true;
            }
        };
    }

    @Test
    void testRetryIfRuntimeException_WhenCheckedExceptionThrown() {
        Callable<Boolean> callable = noIOExceptionAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfRuntimeException()
                .build();

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(callable)
                .hasCauseExactlyInstanceOf(IOException.class)
                .hasNumberOfFailedAttempts(1)
                .hasExceptionOnLastAttempt();
    }

    @Test
    void testRetryIfRuntimeException_WhenRuntimeExceptionThrown() {
        Callable<Boolean> callable = noIllegalStateExceptionAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfRuntimeException()
                .build();

        assertThatRetryer(retryer)
                .completesSuccessfullyCalling(callable)
                .hasResult(true);
    }

    @Test
    void testRetryIfRuntimeException_WhenRuntimeExceptionThrown_ButStopsAfterAttempt() {
        Callable<Boolean> callable = noIllegalStateExceptionAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfRuntimeException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(callable)
                .hasCauseExactlyInstanceOf(IllegalStateException.class)
                .hasNumberOfFailedAttempts(3)
                .hasExceptionOnLastAttempt();
    }

    @Test
    void testRetryIfExceptionOfType_WhenSucceedsAfterMultipleAttempts() {
        Callable<Boolean> callable = noIOExceptionAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(IOException.class)
                .build();

        assertThatRetryer(retryer)
                .completesSuccessfullyCalling(callable)
                .hasResult(true);
    }

    @Test
    void testRetryIfExceptionOfType_WhenFailsToComplete() {
        Callable<Boolean> callable = noIllegalStateExceptionAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(IOException.class)
                .build();

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(callable)
                .hasCauseExactlyInstanceOf(IllegalStateException.class)
                .hasNumberOfFailedAttempts(1)
                .hasExceptionOnLastAttempt();
    }

    @Test
    void testRetryIfExceptionOfType_WhenHasStopStrategy() {
        Callable<Boolean> callable = noIOExceptionAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(IOException.class)
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(callable)
                .hasCauseExactlyInstanceOf(IOException.class)
                .hasNumberOfFailedAttempts(3)
                .hasExceptionOnLastAttempt();
    }

    @Test
    void testRetryIfExceptionWithPredicate_WhenSucceedsAfterMultipleAttempts() {
        Callable<Boolean> callable = noIOExceptionAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfException(t -> t instanceof IOException)
                .build();

        assertThatRetryer(retryer)
                .completesSuccessfullyCalling(callable)
                .hasResult(true);
    }

    @Test
    void testRetryIfExceptionWithPredicate_WhenFailsToComplete() {
        Callable<Boolean> callable = noIllegalStateExceptionAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfException(t -> t instanceof IOException)
                .build();

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(callable)
                .hasCauseExactlyInstanceOf(IllegalStateException.class)
                .hasNumberOfFailedAttempts(1)
                .hasExceptionOnLastAttempt();
    }

    @Test
    void testRetryIfExceptionWithPredicate_WhenHasStopStrategy() {
        Callable<Boolean> callable = noIOExceptionAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfException(t -> t instanceof IOException)
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(callable)
                .hasCauseExactlyInstanceOf(IOException.class)
                .hasNumberOfFailedAttempts(3)
                .hasExceptionOnLastAttempt();
    }

    @Test
    void testRetryIfResult_WhenCompletesSuccessfully() {
        Callable<Boolean> callable = notNullAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfResult(Objects::isNull)
                .build();

        assertThatRetryer(retryer)
                .completesSuccessfullyCalling(callable)
                .hasResult(true);
    }

    @Test
    void testRetryIfResult_WhenHasStopStrategy() {
        Callable<Boolean> callable = notNullAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfResult(Objects::isNull)
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(callable)
                .hasNoCause()
                .hasNumberOfFailedAttempts(3)
                .hasResultOnLastAttempt(null);
    }

    @Test
    void testMultipleRetryConditions_WhenHasStopStrategy() {
        Callable<Boolean> callable = notNullResultOrIOExceptionOrRuntimeExceptionAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfResult(Objects::isNull)
                .retryIfExceptionOfType(IOException.class)
                .retryIfRuntimeException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(callable)
                .hasCauseExactlyInstanceOf(IllegalStateException.class)
                .hasNumberOfFailedAttempts(3)
                .hasExceptionOnLastAttempt();
    }

    @Test
    void testMultipleRetryConditions_WhenCompletesSuccessfully() throws Exception {
        Callable<Boolean> callable = notNullResultOrIOExceptionOrRuntimeExceptionAfter5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfResult(Objects::isNull)
                .retryIfExceptionOfType(IOException.class)
                .retryIfRuntimeException()
                .build();
        assertThat(retryer.call(callable)).isTrue();
    }

    private Callable<Boolean> notNullResultOrIOExceptionOrRuntimeExceptionAfter5Attempts() {
        return new Callable<>() {
            int counter = 0;

            @Override
            public Boolean call() throws IOException {
                if (counter < 1) {
                    counter++;
                    return null;
                } else if (counter < 2) {
                    counter++;
                    throw new IOException();
                } else if (counter < 5) {
                    counter++;
                    throw new IllegalStateException();
                }
                return true;
            }
        };
    }

    @Test
    void testInterruption() throws Exception {
        var result = new AtomicBoolean(false);
        var latch = new CountDownLatch(1);
        Runnable r = () -> {
            var retryer = RetryerBuilder.newBuilder()
                    .withWaitStrategy(WaitStrategies.fixedWait(1000L, TimeUnit.MILLISECONDS))
                    .retryIfResult(Objects::isNull)
                    .build();

            assertThatThrownBy(() -> retryer.call(alwaysNull(latch)))
                    .isExactlyInstanceOf(InterruptedException.class);

            result.set(true);
        };

        var t = new Thread(r);
        t.start();
        latch.countDown();
        t.interrupt();
        t.join();

        //noinspection ConstantConditions
        assertThat(result).isTrue();
    }

    @Test
    void testWrap() throws Exception {
        Callable<Boolean> callable = notNullAfter5Attempts();
        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfResult(Objects::isNull)
                .build();
        RetryerCallable<Boolean> wrapped = retryer.wrap(callable);
        assertThat(wrapped.call()).isTrue();
    }

    @Test
    void testWhetherBuilderFailsForNullWaitStrategyWithCompositeStrategies() {
        assertThatThrownBy(() ->
                RetryerBuilder.newBuilder()
                        .withWaitStrategy(WaitStrategies.join(null, null))
                        .build())
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot have a null wait strategy");
    }

    @Test
    void testRetryListener_SuccessfulAttempt() throws Exception {
        final Map<Integer, Attempt<?>> attempts = new HashMap<>();

        RetryListener listener = attempt -> attempts.put(attempt.getAttemptNumber(), attempt);

        Callable<Boolean> callable = notNullAfter5Attempts();

        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfResult(Objects::isNull)
                .withRetryListener(listener)
                .build();
        assertThat(retryer.call(callable)).isTrue();

        assertThat(attempts).hasSize(6);

        assertResultAttempt(attempts.get(1), null);
        assertResultAttempt(attempts.get(2), null);
        assertResultAttempt(attempts.get(3), null);
        assertResultAttempt(attempts.get(4), null);
        assertResultAttempt(attempts.get(5), null);
        assertResultAttempt(attempts.get(6), true);
    }

    @Test
    void testRetryListener_WithException() throws Exception {
        final Map<Integer, Attempt<?>> attempts = new HashMap<>();

        RetryListener listener = attempt -> attempts.put(attempt.getAttemptNumber(), attempt);

        Callable<Boolean> callable = noIOExceptionAfter5Attempts();

        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfResult(Objects::isNull)
                .retryIfException()
                .withRetryListener(listener)
                .build();
        assertThat(retryer.call(callable)).isTrue();

        assertThat(attempts).hasSize(6);

        assertExceptionAttempt(attempts.get(1), IOException.class);
        assertExceptionAttempt(attempts.get(2), IOException.class);
        assertExceptionAttempt(attempts.get(3), IOException.class);
        assertExceptionAttempt(attempts.get(4), IOException.class);
        assertExceptionAttempt(attempts.get(5), IOException.class);
        assertResultAttempt(attempts.get(6), true);
    }

    @Test
    void testMultipleRetryListeners() throws Exception {
        Callable<Boolean> callable = () -> true;

        final AtomicBoolean listenerOne = new AtomicBoolean(false);
        final AtomicBoolean listenerTwo = new AtomicBoolean(false);

        Retryer retryer = RetryerBuilder.newBuilder()
                .withRetryListener(attempt -> listenerOne.set(true))
                .withRetryListener(attempt -> listenerTwo.set(true))
                .build();

        assertThat(retryer.call(callable)).isTrue();
        assertThat(listenerOne.get()).isTrue();
        assertThat(listenerTwo.get()).isTrue();
    }

    private void assertResultAttempt(Attempt<?> actualAttempt, Object expectedResult) {
        assertThat(actualAttempt.hasException()).isFalse();
        assertThat(actualAttempt.hasResult()).isTrue();
        assertThat(actualAttempt.getResult()).isEqualTo(expectedResult);
    }

    @SuppressWarnings("SameParameterValue")
    private void assertExceptionAttempt(Attempt<?> actualAttempt, Class<?> expectedExceptionClass) {
        assertThat(actualAttempt.hasResult()).isFalse();
        assertThat(actualAttempt.hasException()).isTrue();
        assertThat(actualAttempt.getException()).isInstanceOf(expectedExceptionClass);
    }

    private Callable<Boolean> alwaysNull(final CountDownLatch latch) {
        return () -> {
            latch.countDown();
            return null;
        };
    }
}
