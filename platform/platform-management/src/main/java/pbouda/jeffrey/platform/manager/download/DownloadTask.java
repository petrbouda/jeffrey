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

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Represents a download task for downloading files from a remote workspace.
 * Tracks progress for parallel downloads and notifies listeners of updates.
 * Thread-safe for concurrent file downloads.
 */
public class DownloadTask implements ProgressCallback {

    private final String taskId;
    private final String workspaceId;
    private final String projectId;
    private final String sessionId;
    private final List<String> fileIds;
    private final Clock clock;
    private final Instant createdAt;

    // Thread-safe progress tracking for parallel downloads
    private final ConcurrentHashMap<String, FileProgress> activeFiles;
    private final List<FileProgress> completedFiles;
    private final AtomicLong completedBytes;
    private final AtomicInteger completedCount;
    private final AtomicInteger totalFiles;
    private final AtomicLong totalBytes;

    private final List<Consumer<DownloadProgress>> progressListeners;
    private final AtomicBoolean cancelled;
    private volatile CompletableFuture<Void> downloadFuture;
    private volatile DownloadTaskStatus status;

    public DownloadTask(
            String workspaceId,
            String projectId,
            String sessionId,
            List<String> fileIds,
            Clock clock) {
        this.taskId = UUID.randomUUID().toString();
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.sessionId = sessionId;
        this.fileIds = List.copyOf(fileIds);
        this.clock = clock;
        this.createdAt = clock.instant();

        this.activeFiles = new ConcurrentHashMap<>();
        this.completedFiles = new CopyOnWriteArrayList<>();
        this.completedBytes = new AtomicLong(0);
        this.completedCount = new AtomicInteger(0);
        this.totalFiles = new AtomicInteger(0);
        this.totalBytes = new AtomicLong(0);

        this.progressListeners = new CopyOnWriteArrayList<>();
        this.cancelled = new AtomicBoolean(false);
        this.status = DownloadTaskStatus.PENDING;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public List<String> getFileIds() {
        return fileIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public DownloadProgress getCurrentProgress() {
        return buildProgress();
    }

    public void setDownloadFuture(CompletableFuture<Void> future) {
        this.downloadFuture = future;
    }

    /**
     * Adds a listener that will be notified of progress updates.
     */
    public void addProgressListener(Consumer<DownloadProgress> listener) {
        progressListeners.add(listener);
        // Immediately send current progress to the new listener
        listener.accept(buildProgress());
    }

    /**
     * Removes a progress listener.
     */
    public void removeProgressListener(Consumer<DownloadProgress> listener) {
        progressListeners.remove(listener);
    }

    /**
     * Cancels the download task.
     */
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            if (downloadFuture != null) {
                downloadFuture.cancel(true);
            }
            status = DownloadTaskStatus.CANCELLED;
            notifyListeners();
        }
    }

    /**
     * Returns true if the task has been cancelled.
     */
    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Builds the current progress snapshot from thread-safe state.
     */
    private DownloadProgress buildProgress() {
        // Snapshot of active files
        List<FileProgress> activeDownloads = new ArrayList<>(activeFiles.values());
        List<FileProgress> completed = List.copyOf(completedFiles);

        // Calculate total downloaded bytes: completed + active files progress
        long activeBytes = activeDownloads.stream()
                .mapToLong(FileProgress::downloadedBytes)
                .sum();
        long downloadedBytes = completedBytes.get() + activeBytes;

        int percentComplete = 0;
        long total = totalBytes.get();
        if (total > 0) {
            percentComplete = (int) Math.min(100, (downloadedBytes * 100) / total);
        }

        Instant completedAt = status.isTerminal() ? clock.instant() : null;

        return new DownloadProgress(
                taskId,
                sessionId,
                status,
                totalFiles.get(),
                completedCount.get(),
                activeDownloads,
                completed,
                total,
                downloadedBytes,
                percentComplete,
                null,
                createdAt,
                completedAt
        );
    }

    private void notifyListeners() {
        DownloadProgress progress = buildProgress();
        for (Consumer<DownloadProgress> listener : progressListeners) {
            try {
                listener.accept(progress);
            } catch (Exception e) {
                // Log but don't propagate listener errors
            }
        }
    }

    // ProgressCallback implementation

    @Override
    public void onStart(int totalFiles, long totalBytes) {
        this.totalFiles.set(totalFiles);
        this.totalBytes.set(totalBytes);
        this.status = DownloadTaskStatus.DOWNLOADING;
        notifyListeners();
    }

    @Override
    public void onFileStart(String fileName, long fileSize) {
        activeFiles.put(fileName, FileProgress.starting(fileName, fileSize));
        notifyListeners();
    }

    @Override
    public void onFileProgress(String fileName, long bytesDownloaded) {
        activeFiles.computeIfPresent(fileName, (k, v) -> v.withProgress(bytesDownloaded));
        notifyListeners();
    }

    @Override
    public void onFileComplete(String fileName) {
        FileProgress completed = activeFiles.remove(fileName);
        if (completed != null) {
            completedFiles.add(new FileProgress(
                    completed.fileName(),
                    completed.fileSize(),
                    completed.fileSize(),
                    FileProgressStatus.COMPLETED
            ));
            completedBytes.addAndGet(completed.fileSize());
            completedCount.incrementAndGet();
        }
        notifyListeners();
    }

    @Override
    public void onFileError(String fileName, String errorMessage) {
        FileProgress failed = activeFiles.remove(fileName);
        if (failed != null) {
            // Track partial bytes from failed file
            completedBytes.addAndGet(failed.downloadedBytes());
        }
        // Note: For parallel downloads, we continue with other files
        // The overall task only fails if explicitly marked as failed
        notifyListeners();
    }

    @Override
    public void onProcessing() {
        status = DownloadTaskStatus.PROCESSING;
        activeFiles.clear();
        notifyListeners();
    }

    @Override
    public void onComplete() {
        status = DownloadTaskStatus.COMPLETED;
        activeFiles.clear();
        notifyListeners();
    }

    @Override
    public void onError(String errorMessage) {
        status = DownloadTaskStatus.FAILED;
        notifyListeners();
    }
}
