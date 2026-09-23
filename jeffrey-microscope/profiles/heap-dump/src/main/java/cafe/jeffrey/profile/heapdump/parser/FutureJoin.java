/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cafe.jeffrey.profile.heapdump.parser;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Static helper for joining on a {@link Future} and unwrapping the standard
 * checked-exception envelope into either a {@link RuntimeException} (preserved
 * unwrapped) or a wrapping {@code RuntimeException}. Used by the index-build
 * phases that fan work out to virtual-thread workers — each merge point reads
 * the same shape.
 */
public final class FutureJoin {

    private FutureJoin() {
    }

    /**
     * Waits for {@code future} and returns its result. Restores the interrupt
     * flag on {@link InterruptedException}; re-throws {@link RuntimeException}
     * causes unwrapped from {@link ExecutionException}; wraps everything else
     * in a {@link RuntimeException}.
     */
    public static <T> T unwrap(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(cause);
        }
    }
}
