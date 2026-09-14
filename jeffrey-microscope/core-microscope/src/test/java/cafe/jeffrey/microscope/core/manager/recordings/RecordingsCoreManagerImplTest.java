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

package cafe.jeffrey.microscope.core.manager.recordings;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.microscope.persistence.api.RecordingRepository;
import cafe.jeffrey.microscope.persistence.api.RecordingTagsRepository;
import cafe.jeffrey.recordings.core.manager.RecordingMetadataParser;
import cafe.jeffrey.recordings.core.manager.RecordingMetadataParser.RecordingMetadata;
import cafe.jeffrey.recordings.core.manager.RecordingProfileCleanup;
import cafe.jeffrey.recordings.core.manager.RecordingsCoreManagerImpl;
import cafe.jeffrey.shared.common.model.Recording;
import cafe.jeffrey.shared.common.model.RecordingEventSource;
import cafe.jeffrey.shared.common.model.RecordingFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingsCoreManagerImplTest {

    private static final Instant NOW = Instant.parse("2026-05-23T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Mock
    private RecordingRepository recordingRepository;
    @Mock
    private RecordingTagsRepository recordingTagsRepository;
    @Mock
    private RecordingMetadataParser recordingMetadataParser;

    @TempDir
    private Path recordingsDir;
    @TempDir
    private Path sourceDir;

    private RecordingsCoreManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new RecordingsCoreManagerImpl(
                FIXED_CLOCK, recordingsDir,
                recordingRepository, recordingTagsRepository,
                recordingMetadataParser, RecordingProfileCleanup.NOOP);
    }

    @Nested
    class ImportFromPath {

        @Test
        void rejectsNullPath() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> manager.importRecordingFromPath(null));
            assertEquals("Recording path is required", ex.getMessage());
        }

        @Test
        void rejectsMissingFile() {
            Path missing = sourceDir.resolve("does-not-exist.jfr");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> manager.importRecordingFromPath(missing));
            assertEquals("Recording file not found: " + missing, ex.getMessage());
        }

        @Test
        void rejectsUnsupportedFileType() throws Exception {
            Path unsupported = Files.writeString(sourceDir.resolve("notes.txt"), "hello");
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> manager.importRecordingFromPath(unsupported));
            assertEquals("Unsupported recording file type: notes.txt", ex.getMessage());
        }

        @Test
        void ingestsJfrAsUngroupedRecording() throws Exception {
            Path jfr = Files.writeString(sourceDir.resolve("recording.jfr"), "jfr-bytes");
            RecordingMetadata info = new RecordingMetadata(RecordingEventSource.JDK, NOW, NOW.plusSeconds(60));
            when(recordingMetadataParser.parse(any(Path.class))).thenReturn(Optional.of(info));

            String recordingId = manager.importRecordingFromPath(jfr);

            assertNotNull(recordingId);

            ArgumentCaptor<Recording> recordingCaptor = ArgumentCaptor.forClass(Recording.class);
            verify(recordingRepository).insertRecording(recordingCaptor.capture(), any(RecordingFile.class));

            Recording persisted = recordingCaptor.getValue();
            assertEquals(recordingId, persisted.id());
            assertEquals("recording.jfr", persisted.recordingName());
            // Ungrouped quick-analysis recording: no project, no group.
            assertEquals(null, persisted.projectId());
            assertEquals(null, persisted.groupId());
        }
    }

    /**
     * A session downloaded from a hub is stored as it came: every chunk a file of the recording,
     * nothing joined, and the recording's window read off the chunks together.
     */
    @Nested
    class CreateDownloadedRecording {

        @Test
        void storesEveryChunkAsARecordingFileAndSpansTheirWindows() throws Exception {
            Path first = Files.writeString(sourceDir.resolve("profile-20260523-100000.jfr.lz4"), "one");
            Path second = Files.writeString(sourceDir.resolve("profile-20260523-101500.jfr"), "two");
            Path log = Files.writeString(sourceDir.resolve("gc.jvm-log"), "gc");
            when(recordingMetadataParser.parse(any(Path.class))).thenAnswer(invocation -> {
                Path stored = invocation.getArgument(0);
                if (stored.getFileName().toString().endsWith("profile-20260523-100000.jfr.lz4")) {
                    return Optional.of(new RecordingMetadata(RecordingEventSource.ASYNC_PROFILER, NOW, NOW.plusSeconds(900)));
                }
                return Optional.of(new RecordingMetadata(RecordingEventSource.ASYNC_PROFILER, NOW.plusSeconds(900), NOW.plusSeconds(1500)));
            });

            String recordingId = manager.createDownloadedRecording(
                    "checkout_2026-05-23", List.of(first, second), List.of(log), Map.of("origin.hubId", "hub-1"));

            ArgumentCaptor<Recording> recordingCaptor = ArgumentCaptor.forClass(Recording.class);
            ArgumentCaptor<RecordingFile> firstFile = ArgumentCaptor.forClass(RecordingFile.class);
            verify(recordingRepository).insertRecording(recordingCaptor.capture(), firstFile.capture());
            ArgumentCaptor<RecordingFile> otherFiles = ArgumentCaptor.forClass(RecordingFile.class);
            verify(recordingRepository, times(2)).insertRecordingFile(otherFiles.capture());

            Recording persisted = recordingCaptor.getValue();
            assertEquals(recordingId, persisted.id());
            assertEquals("checkout_2026-05-23", persisted.recordingName());
            assertEquals(RecordingEventSource.ASYNC_PROFILER, persisted.eventSource());
            assertEquals(NOW, persisted.recordingStartedAt());
            assertEquals(NOW.plusSeconds(1500), persisted.recordingFinishedAt());

            assertEquals("profile-20260523-100000.jfr.lz4", firstFile.getValue().filename());
            assertEquals(
                    List.of("profile-20260523-101500.jfr", "gc.jvm-log"),
                    otherFiles.getAllValues().stream().map(RecordingFile::filename).toList());
            assertEquals("one", Files.readString(recordingsDir.resolve(recordingId + "-profile-20260523-100000.jfr.lz4")));
            assertEquals("two", Files.readString(recordingsDir.resolve(recordingId + "-profile-20260523-101500.jfr")));
            assertEquals("gc", Files.readString(recordingsDir.resolve(recordingId + "-gc.jvm-log")));
            assertFalse(Files.exists(first), "the chunk is moved, not copied");
            verify(recordingTagsRepository).insert(recordingId, Map.of("origin.hubId", "hub-1"));
        }

        @Test
        void refusesASessionWithoutAChunk() {
            assertThrows(IllegalArgumentException.class,
                    () -> manager.createDownloadedRecording("empty", List.of(), List.of(), Map.of()));
        }
    }

    @Nested
    class FindRecording {

        // Regression: by-id reads must be project-agnostic. A recording downloaded into a project has a
        // non-null project_id, so resolving it via the (project-scoped) listRecordings() would miss it and
        // fail the global AI-export / download endpoints with "Recording not found". findRecording must
        // delegate to the repository's project-agnostic by-id lookup instead.
        @Test
        void delegatesToProjectAgnosticByIdLookup() {
            Recording projectScoped = new Recording(
                    "rec-1", "recording.jfr", "project-42", null, RecordingEventSource.JDK,
                    NOW, NOW, NOW.plusSeconds(60), false, null, null, java.util.List.of());
            when(recordingRepository.findRecording("rec-1")).thenReturn(Optional.of(projectScoped));

            Optional<Recording> found = manager.findRecording("rec-1");

            assertEquals(Optional.of(projectScoped), found);
            verify(recordingRepository).findRecording("rec-1");
        }

        @Test
        void returnsEmptyWhenMissing() {
            when(recordingRepository.findRecording("missing")).thenReturn(Optional.empty());

            assertEquals(Optional.empty(), manager.findRecording("missing"));
        }
    }
}
