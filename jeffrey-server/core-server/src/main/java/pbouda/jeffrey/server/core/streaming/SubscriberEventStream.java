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

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
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
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Per-subscriber JFR EventStream that opens its own repository, filters by event types,
 * and micro-batches events using JFR's own {@code onFlush()} callback.
 *
 * <p>{@code onEvent()} and {@code onFlush()} are called on the same EventStream thread,
 * so no concurrency control is needed — a plain {@link ArrayList} is used as the buffer.</p>
 */
public class SubscriberEventStream implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(SubscriberEventStream.class);

    private static final String STREAMING_REPO_DIR = "streaming-repo";

    private final String sessionId;
    private final Path sessionPath;
    private final Set<String> eventTypes;
    private final Instant startTime;
    private final Instant endTime;
    private final boolean sendEmptyBatches;
    private final StreamObserver<EventBatch> observer;
    private final Clock clock;
    private final Consumer<SubscriberEventStream> onCompletion;
    private final List<StreamingEvent> buffer = new ArrayList<>();
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private boolean closing;

    private EventStream eventStream;

    public SubscriberEventStream(
            String sessionId,
            Path sessionPath,
            Set<String> eventTypes,
            Instant startTime,
            Instant endTime,
            boolean sendEmptyBatches,
            StreamObserver<EventBatch> observer,
            Clock clock,
            Consumer<SubscriberEventStream> onCompletion) {

        this.sessionId = sessionId;
        this.sessionPath = sessionPath;
        this.eventTypes = eventTypes;
        this.startTime = startTime;
        this.endTime = endTime;
        this.sendEmptyBatches = sendEmptyBatches;
        this.observer = observer;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.onCompletion = Objects.requireNonNull(onCompletion, "onCompletion");
    }

    public String sessionId() {
        return sessionId;
    }

    /**
     * Opens the streaming repository, configures event handlers,
     * and starts consuming events asynchronously on a virtual thread.
     *
     * @throws IOException if the streaming repository cannot be opened
     */
    public void start() throws IOException {
        Path streamingRepoPath = sessionPath.resolve(STREAMING_REPO_DIR);

        LOG.debug("Starting subscriber event stream: sessionId={} path={} eventTypes={} startTime={} endTime={}",
                sessionId, streamingRepoPath, eventTypes, startTime, endTime);

        this.eventStream = EventStream.openRepository(streamingRepoPath);

        if (startTime != null) {
            eventStream.setStartTime(startTime);
        }
        if (endTime != null) {
            eventStream.setEndTime(endTime);
        }

        if (eventTypes.isEmpty()) {
            throw new IllegalArgumentException("At least one event type must be specified");
        }
        for (String eventType : eventTypes) {
            eventStream.onEvent(eventType, this::bufferEvent);
        }

        eventStream.onFlush(this::flush);

        eventStream.onClose(() -> {
            flush();
            try {
                observer.onCompleted();
            } catch (Exception e) {
                LOG.info("Observer already closed on stream end: sessionId={}", sessionId);
            }
            fireCompletionOnce();
        });

        eventStream.onError(t -> {
            LOG.error("Error in subscriber event stream: sessionId={}", sessionId, t);
            try {
                observer.onError(Status.INTERNAL.withDescription(t.getMessage()).asRuntimeException());
            } catch (Exception e) {
                LOG.warn("Observer already closed on error: sessionId={}", sessionId);
            }
            fireCompletionOnce();
        });

        Schedulers.streamingExecutor().execute(eventStream::start);
    }

    private void bufferEvent(RecordedEvent event) {
        try {
            StreamingEvent streamingEvent = RecordedEventMapper.toStreamingEvent(sessionId, event);
            buffer.add(streamingEvent);
        } catch (Exception e) {
            LOG.error("Failed to map event: sessionId={} eventType={}", sessionId, event.getEventType().getName(), e);
        }
    }

    private void flush() {
        if (closing) {
            return;
        }
        if (!buffer.isEmpty() || sendEmptyBatches) {
            try {
                observer.onNext(EventBatch.newBuilder().addAllEvents(buffer).build());
            } catch (Exception e) {
                LOG.warn("Failed to send batch, closing stream: sessionId={}", sessionId);
                closeStream();
                return;
            }
            buffer.clear();
        }
        if (endTime != null && clock.instant().isAfter(endTime)) {
            LOG.info("End time reached (wall-clock), closing stream: sessionId={} endTime={}", sessionId, endTime);
            closeStream();
        }
    }

    private void closeStream() {
        if (!closing) {
            closing = true;
            eventStream.close();
        }
    }

    private void fireCompletionOnce() {
        if (completed.compareAndSet(false, true)) {
            try {
                onCompletion.accept(this);
            } catch (Exception e) {
                LOG.warn("onCompletion callback threw: sessionId={}", sessionId, e);
            }
        }
    }

    @Override
    public void close() {
        LOG.info("Closing subscriber event stream: sessionId={}", sessionId);
        if (eventStream != null) {
            eventStream.close();
        }
        fireCompletionOnce();
    }
}
