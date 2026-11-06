/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.parser.data;

import cafe.jeffrey.jfrparser.raw.JfrChunk;
import cafe.jeffrey.jfrparser.raw.JfrParser;
import cafe.jeffrey.shared.common.model.EventTypeName;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A recording's chunks grouped by the profiler that wrote them.
 *
 * <p>A file can hold two chains of chunks that overlap in time: async-profiler started with
 * {@code jfrsync} writes the JDK's own recording into the same file as its own. The two define some
 * event types under the same name with different fields ({@code jdk.ActiveSetting},
 * {@code jdk.ActiveRecording}), and JMC, loading chunks in parallel, keeps whichever definition
 * reaches it first under the name and renames the other — so a rule reading the recording's
 * settings sees one chain's on one load and the other's on the next. Kept apart, each chain is read
 * alike every time.
 *
 * <p>A file whose chunks one profiler wrote is kept as it lies; only a file holding both is cut into
 * its chunks.
 */
final class RecordingChains {

    /**
     * Who wrote a chain of chunks. Declared in the order the chains are loaded in: the JDK's own
     * first, because some JMC rules take the first recording's facts they meet (its start, its length)
     * and were written against the JVM's own recording.
     */
    enum Producer {
        JDK,
        ASYNC_PROFILER
    }

    private static final String FILE_DIRECTORY = "file-";

    private RecordingChains() {
    }

    /**
     * @param recordings the recording's files
     * @param scratch    where a file holding more than one chain is cut into its chunks
     * @return each producer's files, in {@link Producer} order (the JDK's first); one entry when one
     *         profiler wrote every chunk
     */
    static Map<Producer, List<Path>> of(List<Path> recordings, Path scratch) {
        Map<Producer, List<Path>> chains = new EnumMap<>(Producer.class);
        for (int i = 0; i < recordings.size(); i++) {
            Path file = recordings.get(i);
            List<Producer> producers = JfrParser.collectChunks(file).stream()
                    .map(RecordingChains::producerOf)
                    .toList();
            if (producers.stream().distinct().count() <= 1) {
                Producer producer = producers.isEmpty() ? Producer.JDK : producers.getFirst();
                chains.computeIfAbsent(producer, _ -> new ArrayList<>()).add(file);
                continue;
            }
            List<Path> chunks = JfrParser.disassemble(file, scratch.resolve(FILE_DIRECTORY + i));
            for (int chunk = 0; chunk < chunks.size(); chunk++) {
                chains.computeIfAbsent(producers.get(chunk), _ -> new ArrayList<>()).add(chunks.get(chunk));
            }
        }
        return chains;
    }

    /** async-profiler declares its own {@code profiler.} event types in every chunk it writes; the JDK none. */
    private static Producer producerOf(JfrChunk chunk) {
        boolean asyncProfiler = chunk.eventTypes().stream()
                .anyMatch(name -> name.startsWith(EventTypeName.ASYNC_PROFILER_NAMESPACE));
        return asyncProfiler ? Producer.ASYNC_PROFILER : Producer.JDK;
    }
}
