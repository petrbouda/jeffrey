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

package pbouda.jeffrey.project.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.common.filesystem.HomeDirs;
import pbouda.jeffrey.common.model.RepositoryType;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.common.model.repository.RepositoryFile;
import pbouda.jeffrey.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.project.repository.file.AsprofFileInfoProcessor;
import pbouda.jeffrey.project.repository.file.FilesystemFileInfoProcessor;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsprofFileRemoteRepositoryStorageTest {

    @Mock
    private ProjectRepositoryRepository projectRepositoryRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private HomeDirs homeDirs;

    private AsprofFileRemoteRepositoryStorage storage;
    private Clock clock;

    @TempDir
    private Path tempDir;

    private static final String PROJECT_ID = "test-project-id";
    private static final String SESSION_ID = "test-session";
    private static final Duration FINISHED_PERIOD = Duration.ofMinutes(5);

    @BeforeEach
    void setUp() {
        Instant fixedTime = Instant.parse("2025-08-13T10:45:00Z");
        clock = Clock.fixed(fixedTime, ZoneOffset.UTC);
        storage = new AsprofFileRemoteRepositoryStorage(
                PROJECT_ID,
                homeDirs,
                projectRepositoryRepository,
                workspaceRepository,
                new FilesystemFileInfoProcessor(),
                FINISHED_PERIOD,
                clock);
    }

    @Nested
    @DisplayName("Delete Repository Files Tests")
    class DeleteRepositoryFilesTests {

        @Test
        @DisplayName("Should delete repository files when session exists")
        void deleteRepositoryFiles_whenSessionExists_shouldDeleteFiles() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create test files
            Path file1 = Files.createFile(sessionPath.resolve("file1.jfr"));
            Path file2 = Files.createFile(sessionPath.resolve("file2.jfr"));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionByProjectIdAndSessionId(PROJECT_ID, SESSION_ID))
                    .thenReturn(Optional.of(sessionInfo));

            List<String> filesToDelete = List.of(
                    workspacePath.relativize(file1).toString(),
                    workspacePath.relativize(file2).toString()
            );

            // When
            storage.deleteRepositoryFiles(SESSION_ID, filesToDelete);

            // Then
            assertFalse(Files.exists(file1), "File 1 should be deleted");
            assertFalse(Files.exists(file2), "File 2 should be deleted");
        }

        @Test
        @DisplayName("Should not throw exception when session does not exist")
        void deleteRepositoryFiles_whenSessionDoesNotExist_shouldNotThrowException() {
            // Given
            when(workspaceRepository.findSessionByProjectIdAndSessionId(PROJECT_ID, SESSION_ID))
                    .thenReturn(Optional.empty());

            List<String> filesToDelete = List.of("file1.jfr", "file2.jfr");

            // When & Then
            assertDoesNotThrow(() -> storage.deleteRepositoryFiles(SESSION_ID, filesToDelete));
        }

        @Test
        @DisplayName("Should not throw exception when session directory does not exist")
        void deleteRepositoryFiles_whenSessionDirectoryDoesNotExist_shouldNotThrowException() {
            // Given
            Path workspacePath = tempDir;

            // Mock workspace session with non-existent path
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, "non-existent-session", workspacePath);
            when(workspaceRepository.findSessionByProjectIdAndSessionId(PROJECT_ID, SESSION_ID))
                    .thenReturn(Optional.of(sessionInfo));

            List<String> filesToDelete = List.of("file1.jfr", "file2.jfr");

            // When & Then
            assertDoesNotThrow(() -> storage.deleteRepositoryFiles(SESSION_ID, filesToDelete));
        }

        @Test
        @DisplayName("Should handle file not found gracefully")
        void deleteRepositoryFiles_whenFileNotFound_shouldHandleGracefully() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create only one file
            Path file1 = Files.createFile(sessionPath.resolve("file1.jfr"));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionByProjectIdAndSessionId(PROJECT_ID, SESSION_ID))
                    .thenReturn(Optional.of(sessionInfo));

            // Try to delete both existing and non-existing files
            List<String> filesToDelete = List.of(
                    workspacePath.relativize(file1).toString(),
                    "non-existent-file.jfr"
            );

            // When & Then
            assertDoesNotThrow(() -> storage.deleteRepositoryFiles(SESSION_ID, filesToDelete));
            assertFalse(Files.exists(file1), "Existing file should be deleted");
        }
    }

    @Nested
    @DisplayName("Delete Session Tests")
    class DeleteSessionTests {

        @Test
        @DisplayName("Should delete session directory when session exists")
        void deleteSession_whenSessionExists_shouldDeleteDirectory() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create test files in session directory
            Files.createFile(sessionPath.resolve("file1.jfr"));
            Files.createFile(sessionPath.resolve("file2.jfr"));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionByProjectIdAndSessionId(PROJECT_ID, SESSION_ID))
                    .thenReturn(Optional.of(sessionInfo));

            // When
            storage.deleteSession(SESSION_ID);

            // Then
            assertFalse(Files.exists(sessionPath), "Session directory should be deleted");
        }

        @Test
        @DisplayName("Should not throw exception when session does not exist")
        void deleteSession_whenSessionDoesNotExist_shouldNotThrowException() {
            // Given
            when(workspaceRepository.findSessionByProjectIdAndSessionId(PROJECT_ID, SESSION_ID))
                    .thenReturn(Optional.empty());

            // When & Then
            assertDoesNotThrow(() -> storage.deleteSession(SESSION_ID));
        }

        @Test
        @DisplayName("Should not throw exception when session directory does not exist")
        void deleteSession_whenSessionDirectoryDoesNotExist_shouldNotThrowException() {
            // Given
            Path workspacePath = tempDir;

            // Mock workspace session with non-existent path
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, "non-existent-session", workspacePath);
            when(workspaceRepository.findSessionByProjectIdAndSessionId(PROJECT_ID, SESSION_ID))
                    .thenReturn(Optional.of(sessionInfo));

            // When & Then
            assertDoesNotThrow(() -> storage.deleteSession(SESSION_ID));
        }

        @Test
        @DisplayName("Should delete nested directories and files")
        void deleteSession_withNestedDirectories_shouldDeleteAll() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create nested directory structure
            Path nestedDir = Files.createDirectories(sessionPath.resolve("nested"));
            Files.createFile(sessionPath.resolve("file1.jfr"));
            Files.createFile(nestedDir.resolve("nested-file.jfr"));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionByProjectIdAndSessionId(PROJECT_ID, SESSION_ID))
                    .thenReturn(Optional.of(sessionInfo));

            // When
            storage.deleteSession(SESSION_ID);

            // Then
            assertFalse(Files.exists(sessionPath), "Session directory should be deleted");
            assertFalse(Files.exists(nestedDir), "Nested directory should be deleted");
        }
    }

    @Nested
    @DisplayName("Single Session Tests")
    class SingleSessionTests {

        @Test
        @DisplayName("Should return session when it exists")
        void singleSession_whenSessionExists_shouldReturnSession() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));
            
            // Create test files
            Path file1 = Files.createFile(sessionPath.resolve("recording1.jfr"));
            Path file2 = Files.createFile(sessionPath.resolve("recording2.jfr"));
            
            // Set file modification times to be outside the finished period (old)
            Instant now = clock.instant();
            Instant oldTime = now.minus(Duration.ofMinutes(10)); // Outside 5-minute finished period
            Files.setLastModifiedTime(file1, java.nio.file.attribute.FileTime.from(oldTime));
            Files.setLastModifiedTime(file2, java.nio.file.attribute.FileTime.from(oldTime));
            
            // Mock repository info
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));
            
            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));
            
            // When
            Optional<RecordingSession> result = storage.singleSession(SESSION_ID);
            
            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(SESSION_ID, result.get().id(), "Session ID should match");
            assertEquals(RecordingStatus.FINISHED, result.get().status(), "Status should be FINISHED");
        }

        @Test
        @DisplayName("Should return empty when session does not exist")
        void singleSession_whenSessionDoesNotExist_shouldReturnEmpty() {
            // Given
            String nonExistentSessionId = "non-existent-session";

            // Mock empty workspace sessions list
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of());

            // When
            Optional<RecordingSession> result = storage.singleSession(nonExistentSessionId);

            // Then
            assertTrue(result.isEmpty(), "Result should be empty for non-existent session");
        }

        @Test
        @DisplayName("Should return empty when session directory does not exist")
        void singleSession_whenSessionDirectoryDoesNotExist_shouldReturnEmptyRecordings() throws IOException {
            // Given
            Path workspacePath = tempDir;
            // Create the directory but don't put any files in it
            Files.createDirectories(workspacePath.resolve("empty-session-path"));

            // Mock repository info
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Mock workspace session with empty path
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, "empty-session-path", workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            Optional<RecordingSession> result = storage.singleSession(SESSION_ID);

            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertTrue(result.get().files().isEmpty(), "Files list should be empty");
        }
    }

    @Nested
    @DisplayName("List Sessions Tests")
    class ListSessionsTests {

        @Test
        @DisplayName("Should return multiple sessions when they exist")
        void listSessions_withMultipleSessions_shouldReturnAllSessions() throws IOException {
            // Given
            Path workspacePath = tempDir;

            // Create multiple session directories with files
            String session1Id = "session-1";
            String session2Id = "session-2";
            String session3Id = "session-3";

            Path session1Path = Files.createDirectories(workspacePath.resolve(session1Id));
            Path session2Path = Files.createDirectories(workspacePath.resolve(session2Id));
            Path session3Path = Files.createDirectories(workspacePath.resolve(session3Id));

            // Create files in each session
            Files.createFile(session1Path.resolve("recording1.jfr"));
            Files.createFile(session2Path.resolve("recording2.jfr"));
            Files.createFile(session3Path.resolve("recording3.jfr"));

            // Mock repository info
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Create session infos with different creation times to test sorting
            Instant now = clock.instant();
            WorkspaceSessionInfo sessionInfo1 = createWorkspaceSessionInfo(
                    session1Id, session1Id, workspacePath, now.minusSeconds(300)); // Oldest
            WorkspaceSessionInfo sessionInfo2 = createWorkspaceSessionInfo(
                    session2Id, session2Id, workspacePath, now.minusSeconds(200));
            WorkspaceSessionInfo sessionInfo3 = createWorkspaceSessionInfo(
                    session3Id, session3Id, workspacePath, now.minusSeconds(100)); // Newest

            // Mock workspace sessions - order doesn't matter as they should be sorted by creation time
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo1, sessionInfo2, sessionInfo3));

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size(), "Should return all 3 sessions");

            // Verify sessions are sorted by creation time (newest first)
            assertEquals(session3Id, result.getFirst().id(), "First session should be the newest");
            assertEquals(session2Id, result.get(1).id(), "Second session should be the middle one");
            assertEquals(session1Id, result.get(2).id(), "Third session should be the oldest");

            // Verify status - only latest session can be non-FINISHED
            assertEquals(RecordingStatus.FINISHED, result.get(1).status(), "Non-latest sessions should be FINISHED");
            assertEquals(RecordingStatus.FINISHED, result.get(2).status(), "Non-latest sessions should be FINISHED");
        }

        @Test
        @DisplayName("Should return empty list when no sessions exist")
        void listSessions_withNoSessions_shouldReturnEmptyList() {
            // Given
            // Mock empty workspace sessions list
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of());

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertTrue(result.isEmpty(), "Result should be empty when no sessions exist");
        }

        @Test
        @DisplayName("Should handle sessions with empty directories")
        void listSessions_withEmptyDirectories_shouldHandleGracefully() throws IOException {
            // Given
            Path workspacePath = tempDir;

            // Create empty session directories
            String session1Id = "empty-session-1";
            String session2Id = "empty-session-2";

            // Create the directories but don't put any files in them
            Files.createDirectories(workspacePath.resolve(session1Id));
            Files.createDirectories(workspacePath.resolve(session2Id));

            // Mock repository info
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Create session infos with different creation times
            Instant now = clock.instant();
            WorkspaceSessionInfo sessionInfo1 = createWorkspaceSessionInfo(
                    session1Id, session1Id, workspacePath, now.minusSeconds(200));
            WorkspaceSessionInfo sessionInfo2 = createWorkspaceSessionInfo(
                    session2Id, session2Id, workspacePath, now.minusSeconds(100));

            // Mock workspace sessions
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo1, sessionInfo2));

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(2, result.size(), "Should return both sessions with empty directories");
            assertTrue(result.getFirst().files().isEmpty(), "Files list should be empty for empty directory");
            assertTrue(result.get(1).files().isEmpty(), "Files list should be empty for empty directory");
        }

        @Test
        @DisplayName("Should handle sessions with HEAP_DUMP files")
        void listSessions_withHeapDumpFiles_shouldHandleCorrectly() throws IOException {
            // Given
            Path workspacePath = tempDir;

            // Create session directory with heap dump files
            String sessionId = "heap-dump-session";
            Path sessionPath = Files.createDirectories(workspacePath.resolve(sessionId));

            // Create heap dump files
            Files.createFile(sessionPath.resolve("heap-dump1.hprof"));
            Files.createFile(sessionPath.resolve("heap-dump2.hprof"));

            // Mock repository info
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Create session info
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(
                    sessionId, sessionId, workspacePath);

            // Mock workspace session
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(1, result.size(), "Should return one session");
            assertEquals(sessionId, result.getFirst().id(), "Session ID should match");

            // Verify files
            assertEquals(2, result.getFirst().files().size(), "Should have two files");

            // Verify file types
            var files = result.getFirst().files();
            assertTrue(files.stream().allMatch(file -> file.fileType() == SupportedRecordingFile.HEAP_DUMP),
                    "All files should be of type HEAP_DUMP");

            // Verify file names
            var fileNames = files.stream().map(RepositoryFile::name).toList();
            assertTrue(fileNames.contains("heap-dump1.hprof"), "Should contain first heap dump file");
            assertTrue(fileNames.contains("heap-dump2.hprof"), "Should contain second heap dump file");
        }

        @Test
        @DisplayName("Should handle sessions with PERF_COUNTERS files")
        void listSessions_withPerfCountersFiles_shouldHandleCorrectly() throws IOException {
            // Given
            Path workspacePath = tempDir;

            // Create session directory with perf counters files
            String sessionId = "perf-counters-session";
            Path sessionPath = Files.createDirectories(workspacePath.resolve(sessionId));

            // Create perf counters files
            Files.createFile(sessionPath.resolve("perf-counters1.hsperfdata"));
            Files.createFile(sessionPath.resolve("perf-counters2.hsperfdata"));

            // Mock repository info
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Create session info
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(
                    sessionId, sessionId, workspacePath);

            // Mock workspace session
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(1, result.size(), "Should return one session");
            assertEquals(sessionId, result.getFirst().id(), "Session ID should match");

            // Verify files
            assertEquals(2, result.getFirst().files().size(), "Should have two files");

            // Verify file types
            var files = result.getFirst().files();
            assertTrue(files.stream().allMatch(file -> file.fileType() == SupportedRecordingFile.PERF_COUNTERS),
                    "All files should be of type PERF_COUNTERS");

            // Verify file names
            var fileNames = files.stream().map(RepositoryFile::name).toList();
            assertTrue(fileNames.contains("perf-counters1.hsperfdata"), "Should contain first perf counters file");
            assertTrue(fileNames.contains("perf-counters2.hsperfdata"), "Should contain second perf counters file");
        }

        @Test
        @DisplayName("Should handle sessions with mixed file types")
        void listSessions_withMixedFileTypes_shouldHandleCorrectly() throws IOException {
            // Given
            Path workspacePath = tempDir;

            // Create session directory with mixed file types
            String sessionId = "mixed-files-session";
            Path sessionPath = Files.createDirectories(workspacePath.resolve(sessionId));

            // Create different types of files
            Files.createFile(sessionPath.resolve("recording.jfr"));
            Files.createFile(sessionPath.resolve("heap-dump.hprof"));
            Files.createFile(sessionPath.resolve("perf-counters.hsperfdata"));

            // Mock repository info
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Create session info
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(
                    sessionId, sessionId, workspacePath);

            // Mock workspace session
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(1, result.size(), "Should return one session");
            assertEquals(sessionId, result.getFirst().id(), "Session ID should match");

            // Verify files
            assertEquals(3, result.getFirst().files().size(), "Should have three files");

            // Verify file types
            var files = result.getFirst().files();
            long jfrCount = files.stream().filter(file -> file.fileType() == SupportedRecordingFile.JFR).count();
            long heapDumpCount = files.stream().filter(file -> file.fileType() == SupportedRecordingFile.HEAP_DUMP).count();
            long perfCountersCount = files.stream().filter(file -> file.fileType() == SupportedRecordingFile.PERF_COUNTERS).count();

            assertEquals(1, jfrCount, "Should have one JFR file");
            assertEquals(1, heapDumpCount, "Should have one HEAP_DUMP file");
            assertEquals(1, perfCountersCount, "Should have one PERF_COUNTERS file");

            // Verify file names
            var fileNames = files.stream().map(RepositoryFile::name).toList();
            assertTrue(fileNames.contains("recording.jfr"), "Should contain JFR file");
            assertTrue(fileNames.contains("heap-dump.hprof"), "Should contain heap dump file");
            assertTrue(fileNames.contains("perf-counters.hsperfdata"), "Should contain perf counters file");

            // Verify isRecordingFile flag - only JFR files should be marked as recording files
            var recordingFiles = files.stream().filter(RepositoryFile::isRecordingFile).toList();
            assertEquals(1, recordingFiles.size(), "Should have one recording file");
            assertEquals(SupportedRecordingFile.JFR, recordingFiles.getFirst().fileType(), "Recording file should be of type JFR");
        }
    }

    @Nested
    @DisplayName("Session Status Tests")
    class SessionStatusTests {

        private static final String DETECTION_FILE_NAME = "finished.txt";

        @Test
        @DisplayName("Should return UNKNOWN status for latest session with no files and no detection file")
        void singleSession_withNoFilesNoDetectionFile_shouldReturnUnknown() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Mock repository info with detection file
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, DETECTION_FILE_NAME);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            Optional<RecordingSession> result = storage.singleSession(SESSION_ID);

            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(RecordingStatus.UNKNOWN, result.get().status(), "Status should be UNKNOWN");
        }

        @Test
        @DisplayName("Should return UNKNOWN status for latest session with files but no detection file and within finished period")
        void singleSession_withFilesNoDetectionFileWithinPeriod_shouldReturnUnknown() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create a file in the session directory
            Path recordingFile = Files.createFile(sessionPath.resolve("recording.jfr"));

            // Set file modification time to be within the finished period (recent)
            Instant now = clock.instant();
            Instant recentTime = now.minus(Duration.ofMinutes(2)); // Within 5-minute finished period
            Files.setLastModifiedTime(recordingFile, java.nio.file.attribute.FileTime.from(recentTime));

            // Mock repository info with no detection file (will use WithoutDetectionFileStrategy)
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            Optional<RecordingSession> result = storage.singleSession(SESSION_ID);

            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(RecordingStatus.UNKNOWN, result.get().status(), "Status should be UNKNOWN");
        }

        @Test
        @DisplayName("Should return UNKNOWN status in list for latest session with no detection file")
        void listSessions_latestSessionWithNoDetectionFile_shouldReturnUnknown() throws IOException {
            // Given
            Path workspacePath = tempDir;

            // Create multiple session directories with files
            String session1Id = "session-1";
            String session2Id = "session-2"; // This will be the latest session

            Path session1Path = Files.createDirectories(workspacePath.resolve(session1Id));
            Path session2Path = Files.createDirectories(workspacePath.resolve(session2Id));

            // Create files in each session
            Files.createFile(session1Path.resolve("recording1.jfr"));
            Path recordingFile2 = Files.createFile(session2Path.resolve("recording2.jfr"));

            // Set file modification time for latest session to be within the finished period
            Instant now = clock.instant();
            Instant recentTime = now.minus(Duration.ofMinutes(2)); // Within 5-minute finished period
            Files.setLastModifiedTime(recordingFile2, java.nio.file.attribute.FileTime.from(recentTime));

            // Mock repository info with no detection file (will use WithoutDetectionFileStrategy)
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Create session infos with different creation times
            WorkspaceSessionInfo sessionInfo1 = createWorkspaceSessionInfo(
                    session1Id, session1Id, workspacePath, now.minusSeconds(200)); // Older
            WorkspaceSessionInfo sessionInfo2 = createWorkspaceSessionInfo(
                    session2Id, session2Id, workspacePath, now.minusSeconds(100)); // Newer (latest)

            // Mock workspace sessions
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo1, sessionInfo2));

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(2, result.size(), "Should return both sessions");

            // Latest session (first in the list) should have UNKNOWN status
            assertEquals(session2Id, result.getFirst().id(), "First session should be the latest");
            assertEquals(RecordingStatus.UNKNOWN, result.getFirst().status(), "Latest session should have UNKNOWN status");

            // Older session should have FINISHED status
            assertEquals(session1Id, result.get(1).id(), "Second session should be the older one");
            assertEquals(RecordingStatus.FINISHED, result.get(1).status(), "Older session should have FINISHED status");
        }

        @Test
        @DisplayName("Should return ACTIVE status for latest session with detection file strategy and recent files")
        void singleSession_withDetectionFileStrategyAndRecentFiles_shouldReturnActive() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create a file in the session directory
            Path recordingFile = Files.createFile(sessionPath.resolve("recording.jfr"));

            // Set file modification time to be within the finished period (recent)
            Instant now = clock.instant();
            Instant recentTime = now.minus(Duration.ofMinutes(2)); // Within 5-minute finished period
            Files.setLastModifiedTime(recordingFile, java.nio.file.attribute.FileTime.from(recentTime));

            // Mock repository info with detection file (will use WithDetectionFileStrategy)
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, DETECTION_FILE_NAME);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            Optional<RecordingSession> result = storage.singleSession(SESSION_ID);

            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(RecordingStatus.ACTIVE, result.get().status(), "Status should be ACTIVE");
        }

        @Test
        @DisplayName("Should return ACTIVE status in list for latest session with detection file strategy and recent files")
        void listSessions_latestSessionWithDetectionFileStrategyAndRecentFiles_shouldReturnActive() throws IOException {
            // Given
            Path workspacePath = tempDir;

            // Create multiple session directories with files
            String session1Id = "session-1";
            String session2Id = "session-2"; // This will be the latest session

            Path session1Path = Files.createDirectories(workspacePath.resolve(session1Id));
            Path session2Path = Files.createDirectories(workspacePath.resolve(session2Id));

            // Create files in each session
            Files.createFile(session1Path.resolve("recording1.jfr"));
            Path recordingFile2 = Files.createFile(session2Path.resolve("recording2.jfr"));

            // Set file modification time for latest session to be within the finished period
            Instant now = clock.instant();
            Instant recentTime = now.minus(Duration.ofMinutes(2)); // Within 5-minute finished period
            Files.setLastModifiedTime(recordingFile2, java.nio.file.attribute.FileTime.from(recentTime));

            // Mock repository info with detection file (will use WithDetectionFileStrategy)
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, DETECTION_FILE_NAME);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Create session infos with different creation times
            WorkspaceSessionInfo sessionInfo1 = createWorkspaceSessionInfo(
                    session1Id, session1Id, workspacePath, now.minusSeconds(200)); // Older
            WorkspaceSessionInfo sessionInfo2 = createWorkspaceSessionInfo(
                    session2Id, session2Id, workspacePath, now.minusSeconds(100)); // Newer (latest)

            // Mock workspace sessions
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo1, sessionInfo2));

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(2, result.size(), "Should return both sessions");

            // Latest session (first in the list) should have ACTIVE status
            assertEquals(session2Id, result.getFirst().id(), "First session should be the latest");
            assertEquals(RecordingStatus.ACTIVE, result.getFirst().status(), "Latest session should have ACTIVE status");

            // Older session should have FINISHED status
            assertEquals(session1Id, result.get(1).id(), "Second session should be the older one");
            assertEquals(RecordingStatus.FINISHED, result.get(1).status(), "Older session should have FINISHED status");
        }

        @Test
        @DisplayName("Should return FINISHED status for latest session with detection file present")
        void singleSession_withDetectionFilePresent_shouldReturnFinished() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create a file in the session directory
            Path recordingFile = Files.createFile(sessionPath.resolve("recording.jfr"));

            // Create the detection file
            Files.createFile(sessionPath.resolve(DETECTION_FILE_NAME));

            // Set file modification time to be within the finished period (recent)
            Instant now = clock.instant();
            Instant recentTime = now.minus(Duration.ofMinutes(2)); // Within 5-minute finished period
            Files.setLastModifiedTime(recordingFile, java.nio.file.attribute.FileTime.from(recentTime));

            // Mock repository info with detection file (will use WithDetectionFileStrategy)
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, DETECTION_FILE_NAME);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            Optional<RecordingSession> result = storage.singleSession(SESSION_ID);

            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(RecordingStatus.FINISHED, result.get().status(), "Status should be FINISHED");
        }

        @Test
        @DisplayName("Should return FINISHED status for latest session with old files")
        void singleSession_withOldFiles_shouldReturnFinished() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create a file in the session directory
            Path recordingFile = Files.createFile(sessionPath.resolve("recording.jfr"));

            // Set file modification time to be outside the finished period (old)
            Instant now = clock.instant();
            Instant oldTime = now.minus(Duration.ofMinutes(10)); // Outside 5-minute finished period
            Files.setLastModifiedTime(recordingFile, java.nio.file.attribute.FileTime.from(oldTime));

            // Mock repository info with detection file (will use WithDetectionFileStrategy)
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, DETECTION_FILE_NAME);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            Optional<RecordingSession> result = storage.singleSession(SESSION_ID);

            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(RecordingStatus.FINISHED, result.get().status(), "Status should be FINISHED");
        }

        @Test
        @DisplayName("Should return FINISHED status for non-latest session regardless of files")
        void singleSession_nonLatestSession_shouldReturnFinished() throws IOException {
            // Given
            Path workspacePath = tempDir;

            // Create multiple session directories with files
            String session1Id = "session-1"; // This will be the older session
            String session2Id = "session-2"; // This will be the latest session

            Path session1Path = Files.createDirectories(workspacePath.resolve(session1Id));
            Path session2Path = Files.createDirectories(workspacePath.resolve(session2Id));

            // Create files in each session
            Path recordingFile1 = Files.createFile(session1Path.resolve("recording1.jfr"));
            Files.createFile(session2Path.resolve("recording2.jfr"));

            // Set file modification time for older session to be recent (within finished period)
            // Even though it's recent, it should still be FINISHED because it's not the latest session
            Instant now = clock.instant();
            Instant recentTime = now.minus(Duration.ofMinutes(2)); // Within 5-minute finished period
            Files.setLastModifiedTime(recordingFile1, java.nio.file.attribute.FileTime.from(recentTime));

            // Mock repository info
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, DETECTION_FILE_NAME);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Create session infos with different creation times
            WorkspaceSessionInfo sessionInfo1 = createWorkspaceSessionInfo(
                    session1Id, session1Id, workspacePath, now.minusSeconds(200)); // Older
            WorkspaceSessionInfo sessionInfo2 = createWorkspaceSessionInfo(
                    session2Id, session2Id, workspacePath, now.minusSeconds(100)); // Newer (latest)

            // Mock workspace sessions
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo1, sessionInfo2));

            // When - get the older session
            Optional<RecordingSession> result = storage.singleSession(session1Id);

            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(RecordingStatus.FINISHED, result.get().status(), "Status should be FINISHED for non-latest session");
        }
    }

    @Nested
    @DisplayName("File Status Tests")
    class FileStatusTests {

        private static final String FINISHED_FILE_NAME = "finished.txt";

        @Test
        @DisplayName("Should set file status to UNKNOWN when session status is UNKNOWN")
        void singleSession_withUnknownSessionStatus_shouldSetFileStatusToUnknown() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create a recording file in the session directory
            Path recordingFile = Files.createFile(sessionPath.resolve("recording.jfr"));

            // Set file modification time to be within the finished period (recent)
            Instant now = clock.instant();
            Instant recentTime = now.minus(Duration.ofMinutes(2)); // Within 5-minute finished period
            Files.setLastModifiedTime(recordingFile, java.nio.file.attribute.FileTime.from(recentTime));

            // Mock repository info with no detection file (will use WithoutDetectionFileStrategy)
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            Optional<RecordingSession> result = storage.singleSession(SESSION_ID);

            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(RecordingStatus.UNKNOWN, result.get().status(), "Session status should be UNKNOWN");

            // Verify file status
            assertFalse(result.get().files().isEmpty(), "Files list should not be empty");
            assertEquals(1, result.get().files().size(), "Should have one file");
            assertEquals(RecordingStatus.UNKNOWN, result.get().files().getFirst().status(), "File status should match session status (UNKNOWN)");
        }

        @Test
        @DisplayName("Should set file status to ACTIVE when session status is ACTIVE")
        void singleSession_withActiveSessionStatus_shouldSetFileStatusToActive() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create a recording file in the session directory
            Path recordingFile = Files.createFile(sessionPath.resolve("recording.jfr"));

            // Set file modification time to be within the finished period (recent)
            Instant now = clock.instant();
            Instant recentTime = now.minus(Duration.ofMinutes(2)); // Within 5-minute finished period
            Files.setLastModifiedTime(recordingFile, java.nio.file.attribute.FileTime.from(recentTime));

            // Mock repository info with detection file (will use WithDetectionFileStrategy)
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, FINISHED_FILE_NAME);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            Optional<RecordingSession> result = storage.singleSession(SESSION_ID);

            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(RecordingStatus.ACTIVE, result.get().status(), "Session status should be ACTIVE");

            // Verify file status
            assertFalse(result.get().files().isEmpty(), "Files list should not be empty");
            assertEquals(1, result.get().files().size(), "Should have one file");
            assertEquals(RecordingStatus.ACTIVE, result.get().files().getFirst().status(), "File status should match session status (ACTIVE)");
        }

        @Test
        @DisplayName("Should set file status to FINISHED when session status is FINISHED")
        void singleSession_withFinishedSessionStatus_shouldSetFileStatusToFinished() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create a recording file in the session directory
            Path recordingFile = Files.createFile(sessionPath.resolve("recording.jfr"));

            // Create the detection file to force FINISHED status
            Files.createFile(sessionPath.resolve(FINISHED_FILE_NAME));

            // Set file modification time to be within the finished period (recent)
            Instant now = clock.instant();
            Instant recentTime = now.minus(Duration.ofMinutes(2)); // Within 5-minute finished period
            Files.setLastModifiedTime(recordingFile, java.nio.file.attribute.FileTime.from(recentTime));

            // Mock repository info with detection file (will use WithDetectionFileStrategy)
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, FINISHED_FILE_NAME);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            Optional<RecordingSession> result = storage.singleSession(SESSION_ID);

            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(RecordingStatus.FINISHED, result.get().status(), "Session status should be FINISHED");

            // Verify file status
            assertFalse(result.get().files().isEmpty(), "Files list should not be empty");
            assertEquals(2, result.get().files().size(), "Should have two files (recording and detection file)");

            // Find the recording file (not the finishing file)
            var recordingFileResult = result.get().files().stream()
                    .filter(file -> !file.isFinishingFile())
                    .findFirst();

            assertTrue(recordingFileResult.isPresent(), "Recording file should be present");
            assertEquals(RecordingStatus.FINISHED, recordingFileResult.get().status(),
                    "Recording file status should match session status (FINISHED)");
        }

        @Test
        @DisplayName("Should set only latest recording file status to match session status")
        void singleSession_withMultipleFiles_shouldSetOnlyLatestFileStatus() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create multiple recording files in the session directory
            Path oldRecordingFile = Files.createFile(sessionPath.resolve("old-recording.jfr"));
            Path latestRecordingFile = Files.createFile(sessionPath.resolve("latest-recording.jfr"));

            // Set file modification times - one old, one recent
            Instant now = clock.instant();
            Instant oldTime = now.minus(Duration.ofMinutes(10)); // Outside 5-minute finished period
            Instant recentTime = now.minus(Duration.ofMinutes(2)); // Within 5-minute finished period

            Files.setLastModifiedTime(oldRecordingFile, java.nio.file.attribute.FileTime.from(oldTime));
            Files.setLastModifiedTime(latestRecordingFile, java.nio.file.attribute.FileTime.from(recentTime));

            // Mock repository info with detection file (will use WithDetectionFileStrategy)
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, FINISHED_FILE_NAME);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            Optional<RecordingSession> result = storage.singleSession(SESSION_ID);

            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(RecordingStatus.ACTIVE, result.get().status(), "Session status should be ACTIVE");

            // Verify file statuses
            assertEquals(2, result.get().files().size(), "Should have two files");

            // Files should be sorted by modification time (newest first)
            var files = result.get().files();

            // Latest file should have ACTIVE status
            assertEquals("latest-recording.jfr", files.getFirst().name(), "First file should be the latest");
            assertEquals(RecordingStatus.ACTIVE, files.getFirst().status(), "Latest file status should match session status (ACTIVE)");

            // Older file should have FINISHED status
            assertEquals("old-recording.jfr", files.get(1).name(), "Second file should be the older one");
            assertEquals(RecordingStatus.FINISHED, files.get(1).status(), "Older file status should be FINISHED");
        }

        @Test
        @DisplayName("Should set only latest recording file status to match session status with multiple files")
        void singleSession_withMultipleFiles_shouldSetOnlyLatestFileStatusWithMultipleFiles() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create multiple recording files in the session directory
            Path oldestRecordingFile = Files.createFile(sessionPath.resolve("oldest-recording.jfr"));
            Path olderRecordingFile = Files.createFile(sessionPath.resolve("older-recording.jfr"));
            Path middleRecordingFile = Files.createFile(sessionPath.resolve("middle-recording.jfr"));
            Path latestRecordingFile = Files.createFile(sessionPath.resolve("latest-recording.jfr"));

            // Set file modification times with different timestamps
            Instant now = clock.instant();
            Instant oldestTime = now.minus(Duration.ofMinutes(20)); // Oldest file
            Instant olderTime = now.minus(Duration.ofMinutes(15));  // Older file
            Instant middleTime = now.minus(Duration.ofMinutes(10)); // Middle file
            Instant recentTime = now.minus(Duration.ofMinutes(2));  // Latest file within 5-minute finished period

            Files.setLastModifiedTime(oldestRecordingFile, java.nio.file.attribute.FileTime.from(oldestTime));
            Files.setLastModifiedTime(olderRecordingFile, java.nio.file.attribute.FileTime.from(olderTime));
            Files.setLastModifiedTime(middleRecordingFile, java.nio.file.attribute.FileTime.from(middleTime));
            Files.setLastModifiedTime(latestRecordingFile, java.nio.file.attribute.FileTime.from(recentTime));

            // Mock repository info with detection file (will use WithDetectionFileStrategy)
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, FINISHED_FILE_NAME);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            Optional<RecordingSession> result = storage.singleSession(SESSION_ID);

            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(RecordingStatus.ACTIVE, result.get().status(), "Session status should be ACTIVE");

            // Verify file statuses
            assertEquals(4, result.get().files().size(), "Should have four files");

            // Files should be sorted by modification time (newest first)
            var files = result.get().files();

            // Latest file should have ACTIVE status
            assertEquals("latest-recording.jfr", files.getFirst().name(), "First file should be the latest");
            assertEquals(RecordingStatus.ACTIVE, files.getFirst().status(), "Latest file status should match session status (ACTIVE)");

            // All other files should have FINISHED status
            assertEquals("middle-recording.jfr", files.get(1).name(), "Second file should be the middle one");
            assertEquals(RecordingStatus.FINISHED, files.get(1).status(), "Middle file status should be FINISHED");

            assertEquals("older-recording.jfr", files.get(2).name(), "Third file should be the older one");
            assertEquals(RecordingStatus.FINISHED, files.get(2).status(), "Older file status should be FINISHED");

            assertEquals("oldest-recording.jfr", files.get(3).name(), "Fourth file should be the oldest one");
            assertEquals(RecordingStatus.FINISHED, files.get(3).status(), "Oldest file status should be FINISHED");
        }

        @Test
        @DisplayName("Should set only latest recording file status to UNKNOWN with multiple files")
        void singleSession_withMultipleFiles_shouldSetOnlyLatestFileStatusToUnknown() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create multiple recording files in the session directory
            Path oldestRecordingFile = Files.createFile(sessionPath.resolve("oldest-recording.jfr"));
            Path olderRecordingFile = Files.createFile(sessionPath.resolve("older-recording.jfr"));
            Path middleRecordingFile = Files.createFile(sessionPath.resolve("middle-recording.jfr"));
            Path latestRecordingFile = Files.createFile(sessionPath.resolve("latest-recording.jfr"));

            // Set file modification times with different timestamps
            Instant now = clock.instant();
            Instant oldestTime = now.minus(Duration.ofMinutes(20)); // Oldest file
            Instant olderTime = now.minus(Duration.ofMinutes(15));  // Older file
            Instant middleTime = now.minus(Duration.ofMinutes(10)); // Middle file
            Instant recentTime = now.minus(Duration.ofMinutes(2));  // Latest file within 5-minute finished period

            Files.setLastModifiedTime(oldestRecordingFile, java.nio.file.attribute.FileTime.from(oldestTime));
            Files.setLastModifiedTime(olderRecordingFile, java.nio.file.attribute.FileTime.from(olderTime));
            Files.setLastModifiedTime(middleRecordingFile, java.nio.file.attribute.FileTime.from(middleTime));
            Files.setLastModifiedTime(latestRecordingFile, java.nio.file.attribute.FileTime.from(recentTime));

            // Mock repository info with NO detection file (will use WithoutDetectionFileStrategy)
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            Optional<RecordingSession> result = storage.singleSession(SESSION_ID);

            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(RecordingStatus.UNKNOWN, result.get().status(), "Session status should be UNKNOWN");

            // Verify file statuses
            assertEquals(4, result.get().files().size(), "Should have four files");

            // Files should be sorted by modification time (newest first)
            var files = result.get().files();

            // Latest file should have UNKNOWN status
            assertEquals("latest-recording.jfr", files.getFirst().name(), "First file should be the latest");
            assertEquals(RecordingStatus.UNKNOWN, files.getFirst().status(), "Latest file status should match session status (UNKNOWN)");

            // All other files should have FINISHED status
            assertEquals("middle-recording.jfr", files.get(1).name(), "Second file should be the middle one");
            assertEquals(RecordingStatus.FINISHED, files.get(1).status(), "Middle file status should be FINISHED");

            assertEquals("older-recording.jfr", files.get(2).name(), "Third file should be the older one");
            assertEquals(RecordingStatus.FINISHED, files.get(2).status(), "Older file status should be FINISHED");

            assertEquals("oldest-recording.jfr", files.get(3).name(), "Fourth file should be the oldest one");
            assertEquals(RecordingStatus.FINISHED, files.get(3).status(), "Oldest file status should be FINISHED");
        }

        @Test
        @DisplayName("Should set all files to FINISHED status with multiple files when detection file exists")
        void singleSession_withMultipleFiles_shouldSetAllFilesToFinishedWithDetectionFile() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));

            // Create multiple recording files in the session directory
            Path oldestRecordingFile = Files.createFile(sessionPath.resolve("oldest-recording.jfr"));
            Path olderRecordingFile = Files.createFile(sessionPath.resolve("older-recording.jfr"));
            Path middleRecordingFile = Files.createFile(sessionPath.resolve("middle-recording.jfr"));
            Path latestRecordingFile = Files.createFile(sessionPath.resolve("latest-recording.jfr"));

            // Create the detection file to force FINISHED status
            Files.createFile(sessionPath.resolve(FINISHED_FILE_NAME));

            // Set file modification times with different timestamps
            Instant now = clock.instant();
            Instant oldestTime = now.minus(Duration.ofMinutes(20)); // Oldest file
            Instant olderTime = now.minus(Duration.ofMinutes(15));  // Older file
            Instant middleTime = now.minus(Duration.ofMinutes(10)); // Middle file
            Instant recentTime = now.minus(Duration.ofMinutes(2));  // Latest file within 5-minute finished period

            Files.setLastModifiedTime(oldestRecordingFile, java.nio.file.attribute.FileTime.from(oldestTime));
            Files.setLastModifiedTime(olderRecordingFile, java.nio.file.attribute.FileTime.from(olderTime));
            Files.setLastModifiedTime(middleRecordingFile, java.nio.file.attribute.FileTime.from(middleTime));
            Files.setLastModifiedTime(latestRecordingFile, java.nio.file.attribute.FileTime.from(recentTime));

            // Mock repository info with detection file (will use WithDetectionFileStrategy)
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, FINISHED_FILE_NAME);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));

            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));

            // When
            Optional<RecordingSession> result = storage.singleSession(SESSION_ID);

            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(RecordingStatus.FINISHED, result.get().status(), "Session status should be FINISHED");

            // Verify file statuses
            assertEquals(5, result.get().files().size(), "Should have five files (4 recordings + detection file)");

            // Files should be sorted by modification time (newest first)
            var files = result.get().files();

            // Find all recording files (not the finishing file)
            var recordingFiles = files.stream()
                    .filter(file -> !file.isFinishingFile())
                    .toList();

            assertEquals(4, recordingFiles.size(), "Should have four recording files");

            // All recording files should have FINISHED status
            for (var file : recordingFiles) {
                assertEquals(RecordingStatus.FINISHED, file.status(),
                        "All recording files should have FINISHED status when detection file exists");
            }
        }
    }

    // Helper methods

    private WorkspaceSessionInfo createWorkspaceSessionInfo(String sessionId, String relativePath, Path workspacePath) {
        return createWorkspaceSessionInfo(sessionId, relativePath, workspacePath, Instant.now());
    }

    private WorkspaceSessionInfo createWorkspaceSessionInfo(String sessionId, String relativePath, Path workspacePath, Instant createdAt) {
        return new WorkspaceSessionInfo(
                sessionId,
                sessionId,
                PROJECT_ID,
                "workspace-1",
                "finished.txt",
                Path.of(relativePath),
                workspacePath,
                createdAt,
                createdAt
        );
    }
    
    @Nested
    @DisplayName("AsprofFileInfoProcessor Tests")
    class AsprofFileInfoProcessorTests {
        
        private AsprofFileRemoteRepositoryStorage asprofStorage;
        private static final String FILE_PREFIX = "profile-";
        
        @BeforeEach
        void setUp() {
            Instant fixedTime = Instant.parse("2025-08-13T10:45:00Z");
            clock = Clock.fixed(fixedTime, ZoneOffset.UTC);
            asprofStorage = new AsprofFileRemoteRepositoryStorage(
                    PROJECT_ID,
                    homeDirs,
                    projectRepositoryRepository,
                    workspaceRepository,
                    new AsprofFileInfoProcessor(),
                    FINISHED_PERIOD,
                    clock);
        }
        
        @Test
        @DisplayName("Should extract timestamp from filename with correct pattern")
        void singleSession_withCorrectFilenamePattern_shouldExtractTimestamp() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));
            
            // Create a file with the correct naming pattern: profile-{timestamp}.jfr
            String timestamp = "20250506-074120"; // May 6, 2025, 07:41:20
            Path recordingFile = Files.createFile(sessionPath.resolve(FILE_PREFIX + timestamp + ".jfr"));
            
            // Set file modification time to a different time to verify we're using the filename timestamp
            Instant fileSystemTime = Instant.parse("2025-01-01T00:00:00Z"); // Much earlier than the filename timestamp
            Files.setLastModifiedTime(recordingFile, java.nio.file.attribute.FileTime.from(fileSystemTime));
            
            // Mock repository info
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));
            
            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));
            
            // When
            Optional<RecordingSession> result = asprofStorage.singleSession(SESSION_ID);
            
            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(1, result.get().files().size(), "Should have one file");
            
            RepositoryFile file = result.get().files().getFirst();
            
            // Calculate expected timestamp from the filename
            Instant expectedTimestamp = Instant.parse("2025-05-06T07:41:20Z");
            assertEquals(expectedTimestamp, file.createdAt(), 
                    "File timestamp should be extracted from filename, not filesystem");
            
            // Verify it's not using the filesystem timestamp
            assertNotEquals(fileSystemTime, file.createdAt(), "File timestamp should not match filesystem timestamp");
        }
        
        @Test
        @DisplayName("Should fall back to filesystem timestamp for files with incorrect pattern")
        void singleSession_withIncorrectFilenamePattern_shouldFallbackToFilesystem() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));
            
            // Create a file with an incorrect naming pattern
            Path recordingFile = Files.createFile(sessionPath.resolve("recording.jfr"));
            
            // Set file modification time
            Instant fileSystemTime = Instant.parse("2025-01-01T00:00:00Z");
            Files.setLastModifiedTime(recordingFile, java.nio.file.attribute.FileTime.from(fileSystemTime));
            
            // Mock repository info
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));
            
            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));
            
            // When
            Optional<RecordingSession> result = asprofStorage.singleSession(SESSION_ID);
            
            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(1, result.get().files().size(), "Should have one file");
            
            RepositoryFile file = result.get().files().getFirst();
            assertEquals(fileSystemTime, file.createdAt(), 
                    "File timestamp should fall back to filesystem timestamp");
        }
        
        @Test
        @DisplayName("Should correctly sort files based on extracted timestamps")
        void listSessions_withMultipleFiles_shouldSortByExtractedTimestamp() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));
            
            // Create files with different timestamps in filenames
            // Note: We're creating them in reverse chronological order to ensure sorting is by timestamp, not creation order
            String timestamp1 = "20250508-093000"; // Latest
            String timestamp2 = "20250507-083000"; // Middle
            String timestamp3 = "20250506-074120"; // Oldest
            
            Path file1 = Files.createFile(sessionPath.resolve(FILE_PREFIX + timestamp1 + ".jfr"));
            Path file2 = Files.createFile(sessionPath.resolve(FILE_PREFIX + timestamp2 + ".jfr"));
            Path file3 = Files.createFile(sessionPath.resolve(FILE_PREFIX + timestamp3 + ".jfr"));
            
            // Set all filesystem timestamps to the same time to ensure sorting is by extracted timestamp
            Instant fileSystemTime = Instant.parse("2025-01-01T00:00:00Z");
            Files.setLastModifiedTime(file1, java.nio.file.attribute.FileTime.from(fileSystemTime));
            Files.setLastModifiedTime(file2, java.nio.file.attribute.FileTime.from(fileSystemTime));
            Files.setLastModifiedTime(file3, java.nio.file.attribute.FileTime.from(fileSystemTime));
            
            // Mock repository info
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));
            
            // Mock workspace session
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(SESSION_ID, SESSION_ID, workspacePath);
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));
            
            // When
            Optional<RecordingSession> result = asprofStorage.singleSession(SESSION_ID);
            
            // Then
            assertTrue(result.isPresent(), "Session should be found");
            assertEquals(3, result.get().files().size(), "Should have three files");
            
            // Files should be sorted by modification time (newest first)
            List<RepositoryFile> files = result.get().files();
            
            // Verify the order of files based on their names
            assertEquals(FILE_PREFIX + timestamp1 + ".jfr", files.getFirst().name(), "First file should be the latest by timestamp");
            assertEquals(FILE_PREFIX + timestamp2 + ".jfr", files.get(1).name(), "Second file should be the middle by timestamp");
            assertEquals(FILE_PREFIX + timestamp3 + ".jfr", files.get(2).name(), "Third file should be the oldest by timestamp");
        }
        
        @Test
        @DisplayName("Should correctly handle multiple sessions with timestamp-based files")
        void listSessions_withMultipleSessions_shouldHandleTimestampBasedFiles() throws IOException {
            // Given
            Path workspacePath = tempDir;
            
            // Create multiple session directories
            String session1Id = "session-1";
            String session2Id = "session-2";
            
            Path session1Path = Files.createDirectories(workspacePath.resolve(session1Id));
            Path session2Path = Files.createDirectories(workspacePath.resolve(session2Id));
            
            // Create files with timestamps in filenames for each session
            String timestamp1 = "20250508-093000"; // Latest in session 1
            String timestamp2 = "20250507-083000"; // Oldest in session 1
            String timestamp3 = "20250506-074120"; // Latest in session 2
            String timestamp4 = "20250505-064000"; // Oldest in session 2
            
            Path file1 = Files.createFile(session1Path.resolve(FILE_PREFIX + timestamp1 + ".jfr"));
            Path file2 = Files.createFile(session1Path.resolve(FILE_PREFIX + timestamp2 + ".jfr"));
            Path file3 = Files.createFile(session2Path.resolve(FILE_PREFIX + timestamp3 + ".jfr"));
            Path file4 = Files.createFile(session2Path.resolve(FILE_PREFIX + timestamp4 + ".jfr"));
            
            // Set all filesystem timestamps to the same time
            Instant fileSystemTime = Instant.parse("2025-01-01T00:00:00Z");
            Files.setLastModifiedTime(file1, java.nio.file.attribute.FileTime.from(fileSystemTime));
            Files.setLastModifiedTime(file2, java.nio.file.attribute.FileTime.from(fileSystemTime));
            Files.setLastModifiedTime(file3, java.nio.file.attribute.FileTime.from(fileSystemTime));
            Files.setLastModifiedTime(file4, java.nio.file.attribute.FileTime.from(fileSystemTime));
            
            // Calculate expected timestamps
            Instant expectedTimestamp1 = Instant.parse("2025-05-08T09:30:00Z");
            Instant expectedTimestamp3 = Instant.parse("2025-05-06T07:41:20Z");
            
            // Mock repository info
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));
            
            // Create session infos with different creation times
            Instant now = clock.instant();
            WorkspaceSessionInfo sessionInfo1 = createWorkspaceSessionInfo(
                    session1Id, session1Id, workspacePath, now.minusSeconds(100)); // Newer (latest)
            WorkspaceSessionInfo sessionInfo2 = createWorkspaceSessionInfo(
                    session2Id, session2Id, workspacePath, now.minusSeconds(200)); // Older
            
            // Mock workspace sessions
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo1, sessionInfo2));
            
            // When
            List<RecordingSession> result = asprofStorage.listSessions();
            
            // Then
            assertEquals(2, result.size(), "Should return both sessions");
            
            // Verify sessions are sorted by creation time (newest first)
            assertEquals(session1Id, result.getFirst().id(), "First session should be the newest");
            assertEquals(session2Id, result.get(1).id(), "Second session should be the older one");
            
            // Verify file timestamps in each session
            assertEquals(expectedTimestamp1, result.getFirst().files().getFirst().createdAt(),
                    "First session timestamp should be from the latest file");
            assertEquals(expectedTimestamp3, result.get(1).files().getFirst().createdAt(),
                    "Second session timestamp should be from the latest file");
        }
        
        @Test
        @DisplayName("Should handle mixed file types with timestamp-based JFR files")
        void listSessions_withMixedFileTypes_shouldHandleTimestampBasedJfrFiles() throws IOException {
            // Given
            Path workspacePath = tempDir;
            
            // Create session directory with mixed file types
            String sessionId = "mixed-files-session";
            Path sessionPath = Files.createDirectories(workspacePath.resolve(sessionId));
            
            // Create different types of files
            String timestamp = "20250508-093000";
            Path jfrFile = Files.createFile(sessionPath.resolve(FILE_PREFIX + timestamp + ".jfr"));
            Path heapDumpFile = Files.createFile(sessionPath.resolve("heap-dump.hprof"));
            Path perfCountersFile = Files.createFile(sessionPath.resolve("perf-counters.hsperfdata"));
            
            // Set all filesystem timestamps to the same time
            Instant fileSystemTime = Instant.parse("2025-01-01T00:00:00Z");
            Files.setLastModifiedTime(jfrFile, java.nio.file.attribute.FileTime.from(fileSystemTime));
            Files.setLastModifiedTime(heapDumpFile, java.nio.file.attribute.FileTime.from(fileSystemTime));
            Files.setLastModifiedTime(perfCountersFile, java.nio.file.attribute.FileTime.from(fileSystemTime));
            
            // Calculate expected timestamp for JFR file
            Instant expectedJfrTimestamp = Instant.parse("2025-05-08T09:30:00Z");
            
            // Mock repository info
            DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));
            
            // Create session info
            WorkspaceSessionInfo sessionInfo = createWorkspaceSessionInfo(
                    sessionId, sessionId, workspacePath);
            
            // Mock workspace session
            when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                    .thenReturn(List.of(sessionInfo));
            
            // When
            List<RecordingSession> result = asprofStorage.listSessions();
            
            // Then
            assertEquals(1, result.size(), "Should return one session");
            assertEquals(sessionId, result.getFirst().id(), "Session ID should match");
            
            // Verify files
            assertEquals(3, result.getFirst().files().size(), "Should have three files");
            
            // Verify file types
            var files = result.getFirst().files();
            long jfrCount = files.stream().filter(file -> file.fileType() == SupportedRecordingFile.JFR).count();
            long heapDumpCount = files.stream().filter(file -> file.fileType() == SupportedRecordingFile.HEAP_DUMP).count();
            long perfCountersCount = files.stream().filter(file -> file.fileType() == SupportedRecordingFile.PERF_COUNTERS).count();
            
            assertEquals(1, jfrCount, "Should have one JFR file");
            assertEquals(1, heapDumpCount, "Should have one HEAP_DUMP file");
            assertEquals(1, perfCountersCount, "Should have one PERF_COUNTERS file");
            
            // Verify file names
            var fileNames = files.stream().map(RepositoryFile::name).toList();
            assertTrue(fileNames.contains(FILE_PREFIX + timestamp + ".jfr"), "Should contain JFR file");
            assertTrue(fileNames.contains("heap-dump.hprof"), "Should contain heap dump file");
            assertTrue(fileNames.contains("perf-counters.hsperfdata"), "Should contain perf counters file");
            
            // Verify JFR file timestamp is extracted from filename
            var jfrFile1 = files.stream()
                    .filter(file -> file.fileType() == SupportedRecordingFile.JFR)
                    .findFirst()
                    .orElseThrow();
            
            assertEquals(expectedJfrTimestamp, jfrFile1.createdAt(), 
                    "JFR file timestamp should be extracted from filename");
            
            // Verify other files use filesystem timestamp
            var heapDumpFile1 = files.stream()
                    .filter(file -> file.fileType() == SupportedRecordingFile.HEAP_DUMP)
                    .findFirst()
                    .orElseThrow();
            
            assertEquals(fileSystemTime, heapDumpFile1.createdAt(),
                    "Heap dump file should use filesystem timestamp");
        }
    }
}
