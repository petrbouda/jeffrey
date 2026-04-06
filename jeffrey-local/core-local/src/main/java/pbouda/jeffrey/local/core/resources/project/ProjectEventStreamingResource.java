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
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.client.RemoteEventStreamingClient.EventStreamingSubscription;
import pbouda.jeffrey.local.core.manager.EventStreamingManager;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * REST resource that bridges event streaming to SSE for frontend consumption.
 */
public class ProjectEventStreamingResource {

    private static final Logger LOG = LoggerFactory.getLogger(ProjectEventStreamingResource.class);

    private final EventStreamingManager eventStreamingManager;

    public ProjectEventStreamingResource(EventStreamingManager eventStreamingManager) {
        this.eventStreamingManager = eventStreamingManager;
    }

    @GET
    @Path("/{sessionId}/subscribe")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(
            @PathParam("sessionId") String sessionId,
            @QueryParam("eventTypes") @DefaultValue("") String eventTypes,
            @QueryParam("startTime") Long startTime,
            @QueryParam("heartbeat") @DefaultValue("false") boolean heartbeat,
            @Context SseEventSink eventSink,
            @Context Sse sse) {

        Set<String> eventTypeSet = eventTypes.isEmpty()
                ? Set.of()
                : new LinkedHashSet<>(List.of(eventTypes.split(",")));

        EventStreamingSubscription subscription = eventStreamingManager.subscribe(
                sessionId,
                eventTypeSet,
                startTime,
                heartbeat,
                batch -> sendSseEvent(eventSink, sse, batch),
                () -> closeSink(eventSink),
                error -> closeSink(eventSink));

        eventSink.send(sse.newEventBuilder().comment("connected").build())
                .whenComplete((__, error) -> {
                    if (error != null) {
                        subscription.cancel();
                    }
                });
    }

    private static void sendSseEvent(SseEventSink eventSink, Sse sse, ArrayNode batch) {
        if (eventSink.isClosed()) {
            return;
        }
        try {
            eventSink.send(sse.newEventBuilder()
                    .name("events")
                    .data(batch.toString())
                    .build());
        } catch (Exception e) {
            LOG.debug("Failed to send SSE event, client likely disconnected: {}", e.getMessage());
        }
    }

    private static void closeSink(SseEventSink eventSink) {
        try {
            if (!eventSink.isClosed()) {
                eventSink.close();
            }
        } catch (IOException e) {
            LOG.debug("Error closing SSE sink");
        }
    }
}
