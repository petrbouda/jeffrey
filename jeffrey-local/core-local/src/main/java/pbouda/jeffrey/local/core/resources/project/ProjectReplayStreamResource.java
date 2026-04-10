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
import pbouda.jeffrey.local.core.client.RemoteEventStreamingClient.EventStreamingSubscription;
import pbouda.jeffrey.local.core.manager.EventStreamingManager;
import pbouda.jeffrey.local.core.client.ReplaySubscriptionRequest;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * REST resource that bridges single-session event replay to SSE for frontend consumption.
 * Reads historical events from dumped recording files and streams them to the client.
 */
public class ProjectReplayStreamResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectReplayStreamResource.class);

    private final EventStreamingManager eventStreamingManager;

    public ProjectReplayStreamResource(EventStreamingManager eventStreamingManager) {
        this.eventStreamingManager = eventStreamingManager;
    }

    @GET
    @Path("/subscribe")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(
            @QueryParam("sessionId") String sessionId,
            @QueryParam("eventTypes") @DefaultValue("") String eventTypes,
            @QueryParam("startTime") Long startTime,
            @QueryParam("endTime") Long endTime,
            @Context SseEventSink eventSink,
            @Context Sse sse) {

        if (sessionId == null || sessionId.isBlank()) {
            throw new WebApplicationException(
                    "sessionId is required", Response.Status.BAD_REQUEST);
        }

        validateTimeRange(startTime, endTime);

        var request = new ReplaySubscriptionRequest(
                sessionId,
                parseCsv(eventTypes).stream().collect(Collectors.toUnmodifiableSet()),
                startTime,
                endTime);

        var subscriptionRef = new AtomicReference<EventStreamingSubscription>();

        EventStreamingSubscription subscription = eventStreamingManager.subscribeReplayStreaming(
                request,
                batch -> {
                    if (eventSink.isClosed()) {
                        EventStreamingSubscription sub = subscriptionRef.get();
                        if (sub != null) {
                            sub.cancel();
                        }
                        return;
                    }
                    sendSseBatch(eventSink, sse, batch);
                },
                () -> {
                    sendSseComplete(eventSink, sse);
                    closeSink(eventSink);
                },
                error -> {
                    LOG.warn("Session replay errored: sessionId={}", sessionId, error);
                    sendSseError(eventSink, sse, error);
                    closeSink(eventSink);
                });

        subscriptionRef.set(subscription);

        eventSink.send(sse.newEventBuilder().comment("connected").build())
                .whenComplete((__, error) -> {
                    if (error != null) {
                        subscription.cancel();
                    }
                });
    }

    private void validateTimeRange(Long startTime, Long endTime) {
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

    private static void sendSseBatch(SseEventSink eventSink, Sse sse, ArrayNode batch) {
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

    private static void sendSseComplete(SseEventSink eventSink, Sse sse) {
        if (eventSink.isClosed()) {
            return;
        }
        try {
            eventSink.send(sse.newEventBuilder()
                    .name("complete")
                    .data("")
                    .build());
        } catch (Exception e) {
            LOG.warn("Failed to send SSE complete event: {}", e.getMessage());
        }
    }

    private static void sendSseError(SseEventSink eventSink, Sse sse, Throwable error) {
        if (eventSink.isClosed()) {
            return;
        }
        try {
            String message = error.getMessage() != null ? error.getMessage() : "Unknown error";
            eventSink.send(sse.newEventBuilder()
                    .name("replayError")
                    .data(message)
                    .build());
        } catch (Exception e) {
            LOG.warn("Failed to send SSE error event: {}", e.getMessage());
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
