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
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.project.repository.MergedRecording;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.provider.platform.NewRecordingHolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecordingsDownloadManagerImplTest {

    @Mock
    private ProjectRecordingInitializer recordingInitializer;
    @Mock
    private RepositoryStorage repositoryStorage;

    private RecordingsDownloadManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new RecordingsDownloadManagerImpl(recordingInitializer, repositoryStorage);
    }

    @Nested
    class MergeAndDownloadSession {

        @Test
        void mergesRecordings_andCreatesNewRecording(@TempDir Path tempDir) throws Exception {
            Path mergedFile = tempDir.resolve("session-1.jfr.lz4");
            Files.writeString(mergedFile, "merged-content");
            MergedRecording merged = new MergedRecording(mergedFile);

            Path artifactFile = tempDir.resolve("heap.hprof");
            Files.writeString(artifactFile, "heap-data");

            NewRecordingHolder holder = mock(NewRecordingHolder.class);
            when(holder.outputPath()).thenReturn(tempDir.resolve("output.jfr.lz4"));

            when(repositoryStorage.mergeRecordings("session-1")).thenReturn(merged);
            when(repositoryStorage.artifacts("session-1")).thenReturn(List.of(artifactFile));
            when(recordingInitializer.newRecordingWithPaths(any(), eq(List.of(artifactFile))))
                    .thenReturn(holder);

            manager.mergeAndDownloadSession("session-1");

            verify(recordingInitializer).newRecordingWithPaths(any(), eq(List.of(artifactFile)));
            verify(holder).close();
        }
    }

    @Nested
    class MergeAndDownloadRecordings {

        @Test
        void mergesSelectedRecordings_andCreatesNewRecording(@TempDir Path tempDir) throws Exception {
            Path mergedFile = tempDir.resolve("merged.jfr.lz4");
            Files.writeString(mergedFile, "merged-content");
            MergedRecording merged = new MergedRecording(mergedFile);

            NewRecordingHolder holder = mock(NewRecordingHolder.class);
            when(holder.outputPath()).thenReturn(tempDir.resolve("output.jfr.lz4"));

            when(repositoryStorage.mergeRecordings("session-1", List.of("r1", "r2"))).thenReturn(merged);
            when(repositoryStorage.artifacts("session-1")).thenReturn(List.of());
            when(recordingInitializer.newRecordingWithPaths(any(), eq(List.of())))
                    .thenReturn(holder);

            manager.mergeAndDownloadRecordings("session-1", List.of("r1", "r2"));

            verify(repositoryStorage).mergeRecordings("session-1", List.of("r1", "r2"));
            verify(holder).close();
        }
    }

    @Nested
    class CreateNewRecording {

        @Test
        void createsRecording_viaInitializerWithArtifacts(@TempDir Path tempDir) throws Exception {
            Path recordingPath = tempDir.resolve("recording.jfr");
            Files.writeString(recordingPath, "recording-data");
            Path artifactPath = tempDir.resolve("artifact.hprof");
            Files.writeString(artifactPath, "artifact-data");

            NewRecordingHolder holder = mock(NewRecordingHolder.class);
            when(holder.outputPath()).thenReturn(tempDir.resolve("output.jfr"));
            when(recordingInitializer.newRecordingWithPaths(any(), eq(List.of(artifactPath))))
                    .thenReturn(holder);

            manager.createNewRecording("my-recording", recordingPath, List.of(artifactPath));

            verify(recordingInitializer).newRecordingWithPaths(any(), eq(List.of(artifactPath)));
            verify(holder).close();
        }
    }
}
