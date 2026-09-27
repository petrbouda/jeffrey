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

package cafe.jeffrey.recordings.core.manager;

import cafe.jeffrey.microscope.persistence.api.RecordingRepository;
import cafe.jeffrey.microscope.persistence.api.RecordingTagsRepository;
import cafe.jeffrey.microscope.model.RecordingEventSource;
import cafe.jeffrey.storage.recording.api.file.Recording;
import cafe.jeffrey.storage.recording.api.file.RecordingFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordingsCoreManagerImplTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T12:00:00Z"), ZoneOffset.UTC);
    private static final String CONTENT = "not really a recording, but a real file";

    @Mock
    RecordingRepository recordingRepository;

    @Mock
    RecordingTagsRepository recordingTagsRepository;

    @TempDir
    Path tempDir;

    private Path sourceDir;
    private RecordingsCoreManagerImpl manager;

    @BeforeEach
    void setUp() throws IOException {
        Path recordingsDir = Files.createDirectories(tempDir.resolve("recordings"));
        sourceDir = Files.createDirectories(tempDir.resolve("project"));
        manager = new RecordingsCoreManagerImpl(
                CLOCK, recordingsDir, recordingRepository, recordingTagsRepository, null, null);
    }

    private Path sourceFile(String filename) throws IOException {
        Path file = sourceDir.resolve(filename);
        Files.writeString(file, CONTENT);
        return file;
    }

    /**
     * An imported file is recognised again by the name and size its one stored file carries, so
     * those two have to be the source file's own.
     */
    @Nested
    class ImportRecordingFromPath {

        @Test
        void storesTheFileUnderItsOwnNameAndSize() throws IOException {
            Path file = sourceFile("app.jfr");

            String recordingId = manager.importRecordingFromPath(file);

            ArgumentCaptor<RecordingFile> stored = ArgumentCaptor.forClass(RecordingFile.class);
            verify(recordingRepository).insertRecording(any(Recording.class), stored.capture());
            assertEquals(recordingId, stored.getValue().recordingId());
            assertEquals("app.jfr", stored.getValue().filename());
            assertEquals(CONTENT.length(), stored.getValue().sizeInBytes());
        }
    }

    @Nested
    class FindByFileNameAndSize {

        @Test
        void asksTheStoreForTheRecordingOfThatNameAndSize() {
            Recording existing = new Recording("rec-1", "app.jfr", null, RecordingEventSource.JDK,
                    CLOCK.instant(), null, null, false, null, null, List.of());
            when(recordingRepository.findByFileNameAndSize("app.jfr", CONTENT.length()))
                    .thenReturn(Optional.of(existing));

            Optional<Recording> result = manager.findByFileNameAndSize("app.jfr", CONTENT.length());

            assertSame(existing, result.orElseThrow());
        }
    }
}
