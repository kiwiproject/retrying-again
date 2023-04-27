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

import static com.google.common.base.Preconditions.checkState;
import static java.util.Objects.nonNull;

import com.google.errorprone.annotations.Immutable;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * An exception indicating that none of the attempts of the {@link Retryer}
 * succeeded. If the last {@link Attempt} resulted in an Exception, it is set as
 * the cause of the {@link RetryException}.
 */
@Immutable
public final class RetryException extends Exception {

    private final transient Attempt<?> lastFailedAttempt;

    /**
     * If the last {@link Attempt} had an Exception, ensure it is available in
     * the stack trace.
     *
     * @param attempt what happened the last time we failed
     */
    RetryException(@NonNull Attempt<?> attempt) {
        this(errorMessageFor(attempt), attempt);
    }

    private static String errorMessageFor(Attempt<?> attempt) {
        return "Retrying failed to complete successfully after " + attempt.getAttemptNumber() + " attempts.";
    }

    /**
     * If the last {@link Attempt} had an Exception, ensure it is available in
     * the stack trace.
     *
     * @param message Exception description to be added to the stack trace
     * @param attempt what happened the last time we failed
     */
    private RetryException(String message, Attempt<?> attempt) {
        super(message, attempt.hasException() ? attempt.getException() : null);
        this.lastFailedAttempt = attempt;
    }

    /**
     * Returns the number of failed attempts
     *
     * @return the number of failed attempts
     */
    public int getNumberOfFailedAttempts() {
        checkState(nonNull(lastFailedAttempt), "lastFailedAttempt is null; cannot get attempt number");
        return lastFailedAttempt.getAttemptNumber();
    }

    /**
     * Returns the last failed attempt
     *
     * @return the last failed attempt
     */
    public Attempt<?> getLastFailedAttempt() {
        return lastFailedAttempt;
    }
}
