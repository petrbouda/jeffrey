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

import cafe.jeffrey.shared.common.measure.Elapsed;
import cafe.jeffrey.shared.common.measure.Measuring;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One in-flight pipeline run: the per-stage bookkeeping behind a progress endpoint.
 *
 * <p>Mutable, and deliberately so — a run is a moving thing. The concurrency rule is that
 * <strong>all stage mutations happen on the single pipeline thread</strong> while any number of request
 * threads call {@link #progress()}. That is why the stage map is guarded but the scalar fields are
 * merely volatile: there is one writer, so a snapshot only has to be internally consistent, not
 * serialised against a second mutator.</p>
 *
 * <p>The live timer is the subtle part. {@link #liveTimerStageId} points at <em>exactly one</em> stage,
 * so only that stage reports a running clock. Without that, a pipeline that marks several stages
 * in-progress at once (as the heap dump's index group does while sub-phases arrive) would show the
 * same elapsed time on all of them, which reads as three stages each taking the whole build.</p>
 */
public final class PipelineRun {

    private final PipelineDefinition definition;
    private final String scopeId;
    private final Clock clock;
    private final Instant startedAt;
    private final Map<String, StageProgress> stages = new LinkedHashMap<>();

    private volatile PipelineState state = PipelineState.RUNNING;
    private volatile Instant completedAt;
    private volatile String errorCode;
    private volatile String errorMessage;
    /** The single stage the live timer currently points at; {@code null} between stages. */
    private volatile String liveTimerStageId;
    /** Start of {@link #liveTimerStageId}; {@code null} between stages. */
    private volatile Instant liveTimerStartedAt;

    public PipelineRun(PipelineDefinition definition, String scopeId, Clock clock) {
        this.definition = definition;
        this.scopeId = scopeId == null ? "" : scopeId;
        this.clock = clock;
        this.startedAt = clock.instant();
        synchronized (stages) {
            for (String id : definition.stageIds()) {
                stages.put(id, StageProgress.pending(id));
            }
        }
    }

    public PipelineDefinition definition() {
        return definition;
    }

    public String scopeId() {
        return scopeId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    /** When the run reached a terminal state, or {@code null} while it is still going. */
    public Instant completedAt() {
        return completedAt;
    }

    public boolean isRunning() {
        return state == PipelineState.RUNNING;
    }

    /**
     * Runs one stage, timing it and marking it failed if it throws. The exception propagates: a stage
     * that fails ends the run, and the caller decides what that means.
     */
    public void runStage(String id, StageWork work) {
        beginStage(id);
        try {
            Elapsed<List<SubPhaseTiming>> elapsed = Measuring.s(work::run);
            completeStage(id, elapsed.duration().toMillis(), elapsed.entity());
        } catch (RuntimeException e) {
            updateStage(id, StageStatus.FAILED, null, null);
            throw e;
        } finally {
            clearActiveStage();
        }
    }

    /**
     * Runs a stage with nothing to report but its duration — the common case.
     */
    public void runStage(String id, Runnable work) {
        runStage(id, () -> {
            work.run();
            return null;
        });
    }

    /**
     * Marks {@code id} in-progress and points the live timer at it, so only this stage shows a running
     * elapsed time until it is completed or another {@code beginStage} moves the timer on.
     *
     * <p>Public alongside {@link #runStage} because a pipeline whose stages are driven by an external
     * progress callback — rather than by wrapping a callable — has to advance them itself. The heap
     * dump's index build is one atomic operation surfaced as three stages, and it is only able to
     * complete a stage at its real phase boundary because it can call this directly.</p>
     */
    /**
     * Opens a stage and starts the live timer on it — unless the run has already ended.
     *
     * <p>The guard is what stops a cancelled run from coming back to life. Cancellation marks the run
     * failed and interrupts its worker, but whether the work notices the interrupt is up to the work:
     * a callback-driven pipeline whose HTTP call was already in flight keeps going and announces its
     * next phase on the way out. Without this, that announcement would open a fresh stage and restart
     * the timer on a terminal run, leaving the timeline with a stage spinning forever underneath a
     * "Run failed" heading — and the stages after it reading as work that succeeded after the cancel.</p>
     */
    public synchronized void beginStage(String id) {
        if (state != PipelineState.RUNNING) {
            return;
        }
        liveTimerStageId = id;
        liveTimerStartedAt = clock.instant();
        updateStage(id, StageStatus.IN_PROGRESS, null, null);
    }

    public void completeStage(String id, long durationMs, List<SubPhaseTiming> subPhases) {
        updateStage(id, StageStatus.COMPLETED, durationMs, subPhases);
    }

    /**
     * Closes whichever stage the live timer is on — timing it <em>from the timer</em> — and opens
     * {@code id}.
     *
     * <p>This is the third way to drive a pipeline, for the case the other two do not cover: work that
     * announces each phase as it enters it, through a callback, without ever saying when a phase ended.
     * {@link #runStage} needs to own the call, and {@link #completeStage} needs a caller who already
     * knows the duration; a pipeline told only "now analyzing" knows neither, but the boundary is
     * exactly where the announcement lands, which is what this measures.</p>
     */
    public void advanceTo(String id) {
        completeActiveStage();
        beginStage(id);
    }

    /**
     * Closes the stage the live timer is on, if any, timing it from the timer. Called at the end of a
     * callback-driven pipeline so its last stage gets a duration rather than being left in progress.
     */
    public void completeActiveStage() {
        String activeId = liveTimerStageId;
        Instant activeStartedAt = liveTimerStartedAt;
        if (activeId == null || activeStartedAt == null) {
            return;
        }
        completeStage(activeId, Duration.between(activeStartedAt, clock.instant()).toMillis(), null);
        clearActiveStage();
    }

    /**
     * Records a stage that had nothing to do. Kept distinct from completed so a reader can tell "ran and
     * found nothing" from "ran and did the work".
     */
    public void skipStage(String id) {
        updateStage(id, StageStatus.SKIPPED, null, null);
    }

    public void failStages(List<String> ids) {
        for (String id : ids) {
            updateStage(id, StageStatus.FAILED, null, null);
        }
        clearActiveStage();
    }

    /** Stops the live timer once a stage (or stage group) has finished. */
    public void clearActiveStage() {
        liveTimerStageId = null;
        liveTimerStartedAt = null;
    }

    /**
     * Ends the run as succeeded. Normally called by {@link PipelineRunRegistry} once the pipeline body
     * returns; public because a run is a complete object in its own right, and a caller that schedules
     * its own work should not have to go through the registry to say the work finished.
     *
     * <p>The first terminal transition wins: a run cancelled while its work was still unwinding must
     * stay failed when that work eventually returns, not flip to completed as if the cancel never
     * happened. Synchronized because completion and cancellation genuinely race — the worker thread
     * calls this, a request thread calls {@link #fail}.</p>
     *
     * @return true when this call ended the run, false when it was already terminal
     */
    public synchronized boolean complete() {
        if (state != PipelineState.RUNNING) {
            return false;
        }
        completedAt = clock.instant();
        state = PipelineState.COMPLETED;
        clearActiveStage();
        return true;
    }

    /**
     * Ends the run as failed, marking whichever stage was still in flight as failed too. First terminal
     * transition wins, as with {@link #complete}.
     *
     * <p>Marking the in-flight stage matters for callback-driven pipelines. {@link #runStage} marks its
     * own stage failed on the way out, but a pipeline driven through {@link #advanceTo} is only ever
     * told which phase is <em>starting</em> — so without this, the phase that was running when the work
     * blew up would keep a spinning clock forever instead of reading as the one that broke.</p>
     *
     * @return true when this call ended the run, false when it was already terminal
     */
    public synchronized boolean fail(String code, String message) {
        if (state != PipelineState.RUNNING) {
            return false;
        }
        String activeId = liveTimerStageId;
        if (activeId != null) {
            updateStage(activeId, StageStatus.FAILED, null, null);
        }
        errorCode = code;
        errorMessage = message;
        completedAt = clock.instant();
        state = PipelineState.FAILED;
        clearActiveStage();
        return true;
    }

    public PipelineProgress progress() {
        String timerStageId = liveTimerStageId;
        Instant timerStartedAt = liveTimerStartedAt;
        List<StageProgress> ordered;
        synchronized (stages) {
            ordered = stages.values().stream()
                    .map(stage -> withLiveElapsed(stage, timerStageId, timerStartedAt))
                    .toList();
        }
        return new PipelineProgress(
                definition.pipelineId(), scopeId, state, errorCode, errorMessage, ordered);
    }

    PipelineRunResult result(Instant completedAt) {
        return PipelineRunResult.from(progress(), startedAt, completedAt, definition.stageCount());
    }

    /**
     * Stamps the live elapsed time onto the single active (timed) in-progress stage so a polling (or
     * reconnecting) frontend can render the stage timer from the backend's clock instead of restarting
     * it from zero. Only the stage the timer currently points at is stamped, so concurrently-visible
     * in-progress stages don't all show the same elapsed time.
     */
    private StageProgress withLiveElapsed(
            StageProgress stage, String timerStageId, Instant timerStartedAt) {

        if (stage.status() != StageStatus.IN_PROGRESS
                || timerStartedAt == null
                || !stage.id().equals(timerStageId)) {
            return stage;
        }
        long elapsedMs = Duration.between(timerStartedAt, clock.instant()).toMillis();
        return new StageProgress(
                stage.id(), stage.status(), stage.durationMs(), elapsedMs, stage.subPhases());
    }

    private void updateStage(String id, StageStatus status, Long durationMs, List<SubPhaseTiming> subPhases) {
        synchronized (stages) {
            if (!stages.containsKey(id)) {
                throw new IllegalArgumentException(
                        "Stage is not part of pipeline " + definition.pipelineId() + ": " + id);
            }
            stages.put(id, new StageProgress(id, status, durationMs, null, subPhases));
        }
    }

    /**
     * A stage's body. Returns the sub-phase timings to show under the stage, or {@code null} when the
     * stage has no finer instrumentation.
     */
    @FunctionalInterface
    public interface StageWork {

        List<SubPhaseTiming> run();
    }
}
