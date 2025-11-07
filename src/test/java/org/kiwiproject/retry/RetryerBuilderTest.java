package org.kiwiproject.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.retry.RetryerAssert.assertThatRetryer;

import org.junit.jupiter.api.Test;
import org.kiwiproject.retry.Retryer.RetryerCallable;

import java.io.IOException;
import java.net.SocketException;
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
        Callable<Boolean> callable = callableReturningNullUntil5Attempts();
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
    void testWithWaitStrategy_ShouldNotAllowSettingIt_WhenOneAlreadyExists() {
        var fixedWait = WaitStrategies.fixedWait(50L, TimeUnit.MILLISECONDS);
        assertThatIllegalStateException()
                .isThrownBy(() -> RetryerBuilder.newBuilder()
                        .withWaitStrategy(fixedWait)
                        .withWaitStrategy(WaitStrategies.fibonacciWait())
                        .retryIfResult(Objects::isNull)
                        .build())
                .withMessage("a wait strategy has already been set: %s", fixedWait);
    }

    @Test
    void testWithMoreThanOneWaitStrategyOneBeingFixed() throws Exception {
        Callable<Boolean> callable = callableReturningNullUntil5Attempts();
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
        Callable<Boolean> callable = callableReturningNullUntil5Attempts();
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

    @Test
    void testWithStopStrategy() {
        Callable<Boolean> callable = callableReturningNullUntil5Attempts();
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
    void testWithStopStrategy_ShouldNotAllowSettingIt_WhenOneAlreadyExists() {
        var stopAfterAttempt = StopStrategies.stopAfterAttempt(3);
        assertThatIllegalStateException()
                .isThrownBy(() -> RetryerBuilder.newBuilder()
                        .withStopStrategy(stopAfterAttempt)
                        .withStopStrategy(StopStrategies.neverStop())
                        .retryIfResult(Objects::isNull)
                        .build())
                .withMessage("a stop strategy has already been set: %s", stopAfterAttempt);
    }

    @Test
    void testRetryIfNotOfExceptionType() {
        Callable<Boolean> callable = callableThrowingIOExceptionUntil5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(SocketException.class)
                .build();

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(callable)
                .hasCauseExactlyInstanceOf(IOException.class)
                .hasNumberOfFailedAttempts(1)
                .hasExceptionOnLastAttempt();
    }

    @Test
    void testWithBlockStrategy() {
        Callable<Boolean> callable = callableReturningNullUntil5Attempts();
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
    void testWithBlockStrategy_ShouldNotAllowSettingIt_WhenOneAlreadyExists() {
        var threadSleepStrategy = BlockStrategies.threadSleepStrategy();
        assertThatIllegalStateException()
                .isThrownBy(() -> RetryerBuilder.newBuilder()
                        .withBlockStrategy(threadSleepStrategy)
                        .withBlockStrategy(BlockStrategies.threadSleepStrategy())
                        .build())
                .withMessage("a block strategy has already been set: %s", threadSleepStrategy);
    }

    @Test
    void testRetryIfException_WhenCompletesSuccessfully() throws Exception {
        Callable<Boolean> callable = callableThrowingIOExceptionUntil5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfException()
                .build();
        boolean result = retryer.call(callable);
        assertThat(result).isTrue();
    }

    @Test
    void testRetryIfException_WhenFailsToComplete_DueToCheckedException() {
        Callable<Boolean> callable = callableThrowingIOExceptionUntil5Attempts();
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
        Callable<Boolean> callable = callableThrowingIllegalStateExceptionUntil5Attempts();
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

    @Test
    void testRetryIfRuntimeException_WhenCheckedExceptionThrown() {
        Callable<Boolean> callable = callableThrowingIOExceptionUntil5Attempts();
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
        Callable<Boolean> callable = callableThrowingIllegalStateExceptionUntil5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfRuntimeException()
                .build();

        assertThatRetryer(retryer)
                .completesSuccessfullyCalling(callable)
                .hasResult(true);
    }

    @Test
    void testRetryIfRuntimeException_WhenRuntimeExceptionThrown_ButStopsAfterAttempt() {
        Callable<Boolean> callable = callableThrowingIllegalStateExceptionUntil5Attempts();
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
        Callable<Boolean> callable = callableThrowingIOExceptionUntil5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(IOException.class)
                .build();

        assertThatRetryer(retryer)
                .completesSuccessfullyCalling(callable)
                .hasResult(true);
    }

    @Test
    void testRetryIfExceptionOfType_WhenFailsToComplete() {
        Callable<Boolean> callable = callableThrowingIllegalStateExceptionUntil5Attempts();
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
        Callable<Boolean> callable = callableThrowingIOExceptionUntil5Attempts();
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
        Callable<Boolean> callable = callableThrowingIOExceptionUntil5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfException(IOException.class::isInstance)
                .build();

        assertThatRetryer(retryer)
                .completesSuccessfullyCalling(callable)
                .hasResult(true);
    }

    @Test
    void testRetryIfExceptionWithPredicate_WhenFailsToComplete() {
        Callable<Boolean> callable = callableThrowingIllegalStateExceptionUntil5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfException(IOException.class::isInstance)
                .build();

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(callable)
                .hasCauseExactlyInstanceOf(IllegalStateException.class)
                .hasNumberOfFailedAttempts(1)
                .hasExceptionOnLastAttempt();
    }

    @Test
    void testRetryIfExceptionWithPredicate_WhenHasStopStrategy() {
        Callable<Boolean> callable = callableThrowingIOExceptionUntil5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfException(IOException.class::isInstance)
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
        Callable<Boolean> callable = callableReturningNullUntil5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfResult(Objects::isNull)
                .build();

        assertThatRetryer(retryer)
                .completesSuccessfullyCalling(callable)
                .hasResult(true);
    }

    @Test
    void testRetryIfResult_WhenHasStopStrategy() {
        Callable<Boolean> callable = callableReturningNullUntil5Attempts();
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
        Callable<Boolean> callable = callableThrowingOrReturningNullUntil5Attempts();
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
        Callable<Boolean> callable = callableThrowingOrReturningNullUntil5Attempts();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfResult(Objects::isNull)
                .retryIfExceptionOfType(IOException.class)
                .retryIfRuntimeException()
                .build();
        assertThat(retryer.call(callable)).isTrue();
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

            assertThatThrownBy(() -> retryer.call(callableCountingDownAlwaysNull(latch)))
                    .isExactlyInstanceOf(InterruptedException.class);

            result.set(true);
        };

        var t = new Thread(r);
        t.start();
        latch.countDown();
        t.interrupt();
        t.join();

        assertThat(result).isTrue();
    }

    @Test
    void testWrap() throws Exception {
        Callable<Boolean> callable = callableReturningNullUntil5Attempts();
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

        Callable<Boolean> callable = callableReturningNullUntil5Attempts();

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

        Callable<Boolean> callable = callableThrowingIOExceptionUntil5Attempts();

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
    void testRetryListener_ShouldIgnoreExceptions_ThrownByListener() throws Exception {
        var listenerCalls = new AtomicInteger();
        RetryListener badListener = attempt -> {
            listenerCalls.incrementAndGet();
            throw new RuntimeException("I am not well-behaved");
        };

        Callable<Boolean> callable = callableThrowingIOExceptionUntil5Attempts();

        var retryer = RetryerBuilder.newBuilder()
                .retryIfResult(Objects::isNull)
                .retryIfException()
                .withRetryListener(badListener)
                .build();

        assertThat(retryer.call(callable)).isTrue();

        assertThat(listenerCalls).hasValue(6);
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

    private static void assertResultAttempt(Attempt<?> actualAttempt, Object expectedResult) {
        assertThat(actualAttempt.hasException()).isFalse();
        assertThat(actualAttempt.hasResult()).isTrue();
        assertThat(actualAttempt.getResult()).isEqualTo(expectedResult);
    }

    @SuppressWarnings("SameParameterValue")
    private static void assertExceptionAttempt(Attempt<?> actualAttempt, Class<?> expectedExceptionClass) {
        assertThat(actualAttempt.hasResult()).isFalse();
        assertThat(actualAttempt.hasException()).isTrue();
        assertThat(actualAttempt.getException()).isInstanceOf(expectedExceptionClass);
    }

    private static Callable<Boolean> callableCountingDownAlwaysNull(final CountDownLatch latch) {
        return () -> {
            latch.countDown();
            return null;
        };
    }

    private static Callable<Boolean> callableReturningNullUntil5Attempts() {
        return new Callable<>() {
            final AtomicInteger count = new AtomicInteger();

            @Override
            public Boolean call() {
                if (count.getAndIncrement() < 5) {
                    return null;
                }
                return true;
            }
        };
    }

    private static Callable<Boolean> callableThrowingIllegalStateExceptionUntil5Attempts() {
        return callableThrowingExceptionUntil5Attempts(new IllegalStateException());
    }

    private static Callable<Boolean> callableThrowingIOExceptionUntil5Attempts() {
        return callableThrowingExceptionUntil5Attempts(new IOException());
    }

    private static <E extends Exception> Callable<Boolean> callableThrowingExceptionUntil5Attempts(E e) {
        return new Callable<>() {
            final AtomicInteger count = new AtomicInteger();

            @Override
            public Boolean call() throws E {
                if (count.getAndIncrement() < 5) {
                    throw e;
                }
                return true;
            }
        };
    }

    private static Callable<Boolean> callableThrowingOrReturningNullUntil5Attempts() {
        return new Callable<>() {
            final AtomicInteger counter = new AtomicInteger();

            @Override
            public Boolean call() throws IOException {
                var count = counter.getAndIncrement();
                if (count < 1) {
                    return null;
                } else if (count < 2) {
                    throw new IOException();
                } else if (count < 5) {
                    throw new IllegalStateException();
                }
                return true;
            }
        };
    }
}
