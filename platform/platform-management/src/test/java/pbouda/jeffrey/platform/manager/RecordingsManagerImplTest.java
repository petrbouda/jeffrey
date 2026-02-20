/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.manager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.provider.platform.NewRecordingHolder;
import pbouda.jeffrey.provider.platform.model.NewRecording;
import pbouda.jeffrey.provider.platform.repository.ProjectRecordingRepository;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.Recording;
import pbouda.jeffrey.shared.common.model.RecordingEventSource;
import pbouda.jeffrey.shared.common.model.RecordingFile;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.storage.recording.api.ProjectRecordingStorage;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordingsManagerImplTest {

    private static final Instant NOW = Instant.parse("2025-06-01T12:00:00Z");
    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            "proj-1", null, "Test Project", null, null,
            "ws-1", WorkspaceType.SANDBOX, NOW, null, Map.of());

    @Mock
    private ProjectRecordingInitializer recordingInitializer;
    @Mock
    private ProjectRecordingRepository projectRecordingRepository;

    private RecordingsManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new RecordingsManagerImpl(PROJECT_INFO, recordingInitializer, projectRecordingRepository);
    }

    @Nested
    class Upload {

        @Test
        void uploadsRecording_viaInitializer() throws Exception {
            NewRecording newRecording = new NewRecording("test.jfr", "test.jfr", null);
            NewRecordingHolder holder = mock(NewRecordingHolder.class);
            when(recordingInitializer.newRecording(newRecording)).thenReturn(holder);

            InputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3});
            manager.upload(newRecording, stream);

            verify(holder).transferFrom(stream);
            verify(holder).close();
        }

        @Test
        void wrapsException_asRuntimeException_onFailure() throws Exception {
            NewRecording newRecording = new NewRecording("test.jfr", "test.jfr", null);
            NewRecordingHolder holder = mock(NewRecordingHolder.class);
            when(recordingInitializer.newRecording(newRecording)).thenReturn(holder);
            doThrow(new RuntimeException("disk full")).when(holder).transferFrom(any());

            InputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3});
            assertThrows(RuntimeException.class, () -> manager.upload(newRecording, stream));
        }
    }

    @Nested
    class FindRecordingFile {

        @Test
        void returnsPath_whenRecordingAndFileExist() {
            RecordingFile file = new RecordingFile("file-1", "rec-1", "recording.jfr",
                    SupportedRecordingFile.JFR, NOW, 1024L);
            Recording recording = new Recording("rec-1", "recording.jfr", "proj-1", null,
                    RecordingEventSource.JDK, NOW, NOW, NOW, false, List.of(file));

            Path storedFile = Path.of("/storage/proj-1/rec-1/recording.jfr");
            ProjectRecordingStorage storage = mock(ProjectRecordingStorage.class);

            when(projectRecordingRepository.findRecording("rec-1")).thenReturn(Optional.of(recording));
            when(recordingInitializer.recordingStorage()).thenReturn(storage);
            when(storage.findAllFiles("rec-1")).thenReturn(List.of(storedFile));

            Optional<Path> result = manager.findRecordingFile("rec-1", "file-1");

            assertTrue(result.isPresent());
            assertEquals(storedFile, result.get());
        }

        @Test
        void returnsEmpty_whenRecordingNotFound() {
            when(projectRecordingRepository.findRecording("rec-missing")).thenReturn(Optional.empty());

            Optional<Path> result = manager.findRecordingFile("rec-missing", "file-1");

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmpty_whenFileIdNotInRecording() {
            Recording recording = new Recording("rec-1", "recording.jfr", "proj-1", null,
                    RecordingEventSource.JDK, NOW, NOW, NOW, false, List.of());

            when(projectRecordingRepository.findRecording("rec-1")).thenReturn(Optional.of(recording));

            Optional<Path> result = manager.findRecordingFile("rec-1", "file-wrong");

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmpty_whenFileNotInStorage() {
            RecordingFile file = new RecordingFile("file-1", "rec-1", "recording.jfr",
                    SupportedRecordingFile.JFR, NOW, 1024L);
            Recording recording = new Recording("rec-1", "recording.jfr", "proj-1", null,
                    RecordingEventSource.JDK, NOW, NOW, NOW, false, List.of(file));

            ProjectRecordingStorage storage = mock(ProjectRecordingStorage.class);

            when(projectRecordingRepository.findRecording("rec-1")).thenReturn(Optional.of(recording));
            when(recordingInitializer.recordingStorage()).thenReturn(storage);
            when(storage.findAllFiles("rec-1")).thenReturn(List.of());

            Optional<Path> result = manager.findRecordingFile("rec-1", "file-1");

            assertTrue(result.isEmpty());
        }
    }
}
