/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package cafe.jeffrey.profile.parser;

import cafe.jeffrey.jfrparser.raw.JfrParser;
import cafe.jeffrey.provider.profile.api.Event;
import cafe.jeffrey.provider.profile.api.EventWriter;
import cafe.jeffrey.provider.profile.api.RecordingSources;
import cafe.jeffrey.provider.profile.api.SingleThreadedEventWriter;
import cafe.jeffrey.shared.common.compression.Lz4Compressor;
import cafe.jeffrey.shared.common.filesystem.TempDirFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("JfrRecordingEventParser")
class JfrRecordingEventParserTest {

    private static final String RECORDING_RESOURCE = "/jfr/method-trace.jfr";

    /** Low enough that a handful of files is already "enough files". */
    private static final int WHOLE_FILE_THRESHOLD = 1;

    /** High enough that nothing reaches it, so every source is split. */
    private static final int ALWAYS_SPLIT_THRESHOLD = Integer.MAX_VALUE;

    @TempDir
    Path tempDir;

    private Path whole;
    private List<Path> pieces;
    private Map<String, Long> singleChunkEvents;

    /**
     * The fixture is a single-chunk recording, so it is written out twice to get a recording worth
     * splitting. That is not a trick: a JFR file is a run of self-contained chunks, which is the
     * very reason a session's files can be read independently — and it makes the expected answer
     * exact, since two copies must hold twice what one does.
     */
    @BeforeEach
    void splitTheFixture() throws IOException {
        byte[] fixture = fixtureBytes();
        singleChunkEvents = parse(RecordingSources.of(writeRecording("single.jfr", fixture)), ALWAYS_SPLIT_THRESHOLD);

        whole = writeRecording("whole.jfr", fixture, fixture);
        pieces = JfrParser.disassemble(whole, tempDir.resolve("pieces"));
    }

    /**
     * The claim the whole change rests on: a recording held as several files is the same recording.
     * Nothing joins them, so were this false the merge would have been doing real work rather than
     * undoing itself one step later.
     */
    @Nested
    @DisplayName("One recording, however many files")
    class Equivalence {

        @Test
        @DisplayName("the files separately hold what the single file holds")
        void filesAreEquivalentToTheirConcatenation() {
            assertEquals(2, pieces.size(), "the doubled fixture must split back into its two chunks");

            Map<String, Long> fromWhole = parse(RecordingSources.of(whole), ALWAYS_SPLIT_THRESHOLD);
            Map<String, Long> fromPieces = parse(new RecordingSources(pieces), ALWAYS_SPLIT_THRESHOLD);

            assertEquals(fromWhole, fromPieces);

            // And both are exactly the one chunk twice over, so neither dropped nor doubled a file.
            assertEquals(total(singleChunkEvents) * 2, total(fromPieces));
        }

        /**
         * The same recording read both ways round, so the two runs differ in the mode they took
         * rather than only in how the bytes were laid out on disk.
         */
        @Test
        @DisplayName("splitting the files changes nothing about what is parsed out of them")
        void holdsAcrossParseModes() {
            Map<String, Long> split = parse(new RecordingSources(pieces), ALWAYS_SPLIT_THRESHOLD);
            Map<String, Long> wholeFiles = parse(new RecordingSources(pieces), WHOLE_FILE_THRESHOLD);

            assertEquals(split, wholeFiles);
        }

        @Test
        @DisplayName("a single file is unchanged by the file-count rule")
        void aSingleFileReadsTheSameEitherWay() {
            Map<String, Long> split = parse(RecordingSources.of(whole), ALWAYS_SPLIT_THRESHOLD);
            Map<String, Long> asItLies = parse(RecordingSources.of(whole), WHOLE_FILE_THRESHOLD);

            assertEquals(split, asItLies);
        }
    }

    @Nested
    @DisplayName("Several sources at once")
    class SeveralSources {

        /**
         * Chunk files are named by their position within one recording, so sources sharing a
         * scratch directory would overwrite each other's {@code chunk_0}. Nothing downstream could
         * tell: the profile would simply be missing whatever those chunks held. Splitting every
         * source is the case that would lose them, so it is the case asserted here.
         */
        @Test
        @DisplayName("keeps every source's chunks, rather than letting them overwrite each other")
        void keepsSourcesApart() {
            assertEquals(2, pieces.size(), "the doubled fixture must split back into its two chunks");

            Map<String, Long> all = parse(new RecordingSources(pieces), ALWAYS_SPLIT_THRESHOLD);
            Map<String, Long> justTheFirst = parse(
                    RecordingSources.of(pieces.getFirst()), ALWAYS_SPLIT_THRESHOLD);

            // Both sources landed. Sharing a scratch directory would have left one of them,
            // and the total would have matched a single source exactly.
            assertEquals(total(justTheFirst) * 2, total(all));
            assertTrue(total(all) > total(justTheFirst), "every source contributed, not just the last one");
        }
    }

    private Map<String, Long> parse(RecordingSources sources, int chunkSplitThreshold) {
        TempDirFactory tempDirFactory = TempDirFactory.of(tempDir.resolve("scratch"));

        RecordingEvents events = new RecordingEvents();
        new JfrRecordingEventParser(tempDirFactory, new Lz4Compressor(tempDirFactory), chunkSplitThreshold)
                .start(events, sources);
        return events.countsByType();
    }

    private static long total(Map<String, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }

    private static byte[] fixtureBytes() throws IOException {
        try (InputStream source = JfrRecordingEventParserTest.class.getResourceAsStream(RECORDING_RESOURCE)) {
            if (source == null) {
                throw new IllegalStateException("Missing test recording: " + RECORDING_RESOURCE);
            }
            return source.readAllBytes();
        }
    }

    private Path writeRecording(String name, byte[]... chunks) throws IOException {
        Path target = tempDir.resolve(name);
        try (OutputStream out = Files.newOutputStream(target)) {
            for (byte[] chunk : chunks) {
                out.write(chunk);
            }
        }
        return target;
    }

    /**
     * Counts what reached the writer, by event type. Ids are handed out per writer and a parse uses
     * one writer per unit, so they differ between runs by construction — the counts are what two
     * readings of the same recording have to agree on.
     */
    private static final class RecordingEvents implements EventWriter {

        private final Map<String, AtomicLong> counts = new ConcurrentHashMap<>();

        @Override
        public SingleThreadedEventWriter newSingleThreadedWriter() {
            return new SingleThreadedEventWriter() {
                @Override
                public void onEvent(Event event) {
                    counts.computeIfAbsent(event.eventType(), _ -> new AtomicLong()).incrementAndGet();
                }
            };
        }

        @Override
        public void onComplete() {
        }

        private Map<String, Long> countsByType() {
            return counts.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().get()));
        }
    }
}
