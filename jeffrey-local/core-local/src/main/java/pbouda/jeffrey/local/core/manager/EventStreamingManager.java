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

import java.util.Map;
import java.util.Set;
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
     * Subscribes to live JFR events from a remote session.
     * Event batches are converted to JSON and delivered via the provided callback.
     *
     * @param sessionId  the session ID
     * @param eventTypes JFR event types to receive (empty = all)
     * @param startTime  optional start time in epoch millis for historical replay
     * @param endTime    optional end time in epoch millis to stop streaming
     * @param continuous when true, stream stays open waiting for new events
     * @param onBatch    callback receiving event batches as JSON array nodes
     * @param onComplete called when the stream ends
     * @param onError    called on stream errors
     * @return a cancellation handle
     */
    public EventStreamingSubscription subscribe(
            String sessionId,
            Set<String> eventTypes,
            Long startTime,
            Long endTime,
            boolean continuous,
            Consumer<ArrayNode> onBatch,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        LOG.info("Subscribing to event stream: sessionId={} eventTypes={} startTime={} endTime={} continuous={}",
                sessionId, eventTypes, startTime, endTime, continuous);

        return eventStreamingClient.subscribe(
                workspaceId,
                projectId,
                sessionId,
                eventTypes,
                startTime,
                endTime,
                continuous,
                batch -> onBatch.accept(batchToJson(batch)),
                onComplete,
                onError);
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

    private static void putTypedValue(ObjectNode node, String key, TypedValue tv) {
        switch (tv.getValueCase()) {
            case STRING_VALUE -> node.put(key, tv.getStringValue());
            case LONG_VALUE -> node.put(key, tv.getLongValue());
            case DOUBLE_VALUE -> node.put(key, tv.getDoubleValue());
            case BOOL_VALUE -> node.put(key, tv.getBoolValue());
            case FLOAT_VALUE -> node.put(key, tv.getFloatValue());
            case VALUE_NOT_SET -> node.putNull(key);
        }
    }
}
