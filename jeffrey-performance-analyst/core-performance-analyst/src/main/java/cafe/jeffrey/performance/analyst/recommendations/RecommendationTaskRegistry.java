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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * In-memory registry of running and recently-finished {@link RecommendationTask}s, keyed by task id.
 * Completed tasks are evicted after a short TTL so their (potentially large) result markdown does not
 * linger. Mirrors the shared {@code DownloadTaskRegistry} but is registered as a Spring bean rather
 * than a static singleton.
 */
public class RecommendationTaskRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(RecommendationTaskRegistry.class);

    private static final Duration COMPLETED_TASK_TTL = Duration.ofMinutes(15);
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

    private final Map<String, RecommendationTask> tasks = new ConcurrentHashMap<>();
    private final Clock clock;

    public RecommendationTaskRegistry(Clock clock) {
        this.clock = clock;
        Schedulers.sharedSingleScheduled().scheduleAtFixedRate(
                this::cleanupCompletedTasks,
                CLEANUP_INTERVAL.toMillis(),
                CLEANUP_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    public void register(RecommendationTask task) {
        tasks.put(task.taskId(), task);
        LOG.info("Registered recommendation task: taskId={} recordingId={} eventType={}",
                task.taskId(), task.recordingId(), task.eventType());
    }

    public Optional<RecommendationTask> getTask(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    public Optional<RecommendationProgress> getProgress(String taskId) {
        return getTask(taskId).map(RecommendationTask::currentProgress);
    }

    public boolean cancelTask(String taskId) {
        RecommendationTask task = tasks.get(taskId);
        if (task == null) {
            return false;
        }
        if (task.currentProgress().status().isTerminal()) {
            return false;
        }
        task.cancel();
        LOG.info("Cancelled recommendation task: taskId={}", taskId);
        return true;
    }

    private void cleanupCompletedTasks() {
        Instant cutoff = clock.instant().minus(COMPLETED_TASK_TTL);
        Iterator<Map.Entry<String, RecommendationTask>> iterator = tasks.entrySet().iterator();

        int cleaned = 0;
        while (iterator.hasNext()) {
            RecommendationProgress progress = iterator.next().getValue().currentProgress();
            if (progress.status().isTerminal()
                    && progress.completedAt() != null
                    && progress.completedAt().isBefore(cutoff)) {
                iterator.remove();
                cleaned++;
            }
        }

        if (cleaned > 0) {
            LOG.debug("Cleaned up completed recommendation tasks: count={}", cleaned);
        }
    }
}
