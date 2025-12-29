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
import pbouda.jeffrey.common.compression.Lz4Compressor;
import pbouda.jeffrey.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.common.model.ProjectInfo;
import pbouda.jeffrey.common.model.RepositoryInfo;
import pbouda.jeffrey.common.model.RepositoryType;
import pbouda.jeffrey.common.model.workspace.RepositorySessionInfo;
import pbouda.jeffrey.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.platform.project.repository.file.AsprofFileInfoProcessor;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AsprofFileRemoteRepositoryStorageTest {

    private static final Path JFRS_DIR = Path.of("../common/src/test/resources/jfrs");
    private static final String SESSION_ID = "test-session-123";
    private static final String PROJECT_ID = "test-project-456";
    private static final String WORKSPACE_ID = "test-workspace-789";

    // Properly formatted filenames for AsprofFileInfoProcessor (profile-YYYYMMDD-HHmmss.jfr)
    private static final String FORMATTED_FILE_1 = "profile-20250101-120000.jfr";
    private static final String FORMATTED_FILE_2 = "profile-20250101-120001.jfr";

    // IDs are filenames without extension (FileSystemUtils.removeExtension uses getFileName())
    private static final String FILE_ID_1 = "profile-20250101-120000";
    private static final String FILE_ID_2 = "profile-20250101-120001";

    @TempDir
    Path tempDir;

    private Path repositoryPath;
    private Path sessionPath;
    private Path jeffreyHome;
    private Path jeffreyTemp;
    private JeffreyDirs jeffreyDirs;
    private ProjectRepositoryRepository projectRepositoryRepository;
    private AsprofFileRemoteRepositoryStorage storage;

    @BeforeEach
    void setUp() throws IOException {
        // Set up directory structure
        // Structure: workspacesPath / relativeWorkspacePath / relativeProjectPath / relativeSessionPath
        jeffreyHome = tempDir.resolve("jeffrey-home");
        jeffreyTemp = tempDir.resolve("jeffrey-temp");
        repositoryPath = tempDir.resolve("repository");
        sessionPath = repositoryPath.resolve(SESSION_ID);

        Files.createDirectories(jeffreyHome);
        Files.createDirectories(jeffreyTemp);
        Files.createDirectories(sessionPath);

        jeffreyDirs = new JeffreyDirs(jeffreyHome, jeffreyTemp);

        // Mock ProjectRepositoryRepository
        projectRepositoryRepository = mock(ProjectRepositoryRepository.class);

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
        RepositorySessionInfo sessionInfo = new RepositorySessionInfo(
                SESSION_ID,
                PROJECT_ID,
                Path.of(SESSION_ID),  // relativeSessionPath
                ".finished",          // finishedFile
                null,                 // profilerSettings
                now,                  // originCreatedAt
                now);                 // createdAt
        when(projectRepositoryRepository.findAllSessions()).thenReturn(List.of(sessionInfo));

        // Create ProjectInfo
        ProjectInfo projectInfo = new ProjectInfo(
                PROJECT_ID,
                PROJECT_ID,
                "Test Project",
                "Test Label",
                WORKSPACE_ID,
                WorkspaceType.LIVE,
                now,
                now,
                Map.of());

        // Create storage instance
        Clock fixedClock = Clock.fixed(now, ZoneId.systemDefault());
        storage = new AsprofFileRemoteRepositoryStorage(
                projectInfo,
                jeffreyDirs,
                projectRepositoryRepository,
                new AsprofFileInfoProcessor(),
                Duration.ofMinutes(5),
                fixedClock);
    }

    private void copyJfrToSession(String sourceFileName, String targetFileName) throws IOException {
        Path source = JFRS_DIR.resolve(sourceFileName);
        Path target = sessionPath.resolve(targetFileName);
        Files.copy(source, target);
    }

    private void createFinishedFile() throws IOException {
        Files.createFile(sessionPath.resolve(".finished"));
    }

    @Nested
    class RecordingsMethod {

        @Test
        void returnsCompressedPaths_whenFilesAlreadyCompressed() throws IOException {
            // Copy JFR and compress it
            Path jfrFile = sessionPath.resolve(FORMATTED_FILE_1);
            Files.copy(JFRS_DIR.resolve("profile-1.jfr"), jfrFile);
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
                assertTrue(merged.size() > 0);
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
        void compressesBeforeMerging_whenFilesAreUncompressed() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            createFinishedFile();

            try (MergedRecording merged = storage.mergeRecordings(SESSION_ID)) {
                // Verify the merged file is compressed (has .lz4 extension)
                assertTrue(merged.filename().endsWith(".jfr.lz4"));
            }

            // Verify compressed file was created persistently
            assertTrue(Files.exists(sessionPath.resolve(FORMATTED_FILE_1 + ".lz4")));
        }

        @Test
        void mergesOnlySpecifiedRecordings_whenRecordingIdsProvided() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            copyJfrToSession("profile-2.jfr", FORMATTED_FILE_2);
            createFinishedFile();

            // Merge all recordings
            long allSize;
            try (MergedRecording mergedAll = storage.mergeRecordings(SESSION_ID)) {
                allSize = mergedAll.size();
            }

            // Merge only one recording using its ID (relative path without extension)
            long oneSize;
            try (MergedRecording mergedOne = storage.mergeRecordings(SESSION_ID, List.of(FILE_ID_1))) {
                oneSize = mergedOne.size();
            }

            // Size of one recording should be smaller than both
            assertTrue(oneSize < allSize);
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

            // Should only include the heap dump, not the JFR file
            assertEquals(1, artifacts.size());
            assertTrue(artifacts.getFirst().toString().endsWith(".hprof"));
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
            assertTrue(filtered.getFirst().toString().endsWith(".hprof"));
        }

        @Test
        void returnsEmptyList_whenNoArtifacts() throws IOException {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            createFinishedFile();

            List<Path> artifacts = storage.artifacts(SESSION_ID);

            assertTrue(artifacts.isEmpty());
        }
    }

    @Nested
    class CompressionThreadSafety {

        @Test
        void compressesOnlyOnce_whenCalledConcurrently() throws Exception {
            copyJfrToSession("profile-1.jfr", FORMATTED_FILE_1);
            createFinishedFile();

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        List<Path> recordings = storage.recordings(SESSION_ID);
                        if (!recordings.isEmpty()) {
                            successCount.incrementAndGet();
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            assertTrue(doneLatch.await(30, TimeUnit.SECONDS));
            executor.shutdown();

            // All threads should succeed
            assertEquals(threadCount, successCount.get());

            // Only one compressed file should exist
            long compressedFileCount = Files.list(sessionPath)
                    .filter(p -> p.toString().endsWith(".jfr.lz4"))
                    .count();
            assertEquals(1, compressedFileCount);
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
}
