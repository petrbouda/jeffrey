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
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRequest;
import cafe.jeffrey.profile.common.pipeline.PipelineRunResult;
import cafe.jeffrey.provider.profile.api.PipelineRunRepository;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * Starts Advisor runs and answers where the current one has got to.
 *
 * <p>All the bookkeeping is {@link PipelineRunRegistry}, shared with heap-dump initialization. What is
 * Advisor-specific is the two options it configures, and both are about the resource a run holds rather
 * than about taste:</p>
 *
 * <ul>
 *   <li>A <strong>ceiling</strong>, because a run holds a frontier-model call open for minutes; without
 *       one, a handful of concurrent requests hits the provider's rate limit and every run degrades
 *       together. A queued run says so instead of appearing stuck.</li>
 *   <li>A <strong>TTL</strong>, because a finished run is superseded by the stored findings — the page
 *       reads those, so keeping the run snapshot around forever would only hold memory.</li>
 * </ul>
 */
public class AdvisorRunner {

    private static final Duration COMPLETED_RUN_TTL = Duration.ofMinutes(15);

    private final PipelineRunRegistry<String> registry;
    private final AdvisorServiceFactory advisorServiceFactory;
    private final Function<ProfileInfo, PipelineRunRepository> pipelineRunsFactory;

    public AdvisorRunner(
            AdvisorServiceFactory advisorServiceFactory,
            Function<ProfileInfo, PipelineRunRepository> pipelineRunsFactory,
            int maxConcurrentRuns,
            Clock clock) {

        this.advisorServiceFactory = advisorServiceFactory;
        this.pipelineRunsFactory = pipelineRunsFactory;
        this.registry = new PipelineRunRegistry<>(
                AdvisorStages.DEFINITION,
                PipelineRunOptions.bounded(maxConcurrentRuns, COMPLETED_RUN_TTL),
                clock);
    }

    /**
     * Starts a run for {@code profile} and {@code eventType} unless one is already in flight for the
     * same profile.
     *
     * @return true when a run was started, false when one was already running
     */
    public boolean start(ProfileInfo profile, String eventType) {
        AdvisorTarget target = AdvisorTarget.of(profile, eventType);
        PipelineRunRepository pipelineRuns = pipelineRunsFactory.apply(profile);

        return registry.start(new PipelineRunRequest<>(
                profile.id(),
                eventType,
                run -> advisorServiceFactory.apply(profile).generate(target, run),
                pipelineRuns::upsert));
    }

    public PipelineProgress progress(String profileId) {
        return registry.progress(profileId);
    }

    public boolean isRunning(String profileId) {
        return registry.isRunning(profileId);
    }

    public boolean cancel(String profileId) {
        return registry.cancel(profileId);
    }

    /**
     * The stored runs for a profile, one per event type. Read from the profile database rather than the
     * registry, so a page opened long after a run still shows what it did.
     */
    public List<PipelineRunResult> storedRuns(ProfileInfo profile) {
        return pipelineRunsFactory.apply(profile).findAll(AdvisorStages.PIPELINE_ID);
    }
}
