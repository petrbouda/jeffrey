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

package cafe.jeffrey.provider.profile.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("EventDeduplicator")
class EventDeduplicatorTest {

    @Nested
    class CheckAndAddSemantics {

        @Test
        void firstAddReturnsTrueSecondReturnsFalse() {
            EventDeduplicator deduplicator = new EventDeduplicator();

            assertTrue(deduplicator.checkAndAddFrame(42L));
            assertFalse(deduplicator.checkAndAddFrame(42L));

            assertTrue(deduplicator.checkAndAddStacktrace(42L));
            assertFalse(deduplicator.checkAndAddStacktrace(42L));

            assertTrue(deduplicator.checkAndAddThread(42L));
            assertFalse(deduplicator.checkAndAddThread(42L));
        }

        @Test
        void frameStacktraceAndThreadSetsAreIndependent() {
            EventDeduplicator deduplicator = new EventDeduplicator();

            assertTrue(deduplicator.checkAndAddFrame(7L));
            assertTrue(deduplicator.checkAndAddStacktrace(7L), "stacktrace set must not see frame values");
            assertTrue(deduplicator.checkAndAddThread(7L), "thread set must not see frame values");
        }

        @Test
        void boundaryValuesAreDeduplicatedCorrectly() {
            EventDeduplicator deduplicator = new EventDeduplicator();

            long[] boundaryValues = {0L, -1L, 1L, Long.MIN_VALUE, Long.MAX_VALUE};
            for (long value : boundaryValues) {
                assertTrue(deduplicator.checkAndAddFrame(value), "first add of " + value);
                assertFalse(deduplicator.checkAndAddFrame(value), "second add of " + value);
            }
        }

        @Test
        void valuesDifferingOnlyInHighBitsAreDistinct() {
            EventDeduplicator deduplicator = new EventDeduplicator();

            assertTrue(deduplicator.checkAndAddFrame(1L));
            assertTrue(deduplicator.checkAndAddFrame(1L << 32 | 1L), "high-bit variant is a different value");
            assertFalse(deduplicator.checkAndAddFrame(1L << 32 | 1L));
        }
    }

    @Nested
    class ConcurrentHammer {

        private static final int THREAD_COUNT = 8;
        private static final int DISTINCT_VALUES = 50_000;

        @Test
        void exactlyOneThreadWinsEachValue() throws InterruptedException {
            EventDeduplicator deduplicator = new EventDeduplicator();

            AtomicLong successfulAdds = new AtomicLong();
            CountDownLatch startBarrier = new CountDownLatch(1);
            CountDownLatch finished = new CountDownLatch(THREAD_COUNT);

            // Every thread races over the SAME value range — exactly one add() per value may win.
            try (ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT)) {
                for (int t = 0; t < THREAD_COUNT; t++) {
                    executor.submit(() -> {
                        try {
                            startBarrier.await();
                            for (long value = 0; value < DISTINCT_VALUES; value++) {
                                if (deduplicator.checkAndAddFrame(value)) {
                                    successfulAdds.incrementAndGet();
                                }
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } finally {
                            finished.countDown();
                        }
                    });
                }

                startBarrier.countDown();
                await().atMost(30, SECONDS).until(() -> finished.getCount() == 0);
            }

            assertEquals(DISTINCT_VALUES, successfulAdds.get(),
                    "each distinct value must be reported as newly added exactly once across all threads");

            // After the hammer, every value is present — re-adding must consistently return false.
            for (long value = 0; value < DISTINCT_VALUES; value++) {
                assertFalse(deduplicator.checkAndAddFrame(value));
            }
        }
    }
}
