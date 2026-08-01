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

import cafe.jeffrey.profile.common.pipeline.PipelineRunResult;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One launch of the Advisor that processes every requested event type for a profile. It owns an
 * {@link AdvisorRun} per type — each its own pipeline, scheduled independently by {@link AdvisorRunner}
 * through the shared slot ceiling — and aggregates them into a {@link BatchAdvisorProgress} the UI
 * renders as a timeline.
 *
 * <p>The batch holds its runs by strong reference on purpose. They are also in the pipeline registry,
 * which evicts finished runs after a TTL; if the batch read through to the registry instead, an evicted
 * type would silently read as queued again and the batch would never look finished.</p>
 *
 * <p>The batch holds no result. Each type stores its own artifacts in the profile database as it
 * finishes, so the findings are read from there; the batch answers only "where is each type up to".</p>
 */
public final class BatchAdvisorRun {

    private final String profileId;
    private final List<AdvisorTarget> targets;
    private final Map<AdvisorTarget, AdvisorRun> runs = new ConcurrentHashMap<>();
    private final Map<AdvisorTarget, PipelineRunResult> results = new ConcurrentHashMap<>();
    private final AtomicBoolean completionClaimed = new AtomicBoolean(false);

    public BatchAdvisorRun(String profileId, List<AdvisorTarget> targets) {
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("A batch run needs at least one event type");
        }
        this.profileId = profileId;
        this.targets = List.copyOf(targets);
    }

    public List<AdvisorTarget> targets() {
        return targets;
    }

    /** Registers a type's run once the pipeline registry has created it. */
    void attach(AdvisorRun run) {
        runs.put(run.target(), run);
    }

    /** Keeps a type's terminal result, so the batch can be stored in one go when the last type settles. */
    void record(AdvisorTarget target, PipelineRunResult result) {
        results.put(target, result);
    }

    /** The terminal results collected so far, in the batch's declared order. */
    List<PipelineRunResult> results() {
        return targets.stream().map(results::get).filter(result -> result != null).toList();
    }

    public boolean isRunning() {
        // A batch whose types are still being launched is running by definition — otherwise the guard
        // against a second batch would have a window where it let one through.
        if (runs.size() < targets.size()) {
            return true;
        }
        return runs.values().stream().anyMatch(AdvisorRun::isRunning);
    }

    /**
     * Returns true exactly once — to the first caller that observes every type's terminal result
     * recorded. Each per-type worker records its result and then calls this, so the last one to finish
     * claims the batch and persists it; the rest get false.
     *
     * <p>The claim is decided by <em>recorded results</em>, not by run states, deliberately: a run turns
     * terminal a moment before its worker records the result, so a state-based claim could win while a
     * sibling's result is still in flight — and persist a timeline missing that type.</p>
     */
    public boolean tryClaimCompletion() {
        return results.size() == targets.size() && completionClaimed.compareAndSet(false, true);
    }

    public BatchAdvisorProgress progress() {
        List<AdvisorProgress> types = targets.stream()
                .map(target -> {
                    AdvisorRun run = runs.get(target);
                    return run == null ? AdvisorRun.queued(target) : run.progress();
                })
                .toList();

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

    private static Long earliestStart(List<AdvisorProgress> types) {
        return types.stream()
                .map(AdvisorProgress::startedAt)
                .filter(startedAt -> startedAt != null)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static Long latestCompletion(List<AdvisorProgress> types) {
        return types.stream()
                .map(AdvisorProgress::completedAt)
                .filter(completedAt -> completedAt != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }
}
