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
import pbouda.jeffrey.common.model.workspace.WorkspaceSessionInfo;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsprofWithTempFileRemoteRepositoryStorageTest {

    @Mock
    private ProjectRepositoryRepository projectRepositoryRepository;

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private HomeDirs homeDirs;

    private AsprofWithTempFileRemoteRepositoryStorage storage;
    private Clock clock;

    @TempDir
    private Path tempDir;

    private static final String SESSION_ID = "test-session";
    private static final Duration FINISHED_PERIOD = Duration.ofMinutes(5);
    private static final List<String> RECORDING_FILE_NAMES = List.of(
            "old-recording.jfr",
            "middle-recording.jfr",
            "latest-recording.jfr"
    );
    private static final List<String> ASPROF_CACHE_FILES = List.of(
            "profile.jfr.1~",
            "profile.jfr.2~",
            "async-profile-20250803.jfr.123~"
    );
    private static final List<String> MULTIPLE_SESSION_IDS = List.of(
            "session-1",
            "session-2",
            "session-3"
    );

    private static final String PROJECT_ID = "test-project-id";

    @BeforeEach
    void setUp() {
        Instant fixedTime = Instant.parse("2025-08-03T12:00:00Z");
        clock = Clock.fixed(fixedTime, ZoneOffset.UTC);
        storage = new AsprofWithTempFileRemoteRepositoryStorage(
                PROJECT_ID,
                homeDirs,
                projectRepositoryRepository,
                workspaceRepository,
                new FilesystemFileInfoProcessor(),
                FINISHED_PERIOD,
                clock);
    }

    @Nested
    @DisplayName("ASPROF Cache File Detection - Single Session")
    class AsprofCacheDetectionSingleSessionTests {

        @Test
        @DisplayName("Should return ACTIVE when ASPROF cache files exist and are within finished period")
        void singleSession_withAsprofCacheFilesWithinPeriod_shouldReturnActive() throws IOException {
            // Given
            Instant now = clock.instant();
            Path sessionDir = createSessionWithRecordingFiles(SESSION_ID);

            // Create ASPROF cache files 
            Path asprofFile1 = Files.createFile(sessionDir.resolve(ASPROF_CACHE_FILES.get(0)));
            Path asprofFile2 = Files.createFile(sessionDir.resolve(ASPROF_CACHE_FILES.get(1)));

            // Set ASPROF cache file times to recent (within finished period)
            Instant recentTime = now.minus(Duration.ofMinutes(2));
            Files.setLastModifiedTime(asprofFile1, FileTime.from(recentTime.minus(Duration.ofMinutes(1))));
            Files.setLastModifiedTime(asprofFile2, FileTime.from(recentTime)); // Latest ASPROF file

            // Set recording file times (older)
            setFileModificationTimes(sessionDir,
                    now.minus(Duration.ofMinutes(10)),
                    now.minus(Duration.ofMinutes(8)),
                    now.minus(Duration.ofMinutes(6)));

            mockRepositoryInfo();
            mockWorkspaceSession(SESSION_ID, SESSION_ID);

            // When
            RecordingSession result = storage.singleSession(SESSION_ID).get();

            // Then
            assertBasicSessionProperties(result, SESSION_ID, RecordingStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should return FINISHED when ASPROF cache files exist but are after finished period")
        void singleSession_withAsprofCacheFilesAfterPeriod_shouldReturnFinished() throws IOException {
            // Given
            Instant now = clock.instant();
            Path sessionDir = createSessionWithRecordingFiles(SESSION_ID);

            // Create ASPROF cache files
            Path asprofFile = Files.createFile(sessionDir.resolve(ASPROF_CACHE_FILES.get(0)));

            // Set ASPROF cache file time to after finished period
            Instant oldTime = now.minus(Duration.ofMinutes(10)); // After 5-minute finished period
            Files.setLastModifiedTime(asprofFile, FileTime.from(oldTime));

            // Set recording file times (also old)
            setFileModificationTimes(sessionDir,
                    now.minus(Duration.ofMinutes(15)),
                    now.minus(Duration.ofMinutes(12)),
                    now.minus(Duration.ofMinutes(8)));

            mockRepositoryInfo();
            mockWorkspaceSession(SESSION_ID, SESSION_ID);

            // When
            RecordingSession result = storage.singleSession(SESSION_ID).get();

            // Then
            assertBasicSessionProperties(result, SESSION_ID, RecordingStatus.FINISHED);
        }

        @Test
        @DisplayName("Should return FINISHED when no ASPROF cache files exist")
        void singleSession_withNoAsprofCacheFiles_shouldReturnFinished() throws IOException {
            // Given
            Instant now = clock.instant();
            Path sessionDir = createSessionWithRecordingFiles(SESSION_ID);

            // Set recording file times to recent (within finished period)
            setFileModificationTimes(sessionDir,
                    now.minus(Duration.ofMinutes(8)),
                    now.minus(Duration.ofMinutes(5)),
                    now.minus(Duration.ofMinutes(2))); // Latest recording file within period

            mockRepositoryInfo();
            mockWorkspaceSession(SESSION_ID, SESSION_ID);

            // When
            RecordingSession result = storage.singleSession(SESSION_ID).get();

            // Then
            assertBasicSessionProperties(result, SESSION_ID, RecordingStatus.FINISHED);
        }

        @Test
        @DisplayName("Should return ACTIVE when multiple ASPROF cache files exist and latest is within period")
        void singleSession_withMultipleAsprofCacheFilesLatestWithinPeriod_shouldReturnActive() throws IOException {
            // Given
            Instant now = clock.instant();
            Path sessionDir = createSessionWithRecordingFiles(SESSION_ID);

            // Create multiple ASPROF cache files
            Path oldAsprofFile = Files.createFile(sessionDir.resolve(ASPROF_CACHE_FILES.get(0)));
            Path recentAsprofFile = Files.createFile(sessionDir.resolve(ASPROF_CACHE_FILES.get(1)));

            // Set old ASPROF file to after finished period
            Instant oldTime = now.minus(Duration.ofMinutes(10));
            Files.setLastModifiedTime(oldAsprofFile, FileTime.from(oldTime));

            // Set recent ASPROF file to within finished period
            Instant recentTime = now.minus(Duration.ofMinutes(3));
            Files.setLastModifiedTime(recentAsprofFile, FileTime.from(recentTime));

            setFileModificationTimes(sessionDir,
                    now.minus(Duration.ofMinutes(12)),
                    now.minus(Duration.ofMinutes(10)),
                    now.minus(Duration.ofMinutes(8)));

            mockRepositoryInfo();
            mockWorkspaceSession(SESSION_ID, SESSION_ID);

            // When
            RecordingSession result = storage.singleSession(SESSION_ID).get();

            // Then
            assertBasicSessionProperties(result, SESSION_ID, RecordingStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should return FINISHED when multiple ASPROF cache files exist but all are after finished period")
        void singleSession_withMultipleAsprofCacheFilesAllAfterPeriod_shouldReturnFinished() throws IOException {
            // Given
            Instant now = clock.instant();
            Path sessionDir = createSessionWithRecordingFiles(SESSION_ID);

            // Create multiple ASPROF cache files
            Path asprofFile1 = Files.createFile(sessionDir.resolve(ASPROF_CACHE_FILES.get(0)));
            Path asprofFile2 = Files.createFile(sessionDir.resolve(ASPROF_CACHE_FILES.get(1)));

            // Set all ASPROF files to after finished period
            Instant oldTime1 = now.minus(Duration.ofMinutes(8));
            Instant oldTime2 = now.minus(Duration.ofMinutes(6));
            Files.setLastModifiedTime(asprofFile1, FileTime.from(oldTime1));
            Files.setLastModifiedTime(asprofFile2, FileTime.from(oldTime2));

            setFileModificationTimes(sessionDir,
                    now.minus(Duration.ofMinutes(15)),
                    now.minus(Duration.ofMinutes(12)),
                    now.minus(Duration.ofMinutes(10)));

            mockRepositoryInfo();
            mockWorkspaceSession(SESSION_ID, SESSION_ID);

            // When
            RecordingSession result = storage.singleSession(SESSION_ID).get();

            // Then
            assertBasicSessionProperties(result, SESSION_ID, RecordingStatus.FINISHED);
        }

        @Test
        @DisplayName("Should return ACTIVE when ASPROF cache files exist alongside other file types")
        void singleSession_withAsprofCacheFilesAndOtherFiles_shouldReturnActive() throws IOException {
            // Given
            Instant now = clock.instant();
            Path sessionDir = createSessionWithRecordingFiles(SESSION_ID);

            // Create ASPROF cache file
            Path asprofFile = Files.createFile(sessionDir.resolve(ASPROF_CACHE_FILES.get(0)));

            // Create other file types that should be ignored
            Files.createFile(sessionDir.resolve("metadata.txt"));
            Files.createFile(sessionDir.resolve("profile.jfr.txt")); // Not ASPROF pattern
            Files.createFile(sessionDir.resolve("profile.jfr.abc~")); // Not ASPROF pattern

            // Set ASPROF file to within finished period
            Instant recentTime = now.minus(Duration.ofMinutes(1));
            Files.setLastModifiedTime(asprofFile, FileTime.from(recentTime));

            setFileModificationTimes(sessionDir,
                    now.minus(Duration.ofMinutes(15)),
                    now.minus(Duration.ofMinutes(12)),
                    now.minus(Duration.ofMinutes(10)));

            mockRepositoryInfo();
            mockWorkspaceSession(SESSION_ID, SESSION_ID);

            // When
            RecordingSession result = storage.singleSession(SESSION_ID).get();

            // Then
            assertBasicSessionProperties(result, SESSION_ID, RecordingStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should return FINISHED when hidden ASPROF cache files exist")
        void singleSession_withHiddenAsprofCacheFiles_shouldReturnFinished() throws IOException {
            // Given
            Instant now = clock.instant();
            Path sessionDir = createSessionWithRecordingFiles(SESSION_ID);

            // Create hidden ASPROF cache file (should be ignored)
            Path hiddenAsprofFile = Files.createFile(sessionDir.resolve(".profile.jfr.1~"));

            // Set hidden ASPROF file to within finished period
            Instant recentTime = now.minus(Duration.ofMinutes(1));
            Files.setLastModifiedTime(hiddenAsprofFile, FileTime.from(recentTime));

            setFileModificationTimes(sessionDir,
                    now.minus(Duration.ofMinutes(10)),
                    now.minus(Duration.ofMinutes(8)),
                    now.minus(Duration.ofMinutes(6)));

            mockRepositoryInfo();
            mockWorkspaceSession(SESSION_ID, SESSION_ID);

            // When
            RecordingSession result = storage.singleSession(SESSION_ID).get();

            // Then
            assertBasicSessionProperties(result, SESSION_ID, RecordingStatus.FINISHED); // Should ignore hidden files
        }

        @Test
        @DisplayName("Should return ACTIVE for complex ASPROF filename patterns")
        void singleSession_withComplexAsprofFilenamePatterns_shouldReturnActive() throws IOException {
            // Given
            Instant now = clock.instant();
            Path sessionDir = createSessionWithRecordingFiles(SESSION_ID);

            // Create complex ASPROF cache file
            Path complexAsprofFile = Files.createFile(sessionDir.resolve(ASPROF_CACHE_FILES.get(2))); // "async-profile-20250803.jfr.123~"

            // Set ASPROF file to within finished period
            Instant recentTime = now.minus(Duration.ofMinutes(2));
            Files.setLastModifiedTime(complexAsprofFile, FileTime.from(recentTime));

            setFileModificationTimes(sessionDir,
                    now.minus(Duration.ofMinutes(15)),
                    now.minus(Duration.ofMinutes(12)),
                    now.minus(Duration.ofMinutes(10)));

            mockRepositoryInfo();
            mockWorkspaceSession(SESSION_ID, SESSION_ID);

            // When
            RecordingSession result = storage.singleSession(SESSION_ID).get();

            // Then
            assertBasicSessionProperties(result, SESSION_ID, RecordingStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should return ACTIVE when ASPROF cache files at finished period boundary")
        void singleSession_withAsprofCacheFilesAtBoundary_shouldReturnActive() throws IOException {
            // Given
            Instant now = clock.instant();
            Path sessionDir = createSessionWithRecordingFiles(SESSION_ID);

            // Create ASPROF cache file
            Path asprofFile = Files.createFile(sessionDir.resolve(ASPROF_CACHE_FILES.get(0)));

            // Set ASPROF file to just within boundary (4 minutes 59 seconds ago)
            Instant boundaryTime = now.minus(FINISHED_PERIOD).plusSeconds(1);
            Files.setLastModifiedTime(asprofFile, FileTime.from(boundaryTime));

            setFileModificationTimes(sessionDir,
                    now.minus(Duration.ofMinutes(15)),
                    now.minus(Duration.ofMinutes(12)),
                    now.minus(Duration.ofMinutes(10)));

            mockRepositoryInfo();
            mockWorkspaceSession(SESSION_ID, SESSION_ID);

            // When
            RecordingSession result = storage.singleSession(SESSION_ID).get();

            // Then
            assertBasicSessionProperties(result, SESSION_ID, RecordingStatus.ACTIVE);
        }
    }

    @Nested
    @DisplayName("ASPROF Cache File Detection - Multiple Sessions")
    class AsprofCacheDetectionMultipleSessionsTests {

        @Test
        @DisplayName("Should return mixed statuses: latest ACTIVE (has ASPROF cache), others FINISHED")
        void listSessions_latestSessionWithAsprofCache_othersFinished() throws IOException {
            // Given
            Instant now = clock.instant();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            // Create ASPROF cache file only in the latest session
            Path latestSessionDir = tempDir.resolve(MULTIPLE_SESSION_IDS.get(2));
            Path asprofFile = Files.createFile(latestSessionDir.resolve(ASPROF_CACHE_FILES.get(0)));

            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(25)), now.minus(Duration.ofMinutes(20)), now.minus(Duration.ofMinutes(15))), // session-1: finished
                    List.of(now.minus(Duration.ofMinutes(23)), now.minus(Duration.ofMinutes(18)), now.minus(Duration.ofMinutes(13))), // session-2: finished
                    List.of(now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(4))) // session-3: latest
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);

            // Set ASPROF cache file to within finished period
            Instant asprofTime = now.minus(Duration.ofMinutes(2));
            Files.setLastModifiedTime(asprofFile, FileTime.from(asprofTime));

            mockRepositoryInfo();
            mockMultipleWorkspaceSessions(MULTIPLE_SESSION_IDS);

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());

            // First two sessions should be FINISHED (only latest can be ACTIVE)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(0)), MULTIPLE_SESSION_IDS.get(0), RecordingStatus.FINISHED);
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(1)), MULTIPLE_SESSION_IDS.get(1), RecordingStatus.FINISHED);

            // Latest session should be ACTIVE (has ASPROF cache file within period)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(2)), MULTIPLE_SESSION_IDS.get(2), RecordingStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should return all FINISHED when latest session has ASPROF cache files but after finished period")
        void listSessions_latestSessionWithAsprofCacheAfterPeriod_allFinished() throws IOException {
            // Given
            Instant now = clock.instant();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            // Create ASPROF cache file in the latest session but set it to after finished period
            Path latestSessionDir = tempDir.resolve(MULTIPLE_SESSION_IDS.get(2));
            Path asprofFile = Files.createFile(latestSessionDir.resolve(ASPROF_CACHE_FILES.get(0)));

            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(30)), now.minus(Duration.ofMinutes(28)), now.minus(Duration.ofMinutes(25))), // session-1
                    List.of(now.minus(Duration.ofMinutes(22)), now.minus(Duration.ofMinutes(20)), now.minus(Duration.ofMinutes(18))), // session-2
                    List.of(now.minus(Duration.ofMinutes(15)), now.minus(Duration.ofMinutes(13)), now.minus(Duration.ofMinutes(11))) // session-3: latest
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);

            // Set ASPROF cache file to after finished period
            Instant asprofTime = now.minus(Duration.ofMinutes(10)); // After 5-minute finished period
            Files.setLastModifiedTime(asprofFile, FileTime.from(asprofTime));

            mockRepositoryInfo();
            mockMultipleWorkspaceSessions(MULTIPLE_SESSION_IDS);

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());

            // All sessions should be FINISHED
            for (String sessionId : MULTIPLE_SESSION_IDS) {
                assertBasicSessionProperties(findSessionById(result, sessionId), sessionId, RecordingStatus.FINISHED);
            }
        }

        @Test
        @DisplayName("Should return all FINISHED when no sessions have ASPROF cache files")
        void listSessions_noSessionsWithAsprofCache_allFinished() throws IOException {
            // Given
            Instant now = clock.instant();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            // No ASPROF cache files created for any session
            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(12)), now.minus(Duration.ofMinutes(10)), now.minus(Duration.ofMinutes(8))), // session-1: within period
                    List.of(now.minus(Duration.ofMinutes(6)), now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(4))), // session-2: within period
                    List.of(now.minus(Duration.ofMinutes(3)), now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(1))) // session-3: latest, within period
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);
            mockRepositoryInfo();
            mockMultipleWorkspaceSessions(MULTIPLE_SESSION_IDS);

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());

            // All sessions should be FINISHED (no ASPROF cache files)
            for (String sessionId : MULTIPLE_SESSION_IDS) {
                assertBasicSessionProperties(findSessionById(result, sessionId), sessionId, RecordingStatus.FINISHED);
            }
        }

        @Test
        @DisplayName("Should return mixed statuses: latest ACTIVE (multiple ASPROF cache files, latest within period), others FINISHED")
        void listSessions_latestSessionWithMultipleAsprofCaches_othersFinished() throws IOException {
            // Given
            Instant now = clock.instant();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            // Create multiple ASPROF cache files in the latest session
            Path latestSessionDir = tempDir.resolve(MULTIPLE_SESSION_IDS.get(2));
            Path oldAsprofFile = Files.createFile(latestSessionDir.resolve(ASPROF_CACHE_FILES.get(0)));
            Path recentAsprofFile = Files.createFile(latestSessionDir.resolve(ASPROF_CACHE_FILES.get(1)));

            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(25)), now.minus(Duration.ofMinutes(20)), now.minus(Duration.ofMinutes(15))), // session-1
                    List.of(now.minus(Duration.ofMinutes(23)), now.minus(Duration.ofMinutes(18)), now.minus(Duration.ofMinutes(13))), // session-2
                    List.of(now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(4))) // session-3: latest
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);

            // Set old ASPROF cache file to after finished period
            Instant oldAsprofTime = now.minus(Duration.ofMinutes(10));
            Files.setLastModifiedTime(oldAsprofFile, FileTime.from(oldAsprofTime));

            // Set recent ASPROF cache file to within finished period (this should make it ACTIVE)
            Instant recentAsprofTime = now.minus(Duration.ofMinutes(3));
            Files.setLastModifiedTime(recentAsprofFile, FileTime.from(recentAsprofTime));

            mockRepositoryInfo();
            mockMultipleWorkspaceSessions(MULTIPLE_SESSION_IDS);

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());

            // First two sessions should be FINISHED
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(0)), MULTIPLE_SESSION_IDS.get(0), RecordingStatus.FINISHED);
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(1)), MULTIPLE_SESSION_IDS.get(1), RecordingStatus.FINISHED);

            // Latest session should be ACTIVE (latest ASPROF cache file within period)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(2)), MULTIPLE_SESSION_IDS.get(2), RecordingStatus.ACTIVE);
        }

        @Test
        @DisplayName("Should handle subdirectories and ignore ASPROF cache files in subdirectories")
        void listSessions_withAsprofCacheFilesInSubdirectories_shouldIgnoreSubdirFiles() throws IOException {
            // Given
            Instant now = clock.instant();
            createMultipleSessionsWithRecordingFiles(MULTIPLE_SESSION_IDS);

            // Create ASPROF cache files in subdirectory (should be ignored)
            Path latestSessionDir = tempDir.resolve(MULTIPLE_SESSION_IDS.get(2));
            Path subDir = Files.createDirectories(latestSessionDir.resolve("subdir"));
            Path asprofInSubdir = Files.createFile(subDir.resolve(ASPROF_CACHE_FILES.get(0)));

            // Create ASPROF cache file in root directory (should be detected)
            Path asprofInRoot = Files.createFile(latestSessionDir.resolve(ASPROF_CACHE_FILES.get(1)));

            List<List<Instant>> times = List.of(
                    List.of(now.minus(Duration.ofMinutes(25)), now.minus(Duration.ofMinutes(20)), now.minus(Duration.ofMinutes(15))), // session-1
                    List.of(now.minus(Duration.ofMinutes(23)), now.minus(Duration.ofMinutes(18)), now.minus(Duration.ofMinutes(13))), // session-2
                    List.of(now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(4))) // session-3: latest
            );

            setMultipleSessionsModificationTimes(MULTIPLE_SESSION_IDS, times);

            // Set both ASPROF files to within period
            Instant asprofTime = now.minus(Duration.ofMinutes(1));
            Files.setLastModifiedTime(asprofInSubdir, FileTime.from(asprofTime));
            Files.setLastModifiedTime(asprofInRoot, FileTime.from(asprofTime));

            mockRepositoryInfo();
            mockMultipleWorkspaceSessions(MULTIPLE_SESSION_IDS);

            // When
            List<RecordingSession> result = storage.listSessions();

            // Then
            assertEquals(3, result.size());

            // First two sessions should be FINISHED
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(0)), MULTIPLE_SESSION_IDS.get(0), RecordingStatus.FINISHED);
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(1)), MULTIPLE_SESSION_IDS.get(1), RecordingStatus.FINISHED);

            // Latest session should be ACTIVE (root-level ASPROF cache file detected)
            assertBasicSessionProperties(findSessionById(result, MULTIPLE_SESSION_IDS.get(2)), MULTIPLE_SESSION_IDS.get(2), RecordingStatus.ACTIVE);
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

    private void mockRepositoryInfo() {
        DBRepositoryInfo repositoryInfo = new DBRepositoryInfo(RepositoryType.ASYNC_PROFILER, null);
        when(projectRepositoryRepository.getAll()).thenReturn(List.of(repositoryInfo));
    }

    private void assertBasicSessionProperties(RecordingSession result, String expectedSessionId, RecordingStatus expectedStatus) {
        assertNotNull(result);
        assertEquals(expectedSessionId, result.id());
        assertEquals(expectedSessionId, result.name());
        assertEquals(expectedStatus, result.status());
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

    private void mockWorkspaceSession(String sessionId, String relativePath) {
        WorkspaceSessionInfo sessionInfo = new WorkspaceSessionInfo(
                sessionId,
                sessionId,
                PROJECT_ID,
                "workspace-1",
                "finished.txt",
                Path.of(relativePath),
                tempDir, // workspace path - since this is set, homeDirs.workspaces() won't be called
                Instant.now(),
                Instant.now()
        );

        // Single session tests actually use listSessions() internally, which calls findSessionsByProjectId
        when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                .thenReturn(List.of(sessionInfo));
    }

    private void mockMultipleWorkspaceSessions(List<String> sessionIds) {
        List<WorkspaceSessionInfo> sessions = new ArrayList<>();

        for (int i = 0; i < sessionIds.size(); i++) {
            String sessionId = sessionIds.get(i);
            WorkspaceSessionInfo sessionInfo = new WorkspaceSessionInfo(
                    sessionId,
                    sessionId,
                    PROJECT_ID,
                    "workspace-1",
                    "finished.txt",
                    Path.of(sessionId), // relative path within workspace
                    tempDir, // workspace path - since this is set, homeDirs.workspaces() won't be called
                    // Create times so that the LAST session (index 2) is the LATEST (newest)
                    // Since sessions are sorted by createdAt DESC, the last session should have the newest time
                    Instant.now().minusSeconds((sessionIds.size() - 1 - i) * 60), // Reverse the order
                    Instant.now().minusSeconds((sessionIds.size() - 1 - i) * 60)
            );
            sessions.add(sessionInfo);
        }

        // For multiple sessions tests, we only need the list method
        when(workspaceRepository.findSessionsByProjectId(PROJECT_ID))
                .thenReturn(sessions);
    }
}
