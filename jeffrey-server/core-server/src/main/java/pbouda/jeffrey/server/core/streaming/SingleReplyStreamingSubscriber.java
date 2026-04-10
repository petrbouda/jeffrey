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
import pbouda.jeffrey.shared.common.compression.Lz4Compressor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Reads events from a single JFR recording file (.jfr / .jfr.lz4) and delivers them
 * as batched {@link EventBatch} messages using {@link EventStream#openFile(Path)}.
 * LZ4-compressed files are decompressed to the provided temp directory before reading.
 *
 * <p>Uses EventStream instead of RecordingFile for better resilience against
 * corrupted JFR chunks — EventStream reports errors via {@code onError()} and
 * can continue processing subsequent chunks.</p>
 */
public class SingleReplyStreamingSubscriber {

    private static final Logger LOG = LoggerFactory.getLogger(SingleReplyStreamingSubscriber.class);

    private static final int BATCH_SIZE = 100;

    private final ReplayStreamSubscription subscription;
    private final Path tempDir;
    private final Consumer<EventBatch> consumer;
    private final Consumer<Throwable> onError;
    private final Supplier<Boolean> isClosed;

    public SingleReplyStreamingSubscriber(
            ReplayStreamSubscription subscription,
            Path tempDir,
            Consumer<EventBatch> consumer,
            Consumer<Throwable> onError,
            Supplier<Boolean> isClosed) {

        this.subscription = subscription;
        this.tempDir = tempDir;
        this.consumer = consumer;
        this.onError = onError;
        this.isClosed = isClosed;
    }

    /**
     * Reads all matching events from the given file and delivers them in batches.
     */
    public void read(Path file) throws IOException {
        Path readPath = file;

        if (Lz4Compressor.isLz4Compressed(file)) {
            readPath = tempDir.resolve(file.getFileName().toString().replace(".lz4", ""));
            Lz4Compressor.decompress(file, readPath);
        }

        List<StreamingEvent> buffer = new ArrayList<>(BATCH_SIZE);

        try (EventStream stream = EventStream.openFile(readPath)) {
            if (subscription.window().startTime() != null) {
                stream.setStartTime(subscription.window().startTime());
            }
            if (subscription.window().endTime() != null) {
                stream.setEndTime(subscription.window().endTime());
            }

            for (String eventType : subscription.eventTypes()) {
                stream.onEvent(eventType, event -> bufferEvent(event, buffer));
            }

            stream.onFlush(() -> {
                if (!buffer.isEmpty() && !isClosed.get()) {
                    flush(buffer);
                }
            });

            stream.onError(t -> {
                LOG.warn("Error in recording file, skipping chunk: file={} error={}",
                        file.getFileName(), t.getMessage());
                onError.accept(t);
            });

            stream.start();
        }

        if (!buffer.isEmpty() && !isClosed.get()) {
            flush(buffer);
        }
    }

    private void bufferEvent(RecordedEvent event, List<StreamingEvent> buffer) {
        if (isClosed.get()) {
            return;
        }

        try {
            buffer.add(RecordedEventMapper.toStreamingEvent(subscription.sessionId(), event));

            if (buffer.size() >= BATCH_SIZE) {
                flush(buffer);
            }
        } catch (Exception e) {
            LOG.warn("Failed to map event: eventType={}", event.getEventType().getName(), e);
        }
    }

    private void flush(List<StreamingEvent> buffer) {
        consumer.accept(EventBatch.newBuilder().addAllEvents(buffer).build());
        buffer.clear();
    }
}
