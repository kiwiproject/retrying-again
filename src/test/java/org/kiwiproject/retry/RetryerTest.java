package org.kiwiproject.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.kiwiproject.retry.RetryExceptionAssert.assertThatRetryExceptionThrownBy;
import static org.kiwiproject.retry.RetryerAssert.assertThatRetryer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

class RetryerTest {

    @ParameterizedTest
    @MethodSource("errors")
    void testCallDoesNotCatchError(Class<? extends Error> errorClass) {
        var retryer = RetryerBuilder.newBuilder()
                .retryIfException()
                .build();
        var thrower = new Thrower(errorClass, 3);

        assertThatThrownBy(() -> retryer.call(thrower))
                .isExactlyInstanceOf(errorClass);
    }

    @ParameterizedTest
    @MethodSource("errors")
    void testRunDoesNotCatchError(Class<? extends Error> errorClass) {
        var retryer = RetryerBuilder.newBuilder()
                .retryIfException()
                .build();
        var thrower = new Thrower(errorClass, 3);

        assertThatThrownBy(() -> retryer.run(thrower))
                .isExactlyInstanceOf(errorClass);
    }

    private static Stream<Arguments> errors() {
        return Stream.of(
                Arguments.of(CustomError.class),
                Arguments.of(Error.class),
                Arguments.of(LinkageError.class),
                Arguments.of(OutOfMemoryError.class)
        );
    }

    private static class CustomError extends Error {
    }

    @ParameterizedTest
    @MethodSource("checkedAndUnchecked")
    void testCallThrowsWithNoRetryOnException(Class<? extends Exception> exceptionClass) {
        var retryer = RetryerBuilder.newBuilder().build();
        var thrower = new Thrower(exceptionClass, 5);

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(thrower)
                .hasNumberOfFailedAttempts(1)
                .hasExceptionOnLastAttempt();

        assertThat(thrower.invocations).isOne();
    }

    @ParameterizedTest
    @MethodSource("unchecked")
    void testRunThrowsWithNoRetryOnException(Class<? extends Exception> exceptionClass) {
        var retryer = RetryerBuilder.newBuilder().build();
        var thrower = new Thrower(exceptionClass, 5);

        assertThatRetryExceptionThrownBy(() -> retryer.run(thrower))
                .hasCauseExactlyInstanceOf(exceptionClass)
                .hasNumberOfFailedAttempts(1)
                .hasExceptionOnLastAttempt();

        assertThat(thrower.invocations).isOne();
    }

    @ParameterizedTest
    @MethodSource("checkedAndUnchecked")
    void testCallThrowsWithRetryOnException(Class<? extends Exception> exceptionClass) throws Exception {
        var retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(Exception.class)
                .build();
        var thrower = new Thrower(exceptionClass, 5);
        retryer.call(thrower);
        assertThat(thrower.invocations).isEqualTo(5);
    }

    @ParameterizedTest
    @MethodSource("unchecked")
    void testRunThrowsWithRetryOnException(Class<? extends Exception> exceptionClass) throws Exception {
        var retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(Exception.class)
                .build();
        var thrower = new Thrower(exceptionClass, 5);
        retryer.run(thrower);
        assertThat(thrower.invocations).isEqualTo(5);
    }

    @ParameterizedTest
    @MethodSource("checkedAndUnchecked")
    void testCallThrowsSubclassWithRetryOnException(Class<? extends Exception> exceptionClass) throws Exception {
        @SuppressWarnings("unchecked")
        var superclass = (Class<? extends Exception>) exceptionClass.getSuperclass();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(superclass)
                .build();
        var thrower = new Thrower(exceptionClass, 5);
        retryer.call(thrower);
        assertThat(thrower.invocations).isEqualTo(5);
    }

    @ParameterizedTest
    @MethodSource("unchecked")
    void testRunThrowsSubclassWithRetryOnException(Class<? extends Exception> exceptionClass) throws Exception {
        @SuppressWarnings("unchecked")
        var superclass = (Class<? extends Exception>) exceptionClass.getSuperclass();
        var retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(superclass)
                .build();
        var thrower = new Thrower(exceptionClass, 5);
        retryer.run(thrower);
        assertThat(thrower.invocations).isEqualTo(5);
    }

    @ParameterizedTest
    @MethodSource("checkedAndUnchecked")
    void testCallThrowsWhenRetriesAreStopped(Class<? extends Exception> exceptionClass) {
        var retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(exceptionClass)
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();
        var thrower = new Thrower(exceptionClass, 5);

        assertThatRetryer(retryer)
                .throwsRetryExceptionCalling(thrower)
                .hasCauseExactlyInstanceOf(exceptionClass)
                .hasNumberOfFailedAttempts(3)
                .hasExceptionOnLastAttempt();

        assertThat(thrower.invocations).isEqualTo(3);
    }

    @ParameterizedTest
    @MethodSource("unchecked")
    void testRunThrowsWhenRetriesAreStopped(Class<? extends Exception> exceptionClass) {
        var retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(exceptionClass)
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();
        var thrower = new Thrower(exceptionClass, 5);

        assertThatRetryExceptionThrownBy(() -> retryer.run(thrower))
                .hasCauseExactlyInstanceOf(exceptionClass)
                .hasNumberOfFailedAttempts(3)
                .hasExceptionOnLastAttempt();

        assertThat(thrower.invocations).isEqualTo(3);
    }

    @Test
    void testCallThatIsInterrupted() {
        var retryer = RetryerBuilder.newBuilder()
                .retryIfRuntimeException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(10))
                .build();
        var interrupter = new Interrupter(4);

        assertThatThrownBy(() -> retryer.call(interrupter))
                .isExactlyInstanceOf(InterruptedException.class);

        assertThat(interrupter.invocations).isEqualTo(4);
    }

    @Test
    void testRunThatIsInterrupted() {
        var retryer = RetryerBuilder.newBuilder()
                .retryIfRuntimeException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(10))
                .build();
        var interrupter = new Interrupter(4);

        assertThatThrownBy(() -> retryer.run(interrupter))
                .isExactlyInstanceOf(InterruptedException.class);

        assertThat(interrupter.invocations).isEqualTo(4);
    }

    @Test
    void testCallWhenBlockerIsInterrupted() {
        var retryer = RetryerBuilder.newBuilder()
                .retryIfException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(10))
                .withBlockStrategy(new InterruptingBlockStrategy(3))
                .build();
        var thrower = new Thrower(Exception.class, 5);

        assertThatThrownBy(() -> retryer.call(thrower))
                .isExactlyInstanceOf(InterruptedException.class);

        assertThat(thrower.invocations).isEqualTo(3);
    }

    @Test
    void testRunWhenBlockerIsInterrupted() {
        var retryer = RetryerBuilder.newBuilder()
                .retryIfException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(10))
                .withBlockStrategy(new InterruptingBlockStrategy(3))
                .build();
        var thrower = new Thrower(Exception.class, 5);

        assertThatThrownBy(() -> retryer.run(thrower))
                .isExactlyInstanceOf(InterruptedException.class);

        assertThat(thrower.invocations).isEqualTo(3);
    }

    private static Stream<Arguments> checkedAndUnchecked() {
        return Stream.concat(unchecked(), Stream.of(
                Arguments.of(ClassNotFoundException.class),
                Arguments.of(IOException.class),
                Arguments.of(SocketTimeoutException.class)
        ));
    }

    private static Stream<Arguments> unchecked() {
        return Stream.of(
                Arguments.of(IllegalArgumentException.class),
                Arguments.of(IllegalStateException.class),
                Arguments.of(NullPointerException.class)
        );
    }

    /**
     * BlockStrategy that interrupts the thread
     */
    private static class InterruptingBlockStrategy implements BlockStrategy {

        private final int invocationToInterrupt;

        private int currentInvocation;

        InterruptingBlockStrategy(int invocationToInterrupt) {
            this.invocationToInterrupt = invocationToInterrupt;
        }

        @Override
        public void block(long sleepTime) throws InterruptedException {
            ++currentInvocation;
            if (currentInvocation == invocationToInterrupt) {
                throw new InterruptedException("Block strategy interrupted itself on invocation " + invocationToInterrupt);
            } else {
                Thread.sleep(sleepTime);
            }
        }
    }

    /**
     * Callable that throws an exception on a specified attempt (indexed starting with 1).
     * Calls before the interrupt attempt throw an Exception.
     */
    private static class Interrupter implements Callable<Void>, Runnable {

        private final int interruptAttempt;

        private int invocations;

        Interrupter(int interruptAttempt) {
            this.interruptAttempt = interruptAttempt;
        }

        @Override
        public Void call() throws InterruptedException {
            invocations++;
            if (invocations == interruptAttempt) {
                throw new InterruptedException("Interrupted invocation " + invocations);
            } else {
                throw new RuntimeException("Throwing on invocation " + invocations);
            }
        }

        @Override
        public void run() throws RuntimeException {
            try {
                call();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

    }

    /**
     * @implNote The throwable type instance field uses Throwable as the wildcard bound because we
     * need to be able test Error instances such as OutOfMemoryError in addition to Exception and
     * RuntimeException.
     */
    private static class Thrower implements Callable<Void>, Runnable {

        private final Class<? extends Throwable> throwableType;

        private final int successAttempt;

        private int invocations = 0;

        Thrower(Class<? extends Throwable> throwableType, int successAttempt) {
            this.throwableType = throwableType;
            this.successAttempt = successAttempt;
        }

        @Override
        public Void call() throws Exception {
            invocations++;
            if (invocations == successAttempt) {
                return null;
            }
            if (Error.class.isAssignableFrom(throwableType)) {
                throw (Error) throwable();
            }
            throw (Exception) throwable();
        }

        @Override
        public void run() {
            invocations++;
            if (invocations == successAttempt) {
                return;
            }
            if (Error.class.isAssignableFrom(throwableType)) {
                throw (Error) throwable();
            }
            throw (RuntimeException) throwable();
        }

        private Throwable throwable() {
            try {
                return throwableType.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                throw new RuntimeException("Failed to create throwable of type " + throwableType);
            }
        }
    }
}

