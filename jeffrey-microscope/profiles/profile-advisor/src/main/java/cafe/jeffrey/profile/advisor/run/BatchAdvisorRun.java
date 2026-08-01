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
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One launch of the Advisor that processes every requested event type for a profile. It owns a
 * {@link AdvisorRun} per type — each its own pipeline, scheduled independently by {@link AdvisorRunner}
 * through the shared slot semaphore — and aggregates them into a {@link BatchAdvisorProgress} the UI
 * renders as a timeline.
 *
 * <p>The batch holds no result. Each type stores its own artifacts in the profile database as it
 * finishes, so the findings are read from there; the batch answers only "where is each type up to".</p>
 */
public class BatchAdvisorRun {

    private final String profileId;
    private final List<AdvisorRun> runs;
    private final AtomicBoolean completionClaimed = new AtomicBoolean(false);

    public BatchAdvisorRun(String profileId, List<AdvisorTarget> targets, Clock clock) {
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("A batch run needs at least one event type");
        }
        this.profileId = profileId;
        this.runs = targets.stream().map(target -> new AdvisorRun(target, clock)).toList();
    }

    /** The per-type runs, so the runner can schedule each one on its own slot. */
    public List<AdvisorRun> runs() {
        return runs;
    }

    public boolean isRunning() {
        return runs.stream().anyMatch(AdvisorRun::isRunning);
    }

    public void cancel() {
        runs.forEach(AdvisorRun::cancel);
    }

    /**
     * Returns true exactly once — to the first caller that observes every type settled. Each per-type
     * worker calls this as it finishes, so the last one to complete claims the batch and persists the
     * result; the rest get false.
     */
    public boolean tryClaimCompletion() {
        return !isRunning() && completionClaimed.compareAndSet(false, true);
    }

    public BatchAdvisorProgress progress() {
        List<AdvisorProgress> types = runs.stream().map(AdvisorRun::progress).toList();
        int total = types.size();
        int done = (int) types.stream().filter(BatchAdvisorRun::isTerminal).count();
        int pct = total > 0 ? Math.round((done * 100f) / total) : 0;

        return new BatchAdvisorProgress(
                profileId,
                overallStatus(types, done, total),
                done,
                total,
                pct,
                earliestStart(types),
                done == total ? latestCompletion(types) : null,
                types);
    }

    private static BatchStatus overallStatus(List<AdvisorProgress> types, int done, int total) {
        if (done == total) {
            boolean allFailed = types.stream().allMatch(p -> p.status() == AdvisorStatus.FAILED);
            return allFailed ? BatchStatus.FAILED : BatchStatus.COMPLETED;
        }
        boolean anyStarted = types.stream().anyMatch(p -> p.status() != AdvisorStatus.QUEUED);
        return anyStarted ? BatchStatus.RUNNING : BatchStatus.QUEUED;
    }

    private static boolean isTerminal(AdvisorProgress progress) {
        return progress.status() != null && progress.status().isTerminal();
    }

    private static Instant earliestStart(List<AdvisorProgress> types) {
        return types.stream()
                .map(AdvisorProgress::startedAt)
                .filter(instant -> instant != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static Instant latestCompletion(List<AdvisorProgress> types) {
        return types.stream()
                .map(AdvisorProgress::completedAt)
                .filter(instant -> instant != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
