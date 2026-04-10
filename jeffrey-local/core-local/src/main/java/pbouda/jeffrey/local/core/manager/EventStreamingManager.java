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
import pbouda.jeffrey.local.core.client.LiveSubscriptionRequest;
import pbouda.jeffrey.local.core.client.RemoteEventStreamingClient;
import pbouda.jeffrey.local.core.client.RemoteEventStreamingClient.EventStreamingSubscription;
import pbouda.jeffrey.local.core.client.ReplaySubscriptionRequest;
import pbouda.jeffrey.local.core.client.StreamingCallbacks;
import pbouda.jeffrey.server.api.v1.EventBatch;
import pbouda.jeffrey.server.api.v1.StreamingEvent;
import pbouda.jeffrey.server.api.v1.TypedValue;
import pbouda.jeffrey.shared.common.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Manages event streaming subscriptions for a project.
 * Bridges gRPC event batches to JSON for SSE delivery.
 */
public class EventStreamingManager {

    private static final Logger LOG = LoggerFactory.getLogger(EventStreamingManager.class);
    private final RemoteEventStreamingClient eventStreamingClient;

    public EventStreamingManager(RemoteEventStreamingClient eventStreamingClient) {
        this.eventStreamingClient = eventStreamingClient;
    }

    /**
     * Subscribes to live JFR events from multiple remote sessions.
     * Always continuous — streams stay open waiting for new events.
     * Each session runs as an independent gRPC subscription — a failure in one does not affect the others.
     *
     * @param request        live subscription parameters (session IDs, event types)
     * @param onBatch        callback receiving event batches as JSON array nodes
     *                       (invoked concurrently from multiple gRPC threads — callers must serialize)
     * @param onSessionError callback receiving the sessionId of a session whose stream errored
     * @param onAllComplete  called once when every session's stream has ended
     * @return a cancellation handle for all subscriptions
     */
    public CompositeSubscription subscribeLiveStreaming(
            LiveSubscriptionRequest request,
            Consumer<ArrayNode> onBatch,
            Consumer<String> onSessionError,
            Runnable onAllComplete) {

        LOG.info("Subscribing to multi-session event stream: request={}", request);

        AtomicInteger remaining = new AtomicInteger(request.sessionIds().size());
        List<EventStreamingSubscription> subs = new ArrayList<>(request.sessionIds().size());

        for (String sessionId : request.sessionIds()) {
            var callbacks = new StreamingCallbacks(
                    batch -> onBatch.accept(batchToJson(batch)),
                    () -> {
                        if (remaining.decrementAndGet() == 0) {
                            onAllComplete.run();
                        }
                    },
                    _ -> {
                        onSessionError.accept(sessionId);
                        if (remaining.decrementAndGet() == 0) {
                            onAllComplete.run();
                        }
                    });

            EventStreamingSubscription sub = eventStreamingClient.subscribeLiveStreaming(
                    sessionId, request, callbacks);
            subs.add(sub);
        }

        return new CompositeSubscription(subs);
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
            case VALUE_NOT_SET -> {
            }
        }
        fields.set(key, wrapper);
    }
}
