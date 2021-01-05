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

package com.github.rholder.retry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

class RetryerTest {

    @ParameterizedTest
    @MethodSource("checkedAndUnchecked")
    void testCallThrowsWithNoRetryOnException(Class<? extends Throwable> throwableClass) throws Exception {
        Retryer retryer = RetryerBuilder.newBuilder().build();
        Thrower thrower = new Thrower(throwableClass, 5);
        try {
            retryer.call(thrower);
            fail("Should have thrown");
        } catch (RetryException e) {
            assertThat(e.getCause().getClass()).isSameAs(throwableClass);
        }
        assertThat(thrower.invocations).isOne();
    }

    @ParameterizedTest
    @MethodSource("unchecked")
    void testRunThrowsWithNoRetryOnException(Class<? extends Throwable> throwableClass) throws Exception {
        Retryer retryer = RetryerBuilder.newBuilder().build();
        Thrower thrower = new Thrower(throwableClass, 5);
        try {
            retryer.run(thrower);
            fail("Should have thrown");
        } catch (RetryException e) {
            assertThat(e.getCause().getClass()).isSameAs(throwableClass);
        }
        assertThat(thrower.invocations).isOne();
    }

    @ParameterizedTest
    @MethodSource("checkedAndUnchecked")
    void testCallThrowsWithRetryOnException(Class<? extends Throwable> throwable) throws Exception {
        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(Throwable.class)
                .build();
        Thrower thrower = new Thrower(throwable, 5);
        retryer.call(thrower);
        assertThat(thrower.invocations).isEqualTo(5);
    }

    @ParameterizedTest
    @MethodSource("unchecked")
    void testRunThrowsWithRetryOnException(Class<? extends Throwable> throwableClass) throws Exception {
        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(Throwable.class)
                .build();
        Thrower thrower = new Thrower(throwableClass, 5);
        retryer.run(thrower);
        assertThat(thrower.invocations).isEqualTo(5);
    }

    @ParameterizedTest
    @MethodSource("checkedAndUnchecked")
    void testCallThrowsSubclassWithRetryOnException(Class<? extends Throwable> throwableClass) throws Exception {
        @SuppressWarnings("unchecked")
        Class<? extends Throwable> superclass = (Class<? extends Throwable>) throwableClass.getSuperclass();
        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(superclass)
                .build();
        Thrower thrower = new Thrower(throwableClass, 5);
        retryer.call(thrower);
        assertThat(thrower.invocations).isEqualTo(5);
    }

    @ParameterizedTest
    @MethodSource("unchecked")
    void testRunThrowsSubclassWithRetryOnException(Class<? extends Throwable> throwableClass) throws Exception {
        @SuppressWarnings("unchecked")
        Class<? extends Throwable> superclass = (Class<? extends Throwable>) throwableClass.getSuperclass();
        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(superclass)
                .build();
        Thrower thrower = new Thrower(throwableClass, 5);
        retryer.run(thrower);
        assertThat(thrower.invocations).isEqualTo(5);
    }

    @ParameterizedTest
    @MethodSource("checkedAndUnchecked")
    void testCallThrowsWhenRetriesAreStopped(Class<? extends Throwable> throwableClass) throws Exception {
        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(throwableClass)
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();
        Thrower thrower = new Thrower(throwableClass, 5);
        try {
            retryer.call(thrower);
            fail("Should have thrown");
        } catch (RetryException e) {
            assertThat(e.getCause().getClass()).isSameAs(throwableClass);
        }
        assertThat(thrower.invocations).isEqualTo(3);
    }

    @ParameterizedTest
    @MethodSource("unchecked")
    void testRunThrowsWhenRetriesAreStopped(Class<? extends Throwable> throwableClass) throws Exception {
        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfExceptionOfType(throwableClass)
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .build();
        Thrower thrower = new Thrower(throwableClass, 5);
        try {
            retryer.run(thrower);
            fail("Should have thrown");
        } catch (RetryException e) {
            assertThat(e.getCause().getClass()).isSameAs(throwableClass);
        }
        assertThat(thrower.invocations).isEqualTo(3);
    }

    @Test
    void testCallThatIsInterrupted() {
        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfRuntimeException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(10))
                .build();
        Interrupter thrower = new Interrupter(4);
        boolean interrupted = false;
        try {
            retryer.call(thrower);
            fail("Should have thrown");
        } catch (InterruptedException ignored) {
            interrupted = true;
        } catch (Exception e) {
            // TODO Clean this up or remove
            System.out.println(e);
        }
        assertThat(interrupted).isTrue();
        assertThat(thrower.invocations).isEqualTo(4);
    }

    @Test
    void testRunThatIsInterrupted() throws Exception {
        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfRuntimeException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(10))
                .build();
        Interrupter thrower = new Interrupter(4);
        boolean interrupted = false;
        try {
            retryer.run(thrower);
            fail("Should have thrown");
        } catch (InterruptedException ignored) {
            interrupted = true;
        }
        assertThat(interrupted).isTrue();
        assertThat(thrower.invocations).isEqualTo(4);
    }

    @Test
    void testCallWhenBlockerIsInterrupted() throws Exception {
        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(10))
                .withBlockStrategy(new InterruptingBlockStrategy(3))
                .build();
        Thrower thrower = new Thrower(Exception.class, 5);
        boolean interrupted = false;
        try {
            retryer.call(thrower);
            fail("Should have thrown");
        } catch (InterruptedException e) {
            interrupted = true;
        }
        assertThat(interrupted).isTrue();
        assertThat(thrower.invocations).isEqualTo(3);
    }

    @Test
    void testRunWhenBlockerIsInterrupted() throws Exception {
        Retryer retryer = RetryerBuilder.newBuilder()
                .retryIfException()
                .withStopStrategy(StopStrategies.stopAfterAttempt(10))
                .withBlockStrategy(new InterruptingBlockStrategy(3))
                .build();
        Thrower thrower = new Thrower(Exception.class, 5);
        boolean interrupted = false;
        try {
            retryer.run(thrower);
            fail("Should have thrown");
        } catch (InterruptedException e) {
            interrupted = true;
        }
        assertThat(interrupted).isTrue();
        assertThat(thrower.invocations).isEqualTo(3);
    }

    private static Stream<Arguments> checkedAndUnchecked() {
        return Stream.concat(unchecked(), Stream.of(
                Arguments.of(Exception.class),
                Arguments.of(IOException.class)
        ));
    }

    private static Stream<Arguments> unchecked() {
        return Stream.of(
                Arguments.of(Error.class),
                Arguments.of(RuntimeException.class),
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
                throw new InterruptedException("Block strategy interrupted itself");
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
                throw new RuntimeException("Throwing on invocaion " + invocations);
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

