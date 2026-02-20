/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.scheduler;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.shared.common.model.job.JobType;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PeriodicalSchedulerTest {

    private PeriodicalScheduler scheduler;

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.close();
        }
    }

    private static Job testJob(Runnable action, Duration period) {
        return new Job() {
            @Override
            public void execute(JobContext context) {
                action.run();
            }

            @Override
            public Duration period() {
                return period;
            }

            @Override
            public JobType jobType() {
                return JobType.PROJECTS_SYNCHRONIZER;
            }
        };
    }

    @Nested
    class Start {

        @Test
        void schedulesAllJobs_atFixedRate() throws InterruptedException {
            CountDownLatch latch = new CountDownLatch(2);
            Job job = testJob(latch::countDown, Duration.ofMillis(50));

            scheduler = new PeriodicalScheduler(List.of(job));
            scheduler.start();

            assertTrue(latch.await(2, TimeUnit.SECONDS));
        }

        @Test
        void startIsIdempotent_calledTwice_onlyOneScheduler() throws InterruptedException {
            AtomicInteger counter = new AtomicInteger();
            Job job = testJob(counter::incrementAndGet, Duration.ofMillis(50));

            scheduler = new PeriodicalScheduler(List.of(job));
            scheduler.start();
            scheduler.start();

            Thread.sleep(200);
            // If two schedulers were created, counter would grow much faster
            // With one scheduler at 50ms intervals over 200ms, we'd expect around 4-5 executions
            assertTrue(counter.get() < 10);
        }
    }

    @Nested
    class Submit {

        @Test
        void submitsJobImmediately_whenSchedulerStarted() throws Exception {
            CountDownLatch latch = new CountDownLatch(1);
            Job periodicJob = testJob(() -> {}, Duration.ofSeconds(60));
            Job immediateJob = testJob(latch::countDown, Duration.ofSeconds(60));

            scheduler = new PeriodicalScheduler(List.of(periodicJob));
            scheduler.start();

            CompletableFuture<Void> future = scheduler.submit(immediateJob);
            assertNotNull(future);
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        }

        @Test
        void returnsNull_whenSchedulerNotStarted() {
            Job job = testJob(() -> {}, Duration.ofSeconds(60));
            scheduler = new PeriodicalScheduler(List.of());

            CompletableFuture<Void> result = scheduler.submit(job);

            assertNull(result);
        }
    }

    @Nested
    class Close {

        @Test
        void shutsDownExecutor() throws InterruptedException {
            AtomicInteger counter = new AtomicInteger();
            Job job = testJob(counter::incrementAndGet, Duration.ofMillis(50));

            scheduler = new PeriodicalScheduler(List.of(job));
            scheduler.start();
            Thread.sleep(100);

            scheduler.close();
            int countAfterClose = counter.get();
            Thread.sleep(200);

            // No more executions should happen after close
            assertEquals(countAfterClose, counter.get());
        }

        @Test
        void closeIsNoOp_whenNeverStarted() {
            scheduler = new PeriodicalScheduler(List.of());
            assertDoesNotThrow(() -> scheduler.close());
        }
    }
}
