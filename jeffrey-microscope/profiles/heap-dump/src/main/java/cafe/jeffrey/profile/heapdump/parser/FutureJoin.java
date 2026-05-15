/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
