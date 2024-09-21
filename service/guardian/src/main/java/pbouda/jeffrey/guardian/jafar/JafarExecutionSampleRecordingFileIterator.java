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

package pbouda.jeffrey.guardian.jafar;

import io.jafar.parser.api.JafarParser;
import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.frameir.Frame;
import pbouda.jeffrey.frameir.record.ExecutionSampleRecord;
import pbouda.jeffrey.frameir.tree.SimpleTreeBuilder;
import pbouda.jeffrey.jfrparser.api.RecordingFileIterator;

import java.nio.file.Files;
import java.nio.file.Path;

public class JafarExecutionSampleRecordingFileIterator implements RecordingFileIterator<Frame, Frame> {

    private final Path recording;

    private final SimpleTreeBuilder builder = new SimpleTreeBuilder(false);

    public JafarExecutionSampleRecordingFileIterator(Path recording) {
        this.recording = recording;
    }

    @Override
    public Frame collect(Collector<Frame, Frame> collector) {
        _iterate();
        return collector.finisher(builder.build());
    }

    @Override
    public Frame partialCollect(Collector<Frame, ?> collector) {
        _iterate();
        return builder.build();
    }

    private void _iterate() {
        if (!Files.exists(recording)) {
            throw new RuntimeException("File does not exists: " + recording);
        }

        try (JafarParser parser = JafarParser.open(recording.toString())) {
            parser.handle(ExecutionSampleEvent.class, (event, ctl) -> {
                JafarStackTrace stackTrace = new JafarStackTrace(event.stackTrace());
                builder.addRecord(new ExecutionSampleRecord(stackTrace));
            });
            parser.run();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
