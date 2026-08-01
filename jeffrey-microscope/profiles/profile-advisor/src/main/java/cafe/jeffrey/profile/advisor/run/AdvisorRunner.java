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

import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRequest;
import cafe.jeffrey.profile.common.pipeline.PipelineRunResult;
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Starts advisor batch runs — one launch processes every requested event type for a profile — and
 * remembers the most recent batch per profile so the UI can poll it.
 *
 * <p>The per-type run machinery is {@link PipelineRunRegistry}, shared with heap-dump initialization:
 * the slot ceiling, the TTL on finished runs, cancellation and the terminal-result callback all live
 * there. What stays here is the batch, which is genuinely the Advisor's own idea.</p>
 *
 * <p>Two limits are enforced, for different reasons. <strong>One batch per profile</strong> exists
 * because a second batch would overwrite the first one's stored results with near-identical answers,
 * which is a waste of money rather than a race; it is a per-profile question, so it is answered here.
 * <strong>A global ceiling</strong> exists because each per-type run holds a frontier-model call open
 * for minutes; without it the types of even a single batch would hit the provider's rate limit and
 * degrade together, so the types drain through the registry's shared slots a few at a time. A queued
 * type says so instead of appearing stuck.</p>
 */
public class AdvisorRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdvisorRunner.class);

    private static final Duration COMPLETED_RUN_TTL = Duration.ofMinutes(15);

    private final Map<String, BatchAdvisorRun> batchesByProfileId = new ConcurrentHashMap<>();
    private final PipelineRunRegistry<AdvisorRunKey> registry;
    private final AdvisorServiceFactory advisorServiceFactory;
    private final AdvisorRunResultWriter runResultWriter;

    public AdvisorRunner(
            AdvisorServiceFactory advisorServiceFactory,
            AdvisorRunResultWriter runResultWriter,
            int maxConcurrentRuns,
            Clock clock) {

        this.advisorServiceFactory = advisorServiceFactory;
        this.runResultWriter = runResultWriter;
        this.registry = new PipelineRunRegistry<>(
                AdvisorStages.DEFINITION,
                PipelineRunOptions.bounded(maxConcurrentRuns, COMPLETED_RUN_TTL),
                clock);
    }

    /**
     * Starts a batch that analyzes every {@code target} for {@code profile}, unless a batch is already in
     * flight for the same profile. Each type is scheduled on its own slot, so they drain a few at a time.
     *
     * @return true when a batch was started, false when one was already running
     */
    public boolean startBatch(ProfileInfo profile, List<AdvisorTarget> targets) {
        // The candidate is built up front so the outcome can be decided by identity: if compute() gave
        // back anything else, an in-flight batch kept its place and this call started nothing.
        BatchAdvisorRun candidate = new BatchAdvisorRun(profile.id(), targets);
        BatchAdvisorRun current = batchesByProfileId.compute(profile.id(), (_, existing) ->
                existing != null && existing.isRunning() ? existing : candidate);

        if (current != candidate) {
            LOG.debug("Advisor batch already in flight for this profile: profile_id={}", profile.id());
            return false;
        }

        LOG.info("Queued advisor batch: profile_id={} event_types={}", profile.id(), targets.size());

        for (AdvisorTarget target : targets) {
            startType(profile, candidate, target);
        }
        return true;
    }

    public BatchAdvisorProgress batchProgress(String profileId) {
        BatchAdvisorRun batch = batchesByProfileId.get(profileId);
        return batch != null ? batch.progress() : BatchAdvisorProgress.idle(profileId);
    }

    public boolean cancel(String profileId) {
        BatchAdvisorRun batch = batchesByProfileId.get(profileId);
        if (batch == null || !batch.isRunning()) {
            return false;
        }
        for (AdvisorTarget target : batch.targets()) {
            registry.cancel(AdvisorRunKey.of(target));
        }
        LOG.info("Cancelled advisor batch: profile_id={}", profileId);
        return true;
    }

    /**
     * Schedules one event type and attaches its run to the batch. The registry creates the run
     * synchronously, so it is attached before this returns and a poll sees the type as queued at worst,
     * never as missing.
     */
    private void startType(ProfileInfo profile, BatchAdvisorRun batch, AdvisorTarget target) {
        AdvisorRunKey key = AdvisorRunKey.of(target);

        registry.start(new PipelineRunRequest<>(
                key,
                target.eventType(),
                run -> {
                    AdvisorRun advisorRun = new AdvisorRun(target, run);
                    advisorServiceFactory.apply(profile).generate(target, advisorRun);
                    advisorRun.finish();
                },
                result -> {
                    batch.record(target, result);
                    persistIfComplete(profile, batch);
                }));

        registry.find(key).ifPresent(run -> batch.attach(new AdvisorRun(target, run)));
    }

    /**
     * Stores the batch's timeline once every type has settled — claimed by whichever worker finishes
     * last. A batch where no type produced findings (all failed or cancelled) is not persisted, so a
     * failed re-run never clobbers a good previous result.
     */
    private void persistIfComplete(ProfileInfo profile, BatchAdvisorRun batch) {
        if (!batch.tryClaimCompletion()) {
            return;
        }
        List<PipelineRunResult> results = batch.results();
        boolean anyCompleted = results.stream()
                .anyMatch(result -> result.state() == PipelineState.COMPLETED);
        if (!anyCompleted) {
            return;
        }
        try {
            runResultWriter.store(profile, results);
        } catch (Exception e) {
            LOG.warn("Failed to store advisor run result: profile_id={} error={}", profile.id(), e.getMessage());
        }
    }
}
