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
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.microscope.persistence.api.RecordingRepository;
import cafe.jeffrey.storage.recording.api.file.ManagedFile;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import cafe.jeffrey.storage.recording.api.file.RecordingStorageLayout;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("RecordingFileLookup")
class RecordingFileLookupTest {

    private static final String RECORDING_ID = "rec-1";
    private static final Instant UPLOADED_AT = Instant.parse("2026-09-01T10:00:00Z");

    private final RecordingRepository recordingRepository = mock(RecordingRepository.class);

    @TempDir
    Path recordingsDir;

    @Nested
    @DisplayName("when there is nothing to find")
    class Missing {

        @Test
        @DisplayName("answers empty for a recording the repository does not know")
        void unknownRecordingIsEmpty() {
            when(recordingRepository.findRecording("no-such-recording")).thenReturn(Optional.empty());

            assertTrue(lookup().findJfrFiles("no-such-recording").isEmpty());
        }

        @Test
        @DisplayName("answers empty for a null or blank recording id without asking the repository")
        void noRecordingIdIsEmpty() {
            assertTrue(lookup().findJfrFiles(null).isEmpty());
            assertTrue(lookup().findJfrFiles("   ").isEmpty());
            verifyNoInteractions(recordingRepository);
        }

        @Test
        @DisplayName("creates nothing on disk")
        void createsNothing() throws IOException {
            recording(file("app.jfr", ManagedFile.JFR));

            lookup().findJfrFiles(RECORDING_ID);

            try (var entries = Files.list(recordingsDir)) {
                assertTrue(entries.findAny().isEmpty(), "the lookup left something behind");
            }
        }

        @Test
        @DisplayName("requires a repository and a directory")
        void requiresCollaborators() {
            assertThrows(IllegalArgumentException.class, () -> new RecordingFileLookup(null, recordingsDir));
            assertThrows(IllegalArgumentException.class,
                    () -> new RecordingFileLookup(recordingRepository, null));
        }
    }

    @Nested
    @DisplayName("when the recording has JFR files")
    class Found {

        @Test
        @DisplayName("resolves each file to where storage put it")
        void resolvesTheStoragePath() {
            RecordingFile jfr = file("app.jfr", ManagedFile.JFR);
            recording(jfr);
            Path stored = store(jfr);

            assertEquals(List.of(stored), lookup().findJfrFiles(RECORDING_ID));
        }

        /**
         * A downloaded session is one file per rolled chunk. Taking only one of them would analyse
         * a few minutes of a run that lasted hours and overwrite the import's findings with that.
         */
        @Test
        @DisplayName("returns every chunk, in the order the repository lists them")
        void returnsEveryChunk() {
            RecordingFile first = file("chunk-1.jfr.lz4", ManagedFile.JFR_LZ4);
            RecordingFile second = file("chunk-2.jfr.lz4", ManagedFile.JFR_LZ4);
            RecordingFile third = file("chunk-3.jfr", ManagedFile.JFR);
            recording(first, second, third);

            assertEquals(
                    List.of(store(first), store(second), store(third)),
                    lookup().findJfrFiles(RECORDING_ID));
        }

        @Test
        @DisplayName("leaves out the artifacts stored beside the recording")
        void leavesOutArtifacts() {
            RecordingFile heapDump = file("heap.hprof", ManagedFile.HEAP_DUMP);
            RecordingFile gcLog = file("gc.jvm-log", ManagedFile.JVM_LOG);
            RecordingFile jfr = file("app.jfr", ManagedFile.JFR);
            recording(heapDump, gcLog, jfr);
            store(heapDump);
            store(gcLog);

            assertEquals(List.of(store(jfr)), lookup().findJfrFiles(RECORDING_ID));
        }

        /**
         * The rules reason about the run as a whole. Running them over the chunks that are left
         * would describe a recording that was never taken.
         */
        @Test
        @DisplayName("answers empty when one of the chunks is gone from disk")
        void missingChunkIsEmpty() {
            RecordingFile first = file("chunk-1.jfr.lz4", ManagedFile.JFR_LZ4);
            RecordingFile second = file("chunk-2.jfr.lz4", ManagedFile.JFR_LZ4);
            recording(first, second);
            store(first);

            assertTrue(lookup().findJfrFiles(RECORDING_ID).isEmpty());
        }
    }

    /**
     * The rule set reads JFR and nothing else. Handing it any of these would report auto-analysis
     * as available for a profile it can only fail on.
     */
    @Nested
    @DisplayName("when the recording has no JFR file")
    class NoJfr {

        @Test
        @DisplayName("answers empty for a heap dump")
        void heapDumpIsEmpty() {
            RecordingFile heapDump = file("heap.hprof", ManagedFile.HEAP_DUMP);
            recording(heapDump);
            store(heapDump);

            assertTrue(lookup().findJfrFiles(RECORDING_ID).isEmpty());
        }

        @Test
        @DisplayName("answers empty for a pprof or OTLP import")
        void flamegraphOnlyImportIsEmpty() {
            RecordingFile pprof = file("cpu.pprof", ManagedFile.PPROF);
            RecordingFile otlp = file("profiles.otlp", ManagedFile.OTLP_PROFILE);
            recording(pprof, otlp);
            store(pprof);
            store(otlp);

            assertTrue(lookup().findJfrFiles(RECORDING_ID).isEmpty());
        }
    }

    private RecordingFileLookup lookup() {
        return new RecordingFileLookup(recordingRepository, recordingsDir);
    }

    private static RecordingFile file(String filename, ManagedFile type) {
        return new RecordingFile("file-" + filename, RECORDING_ID, filename, type, UPLOADED_AT, 1);
    }

    private void recording(RecordingFile... files) {
        Recording recording = new Recording(
                RECORDING_ID, "app", null, RecordingEventSource.JDK, UPLOADED_AT, UPLOADED_AT, UPLOADED_AT,
                false, null, null, Arrays.asList(files));
        when(recordingRepository.findRecording(RECORDING_ID)).thenReturn(Optional.of(recording));
    }

    private Path store(RecordingFile file) {
        Path path = RecordingStorageLayout.storagePath(recordingsDir, file.recordingId(), file.filename());
        try {
            if (!Files.exists(path)) {
                Files.createFile(path);
            }
            return path;
        } catch (IOException e) {
            throw new IllegalStateException("Could not stage a recording file: " + path, e);
        }
    }
}
