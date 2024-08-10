/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
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

package pbouda.jeffrey.jfrparser.jdk;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import pbouda.jeffrey.jfrparser.jdk.EventProcessor.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class SingleRecordingFileIterator<PARTIAL, RESULT> implements RecordingFileIterator<PARTIAL, RESULT> {

    private final Path recording;
    private final EventProcessor<PARTIAL> processor;

    public SingleRecordingFileIterator(Path recording, EventProcessor<PARTIAL> processor) {
        this.recording = recording;
        this.processor = processor;
    }

    public RESULT collect(Collector<PARTIAL, ?, RESULT> collector) {
        Objects.requireNonNull(processor, "processor needs to be added to constructor to be able to collect result");

        _iterate(processor);
        return Stream.of(processor.get()).collect(collector);
    }

    private void _iterate(EventProcessor<PARTIAL> eventProcessor) {
        if (!Files.exists(recording)) {
            throw new RuntimeException("File does not exists: " + recording);
        }

        try (RecordingFile rec = new RecordingFile(recording)) {
            eventProcessor.onStart();
            while (rec.hasMoreEvents()) {
                RecordedEvent event = rec.readEvent();
                if (eventProcessor.processableEvents().isProcessable(event.getEventType())) {
                    Result result = eventProcessor.onEvent(event);
                    if (result == Result.DONE) {
                        break;
                    }
                }
            }
            eventProcessor.onComplete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
