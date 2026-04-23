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

package pbouda.jeffrey.local.core.resources.project;

import tools.jackson.databind.node.ArrayNode;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.manager.EventStreamingManager;
import pbouda.jeffrey.local.core.manager.EventStreamingManager.CompositeSubscription;
import pbouda.jeffrey.local.core.client.LiveSubscriptionRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * REST resource that bridges multi-session live streaming to SSE for frontend consumption.
 * A single SSE connection can subscribe to N sessions with the same event-type filter.
 * Always continuous — streams stay open waiting for new events.
 */
public class ProjectLiveStreamResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectLiveStreamResource.class);

    private final EventStreamingManager eventStreamingManager;

    public ProjectLiveStreamResource(EventStreamingManager eventStreamingManager) {
        this.eventStreamingManager = eventStreamingManager;
    }

    @GET
    @Path("/subscribe")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(
            @QueryParam("sessionIds") @DefaultValue("") String sessionIds,
            @QueryParam("eventTypes") @DefaultValue("") String eventTypes,
            @Context SseEventSink eventSink,
            @Context Sse sse) {

        List<String> sessionIdList = parseCsv(sessionIds);
        if (sessionIdList.isEmpty()) {
            throw new WebApplicationException(
                    "At least one sessionId is required", Response.Status.BAD_REQUEST);
        }

        var request = new LiveSubscriptionRequest(
                sessionIdList,
                parseCsv(eventTypes).stream().collect(Collectors.toUnmodifiableSet()));

        // Guard: SseEventSink.send() must be serialized across the N concurrent gRPC batch callbacks.
        Object sinkLock = new Object();

        var subscriptionRef = new AtomicReference<CompositeSubscription>();

        CompositeSubscription subscription = eventStreamingManager.subscribeLiveStreaming(
                request,
                batch -> {
                    if (eventSink.isClosed()) {
                        CompositeSubscription sub = subscriptionRef.get();
                        if (sub != null) {
                            sub.cancel();
                        }
                        return;
                    }
                    sendSseBatch(eventSink, sse, batch, sinkLock);
                },
                sessionId -> {
                    LOG.warn("Session stream errored, notifying client: sessionId={}", sessionId);
                    sendSessionError(eventSink, sse, sessionId, sinkLock);
                },
                () -> closeSink(eventSink));

        subscriptionRef.set(subscription);

        synchronized (sinkLock) {
            eventSink.send(sse.newEventBuilder().comment("connected").build())
                    .whenComplete((__, error) -> {
                        if (error != null) {
                            subscription.cancel();
                        }
                    });
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

    private static void sendSseBatch(SseEventSink eventSink, Sse sse, ArrayNode batch, Object sinkLock) {
        synchronized (sinkLock) {
            if (eventSink.isClosed()) {
                return;
            }
            try {
                eventSink.send(sse.newEventBuilder()
                        .name("events")
                        .data(batch.toString())
                        .build());
            } catch (Exception e) {
                LOG.warn("Failed to send SSE event, client likely disconnected: {}", e.getMessage());
            }
        }
    }

    private static void sendSessionError(SseEventSink eventSink, Sse sse, String sessionId, Object sinkLock) {
        synchronized (sinkLock) {
            if (eventSink.isClosed()) {
                return;
            }
            try {
                eventSink.send(sse.newEventBuilder()
                        .name("sessionError")
                        .data("{\"sessionId\":\"" + sessionId + "\"}")
                        .build());
            } catch (Exception e) {
                LOG.warn("Failed to send sessionError SSE event: {}", e.getMessage());
            }
        }
    }

    private static void closeSink(SseEventSink eventSink) {
        try {
            if (!eventSink.isClosed()) {
                eventSink.close();
            }
        } catch (IOException e) {
            LOG.warn("Error closing SSE sink");
        }
    }
}
