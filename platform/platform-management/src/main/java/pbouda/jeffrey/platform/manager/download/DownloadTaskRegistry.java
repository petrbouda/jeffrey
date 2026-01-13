/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.manager.download;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.Schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Global singleton registry for download tasks.
 * Tasks are stored in memory and automatically cleaned up after completion.
 */
public final class DownloadTaskRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(DownloadTaskRegistry.class);

    /**
     * Time to keep completed tasks before cleanup.
     */
    private static final Duration COMPLETED_TASK_TTL = Duration.ofMinutes(5);

    /**
     * Interval for cleanup of completed tasks.
     */
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

    private static final DownloadTaskRegistry INSTANCE = new DownloadTaskRegistry();

    private final Map<String, DownloadTask> tasks = new ConcurrentHashMap<>();
    private final Clock clock = Clock.systemUTC();

    private DownloadTaskRegistry() {
        // Schedule periodic cleanup of completed tasks
        Schedulers.sharedSingleScheduled().scheduleAtFixedRate(
                this::cleanupCompletedTasks,
                CLEANUP_INTERVAL.toMillis(),
                CLEANUP_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    public static DownloadTaskRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Registers a new task in the registry.
     */
    public void register(DownloadTask task) {
        tasks.put(task.getTaskId(), task);
        LOG.info("Registered download task: taskId={}", task.getTaskId());
    }

    /**
     * Gets a task by its ID.
     */
    public Optional<DownloadTask> getTask(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    /**
     * Gets the current progress for a task.
     */
    public Optional<DownloadProgress> getProgress(String taskId) {
        return getTask(taskId).map(DownloadTask::getCurrentProgress);
    }

    /**
     * Cancels a task.
     *
     * @return true if cancelled, false if not found or already terminal
     */
    public boolean cancelTask(String taskId) {
        DownloadTask task = tasks.get(taskId);
        if (task == null) {
            return false;
        }

        if (task.getCurrentProgress().status().isTerminal()) {
            return false;
        }

        task.cancel();
        LOG.info("Cancelled download task: taskId={}", taskId);
        return true;
    }

    /**
     * Adds a progress listener to a task.
     */
    public void addProgressListener(String taskId, Consumer<DownloadProgress> listener) {
        DownloadTask task = tasks.get(taskId);
        if (task != null) {
            task.addProgressListener(listener);
        }
    }

    /**
     * Removes a progress listener from a task.
     */
    public void removeProgressListener(String taskId, Consumer<DownloadProgress> listener) {
        DownloadTask task = tasks.get(taskId);
        if (task != null) {
            task.removeProgressListener(listener);
        }
    }

    /**
     * Returns the clock used for timestamps.
     */
    public Clock getClock() {
        return clock;
    }

    /**
     * Removes completed tasks that are older than the TTL.
     */
    private void cleanupCompletedTasks() {
        Instant cutoff = clock.instant().minus(COMPLETED_TASK_TTL);
        Iterator<Map.Entry<String, DownloadTask>> iterator = tasks.entrySet().iterator();

        int cleaned = 0;
        while (iterator.hasNext()) {
            Map.Entry<String, DownloadTask> entry = iterator.next();
            DownloadTask task = entry.getValue();
            DownloadProgress progress = task.getCurrentProgress();

            if (progress.status().isTerminal() && progress.completedAt() != null) {
                if (progress.completedAt().isBefore(cutoff)) {
                    iterator.remove();
                    cleaned++;
                }
            }
        }

        if (cleaned > 0) {
            LOG.debug("Cleaned up {} completed download tasks", cleaned);
        }
    }
}
