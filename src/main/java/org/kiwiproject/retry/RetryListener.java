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

/**
 * This listener provides callbacks for several events that occur when running
 * code through a {@link Retryer} instance.
 */
public interface RetryListener {

    /**
     * This method with fire no matter what the result is and before the
     * retry predicate and stop strategies are applied.
     *
     * @param attempt the current {@link Attempt}
     * @apiNote No exceptions should be thrown from this method. But, if an exception is thrown by an
     * implementation, it will be silently ignored so that it does not halt processing of a {@link Retryer}.
     */
    void onRetry(Attempt<?> attempt);
}
