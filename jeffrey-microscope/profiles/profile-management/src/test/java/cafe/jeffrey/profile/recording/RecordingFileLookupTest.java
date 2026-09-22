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

package cafe.jeffrey.profile.recording;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RecordingFileLookup")
class RecordingFileLookupTest {

    private static final String RECORDING_ID = "rec-1";

    @TempDir
    Path recordingsDir;

    @Nested
    @DisplayName("when there is nothing to find")
    class Missing {

        @Test
        @DisplayName("answers empty for a recording that was never stored")
        void unknownRecordingIsEmpty() {
            assertTrue(lookup().find("no-such-recording").isEmpty());
        }

        /**
         * The lookup this replaced created the directory it was about to search, so asking about a
         * recording that did not exist left an empty directory behind. Reads do not write.
         */
        @Test
        @DisplayName("creates nothing when the recording is unknown")
        void unknownRecordingCreatesNothing() throws IOException {
            lookup().find("no-such-recording");

            try (var entries = Files.list(recordingsDir)) {
                assertTrue(entries.findAny().isEmpty(), "the lookup left something behind");
            }
        }

        @Test
        @DisplayName("answers empty when the recordings directory does not exist")
        void missingDirectoryIsEmpty() {
            Path absent = recordingsDir.resolve("absent");

            assertTrue(new RecordingFileLookup(absent).find(RECORDING_ID).isEmpty());
            assertFalse(Files.exists(absent), "the lookup created the directory it was given");
        }

        @Test
        @DisplayName("answers empty for a null or blank recording id")
        void noRecordingIdIsEmpty() {
            assertTrue(lookup().find(null).isEmpty());
            assertTrue(lookup().find("   ").isEmpty());
        }
    }

    @Nested
    @DisplayName("when matching the storage prefix")
    class Matching {

        @Test
        @DisplayName("finds a JFR recording stored under its id")
        void findsJfr() {
            Path recording = store(RECORDING_ID + "-app.jfr");

            assertEquals(Optional.of(recording), lookup().find(RECORDING_ID));
        }

        @Test
        @DisplayName("finds a compressed JFR recording")
        void findsCompressedJfr() {
            Path recording = store(RECORDING_ID + "-app.jfr.lz4");

            assertEquals(Optional.of(recording), lookup().find(RECORDING_ID));
        }

        @Test
        @DisplayName("finds a pprof recording")
        void findsPprof() {
            Path recording = store(RECORDING_ID + "-cpu.pprof");

            assertEquals(Optional.of(recording), lookup().find(RECORDING_ID));
        }

        /**
         * The prefix ends at the separator. Without it {@code rec-1} would claim {@code rec-10}'s
         * files and hand auto-analysis a different recording entirely.
         */
        @Test
        @DisplayName("does not claim a longer id that starts with the same characters")
        void doesNotMatchALongerId() {
            store("rec-10-app.jfr");

            assertTrue(lookup().find(RECORDING_ID).isEmpty());
        }

        @Test
        @DisplayName("does not match a file that merely contains the id")
        void doesNotMatchAnInfix() {
            store("other-" + RECORDING_ID + "-app.jfr");

            assertTrue(lookup().find(RECORDING_ID).isEmpty());
        }

        @Test
        @DisplayName("ignores a directory named like a stored file")
        void ignoresDirectories() throws IOException {
            Files.createDirectory(recordingsDir.resolve(RECORDING_ID + "-app.jfr"));

            assertTrue(lookup().find(RECORDING_ID).isEmpty());
        }
    }

    @Nested
    @DisplayName("when a recording has several files")
    class RecordingWins {

        @Test
        @DisplayName("prefers the recording over an artifact beside it")
        void prefersTheRecording() {
            store(RECORDING_ID + "-heap.hprof");
            Path recording = store(RECORDING_ID + "-app.jfr");

            assertEquals(Optional.of(recording), lookup().find(RECORDING_ID));
        }

        /**
         * The artifact here sorts first, so a lookup that merely took the first match by name would
         * hand auto-analysis a heap dump to run JMC rules over.
         */
        @Test
        @DisplayName("prefers the recording even when an artifact sorts ahead of it")
        void prefersTheRecordingOverAnEarlierName() {
            store(RECORDING_ID + "-aaa.hprof");
            Path recording = store(RECORDING_ID + "-zzz.jfr");

            assertEquals(Optional.of(recording), lookup().find(RECORDING_ID));
        }

        /**
         * A heap dump has no recording beside it. Answering with nothing would take auto-analysis
         * away from heap-dump profiles rather than give them a better answer.
         */
        @Test
        @DisplayName("falls back to the only file when none of them is a recording")
        void fallsBackToTheArtifact() {
            Path heapDump = store(RECORDING_ID + "-heap.hprof");

            assertEquals(Optional.of(heapDump), lookup().find(RECORDING_ID));
        }

        @Test
        @DisplayName("picks the same recording every time when two of them qualify")
        void picksDeterministically() {
            store(RECORDING_ID + "-second.jfr");
            Path first = store(RECORDING_ID + "-first.jfr");

            assertEquals(Optional.of(first), lookup().find(RECORDING_ID));
        }
    }

    private RecordingFileLookup lookup() {
        return new RecordingFileLookup(recordingsDir);
    }

    private Path store(String filename) {
        try {
            return Files.createFile(recordingsDir.resolve(filename));
        } catch (IOException e) {
            throw new IllegalStateException("Could not stage a recording file: " + filename, e);
        }
    }
}
