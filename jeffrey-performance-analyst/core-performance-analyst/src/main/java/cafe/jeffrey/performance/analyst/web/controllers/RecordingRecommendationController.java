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

package cafe.jeffrey.performance.analyst.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
import cafe.jeffrey.performance.analyst.recommendations.RecommendationArtifactsResponse;
import cafe.jeffrey.performance.analyst.recommendations.RecommendationProgress;
import cafe.jeffrey.performance.analyst.recommendations.RecommendationResult;
import cafe.jeffrey.performance.analyst.recommendations.RecommendationStartRequest;
import cafe.jeffrey.performance.analyst.recommendations.RecommendationTarget;
import cafe.jeffrey.performance.analyst.recommendations.RecommendationTask;
import cafe.jeffrey.performance.analyst.recommendations.RecommendationTaskRegistry;
import cafe.jeffrey.performance.analyst.recommendations.RecommendationTaskResponse;
import cafe.jeffrey.performance.analyst.recommendations.RecordingRecommendationManager;
import cafe.jeffrey.shared.common.Schedulers;
import cafe.jeffrey.shared.common.exception.Exceptions;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Starts and streams repository-aware AI recommendation tasks for a recording. The work (clone + AI
 * analysis) is long-running, so it runs asynchronously on the shared virtual-thread executor and the
 * client follows it over SSE — mirroring the shared {@code ProjectDownloadTaskController}. The terminal
 * {@code COMPLETED} progress event carries the recommendations markdown.
 */
@RestController
@ConditionalOnExpression("'${jeffrey.performance-analyst.ai.provider:none}' != 'none'")
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}/projects/{projectId}/recordings/{recordingId}/ai-recommendations")
public class RecordingRecommendationController {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingRecommendationController.class);
    private static final long SSE_NEVER_TIMEOUT = 0L;
    private static final String PROGRESS_EVENT = "progress";

    private final RecordingRecommendationManager recommendationManager;
    private final RecommendationTaskRegistry taskRegistry;
    private final Clock clock;

    public RecordingRecommendationController(
            RecordingRecommendationManager recommendationManager,
            RecommendationTaskRegistry taskRegistry,
            Clock clock) {
        this.recommendationManager = recommendationManager;
        this.taskRegistry = taskRegistry;
        this.clock = clock;
    }

    @PostMapping(value = "/start",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RecommendationTaskResponse> start(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @PathVariable("recordingId") String recordingId,
            @RequestBody RecommendationStartRequest request) {

        if (request.eventType() == null || request.eventType().isBlank()) {
            throw Exceptions.invalidRequest("eventType is required");
        }

        RecommendationTask task = new RecommendationTask(
                hubId, workspaceId, projectId, recordingId, request.eventType(), clock);
        taskRegistry.register(task);

        RecommendationTarget target = new RecommendationTarget(
                hubId, workspaceId, projectId, request.projectName(), recordingId, request.eventType());

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                RecommendationResult result = recommendationManager.generate(target, task);
                task.completed(result);
            } catch (Exception e) {
                LOG.warn("Recommendation task failed: taskId={} recordingId={} error={}",
                        task.taskId(), recordingId, e.getMessage());
                task.failed(e.getMessage());
            }
        }, Schedulers.sharedVirtual());
        task.setFuture(future);

        LOG.info("Started recommendation task: taskId={} recordingId={} eventType={}",
                task.taskId(), recordingId, request.eventType());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new RecommendationTaskResponse(task.taskId()));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<RecommendationArtifactsResponse> peek(@PathVariable("recordingId") String recordingId) {
        return recommendationManager.peek(recordingId).stream()
                .map(RecommendationArtifactsResponse::from)
                .toList();
    }

    @GetMapping(value = "/{taskId}/progress", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamProgress(@PathVariable("taskId") String taskId) {
        RecommendationTask task = taskRegistry.getTask(taskId)
                .orElseThrow(() -> Exceptions.invalidRequest("Recommendation task not found: " + taskId));

        SseEmitter emitter = new SseEmitter(SSE_NEVER_TIMEOUT);

        Consumer<RecommendationProgress> listener = progress -> {
            try {
                emitter.send(SseEmitter.event().name(PROGRESS_EVENT).data(progress));
                if (progress.status().isTerminal()) {
                    emitter.complete();
                }
            } catch (IOException | IllegalStateException e) {
                LOG.warn("Error sending recommendation SSE event: taskId={} error={}", taskId, e.getMessage());
                emitter.completeWithError(e);
            }
        };

        emitter.onCompletion(() -> task.removeProgressListener(listener));
        emitter.onTimeout(() -> task.removeProgressListener(listener));
        emitter.onError(__ -> task.removeProgressListener(listener));

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }
        task.addProgressListener(listener);
        return emitter;
    }

    @GetMapping(value = "/{taskId}/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public RecommendationProgress getStatus(@PathVariable("taskId") String taskId) {
        return taskRegistry.getProgress(taskId)
                .orElseThrow(() -> Exceptions.invalidRequest("Recommendation task not found: " + taskId));
    }

    @DeleteMapping("/{taskId}")
    public ResponseEntity<Void> cancel(@PathVariable("taskId") String taskId) {
        boolean cancelled = taskRegistry.cancelTask(taskId);
        if (cancelled) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
