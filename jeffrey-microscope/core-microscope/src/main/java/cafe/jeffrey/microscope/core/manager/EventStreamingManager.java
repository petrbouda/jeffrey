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

package cafe.jeffrey.microscope.core.manager;

import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.grpc.client.ActivityScanQuery;
import cafe.jeffrey.microscope.grpc.client.ActivityScanRequest;
import cafe.jeffrey.microscope.grpc.client.ActivityScanSnapshot;
import cafe.jeffrey.microscope.grpc.client.ActivityScanTarget;
import cafe.jeffrey.microscope.grpc.client.EventStreamingClient;
import cafe.jeffrey.microscope.grpc.client.EventStreamingClient.EventStreamingSubscription;
import cafe.jeffrey.microscope.grpc.client.ReplaySubscriptionRequest;
import cafe.jeffrey.microscope.grpc.client.StreamingCallbacks;
import cafe.jeffrey.hub.api.v1.EventBatch;
import cafe.jeffrey.hub.api.v1.StreamingEvent;
import cafe.jeffrey.hub.api.v1.TypedValue;
import cafe.jeffrey.shared.common.Json;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Manages event streaming subscriptions for a project.
 * Bridges gRPC event batches to JSON for SSE delivery.
 */
public class EventStreamingManager {

    private static final Logger LOG = LoggerFactory.getLogger(EventStreamingManager.class);
    private final EventStreamingClient eventStreamingClient;

    public EventStreamingManager(EventStreamingClient eventStreamingClient) {
        this.eventStreamingClient = eventStreamingClient;
    }

    /**
     * Replays historical JFR events from a single remote session's dumped recording files.
     *
     * @param request    replay parameters (session ID, event types, time range)
     * @param onBatch    callback receiving event batches as JSON array nodes
     * @param onComplete called when the replay finishes
     * @param onError    called if the replay encounters an error
     * @return a cancellation handle for the replay
     */
    public EventStreamingSubscription subscribeReplayStreaming(
            ReplaySubscriptionRequest request,
            Consumer<ArrayNode> onBatch,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        LOG.info("Starting event replay: request={}", request);

        var callbacks = new StreamingCallbacks(
                batch -> onBatch.accept(batchToJson(batch)),
                onComplete,
                onError);

        return eventStreamingClient.subscribeReplayStreaming(request, callbacks);
    }

    /** Supplies protobuf batches, including scoped replay coverage metadata. */
    public EventStreamingSubscription subscribeReplayRaw(ReplaySubscriptionRequest request, StreamingCallbacks callbacks) {
        return eventStreamingClient.subscribeReplayStreaming(request, callbacks);
    }

    public ActivityScanSnapshot startActivity(ActivityScanRequest request) {
        return eventStreamingClient.startActivity(request);
    }

    public ActivityScanSnapshot getActivity(ActivityScanQuery query) {
        return eventStreamingClient.getActivity(query);
    }

    public ActivityScanSnapshot cancelActivity(ActivityScanTarget target) {
        return eventStreamingClient.cancelActivity(target);
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
            case VALUE_NOT_SET -> {
            }
        }
        fields.set(key, wrapper);
    }
}
