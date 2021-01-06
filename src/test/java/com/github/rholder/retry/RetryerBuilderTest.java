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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.kiwiproject.test.assertj.KiwiAssertJ.assertIsExactType;

import com.github.rholder.retry.Retryer.RetryerCallable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

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

        var thrown = catchThrowable(() -> retryer.call(callable));
        var retryException = assertIsExactType(thrown, RetryException.class);
        assertThat(retryException.getNumberOfFailedAttempts()).isEqualTo(3);
    }

    @Test
    @Disabled("Empty test needs to be implemented or deleted!")
    void testRetryIfNotOfExceptionType() {
        // TODO Found this blank test. Delete it? Or try to determine what the original
        //  author meant and implement it?
    }

    @Test
    void testWithBlockStrategy() throws Exception {
        Callable<Boolean> callable = notNullAfter5Attempts();
        var counter = new AtomicInteger();
        BlockStrategy blockStrategy = sleepTime -> counter.incrementAndGet();

        var retryer = RetryerBuilder.newBuilder()
                .withBlockStrategy(blockStrategy)
                .retryIfResult(Objects::isNull)
                .build();
        final int retryCount = 5;
        boolean result = retryer.call(callable);

        assertThat(result).isTrue();
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

        var thrown = catchThrowable(() -> retryer.call(callable));
        var retryException = assertIsExactType(thrown, RetryException.class);
        assertThat(retryException.getNumberOfFailedAttempts()).isEqualTo(3);
    }

    @Test
    void testRetryIfException_WhenFailsToComplete_DueToRuntimeException() {
        Callable<Boolean> callable = noIllegalStateExceptionAfter5Attempts();
        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        var thrown = catchThrowable(() -> retryer.call(callable));
        var retryException = assertIsExactType(thrown, RetryException.class);
        assertThat(retryException.getNumberOfFailedAttempts()).isEqualTo(3);
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
    void testRetryIfRuntimeException() throws Exception {
        // TODO Split out logical tests: testRetryIfRuntimeException

        Callable<Boolean> callable1 = noIOExceptionAfter5Attempts();
        var retryer1 = RetryerBuilder.newBuilder()
                .retryIfRuntimeException()
                .build();

        var thrown1 = catchThrowable(() -> retryer1.call(callable1));
        var retryException1 = assertIsExactType(thrown1, RetryException.class);
        assertThat(retryException1).hasCauseExactlyInstanceOf(IOException.class);

        //

        Callable<Boolean> callable2 = noIllegalStateExceptionAfter5Attempts();
        var retryer2 = RetryerBuilder.newBuilder()
                .retryIfRuntimeException()
                .build();
        assertThat(retryer2.call(callable2)).isTrue();

        //

        Callable<Boolean> callable3 = noIllegalStateExceptionAfter5Attempts();
        var retryer3 = RetryerBuilder.newBuilder()
                .retryIfRuntimeException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        var thrown3 = catchThrowable(() -> retryer3.call(callable3));
        var retryException3 = assertIsExactType(thrown3, RetryException.class);
        assertThat(retryException3).hasCauseExactlyInstanceOf(IllegalStateException.class);
    }

    @Test
    void testRetryIfExceptionOfType() throws Exception {
        // TODO Split out logical tests: testRetryIfExceptionOfType

        Callable<Boolean> callable1 = noIOExceptionAfter5Attempts();
        var retryer1 = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(IOException.class)
                .build();
        assertThat(retryer1.call(callable1)).isTrue();

        //

        Callable<Boolean> callable2 = noIllegalStateExceptionAfter5Attempts();
        var retryer2 = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(IOException.class)
                .build();

        var thrown2 = catchThrowable(() -> retryer2.call(callable2));
        var retryException2 = assertIsExactType(thrown2, RetryException.class);
        assertThat(retryException2).hasCauseExactlyInstanceOf(IllegalStateException.class);

        //

        Callable<Boolean> callable3 = noIOExceptionAfter5Attempts();
        var retryer3 = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(IOException.class)
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        var thrown3 = catchThrowable(() -> retryer3.call(callable3));
        var retryException3 = assertIsExactType(thrown3, RetryException.class);
        assertThat(retryException3).hasCauseExactlyInstanceOf(IOException.class);
    }

    @Test
    void testRetryIfExceptionWithPredicate() throws Exception {
        // TODO Split out logical tests: testRetryIfExceptionWithPredicate

        Callable<Boolean> callable1 = noIOExceptionAfter5Attempts();
        var retryer1 = RetryerBuilder.newBuilder()
                .retryIfException(t -> t instanceof IOException)
                .build();
        assertThat(retryer1.call(callable1)).isTrue();

        //

        Callable<Boolean> callable2 = noIllegalStateExceptionAfter5Attempts();
        var retryer2 = RetryerBuilder.newBuilder()
                .retryIfException(t -> t instanceof IOException)
                .build();

        var thrown2 = catchThrowable(() -> retryer2.call(callable2));
        var retryException2 = assertIsExactType(thrown2, RetryException.class);
        assertThat(retryException2).hasCauseExactlyInstanceOf(IllegalStateException.class);

        //

        Callable<Boolean> callable3 = noIOExceptionAfter5Attempts();
        var retryer3 = RetryerBuilder.newBuilder()
                .retryIfException(t -> t instanceof IOException)
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        var thrown3 = catchThrowable(() -> retryer3.call(callable3));
        var retryException3 = assertIsExactType(thrown3, RetryException.class);
        assertThat(retryException3).hasCauseExactlyInstanceOf(IOException.class);
    }

    @Test
    void testRetryIfResult() throws Exception {
        // TODO Split out logical tests: testRetryIfResult

        Callable<Boolean> callable1 = notNullAfter5Attempts();
        var retryer1 = RetryerBuilder.newBuilder()
                .retryIfResult(Objects::isNull)
                .build();
        assertThat(retryer1.call(callable1)).isTrue();

        //

        Callable<Boolean> callable2 = notNullAfter5Attempts();
        var retryer2 = RetryerBuilder.newBuilder()
                .retryIfResult(Objects::isNull)
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        var thrown2 = catchThrowable(() -> retryer2.call(callable2));
        var retryException2 = assertIsExactType(thrown2, RetryException.class);
        assertThat(retryException2.getNumberOfFailedAttempts()).isEqualTo(3);
        assertThat(retryException2.getLastFailedAttempt().hasResult()).isTrue();
        assertThat(retryException2.getLastFailedAttempt().getResult()).isNull();
        assertThat(retryException2.getCause()).isNull();
    }

    @Test
    void testMultipleRetryConditions() throws Exception {
        // TODO Split out logical tests: testMultipleRetryConditions

        Callable<Boolean> callable1 = notNullResultOrIOExceptionOrRuntimeExceptionAfter5Attempts();
        var retryer1 = RetryerBuilder.newBuilder()
                .retryIfResult(Objects::isNull)
                .retryIfExceptionOfType(IOException.class)
                .retryIfRuntimeException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();

        assertThatThrownBy(() -> retryer1.call(callable1))
                .isExactlyInstanceOf(RetryException.class);

        //

        Callable<Boolean> callable2 = notNullResultOrIOExceptionOrRuntimeExceptionAfter5Attempts();
        var retryer2 = RetryerBuilder.newBuilder()
                .retryIfResult(Objects::isNull)
                .retryIfExceptionOfType(IOException.class)
                .retryIfRuntimeException()
                .build();
        assertThat(retryer2.call(callable2)).isTrue();
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
        assertThatThrownBy(() -> RetryerBuilder.newBuilder()
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
