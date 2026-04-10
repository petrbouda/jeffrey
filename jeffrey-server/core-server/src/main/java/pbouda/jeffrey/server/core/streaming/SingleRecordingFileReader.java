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

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
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
 * as batched {@link EventBatch} messages. LZ4-compressed files are decompressed
 * to the provided temp directory before reading.
 */
public class SingleRecordingFileReader {

    private static final int BATCH_SIZE = 100;

    private final ReplayStreamSubscription subscription;
    private final Path tempDir;
    private final Consumer<EventBatch> consumer;
    private final Supplier<Boolean> isClosed;

    public SingleRecordingFileReader(
            ReplayStreamSubscription subscription,
            Path tempDir,
            Consumer<EventBatch> consumer,
            Supplier<Boolean> isClosed) {

        this.subscription = subscription;
        this.tempDir = tempDir;
        this.consumer = consumer;
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

        try (RecordingFile recordingFile = new RecordingFile(readPath)) {
            while (recordingFile.hasMoreEvents() && !isClosed.get()) {
                RecordedEvent event = recordingFile.readEvent();

                if (!matchesEventType(event) || !inStreamingWindow(event)) {
                    continue;
                }

                buffer.add(RecordedEventMapper.toStreamingEvent(subscription.sessionId(), event));

                if (buffer.size() >= BATCH_SIZE) {
                    flush(buffer);
                }
            }
        }

        if (!buffer.isEmpty() && !isClosed.get()) {
            flush(buffer);
        }
    }

    private boolean matchesEventType(RecordedEvent event) {
        return subscription.eventTypes().isEmpty()
                || subscription.eventTypes().contains(event.getEventType().getName());
    }

    private boolean inStreamingWindow(RecordedEvent event) {
        return subscription.window().contains(event.getStartTime());
    }

    private void flush(List<StreamingEvent> buffer) {
        consumer.accept(EventBatch.newBuilder().addAllEvents(buffer).build());
        buffer.clear();
    }
}
