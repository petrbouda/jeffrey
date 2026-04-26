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

package pbouda.jeffrey.local.core.web.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pbouda.jeffrey.local.core.client.RemoteEventStreamingClient.EventStreamingSubscription;
import pbouda.jeffrey.local.core.client.ReplaySubscriptionRequest;
import pbouda.jeffrey.local.core.manager.EventStreamingManager;
import pbouda.jeffrey.local.core.manager.project.ProjectManager;
import pbouda.jeffrey.local.core.web.ProjectManagerResolver;
import pbouda.jeffrey.shared.common.exception.Exceptions;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * SSE bridge for single-session event replay; reads historical events from
 * dumped recording files and streams them to the client.
 */
@RequestMapping("/api/internal/workspaces/{workspaceId}/projects/{projectId}/replay-stream")
@ResponseBody
public class ProjectReplayStreamController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectReplayStreamController.class);

    private final ProjectManagerResolver resolver;

    public ProjectReplayStreamController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "eventTypes", defaultValue = "") String eventTypes,
            @RequestParam(value = "startTime", required = false) Long startTime,
            @RequestParam(value = "endTime", required = false) Long endTime) {

        if (sessionId == null || sessionId.isBlank()) {
            throw Exceptions.invalidRequest("sessionId is required");
        }
        if (startTime != null && endTime != null && startTime >= endTime) {
            throw Exceptions.invalidRequest("startTime must be strictly before endTime");
        }

        ProjectManager pm = resolver.resolve(workspaceId, projectId).projectManager();
        EventStreamingManager streamingManager = pm.eventStreamingManager();

        var request = new ReplaySubscriptionRequest(
                sessionId,
                parseCsv(eventTypes).stream().collect(Collectors.toUnmodifiableSet()),
                startTime,
                endTime);

        SseEmitter emitter = new SseEmitter(0L);
        var subscriptionRef = new AtomicReference<EventStreamingSubscription>();

        EventStreamingSubscription subscription = streamingManager.subscribeReplayStreaming(
                request,
                batch -> {
                    try {
                        emitter.send(SseEmitter.event().name("events").data(batch.toString()));
                    } catch (IOException | IllegalStateException e) {
                        LOG.warn("Failed to send SSE event, client likely disconnected: {}", e.getMessage());
                        EventStreamingSubscription sub = subscriptionRef.get();
                        if (sub != null) {
                            sub.cancel();
                        }
                    }
                },
                () -> {
                    try {
                        emitter.send(SseEmitter.event().name("complete").data(""));
                    } catch (IOException | IllegalStateException ignored) {
                        // sink closed
                    }
                    emitter.complete();
                },
                error -> {
                    LOG.warn("Session replay errored: sessionId={}", sessionId, error);
                    try {
                        String message = error.getMessage() != null ? error.getMessage() : "Unknown error";
                        emitter.send(SseEmitter.event().name("replayError").data(message));
                    } catch (IOException | IllegalStateException ignored) {
                        // sink closed
                    }
                    emitter.complete();
                });

        subscriptionRef.set(subscription);
        emitter.onCompletion(subscription::cancel);
        emitter.onTimeout(subscription::cancel);
        emitter.onError(__ -> subscription.cancel());

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            subscription.cancel();
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private static List<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
