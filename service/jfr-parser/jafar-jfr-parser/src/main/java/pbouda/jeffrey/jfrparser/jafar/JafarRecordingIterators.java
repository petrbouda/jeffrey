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

import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.jfrparser.api.ParallelRecordingFileIterator;
import pbouda.jeffrey.jfrparser.api.RecordingFileIterator;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public abstract class JafarRecordingIterators {

    public static <PARTIAL, RESULT> RecordingFileIterator<PARTIAL, RESULT> automatic(
            List<Path> recordings, Function<Path, RecordingFileIterator<PARTIAL, PARTIAL>> singleIterator) {

//        if (recordings.size() > 1) {
        return parallel(recordings, singleIterator);
//        } else {
//            return singleIterator.apply(recordings.getFirst());
//        }
    }

    public static <PARTIAL, RESULT> RESULT automaticAndCollect(
            List<Path> recordings,
            Function<Path, RecordingFileIterator<PARTIAL, PARTIAL>> singleIterator,
            Collector<PARTIAL, RESULT> collector) {

        RecordingFileIterator<PARTIAL, RESULT> iterator = automatic(recordings, singleIterator);
        return iterator.collect(collector);
    }

    public static <PARTIAL, RESULT> RecordingFileIterator<PARTIAL, RESULT> parallel(
            List<Path> recordings, Function<Path, RecordingFileIterator<PARTIAL, PARTIAL>> singleIterator) {

        return new ParallelRecordingFileIterator<>(recordings, singleIterator);
    }
}
