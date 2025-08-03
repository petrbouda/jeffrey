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

package pbouda.jeffrey.project.repository.detection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pbouda.jeffrey.common.model.repository.RecordingStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AsprofStatusStrategyTest {

    @TempDir
    private Path tempDir;

    private AsprofStatusStrategy strategy;
    private static final Duration FINISHED_PERIOD = Duration.ofMinutes(5);

    @BeforeEach
    void setUp() {
        strategy = new AsprofStatusStrategy(FINISHED_PERIOD, Clock.systemUTC());
    }

    @Nested
    @DisplayName("ACTIVE Status Tests")
    class ActiveStatusTests {

        @Test
        @DisplayName("Should return ACTIVE when ASPROF file exists and is within finished period")
        void shouldReturnActiveWhenAsprofFileExistsAndWithinPeriod() throws IOException {
            // Given
            Path sessionDir = Files.createDirectories(tempDir.resolve("session1"));
            Path asprofFile = Files.createFile(sessionDir.resolve("profile.jfr.1~"));
            
            // Set modification time to 2 minutes ago (within 5-minute period)
            Instant recentTime = Instant.now().minus(Duration.ofMinutes(2));
            Files.setLastModifiedTime(asprofFile, FileTime.from(recentTime));

            // When
            RecordingStatus result = strategy.determineStatus(sessionDir);

            // Then
            assertEquals(RecordingStatus.ACTIVE, result);
        }

        @Test
        @DisplayName("Should return ACTIVE when multiple ASPROF files exist and latest is within period")
        void shouldReturnActiveWhenMultipleAsprofFilesExistAndLatestWithinPeriod() throws IOException {
            // Given
            Path sessionDir = Files.createDirectories(tempDir.resolve("session2"));
            Path oldAsprofFile = Files.createFile(sessionDir.resolve("profile.jfr.1~"));
            Path newAsprofFile = Files.createFile(sessionDir.resolve("profile.jfr.2~"));
            
            // Set old file to 10 minutes ago (after finished period)
            Instant oldTime = Instant.now().minus(Duration.ofMinutes(10));
            Files.setLastModifiedTime(oldAsprofFile, FileTime.from(oldTime));
            
            // Set new file to 3 minutes ago (within finished period)
            Instant newTime = Instant.now().minus(Duration.ofMinutes(3));
            Files.setLastModifiedTime(newAsprofFile, FileTime.from(newTime));

            // When
            RecordingStatus result = strategy.determineStatus(sessionDir);

            // Then
            assertEquals(RecordingStatus.ACTIVE, result);
        }

        @Test
        @DisplayName("Should return ACTIVE when ASPROF file exists alongside other file types")
        void shouldReturnActiveWhenAsprofFileExistsAlongsideOtherFileTypes() throws IOException {
            // Given
            Path sessionDir = Files.createDirectories(tempDir.resolve("session3"));
            Path asprofFile = Files.createFile(sessionDir.resolve("profile.jfr.5~"));
            Path jfrFile = Files.createFile(sessionDir.resolve("recording.jfr"));
            Path logFile = Files.createFile(sessionDir.resolve("app.log"));
            
            // Set ASPROF file to 1 minute ago (within period)
            Instant recentTime = Instant.now().minus(Duration.ofMinutes(1));
            Files.setLastModifiedTime(asprofFile, FileTime.from(recentTime));

            // When
            RecordingStatus result = strategy.determineStatus(sessionDir);

            // Then
            assertEquals(RecordingStatus.ACTIVE, result);
        }

        @Test
        @DisplayName("Should return ACTIVE for complex ASPROF filename patterns")
        void shouldReturnActiveForComplexAsprofFilenamePatterns() throws IOException {
            // Given
            Path sessionDir = Files.createDirectories(tempDir.resolve("session4"));
            Path complexAsprofFile = Files.createFile(sessionDir.resolve("profile-20250803-090556.jfr.123~"));
            
            // Set modification time to just now (definitely within period)
            Instant now = Instant.now();
            Files.setLastModifiedTime(complexAsprofFile, FileTime.from(now));

            // When
            RecordingStatus result = strategy.determineStatus(sessionDir);

            // Then
            assertEquals(RecordingStatus.ACTIVE, result);
        }
    }

    @Nested
    @DisplayName("FINISHED Status Tests")
    class FinishedStatusTests {

        @Test
        @DisplayName("Should return FINISHED when no ASPROF files exist")
        void shouldReturnFinishedWhenNoAsprofFilesExist() throws IOException {
            // Given
            Path sessionDir = Files.createDirectories(tempDir.resolve("session1"));
            Files.createFile(sessionDir.resolve("recording.jfr"));
            Files.createFile(sessionDir.resolve("app.log"));

            // When
            RecordingStatus result = strategy.determineStatus(sessionDir);

            // Then
            assertEquals(RecordingStatus.FINISHED, result);
        }

        @Test
        @DisplayName("Should return FINISHED when ASPROF files exist but are after finished period")
        void shouldReturnFinishedWhenAsprofFilesExistButAfterPeriod() throws IOException {
            // Given
            Path sessionDir = Files.createDirectories(tempDir.resolve("session2"));
            Path asprofFile = Files.createFile(sessionDir.resolve("profile.jfr.1~"));
            
            // Set modification time to 10 minutes ago (after 5-minute finished period)
            Instant oldTime = Instant.now().minus(Duration.ofMinutes(10));
            Files.setLastModifiedTime(asprofFile, FileTime.from(oldTime));

            // When
            RecordingStatus result = strategy.determineStatus(sessionDir);

            // Then
            assertEquals(RecordingStatus.FINISHED, result);
        }

        @Test
        @DisplayName("Should return FINISHED when all ASPROF files are after finished period")
        void shouldReturnFinishedWhenAllAsprofFilesAfterPeriod() throws IOException {
            // Given
            Path sessionDir = Files.createDirectories(tempDir.resolve("session3"));
            Path asprofFile1 = Files.createFile(sessionDir.resolve("profile.jfr.1~"));
            Path asprofFile2 = Files.createFile(sessionDir.resolve("profile.jfr.2~"));
            
            // Set both files to times after finished period
            Instant oldTime1 = Instant.now().minus(Duration.ofMinutes(8));
            Instant oldTime2 = Instant.now().minus(Duration.ofMinutes(6));
            Files.setLastModifiedTime(asprofFile1, FileTime.from(oldTime1));
            Files.setLastModifiedTime(asprofFile2, FileTime.from(oldTime2));

            // When
            RecordingStatus result = strategy.determineStatus(sessionDir);

            // Then
            assertEquals(RecordingStatus.FINISHED, result);
        }

        @Test
        @DisplayName("Should return FINISHED when session directory is empty")
        void shouldReturnFinishedWhenSessionDirectoryIsEmpty() throws IOException {
            // Given
            Path emptySessionDir = Files.createDirectories(tempDir.resolve("empty-session"));

            // When
            RecordingStatus result = strategy.determineStatus(emptySessionDir);

            // Then
            assertEquals(RecordingStatus.FINISHED, result);
        }

        @Test
        @DisplayName("Should return FINISHED when only non-ASPROF files with similar names exist")
        void shouldReturnFinishedWhenOnlyNonAsprofFilesWithSimilarNamesExist() throws IOException {
            // Given
            Path sessionDir = Files.createDirectories(tempDir.resolve("session4"));
            Files.createFile(sessionDir.resolve("profile.jfr"));  // JFR file, not ASPROF
            Files.createFile(sessionDir.resolve("profile.jfr.txt"));  // Not ASPROF pattern
            Files.createFile(sessionDir.resolve("profile.jfr.abc~"));  // Not ASPROF pattern (non-numeric)

            // When
            RecordingStatus result = strategy.determineStatus(sessionDir);

            // Then
            assertEquals(RecordingStatus.FINISHED, result);
        }
    }

    @Nested
    @DisplayName("FINISHED Status for Invalid Paths")
    class InvalidPathTests {

        @Test
        @DisplayName("Should return FINISHED when session directory does not exist")
        void shouldReturnFinishedWhenSessionDirectoryDoesNotExist() {
            // Given
            Path nonExistentDir = tempDir.resolve("non-existent");

            // When
            RecordingStatus result = strategy.determineStatus(nonExistentDir);

            // Then
            assertEquals(RecordingStatus.FINISHED, result);
        }

        @Test
        @DisplayName("Should return FINISHED when session path is a file instead of directory")
        void shouldReturnFinishedWhenSessionPathIsFile() throws IOException {
            // Given
            Path filePath = Files.createFile(tempDir.resolve("not-a-directory.txt"));

            // When
            RecordingStatus result = strategy.determineStatus(filePath);

            // Then
            assertEquals(RecordingStatus.FINISHED, result);
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCasesTests {

        @Test
        @DisplayName("Should ignore hidden ASPROF files")
        void shouldIgnoreHiddenAsprofFiles() throws IOException {
            // Given
            Path sessionDir = Files.createDirectories(tempDir.resolve("session1"));
            Path hiddenAsprofFile = Files.createFile(sessionDir.resolve(".hidden-profile.jfr.1~"));
            
            // Set modification time to recent (within period)
            Instant recentTime = Instant.now().minus(Duration.ofMinutes(1));
            Files.setLastModifiedTime(hiddenAsprofFile, FileTime.from(recentTime));

            // When
            RecordingStatus result = strategy.determineStatus(sessionDir);

            // Then
            assertEquals(RecordingStatus.FINISHED, result); // Should be FINISHED since hidden files are ignored
        }

        @Test
        @DisplayName("Should handle ASPROF files with zero-number suffix")
        void shouldHandleAsprofFilesWithZeroNumberSuffix() throws IOException {
            // Given
            Path sessionDir = Files.createDirectories(tempDir.resolve("session2"));
            Path zeroAsprofFile = Files.createFile(sessionDir.resolve("profile.jfr.0~"));
            
            // Set modification time to recent (within period)
            Instant recentTime = Instant.now().minus(Duration.ofMinutes(2));
            Files.setLastModifiedTime(zeroAsprofFile, FileTime.from(recentTime));

            // When
            RecordingStatus result = strategy.determineStatus(sessionDir);

            // Then
            assertEquals(RecordingStatus.ACTIVE, result);
        }

        @Test
        @DisplayName("Should handle ASPROF files with very large number suffix")
        void shouldHandleAsprofFilesWithVeryLargeNumberSuffix() throws IOException {
            // Given
            Path sessionDir = Files.createDirectories(tempDir.resolve("session3"));
            Path largeNumberAsprofFile = Files.createFile(sessionDir.resolve("profile.jfr.999999~"));
            
            // Set modification time to recent (within period)
            Instant recentTime = Instant.now().minus(Duration.ofMinutes(1));
            Files.setLastModifiedTime(largeNumberAsprofFile, FileTime.from(recentTime));

            // When
            RecordingStatus result = strategy.determineStatus(sessionDir);

            // Then
            assertEquals(RecordingStatus.ACTIVE, result);
        }

        @Test
        @DisplayName("Should handle ASPROF files at finished period boundary")
        void shouldHandleAsprofFilesAtFinishedPeriodBoundary() throws IOException {
            // Given
            Path sessionDir = Files.createDirectories(tempDir.resolve("session4"));
            Path boundaryAsprofFile = Files.createFile(sessionDir.resolve("profile.jfr.1~"));
            
            // Set modification time to slightly within the finished period (4 minutes 59 seconds ago)
            Instant withinBoundaryTime = Instant.now().minus(FINISHED_PERIOD).plusSeconds(1);
            Files.setLastModifiedTime(boundaryAsprofFile, FileTime.from(withinBoundaryTime));

            // When
            RecordingStatus result = strategy.determineStatus(sessionDir);

            // Then
            // Just within boundary should be ACTIVE
            assertEquals(RecordingStatus.ACTIVE, result);
        }

        @Test
        @DisplayName("Should handle subdirectories within session directory")
        void shouldHandleSubdirectoriesWithinSessionDirectory() throws IOException {
            // Given
            Path sessionDir = Files.createDirectories(tempDir.resolve("session5"));
            Path subDir = Files.createDirectories(sessionDir.resolve("subdir"));
            Path asprofInSubdir = Files.createFile(subDir.resolve("profile.jfr.1~"));
            Path asprofInRoot = Files.createFile(sessionDir.resolve("root-profile.jfr.2~"));
            
            // Set modification time to recent (within period)
            Instant recentTime = Instant.now().minus(Duration.ofMinutes(1));
            Files.setLastModifiedTime(asprofInRoot, FileTime.from(recentTime));

            // When
            RecordingStatus result = strategy.determineStatus(sessionDir);

            // Then
            assertEquals(RecordingStatus.ACTIVE, result); // Should find the root-level ASPROF file
        }
    }
}