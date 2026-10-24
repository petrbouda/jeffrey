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
import cafe.jeffrey.storage.recording.api.file.Bundle;
import cafe.jeffrey.storage.recording.api.file.BundleFile;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;

import java.io.ByteArrayInputStream;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
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
            assertTrue(ex.getMessage().startsWith("Unsupported recording file type: notes.txt"), ex.getMessage());
        }

        @Test
        void ingestsJfrAsUngroupedRecording() throws Exception {
            Path jfr = Files.writeString(sourceDir.resolve("recording.jfr"), "jfr-bytes");
            RecordingMetadata info = new RecordingMetadata(RecordingEventSource.JDK, NOW, NOW.plusSeconds(60));
            when(recordingMetadataParser.parse(anyList())).thenReturn(Optional.of(info));

            String recordingId = manager.importRecordingFromPath(jfr);

            assertNotNull(recordingId);

            ArgumentCaptor<Recording> recordingCaptor = ArgumentCaptor.forClass(Recording.class);
            verify(recordingRepository).insertRecording(recordingCaptor.capture(), any(RecordingFile.class));

            Recording persisted = recordingCaptor.getValue();
            assertEquals(recordingId, persisted.id());
            assertEquals("recording.jfr", persisted.recordingName());
            // Ungrouped recording: no group.
            assertEquals(null, persisted.groupId());
        }
    }

    /**
     * The file row carries the file's own name, and storage puts the recording id in front of it.
     * A row that stored the name it sits under instead would be resolved back to a path with that
     * prefix on it twice — nothing would find the file again: analysis would report it missing,
     * deletion would leave it behind, and the IDE panel, which matches a file by name and size,
     * would read every imported recording as never imported.
     */
    @Nested
    class StoredFileNames {

        @Test
        void storesTheFilesOwnNameRatherThanTheNameItSitsUnder() throws Exception {
            Path jfr = Files.writeString(sourceDir.resolve("recording.jfr"), "jfr-bytes");
            when(recordingMetadataParser.parse(anyList())).thenReturn(Optional.empty());

            String recordingId = manager.importRecordingFromPath(jfr);

            ArgumentCaptor<RecordingFile> fileCaptor = ArgumentCaptor.forClass(RecordingFile.class);
            verify(recordingRepository).insertRecording(any(Recording.class), fileCaptor.capture());

            RecordingFile persisted = fileCaptor.getValue();
            assertEquals("recording.jfr", persisted.filename());
            assertEquals(recordingId, persisted.recordingId());

            // And the row resolves to the file that is actually there.
            Path onDisk = recordingsDir.resolve(persisted.recordingId() + "-" + persisted.filename());
            assertTrue(Files.exists(onDisk), "the row must resolve to the stored file: " + onDisk);
        }

        @Test
        void storesADownloadAsABundleOfOneRecordingPerItemAndItsLogsAsFiles() throws Exception {
            Path first = Files.writeString(sourceDir.resolve("profile-1.jfr"), "chunk-one");
            Path second = Files.writeString(sourceDir.resolve("profile-2.jfr"), "chunk-two");
            Path heapDump = Files.writeString(sourceDir.resolve("heap.hprof"), "dump");
            Path gcLog = Files.writeString(sourceDir.resolve("gc.jvm-log"), "log");
            when(recordingMetadataParser.parse(anyList())).thenReturn(Optional.empty());

            String recordingId = manager.createDownloadedBundle(
                    "checkout_2026-03-01T12-00-00Z", List.of(first, second), List.of(heapDump, gcLog),
                    Map.of("origin.hubId", "hub-1"));

            ArgumentCaptor<Bundle> bundle = ArgumentCaptor.forClass(Bundle.class);
            ArgumentCaptor<List<Recording>> members = ArgumentCaptor.captor();
            verify(recordingRepository).insertBundle(bundle.capture(), members.capture());

            Recording jfr = members.getValue().getFirst();
            assertEquals(recordingId, jfr.id(), "the download answers its recording item");
            assertEquals("checkout_2026-03-01T12-00-00Z", jfr.recordingName());
            assertEquals(List.of("profile-1.jfr", "profile-2.jfr"),
                    jfr.files().stream().map(RecordingFile::filename).toList());

            Recording heap = members.getValue().get(1);
            assertEquals(RecordingEventSource.HEAP_DUMP, heap.eventSource());
            assertEquals("heap.hprof", heap.recordingName());

            assertTrue(members.getValue().stream().allMatch(member -> bundle.getValue().id().equals(member.bundleId())));
            assertEquals(List.of("gc.jvm-log"), bundle.getValue().files().stream().map(BundleFile::filename).toList());

            for (Recording member : members.getValue()) {
                for (RecordingFile file : member.files()) {
                    assertTrue(Files.exists(recordingsDir.resolve(member.id() + "-" + file.filename())),
                            "every member row must resolve to a stored file: " + file.filename());
                }
                verify(recordingTagsRepository).insert(member.id(), Map.of("origin.hubId", "hub-1"));
            }
            assertTrue(Files.exists(recordingsDir.resolve(bundle.getValue().id() + "-gc.jvm-log")));
        }
    }

    @Nested
    class OnlyInitializableFiles {

        @Test
        void refusesALogUploadedOnItsOwn() {
            assertThrows(IllegalArgumentException.class, () -> manager.uploadRecording(
                    "app.log", new ByteArrayInputStream(new byte[]{1}), null));
        }

        @Test
        void refusesALogImportedFromAPath() throws Exception {
            Path log = Files.writeString(sourceDir.resolve("hs-jvm-err.log"), "crash");

            assertThrows(IllegalArgumentException.class, () -> manager.importRecordingFromPath(log));
        }
    }

    @Nested
    class DeletingABundleMember {

        private static final String BUNDLE_ID = "bundle-1";

        private Recording member(String id) {
            return new Recording(id, id, null, RecordingEventSource.HEAP_DUMP, NOW, NOW, NOW,
                    false, null, null, List.of(), BUNDLE_ID);
        }

        @Test
        void removesTheBundleWithItsLastItem() {
            when(recordingRepository.findRecording("heap-1")).thenReturn(Optional.of(member("heap-1")));
            when(recordingRepository.findBundle(BUNDLE_ID))
                    .thenReturn(Optional.of(new Bundle(BUNDLE_ID, "session", null, NOW, List.of())));
            when(recordingRepository.findBundleMembers(BUNDLE_ID)).thenReturn(List.of());

            manager.deleteRecording("heap-1");

            verify(recordingRepository).deleteRecordingWithFiles("heap-1");
            verify(recordingRepository).deleteBundle(BUNDLE_ID);
        }

        @Test
        void keepsTheBundleWhileOtherItemsRemain() {
            when(recordingRepository.findRecording("heap-1")).thenReturn(Optional.of(member("heap-1")));
            when(recordingRepository.findBundle(BUNDLE_ID))
                    .thenReturn(Optional.of(new Bundle(BUNDLE_ID, "session", null, NOW, List.of())));
            when(recordingRepository.findBundleMembers(BUNDLE_ID)).thenReturn(List.of(member("jfr-1")));

            manager.deleteRecording("heap-1");

            verify(recordingRepository, never()).deleteBundle(BUNDLE_ID);
        }
    }

    @Nested
    class FindRecording {

        // By-id reads go straight to the repository's by-id lookup rather than filtering
        // listRecordings(), so the AI-export and download endpoints resolve a recording in one query.
        @Test
        void delegatesToByIdLookup() {
            Recording stored = new Recording(
                    "rec-1", "recording.jfr", null, RecordingEventSource.JDK,
                    NOW, NOW, NOW.plusSeconds(60), false, null, null, List.of());
            when(recordingRepository.findRecording("rec-1")).thenReturn(Optional.of(stored));

            Optional<Recording> found = manager.findRecording("rec-1");

            assertEquals(Optional.of(stored), found);
            verify(recordingRepository).findRecording("rec-1");
        }

        @Test
        void returnsEmptyWhenMissing() {
            when(recordingRepository.findRecording("missing")).thenReturn(Optional.empty());

            assertEquals(Optional.empty(), manager.findRecording("missing"));
        }
    }
}
