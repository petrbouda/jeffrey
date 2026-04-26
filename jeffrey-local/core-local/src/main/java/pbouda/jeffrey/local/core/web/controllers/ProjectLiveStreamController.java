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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import pbouda.jeffrey.local.core.client.LiveSubscriptionRequest;
import pbouda.jeffrey.local.core.manager.EventStreamingManager;
import pbouda.jeffrey.local.core.manager.EventStreamingManager.CompositeSubscription;
import pbouda.jeffrey.local.core.manager.project.ProjectManager;
import pbouda.jeffrey.local.core.web.ProjectManagerResolver;
import pbouda.jeffrey.shared.common.exception.Exceptions;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * SSE bridge for multi-session live event streaming. A single SSE connection
 * can subscribe to N sessions with the same event-type filter; streams stay
 * open waiting for new events.
 */
@RestController
@RequestMapping("/api/internal/workspaces/{workspaceId}/projects/{projectId}/live-stream")
public class ProjectLiveStreamController {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectLiveStreamController.class);

    private final ProjectManagerResolver resolver;

    public ProjectLiveStreamController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @PathVariable("workspaceId") String workspaceId,
            @PathVariable("projectId") String projectId,
            @RequestParam(value = "sessionIds", defaultValue = "") String sessionIds,
            @RequestParam(value = "eventTypes", defaultValue = "") String eventTypes) {

        List<String> sessionIdList = parseCsv(sessionIds);
        if (sessionIdList.isEmpty()) {
            throw Exceptions.invalidRequest("At least one sessionId is required");
        }

        ProjectManager pm = resolver.resolve(workspaceId, projectId).projectManager();
        EventStreamingManager streamingManager = pm.eventStreamingManager();

        var request = new LiveSubscriptionRequest(
                sessionIdList,
                parseCsv(eventTypes).stream().collect(Collectors.toUnmodifiableSet()));

        SseEmitter emitter = new SseEmitter(0L);
        Object sinkLock = new Object();
        var subscriptionRef = new AtomicReference<CompositeSubscription>();

        CompositeSubscription subscription = streamingManager.subscribeLiveStreaming(
                request,
                batch -> sendUnderLock(emitter, sinkLock, "events", batch.toString(), subscriptionRef::get),
                sessionId -> {
                    LOG.warn("Session stream errored, notifying client: sessionId={}", sessionId);
                    sendUnderLock(emitter, sinkLock, "sessionError",
                            "{\"sessionId\":\"" + sessionId + "\"}", subscriptionRef::get);
                },
                emitter::complete);

        subscriptionRef.set(subscription);
        emitter.onCompletion(subscription::cancel);
        emitter.onTimeout(subscription::cancel);
        emitter.onError(__ -> subscription.cancel());

        synchronized (sinkLock) {
            try {
                emitter.send(SseEmitter.event().comment("connected"));
            } catch (IOException e) {
                subscription.cancel();
                emitter.completeWithError(e);
            }
        }
        return emitter;
    }

    private static void sendUnderLock(
            SseEmitter emitter,
            Object sinkLock,
            String eventName,
            String data,
            java.util.function.Supplier<CompositeSubscription> currentSubscription) {
        synchronized (sinkLock) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                LOG.warn("Failed to send SSE {} event, client likely disconnected: {}", eventName, e.getMessage());
                CompositeSubscription sub = currentSubscription.get();
                if (sub != null) {
                    sub.cancel();
                }
            }
        }
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
