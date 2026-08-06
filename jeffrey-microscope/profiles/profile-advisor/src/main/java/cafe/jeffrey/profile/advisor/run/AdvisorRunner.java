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

import cafe.jeffrey.profile.advisor.source.SourceTree;
import cafe.jeffrey.profile.common.pipeline.PipelineRunOptions;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRegistry;
import cafe.jeffrey.profile.common.pipeline.PipelineRunRequest;
import cafe.jeffrey.profile.common.pipeline.PipelineRunResult;
import cafe.jeffrey.profile.common.pipeline.PipelineState;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.model.ProfileInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

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
public final class AdvisorRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdvisorRunner.class);

    private static final Duration COMPLETED_RUN_TTL = Duration.ofMinutes(15);

    private static final Duration BATCH_EVICTION_INTERVAL = Duration.ofMinutes(1);

    private final Map<String, BatchAdvisorRun> batchesByProfileId = new ConcurrentHashMap<>();
    private final PipelineRunRegistry<AdvisorRunKey> registry;
    private final AdvisorServiceFactory advisorServiceFactory;
    private final AdvisorRunResultWriter runResultWriter;
    private final Clock clock;

    public AdvisorRunner(
            AdvisorServiceFactory advisorServiceFactory,
            AdvisorRunResultWriter runResultWriter,
            int maxConcurrentRuns,
            Clock clock) {

        this.advisorServiceFactory = advisorServiceFactory;
        this.runResultWriter = runResultWriter;
        this.clock = clock;
        this.registry = new PipelineRunRegistry<>(
                AdvisorStages.DEFINITION,
                PipelineRunOptions.bounded(maxConcurrentRuns, COMPLETED_RUN_TTL),
                clock);

        // The registry evicts its own runs on the same TTL, but the batch map holds strong references
        // to every run it aggregates — without this sweep a finished batch (and all its runs) would
        // stay for the life of the process, one per profile ever analyzed.
        Schedulers.sharedSingleScheduled().scheduleAtFixedRate(
                this::evictFinishedBatches,
                BATCH_EVICTION_INTERVAL.toMillis(),
                BATCH_EVICTION_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private void evictFinishedBatches() {
        long cutoff = clock.instant().minus(COMPLETED_RUN_TTL).toEpochMilli();
        batchesByProfileId.values().removeIf(batch -> {
            if (batch.isRunning()) {
                return false;
            }
            Long completedAt = batch.progress().completedAt();
            return completedAt != null && completedAt < cutoff;
        });
    }

    /**
     * Starts a batch that analyzes every {@code target} for {@code profile}, unless a batch is already in
     * flight for the same profile. Each type is scheduled on its own slot, so they drain a few at a time.
     *
     * <p>The source folder is resolved <em>once</em>, here, before anything is scheduled. It belongs to
     * the profile rather than to an event type, so validating it per type would re-read the same folder
     * and re-parse the same {@code .git} for every type in the fan-out. Resolving it up front also makes
     * an unusable folder refuse the launch with the reason, at the request the user just made, instead
     * of starting a batch that fails every type identically.</p>
     *
     * @return true when a batch was started, false when one was already running
     * @throws cafe.jeffrey.shared.common.exception.JeffreyClientException when the source folder is
     *         unusable — nothing is scheduled and no batch is remembered
     */
    public boolean startBatch(ProfileInfo profile, List<AdvisorTarget> targets) {
        // Resolved before the batch is claimed below, so a bad folder leaves no batch behind to be
        // reported as failed or to block the next attempt.
        SourceTree sourceTree;
        try (AdvisorService service = advisorServiceFactory.apply(profile)) {
            sourceTree = service.resolveSource();
        }

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
            startType(profile, candidate, target, sourceTree);
        }
        return true;
    }

    public BatchAdvisorProgress batchProgress(String profileId) {
        BatchAdvisorRun batch = batchesByProfileId.get(profileId);
        return batch != null ? batch.progress() : BatchAdvisorProgress.idle(profileId);
    }

    /**
     * Forgets the remembered batch for a profile, so progress reads as "never run" again — unless the
     * batch is still in flight, in which case nothing happens and {@code false} comes back.
     *
     * <p>Needed when the profile's stored results are deleted: the finished batch would otherwise
     * survive and keep answering {@code COMPLETED} for a profile that now has nothing to show, which
     * reads as a successful run whose results vanished.</p>
     *
     * <p>The running check lives inside the map operation rather than at the caller, because the caller
     * cannot make check-then-forget atomic — a run started between the two would be deleted out from
     * under, keep executing invisibly, and let a second batch start for the same profile.</p>
     *
     * @return true when the batch was forgotten (or none existed), false when one is still running
     */
    public boolean forget(String profileId) {
        BatchAdvisorRun kept = batchesByProfileId.computeIfPresent(
                profileId, (_, batch) -> batch.isRunning() ? batch : null);
        if (kept != null) {
            LOG.debug("Refused to forget a running advisor batch: profile_id={}", profileId);
            return false;
        }
        LOG.info("Forgot advisor batch: profile_id={}", profileId);
        return true;
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
    private void startType(
            ProfileInfo profile, BatchAdvisorRun batch, AdvisorTarget target, SourceTree sourceTree) {

        AdvisorRunKey key = AdvisorRunKey.of(target);

        boolean started = registry.start(new PipelineRunRequest<>(
                key,
                target.eventType(),
                run -> {
                    AdvisorRun advisorRun = new AdvisorRun(target, run);
                    // Closed when the type finishes, releasing the pool this run pinned for its
                    // duration — see AdvisorService#close.
                    try (AdvisorService service = advisorServiceFactory.apply(profile)) {
                        service.generate(target, advisorRun, sourceTree);
                    }
                    advisorRun.finish();
                },
                result -> {
                    batch.record(target, result);
                    persistIfComplete(profile, batch);
                }));

        // Should be impossible — the one-batch-per-profile guard means no run of ours is in flight for
        // this key. If it happens anyway, the stale run's onFinished points at the old batch, so this
        // batch will never record the type and never persist; say so instead of failing silently.
        if (!started) {
            LOG.warn("Event type already had a run in flight, batch will not persist: "
                    + "profile_id={} event_type={}", profile.id(), target.eventType());
        }

        registry.find(key).ifPresent(run -> batch.attach(new AdvisorRun(target, run)));
    }

    /**
     * Stores the batch's timeline once every type has settled — claimed by whichever worker finishes
     * last. A batch where no type produced a result (all failed or cancelled) is not persisted, so a
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
