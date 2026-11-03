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

package cafe.jeffrey.jfrparser.raw;

import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.provider.profile.api.RecordingInformation;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.test.recording.PersonsRecording;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static cafe.jeffrey.test.recording.PersonsRecording.EXECUTION_SAMPLE;
import static cafe.jeffrey.test.recording.PersonsRecording.WALL_CLOCK_SAMPLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The raw parser over realistic recordings: files a profiler with {@code jfrsync} wrote, each two
 * chains of chunks that overlap in time — the JDK's own and async-profiler's — and kept
 * LZ4-compressed as the hub leaves them. Every figure is the file's own, as {@link PersonsRecording}
 * pins it.
 */
class JfrParserRealisticRecordingTest {

    private static final String JDK_GC_EVENT = "jdk.GCPhaseParallel";

    @TempDir
    Path dir;

    /** The file as kept, compressed. */
    private Path archive(PersonsRecording recording) throws IOException {
        return Files.write(dir.resolve(recording.fileName()), recording.bytes());
    }

    /** The same file, decompressed. */
    private Path plain(PersonsRecording recording) throws IOException {
        Path plain = dir.resolve("plain.jfr");
        Lz4Compressor.decompress(archive(recording), plain);
        return plain;
    }

    /** Both chains, in the order the file holds them, each chunk where its header says it began. */
    @ParameterizedTest
    @EnumSource(PersonsRecording.class)
    void readsEveryChunkOfBothChains(PersonsRecording recording) throws IOException {
        Path plain = plain(recording);

        List<JfrChunk> chunks = JfrParser.collectChunks(archive(recording));

        assertEquals(recording.chunkStarts(), chunks.stream()
                .map(chunk -> chunk.startTime().truncatedTo(ChronoUnit.SECONDS))
                .toList());
        assertEquals(Files.size(plain), chunks.stream().mapToLong(JfrChunk::sizeInBytes).sum());
    }

    /**
     * The recording runs from the earliest chunk's start to the latest chunk's end. Its chains
     * overlap, so that is far shorter than their durations added up, which is what reading the
     * chunks one after another would make of it.
     */
    @ParameterizedTest
    @EnumSource(PersonsRecording.class)
    void theRecordingSpansItsChunksWithoutAddingUpTheirOverlap(PersonsRecording recording) throws IOException {
        Path archive = archive(recording);

        RecordingInformation information = JfrParser.recordingInfo(archive);
        Duration added = JfrParser.collectChunks(archive).stream()
                .map(JfrChunk::duration)
                .reduce(Duration.ZERO, Duration::plus);

        assertEquals(recording.recordingStartedAt(), information.recordingStartedAt().truncatedTo(ChronoUnit.MILLIS));
        assertEquals(recording.recordingFinishedAt(), information.recordingFinishedAt().truncatedTo(ChronoUnit.MILLIS));
        Duration span = Duration.between(information.recordingStartedAt(), information.recordingFinishedAt());
        assertTrue(added.compareTo(span.plus(span.dividedBy(2))) > 0, () -> span + " against " + added);
        assertEquals(RecordingEventSource.ASYNC_PROFILER, information.eventSource());
    }

    /** The profiler's types and the JDK's both, from the two chains' own metadata. */
    @ParameterizedTest
    @EnumSource(PersonsRecording.class)
    void namesTheEventTypesOfBothChains(PersonsRecording recording) throws IOException {
        Set<String> types = JfrParser.eventTypes(archive(recording));

        assertTrue(types.containsAll(Set.of(EXECUTION_SAMPLE, WALL_CLOCK_SAMPLE, JDK_GC_EVENT)), types::toString);
    }

    /** Compressed or not, the file is the same recording. */
    @ParameterizedTest
    @EnumSource(PersonsRecording.class)
    void readsTheArchiveAsTheFileItCompresses(PersonsRecording recording) throws IOException {
        assertEquals(JfrParser.recordingInfo(plain(recording)), JfrParser.recordingInfo(archive(recording)));
        assertEquals(JfrParser.eventTypes(plain(recording)), JfrParser.eventTypes(archive(recording)));
    }

    /**
     * A file channel reads only a plain recording; handed an archive it would find no chunk header
     * and read nothing, so it is refused by name instead.
     */
    @ParameterizedTest
    @EnumSource(PersonsRecording.class)
    void refusesToReadAnArchiveThroughAFileChannel(PersonsRecording recording) throws IOException {
        Path archive = archive(recording);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> JfrParser.iterateChunks(archive, (_, _) -> { }));

        assertTrue(refused.getMessage().contains("LZ4-compressed"), refused.getMessage());
    }

    /** Cut apart, each chunk is a whole recording of one chunk, beginning where the file said it did. */
    @ParameterizedTest
    @EnumSource(PersonsRecording.class)
    void disassemblesIntoOneSelfContainedFilePerChunk(PersonsRecording recording) throws IOException {
        List<Path> pieces = JfrParser.disassemble(archive(recording), dir.resolve("pieces"));

        assertEquals(recording.chunkStarts().size(), pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            List<JfrChunk> chunks = JfrParser.collectChunks(pieces.get(i));
            assertEquals(1, chunks.size(), pieces.get(i)::toString);
            Instant start = chunks.getFirst().startTime().truncatedTo(ChronoUnit.SECONDS);
            assertEquals(recording.chunkStarts().get(i), start, pieces.get(i)::toString);
        }
    }
}
