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

import cafe.jeffrey.profile.common.pipeline.PipelineProgress;
import cafe.jeffrey.profile.common.pipeline.PipelineRun;
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import cafe.jeffrey.profile.common.pipeline.StageProgress;
import cafe.jeffrey.profile.common.pipeline.StageStatus;

import java.util.List;

/**
 * One advisor run for a profile and sample event type, expressed in the Advisor's own vocabulary.
 *
 * <p>The bookkeeping underneath — the per-step timing, the live timer, the terminal state — is a
 * {@link PipelineRun}, shared with heap-dump initialization. What lives here is the translation: the
 * service announces phases by name through {@link AdvisorProgressSink}, and the UI reads
 * {@link AdvisorProgress}; neither should have to know about a generic pipeline, and the pipeline should
 * not have to know what "grounding" means.</p>
 *
 * <p>The run holds no findings. Once it completes, the artifacts live in the profile database and the UI
 * reads them from there — which means a page opened after the run finished sees exactly what a page
 * that watched it live sees.</p>
 */
public final class AdvisorRun implements AdvisorProgressSink {

    private final AdvisorTarget target;
    private final PipelineRun run;

    AdvisorRun(AdvisorTarget target, PipelineRun run) {
        this.target = target;
        this.run = run;
    }

    public AdvisorTarget target() {
        return target;
    }

    public boolean isRunning() {
        return run.isRunning();
    }

    @Override
    public void preparingPrompt() {
        run.advanceTo(AdvisorStatus.PREPARING_PROMPT.name());
    }

    @Override
    public void resolvingSource() {
        run.advanceTo(AdvisorStatus.RESOLVING_SOURCE.name());
    }

    @Override
    public void analyzing() {
        run.advanceTo(AdvisorStatus.ANALYZING.name());
    }

    @Override
    public void grounding() {
        run.advanceTo(AdvisorStatus.GROUNDING.name());
    }

    /**
     * Closes the last step. The sink only ever says which phase is <em>starting</em>, so without this the
     * final step would keep a running clock forever after the work returned.
     */
    void finish() {
        run.completeActiveStage();
    }

    public AdvisorProgress progress() {
        PipelineProgress progress = run.progress();
        AdvisorStatus status = statusOf(progress);

        return new AdvisorProgress(
                target.profileId(),
                target.eventType(),
                status,
                status.message(),
                progress.errorMessage(),
                run.startedAt(),
                run.completedAt(),
                progress.stages().stream().map(AdvisorRun::toStep).toList());
    }

    /**
     * Collapses the pipeline's state plus its in-flight stage into the single status the UI shows. A run
     * that exists but has not begun a stage is {@link AdvisorStatus#QUEUED} — which is exactly what it
     * is, waiting on a generation slot — so waiting needs no state of its own.
     */
    private static AdvisorStatus statusOf(PipelineProgress progress) {
        if (progress.state() == PipelineState.COMPLETED) {
            return AdvisorStatus.COMPLETED;
        }
        if (progress.state() == PipelineState.FAILED) {
            return AdvisorStatus.FAILED;
        }
        return progress.stages().stream()
                .filter(stage -> stage.status() == StageStatus.IN_PROGRESS)
                .map(stage -> AdvisorStatus.valueOf(stage.id()))
                .findFirst()
                .orElse(AdvisorStatus.QUEUED);
    }

    private static AdvisorStepProgress toStep(StageProgress stage) {
        return new AdvisorStepProgress(
                stage.id(), stage.status().code(), stage.durationMs(), stage.elapsedMs());
    }

    /**
     * What a type in a just-launched batch reads as before its run has been registered. The window is a
     * few microseconds wide, but a poll that lands in it should say "queued" rather than miss the type.
     */
    static AdvisorProgress queued(AdvisorTarget target) {
        List<AdvisorStepProgress> steps = AdvisorStages.STAGE_IDS.stream()
                .map(id -> new AdvisorStepProgress(id, AdvisorStepProgress.PENDING, null, null))
                .toList();

        return new AdvisorProgress(
                target.profileId(),
                target.eventType(),
                AdvisorStatus.QUEUED,
                AdvisorStatus.QUEUED.message(),
                null,
                null,
                null,
                steps);
    }
}
