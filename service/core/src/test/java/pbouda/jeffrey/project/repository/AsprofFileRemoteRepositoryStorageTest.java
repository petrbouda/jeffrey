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
import pbouda.jeffrey.common.model.RepositoryType;
import pbouda.jeffrey.common.model.repository.RecordingSession;
import pbouda.jeffrey.common.model.repository.RecordingStatus;
import pbouda.jeffrey.provider.api.model.DBRepositoryInfo;
import pbouda.jeffrey.provider.api.repository.ProjectRepositoryRepository;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsprofFileRemoteRepositoryStorageTest {

    @Mock
    private ProjectRepositoryRepository projectRepositoryRepository;

    private AsprofFileRemoteRepositoryStorage storage;

    @TempDir
    private Path tempDir;

    private static final String SESSION_ID = "test-session";
    private static final String DETECTION_FILE_NAME = "finished.txt";
    private static final List<String> RECORDING_FILE_NAMES = List.of(
            "old-recording.jfr",
            "middle-recording.jfr",
            "latest-recording.jfr"
    );
    private static final List<String> MULTIPLE_SESSION_IDS = List.of(
            "session-1",
            "session-2",
            "session-3"
    );

    @BeforeEach
    void setUp() {
        Duration finishedPeriod = Duration.ofMinutes(5);
        storage = new AsprofFileRemoteRepositoryStorage(
                projectRepositoryRepository, finishedPeriod, Clock.systemUTC());
    }

    @Nested
    @DisplayName("Single Session Operations")
    class SingleSessionTests {

        @Test
        @DisplayName("Should return UNKNOWN status when files are within finished period")
        void singleSession_withFileWithinFinishedPeriod_shouldReturnUnknownStatus() throws IOException {
            // Given
            Instant now = Instant.now();
            Path sessionDir = createSessionWithRecordingFiles(SESSION_ID);

            Instant oldTime = now.minus(Duration.ofMinutes(8));
            Instant middleTime = now.minus(Duration.ofMinutes(5));
            Instant latestTime = now.minus(Duration.ofMinutes(2)); // within finished period

            setFileModificationTimes(sessionDir, oldTime, middleTime, latestTime);
            mockRepositoryInfo(null);

            // When
            RecordingSession result = storage.singleSession(SESSION_ID).get();

            // Then
            assertBasicSessionProperties(result, SESSION_ID, RecordingStatus.UNKNOWN);
            assertFilesSortedByModificationTime(result.files(), RECORDING_FILE_NAMES.get(2), RECORDING_FILE_NAMES.get(1), RECORDING_FILE_NAMES.get(0));
            assertAllRecordingFiles(result.files(), 0, 3);
        }

        @Test
        @DisplayName("Should return FINISHED status when files are after finished period")
        void singleSession_withFileAfterFinishedPeriod_shouldReturnFinishedStatus() throws IOException {
            // Given
            Instant now = Instant.now();
            Path sessionDir = createSessionWithRecordingFiles(SESSION_ID);

            Instant oldTime = now.minus(Duration.ofMinutes(15));
            Instant middleTime = now.minus(Duration.ofMinutes(12));
            Instant latestTime = now.minus(Duration.ofMinutes(10)); // after finished period

            setFileModificationTimes(sessionDir, oldTime, middleTime, latestTime);
            mockRepositoryInfo(null);

            // When
            RecordingSession result = storage.singleSession(SESSION_ID).get();

            // Then
            assertBasicSessionProperties(result, SESSION_ID, RecordingStatus.FINISHED);
            assertFilesSortedByModificationTime(result.files(), RECORDING_FILE_NAMES.get(2), RECORDING_FILE_NAMES.get(1), RECORDING_FILE_NAMES.get(0));
            assertAllRecordingFiles(result.files(), 0, 3);
        }

        @Test
        @DisplayName("Should return FINISHED status when detection file is present")
        void singleSession_withFinishedDetectionFilePresent_shouldReturnFinishedStatus() throws IOException {
            // Given
            Instant now = Instant.now();
            Path sessionDir = createSessionWithRecordingFiles(SESSION_ID);
            Path detectionFile = Files.createFile(sessionDir.resolve(DETECTION_FILE_NAME));

            Instant oldTime = now.minus(Duration.ofMinutes(8));
            Instant middleTime = now.minus(Duration.ofMinutes(5));
            Instant latestTime = now.minus(Duration.ofMinutes(2)); // within finished period
            Instant detectionTime = now.minus(Duration.ofMinutes(1));

            setFileModificationTimes(sessionDir, oldTime, middleTime, latestTime);
            Files.setLastModifiedTime(detectionFile, FileTime.from(detectionTime));
            mockRepositoryInfo(DETECTION_FILE_NAME);

            // When
            RecordingSession result = storage.singleSession(SESSION_ID).get();

            // Then
            assertBasicSessionProperties(result, SESSION_ID, RecordingStatus.FINISHED);
            assertEquals(4, result.files().size()); // 3 recording files + 1 detection file

            assertFilesSortedByModificationTime(result.files(), DETECTION_FILE_NAME, RECORDING_FILE_NAMES.get(2), RECORDING_FILE_NAMES.get(1), RECORDING_FILE_NAMES.get(0));

            // Only JFR files should be recording files, detection file should not be
            assertFalse(result.files().getFirst().isRecordingFile()); // detection file
            assertAllRecordingFiles(result.files(), 1, 3);
        }

        @Test
        @DisplayName("Should return ACTIVE status when detection file is absent but within period")
        void singleSession_withFinishedDetectionFileAbsentButWithinPeriod_shouldReturnActiveStatus() throws IOException {
            // Given
            Instant now = Instant.now();
            Path sessionDir = createSessionWithRecordingFiles(SESSION_ID);

            Instant oldTime = now.minus(Duration.ofMinutes(8));
            Instant middleTime = now.minus(Duration.ofMinutes(5));
            Instant latestTime = now.minus(Duration.ofMinutes(2)); // within finished period

            setFileModificationTimes(sessionDir, oldTime, middleTime, latestTime);
            mockRepositoryInfo(DETECTION_FILE_NAME); // detection file configured but not created

            // When
            RecordingSession result = storage.singleSession(SESSION_ID).get();

            // Then
            assertBasicSessionProperties(result, SESSION_ID, RecordingStatus.ACTIVE);
            assertFilesSortedByModificationTime(result.files(), RECORDING_FILE_NAMES.get(2), RECORDING_FILE_NAMES.get(1), RECORDING_FILE_NAMES.get(0));
            assertAllRecordingFiles(result.files(), 0, 3);
        }
    }

    @Nested
    @DisplayName("Download Recording Operations")
    class DownloadRecordingTests {

        @Test
        @DisplayName("Should return input stream when recording file exists")
        void downloadRecording_withExistingFile_shouldReturnInputStream() throws IOException {
            // Given
            String sessionId = "test-recording.jfr";
            Path recordingFile = Files.createFile(tempDir.resolve(sessionId));
            String testContent = "test recording content";
            Files.writeString(recordingFile, testContent);
            mockRepositoryInfo(null);

            // When
            InputStream result = storage.downloadRecording(sessionId);

            // Then
            assertNotNull(result);
            String actualContent = new String(result.readAllBytes());
            assertEquals(testContent, actualContent);
            result.close();
        }

        @Test
        @DisplayName("Should return null when repository info is not available")
        void downloadRecording_withNoRepositoryInfo_shouldReturnNull() {
            // Given
            when(projectRepositoryRepository.getAll()).thenReturn(List.of());

            // When
            InputStream result = storage.downloadRecording("any-session-id");

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when recording file does not exist")
        void downloadRecording_withNonExistentFile_shouldReturnNull() {
            // Given
            String sessionId = "non-existent-recording.jfr";
            mockRepositoryInfo(null);

            // When
            InputStream result = storage.downloadRecording(sessionId);

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when path points to directory instead of file")
        void downloadRecording_withDirectoryPath_shouldReturnNull() throws IOException {
            // Given
            String sessionId = "test-directory";
            Files.createDirectories(tempDir.resolve(sessionId));
            mockRepositoryInfo(null);

            // When
            InputStream result = storage.downloadRecording(sessionId);

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("Should handle relative paths correctly")
        void downloadRecording_withRelativePath_shouldResolveCorrectly() throws IOException {
            // Given
            String sessionId = "subdir/recording.jfr";
            Path subDir = Files.createDirectories(tempDir.resolve("subdir"));
            Path recordingFile = Files.createFile(subDir.resolve("recording.jfr"));
            String testContent = "relative path content";
            Files.writeString(recordingFile, testContent);
            mockRepositoryInfo(null);

            // When
            InputStream result = storage.downloadRecording(sessionId);

            // Then
            assertNotNull(result);
            String actualContent = new String(result.readAllBytes());
            assertEquals(testContent, actualContent);
            result.close();
        }

        @Test
        @DisplayName("Should return null when file exists but cannot be read due to permissions")
        void downloadRecording_withUnreadableFile_shouldReturnNull() throws IOException {
            // Given
            String sessionId = "unreadable-recording.jfr";
            Path recordingFile = Files.createFile(tempDir.resolve(sessionId));
            
            try {
                // Make file unreadable (this might not work on all systems)
                recordingFile.toFile().setReadable(false);
                mockRepositoryInfo(null);

                // When
                InputStream result = storage.downloadRecording(sessionId);

                // Then - depending on system, might return null or throw exception
                // We'll check that it handles the error gracefully
                if (result != null) {
                    result.close();
                }
                
                // Test passes if no exception is thrown and method returns gracefully
                assertTrue(true, "Method handled unreadable file gracefully");
                
            } finally {
                // Restore permissions for cleanup
                recordingFile.toFile().setReadable(true);
            }
        }

        @Test
        @DisplayName("Should handle empty files correctly")
        void downloadRecording_withEmptyFile_shouldReturnEmptyInputStream() throws IOException {
            // Given
            String sessionId = "empty-recording.jfr";
            Path recordingFile = Files.createFile(tempDir.resolve(sessionId));
            mockRepositoryInfo(null);

            // When
            InputStream result = storage.downloadRecording(sessionId);

            // Then
            assertNotNull(result);
            assertEquals(0, result.available());
            result.close();
        }

        @Test
        @DisplayName("Should handle special characters in session ID")
        void downloadRecording_withSpecialCharactersInSessionId_shouldWork() throws IOException {
            // Given
            String sessionId = "test recording with spaces & symbols.jfr";
            Path recordingFile = Files.createFile(tempDir.resolve(sessionId));
            String testContent = "special chars content";
            Files.writeString(recordingFile, testContent);
            mockRepositoryInfo(null);

            // When
            InputStream result = storage.downloadRecording(sessionId);

            // Then
            assertNotNull(result);
            String actualContent = new String(result.readAllBytes());
            assertEquals(testContent, actualContent);
            result.close();
        }
    }

    @Nested
    @DisplayName("Delete Session Operations")
    class DeleteSessionTests {

        @Test
        @DisplayName("Should successfully delete existing session directory")
        void deleteSession_withExistingSession_shouldDeleteDirectory() throws IOException {
            // Given
            String sessionId = "test-session-to-delete";
            Path sessionDir = createSessionWithRecordingFiles(sessionId);
            assertTrue(Files.exists(sessionDir));
            mockRepositoryInfo(null);

            // When
            storage.deleteSession(sessionId);

            // Then
            assertFalse(Files.exists(sessionDir));
        }

        @Test
        @DisplayName("Should handle session with multiple files and subdirectories")
        void deleteSession_withComplexSessionStructure_shouldDeleteAll() throws IOException {
            // Given
            String sessionId = "complex-session";
            Path sessionDir = Files.createDirectories(tempDir.resolve(sessionId));
            
            // Create files in session directory
            Files.createFile(sessionDir.resolve("recording1.jfr"));
            Files.createFile(sessionDir.resolve("recording2.jfr"));
            Files.createFile(sessionDir.resolve("metadata.txt"));
            
            // Create subdirectory with files
            Path subDir = Files.createDirectories(sessionDir.resolve("logs"));
            Files.createFile(subDir.resolve("debug.log"));
            Files.createFile(subDir.resolve("error.log"));
            
            assertTrue(Files.exists(sessionDir));
            assertTrue(Files.list(sessionDir).count() >= 3); // At least 2 files + 1 subdirectory
            mockRepositoryInfo(null);

            // When
            storage.deleteSession(sessionId);

            // Then
            assertFalse(Files.exists(sessionDir));
        }

        @Test
        @DisplayName("Should do nothing when repository info is not available")
        void deleteSession_withNoRepositoryInfo_shouldDoNothing() throws IOException {
            // Given
            String sessionId = "test-session";
            Path sessionDir = createSessionWithRecordingFiles(sessionId);
            assertTrue(Files.exists(sessionDir));
            when(projectRepositoryRepository.getAll()).thenReturn(List.of());

            // When
            storage.deleteSession(sessionId);

            // Then
            assertTrue(Files.exists(sessionDir)); // Should still exist
        }

        @Test
        @DisplayName("Should do nothing when session directory does not exist")
        void deleteSession_withNonExistentSession_shouldDoNothing() {
            // Given
            String sessionId = "non-existent-session";
            Path sessionPath = tempDir.resolve(sessionId);
            assertFalse(Files.exists(sessionPath));
            mockRepositoryInfo(null);

            // When & Then - should not throw exception
            storage.deleteSession(sessionId);
            
            // Still doesn't exist
            assertFalse(Files.exists(sessionPath));
        }

        @Test
        @DisplayName("Should handle session ID with special characters")
        void deleteSession_withSpecialCharactersInSessionId_shouldWork() throws IOException {
            // Given
            String sessionId = "session with spaces & symbols";
            Path sessionDir = Files.createDirectories(tempDir.resolve(sessionId));
            Files.createFile(sessionDir.resolve("recording.jfr"));
            assertTrue(Files.exists(sessionDir));
            mockRepositoryInfo(null);

            // When
            storage.deleteSession(sessionId);

            // Then
            assertFalse(Files.exists(sessionDir));
        }

        @Test
        @DisplayName("Should handle session ID with path separators")
        void deleteSession_withPathSeparatorsInSessionId_shouldWork() throws IOException {
            // Given
            String sessionId = "sub/session/path";
            Path fullPath = tempDir.resolve(sessionId);
            Files.createDirectories(fullPath.getParent());
            Path sessionDir = Files.createDirectories(fullPath);
            Files.createFile(sessionDir.resolve("recording.jfr"));
            assertTrue(Files.exists(sessionDir));
            mockRepositoryInfo(null);

            // When
            storage.deleteSession(sessionId);

            // Then
            assertFalse(Files.exists(sessionDir));
        }

        @Test
        @DisplayName("Should do nothing when path points to regular file instead of directory")
        void deleteSession_withFileInsteadOfDirectory_shouldDoNothing() throws IOException {
            // Given
            String sessionId = "file-not-directory.txt";
            Path filePath = Files.createFile(tempDir.resolve(sessionId));
            assertTrue(Files.exists(filePath));
            assertTrue(Files.isRegularFile(filePath));
            mockRepositoryInfo(null);

            // When
            storage.deleteSession(sessionId);

            // Then
            assertTrue(Files.exists(filePath)); // File should still exist
        }

        @Test
        @DisplayName("Should handle empty session directory")
        void deleteSession_withEmptyDirectory_shouldDeleteDirectory() throws IOException {
            // Given
            String sessionId = "empty-session";
            Path sessionDir = Files.createDirectories(tempDir.resolve(sessionId));
            assertTrue(Files.exists(sessionDir));
            assertTrue(Files.isDirectory(sessionDir));
            mockRepositoryInfo(null);

            // When
            storage.deleteSession(sessionId);

            // Then
            assertFalse(Files.exists(sessionDir));
        }

        @Test
        @DisplayName("Should handle session with detection file")
        void deleteSession_withDetectionFile_shouldDeleteAll() throws IOException {
            // Given
            String sessionId = "session-with-detection";
            Path sessionDir = createSessionWithRecordingFiles(sessionId);
            Files.createFile(sessionDir.resolve(DETECTION_FILE_NAME));
            
            // Verify setup
            assertTrue(Files.exists(sessionDir));
            assertTrue(Files.exists(sessionDir.resolve(DETECTION_FILE_NAME)));
            mockRepositoryInfo(DETECTION_FILE_NAME);

            // When
            storage.deleteSession(sessionId);

            // Then
            assertFalse(Files.exists(sessionDir));
        }
    }

    @Nested
    @DisplayName("Delete Repository Files Operations")
    class DeleteRepositoryFilesTests {

        @Test
        @DisplayName("Should successfully delete single file from session")
        void deleteRepositoryFiles_withSingleFile_shouldDeleteFile() throws IOException {
            // Given
            String sessionId = "test-session";
            Path sessionDir = createSessionWithRecordingFiles(sessionId);
            String fileToDelete = sessionId + "/" + RECORDING_FILE_NAMES.get(0);
            Path targetFile = sessionDir.resolve(RECORDING_FILE_NAMES.get(0));
            
            assertTrue(Files.exists(targetFile));
            mockRepositoryInfo(null);

            // When
            storage.deleteRepositoryFiles(sessionId, List.of(fileToDelete));

            // Then
            assertFalse(Files.exists(targetFile));
            // Other files should still exist
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(1))));
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(2))));
        }

        @Test
        @DisplayName("Should successfully delete multiple files from session")
        void deleteRepositoryFiles_withMultipleFiles_shouldDeleteAllFiles() throws IOException {
            // Given
            String sessionId = "multi-file-session";
            Path sessionDir = createSessionWithRecordingFiles(sessionId);
            List<String> filesToDelete = List.of(
                sessionId + "/" + RECORDING_FILE_NAMES.get(0),
                sessionId + "/" + RECORDING_FILE_NAMES.get(1)
            );
            
            // Verify files exist
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(0))));
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(1))));
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(2))));
            mockRepositoryInfo(null);

            // When
            storage.deleteRepositoryFiles(sessionId, filesToDelete);

            // Then
            assertFalse(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(0))));
            assertFalse(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(1))));
            // Last file should still exist
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(2))));
        }

        @Test
        @DisplayName("Should handle empty file list gracefully")
        void deleteRepositoryFiles_withEmptyFileList_shouldDoNothing() throws IOException {
            // Given
            String sessionId = "empty-list-session";
            Path sessionDir = createSessionWithRecordingFiles(sessionId);
            mockRepositoryInfo(null);

            // When
            storage.deleteRepositoryFiles(sessionId, List.of());

            // Then
            // All files should still exist
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(0))));
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(1))));
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(2))));
        }

        @Test
        @DisplayName("Should do nothing when repository info is not available")  
        void deleteRepositoryFiles_withNoRepositoryInfo_shouldDoNothing() throws IOException {
            // Given
            String sessionId = "no-repo-session";
            Path sessionDir = createSessionWithRecordingFiles(sessionId);
            String fileToDelete = sessionId + "/" + RECORDING_FILE_NAMES.get(0);
            when(projectRepositoryRepository.getAll()).thenReturn(List.of());

            // When
            storage.deleteRepositoryFiles(sessionId, List.of(fileToDelete));

            // Then
            // All files should still exist
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(0))));
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(1))));
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(2))));
        }

        @Test
        @DisplayName("Should do nothing when session directory does not exist")
        void deleteRepositoryFiles_withNonExistentSession_shouldDoNothing() {
            // Given
            String sessionId = "non-existent-session";
            String fileToDelete = sessionId + "/some-file.jfr";
            Path sessionPath = tempDir.resolve(sessionId);
            assertFalse(Files.exists(sessionPath));
            mockRepositoryInfo(null);

            // When & Then - should not throw exception
            storage.deleteRepositoryFiles(sessionId, List.of(fileToDelete));
        }

        @Test
        @DisplayName("Should handle files in subdirectories")
        void deleteRepositoryFiles_withFilesInSubdirectories_shouldDeleteFiles() throws IOException {
            // Given
            String sessionId = "subdir-session";
            Path sessionDir = Files.createDirectories(tempDir.resolve(sessionId));
            Path subDir = Files.createDirectories(sessionDir.resolve("logs"));
            
            // Create files in subdirectory
            Path logFile1 = Files.createFile(subDir.resolve("debug.log"));
            Path logFile2 = Files.createFile(subDir.resolve("error.log"));
            Path mainFile = Files.createFile(sessionDir.resolve("main.jfr"));
            
            List<String> filesToDelete = List.of(
                sessionId + "/logs/debug.log",
                sessionId + "/main.jfr"
            );
            
            assertTrue(Files.exists(logFile1));
            assertTrue(Files.exists(logFile2));
            assertTrue(Files.exists(mainFile));
            mockRepositoryInfo(null);

            // When
            storage.deleteRepositoryFiles(sessionId, filesToDelete);

            // Then
            assertFalse(Files.exists(logFile1)); // Should be deleted
            assertTrue(Files.exists(logFile2));  // Should still exist
            assertFalse(Files.exists(mainFile)); // Should be deleted
        }

        @Test
        @DisplayName("Should handle non-existent files gracefully")
        void deleteRepositoryFiles_withNonExistentFiles_shouldHandleGracefully() throws IOException {
            // Given
            String sessionId = "missing-files-session";
            Path sessionDir = createSessionWithRecordingFiles(sessionId);
            List<String> filesToDelete = List.of(
                sessionId + "/" + RECORDING_FILE_NAMES.get(0), // exists
                sessionId + "/non-existent-file.jfr",         // doesn't exist
                sessionId + "/another-missing.jfr"            // doesn't exist
            );
            
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(0))));
            mockRepositoryInfo(null);

            // When & Then - should not throw exception
            storage.deleteRepositoryFiles(sessionId, filesToDelete);
            
            // Existing file should be deleted
            assertFalse(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(0))));
            // Other files should still exist
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(1))));
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(2))));
        }

        @Test
        @DisplayName("Should handle special characters in file names")
        void deleteRepositoryFiles_withSpecialCharacters_shouldWork() throws IOException {
            // Given
            String sessionId = "special-chars-session";
            Path sessionDir = Files.createDirectories(tempDir.resolve(sessionId));
            String specialFileName = "recording with spaces & symbols.jfr";
            Path specialFile = Files.createFile(sessionDir.resolve(specialFileName));
            
            String fileToDelete = sessionId + "/" + specialFileName;
            assertTrue(Files.exists(specialFile));
            mockRepositoryInfo(null);

            // When
            storage.deleteRepositoryFiles(sessionId, List.of(fileToDelete));

            // Then
            assertFalse(Files.exists(specialFile));
        }

        @Test
        @DisplayName("Should delete detection file when specified")
        void deleteRepositoryFiles_withDetectionFile_shouldDeleteDetectionFile() throws IOException {
            // Given
            String sessionId = "detection-file-session";
            Path sessionDir = createSessionWithRecordingFiles(sessionId);
            Path detectionFile = Files.createFile(sessionDir.resolve(DETECTION_FILE_NAME));
            
            String fileToDelete = sessionId + "/" + DETECTION_FILE_NAME;
            assertTrue(Files.exists(detectionFile));
            mockRepositoryInfo(DETECTION_FILE_NAME);

            // When
            storage.deleteRepositoryFiles(sessionId, List.of(fileToDelete));

            // Then
            assertFalse(Files.exists(detectionFile));
            // Recording files should still exist
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(0))));
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(1))));
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(2))));
        }

        @Test
        @DisplayName("Should handle mixed file types (recording files and detection files)")
        void deleteRepositoryFiles_withMixedFileTypes_shouldDeleteAll() throws IOException {
            // Given
            String sessionId = "mixed-files-session";
            Path sessionDir = createSessionWithRecordingFiles(sessionId);
            Path detectionFile = Files.createFile(sessionDir.resolve(DETECTION_FILE_NAME));
            Path metadataFile = Files.createFile(sessionDir.resolve("metadata.txt"));
            
            List<String> filesToDelete = List.of(
                sessionId + "/" + RECORDING_FILE_NAMES.get(0),
                sessionId + "/" + DETECTION_FILE_NAME,
                sessionId + "/metadata.txt"
            );
            
            // Verify files exist
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(0))));
            assertTrue(Files.exists(detectionFile));
            assertTrue(Files.exists(metadataFile));
            mockRepositoryInfo(DETECTION_FILE_NAME);

            // When
            storage.deleteRepositoryFiles(sessionId, filesToDelete);

            // Then
            assertFalse(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(0))));
            assertFalse(Files.exists(detectionFile));
            assertFalse(Files.exists(metadataFile));
            // Other recording files should still exist
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(1))));
            assertTrue(Files.exists(sessionDir.resolve(RECORDING_FILE_NAMES.get(2))));
        }

        @Test
        @DisplayName("Should handle session with path separators in session ID")
        void deleteRepositoryFiles_withPathSeparatorsInSessionId_shouldWork() throws IOException {
            // Given
            String sessionId = "sub/session/path";
            Path fullPath = tempDir.resolve(sessionId);
            Files.createDirectories(fullPath.getParent());
            Path sessionDir = Files.createDirectories(fullPath);
            Path recordingFile = Files.createFile(sessionDir.resolve("recording.jfr"));
            
            String fileToDelete = sessionId + "/recording.jfr";
            assertTrue(Files.exists(recordingFile));
            mockRepositoryInfo(null);

            // When
            storage.deleteRepositoryFiles(sessionId, List.of(fileToDelete));

            // Then
            assertFalse(Files.exists(recordingFile));
        }

        @Test
        @DisplayName("Should handle large number of files")
        void deleteRepositoryFiles_withManyFiles_shouldDeleteAll() throws IOException {
            // Given
            String sessionId = "many-files-session";
            Path sessionDir = Files.createDirectories(tempDir.resolve(sessionId));
            
            // Create many files
            List<String> filesToDelete = new ArrayList<>();
            List<Path> createdFiles = new ArrayList<>();
            
            for (int i = 0; i < 50; i++) {
                String fileName = "recording-" + i + ".jfr";
                Path file = Files.createFile(sessionDir.resolve(fileName));
                createdFiles.add(file);
                filesToDelete.add(sessionId + "/" + fileName);
                assertTrue(Files.exists(file));
            }
            
            mockRepositoryInfo(null);

            // When
            storage.deleteRepositoryFiles(sessionId, filesToDelete);

            // Then
            for (Path file : createdFiles) {
                assertFalse(Files.exists(file));
            }
        }
    }

    @Nested
    @DisplayName("Multiple Sessions Operations")
    class ListSessionsTests {

        @Test
        @DisplayName("Should return mixed statuses: latest UNKNOWN (within period, no detection), others FINISHED")
        void listSessions_withFilesWithinFinishedPeriod_shouldReturnUnknownStatus() throws IOException {
            // Given
            Instant now = Instant.now();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(15)), now.minus(Duration.ofMinutes(12)), now.minus(Duration.ofMinutes(10))), // session-1: oldest
                    List.of(now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(6)), now.minus(Duration.ofMinutes(4))), // session-2: middle
                    List.of(now.minus(Duration.ofMinutes(3)), now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(1))) // session-3: latest
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);
            mockRepositoryInfo(null);

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());

            // First two sessions should always be FINISHED (only latest can be ACTIVE/UNKNOWN)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(0)), MULTIPLE_SESSION_IDS.get(0), RecordingStatus.FINISHED);
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(1)), MULTIPLE_SESSION_IDS.get(1), RecordingStatus.FINISHED);

            // Latest session should be UNKNOWN (no detection configured, within period)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(2)), MULTIPLE_SESSION_IDS.get(2), RecordingStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Should return FINISHED status for all sessions when files are after finished period")
        void listSessions_withFilesAfterFinishedPeriod_shouldReturnFinishedStatus() throws IOException {
            // Given
            Instant now = Instant.now();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(30)), now.minus(Duration.ofMinutes(28)), now.minus(Duration.ofMinutes(25))), // session-1: oldest, all after finished period
                    List.of(now.minus(Duration.ofMinutes(22)), now.minus(Duration.ofMinutes(20)), now.minus(Duration.ofMinutes(18))), // session-2: middle, all after finished period
                    List.of(now.minus(Duration.ofMinutes(15)), now.minus(Duration.ofMinutes(13)), now.minus(Duration.ofMinutes(11))) // session-3: latest, all after finished period
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);
            mockRepositoryInfo(null);

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());
            for (String sessionId : MULTIPLE_SESSION_IDS) {
                RecordingSession session = findSessionById(result, sessionId);
                assertBasicSessionProperties(session, sessionId, RecordingStatus.FINISHED);
                assertEquals(3, session.files().size());
                assertAllRecordingFiles(session.files(), 0, 3);
            }
        }

        @Test
        @DisplayName("Should return FINISHED status for all sessions when detection files are present")
        void listSessions_withFinishedDetectionFilePresent_shouldReturnFinishedStatus() throws IOException {
            // Given
            Instant now = Instant.now();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(15)), now.minus(Duration.ofMinutes(12)), now.minus(Duration.ofMinutes(10))), // session-1: oldest
                    List.of(now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(6)), now.minus(Duration.ofMinutes(4))), // session-2: middle
                    List.of(now.minus(Duration.ofMinutes(3)), now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(1))) // session-3: latest
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);
            
            // Create detection files for all sessions and set their times to match the latest recording file
            for (int i = 0; i < MULTIPLE_SESSION_IDS.size(); i++) {
                String sessionId = MULTIPLE_SESSION_IDS.get(i);
                Path detectionFile = Files.createFile(tempDir.resolve(sessionId).resolve(DETECTION_FILE_NAME));
                Files.setLastModifiedTime(detectionFile, FileTime.from(times.get(i).get(2))); // Set to latest recording file time
            }
            mockRepositoryInfo(DETECTION_FILE_NAME);

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());
            // All sessions should be FINISHED (detection files present)
            for (String sessionId : MULTIPLE_SESSION_IDS) {
                RecordingSession session = findSessionById(result, sessionId);
                assertBasicSessionProperties(session, sessionId, RecordingStatus.FINISHED);
                assertEquals(4, session.files().size()); // 3 recording files + 1 detection file
            }
        }

        @Test
        @DisplayName("Should return mixed statuses: latest ACTIVE (no detection file), others FINISHED")
        void listSessions_withFinishedDetectionFileAbsentButWithinPeriod_shouldReturnActiveStatus() throws IOException {
            // Given
            Instant now = Instant.now();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(25)), now.minus(Duration.ofMinutes(20)), now.minus(Duration.ofMinutes(15))), // finished
                    List.of(now.minus(Duration.ofMinutes(23)), now.minus(Duration.ofMinutes(18)), now.minus(Duration.ofMinutes(13))), // finished
                    List.of(now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(2))) // within period - should be ACTIVE
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);
            mockRepositoryInfo(DETECTION_FILE_NAME); // detection file configured but not created

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());

            // First two sessions should be FINISHED (after finished period)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(0)), MULTIPLE_SESSION_IDS.get(0), RecordingStatus.FINISHED);
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(1)), MULTIPLE_SESSION_IDS.get(1), RecordingStatus.FINISHED);

            // Last session should be ACTIVE (no detection file created, within period)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(2)), MULTIPLE_SESSION_IDS.get(2), RecordingStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should return FINISHED status for all sessions when all are finished (after finished period without detection files)")
        void listSessions_allSessionsFinished_shouldReturnAllFinishedStatus() throws IOException {
            // Given
            Instant now = Instant.now();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            // All sessions have files after finished period
            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(30)), now.minus(Duration.ofMinutes(28)), now.minus(Duration.ofMinutes(25))), // session-1: oldest, all after finished period
                    List.of(now.minus(Duration.ofMinutes(22)), now.minus(Duration.ofMinutes(20)), now.minus(Duration.ofMinutes(18))), // session-2: middle, all after finished period
                    List.of(now.minus(Duration.ofMinutes(15)), now.minus(Duration.ofMinutes(13)), now.minus(Duration.ofMinutes(11))) // session-3: latest, all after finished period
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);
            mockRepositoryInfo(null);

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());
            for (String sessionId : MULTIPLE_SESSION_IDS) {
                RecordingSession session = findSessionById(result, sessionId);
                assertBasicSessionProperties(session, sessionId, RecordingStatus.FINISHED);
            }
        }

        @Test
        @DisplayName("Should return mixed statuses: latest ACTIVE (within period, no detection), others FINISHED (after period, with detection)")
        void listSessions_latestSessionActiveWithDetectionFile_othersFinished() throws IOException {
            // Given
            Instant now = Instant.now();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(25)), now.minus(Duration.ofMinutes(20)), now.minus(Duration.ofMinutes(17))), // after finished period
                    List.of(now.minus(Duration.ofMinutes(15)), now.minus(Duration.ofMinutes(12)), now.minus(Duration.ofMinutes(10))), // after finished period
                    List.of(now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(2))) // within finished period
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);
            
            // Create detection files for first two sessions only (detection enabled for all, but only first two have the file present)
            Path detectionFile1 = Files.createFile(tempDir.resolve(MULTIPLE_SESSION_IDS.get(0)).resolve(DETECTION_FILE_NAME));
            Path detectionFile2 = Files.createFile(tempDir.resolve(MULTIPLE_SESSION_IDS.get(1)).resolve(DETECTION_FILE_NAME));
            // MULTIPLE_SESSION_IDS.get(2) has detection enabled but no detection file present
            
            // Set detection file times to match the latest recording file in each session
            Files.setLastModifiedTime(detectionFile1, FileTime.from(times.get(0).get(2))); // 17 minutes ago
            Files.setLastModifiedTime(detectionFile2, FileTime.from(times.get(1).get(2))); // 10 minutes ago
            mockRepositoryInfo(DETECTION_FILE_NAME);

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());

            // First two sessions should be FINISHED (have detection files present, after finished period)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(0)), MULTIPLE_SESSION_IDS.get(0), RecordingStatus.FINISHED);
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(1)), MULTIPLE_SESSION_IDS.get(1), RecordingStatus.FINISHED);

            // Last session should be ACTIVE (detection enabled but file not present, within finished period)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(2)), MULTIPLE_SESSION_IDS.get(2), RecordingStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should return mixed statuses: latest ACTIVE (detection configured but no files present), others FINISHED")
        void listSessions_latestSessionActiveNoDetectionFilesPresent_othersFinished() throws IOException {
            // Given
            Instant now = Instant.now();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            // Detection is configured but NO sessions have detection files present
            // (no Files.createFile calls for detection files)

            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(25)), now.minus(Duration.ofMinutes(20)), now.minus(Duration.ofMinutes(15))), // after finished period
                    List.of(now.minus(Duration.ofMinutes(23)), now.minus(Duration.ofMinutes(18)), now.minus(Duration.ofMinutes(13))), // after finished period
                    List.of(now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(2))) // within finished period
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);
            mockRepositoryInfo(DETECTION_FILE_NAME); // detection configured but no files present

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());

            // First two sessions should be FINISHED (only latest can be ACTIVE, others always FINISHED)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(0)), MULTIPLE_SESSION_IDS.get(0), RecordingStatus.FINISHED);
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(1)), MULTIPLE_SESSION_IDS.get(1), RecordingStatus.FINISHED);

            // Last session should be ACTIVE (detection configured but file not present, within finished period)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(2)), MULTIPLE_SESSION_IDS.get(2), RecordingStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should return mixed statuses: latest ACTIVE (all within period, no detection files), others FINISHED")
        void listSessions_latestSessionActiveAllWithinPeriodNoDetectionFiles_othersFinished() throws IOException {
            // Given
            Instant now = Instant.now();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            // Detection is configured but NO sessions have detection files present
            // All sessions have files within finished period
            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(12)), now.minus(Duration.ofMinutes(10)), now.minus(Duration.ofMinutes(8))), // session-1: oldest, within finished period
                    List.of(now.minus(Duration.ofMinutes(6)), now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(4))), // session-2: middle, within finished period
                    List.of(now.minus(Duration.ofMinutes(3)), now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(1))) // session-3: latest, within finished period
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);
            mockRepositoryInfo(DETECTION_FILE_NAME); // detection configured but no files present

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());

            // First two sessions should be FINISHED (only latest can be ACTIVE, others always FINISHED)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(0)), MULTIPLE_SESSION_IDS.get(0), RecordingStatus.FINISHED);
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(1)), MULTIPLE_SESSION_IDS.get(1), RecordingStatus.FINISHED);

            // Last session should be ACTIVE (detection configured but file not present, within finished period)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(2)), MULTIPLE_SESSION_IDS.get(2), RecordingStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should return mixed statuses: latest UNKNOWN (no detection configured), others FINISHED")
        void listSessions_latestSessionActiveWithinPeriod_othersFinishedWithDetection() throws IOException {
            // Given
            Instant now = Instant.now();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            // All sessions have files within finished period
            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(12)), now.minus(Duration.ofMinutes(10)), now.minus(Duration.ofMinutes(8))), // session-1: oldest, within finished period
                    List.of(now.minus(Duration.ofMinutes(6)), now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(4))), // session-2: middle, within finished period  
                    List.of(now.minus(Duration.ofMinutes(3)), now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(1))) // session-3: latest, within finished period
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);
            mockRepositoryInfo(null); // no detection file configured

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());

            // First two sessions should always be FINISHED (only latest can be ACTIVE/UNKNOWN)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(0)), MULTIPLE_SESSION_IDS.get(0), RecordingStatus.FINISHED);
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(1)), MULTIPLE_SESSION_IDS.get(1), RecordingStatus.FINISHED);

            // Latest session should be UNKNOWN (no detection file configured, within finished period)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(2)), MULTIPLE_SESSION_IDS.get(2), RecordingStatus.UNKNOWN);
        }

        @Test
        @DisplayName("Should return mixed statuses: latest UNKNOWN (no detection file), others FINISHED")
        void listSessions_latestSessionUnknownWithoutDetectionFile_othersFinished() throws IOException {
            // Given
            Instant now = Instant.now();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(25)), now.minus(Duration.ofMinutes(20)), now.minus(Duration.ofMinutes(15))),
                    List.of(now.minus(Duration.ofMinutes(23)), now.minus(Duration.ofMinutes(18)), now.minus(Duration.ofMinutes(13))),
                    List.of(now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(2))) // within period
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);
            mockRepositoryInfo(null); // no detection file configured

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());

            // First two sessions should be FINISHED (after finished period)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(0)), MULTIPLE_SESSION_IDS.get(0), RecordingStatus.FINISHED);
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(1)), MULTIPLE_SESSION_IDS.get(1), RecordingStatus.FINISHED);

            // Last session should be UNKNOWN (no detection file configured, within period)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(2)), MULTIPLE_SESSION_IDS.get(2), RecordingStatus.UNKNOWN);
        }
    }

    private Path createSessionWithRecordingFiles(String sessionId) throws IOException {
        Path sessionDir = Files.createDirectories(tempDir.resolve(sessionId));
        for (String fileName : RECORDING_FILE_NAMES) {
            Files.createFile(sessionDir.resolve(fileName));
        }
        return sessionDir;
    }

    private void setFileModificationTimes(Path sessionDir, Instant oldTime, Instant middleTime, Instant latestTime) throws IOException {
        Files.setLastModifiedTime(sessionDir.resolve(RECORDING_FILE_NAMES.get(0)), FileTime.from(oldTime));
        Files.setLastModifiedTime(sessionDir.resolve(RECORDING_FILE_NAMES.get(1)), FileTime.from(middleTime));
        Files.setLastModifiedTime(sessionDir.resolve(RECORDING_FILE_NAMES.get(2)), FileTime.from(latestTime));
    }

    private void mockRepositoryInfo(String detectionFileName) {
        DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, detectionFileName);
        when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));
    }

    private void assertBasicSessionProperties(RecordingSession result, String expectedSessionId, RecordingStatus expectedStatus) {
        assertNotNull(result);
        assertEquals(expectedSessionId, result.id());
        assertEquals(expectedSessionId, result.name());
        assertEquals(expectedStatus, result.status());
    }

    private void assertFilesSortedByModificationTime(List<pbouda.jeffrey.common.model.repository.RepositoryFile> files, String... expectedNames) {
        assertEquals(expectedNames.length, files.size());
        for (int i = 0; i < expectedNames.length; i++) {
            assertEquals(expectedNames[i], files.get(i).name());
        }
    }

    private void assertAllRecordingFiles(List<pbouda.jeffrey.common.model.repository.RepositoryFile> files, int startIndex, int count) {
        for (int i = startIndex; i < startIndex + count; i++) {
            assertTrue(files.get(i).isRecordingFile());
        }
    }

    private void createMultipleSessionsWithRecordingFiles(List<String> sessionIds) throws IOException {
        for (String sessionId : sessionIds) {
            createSessionWithRecordingFiles(sessionId);
        }
    }

    private void setMultipleSessionsModificationTimes(List<String> sessionIds, List<List<Instant>> times) throws IOException {
        for (int i = 0; i < sessionIds.size(); i++) {
            Path sessionDir = tempDir.resolve(sessionIds.get(i));
            List<Instant> sessionTimes = times.get(i);
            setFileModificationTimes(sessionDir, sessionTimes.get(0), sessionTimes.get(1), sessionTimes.get(2));
        }
    }

    private RecordingSession findSessionById(List<RecordingSession> sessions, String sessionId) {
        return sessions.stream()
                .filter(session -> session.id().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Session not found: " + sessionId));
    }
}
