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
import pbouda.jeffrey.platform.manager.RecordingsDownloadManager;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteRecordingsDownloadManager;
import pbouda.jeffrey.shared.common.Schedulers;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Implementation of DownloadTaskManager that maintains an in-memory registry of download tasks.
 * Completed tasks are automatically cleaned up after a TTL period.
 */
public class DownloadTaskManagerImpl implements DownloadTaskManager {

    private static final Logger LOG = LoggerFactory.getLogger(DownloadTaskManagerImpl.class);

    /**
     * Time to keep completed tasks before cleanup.
     */
    private static final Duration COMPLETED_TASK_TTL = Duration.ofMinutes(5);

    /**
     * Interval for cleanup of completed tasks.
     */
    private static final Duration CLEANUP_INTERVAL = Duration.ofMinutes(1);

    private final String workspaceId;
    private final String projectId;
    private final RecordingsDownloadManager recordingsDownloadManager;
    private final Clock clock;
    private final Map<String, DownloadTask> tasks;
    private final ScheduledExecutorService cleanupExecutor;

    public DownloadTaskManagerImpl(
            String workspaceId,
            String projectId,
            RecordingsDownloadManager recordingsDownloadManager,
            Clock clock) {
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.recordingsDownloadManager = recordingsDownloadManager;
        this.clock = clock;
        this.tasks = new ConcurrentHashMap<>();
        this.cleanupExecutor = Schedulers.sharedSingleScheduled();

        // Schedule periodic cleanup of completed tasks
        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupCompletedTasks,
                CLEANUP_INTERVAL.toMillis(),
                CLEANUP_INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public DownloadTask createTask(String sessionId, List<String> fileIds, boolean merge) {
        DownloadTask task = new DownloadTask(workspaceId, projectId, sessionId, fileIds, clock);
        tasks.put(task.getTaskId(), task);
        LOG.info("Created download task: taskId={} sessionId={} fileCount={}",
                task.getTaskId(), sessionId, fileIds.size());
        return task;
    }

    @Override
    public CompletableFuture<Void> startDownload(String taskId) {
        DownloadTask task = tasks.get(taskId);
        if (task == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Task not found: " + taskId));
        }

        // Check if the download manager supports progress tracking
        if (recordingsDownloadManager instanceof RemoteRecordingsDownloadManager remoteManager) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    remoteManager.mergeAndDownloadRecordingsWithProgress(
                            task.getSessionId(),
                            task.getFileIds(),
                            task
                    );
                } catch (Exception e) {
                    LOG.error("Download failed: taskId={} error={}", taskId, e.getMessage(), e);
                    task.onError(e.getMessage());
                    throw e;
                }
            }, Schedulers.sharedVirtual());

            task.setDownloadFuture(future);
            return future;
        } else {
            // For non-remote download managers, execute synchronously without progress
            return CompletableFuture.runAsync(() -> {
                try {
                    task.onStart(task.getFileIds().size(), 0);
                    recordingsDownloadManager.mergeAndDownloadRecordings(
                            task.getSessionId(),
                            task.getFileIds()
                    );
                    task.onComplete();
                } catch (Exception e) {
                    LOG.error("Download failed: taskId={} error={}", taskId, e.getMessage(), e);
                    task.onError(e.getMessage());
                    throw e;
                }
            }, Schedulers.sharedVirtual());
        }
    }

    @Override
    public Optional<DownloadProgress> getProgress(String taskId) {
        return Optional.ofNullable(tasks.get(taskId))
                .map(DownloadTask::getCurrentProgress);
    }

    @Override
    public Optional<DownloadTask> getTask(String taskId) {
        return Optional.ofNullable(tasks.get(taskId));
    }

    @Override
    public boolean cancelDownload(String taskId) {
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

    @Override
    public void addProgressListener(String taskId, Consumer<DownloadProgress> listener) {
        DownloadTask task = tasks.get(taskId);
        if (task != null) {
            task.addProgressListener(listener);
        }
    }

    @Override
    public void removeProgressListener(String taskId, Consumer<DownloadProgress> listener) {
        DownloadTask task = tasks.get(taskId);
        if (task != null) {
            task.removeProgressListener(listener);
        }
    }

    /**
     * Removes completed tasks that are older than the TTL.
     */
    private void cleanupCompletedTasks() {
        Instant cutoff = clock.instant().minus(COMPLETED_TASK_TTL);
        Iterator<Map.Entry<String, DownloadTask>> iterator = tasks.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, DownloadTask> entry = iterator.next();
            DownloadTask task = entry.getValue();
            DownloadProgress progress = task.getCurrentProgress();

            if (progress.status().isTerminal() && progress.completedAt() != null) {
                if (progress.completedAt().isBefore(cutoff)) {
                    iterator.remove();
                    LOG.debug("Cleaned up completed task: taskId={}", entry.getKey());
                }
            }
        }
    }

    /**
     * Factory for creating DownloadTaskManagerImpl instances.
     */
    public static class Factory implements DownloadTaskManager.Factory {

        private final Clock clock;

        public Factory(Clock clock) {
            this.clock = clock;
        }

        @Override
        public DownloadTaskManager create(String workspaceId, String projectId) {
            // Note: The RecordingsDownloadManager is set later via the REST resource
            // because we need the ProjectManager to get the correct manager instance
            throw new UnsupportedOperationException(
                    "Use createWithManager instead to provide the RecordingsDownloadManager");
        }

        public DownloadTaskManager createWithManager(
                String workspaceId,
                String projectId,
                RecordingsDownloadManager recordingsDownloadManager) {
            return new DownloadTaskManagerImpl(workspaceId, projectId, recordingsDownloadManager, clock);
        }
    }
}
