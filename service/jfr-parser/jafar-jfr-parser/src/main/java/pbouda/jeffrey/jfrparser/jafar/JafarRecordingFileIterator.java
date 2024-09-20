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

package pbouda.jeffrey.jfrparser.jafar;

import io.jafar.parser.api.JafarParser;
import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.jfrparser.api.RecordingFileIterator;

import java.nio.file.Files;
import java.nio.file.Path;

public class JafarRecordingFileIterator<PARTIAL, RESULT> implements RecordingFileIterator<PARTIAL, RESULT> {

    private final Path recording;
    private final EventProcessor<PARTIAL> processor;

    public JafarRecordingFileIterator(Path recording, EventProcessor<PARTIAL> processor) {
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

        try (JafarParser parser = JafarParser.open("path_to_jfr.jfr")) {
            // registering a handler will return a cookie which can be used to deregister the same handler
            var cookie = parser.handle(MyEvent.class, event -> {
                System.out.println(event.startTime());
                System.out.println(event.eventThread().javaName());
                System.out.println(event.myfield());
            });
            parser.handle(MyEvent.class, event -> {
                // do something else
            });
            parser.run();

            cookie.destroy(parser);
            // this time only the second handler will be called
            parser.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
