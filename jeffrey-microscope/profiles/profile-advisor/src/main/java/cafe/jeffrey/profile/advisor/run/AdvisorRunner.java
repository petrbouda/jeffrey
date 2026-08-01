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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.model.ProfileInfo;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Starts advisor batch runs — one launch processes every requested event type for a profile — bounds how
 * many per-type analyses happen at once, and remembers the most recent batch per profile so the UI can
 * poll it.
 *
 * <p>Two limits are enforced, for different reasons. <strong>One batch per profile</strong> exists
 * because a second batch would overwrite the first one's stored results with near-identical answers,
 * which is a waste of money rather than a race. <strong>A global ceiling</strong> exists because each
 * per-type run holds a frontier-model call open for minutes; without it the types of even a single batch
 * would hit the provider's rate limit and degrade together, so the types drain through the shared slots
 * a few at a time. A queued type says so instead of appearing stuck.</p>
 *
 * <p>Finished batches are kept queryable until a short TTL expires, so a page that reloads right after a
 * batch completes still learns how it ended.</p>
 */
public class AdvisorRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdvisorRunner.class);

    private static final Duration COMPLETED_RUN_TTL = Duration.ofMinutes(15);
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

    private final Map<String, BatchAdvisorRun> batchesByProfileId = new ConcurrentHashMap<>();
    private final AdvisorServiceFactory advisorServiceFactory;
    private final AdvisorRunResultWriter runResultWriter;
    private final Semaphore slots;
    private final int maxConcurrentRuns;
    private final Clock clock;

    public AdvisorRunner(
            AdvisorServiceFactory advisorServiceFactory,
            AdvisorRunResultWriter runResultWriter,
            int maxConcurrentRuns,
            Clock clock) {
        if (maxConcurrentRuns < 1) {
            throw new IllegalArgumentException(
                    "At least one concurrent run must be allowed: " + maxConcurrentRuns);
        }
        this.advisorServiceFactory = advisorServiceFactory;
        this.runResultWriter = runResultWriter;
        this.maxConcurrentRuns = maxConcurrentRuns;
        this.slots = new Semaphore(maxConcurrentRuns, true);
        this.clock = clock;

        Schedulers.sharedSingleScheduled().scheduleAtFixedRate(
                this::evictFinishedBatches,
                CLEANUP_INTERVAL.toMillis(),
                CLEANUP_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS);
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
        BatchAdvisorRun candidate = new BatchAdvisorRun(profile.id(), targets, clock);
        BatchAdvisorRun current = batchesByProfileId.compute(profile.id(), (_, existing) ->
                existing != null && existing.isRunning() ? existing : candidate);

        if (current != candidate) {
            LOG.debug("Advisor batch already in flight for this profile: profile_id={}", profile.id());
            return false;
        }

        LOG.info("Queued advisor batch: profile_id={} event_types={} available_slots={} max_concurrent_runs={}",
                profile.id(), targets.size(), slots.availablePermits(), maxConcurrentRuns);

        for (AdvisorRun run : candidate.runs()) {
            run.attach(CompletableFuture.runAsync(
                    () -> execute(profile, candidate, run), Schedulers.sharedVirtual()));
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
        batch.cancel();
        LOG.info("Cancelled advisor batch: profile_id={}", profileId);
        return true;
    }

    private void execute(ProfileInfo profile, BatchAdvisorRun batch, AdvisorRun run) {
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            run.failed("Cancelled while waiting for a generation slot");
            persistIfComplete(profile, batch);
            return;
        }

        try {
            advisorServiceFactory.apply(profile).generate(run.target(), run);
            run.completed();
        } catch (Exception e) {
            LOG.warn("Advisor run failed: profile_id={} event_type={} error={}",
                    run.target().profileId(), run.target().eventType(), e.getMessage());
            run.failed(e.getMessage());
        } finally {
            slots.release();
            persistIfComplete(profile, batch);
        }
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
        BatchAdvisorProgress progress = batch.progress();
        boolean anyCompleted = progress.types().stream()
                .anyMatch(type -> type.status() == AdvisorStatus.COMPLETED);
        if (!anyCompleted) {
            return;
        }
        Instant completedAt = progress.completedAt() != null ? progress.completedAt() : clock.instant();
        try {
            runResultWriter.store(profile, AdvisorRunResult.from(progress, completedAt));
        } catch (Exception e) {
            LOG.warn("Failed to store advisor run result: profile_id={} error={}", profile.id(), e.getMessage());
        }
    }

    private void evictFinishedBatches() {
        Instant cutoff = clock.instant().minus(COMPLETED_RUN_TTL);
        batchesByProfileId.values().removeIf(batch -> {
            Instant completedAt = batch.progress().completedAt();
            return completedAt != null && completedAt.isBefore(cutoff);
        });
    }
}
