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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Starts advisor runs, bounds how many happen at once, and remembers the most recent one per profile so
 * the UI can poll it.
 *
 * <p>Two limits are enforced, for different reasons. <strong>One run per profile</strong> exists because
 * a second run would overwrite the first one's stored result with a near-identical answer, which is a
 * waste of money rather than a race. <strong>A global ceiling</strong> exists because each run holds a
 * frontier-model call open for minutes; without it a handful of simultaneous requests is enough to hit
 * the provider's rate limit and degrade every run together. A queued run says so instead of appearing
 * stuck.</p>
 *
 * <p>Finished runs are kept queryable until a short TTL expires, so a page that reloads right after a
 * run completes still learns how it ended.</p>
 */
public class AdvisorRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdvisorRunner.class);

    private static final Duration COMPLETED_RUN_TTL = Duration.ofMinutes(15);
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

    private final Map<String, AdvisorRun> runsByProfileId = new ConcurrentHashMap<>();
    private final AdvisorServiceFactory advisorServiceFactory;
    private final Semaphore slots;
    private final int maxConcurrentRuns;
    private final Clock clock;

    public AdvisorRunner(AdvisorServiceFactory advisorServiceFactory, int maxConcurrentRuns, Clock clock) {
        if (maxConcurrentRuns < 1) {
            throw new IllegalArgumentException(
                    "At least one concurrent run must be allowed: " + maxConcurrentRuns);
        }
        this.advisorServiceFactory = advisorServiceFactory;
        this.maxConcurrentRuns = maxConcurrentRuns;
        this.slots = new Semaphore(maxConcurrentRuns, true);
        this.clock = clock;

        Schedulers.sharedSingleScheduled().scheduleAtFixedRate(
                this::evictFinishedRuns,
                CLEANUP_INTERVAL.toMillis(),
                CLEANUP_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Starts a run for {@code profile} and {@code eventType} unless one is already in flight for the
     * same profile.
     *
     * @return true when a run was started, false when one was already running
     */
    public boolean start(ProfileInfo profile, String eventType) {
        return start(profile, AdvisorTarget.of(profile, eventType));
    }

    private boolean start(ProfileInfo profile, AdvisorTarget target) {
        // The candidate is built up front so the outcome can be decided by identity: if compute() gave
        // back anything else, an in-flight run kept the slot and this call started nothing.
        AdvisorRun candidate = new AdvisorRun(target, clock);
        AdvisorRun current = runsByProfileId.compute(target.profileId(), (_, existing) ->
                existing != null && existing.isRunning() ? existing : candidate);

        if (current != candidate) {
            LOG.debug("Advisor run already in flight for this profile: profile_id={} event_type={}",
                    target.profileId(), current.target().eventType());
            return false;
        }

        LOG.info("Queued advisor run: profile_id={} event_type={} available_slots={} max_concurrent_runs={}",
                target.profileId(), target.eventType(), slots.availablePermits(), maxConcurrentRuns);

        candidate.attach(CompletableFuture.runAsync(
                () -> execute(profile, candidate), Schedulers.sharedVirtual()));
        return true;
    }

    public Optional<AdvisorRun> find(String profileId) {
        return Optional.ofNullable(runsByProfileId.get(profileId));
    }

    public AdvisorProgress progress(String profileId) {
        return find(profileId).map(AdvisorRun::progress).orElseGet(() -> AdvisorProgress.idle(profileId));
    }

    public boolean cancel(String profileId) {
        AdvisorRun run = runsByProfileId.get(profileId);
        if (run == null || !run.isRunning()) {
            return false;
        }
        run.cancel();
        LOG.info("Cancelled advisor run: profile_id={}", profileId);
        return true;
    }

    private void execute(ProfileInfo profile, AdvisorRun run) {
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            run.failed("Cancelled while waiting for a generation slot");
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
        }
    }

    private void evictFinishedRuns() {
        Instant cutoff = clock.instant().minus(COMPLETED_RUN_TTL);
        runsByProfileId.values().removeIf(run -> {
            Instant completedAt = run.progress().completedAt();
            return completedAt != null && completedAt.isBefore(cutoff);
        });
    }
}
