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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Per-subscriber JFR EventStream that opens its own repository, filters by event types,
 * and micro-batches events using JFR's own {@code onFlush()} callback.
 *
 * <p>{@code onEvent()} and {@code onFlush()} are called on the same EventStream thread,
 * so no concurrency control is needed — a plain {@link ArrayList} is used as the buffer.</p>
 */
public class EventStreamSubscriber implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(EventStreamSubscriber.class);

    private static final String STREAMING_REPO_DIR = "streaming-repo";

    private final EventStreamSubscription subscription;
    private final Consumer<EventBatch> consumer;
    private final Runnable onComplete;
    private final Consumer<Throwable> onError;
    private final Runnable onClose;
    private final List<StreamingEvent> buffer = new ArrayList<>();
    private final AtomicBoolean alreadyClosed = new AtomicBoolean(false);

    private EventStream eventStream;

    public EventStreamSubscriber(
            EventStreamSubscription subscription,
            Consumer<EventBatch> consumer,
            Runnable onComplete,
            Consumer<Throwable> onError,
            Runnable onClose) {

        this.subscription = subscription;
        this.consumer = consumer;
        this.onComplete = onComplete;
        this.onError = onError;
        this.onClose = onClose;
    }

    /**
     * Opens the streaming repository, configures event handlers,
     * and starts consuming events asynchronously on a virtual thread.
     *
     * @throws IOException if the streaming repository cannot be opened
     */
    public void start() throws IOException {
        Path streamingRepoPath = subscription.sessionPath().resolve(STREAMING_REPO_DIR);

        LOG.info("Starting subscriber event stream: subscription={}", subscription);
        this.eventStream = EventStream.openRepository(streamingRepoPath);

        if (subscription.startTime() != null) {
            eventStream.setStartTime(subscription.startTime());
        }
        if (subscription.endTime() != null) {
            eventStream.setEndTime(subscription.endTime());
        }
        for (String eventType : subscription.eventTypes()) {
            eventStream.onEvent(eventType, this::bufferEvent);
        }

        eventStream.onFlush(() -> flush(consumer));

        eventStream.onClose(() -> {
            flush(consumer);
            try {
                onComplete.run();
            } catch (Exception e) {
                LOG.info("Observer already closed on stream end: subscription={}", subscription);
            }
        });

        eventStream.onError(t -> {
            LOG.error("Error in subscriber event stream: subscription={}", subscription, t);
            try {
                onError.accept(t);
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

    private void flush(Consumer<EventBatch> consumer) {
        if (alreadyClosed.get()) {
            return;
        }
        if (!buffer.isEmpty() || subscription.sendEmptyBatches()) {
            try {
                consumer.accept(EventBatch.newBuilder().addAllEvents(buffer).build());
            } catch (Exception e) {
                LOG.warn("Failed to send batch, closing stream: subscription={}", subscription);
                close();
            }
            buffer.clear();
        }
    }

    @Override
    public void close() {
        LOG.info("Closing subscriber event stream: subscription={}", subscription);
        if (alreadyClosed.compareAndSet(false, true) && eventStream != null) {
            eventStream.close();
            onClose.run();
        }
    }
}
