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

import com.google.errorprone.annotations.Immutable;

/**
 * Factory class for {@link BlockStrategy} instances.
 */
@SuppressWarnings("WeakerAccess")
public final class BlockStrategies {

    private static final BlockStrategy THREAD_SLEEP_STRATEGY = new ThreadSleepStrategy();

    private BlockStrategies() {
    }

    /**
     * Returns a block strategy that puts the current thread to sleep between
     * retries.
     *
     * @return a block strategy that puts the current thread to sleep between retries
     */
    public static BlockStrategy threadSleepStrategy() {
        return THREAD_SLEEP_STRATEGY;
    }

    @Immutable
    private static class ThreadSleepStrategy implements BlockStrategy {

        @Override
        public void block(long sleepTime) throws InterruptedException {
            Thread.sleep(sleepTime);
        }
    }
}
