/*
 * Jeffrey
 * Copyright (C) 2024 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.jfrparser.jdk;

import jdk.jfr.consumer.EventStream;
import cafe.jeffrey.microscope.model.Type;

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
