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

import cafe.jeffrey.jfrparser.raw.JfrParser;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.test.recording.RecordedSession;
import cafe.jeffrey.test.recording.TestappRecording;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RecordingChainsTest {

    @TempDir
    Path dir;

    /** A file only async-profiler wrote is one chain, kept as it lies: nothing is copied. */
    @Test
    void oneProfilersFileIsOneChainKeptAsItLies() throws IOException {
        Path file = Files.write(dir.resolve("chunk.jfr"), RecordedSession.CHUNKS.getFirst().bytes());

        Map<RecordingEventSource, List<Path>> chains = RecordingChains.of(List.of(file), dir.resolve("scratch"));

        assertEquals(Map.of(RecordingEventSource.ASYNC_PROFILER, List.of(file)), chains);
        assertFalse(Files.exists(dir.resolve("scratch")));
    }

    /**
     * A {@code jfrsync} file is cut into its chunks, each to the chain of the profiler that wrote
     * it: the JDK's two, async-profiler's one, each beginning where the file says it did.
     */
    @Test
    void aJfrsyncFileIsCutIntoItsTwoChains() throws IOException {
        TestappRecording recording = TestappRecording.DIRECT;
        Path file = Files.write(dir.resolve(TestappRecording.RECORDING_FILE), recording.bytes());

        Map<RecordingEventSource, List<Path>> chains = RecordingChains.of(List.of(file), dir.resolve("scratch"));

        List<Instant> starts = recording.chunkStarts();
        assertEquals(List.of(starts.get(0), starts.get(1)), startsOf(chains.get(RecordingEventSource.JDK)));
        assertEquals(List.of(starts.get(TestappRecording.PROFILER_CHUNK)),
                startsOf(chains.get(RecordingEventSource.ASYNC_PROFILER)));
    }

    private static List<Instant> startsOf(List<Path> files) {
        return files.stream()
                .map(file -> JfrParser.collectChunks(file).getFirst().startTime())
                .toList();
    }
}
