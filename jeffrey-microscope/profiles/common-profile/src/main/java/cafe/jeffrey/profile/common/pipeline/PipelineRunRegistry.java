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

package cafe.jeffrey.profile.common.pipeline;

import cafe.jeffrey.jfr.events.trace.SpanKind;
import cafe.jeffrey.jfr.events.trace.SpanStatus;
import cafe.jeffrey.jfr.events.trace.TraceSpanEvent;
import cafe.jeffrey.jfr.events.trace.Tracer;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Runs a pipeline in the background and remembers the current or most recent run per key, so a
 * frontend can poll it.
 *
 * <p>Process-wide, because the managers that do the work are rebuilt per request and cannot hold run
 * state. At most one run per key is active; a second request while one is in flight is not an error,
 * it just answers "already running" and the caller polls the run that exists.</p>
 *
 * <p>Progress lives only here, in memory. That is a deliberate limit: a run interrupted by a restart is
 * gone, which is truthful, because the work died with the process. What survives is the terminal
 * result, handed to {@link PipelineRunRequest#onFinished()} for the caller to store.</p>
 */
public final class PipelineRunRegistry<K> {

    private static final Logger LOG = LoggerFactory.getLogger(PipelineRunRegistry.class);

    private static final Duration EVICTION_INTERVAL = Duration.ofMinutes(1);

    private static final String CANCELLED_MESSAGE = "Cancelled";

    private static final String CANCELLED_WHILE_QUEUED_MESSAGE = "Cancelled while waiting for a slot";

    private final PipelineDefinition definition;
    private final PipelineRunOptions options;
    private final PipelineSlots slots;
    private final Clock clock;

    private final ConcurrentMap<K, TrackedRun> runsByKey = new ConcurrentHashMap<>();

    public PipelineRunRegistry(PipelineDefinition definition, PipelineRunOptions options, Clock clock) {
        if (definition == null || options == null || clock == null) {
            throw new IllegalArgumentException("Definition, options and clock are all required");
        }
        this.definition = definition;
        this.options = options;
        this.clock = clock;
        this.slots = new PipelineSlots(options.maxConcurrentRuns());

        if (options.evictsFinishedRuns()) {
            Schedulers.sharedSingleScheduled().scheduleAtFixedRate(
                    this::evictFinishedRuns,
                    EVICTION_INTERVAL.toMillis(),
                    EVICTION_INTERVAL.toMillis(),
                    TimeUnit.MILLISECONDS);
        }
    }

    public PipelineDefinition definition() {
        return definition;
    }

    /**
     * Changes how many runs may execute at once, for runs scheduled from now on. Runs already holding a
     * slot keep it — a lowered ceiling takes hold as they finish rather than by interrupting them.
     *
     * @param maxConcurrentRuns the new ceiling, or {@link PipelineRunOptions#UNBOUNDED} for none
     */
    public void setMaxConcurrentRuns(int maxConcurrentRuns) {
        if (slots.resize(maxConcurrentRuns)) {
            LOG.info("Pipeline concurrency ceiling changed: pipeline_id={} max_concurrent_runs={}",
                    definition.pipelineId(), maxConcurrentRuns);
        }
    }

    public int maxConcurrentRuns() {
        return slots.permits();
    }

    /**
     * Starts a run unless one is already in flight for the same key.
     *
     * @return true when this call started a run, false when it found one already going
     */
    public boolean start(PipelineRunRequest<K> request) {
        // The candidate is built up front so the outcome can be decided by identity: if compute() gave
        // back anything else, an in-flight run kept the key and this call started nothing.
        TrackedRun candidate = new TrackedRun(new PipelineRun(definition, request.scopeId(), clock));
        TrackedRun current = runsByKey.compute(request.key(), (_, existing) ->
                existing != null && existing.run.isRunning() ? existing : candidate);

        if (current != candidate) {
            LOG.debug("Pipeline run already in flight: pipeline_id={} key={}",
                    definition.pipelineId(), request.key());
            return false;
        }

        LOG.info("Queued pipeline run: pipeline_id={} key={} scope_id={} available_slots={}",
                definition.pipelineId(), request.key(), request.scopeId(), slots.availablePermits());

        CompletableFuture
                .runAsync(() -> execute(request, candidate), Schedulers.sharedVirtual())
                .exceptionally(ex -> {
                    LOG.error("Pipeline run crashed: pipeline_id={} key={}",
                            definition.pipelineId(), request.key(), ex);
                    return null;
                });
        return true;
    }

    /** Live progress of the current (or last finished) run; idle when none exists. */
    public PipelineProgress progress(K key) {
        TrackedRun tracked = runsByKey.get(key);
        return tracked == null ? PipelineProgress.idle(definition.pipelineId()) : tracked.run.progress();
    }

    public Optional<PipelineRun> find(K key) {
        return Optional.ofNullable(runsByKey.get(key)).map(tracked -> tracked.run);
    }

    public boolean isRunning(K key) {
        TrackedRun tracked = runsByKey.get(key);
        return tracked != null && tracked.run.isRunning();
    }

    /**
     * Cancels an in-flight run: marks it failed and interrupts the thread executing it. Whether the
     * underlying work notices the interrupt is up to that work, so the run is marked failed regardless
     * rather than left hanging in a state the UI would render as still going.
     *
     * <p>The mark comes first, and the interrupt only fires when the mark won — {@link PipelineRun#fail}
     * is first-transition-wins, so a run whose work completed a microsecond earlier stays completed and
     * its thread is never interrupted while it stores results.</p>
     */
    public boolean cancel(K key) {
        TrackedRun tracked = runsByKey.get(key);
        if (tracked == null || !tracked.run.fail(null, CANCELLED_MESSAGE)) {
            return false;
        }
        Thread worker = tracked.worker;
        if (worker != null) {
            worker.interrupt();
        }
        LOG.info("Cancelled pipeline run: pipeline_id={} key={}", definition.pipelineId(), key);
        return true;
    }

    private void execute(PipelineRunRequest<K> request, TrackedRun tracked) {
        PipelineRun run = tracked.run;
        // Cancelled before this task ever ran (there was no thread to interrupt yet): the work must
        // not start on a run the caller already ended.
        if (!run.isRunning()) {
            notifyFinished(request, run);
            return;
        }
        tracked.worker = Thread.currentThread();

        try {
            slots.acquire();
        } catch (InterruptedException e) {
            tracked.worker = null;
            Thread.interrupted();
            run.fail(null, CANCELLED_WHILE_QUEUED_MESSAGE);
            notifyFinished(request, run);
            return;
        }

        // The root span of this run's trace. Stages opened by PipelineRun.runStage nest under it.
        // The event is built here rather than left to Tracer.run because a pipeline knows things a
        // generic span does not: which profile the run was for, and whether it actually succeeded.
        TraceSpanEvent span = new TraceSpanEvent();
        span.name = definition.pipelineId();
        span.kind = SpanKind.INTERNAL.name();
        span.begin();

        try {
            // Scoped to the work itself rather than the whole method so the span measures execution,
            // not the time spent queueing for a slot above.
            Tracer.inSpanOf(span, () -> {
                request.work().accept(run);
                return null;
            });
            run.complete();
            // OK rather than UNSET: a pipeline reaching complete() is a success the code observed,
            // not merely the absence of a thrown exception.
            span.status = SpanStatus.OK.name();
            LOG.info("Pipeline run completed: pipeline_id={} key={} duration_in_ms={}",
                    definition.pipelineId(), request.key(),
                    Duration.between(run.startedAt(), clock.instant()).toMillis());
        } catch (Throwable e) {
            span.status = SpanStatus.ERROR.name();
            span.errorType = e.getClass().getName();
            // Errors are marked too — otherwise an OutOfMemoryError would leave the run RUNNING and
            // its key blocked forever. They still propagate after the finally block runs.
            run.fail(errorCodeOf(e), e.getMessage());
            // With the exception attached, not just its message: the message alone is often a wrapper
            // ("Failed to obtain JDBC Connection") whose actual cause is only in the cause chain.
            LOG.warn("Pipeline run failed: pipeline_id={} key={} error_code={} error={}",
                    definition.pipelineId(), request.key(), errorCodeOf(e), e.getMessage(), e);
            if (e instanceof Error error) {
                throw error;
            }
        } finally {
            span.end();
            if (span.shouldCommit()) {
                // What this run was for. The key identifies the profile and the scope the pipeline's
                // unit of work -- the event type for an advisor batch, empty for a once-per-profile
                // run -- which is what tells two runs of the same pipeline apart in the trace list.
                span.attributes = Json.toString(Map.of(
                        "key", String.valueOf(request.key()),
                        "scopeId", request.scopeId()));
                span.commit();
            }
            tracked.worker = null;
            // Absorb a cancellation interrupt that landed after the work returned, so storing the
            // terminal result below is not sabotaged by a flag meant for the work.
            Thread.interrupted();
            slots.release();
            notifyFinished(request, run);
        }
    }

    /**
     * Hands the terminal result to the caller. Anything thrown here is logged and dropped: failing to
     * record a run is worth a warning, not worth turning a completed run into a crashed one.
     */
    private void notifyFinished(PipelineRunRequest<K> request, PipelineRun run) {
        if (request.onFinished() == null) {
            return;
        }
        try {
            request.onFinished().accept(run.result(clock.instant()));
        } catch (RuntimeException e) {
            LOG.warn("Failed to store pipeline run result: pipeline_id={} key={} error={}",
                    definition.pipelineId(), request.key(), e.getMessage());
        }
    }

    /**
     * Jeffrey's own exceptions carry a code the frontend reacts to (the heap dump turns one into a
     * repair prompt). Anything else has no code, which is honest — a message is all we have.
     */
    private static String errorCodeOf(Throwable e) {
        return e instanceof JeffreyException jeffreyException ? jeffreyException.getCode().name() : null;
    }

    private void evictFinishedRuns() {
        Instant cutoff = clock.instant().minus(options.completedRunTtl());
        runsByKey.values().removeIf(tracked -> {
            Instant completedAt = tracked.run.completedAt();
            return completedAt != null && completedAt.isBefore(cutoff);
        });
    }

    /**
     * Pairs a run with the thread executing it, so cancellation can interrupt the actual work.
     * A {@code CompletableFuture} would not do here: its {@code cancel(true)} never interrupts the
     * running task — {@code mayInterruptIfRunning} is documented to have no effect.
     */
    private static final class TrackedRun {

        private final PipelineRun run;
        private volatile Thread worker;

        private TrackedRun(PipelineRun run) {
            this.run = run;
        }
    }
}
