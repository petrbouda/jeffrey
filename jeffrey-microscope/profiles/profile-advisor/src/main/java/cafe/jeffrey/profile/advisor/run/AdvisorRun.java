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

package cafe.jeffrey.profile.advisor.run;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One advisor run for a profile and sample event type. The worker thread drives the step transitions
 * while request threads read {@link #progress()}; the mutable fields are volatile and every read builds
 * a fresh snapshot, so a poll can never observe a half-applied transition.
 *
 * <p>Each of the four pipeline steps (Prompt / Source / Analyze / Ground) is timed against the injected
 * {@link Clock}: entering a step closes the previous one's duration, and the step in progress reports a
 * live {@code elapsedMs} so a reconnecting UI resumes its timer without clock skew. This mirrors the
 * heap-dump init pipeline.</p>
 *
 * <p>The run holds no findings. Once it completes, the artifacts live in the profile database and the UI
 * reads them from there — which means a page opened after the run finished sees exactly what a page
 * that watched it live sees.</p>
 */
public class AdvisorRun implements AdvisorProgressSink {

    private final AdvisorTarget target;
    private final Clock clock;
    private final Instant startedAt;

    private final Map<AdvisorStatus, Long> completedStepMs = new ConcurrentHashMap<>();

    private volatile AdvisorStatus status = AdvisorStatus.QUEUED;
    private volatile String message = AdvisorStatus.QUEUED.message();
    private volatile String errorMessage;
    private volatile Instant completedAt;
    private volatile Instant activeStepStartedAt;
    private volatile AdvisorStatus activeStep;
    private volatile CompletableFuture<Void> future;

    public AdvisorRun(AdvisorTarget target, Clock clock) {
        this.target = target;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    public AdvisorTarget target() {
        return target;
    }

    public void attach(CompletableFuture<Void> future) {
        this.future = future;
    }

    public AdvisorProgress progress() {
        return new AdvisorProgress(target.profileId(), target.eventType(), status, message, errorMessage,
                startedAt, completedAt, steps());
    }

    public boolean isRunning() {
        return !status.isTerminal();
    }

    @Override
    public void preparingPrompt() {
        startStep(AdvisorStatus.PREPARING_PROMPT);
    }

    @Override
    public void resolvingSource() {
        startStep(AdvisorStatus.RESOLVING_SOURCE);
    }

    @Override
    public void analyzing() {
        startStep(AdvisorStatus.ANALYZING);
    }

    @Override
    public void grounding() {
        startStep(AdvisorStatus.GROUNDING);
    }

    public void completed() {
        closeActiveStep();
        this.completedAt = clock.instant();
        setStatus(AdvisorStatus.COMPLETED);
    }

    public void failed(String error) {
        // The active step is left open on purpose: progress() renders it as failed, while the steps that
        // already finished keep their measured durations.
        this.errorMessage = error;
        this.completedAt = clock.instant();
        setStatus(AdvisorStatus.FAILED);
    }

    public void cancel() {
        if (future != null) {
            future.cancel(true);
        }
        failed("Cancelled");
    }

    private synchronized void startStep(AdvisorStatus step) {
        closeActiveStep();
        this.activeStepStartedAt = clock.instant();
        this.activeStep = step;
        setStatus(step);
    }

    private synchronized void closeActiveStep() {
        AdvisorStatus step = activeStep;
        Instant startedAt = activeStepStartedAt;
        if (step != null && startedAt != null) {
            completedStepMs.put(step, Duration.between(startedAt, clock.instant()).toMillis());
        }
        this.activeStep = null;
        this.activeStepStartedAt = null;
    }

    private void setStatus(AdvisorStatus newStatus) {
        this.status = newStatus;
        this.message = newStatus.message();
    }

    private List<AdvisorStepProgress> steps() {
        AdvisorStatus active = activeStep;
        Instant activeStart = activeStepStartedAt;
        boolean failed = status == AdvisorStatus.FAILED;
        Instant now = clock.instant();

        return AdvisorStatus.STEPS.stream()
                .map(step -> {
                    Long duration = completedStepMs.get(step);
                    if (duration != null) {
                        return new AdvisorStepProgress(step.name(), AdvisorStepProgress.COMPLETED, duration, null);
                    }
                    if (step == active) {
                        if (failed) {
                            return new AdvisorStepProgress(step.name(), AdvisorStepProgress.FAILED, null, null);
                        }
                        long elapsed = activeStart != null ? Duration.between(activeStart, now).toMillis() : 0L;
                        return new AdvisorStepProgress(step.name(), AdvisorStepProgress.IN_PROGRESS, null, elapsed);
                    }
                    return new AdvisorStepProgress(step.name(), AdvisorStepProgress.PENDING, null, null);
                })
                .toList();
    }
}
