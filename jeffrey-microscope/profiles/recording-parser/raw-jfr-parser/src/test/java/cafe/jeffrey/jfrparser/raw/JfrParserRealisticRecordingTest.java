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
import cafe.jeffrey.test.recording.CheckoutRecording;
import cafe.jeffrey.test.recording.TestappRecording;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The raw parser over realistic recordings: files a profiler with {@code jfrsync} wrote, each two
 * chains of chunks that overlap in time — the JDK's own and async-profiler's — read plain, as the
 * provisioner leaves the session's newest recording, and LZ4-compressed, as the hub leaves the rest.
 * Every figure is the file's own, as {@link TestappRecording} pins it.
 */
class JfrParserRealisticRecordingTest {

    private static final String JDK_GC_EVENT = "jdk.GCPhaseParallel";

    @TempDir
    Path dir;

    /** The file as the provisioner left it. */
    private Path plain(TestappRecording recording) throws IOException {
        return Files.write(dir.resolve(recording.mode() + ".jfr"), recording.bytes());
    }

    /** The same file, compressed as the hub's compression job leaves it. */
    private Path archive(TestappRecording recording) throws IOException {
        return Lz4Compressor.compress(plain(recording), dir.resolve(recording.mode() + ".jfr.lz4"));
    }

    /** Both chains, in the order the file holds them, each chunk where its header says it began. */
    @ParameterizedTest
    @EnumSource(TestappRecording.class)
    void readsEveryChunkOfBothChains(TestappRecording recording) throws IOException {
        Path plain = plain(recording);

        List<JfrChunk> chunks = JfrParser.collectChunks(archive(recording));

        assertEquals(recording.chunkStarts(), chunks.stream().map(JfrChunk::startTime).toList());
        assertEquals(Files.size(plain), chunks.stream().mapToLong(JfrChunk::sizeInBytes).sum());
    }

    /**
     * The recording runs from the earliest chunk's start to the latest chunk's end. Its chains
     * overlap, so that is far shorter than their durations added up, which is what reading the
     * chunks one after another would make of it.
     */
    @ParameterizedTest
    @EnumSource(TestappRecording.class)
    void theRecordingSpansItsChunksWithoutAddingUpTheirOverlap(TestappRecording recording) throws IOException {
        Path plain = plain(recording);

        RecordingInformation information = JfrParser.recordingInfo(plain);
        Duration added = JfrParser.collectChunks(plain).stream()
                .map(JfrChunk::duration)
                .reduce(Duration.ZERO, Duration::plus);

        assertEquals(recording.recordingStartedAt(), information.recordingStartedAt());
        assertEquals(recording.recordingFinishedAt(), information.recordingFinishedAt());
        Duration span = Duration.between(information.recordingStartedAt(), information.recordingFinishedAt());
        assertTrue(added.compareTo(span.plus(span.dividedBy(2))) > 0, () -> span + " against " + added);
        assertEquals(RecordingEventSource.JFR, information.eventSource());
    }

    /** The profiler's types, the JDK's and the service's own, from the two chains' own metadata. */
    @ParameterizedTest
    @EnumSource(TestappRecording.class)
    void namesTheEventTypesOfBothChains(TestappRecording recording) throws IOException {
        Set<String> types = JfrParser.eventTypes(plain(recording));

        assertTrue(types.containsAll(Set.of(TestappRecording.EXECUTION_SAMPLE, TestappRecording.WALL_CLOCK_SAMPLE,
                JDK_GC_EVENT, TestappRecording.HTTP_SERVER_EXCHANGE, TestappRecording.TRACE_SPAN,
                TestappRecording.JDBC_QUERY)), types::toString);
    }

    /** Compressed or not, the file is the same recording. */
    @ParameterizedTest
    @EnumSource(TestappRecording.class)
    void readsTheArchiveAsTheFileItCompresses(TestappRecording recording) throws IOException {
        Path plain = plain(recording);
        Path archive = archive(recording);

        assertEquals(JfrParser.recordingInfo(plain), JfrParser.recordingInfo(archive));
        assertEquals(JfrParser.eventTypes(plain), JfrParser.eventTypes(archive));
    }

    /**
     * A file channel reads only a plain recording; handed an archive it would find no chunk header
     * and read nothing, so it is refused by name instead.
     */
    @ParameterizedTest
    @EnumSource(TestappRecording.class)
    void refusesToReadAnArchiveThroughAFileChannel(TestappRecording recording) throws IOException {
        Path archive = archive(recording);

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> JfrParser.iterateChunks(archive, (_, _) -> { }));

        assertTrue(refused.getMessage().contains("LZ4-compressed"), refused.getMessage());
    }

    /** Cut apart, each chunk is a whole recording of one chunk, beginning where the file said it did. */
    @ParameterizedTest
    @EnumSource(TestappRecording.class)
    void disassemblesIntoOneSelfContainedFilePerChunk(TestappRecording recording) throws IOException {
        List<Path> pieces = JfrParser.disassemble(archive(recording), dir.resolve("pieces"));

        assertEquals(recording.chunkStarts().size(), pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            List<JfrChunk> chunks = JfrParser.collectChunks(pieces.get(i));
            assertEquals(1, chunks.size(), pieces.get(i)::toString);
            assertEquals(recording.chunkStarts().get(i), chunks.getFirst().startTime(), pieces.get(i)::toString);
        }
    }

    /**
     * The traced checkout runs: one chunk each, written by the JDK's own recorder with the tracing
     * library's event types beside its own.
     */
    @Nested
    class TracedRun {

        @ParameterizedTest
        @EnumSource(CheckoutRecording.class)
        void isOneChunkOfTheJdksRecorderHoldingTheTracingEvents(CheckoutRecording recording) throws IOException {
            Path file = Files.write(dir.resolve(recording.fileName()), recording.bytes());

            List<JfrChunk> chunks = JfrParser.collectChunks(file);
            RecordingInformation information = JfrParser.recordingInfo(file);

            assertEquals(1, chunks.size());
            assertEquals(Files.size(file), chunks.getFirst().sizeInBytes());
            assertEquals(recording.recordingStartedAt(), information.recordingStartedAt());
            assertEquals(recording.recordingFinishedAt(), information.recordingFinishedAt());
            assertEquals(RecordingEventSource.JFR, information.eventSource());
            assertTrue(JfrParser.eventTypes(file).containsAll(Set.of(
                    CheckoutRecording.HTTP_SERVER_EXCHANGE, CheckoutRecording.TRACE_SPAN,
                    CheckoutRecording.EXECUTION_SAMPLE, CheckoutRecording.EXCEPTION_THROW)));
        }
    }
}
