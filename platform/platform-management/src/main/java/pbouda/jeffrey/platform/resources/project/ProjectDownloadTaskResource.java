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

package pbouda.jeffrey.platform.resources.project;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.exception.Exceptions;
import pbouda.jeffrey.platform.manager.RecordingsDownloadManager;
import pbouda.jeffrey.platform.manager.download.DownloadProgress;
import pbouda.jeffrey.platform.manager.download.DownloadTask;
import pbouda.jeffrey.platform.manager.download.DownloadTaskRegistry;
import pbouda.jeffrey.platform.manager.workspace.remote.RemoteRecordingsDownloadManager;
import pbouda.jeffrey.platform.resources.request.SelectedRecordingsRequest;
import pbouda.jeffrey.platform.resources.response.DownloadProgressResponse;
import pbouda.jeffrey.platform.resources.response.DownloadTaskResponse;
import pbouda.jeffrey.shared.common.Schedulers;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * REST resource for managing download tasks with progress tracking.
 * Provides endpoints for starting downloads, streaming progress via SSE,
 * polling status, and cancelling downloads.
 */
public class ProjectDownloadTaskResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectDownloadTaskResource.class);

    private final String workspaceId;
    private final String projectId;
    private final RecordingsDownloadManager recordingsDownloadManager;
    private final DownloadTaskRegistry taskRegistry;

    public ProjectDownloadTaskResource(
            String workspaceId,
            String projectId,
            RecordingsDownloadManager recordingsDownloadManager) {
        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.recordingsDownloadManager = recordingsDownloadManager;
        this.taskRegistry = DownloadTaskRegistry.getInstance();
    }

    /**
     * Starts a new download task.
     *
     * @param request the request containing session ID and file IDs to download
     * @return the created task information
     */
    @POST
    @Path("/start")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response startDownload(SelectedRecordingsRequest request) {
        LOG.info("Starting download task: sessionId={} fileCount={}",
                request.sessionId(), request.recordingIds() != null ? request.recordingIds().size() : 0);

        // Create the task
        DownloadTask task = new DownloadTask(
                workspaceId,
                projectId,
                request.sessionId(),
                request.recordingIds(),
                taskRegistry.getClock()
        );

        // Register the task
        taskRegistry.register(task);

        // Start the download asynchronously
        CompletableFuture<Void> future = startDownloadAsync(task);
        task.setDownloadFuture(future);

        // Log when complete
        future.whenComplete((result, error) -> {
            if (error != null) {
                LOG.error("Download task failed: taskId={} error={}", task.getTaskId(), error.getMessage());
            } else {
                LOG.info("Download task completed: taskId={}", task.getTaskId());
            }
        });

        return Response.status(Response.Status.ACCEPTED)
                .entity(DownloadTaskResponse.from(task))
                .build();
    }

    private CompletableFuture<Void> startDownloadAsync(DownloadTask task) {
        // Check if the download manager supports progress tracking
        if (recordingsDownloadManager instanceof RemoteRecordingsDownloadManager remoteManager) {
            return CompletableFuture.runAsync(() -> {
                try {
                    remoteManager.mergeAndDownloadRecordingsWithProgress(
                            task.getSessionId(),
                            task.getFileIds(),
                            task
                    );
                } catch (Exception e) {
                    LOG.error("Download failed: taskId={} error={}", task.getTaskId(), e.getMessage(), e);
                    task.onError(e.getMessage());
                    throw e;
                }
            }, Schedulers.sharedVirtual());
        } else {
            // For non-remote download managers, execute without detailed progress
            return CompletableFuture.runAsync(() -> {
                try {
                    task.onStart(task.getFileIds().size(), 0);
                    recordingsDownloadManager.mergeAndDownloadRecordings(
                            task.getSessionId(),
                            task.getFileIds()
                    );
                    task.onComplete();
                } catch (Exception e) {
                    LOG.error("Download failed: taskId={} error={}", task.getTaskId(), e.getMessage(), e);
                    task.onError(e.getMessage());
                    throw e;
                }
            }, Schedulers.sharedVirtual());
        }
    }

    /**
     * Streams progress updates via Server-Sent Events.
     *
     * @param taskId    the task ID
     * @param eventSink the SSE event sink
     * @param sse       the SSE factory
     */
    @GET
    @Path("/{taskId}/progress")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void streamProgress(
            @PathParam("taskId") String taskId,
            @Context SseEventSink eventSink,
            @Context Sse sse) {

        LOG.debug("SSE connection opened for task: taskId={}", taskId);

        DownloadTask task = taskRegistry.getTask(taskId)
                .orElseThrow(() -> Exceptions.invalidRequest("Download task not found: " + taskId));

        // Create a listener that sends SSE events
        Consumer<DownloadProgress> listener = progress -> {
            if (eventSink.isClosed()) {
                return;
            }
            try {
                DownloadProgressResponse response = DownloadProgressResponse.from(progress);
                eventSink.send(sse.newEventBuilder()
                        .name("progress")
                        .data(response)
                        .build());

                // Close the sink when the task completes
                if (progress.status().isTerminal()) {
                    LOG.debug("Closing SSE connection for completed task: taskId={}", taskId);
                    eventSink.close();
                }
            } catch (Exception e) {
                LOG.warn("Error sending SSE event: taskId={} error={}", taskId, e.getMessage());
                try {
                    eventSink.close();
                } catch (IOException ignored) {
                    // Ignore close errors
                }
            }
        };

        // Send initial connection comment, then register listener after connection is established
        eventSink.send(sse.newEventBuilder().comment("connected").build())
                .whenComplete((__, error) -> {
                    if (error != null) {
                        LOG.debug("SSE connection failed for task: taskId={}", taskId);
                        return;
                    }

                    // Register the listener only after connection is confirmed
                    task.addProgressListener(listener);
                });
    }

    /**
     * Gets the current status of a download task (polling endpoint).
     *
     * @param taskId the task ID
     * @return the current progress
     */
    @GET
    @Path("/{taskId}/status")
    @Produces(MediaType.APPLICATION_JSON)
    public DownloadProgressResponse getStatus(@PathParam("taskId") String taskId) {
        DownloadProgress progress = taskRegistry.getProgress(taskId)
                .orElseThrow(() -> Exceptions.invalidRequest("Download task not found: " + taskId));
        return DownloadProgressResponse.from(progress);
    }

    /**
     * Cancels an ongoing download.
     *
     * @param taskId the task ID
     * @return 204 No Content on success
     */
    @DELETE
    @Path("/{taskId}")
    public Response cancelDownload(@PathParam("taskId") String taskId) {
        boolean cancelled = taskRegistry.cancelTask(taskId);
        if (cancelled) {
            LOG.info("Download cancelled: taskId={}", taskId);
            return Response.noContent().build();
        } else {
            LOG.warn("Failed to cancel download (not found or already complete): taskId={}", taskId);
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }
}
