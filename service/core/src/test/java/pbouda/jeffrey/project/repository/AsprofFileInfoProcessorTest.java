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
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
import pbouda.jeffrey.project.repository.file.AsprofFileInfoProcessor;
import pbouda.jeffrey.project.repository.file.FilesystemFileInfoProcessor;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.provider.api.repository.WorkspaceRepository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsprofFileInfoProcessorTest {

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
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String FILE_PREFIX = "profile-";

    @BeforeEach
    void setUp() {
        Instant fixedTime = Instant.parse("2025-08-13T10:45:00Z");
        clock = Clock.fixed(fixedTime, ZoneOffset.UTC);
        
        // Create storage with AsprofFileInfoProcessor instead of FilesystemFileInfoProcessor
        storage = new AsprofFileRemoteRepositoryStorage(
                PROJECT_ID,
                homeDirs,
                projectRepositoryRepository,
                workspaceRepository,
                new AsprofFileInfoProcessor(),
                FINISHED_PERIOD,
                clock);
    }

    @Nested
    @DisplayName("AsprofFileInfoProcessor Timestamp Extraction Tests")
    class TimestampExtractionTests {

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
            Files.setLastModifiedTime(recordingFile, FileTime.from(fileSystemTime));
            
            // Calculate expected timestamp from the filename
            LocalDateTime expectedDateTime = LocalDateTime.parse(timestamp, FORMATTER);
            Instant expectedTimestamp = expectedDateTime.toInstant(ZoneOffset.UTC);
            
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
            assertEquals(1, result.get().files().size(), "Should have one file");
            
            RepositoryFile file = result.get().files().get(0);
            assertEquals(expectedTimestamp, file.createdAt(), 
                    "File timestamp should be extracted from filename, not filesystem");
            
            // Verify it's not using the filesystem timestamp
            assertFalse(fileSystemTime.equals(file.createdAt()), 
                    "File timestamp should not match filesystem timestamp");
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
            Files.setLastModifiedTime(recordingFile, FileTime.from(fileSystemTime));
            
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
            assertEquals(1, result.get().files().size(), "Should have one file");
            
            RepositoryFile file = result.get().files().get(0);
            assertEquals(fileSystemTime, file.createdAt(), 
                    "File timestamp should fall back to filesystem timestamp");
        }
        
        @Test
        @DisplayName("Should handle multiple files with mixed naming patterns")
        void singleSession_withMixedFilenamePatterns_shouldHandleCorrectly() throws IOException {
            // Given
            Path workspacePath = tempDir;
            Path sessionPath = Files.createDirectories(workspacePath.resolve(SESSION_ID));
            
            // Create files with different naming patterns
            String timestamp1 = "20250506-074120"; // May 6, 2025, 07:41:20
            String timestamp2 = "20250507-083000"; // May 7, 2025, 08:30:00
            
            Path correctFile1 = Files.createFile(sessionPath.resolve(FILE_PREFIX + timestamp1 + ".jfr"));
            Path correctFile2 = Files.createFile(sessionPath.resolve(FILE_PREFIX + timestamp2 + ".jfr"));
            Path incorrectFile = Files.createFile(sessionPath.resolve("recording.jfr"));
            
            // Set file modification times
            Instant fileSystemTime = Instant.parse("2025-01-01T00:00:00Z");
            Files.setLastModifiedTime(correctFile1, FileTime.from(fileSystemTime));
            Files.setLastModifiedTime(correctFile2, FileTime.from(fileSystemTime));
            Files.setLastModifiedTime(incorrectFile, FileTime.from(fileSystemTime));
            
            // Calculate expected timestamps
            LocalDateTime expectedDateTime1 = LocalDateTime.parse(timestamp1, FORMATTER);
            Instant expectedTimestamp1 = expectedDateTime1.toInstant(ZoneOffset.UTC);
            
            LocalDateTime expectedDateTime2 = LocalDateTime.parse(timestamp2, FORMATTER);
            Instant expectedTimestamp2 = expectedDateTime2.toInstant(ZoneOffset.UTC);
            
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
            assertEquals(3, result.get().files().size(), "Should have three files");
            
            // Find files by name
            RepositoryFile file1 = findFileByName(result.get().files(), FILE_PREFIX + timestamp1 + ".jfr");
            RepositoryFile file2 = findFileByName(result.get().files(), FILE_PREFIX + timestamp2 + ".jfr");
            RepositoryFile file3 = findFileByName(result.get().files(), "recording.jfr");
            
            // Verify timestamps
            assertEquals(expectedTimestamp1, file1.createdAt(), 
                    "First file timestamp should be extracted from filename");
            assertEquals(expectedTimestamp2, file2.createdAt(), 
                    "Second file timestamp should be extracted from filename");
            assertEquals(fileSystemTime, file3.createdAt(), 
                    "Third file timestamp should fall back to filesystem timestamp");
        }
    }
    
    @Nested
    @DisplayName("AsprofFileInfoProcessor List Sessions Tests")
    class ListSessionsTests {
        
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
            Files.setLastModifiedTime(file1, FileTime.from(fileSystemTime));
            Files.setLastModifiedTime(file2, FileTime.from(fileSystemTime));
            Files.setLastModifiedTime(file3, FileTime.from(fileSystemTime));
            
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
            assertEquals(3, result.get().files().size(), "Should have three files");
            
            // Files should be sorted by name (which is the default comparator in AsprofFileInfoProcessor)
            List<RepositoryFile> files = result.get().files();
            assertEquals(FILE_PREFIX + timestamp1 + ".jfr", files.get(0).name(), "First file should be the latest by filename");
            assertEquals(FILE_PREFIX + timestamp2 + ".jfr", files.get(1).name(), "Second file should be the middle by filename");
            assertEquals(FILE_PREFIX + timestamp3 + ".jfr", files.get(2).name(), "Third file should be the oldest by filename");
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
            Files.setLastModifiedTime(file1, FileTime.from(fileSystemTime));
            Files.setLastModifiedTime(file2, FileTime.from(fileSystemTime));
            Files.setLastModifiedTime(file3, FileTime.from(fileSystemTime));
            Files.setLastModifiedTime(file4, FileTime.from(fileSystemTime));
            
            // Calculate expected timestamps
            LocalDateTime expectedDateTime1 = LocalDateTime.parse(timestamp1, FORMATTER);
            Instant expectedTimestamp1 = expectedDateTime1.toInstant(ZoneOffset.UTC);
            
            LocalDateTime expectedDateTime3 = LocalDateTime.parse(timestamp3, FORMATTER);
            Instant expectedTimestamp3 = expectedDateTime3.toInstant(ZoneOffset.UTC);
            
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
            List<RecordingSession> result = storage.listSessions();
            
            // Then
            assertEquals(2, result.size(), "Should return both sessions");
            
            // Verify sessions are sorted by creation time (newest first)
            assertEquals(session1Id, result.get(0).id(), "First session should be the newest");
            assertEquals(session2Id, result.get(1).id(), "Second session should be the older one");
            
            // Verify file timestamps in each session
            assertEquals(expectedTimestamp1, result.get(0).files().getFirst().createdAt(),
                    "First session timestamp should be from the latest file");
            assertEquals(expectedTimestamp3, result.get(1).files().getFirst().createdAt(),
                    "Second session timestamp should be from the latest file");
        }
    }

    // Helper methods
    
    private WorkspaceSessionInfo createWorkspaceSessionInfo(String sessionId, String relativePath, Path workspacePath) {
        return createWorkspaceSessionInfo(sessionId, relativePath, workspacePath, Instant.now());
    }
    
    private WorkspaceSessionInfo createWorkspaceSessionInfo(
            String sessionId, String relativePath, Path workspacePath, Instant createdAt) {
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
    
    private RepositoryFile findFileByName(List<RepositoryFile> files, String name) {
        return files.stream()
                .filter(file -> file.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("File not found: " + name));
    }
}
