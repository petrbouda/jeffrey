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

package pbouda.jeffrey.platform.project.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pbouda.jeffrey.platform.project.repository.file.AsprofFileInfoProcessor;
import pbouda.jeffrey.provider.platform.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.compression.Lz4Compressor;
import pbouda.jeffrey.shared.common.exception.ErrorCode;
import pbouda.jeffrey.shared.common.exception.JeffreyClientException;
import pbouda.jeffrey.shared.common.filesystem.FileSystemUtils;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.RepositoryType;
import pbouda.jeffrey.shared.common.model.repository.FileExtensions;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AsprofFileRepositoryStorageTest {

    private static final Path JFR_DIR = FileSystemUtils.classpathPath("jfrs");
    private static final String SESSION_ID = "test-session-123";
    private static final String PROJECT_ID = "test-project-456";
    private static final String WORKSPACE_ID = "test-workspace-789";

    // Properly formatted filenames for AsprofFileInfoProcessor (profile-YYYYMMDD-HHmmss.jfr)
    private static final String FORMATTED_FILE_1 = "profile-20250101-120000.jfr";
    private static final String FORMATTED_FILE_2 = "profile-20250101-120001.jfr";

    // IDs are filenames without extension (FileSystemUtils.removeExtension uses getFileName())
    private static final String FILE_ID_1 = "profile-20250101-120000";

    @TempDir
    Path tempDir;

    private Path sessionPath;
    private Path jeffreyTemp;
    private AsprofFileRepositoryStorage storage;
    private RecordingFileEventEmitter eventEmitter;
    private ProjectInfo projectInfo;

    @BeforeEach
    void setUp() throws IOException {
        // Set up directory structure
        // Structure: workspacesPath / relativeWorkspacePath / relativeProjectPath / relativeSessionPath
        Path jeffreyHome = tempDir.resolve("jeffrey-home");
        jeffreyTemp = tempDir.resolve("jeffrey-temp");
        Path repositoryPath = tempDir.resolve("repository");
        sessionPath = repositoryPath.resolve(SESSION_ID);

        Files.createDirectories(jeffreyHome);
        Files.createDirectories(jeffreyTemp);
        Files.createDirectories(sessionPath);

        JeffreyDirs jeffreyDirs = new JeffreyDirs(jeffreyHome, jeffreyTemp);

        // Mock ProjectRepositoryRepository
        ProjectRepositoryRepository projectRepositoryRepository = mock(ProjectRepositoryRepository.class);

        // RepositoryInfo: workspacesPath is the base, relativeWorkspacePath and relativeProjectPath are empty
        RepositoryInfo repositoryInfo = new RepositoryInfo(
                PROJECT_ID,
                RepositoryType.ASYNC_PROFILER,
                repositoryPath.toString(),
                "",  // relativeWorkspacePath
                ""); // relativeProjectPath
        when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

        // Mock session info
        Instant now = Instant.now();
        ProjectInstanceSessionInfo sessionInfo = new ProjectInstanceSessionInfo(
                SESSION_ID,
                PROJECT_ID,
                null,                 // instanceId
                1,                    // order
                Path.of(SESSION_ID),  // relativeSessionPath
                null,                 // profilerSettings
                now,                  // originCreatedAt
                now,                  // createdAt
                now);                 // finishedAt - set to mark session as FINISHED
        when(projectRepositoryRepository.findAllSessions()).thenReturn(List.of(sessionInfo));
        when(projectRepositoryRepository.findSessionById(SESSION_ID)).thenReturn(java.util.Optional.of(sessionInfo));
        when(projectRepositoryRepository.findLatestSessionId()).thenReturn(java.util.Optional.of(SESSION_ID));

        // Create ProjectInfo
        projectInfo = new ProjectInfo(
                PROJECT_ID,
                PROJECT_ID,
                "Test Project",
                "Test Label",
                null, // namespace
                WORKSPACE_ID,
                WorkspaceType.LIVE,
                now,
                now,
                Map.of());

        // Create mock event emitter
        eventEmitter = mock(RecordingFileEventEmitter.class);

        // Create storage instance
        storage = new AsprofFileRepositoryStorage(
                projectInfo,
                jeffreyDirs,
                projectRepositoryRepository,
                new AsprofFileInfoProcessor(),
                eventEmitter);
    }

    private void copyJfrToSession(String sourceFileName, String targetFileName) throws IOException {
        Path source = JFR_DIR.resolve(sourceFileName);
        Path target = sessionPath.resolve(targetFileName);
        Files.copy(source, target);
    }

    private void createFinishedFile() throws IOException {
        Files.createFile(sessionPath.resolve("perf-counters.hsperfdata"));
    }

    @Nested
    class RecordingsMethod {

        @Test
        void returnsCompressedPaths_whenFilesAlreadyCompressed() throws IOException {
            // Copy JFR and compress it
            Path jfrFile = sessionPath.resolve(FORMATTED_FILE_1);
            Files.copy(JFR_DIR.resolve("profile-1.jfr"), jfrFile);
            Path compressedFile = sessionPath.resolve(FORMATTED_FILE_1 + ".lz4");
            Lz4Compressor.compress(jfrFile, compressedFile);
            Files.delete(jfrFile); // Remove uncompressed to leave only .lz4
            createFinishedFile();

            List<Path> recordings = storage.recordings(SESSION_ID);

            assertEquals(1, recordings.size());
            assertTrue(recordings.getFirst().toString().endsWith(".jfr.lz4"));
        }

        @Test
        void compressesAndReturnsPaths_whenFilesAreUncompressed() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            createFinishedFile();

            List<Path> recordings = storage.recordings(SESSION_ID);

            assertEquals(1, recordings.size());
            assertTrue(recordings.getFirst().toString().endsWith(".jfr.lz4"));
        }

        @Test
        void createsCompressedFilePersistently_whenCompressing() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            createFinishedFile();

            storage.recordings(SESSION_ID);

            // Verify the compressed file exists in the session directory
            Path expectedCompressed = sessionPath.resolve(FORMATTED_FILE_1 + ".lz4");
            assertTrue(Files.exists(expectedCompressed));
        }

        @Test
        void returnsOnlySpecifiedRecordings_whenRecordingIdsProvided() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            copyJfrToSession("profile-2.jfr", FORMATTED_FILE_2);
            createFinishedFile();

            // Get all recordings first
            List<Path> allRecordings = storage.recordings(SESSION_ID);
            assertEquals(2, allRecordings.size());

            // Request only one specific recording using its ID (relative path without extension)
            List<Path> filtered = storage.recordings(SESSION_ID, List.of(FILE_ID_1));

            assertEquals(1, filtered.size());
            assertTrue(filtered.getFirst().toString().contains("profile-20250101-120000"));
        }

        @Test
        void filtersOutEmptyRecordingFiles() throws IOException {
            // Create one valid JFR and one empty JFR
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            Files.createFile(sessionPath.resolve(FORMATTED_FILE_2));
            createFinishedFile();

            List<Path> recordings = storage.recordings(SESSION_ID);

            assertEquals(1, recordings.size());
            assertTrue(recordings.getFirst().toString().contains("profile-20250101-120000"));
        }
    }

    @Nested
    class MergeRecordingsMethod {

        @Test
        void returnsMergedRecording_withCorrectPath() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            createFinishedFile();

            try (MergedRecording merged = storage.mergeRecordings(SESSION_ID)) {
                assertNotNull(merged.path());
                assertTrue(Files.exists(merged.path()));
                assertTrue(Files.size(merged.path()) > 0);
            }
        }

        @Test
        void mergedFileIsInTempDirectory() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            createFinishedFile();

            try (MergedRecording merged = storage.mergeRecordings(SESSION_ID)) {
                assertTrue(merged.path().startsWith(jeffreyTemp));
            }
        }

        @Test
        void mergedFileIsDeleted_whenClosed() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            createFinishedFile();

            Path mergedPath;
            try (MergedRecording merged = storage.mergeRecordings(SESSION_ID)) {
                mergedPath = merged.path();
                assertTrue(Files.exists(mergedPath));
            }

            assertFalse(Files.exists(mergedPath));
        }

        @Test
        void mergesOnlySpecifiedRecordings_whenRecordingIdsProvided() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            copyJfrToSession("profile-2.jfr", FORMATTED_FILE_2);
            createFinishedFile();

            // Merge all recordings
            long allSize;
            try (MergedRecording mergedAll = storage.mergeRecordings(SESSION_ID)) {
                allSize = Files.size(mergedAll.path());
            }

            // Merge only one recording using its ID (relative path without extension)
            long oneSize;
            try (MergedRecording mergedOne = storage.mergeRecordings(SESSION_ID, List.of(FILE_ID_1))) {
                oneSize = Files.size(mergedOne.path());
            }

            // Size of one recording should be smaller than both
            assertTrue(oneSize < allSize);
        }

        @Test
        void throwsEmptyRecordingSession_whenAllFilesAreEmpty() throws IOException {
            // Create only an empty JFR file
            Files.createFile(sessionPath.resolve(FORMATTED_FILE_1));
            createFinishedFile();

            JeffreyClientException exception = assertThrows(
                    JeffreyClientException.class,
                    () -> storage.mergeRecordings(SESSION_ID));

            assertEquals(ErrorCode.EMPTY_RECORDING_SESSION, exception.getCode());
        }

        @Test
        void mergesSuccessfully_whenSomeFilesAreEmpty() throws IOException {
            // One valid JFR + one empty JFR
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            Files.createFile(sessionPath.resolve(FORMATTED_FILE_2));
            createFinishedFile();

            try (MergedRecording merged = storage.mergeRecordings(SESSION_ID)) {
                assertNotNull(merged.path());
                assertTrue(Files.exists(merged.path()));
                assertTrue(Files.size(merged.path()) > 0);
            }
        }
    }

    @Nested
    class ArtifactsMethod {

        @Test
        void returnsArtifactPaths_excludingRecordings() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            // Create an artifact file (heap dump simulation)
            Path heapDump = sessionPath.resolve("heap.hprof");
            Files.writeString(heapDump, "fake heap dump content");
            createFinishedFile();

            List<Path> artifacts = storage.artifacts(SESSION_ID);

            // Should include the heap dump and the finisher file, but not the JFR file
            assertEquals(2, artifacts.size());
            assertTrue(artifacts.stream().anyMatch(p -> p.toString().endsWith(FileExtensions.HPROF)));
            assertTrue(artifacts.stream().anyMatch(p -> p.toString().endsWith(FileExtensions.PERF_COUNTERS)));
        }

        @Test
        void returnsOnlySpecifiedArtifacts_whenArtifactIdsProvided() throws IOException {
            // Create multiple artifact files
            Path heapDump = sessionPath.resolve("heap.hprof");
            Path logFile = sessionPath.resolve("app.log");
            Files.writeString(heapDump, "fake heap dump");
            Files.writeString(logFile, "fake log");
            createFinishedFile();

            // ID for artifacts is just the filename (extension is kept for non-recording files)
            String heapDumpId = "heap.hprof";
            List<Path> filtered = storage.artifacts(SESSION_ID, List.of(heapDumpId));

            assertEquals(1, filtered.size());
            assertTrue(filtered.getFirst().toString().endsWith(FileExtensions.HPROF));
        }

        @Test
        void returnsOnlyPerfCountersFile_whenNoOtherArtifacts() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            createFinishedFile();

            List<Path> artifacts = storage.artifacts(SESSION_ID);

            // Only the perf-counters file (perf-counters.hsperfdata) is an artifact
            assertEquals(1, artifacts.size());
            assertTrue(artifacts.getFirst().toString().endsWith(FileExtensions.PERF_COUNTERS));
        }
    }

    @Nested
    class CompressSessionMethod {

        @Test
        void compressesAllFinishedJfrFiles_andDeletesOriginals() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            copyJfrToSession("profile-2.jfr", FORMATTED_FILE_2);
            createFinishedFile();

            // Verify originals exist before compression
            assertTrue(Files.exists(sessionPath.resolve(FORMATTED_FILE_1)));
            assertTrue(Files.exists(sessionPath.resolve(FORMATTED_FILE_2)));

            int compressedCount = storage.compressSession(SESSION_ID);

            assertEquals(2, compressedCount);

            // Verify compressed files exist
            assertTrue(Files.exists(sessionPath.resolve(FORMATTED_FILE_1 + ".lz4")));
            assertTrue(Files.exists(sessionPath.resolve(FORMATTED_FILE_2 + ".lz4")));

            // Verify original files are deleted
            assertFalse(Files.exists(sessionPath.resolve(FORMATTED_FILE_1)));
            assertFalse(Files.exists(sessionPath.resolve(FORMATTED_FILE_2)));
        }

        @Test
        void returnsZero_whenNoFilesToCompress() throws IOException {
            // Only create .finished file, no JFR files
            createFinishedFile();

            int compressedCount = storage.compressSession(SESSION_ID);

            assertEquals(0, compressedCount);
        }

        @Test
        void skipsAlreadyCompressedFiles() throws IOException {
            // Create and compress one file
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            Path compressedFile = sessionPath.resolve(FORMATTED_FILE_1 + ".lz4");
            Lz4Compressor.compress(sessionPath.resolve(FORMATTED_FILE_1), compressedFile);
            Files.delete(sessionPath.resolve(FORMATTED_FILE_1)); // Remove original
            createFinishedFile();

            int compressedCount = storage.compressSession(SESSION_ID);

            // Returns count of compressed recordings (1 file exists)
            assertEquals(1, compressedCount);

            // Compressed file still exists
            assertTrue(Files.exists(compressedFile));
        }

        @Test
        void deletesOriginal_whenCompressedFileAlreadyExists() throws IOException {
            // Create JFR file and its compressed version
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            Path compressedFile = sessionPath.resolve(FORMATTED_FILE_1 + ".lz4");
            Lz4Compressor.compress(sessionPath.resolve(FORMATTED_FILE_1), compressedFile);
            // Keep both files (simulating interrupted previous compression)
            createFinishedFile();

            int compressedCount = storage.compressSession(SESSION_ID);

            // Returns count of compressed recordings, original is deleted
            assertEquals(1, compressedCount);
            assertTrue(Files.exists(compressedFile));
            assertFalse(Files.exists(sessionPath.resolve(FORMATTED_FILE_1)));
        }
    }

    @Nested
    class EventEmission {

        @Test
        void emitsEvent_whenFileIsCompressed() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            createFinishedFile();

            storage.recordings(SESSION_ID);

            // Verify event emitter was called once with correct parameters
            verify(eventEmitter, times(1)).emitRecordingFileCreated(
                    eq(projectInfo),
                    eq(SESSION_ID),
                    any(),
                    anyLong(),
                    anyLong(),
                    any(Path.class));
        }

        @Test
        void emitsEventForEachFile_whenMultipleFilesAreCompressed() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            copyJfrToSession("profile-2.jfr", FORMATTED_FILE_2);
            createFinishedFile();

            storage.recordings(SESSION_ID);

            // Verify event emitter was called twice (once per file)
            verify(eventEmitter, times(2)).emitRecordingFileCreated(
                    eq(projectInfo),
                    eq(SESSION_ID),
                    any(),
                    anyLong(),
                    anyLong(),
                    any(Path.class));
        }

        @Test
        void doesNotEmitEvent_whenFileAlreadyCompressed() throws IOException {
            // Create and compress file first
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            Path compressedFile = sessionPath.resolve(FORMATTED_FILE_1 + ".lz4");
            Lz4Compressor.compress(sessionPath.resolve(FORMATTED_FILE_1), compressedFile);
            Files.delete(sessionPath.resolve(FORMATTED_FILE_1)); // Remove original
            createFinishedFile();

            storage.recordings(SESSION_ID);

            // Verify event emitter was never called (file was already compressed)
            verify(eventEmitter, never()).emitRecordingFileCreated(
                    any(),
                    any(),
                    any(),
                    anyLong(),
                    anyLong(),
                    any(Path.class));
        }

        @Test
        void doesNotEmitEvent_whenCompressedVersionAlreadyExists() throws IOException {
            // Create both original and compressed file (simulating interrupted compression)
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            Path compressedFile = sessionPath.resolve(FORMATTED_FILE_1 + ".lz4");
            Lz4Compressor.compress(sessionPath.resolve(FORMATTED_FILE_1), compressedFile);
            // Keep original file (simulating race condition)
            createFinishedFile();

            storage.recordings(SESSION_ID);

            // Verify event emitter was not called (fast path - compressed file exists)
            verify(eventEmitter, never()).emitRecordingFileCreated(
                    any(),
                    any(),
                    any(),
                    anyLong(),
                    anyLong(),
                    any(Path.class));
        }

        @Test
        void emitsEventWithCorrectSizes() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            createFinishedFile();

            // Capture the original file size before compression
            long originalSize = Files.size(sessionPath.resolve(FORMATTED_FILE_1));

            storage.recordings(SESSION_ID);

            // Verify event was emitted with correct original size
            // Note: LZ4 compression has overhead, so compressed size can be larger for very small files
            verify(eventEmitter).emitRecordingFileCreated(
                    eq(projectInfo),
                    eq(SESSION_ID),
                    any(),
                    eq(originalSize),
                    longThat(compressedSize -> compressedSize > 0),
                    any(Path.class));
        }

        @Test
        void emitsEventWithCorrectPath() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            createFinishedFile();

            storage.recordings(SESSION_ID);

            Path expectedCompressedPath = sessionPath.resolve(FORMATTED_FILE_1 + ".lz4");

            verify(eventEmitter).emitRecordingFileCreated(
                    eq(projectInfo),
                    eq(SESSION_ID),
                    any(),
                    anyLong(),
                    anyLong(),
                    eq(expectedCompressedPath));
        }
    }
}
