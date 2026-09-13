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

package cafe.jeffrey.hub.core.mcp;

import cafe.jeffrey.hub.api.v1.EventBatch;
import cafe.jeffrey.hub.core.streaming.ReplayStreamSubscription;
import cafe.jeffrey.hub.core.streaming.ReplayStreamingSubscriber;
import cafe.jeffrey.hub.core.streaming.StreamingCallbacks;
import cafe.jeffrey.shared.common.Schedulers;
import tools.jackson.databind.node.ObjectNode;

import java.io.InterruptedIOException;
import java.nio.channels.ClosedByInterruptException;
import java.util.concurrent.CancellationException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.function.Function;

/** Process-local scans. At most two readers run and at most sixteen jobs are retained. */
public final class HubActivityService implements AutoCloseable {
    private static final int MAX_CONCURRENT_SCANS = 2;
    private static final int MAX_RETAINED_SCANS = 16;
    private static final int MAX_CAUSE_DEPTH = 20;
    private static final int DEFAULT_RESULT_BUCKETS = 20;
    private static final Duration RESULT_RETENTION = Duration.ofHours(1);
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final Semaphore slots = new Semaphore(MAX_CONCURRENT_SCANS);
    private final Function<ActivityRequest, ReplayStreamSubscription> source;
    private final Executor executor;
    private final Clock clock;
    private boolean closed;

    public HubActivityService(Function<ActivityRequest, ReplayStreamSubscription> source) {
        this(source, Schedulers.sharedVirtual(), Clock.systemUTC());
    }

    HubActivityService(Function<ActivityRequest, ReplayStreamSubscription> source, Executor executor, Clock clock) {
        this.source = source;
        this.executor = executor;
        this.clock = clock;
    }

    public synchronized String start(ActivityRequest request) {
        if (closed) {
            throw new IllegalStateException("Hub activity service is stopping");
        }
        jobs.values().removeIf(job -> job.finishedAt != null && job.finishedAt.isBefore(clock.instant().minus(RESULT_RETENTION)));
        if (jobs.size() >= MAX_RETAINED_SCANS) {
            var oldest = jobs.values().stream().filter(job -> job.finishedAt != null)
                    .min(Comparator.comparing(job -> job.finishedAt));
            if (oldest.isEmpty()) {
                throw new IllegalStateException("Too many active scans; cancel or wait for an existing scan");
            }
            jobs.remove(oldest.get().id);
        }
        Job job = new Job(request);
        jobs.put(job.id, job);
        try {
            executor.execute(() -> run(job));
        } catch (RuntimeException e) {
            jobs.remove(job.id);
            throw e;
        }
        return job.id;
    }

    public ObjectNode status(String id, String order, int limit) {
        return require(id).snapshot(order, limit);
    }

    public ObjectNode cancel(String id) {
        Job job = require(id);
        job.cancel();
        return job.snapshot("events", DEFAULT_RESULT_BUCKETS);
    }

    private synchronized Job require(String id) {
        Job job = jobs.get(id);
        if (job == null || (job.finishedAt != null && job.finishedAt.isBefore(clock.instant().minus(RESULT_RETENTION)))) {
            throw new IllegalArgumentException("Unknown or expired scanId; scans are local to this Hub process");
        }
        return job;
    }

    private void run(Job job) {
        boolean acquired = false;
        CompletableFuture<Void> cleaned = new CompletableFuture<>();
        ReplayStreamingSubscriber reader = null;
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
                job.state = "running";
            }
            ReplayStreamSubscription subscription = source.apply(job.request);
            synchronized (job) {
                job.filesTotal = subscription.recordingFiles().size();
            }
            reader = new ReplayStreamingSubscriber(subscription, new StreamingCallbacks(job::accept,
                    () -> {}, error -> job.failed(error.getMessage()), () -> cleaned.complete(null)), job::acceptEvent);
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
                job.state = job.failure != null ? "failed" : job.coverageKnown ? "completed"
                        : job.cancelRequested ? "cancelled" : "failed";
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
    public synchronized void close() {
        closed = true;
        jobs.values().forEach(Job::cancel);
    }

    private final class Job {
        private final String id = UUID.randomUUID().toString();
        private final ActivityRequest request;
        private final EventActivity activity;
        private final Instant startedAt = clock.instant();
        private volatile Instant finishedAt;
        private volatile boolean cancelRequested;
        private String state = "queued";
        private Thread worker;
        private ReplayStreamingSubscriber reader;
        private String failure;
        private boolean coverageKnown;
        private long sourceErrors;
        private int filesTotal;

        Job(ActivityRequest request) {
            this.request = request;
            activity = new EventActivity(request);
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
            boolean stop = false;
            synchronized (this) {
                if (cancelRequested || failure != null) {
                    return;
                }
                try {
                    activity.add(type, timestamp.toEpochMilli());
                } catch (IllegalStateException e) {
                    failure = e.getMessage();
                    stop = true;
                }
            }
            if (stop) {
                reader.close();
            }
        }

        synchronized void failed(String message) {
            failure = message == null ? "Replay failed" : message;
        }

        void cancel() {
            ReplayStreamingSubscriber current;
            synchronized (this) {
                if (finishedAt != null || coverageKnown || cancelRequested) {
                    return;
                }
                cancelRequested = true;
                state = "cancel_requested";
                current = reader;
                if (worker != null) {
                    worker.interrupt();
                }
            }
            if (current != null) {
                current.close();
            }
        }

        synchronized ObjectNode snapshot(String order, int limit) {
            ObjectNode result = activity.summary(order, limit);
            result.put("scanId", id).put("status", state).put("startedAt", startedAt.toString());
            result.put("finishedAt", finishedAt == null ? null : finishedAt.toString());
            result.put("complete", finishedAt != null && coverageKnown && sourceErrors == 0 && failure == null)
                    .put("coverageKnown", coverageKnown).put("sourceErrors", sourceErrors).put("filesTotal", filesTotal)
                    .put("error", failure).put("workspaceId", request.workspaceId()).put("projectId", request.projectId())
                    .put("sessionId", request.sessionId()).put("startTime", request.startTime())
                    .put("endTime", request.endTime()).put("bucketMillis", request.bucketMillis());
            var types = result.putArray("eventTypes");
            request.eventTypes().stream().sorted().forEach(types::add);
            result.put("coverage", "Finished files visible at scan start. Overlapping recordings may count events more than once. "
                    + "Counts reflect recorded events, not equivalent workloads. Incomplete scans are lower bounds; their rankings may change.");
            result.put("timeSemantics", "Epoch milliseconds, start inclusive and end exclusive; buckets anchored at startTime.");
            return result;
        }
    }
}
