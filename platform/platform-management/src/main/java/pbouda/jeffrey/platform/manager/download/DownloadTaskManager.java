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

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Manages download tasks for remote workspace file downloads.
 * Tracks active and recently completed tasks, and provides progress updates.
 */
public interface DownloadTaskManager {

    /**
     * Creates a new download task for the specified session and files.
     *
     * @param sessionId the recording session ID
     * @param fileIds   the list of file IDs to download
     * @param merge     whether to merge recordings into a single file
     * @return the created download task
     */
    DownloadTask createTask(String sessionId, List<String> fileIds, boolean merge);

    /**
     * Starts the download for the given task.
     * The download runs asynchronously and updates the task's progress.
     *
     * @param taskId the task ID
     * @return a CompletableFuture that completes when the download finishes
     */
    CompletableFuture<Void> startDownload(String taskId);

    /**
     * Gets the current progress for a task.
     *
     * @param taskId the task ID
     * @return the current progress, or empty if task not found
     */
    Optional<DownloadProgress> getProgress(String taskId);

    /**
     * Gets a task by its ID.
     *
     * @param taskId the task ID
     * @return the task, or empty if not found
     */
    Optional<DownloadTask> getTask(String taskId);

    /**
     * Cancels an ongoing download.
     *
     * @param taskId the task ID
     * @return true if the task was cancelled, false if task not found or already completed
     */
    boolean cancelDownload(String taskId);

    /**
     * Registers a progress listener for real-time updates.
     *
     * @param taskId   the task ID
     * @param listener the listener to receive progress updates
     */
    void addProgressListener(String taskId, Consumer<DownloadProgress> listener);

    /**
     * Removes a progress listener.
     *
     * @param taskId   the task ID
     * @param listener the listener to remove
     */
    void removeProgressListener(String taskId, Consumer<DownloadProgress> listener);

    /**
     * Factory interface for creating DownloadTaskManager instances per project.
     */
    interface Factory {
        /**
         * Creates a DownloadTaskManager for the specified workspace and project.
         *
         * @param workspaceId the workspace ID
         * @param projectId   the project ID
         * @return the download task manager
         */
        DownloadTaskManager create(String workspaceId, String projectId);
    }
}
