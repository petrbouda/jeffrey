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

package pbouda.jeffrey.local.core.manager;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.client.RemoteEventStreamingClient;
import pbouda.jeffrey.local.core.client.RemoteEventStreamingClient.EventStreamingSubscription;
import pbouda.jeffrey.server.api.v1.EventBatch;
import pbouda.jeffrey.server.api.v1.StreamingEvent;
import pbouda.jeffrey.server.api.v1.TypedValue;
import pbouda.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Manages event streaming subscriptions for a project.
 * Bridges gRPC event batches to JSON for SSE delivery.
 */
public class EventStreamingManager {

    private static final Logger LOG = LoggerFactory.getLogger(EventStreamingManager.class);
    private final String workspaceId;
    private final String projectId;
    private final RemoteEventStreamingClient eventStreamingClient;

    public EventStreamingManager(
            String workspaceId,
            String projectId,
            RemoteEventStreamingClient eventStreamingClient) {

        this.workspaceId = workspaceId;
        this.projectId = projectId;
        this.eventStreamingClient = eventStreamingClient;
    }

    /**
     * Subscribes to live JFR events from multiple remote sessions with the same filter and time range.
     * All event batches are converted to JSON and delivered via a single callback.
     * Each session runs as an independent gRPC subscription — a failure in one does not affect the others.
     *
     * @param sessionIds      the session IDs to subscribe to (must be non-empty)
     * @param eventTypes      JFR event types to receive (empty = all)
     * @param startTime       optional start time in epoch millis for historical replay
     * @param endTime         optional end time in epoch millis to stop streaming
     * @param continuous      when true, streams stay open waiting for new events
     * @param onBatch         callback receiving event batches as JSON array nodes
     *                        (invoked concurrently from multiple gRPC threads — callers must serialize)
     * @param onSessionError  callback receiving the sessionId of a session whose stream errored;
     *                        other sessions keep streaming
     * @param onAllComplete   called once when every session's stream has ended
     * @return a cancellation handle for all subscriptions
     */
    public CompositeSubscription subscribeMulti(
            List<String> sessionIds,
            Set<String> eventTypes,
            Long startTime,
            Long endTime,
            boolean continuous,
            Consumer<ArrayNode> onBatch,
            Consumer<String> onSessionError,
            Runnable onAllComplete) {

        LOG.info("Subscribing to multi-session event stream: sessionIds={} eventTypes={} startTime={} endTime={} continuous={}",
                sessionIds, eventTypes, startTime, endTime, continuous);

        AtomicInteger remaining = new AtomicInteger(sessionIds.size());
        List<EventStreamingSubscription> subs = new ArrayList<>(sessionIds.size());

        for (String sessionId : sessionIds) {
            EventStreamingSubscription sub = eventStreamingClient.subscribe(
                    workspaceId,
                    projectId,
                    sessionId,
                    eventTypes,
                    startTime,
                    endTime,
                    continuous,
                    batch -> onBatch.accept(batchToJson(batch)),
                    () -> {
                        if (remaining.decrementAndGet() == 0) {
                            onAllComplete.run();
                        }
                    },
                    error -> {
                        onSessionError.accept(sessionId);
                        if (remaining.decrementAndGet() == 0) {
                            onAllComplete.run();
                        }
                    });
            subs.add(sub);
        }

        return new CompositeSubscription(subs);
    }

    /**
     * Handle that cancels a fan-in of per-session subscriptions as a single unit.
     */
    public record CompositeSubscription(List<EventStreamingSubscription> subscriptions) {
        public void cancel() {
            for (EventStreamingSubscription sub : subscriptions) {
                sub.cancel();
            }
        }
    }

    /**
     * Converts a gRPC EventBatch to a JSON ArrayNode with typed values resolved.
     */
    private static ArrayNode batchToJson(EventBatch batch) {
        ArrayNode array = Json.createArray();
        for (StreamingEvent event : batch.getEventsList()) {
            ObjectNode node = Json.createObject();
            node.put("eventType", event.getEventType());
            node.put("sessionId", event.getSessionId());
            node.put("timestamp", event.getTimestamp());

            ObjectNode fields = Json.createObject();
            for (Map.Entry<String, TypedValue> entry : event.getFieldsMap().entrySet()) {
                putTypedValue(fields, entry.getKey(), entry.getValue());
            }
            node.set("fields", fields);

            array.add(node);
        }
        return array;
    }

    private static void putTypedValue(ObjectNode fields, String key, TypedValue tv) {
        ObjectNode wrapper = Json.createObject();
        switch (tv.getValueCase()) {
            case STRING_VALUE -> wrapper.put("stringValue", tv.getStringValue());
            case LONG_VALUE -> wrapper.put("longValue", tv.getLongValue());
            case DOUBLE_VALUE -> wrapper.put("doubleValue", tv.getDoubleValue());
            case BOOL_VALUE -> wrapper.put("boolValue", tv.getBoolValue());
            case FLOAT_VALUE -> wrapper.put("floatValue", tv.getFloatValue());
            case VALUE_NOT_SET -> { }
        }
        fields.set(key, wrapper);
    }
}
