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

package cafe.jeffrey.microscope.core.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import cafe.jeffrey.microscope.core.manager.RecordingsDownloadManager;
import cafe.jeffrey.microscope.core.manager.download.DownloadProgress;
import cafe.jeffrey.microscope.core.manager.download.DownloadTask;
import cafe.jeffrey.microscope.core.manager.download.DownloadTaskRegistry;
import cafe.jeffrey.microscope.core.manager.project.ProjectManager;
import cafe.jeffrey.microscope.core.manager.workspace.RemoteRecordingsDownloadManager;
import cafe.jeffrey.microscope.core.resources.request.SelectedRecordingsRequest;
import cafe.jeffrey.microscope.core.resources.response.DownloadProgressResponse;
import cafe.jeffrey.microscope.core.resources.response.DownloadTaskResponse;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@RestController
@RequestMapping("/api/internal/remote-servers/{serverId}/workspaces/{workspaceId}/projects/{projectId}/download")
public class ProjectDownloadTaskController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectDownloadTaskController.class);
    private static final long SSE_TIMEOUT_MS = 0L; // never time out by Spring

    private final ProjectManagerResolver resolver;
    private final DownloadTaskRegistry taskRegistry = DownloadTaskRegistry.getInstance();

    public ProjectDownloadTaskController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @PostMapping(value = "/start",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<DownloadTaskResponse> startDownload(
            @PathVariable("serverId") String serverId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @RequestBody SelectedRecordingsRequest request) {

        LOG.info("Starting download task: sessionId={} fileCount={}",
                request.sessionId(),
                request.recordingIds() != null ? request.recordingIds().size() : 0);

        ProjectManager pm = resolver.resolve(serverId, workspaceId, projectId).projectManager();
        RecordingsDownloadManager downloadManager = pm.recordingsDownloadManager();

        DownloadTask task = new DownloadTask(
                workspaceId,
                projectId,
                request.sessionId(),
                request.recordingIds(),
                taskRegistry.getClock());

        taskRegistry.register(task);

        CompletableFuture<Void> future = startDownloadAsync(task, downloadManager);
        task.setDownloadFuture(future);

        future.whenComplete((__, error) -> {
            if (error != null) {
                LOG.error("Download task failed: taskId={} error={}", task.getTaskId(), error.getMessage());
            } else {
                LOG.info("Download task completed: taskId={}", task.getTaskId());
            }
        });

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(DownloadTaskResponse.from(task));
    }

    @GetMapping(value = "/{taskId}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(@PathVariable("taskId") String taskId) {
        LOG.debug("SSE connection opened for task: taskId={}", taskId);

        DownloadTask task = taskRegistry.getTask(taskId)
                .orElseThrow(() -> Exceptions.invalidRequest("Download task not found: " + taskId));

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        Consumer<DownloadProgress> listener = progress -> {
            try {
                DownloadProgressResponse response = DownloadProgressResponse.from(progress);
                emitter.send(SseEmitter.event().name("progress").data(response));
                if (progress.status().isTerminal()) {
                    LOG.debug("Closing SSE connection for completed task: taskId={}", taskId);
                    emitter.complete();
                }
            } catch (IOException | IllegalStateException e) {
                LOG.warn("Error sending SSE event: taskId={} error={}", taskId, e.getMessage());
                emitter.completeWithError(e);
            }
        };

        emitter.onCompletion(() -> task.removeProgressListener(listener));
        emitter.onTimeout(() -> task.removeProgressListener(listener));
        emitter.onError(__ -> task.removeProgressListener(listener));

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            LOG.debug("SSE connection failed for task: taskId={}", taskId);
            emitter.completeWithError(e);
            return emitter;
        }
        task.addProgressListener(listener);
        return emitter;
    }

    @GetMapping(value = "/{taskId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public DownloadProgressResponse getStatus(@PathVariable("taskId") String taskId) {
        DownloadProgress progress = taskRegistry.getProgress(taskId)
                .orElseThrow(() -> Exceptions.invalidRequest("Download task not found: " + taskId));
        return DownloadProgressResponse.from(progress);
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> cancelDownload(@PathVariable("taskId") String taskId) {
        boolean cancelled = taskRegistry.cancelTask(taskId);
        if (cancelled) {
            LOG.info("Download cancelled: taskId={}", taskId);
            return ResponseEntity.noContent().build();
        }
        LOG.warn("Failed to cancel download (not found or already complete): taskId={}", taskId);
        return ResponseEntity.notFound().build();
    }

    private CompletableFuture<Void> startDownloadAsync(DownloadTask task, RecordingsDownloadManager downloadManager) {
        if (downloadManager instanceof RemoteRecordingsDownloadManager remoteManager) {
            return CompletableFuture.runAsync(() -> {
                try {
                    remoteManager.mergeAndDownloadRecordingsWithProgress(
                            task.getSessionId(), task.getFileIds(), task);
                } catch (Exception e) {
                    LOG.error("Download failed: taskId={} error={}", task.getTaskId(), e.getMessage(), e);
                    task.onError(e.getMessage());
                    throw e;
                }
            }, Schedulers.sharedVirtual());
        }
        return CompletableFuture.runAsync(() -> {
            try {
                task.onStart(task.getFileIds().size(), 0);
                downloadManager.mergeAndDownloadRecordings(task.getSessionId(), task.getFileIds());
                task.onComplete();
            } catch (Exception e) {
                LOG.error("Download failed: taskId={} error={}", task.getTaskId(), e.getMessage(), e);
                task.onError(e.getMessage());
                throw e;
            }
        }, Schedulers.sharedVirtual());
    }
}
