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

public abstract class AbstractRecordingFileIterator<T> implements RecordingFileIterator<T, T> {

    private final Path recording;

    protected AbstractRecordingFileIterator(Path recording) {
        this.recording = recording;
    }

    @Override
    public T collect(Collector<T, T> collector) {
        return collector.finisher(_iterate());
    }

    @Override
    public T partialCollect(Collector<T, ?> collector) {
        _iterate();
        return _iterate();
    }

    protected abstract T iterate();

    private T _iterate() {
        if (!Files.exists(recording)) {
            throw new RuntimeException("File does not exists: " + recording);
        }
        return iterate();
    }
}
