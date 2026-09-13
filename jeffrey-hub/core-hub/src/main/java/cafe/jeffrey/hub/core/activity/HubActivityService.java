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

package cafe.jeffrey.hub.core.activity;

import cafe.jeffrey.hub.api.v1.EventBatch;
import cafe.jeffrey.hub.core.streaming.ReplayStreamSubscription;
import cafe.jeffrey.hub.core.streaming.StreamingCallbacks;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.activity.ActivityLimits;
import cafe.jeffrey.shared.common.activity.ActivityOrder;
import cafe.jeffrey.shared.common.activity.ActivityState;

import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

/** Process-local scans. At most two readers run and at most sixteen jobs are retained. */
public final class HubActivityService implements AutoCloseable {

    private static final int MAX_CONCURRENT_SCANS = 2;
    private static final int MAX_RETAINED_SCANS = 16;
    private static final int MAX_CAUSE_DEPTH = 20;
    private static final Duration RESULT_RETENTION = Duration.ofHours(1);

    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT_SCANS);
    private final Function<ActivityRequest, ReplayStreamSubscription> source;
    private final ActivityReader.Factory readers;
    private final Executor executor;
    private final Clock clock;
    private volatile boolean closed;

    public HubActivityService(Function<ActivityRequest, ReplayStreamSubscription> source) {
        this(source, Schedulers.sharedVirtual(), Clock.systemUTC());
    }

    public HubActivityService(Function<ActivityRequest, ReplayStreamSubscription> source, Clock clock) {
        this(source, Schedulers.sharedVirtual(), clock);
    }

    HubActivityService(
            Function<ActivityRequest, ReplayStreamSubscription> source,
            Executor executor,
            Clock clock) {
        this(source, ActivityReader.Factory.replay(), executor, clock);
    }

    HubActivityService(
            Function<ActivityRequest, ReplayStreamSubscription> source,
            ActivityReader.Factory readers,
            Executor executor,
            Clock clock) {
        this.source = source;
        this.readers = readers;
        this.executor = executor;
        this.clock = clock;
    }

    /**
     * Resolves the scope and admits a scan, or throws because one of the two failed.
     *
     * <p>The scope is resolved here rather than on the worker so that an unknown workspace, project
     * or session is the caller's error — reported now, with nothing registered — instead of a scan ID
     * that occupies one of the retained slots only to report a failure on the first poll. It also
     * runs outside this instance's monitor: it reads a repository and lists a directory, and polls of
     * other scans must not queue behind it.</p>
     */
    public String start(ActivityRequest request) {
        requireOpen();
        ReplayStreamSubscription subscription = source.apply(request);
        return admit(request, subscription);
    }

    private synchronized String admit(ActivityRequest request, ReplayStreamSubscription subscription) {
        requireOpen();
        Instant cutoff = clock.instant().minus(RESULT_RETENTION);
        jobs.values().removeIf(job -> job.finishedAt != null && job.finishedAt.isBefore(cutoff));

        if (jobs.size() >= MAX_RETAINED_SCANS) {
            var oldest = jobs.values().stream()
                    .filter(job -> job.finishedAt != null)
                    .min(Comparator.comparing(job -> job.finishedAt));
            if (oldest.isEmpty()) {
                throw new IllegalStateException("Too many active scans; cancel or wait for an existing scan");
            }
            jobs.remove(oldest.get().id);
        }
        Job job = new Job(request, subscription);
        jobs.put(job.id, job);
        try {
            executor.execute(() -> run(job));
        } catch (RuntimeException e) {
            jobs.remove(job.id);
            throw e;
        }
        return job.id;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("Hub activity service is stopping");
        }
    }

    public ActivitySnapshot status(ActivityScanRef ref, ActivityOrder order, int limit, int offset) {
        return require(ref).snapshot(order, limit, offset);
    }

    public ActivitySnapshot cancel(ActivityScanRef ref) {
        Job job = require(ref);
        job.cancel();
        return job.snapshot(ActivityOrder.EVENTS, ActivityLimits.MAX_RESULT_BUCKETS, 0);
    }

    private synchronized Job require(ActivityScanRef ref) {
        Job job = jobs.get(ref.scanId());
        if (job == null
                || !ref.matches(job.request)
                || (job.finishedAt != null && job.finishedAt.isBefore(clock.instant().minus(RESULT_RETENTION)))) {
            throw new ActivityScanNotFoundException();
        }
        return job;
    }

    private void run(Job job) {
        boolean acquired = false;
        CompletableFuture<Void> cleaned = new CompletableFuture<>();
        ActivityReader reader = null;
        try {
            synchronized (job) {
                job.worker = Thread.currentThread();
                if (job.cancelRequested) {
                    throw new InterruptedException();
                }
            }
            slots.acquire();
            acquired = true;
            synchronized (job) {
                if (job.cancelRequested) {
                    throw new InterruptedException();
                }
                job.state = ActivityState.RUNNING;
            }
            reader = readers.open(
                    job.subscription,
                    new StreamingCallbacks(
                            job::accept,
                            () -> {},
                            error -> job.failed(error.getMessage()),
                            () -> cleaned.complete(null)),
                    job::acceptEvent);
            synchronized (job) {
                job.reader = reader;
            }
            if (job.cancelRequested) {
                reader.close();
            }
            reader.start();
            cleaned.get();
        } catch (InterruptedException e) {
            job.cancel();
        } catch (Exception e) {
            if (!job.cancelRequested || !interrupted(e)) {
                job.failed(e.getMessage());
            }
        } finally {
            if (reader != null) {
                reader.close();
                // A cancellation remains nonterminal until the reader has released its scratch files.
                cleaned.join();
            }
            Thread.interrupted();
            if (acquired) {
                slots.release();
            }
            synchronized (job) {
                job.worker = null;
                job.reader = null;
                job.state = job.failure != null ? ActivityState.FAILED
                        : job.coverageKnown ? ActivityState.COMPLETED
                        : job.cancelRequested ? ActivityState.CANCELLED : ActivityState.FAILED;
                job.finishedAt = clock.instant();
            }
        }
    }

    private static boolean interrupted(Throwable error) {
        for (int depth = 0; error != null && depth < MAX_CAUSE_DEPTH; depth++, error = error.getCause()) {
            if (error instanceof InterruptedException || error instanceof InterruptedIOException
                    || error instanceof ClosedByInterruptException
                    || error instanceof CancellationException) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void close() {
        closed = true;
        List<Job> running;
        synchronized (this) {
            running = new ArrayList<>(jobs.values());
        }
        // Cancelling closes a reader, which can block on the file it holds. Do it off the monitor so
        // a shutdown cannot stall a poll that is already in flight.
        running.forEach(Job::cancel);
    }

    private final class Job {

        private final String id = UUID.randomUUID().toString();
        private final ActivityRequest request;
        private final ReplayStreamSubscription subscription;
        private final EventActivity activity;
        private final Instant startedAt = clock.instant();
        private final int filesTotal;
        private volatile Instant finishedAt;
        private volatile boolean cancelRequested;
        private ActivityState state = ActivityState.QUEUED;
        private Thread worker;
        private ActivityReader reader;
        private String failure;
        private boolean coverageKnown;
        private long sourceErrors;

        Job(ActivityRequest request, ReplayStreamSubscription subscription) {
            this.request = request;
            this.subscription = subscription;
            this.filesTotal = subscription.recordingFiles().size();
            this.activity = new EventActivity(request);
        }

        synchronized void accept(EventBatch batch) {
            if (cancelRequested || failure != null) {
                return;
            }
            if (batch.hasReplayStatus() && batch.getReplayStatus().getTerminal()) {
                coverageKnown = true;
                sourceErrors = batch.getReplayStatus().getSourceErrors();
            }
        }

        void acceptEvent(String type, Instant timestamp) {
            ActivityReader stop = null;
            synchronized (this) {
                if (cancelRequested || failure != null) {
                    return;
                }
                try {
                    activity.add(type, timestamp.toEpochMilli());
                } catch (IllegalStateException e) {
                    failure = e.getMessage();
                    stop = reader;
                }
            }
            if (stop != null) {
                stop.close();
            }
        }

        synchronized void failed(String message) {
            failure = message == null ? "Replay failed" : message;
        }

        void cancel() {
            ActivityReader current;
            synchronized (this) {
                if (finishedAt != null || coverageKnown || cancelRequested) {
                    return;
                }
                cancelRequested = true;
                state = ActivityState.CANCEL_REQUESTED;
                current = reader;
                if (worker != null) {
                    worker.interrupt();
                }
            }
            if (current != null) {
                current.close();
            }
        }

        synchronized ActivitySnapshot snapshot(ActivityOrder order, int limit, int offset) {
            return new ActivitySnapshot(
                    id,
                    state,
                    startedAt,
                    finishedAt,
                    finishedAt != null && coverageKnown && sourceErrors == 0 && failure == null,
                    coverageKnown,
                    sourceErrors,
                    filesTotal,
                    failure,
                    request,
                    activity.summary(order, limit, offset));
        }
    }
}
