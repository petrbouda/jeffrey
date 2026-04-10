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

package pbouda.jeffrey.server.core.streaming;

import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.server.api.v1.EventBatch;
import pbouda.jeffrey.server.api.v1.StreamingEvent;
import pbouda.jeffrey.shared.common.Schedulers;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-subscriber JFR EventStream that opens its own repository, filters by event types,
 * and micro-batches events using JFR's own {@code onFlush()} callback.
 *
 * <p>{@code onEvent()} and {@code onFlush()} are called on the same EventStream thread,
 * so no concurrency control is needed — a plain {@link ArrayList} is used as the buffer.</p>
 */
public class EventStreamSubscriber implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(EventStreamSubscriber.class);

    private final LiveStreamSubscription subscription;
    private final StreamingCallbacks callbacks;
    private final List<StreamingEvent> buffer = new ArrayList<>();
    private final AtomicBoolean alreadyClosed = new AtomicBoolean(false);

    private EventStream eventStream;

    public EventStreamSubscriber(
            LiveStreamSubscription subscription,
            StreamingCallbacks callbacks) {

        this.subscription = subscription;
        this.callbacks = callbacks;
    }

    /**
     * Opens the streaming repository, configures event handlers,
     * and starts consuming events asynchronously on a virtual thread.
     *
     * @throws IOException if the streaming repository cannot be opened
     */
    public void start() throws IOException {
        LOG.info("Starting live stream: subscription={}", subscription);
        this.eventStream = EventStream.openRepository(subscription.sessionPath());

        for (String eventType : subscription.eventTypes()) {
            eventStream.onEvent(eventType, this::bufferEvent);
        }

        eventStream.onFlush(this::flush);

        eventStream.onClose(() -> {
            flush();
            try {
                callbacks.onComplete().run();
            } catch (Exception e) {
                LOG.info("Observer already closed on stream end: subscription={}", subscription);
            }
        });

        eventStream.onError(t -> {
            LOG.error("Error in live stream: subscription={}", subscription, t);
            try {
                callbacks.onError().accept(t);
            } catch (Exception e) {
                LOG.warn("Observer already closed on error: subscription={}", subscription);
            }
        });

        Schedulers.streamingExecutor().execute(eventStream::start);
    }

    private void bufferEvent(RecordedEvent event) {
        try {
            StreamingEvent streamingEvent = RecordedEventMapper.toStreamingEvent(subscription.sessionId(), event);
            buffer.add(streamingEvent);
        } catch (Exception e) {
            LOG.warn("Failed to map event: subscription={} eventType={}",
                    subscription, event.getEventType().getName(), e);
        }
    }

    private void flush() {
        if (alreadyClosed.get()) {
            return;
        }
        if (!buffer.isEmpty() || subscription.sendEmptyBatches()) {
            try {
                callbacks.onNext().accept(EventBatch.newBuilder().addAllEvents(buffer).build());
            } catch (Exception e) {
                LOG.warn("Failed to send batch, closing stream: subscription={}", subscription);
                close();
            }
            buffer.clear();
        }
    }

    @Override
    public void close() {
        LOG.info("Closing live stream: subscription={}", subscription);
        if (alreadyClosed.compareAndSet(false, true) && eventStream != null) {
            eventStream.close();
            callbacks.onClose().run();
        }
    }
}
