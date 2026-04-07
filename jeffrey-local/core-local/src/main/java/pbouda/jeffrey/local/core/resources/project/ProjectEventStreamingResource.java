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

import com.fasterxml.jackson.databind.node.ArrayNode;
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

import java.io.IOException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * REST resource that bridges multi-session event streaming to SSE for frontend consumption.
 * A single SSE connection can subscribe to N sessions with the same event-type filter and time range.
 */
public class ProjectEventStreamingResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectEventStreamingResource.class);

    private final EventStreamingManager eventStreamingManager;
    private final Clock clock;

    public ProjectEventStreamingResource(EventStreamingManager eventStreamingManager, Clock clock) {
        this.eventStreamingManager = eventStreamingManager;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @GET
    @Path("/subscribe")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(
            @QueryParam("sessionIds") @DefaultValue("") String sessionIds,
            @QueryParam("eventTypes") @DefaultValue("") String eventTypes,
            @QueryParam("startTime") Long startTime,
            @QueryParam("endTime") Long endTime,
            @QueryParam("continuous") @DefaultValue("false") boolean continuous,
            @Context SseEventSink eventSink,
            @Context Sse sse) {

        List<String> sessionIdList = parseCsv(sessionIds);
        if (sessionIdList.isEmpty()) {
            throw new WebApplicationException(
                    "At least one sessionId is required", Response.Status.BAD_REQUEST);
        }

        validateTimeRange(startTime, endTime, continuous);

        Set<String> eventTypeSet = parseCsv(eventTypes).stream().collect(Collectors.toUnmodifiableSet());

        // Guard: SseEventSink.send() must be serialized across the N concurrent gRPC batch callbacks.
        Object sinkLock = new Object();

        var subscriptionRef = new AtomicReference<CompositeSubscription>();

        CompositeSubscription subscription = eventStreamingManager.subscribeMulti(
                sessionIdList,
                eventTypeSet,
                startTime,
                endTime,
                continuous,
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

    private void validateTimeRange(Long startTime, Long endTime, boolean continuous) {
        if (continuous && endTime != null) {
            throw new WebApplicationException(
                    "endTime must not be set when continuous=true; use continuous mode without an endTime or set continuous=false",
                    Response.Status.BAD_REQUEST);
        }
        if (endTime != null && endTime > clock.millis()) {
            throw new WebApplicationException(
                    "endTime must not be in the future; for future endTime, switch to continuous mode",
                    Response.Status.BAD_REQUEST);
        }
        if (startTime != null && endTime != null && startTime >= endTime) {
            throw new WebApplicationException(
                    "startTime must be strictly before endTime",
                    Response.Status.BAD_REQUEST);
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
