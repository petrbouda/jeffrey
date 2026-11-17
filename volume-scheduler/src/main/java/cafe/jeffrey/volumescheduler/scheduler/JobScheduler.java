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

package cafe.jeffrey.volumescheduler.scheduler;

import cafe.jeffrey.shared.common.measure.Elapsed;
import cafe.jeffrey.shared.common.measure.Measuring;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The jobs, when each runs on its own, and one lock per job.
 *
 * <p>An enabled job is scheduled with fixed <em>delay</em>, first at {@link #start()}: the period
 * is measured from the end of one run to the start of the next, so a run that overruns its period
 * never produces back-to-back catch-up runs. The pool has a thread per enabled job, so different
 * jobs run side by side; the job's lock keeps a job from running beside itself, which matters when
 * an operator's run on demand ({@link #runNow}) meets a scheduled one: the request waits for the
 * run in flight rather than walking the same projects beside it.</p>
 */
public final class JobScheduler implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(JobScheduler.class);

    /** How long {@link #close()} waits for a run in flight before letting the context go down under it. */
    private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);

    private static final String THREAD_NAME_PREFIX = "volume-scheduler-";

    private static final ThreadFactory THREADS = Thread.ofPlatform()
            .name(THREAD_NAME_PREFIX, 0)
            .daemon(true)
            .factory();

    /** A job with its schedule and the lock every run of it takes. */
    private record Slot(Job job, JobSchedule schedule, ReentrantLock lock) {

        JobStatus status() {
            return new JobStatus(job.type(), schedule);
        }
    }

    private final Map<JobType, Slot> slots = new EnumMap<>(JobType.class);
    private final Clock clock;

    private ScheduledExecutorService executor;

    public JobScheduler(List<ScheduledJob> jobs, Clock clock) {
        for (ScheduledJob scheduled : jobs) {
            Slot previous = slots.put(
                    scheduled.job().type(), new Slot(scheduled.job(), scheduled.schedule(), new ReentrantLock()));
            if (previous != null) {
                throw new IllegalArgumentException("Two jobs of one type: type=" + scheduled.job().type());
            }
        }
        this.clock = clock;
    }

    /** Schedules every enabled job; calling it again does nothing. */
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        List<Slot> enabled = slots.values().stream()
                .filter(slot -> slot.schedule().enabled())
                .toList();
        LOG.info("Scheduling jobs: enabled={} total={}",
                enabled.stream().map(slot -> slot.job().type().id()).toList(), slots.size());
        if (enabled.isEmpty()) {
            return;
        }
        executor = Executors.newScheduledThreadPool(enabled.size(), THREADS);
        for (Slot slot : enabled) {
            executor.scheduleWithFixedDelay(
                    () -> scheduledRun(slot), 0, slot.schedule().period().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stops scheduling and waits for the run in flight, because a session deletion or a
     * compression rename cut off half-way is exactly the state every job is written to never
     * leave behind. {@link ScheduledExecutorService#shutdown()} cancels the runs to come and lets
     * the running one finish; nothing is interrupted.
     */
    @Override
    public synchronized void close() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                LOG.warn("A job was still running when the shutdown timeout passed: timeout={}", SHUTDOWN_TIMEOUT);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Every job, in the order {@link JobType} declares them. */
    public List<JobStatus> jobs() {
        return slots.values().stream()
                .map(Slot::status)
                .toList();
    }

    public Optional<JobStatus> job(JobType type) {
        return Optional.ofNullable(slots.get(type)).map(Slot::status);
    }

    /**
     * Runs the job now, on the caller's thread, and reports what it did. A disabled job runs too:
     * disabling only takes it off the schedule. A failure propagates rather than reporting success.
     */
    public JobRun runNow(JobType type) {
        Slot slot = slots.get(type);
        if (slot == null) {
            throw new IllegalArgumentException("No job of this type is registered: type=" + type);
        }
        Instant startedAt = clock.instant();
        LOG.info("Job run on demand started: job_type={}", type.id());
        Elapsed<String> run = exclusively(slot);
        LOG.info("Job run on demand finished: job_type={} duration_ms={} summary={}",
                type.id(), run.duration().toMillis(), run.entity());
        return new JobRun(type, startedAt, run.duration(), run.entity());
    }

    private static void scheduledRun(Slot slot) {
        try {
            Elapsed<String> run = exclusively(slot);
            LOG.debug("Scheduled job run finished: job_type={} duration_ms={} summary={}",
                    slot.job().type().id(), run.duration().toMillis(), run.entity());
        } catch (Throwable t) {
            // Deliberately Throwable, not Exception: anything escaping the task makes
            // scheduleWithFixedDelay silently cancel this job for the rest of the process lifetime
            LOG.error("Scheduled job run failed: job_type={}", slot.job().type().id(), t);
        }
    }

    private static Elapsed<String> exclusively(Slot slot) {
        slot.lock().lock();
        try {
            return Measuring.s(slot.job()::run);
        } finally {
            slot.lock().unlock();
        }
    }
}
