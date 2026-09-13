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

package cafe.jeffrey.hub.core.streaming;

import cafe.jeffrey.hub.api.v1.EventBatch;
import cafe.jeffrey.hub.api.v1.StreamingEvent;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
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

    private static final int BATCH_SIZE = 1000;

    private final ReplayStreamSubscription subscription;
    private final Path tempDir;
    private final Consumer<EventBatch> consumer;
    private final Supplier<Boolean> isClosed;
    private final Runnable sourceError;
    private final BiConsumer<String, Instant> metadataConsumer;
    private final AtomicReference<EventStream> activeStream = new AtomicReference<>();

    public SingleReplyStreamingSubscriber(
            ReplayStreamSubscription subscription,
            Path tempDir,
            Consumer<EventBatch> consumer,
            Supplier<Boolean> isClosed) {

        this(subscription, tempDir, consumer, isClosed, () -> {});
    }

    public SingleReplyStreamingSubscriber(ReplayStreamSubscription subscription, Path tempDir,
                                         Consumer<EventBatch> consumer, Supplier<Boolean> isClosed, Runnable sourceError) {
        this(subscription, tempDir, consumer, isClosed, sourceError, null);
    }

    SingleReplyStreamingSubscriber(
            ReplayStreamSubscription subscription,
            Path tempDir,
            Consumer<EventBatch> consumer,
            Supplier<Boolean> isClosed,
            Runnable sourceError,
            BiConsumer<String, Instant> metadataConsumer) {
        this.metadataConsumer = metadataConsumer;
        this.sourceError = sourceError;
        this.subscription = subscription;
        this.tempDir = tempDir;
        this.consumer = consumer;
        this.isClosed = isClosed;
    }

    /**
     * Reads all matching events from the given file and delivers them in batches.
     */
    public void read(Path file) throws IOException {
        if (isClosed.get()) {
            return;
        }
        Path readPath = file;

        if (Lz4Compressor.isLz4Compressed(file)) {
            readPath = tempDir.resolve(file.getFileName().toString().replace(".lz4", ""));
            try (InputStream input = Lz4Compressor.decompressStream(file);
                 OutputStream output = Files.newOutputStream(readPath)) {
                byte[] bytes = new byte[65536];
                int count;
                while (!isClosed.get() && (count = input.read(bytes)) != -1) {
                    output.write(bytes, 0, count);
                }
            }
        }
        if (isClosed.get()) {
            return;
        }

        List<StreamingEvent> buffer = new ArrayList<>(BATCH_SIZE);
        if (subscription.reportCoverage()) {
            // EventStream may silently accept an invalid/truncated file without onError.
            // Scoped queries need strict parsing before they can claim complete coverage.
            try (RecordingFile recording = new RecordingFile(readPath)) {
                while (!isClosed.get() && recording.hasMoreEvents()) {
                    RecordedEvent event = recording.readEvent();
                    if ((subscription.eventTypes().isEmpty() || subscription.eventTypes().contains(event.getEventType().getName()))
                            && subscription.window().contains(event.getStartTime())) {
                        if (metadataConsumer == null) {
                            bufferEvent(event, buffer);
                        } else {
                            // Aggregation needs no field/stack conversion or intermediate event batches.
                            metadataConsumer.accept(event.getEventType().getName(), event.getStartTime());
                        }
                    }
                }
                if (!isClosed.get() && !buffer.isEmpty()) {
                    flush(buffer);
                }
            } finally {
                if (!readPath.equals(file)) {
                    Files.deleteIfExists(readPath);
                }
            }
            return;
        }

        try (EventStream stream = EventStream.openFile(readPath)) {
            activeStream.set(stream);
            if (isClosed.get()) {
                return;
            }
            if (subscription.window().startTime() != null) {
                stream.setStartTime(subscription.window().startTime());
            }
            if (subscription.window().endTime() != null) {
                stream.setEndTime(subscription.window().endTime());
            }

            for (String eventType : subscription.eventTypes()) {
                stream.onEvent(eventType, event -> bufferEvent(event, buffer));
            }

            // Chunk-level errors are recoverable: EventStream skips the corrupted chunk and
            // continues with the next one. They must never reach the terminal gRPC onError —
            // that would close the call while more events are still being delivered.
            stream.onError(t -> {
                sourceError.run();
                LOG.warn("Error in recording file, skipping chunk: file={} error={}", file.getFileName(), t.getMessage());
            });

            stream.onClose(() -> {
                if (!buffer.isEmpty() && !isClosed.get()) {
                    flush(buffer);
                }
            });

            stream.start();
        } finally {
            activeStream.set(null);
            if (!readPath.equals(file)) {
                Files.deleteIfExists(readPath);
            }
        }
    }

    /** Stops the current file; decompression checks cancellation between bounded buffer reads. */
    public void close() {
        EventStream stream = activeStream.get();
        if (stream != null) {
            stream.close();
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
            sourceError.run();
            LOG.warn("Failed to map event: eventType={}", event.getEventType().getName(), e);
        }
    }

    private void flush(List<StreamingEvent> buffer) {
        consumer.accept(EventBatch.newBuilder().addAllEvents(buffer).build());
        buffer.clear();
    }
}
