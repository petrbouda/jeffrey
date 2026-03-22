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

import jdk.jfr.consumer.EventStream;
import pbouda.jeffrey.shared.common.model.Type;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JdkRecordingFileIterator<PARTIAL, RESULT> implements RecordingFileIterator<PARTIAL, RESULT> {

    private final Path recording;
    private final EventProcessor<PARTIAL> processor;

    public JdkRecordingFileIterator(Path recording, EventProcessor<PARTIAL> processor) {
        this.recording = recording;
        this.processor = processor;
    }

    @Override
    public RESULT collect(Collector<PARTIAL, RESULT> collector) {
        _iterate(processor);
        return collector.finisher(processor.get());
    }

    @Override
    public PARTIAL partialCollect(Collector<PARTIAL, ?> collector) {
        _iterate(processor);
        return processor.get();
    }

    private void _iterate(EventProcessor<PARTIAL> eventProcessor) {
        if (!Files.exists(recording)) {
            throw new RuntimeException("File does not exists: " + recording);
        }

        ProcessableEvents processableEvents = eventProcessor.processableEvents();

        eventProcessor.onStart();

        try (EventStream stream = EventStream.openFile(recording)) {
            stream.onMetadata(metadata -> {
                eventProcessor.onMetadata(metadata.getEventTypes());
            });

            if (processableEvents.isProcessableAll()) {
                stream.onEvent(eventProcessor::onEvent);
            } else {
                for (Type event : processableEvents.events()) {
                    stream.onEvent(event.code(), eventProcessor::onEvent);
                }
            }

            stream.start();
            eventProcessor.onComplete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
