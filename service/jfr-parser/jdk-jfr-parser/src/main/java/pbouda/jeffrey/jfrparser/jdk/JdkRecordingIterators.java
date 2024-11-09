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

import pbouda.jeffrey.common.Collector;
import pbouda.jeffrey.common.JfrFileUtils;
import pbouda.jeffrey.jfrparser.api.EventProcessor;
import pbouda.jeffrey.jfrparser.api.ParallelRecordingFileIterator;
import pbouda.jeffrey.jfrparser.api.RecordingFileIterator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class JdkRecordingIterators {

    /**
     * Automatically decides the best way to iterate over the recordings. If there is only one recording, it will
     * pick the {@link #single(Path, EventProcessor)}. If the profile's folder contains multiple JFR files
     * (very likely after splitting the bigger JFR file into a smaller chunks) it will choose the
     * {@link #parallel(List, Supplier)} and processes the multiple chunks in parallel.
     *
     * @param recordings        path to all recordings in profile's workspace JFR files.
     * @param processorSupplier creates a processor to collect events from JFR file and transform them into an output.
     * @param <PARTIAL>         result of the single recording file
     * @param <RESULT>          collected result of all recording files
     * @return output from the iterating over the processor
     */
    public static <PARTIAL, RESULT> RecordingFileIterator<PARTIAL, RESULT> automatic(
            List<Path> recordings, Supplier<? extends EventProcessor<PARTIAL>> processorSupplier) {

        if (recordings.size() > 1) {
            return parallel(recordings, processorSupplier);
        } else {
            return single(recordings.getFirst(), processorSupplier.get());
        }
    }

    /**
     * Utility method to {@link #automatic(List, Supplier)} that automatically iterates over the recordings
     * and collect the output from the processor.
     *
     * @param recordings        path to all recordings in profile's workspace JFR files.
     * @param processorSupplier creates a processor to collect events from JFR file and transform them into an output.
     * @param collector         to automatically merge partial results and transform it into a final result.
     * @param <PARTIAL>         result of the single recording file
     * @param <RESULT>          collected result of all recording files
     * @return output from the iterating over the processor
     */
    public static <PARTIAL, RESULT> RESULT automaticAndCollect(
            List<Path> recordings,
            Supplier<? extends EventProcessor<PARTIAL>> processorSupplier,
            Collector<PARTIAL, RESULT> collector) {

        RecordingFileIterator<PARTIAL, RESULT> iterator = automatic(recordings, processorSupplier);
        return iterator.collect(collector);
    }

    /**
     * Utility method to {@link #automatic(List, Supplier)} that automatically iterates over the recordings
     * and collect the output from the processor.
     *
     * @param recordings        path to all recordings in profile's workspace JFR files.
     * @param processorSupplier creates a processor to collect events from JFR file and transform them into an output.
     * @param collector         to automatically merge partial results and transform it into a final result.
     * @param <PARTIAL>         collected result of all recording files
     * @return output from the iterating over the processor
     */
    public static <PARTIAL> PARTIAL automaticAndCollectPartial(
            List<Path> recordings,
            Supplier<? extends EventProcessor<PARTIAL>> processorSupplier,
            Collector<PARTIAL, PARTIAL> collector) {

        RecordingFileIterator<PARTIAL, PARTIAL> iterator = automatic(recordings, processorSupplier);
        return iterator.partialCollect(collector);
    }

    /**
     * Iterates over a single recording in the profile's workspace JFR files and applies the processor on each event
     * to generate the desired output. All events are processed sequentially. The output is automatically collected
     * and returned without any modification.
     * <p/>
     * It's able to identify whether the provided path is a directory or a single file. If it's a directory, it will
     * choose the first file alphabetically and process it.
     *
     * @param recording path to a single recording or directory with JFR files (very likely chunks).
     * @param processor creates a processor to collect events from JFR file and transform them into an output.
     * @param <RESULT>  collected result of all recording files
     * @return output from the iterating over the processor
     */
    public static <RESULT> RESULT fileOrDirAndCollectIdentical(Path recording, EventProcessor<RESULT> processor) {
        Path target = Files.isDirectory(recording) ? JfrFileUtils.findFirstJfrFile(recording) : recording;
        return singleAndCollectIdentical(target, processor);
    }

    /**
     * Iterates over a single recording in the profile's workspace JFR files and applies the processor on each event
     * to generate the desired output. All events are processed sequentially. The output is automatically collected
     * and returned without any modification.
     *
     * @param recording path to a single recording in profile's workspace JFR files.
     * @param processor creates a processor to collect events from JFR file and transform them into an output.
     * @param <RESULT>  collected result of all recording files
     * @return output from the iterating over the processor
     */
    public static <RESULT> RESULT singleAndCollectIdentical(Path recording, EventProcessor<RESULT> processor) {
        RecordingFileIterator<RESULT, RESULT> iterator = single(recording, processor);
        return iterator.partialCollect();
    }

    /**
     * Iterates over a single recording in the profile's workspace JFR files and applies the processor on each event
     * to generate the desired output. All events are processed sequentially.
     *
     * @param recording path to a single recording in profile's workspace JFR files.
     * @param processor a processor to collect events from JFR file and transform them into an output.
     * @param <PARTIAL> result of the single recording file
     * @param <RESULT>  collected result of all recording files
     * @return output from the iterating over the processor
     */
    public static <PARTIAL, RESULT> RecordingFileIterator<PARTIAL, RESULT> single(
            Path recording, EventProcessor<PARTIAL> processor) {

        return new JdkRecordingFileIterator<>(recording, processor);
    }

    /**
     * Iterates over multiple recordings in the profile's workspace JFR files and applies the processor on each event
     * to generate the desired output. JFR files can be processed in parallel depending on the number of CPUs and
     * the implementation.
     *
     * @param recordings        path to all recordings in profile's workspace JFR files.
     * @param processorSupplier creates a processor to collect events from JFR file and transform them into an output.
     * @param <PARTIAL>         result of the single recording file
     * @param <RESULT>          collected result of all recording files
     * @return output from the iterating over the processor
     */
    public static <PARTIAL, RESULT> RecordingFileIterator<PARTIAL, RESULT> parallel(
            List<Path> recordings, Supplier<? extends EventProcessor<PARTIAL>> processorSupplier) {

        Function<Path, RecordingFileIterator<PARTIAL, PARTIAL>> singleIterator =
                recording -> new JdkRecordingFileIterator<>(recording, processorSupplier.get());
        return new ParallelRecordingFileIterator<>(recordings, singleIterator);
    }
}
