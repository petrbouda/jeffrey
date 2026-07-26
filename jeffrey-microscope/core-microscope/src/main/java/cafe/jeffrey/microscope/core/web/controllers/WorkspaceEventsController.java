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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import cafe.jeffrey.hub.client.WorkspaceEventsClient;
import cafe.jeffrey.hub.client.WorkspaceEventsStreamCallbacks;
import cafe.jeffrey.microscope.core.web.dto.response.WorkspaceEventsResponse;
import cafe.jeffrey.microscope.core.web.ProjectManagerResolver;
import cafe.jeffrey.microscope.core.web.RequestParams;
import cafe.jeffrey.shared.common.Json;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Microscope-only WorkspaceBrowser endpoint: the workspace event feed. The analyst has no equivalent,
 * so this lives outside the shared {@code WorkspacesController}.
 */
@RestController
@RequestMapping("/api/internal/hubs/{hubId}/workspaces/{workspaceId}")
public class WorkspaceEventsController {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventsController.class);

    private static final String EVENTS_SSE_NAME = "events";

    private final ProjectManagerResolver resolver;

    public WorkspaceEventsController(ProjectManagerResolver resolver) {
        this.resolver = resolver;
    }

    @GetMapping("/events")
    public WorkspaceEventsResponse events(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {

        LOG.debug("Fetching workspace events: workspaceId={} limit={}", workspaceId, limit);
        return resolver.resolveWorkspace(hubId, workspaceId).events(limit);
    }

    /**
     * Streams workspace events to the browser over SSE, bridging the hub's gRPC stream.
     *
     * @param fromOffset exclusive resume point — the highest event id the client already has
     * @param eventTypes comma-separated type filter; empty means all types
     */
    @GetMapping(value = "/events/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(
            @PathVariable("hubId") String hubId,
            @PathVariable("workspaceId") String workspaceId,
            @RequestParam(name = "fromOffset", defaultValue = "0") long fromOffset,
            @RequestParam(name = "eventTypes", defaultValue = "") String eventTypes) {

        if (fromOffset < 0) {
            throw Exceptions.invalidRequest("fromOffset must not be negative");
        }

        var request = new WorkspaceEventsClient.WorkspaceEventsSubscribeRequest(
                fromOffset, parseEventTypes(eventTypes), true);

        // No timeout: a workspace can be quiet for hours and the stream must survive that. The hub
        // sends heartbeat batches so the connection still proves itself alive.
        SseEmitter emitter = new SseEmitter(0L);
        Object sinkLock = new Object();
        var subscriptionRef = new AtomicReference<WorkspaceEventsClient.WorkspaceEventsSubscription>();

        var callbacks = new WorkspaceEventsStreamCallbacks(
                batch -> sendUnderLock(emitter, sinkLock, EVENTS_SSE_NAME, Json.toString(batch), subscriptionRef::get),
                emitter::complete,
                error -> {
                    LOG.warn("Workspace event stream failed, closing SSE: workspaceId={}", workspaceId, error);
                    emitter.completeWithError(error);
                });

        WorkspaceEventsClient.WorkspaceEventsSubscription subscription =
                resolver.resolveWorkspace(hubId, workspaceId).streamEvents(request, callbacks);

        subscriptionRef.set(subscription);
        emitter.onCompletion(subscription::cancel);
        emitter.onTimeout(subscription::cancel);
        emitter.onError(_ -> subscription.cancel());

        synchronized (sinkLock) {
            try {
                emitter.send(SseEmitter.event().comment("connected"));
            } catch (IOException e) {
                subscription.cancel();
                emitter.completeWithError(e);
            }
        }

        LOG.debug("Subscribed to workspace event stream: workspaceId={} fromOffset={}", workspaceId, fromOffset);
        return emitter;
    }

    private static Set<WorkspaceEventType> parseEventTypes(String eventTypes) {
        try {
            return RequestParams.parseCsv(eventTypes).stream()
                    .map(WorkspaceEventType::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (IllegalArgumentException e) {
            throw Exceptions.invalidRequest("Unknown workspace event type: " + eventTypes);
        }
    }

    /**
     * Sends one SSE frame. The lock is mandatory rather than defensive: the gRPC callback thread
     * and the request thread both write here, and {@link SseEmitter} is not thread-safe.
     */
    private static void sendUnderLock(
            SseEmitter emitter,
            Object sinkLock,
            String eventName,
            String data,
            Supplier<WorkspaceEventsClient.WorkspaceEventsSubscription> currentSubscription) {

        synchronized (sinkLock) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                LOG.debug("Failed to send SSE {} event, client likely disconnected: {}", eventName, e.getMessage());
                WorkspaceEventsClient.WorkspaceEventsSubscription subscription = currentSubscription.get();
                if (subscription != null) {
                    subscription.cancel();
                }
            }
        }
    }
}
