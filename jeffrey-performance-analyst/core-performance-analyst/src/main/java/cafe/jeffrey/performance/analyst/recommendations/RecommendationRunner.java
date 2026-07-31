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

package cafe.jeffrey.performance.analyst.recommendations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.common.Schedulers;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Semaphore;

/**
 * Runs recommendation generations with a ceiling on how many happen at once.
 *
 * <p>Each generation clones a repository onto disk and holds a frontier-model call open for minutes.
 * Without a limit, a handful of simultaneous requests is enough to fill the disk or the provider's rate
 * limit, and every run degrades together. A bounded gate turns that into a queue, which is both
 * survivable and honest — a queued task says so instead of appearing stuck.</p>
 */
public class RecommendationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(RecommendationRunner.class);

    private final RecordingRecommendationManager recommendationManager;
    private final Semaphore slots;
    private final int maxConcurrentRuns;

    public RecommendationRunner(RecordingRecommendationManager recommendationManager, int maxConcurrentRuns) {
        if (maxConcurrentRuns < 1) {
            throw new IllegalArgumentException("At least one concurrent run must be allowed: " + maxConcurrentRuns);
        }
        this.recommendationManager = recommendationManager;
        this.maxConcurrentRuns = maxConcurrentRuns;
        this.slots = new Semaphore(maxConcurrentRuns, true);
    }

    /**
     * Queues {@code task} and returns immediately. The returned future completes when the run finishes;
     * the task itself carries the outcome, so callers watch it rather than the future.
     */
    public CompletableFuture<Void> submit(RecommendationTarget target, RecommendationTask task) {
        LOG.info("Queued recommendation task: task_id={} recording_id={} available_slots={} max_concurrent_runs={}",
                task.taskId(), task.recordingId(), slots.availablePermits(), maxConcurrentRuns);

        return CompletableFuture.runAsync(() -> run(target, task), Schedulers.sharedVirtual());
    }

    private void run(RecommendationTarget target, RecommendationTask task) {
        try {
            slots.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.failed("Cancelled while waiting for a generation slot");
            return;
        }

        try {
            task.started();
            task.completed(recommendationManager.generate(target, task));
        } catch (Exception e) {
            LOG.warn("Recommendation task failed: task_id={} recording_id={} error={}",
                    task.taskId(), task.recordingId(), e.getMessage());
            task.failed(e.getMessage());
        } finally {
            slots.release();
        }
    }
}
