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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FutureJoinTest {

    @Nested
    class Success {

        @Test
        void returnsCompletedValue() {
            Future<String> f = CompletableFuture.completedFuture("ok");
            assertEquals("ok", FutureJoin.unwrap(f));
        }
    }

    @Nested
    class ExecutionExceptionHandling {

        @Test
        void unwrapsRuntimeExceptionCauseUnchanged() {
            IllegalStateException original = new IllegalStateException("boom");
            CompletableFuture<String> f = new CompletableFuture<>();
            f.completeExceptionally(original);

            IllegalStateException thrown = assertThrows(IllegalStateException.class, () -> FutureJoin.unwrap(f));
            assertSame(original, thrown);
        }

        @Test
        void wrapsCheckedCauseInRuntimeException() {
            IOException checked = new IOException("disk gone");
            CompletableFuture<String> f = new CompletableFuture<>();
            f.completeExceptionally(checked);

            RuntimeException thrown = assertThrows(RuntimeException.class, () -> FutureJoin.unwrap(f));
            // CompletableFuture.get wraps the cause in ExecutionException; FutureJoin
            // unwraps that envelope. The IOException is preserved as the cause of the
            // RuntimeException we threw.
            assertFalse(thrown instanceof IllegalStateException);
            assertSame(checked, thrown.getCause());
        }
    }

    @Nested
    class InterruptHandling {

        /**
         * Stub future whose {@link #get()} throws {@link InterruptedException}
         * unconditionally — exercises the catch-InterruptedException branch in
         * {@code FutureJoin.unwrap} without races on Thread.interrupt() timing.
         */
        private static final class InterruptingFuture implements Future<String> {
            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public String get() throws InterruptedException {
                throw new InterruptedException("test");
            }

            @Override
            public String get(long timeout, TimeUnit unit) throws InterruptedException {
                throw new InterruptedException("test");
            }
        }

        @Test
        void restoresInterruptFlagAndWraps() {
            // Clear any stale flag from earlier tests before the assertion.
            Thread.interrupted();
            try {
                RuntimeException thrown = assertThrows(RuntimeException.class,
                        () -> FutureJoin.unwrap(new InterruptingFuture()));
                assertInstanceOf(InterruptedException.class, thrown.getCause());
                assertTrue(Thread.currentThread().isInterrupted(),
                        "interrupt flag must be restored after FutureJoin handles InterruptedException");
            } finally {
                Thread.interrupted();
            }
        }
    }
}
