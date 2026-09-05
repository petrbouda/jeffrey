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
package cafe.jeffrey.microscope.core.mcp.tools;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What keeps a client from turning one import or one multi-gigabyte transfer into two: work is keyed
 * by what the caller already holds, and a second call for the same key joins the first rather than
 * starting a rival.
 */
class BoundedJobsTest {

    private static final Duration GENEROUS = Duration.ofSeconds(10);
    private static final Duration IMMEDIATE = Duration.ofMillis(50);

    @Nested
    class Construction {

        @Test
        void refusesABudgetThatIsNotPositive() {
            assertThrows(IllegalArgumentException.class, () -> new BoundedJobs<>(Duration.ZERO));
            assertThrows(IllegalArgumentException.class,
                    () -> new BoundedJobs<>(Duration.ofSeconds(-1)));
            assertThrows(IllegalArgumentException.class, () -> new BoundedJobs<>(null));
        }
    }

    @Nested
    class InsideTheBudget {

        @Test
        void handsBackTheResultOfWorkThatFinishedInTime() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(GENEROUS);

            assertEquals(Optional.of("done"), jobs.runWithin("r-1", () -> "done"));
        }

        /**
         * The failure the caller sees is the one the work threw. Reporting the ExecutionException
         * wrapper instead would give every failure the same message and hide the one saying which
         * file could not be parsed.
         */
        @Test
        void reportsTheFailureTheWorkActuallyThrew() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(GENEROUS);

            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> jobs.runWithin("r-1", () -> {
                        throw new IllegalArgumentException("recording is not a JFR file");
                    }));

            assertEquals("recording is not a JFR file", thrown.getMessage());
        }

        /**
         * A failed job must not be remembered as in flight, or the tool would report a transfer that
         * is running when nothing is.
         */
        @Test
        void forgetsAJobThatFailed() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(GENEROUS);

            assertThrows(IllegalStateException.class, () -> jobs.runWithin("r-1", () -> {
                throw new IllegalStateException("hub stopped answering");
            }));

            await().atMost(5, SECONDS).untilAsserted(() -> assertFalse(jobs.isRunning("r-1")));
        }

        @Test
        void forgetsAJobThatSucceeded() {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(GENEROUS);
            jobs.runWithin("r-1", () -> "done");

            await().atMost(5, SECONDS).untilAsserted(() -> assertFalse(jobs.isRunning("r-1")));
        }
    }

    @Nested
    class PastTheBudget {

        /**
         * Not a failure: the work carries on and the caller is told how to follow it, which is what
         * stops a client from retrying and starting the whole transfer again.
         */
        @Test
        void handsBackNothingWhileTheWorkContinues() throws InterruptedException {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(IMMEDIATE);
            CountDownLatch release = new CountDownLatch(1);

            Optional<String> answer = jobs.runWithin("r-1", () -> {
                awaitQuietly(release);
                return "done";
            });

            assertTrue(answer.isEmpty());
            assertTrue(jobs.isRunning("r-1"));
            release.countDown();
            await().atMost(5, SECONDS).untilAsserted(() -> assertFalse(jobs.isRunning("r-1")));
        }

        /**
         * The whole point of the key. A second call for the same recording joins the first; without
         * this it would import the same file, or pull the same session, a second time.
         */
        @Test
        void joinsWorkAlreadyRunningForTheSameKey() throws InterruptedException {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(IMMEDIATE);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger started = new AtomicInteger();

            jobs.runWithin("r-1", () -> {
                started.incrementAndGet();
                awaitQuietly(release);
                return "done";
            });
            Optional<String> second = jobs.runWithin("r-1", () -> {
                started.incrementAndGet();
                return "a rival";
            });

            assertTrue(second.isEmpty());
            assertEquals(1, started.get(), "the second call must not have started its own work");
            release.countDown();
            await().atMost(5, SECONDS).untilAsserted(() -> assertFalse(jobs.isRunning("r-1")));
        }

        @Test
        void runsDifferentKeysIndependently() throws InterruptedException {
            BoundedJobs<String, String> jobs = new BoundedJobs<>(IMMEDIATE);
            CountDownLatch release = new CountDownLatch(1);

            jobs.runWithin("r-1", () -> {
                awaitQuietly(release);
                return "slow";
            });

            assertEquals(Optional.of("quick"), jobs.runWithin("r-2", () -> "quick"));
            release.countDown();
        }
    }

    @Test
    void reportsNothingRunningForAKeyItHasNeverSeen() {
        assertFalse(new BoundedJobs<String, String>(GENEROUS).isRunning("never-asked-for"));
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
