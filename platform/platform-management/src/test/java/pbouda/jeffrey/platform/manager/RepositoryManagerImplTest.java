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
import pbouda.jeffrey.platform.project.repository.MergedRecording;
import pbouda.jeffrey.platform.project.repository.RepositoryStorage;
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.platform.scheduler.SchedulerTrigger;
import pbouda.jeffrey.profile.manager.model.RepositoryStatistics;
import pbouda.jeffrey.profile.manager.model.StreamedRecordingFile;
import pbouda.jeffrey.provider.platform.repository.ProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.repository.RecordingSession;
import pbouda.jeffrey.shared.common.model.repository.RecordingStatus;
import pbouda.jeffrey.shared.common.model.repository.RepositoryFile;
import pbouda.jeffrey.shared.common.model.repository.SupportedRecordingFile;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventCreator;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepositoryManagerImplTest {

    private static final Instant NOW = Instant.parse("2025-06-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            "proj-1", null, "Test Project", null, null,
            "ws-1", WorkspaceType.SANDBOX, NOW, null, Map.of());

    @Mock
    private SchedulerTrigger projectsSynchronizerTrigger;
    @Mock
    private ProjectRepositoryRepository repository;
    @Mock
    private RepositoryStorage repositoryStorage;
    @Mock
    private PersistentQueue<WorkspaceEvent> workspaceEventQueue;

    private RepositoryManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new RepositoryManagerImpl(
                FIXED_CLOCK, PROJECT_INFO, projectsSynchronizerTrigger,
                repository, repositoryStorage, workspaceEventQueue);
    }

    private static RepositoryFile file(String id, SupportedRecordingFile type, Long size, Instant createdAt) {
        return new RepositoryFile(id, id + ".jfr", createdAt, size, type, RecordingStatus.FINISHED, null);
    }

    private static RecordingSession session(String id, RecordingStatus status, List<RepositoryFile> files) {
        return new RecordingSession(id, id, null, Instant.now(), null, status, null, null, null, files);
    }

    @Nested
    class CalculateRepositoryStatistics {

        @Test
        void returnsZeroStatistics_whenNoSessions() {
            when(repositoryStorage.listSessions(true)).thenReturn(List.of());

            RepositoryStatistics stats = manager.calculateRepositoryStatistics();

            assertEquals(0, stats.totalSessions());
            assertEquals(RecordingStatus.UNKNOWN, stats.latestSessionStatus());
            assertEquals(0L, stats.lastActivityTimeMillis());
            assertEquals(0L, stats.totalSizeBytes());
            assertEquals(0, stats.totalFiles());
            assertEquals(0L, stats.biggestSessionSizeBytes());
            assertEquals(0, stats.jfrFiles());
            assertEquals(0, stats.heapDumpFiles());
            assertEquals(0, stats.logFiles());
            assertEquals(0, stats.errorLogFiles());
            assertEquals(0, stats.otherFiles());
        }

        @Test
        void countsFileTypes_correctly() {
            Instant t = Instant.parse("2025-06-01T10:00:00Z");
            List<RepositoryFile> files = List.of(
                    file("f1", SupportedRecordingFile.JFR, 100L, t),
                    file("f2", SupportedRecordingFile.JFR_LZ4, 200L, t),
                    file("f3", SupportedRecordingFile.HEAP_DUMP, 300L, t),
                    file("f4", SupportedRecordingFile.HEAP_DUMP_GZ, 400L, t),
                    file("f5", SupportedRecordingFile.JVM_LOG, 50L, t),
                    file("f6", SupportedRecordingFile.HS_JVM_ERROR_LOG, 60L, t),
                    file("f7", SupportedRecordingFile.UNKNOWN, 70L, t)
            );
            when(repositoryStorage.listSessions(true))
                    .thenReturn(List.of(session("s1", RecordingStatus.FINISHED, files)));

            RepositoryStatistics stats = manager.calculateRepositoryStatistics();

            assertEquals(2, stats.jfrFiles());
            assertEquals(2, stats.heapDumpFiles());
            assertEquals(1, stats.logFiles());
            assertEquals(1, stats.errorLogFiles());
            assertEquals(1, stats.otherFiles());
            assertEquals(7, stats.totalFiles());
            assertEquals(1180L, stats.totalSizeBytes());
        }

        @Test
        void calculatesLastActivityTime_acrossMultipleSessions() {
            Instant t1 = Instant.parse("2025-06-01T08:00:00Z");
            Instant t2 = Instant.parse("2025-06-01T10:00:00Z");
            Instant t3 = Instant.parse("2025-06-01T09:00:00Z");

            RecordingSession s1 = session("s1", RecordingStatus.FINISHED,
                    List.of(file("f1", SupportedRecordingFile.JFR, 100L, t1)));
            RecordingSession s2 = session("s2", RecordingStatus.ACTIVE,
                    List.of(file("f2", SupportedRecordingFile.JFR, 200L, t2),
                            file("f3", SupportedRecordingFile.JFR, 150L, t3)));

            when(repositoryStorage.listSessions(true)).thenReturn(List.of(s1, s2));

            RepositoryStatistics stats = manager.calculateRepositoryStatistics();

            assertEquals(t2.toEpochMilli(), stats.lastActivityTimeMillis());
        }

        @Test
        void handlesNullFileSize_gracefully() {
            Instant t = Instant.parse("2025-06-01T10:00:00Z");
            List<RepositoryFile> files = List.of(
                    file("f1", SupportedRecordingFile.JFR, null, t),
                    file("f2", SupportedRecordingFile.JFR, 500L, t)
            );
            when(repositoryStorage.listSessions(true))
                    .thenReturn(List.of(session("s1", RecordingStatus.FINISHED, files)));

            RepositoryStatistics stats = manager.calculateRepositoryStatistics();

            assertEquals(500L, stats.totalSizeBytes());
            assertEquals(2, stats.totalFiles());
        }

        @Test
        void handlesNullCreatedAt_gracefully() {
            List<RepositoryFile> files = List.of(
                    file("f1", SupportedRecordingFile.JFR, 100L, null)
            );
            when(repositoryStorage.listSessions(true))
                    .thenReturn(List.of(session("s1", RecordingStatus.FINISHED, files)));

            RepositoryStatistics stats = manager.calculateRepositoryStatistics();

            assertEquals(0L, stats.lastActivityTimeMillis());
            assertEquals(1, stats.totalFiles());
        }

        @Test
        void calculatesBiggestSessionSize_correctly() {
            Instant t = Instant.parse("2025-06-01T10:00:00Z");
            RecordingSession small = session("s1", RecordingStatus.FINISHED,
                    List.of(file("f1", SupportedRecordingFile.JFR, 100L, t)));
            RecordingSession big = session("s2", RecordingStatus.ACTIVE,
                    List.of(file("f2", SupportedRecordingFile.JFR, 500L, t),
                            file("f3", SupportedRecordingFile.JFR, 300L, t)));

            when(repositoryStorage.listSessions(true)).thenReturn(List.of(small, big));

            RepositoryStatistics stats = manager.calculateRepositoryStatistics();

            assertEquals(800L, stats.biggestSessionSizeBytes());
        }

        @Test
        void latestSessionStatus_isFromFirstSession() {
            Instant t = Instant.parse("2025-06-01T10:00:00Z");
            RecordingSession newest = session("s1", RecordingStatus.ACTIVE,
                    List.of(file("f1", SupportedRecordingFile.JFR, 100L, t)));
            RecordingSession oldest = session("s2", RecordingStatus.FINISHED,
                    List.of(file("f2", SupportedRecordingFile.JFR, 200L, t)));

            when(repositoryStorage.listSessions(true)).thenReturn(List.of(newest, oldest));

            RepositoryStatistics stats = manager.calculateRepositoryStatistics();

            assertEquals(RecordingStatus.ACTIVE, stats.latestSessionStatus());
        }

        @Test
        void singleSessionWithSingleFile_returnsCorrectStatistics() {
            Instant t = Instant.parse("2025-06-01T10:30:00Z");
            RecordingSession session = session("s1", RecordingStatus.FINISHED,
                    List.of(file("f1", SupportedRecordingFile.JFR, 1024L, t)));

            when(repositoryStorage.listSessions(true)).thenReturn(List.of(session));

            RepositoryStatistics stats = manager.calculateRepositoryStatistics();

            assertEquals(1, stats.totalSessions());
            assertEquals(RecordingStatus.FINISHED, stats.latestSessionStatus());
            assertEquals(t.toEpochMilli(), stats.lastActivityTimeMillis());
            assertEquals(1024L, stats.totalSizeBytes());
            assertEquals(1, stats.totalFiles());
            assertEquals(1024L, stats.biggestSessionSizeBytes());
            assertEquals(1, stats.jfrFiles());
            assertEquals(0, stats.heapDumpFiles());
        }
    }

    @Nested
    class StreamArtifact {

        @Test
        void returnsStreamedFile_whenArtifactExists() {
            Path artifactPath = Path.of("/tmp/session-1/artifact.hprof");
            when(repositoryStorage.artifacts("session-1", List.of("art-1")))
                    .thenReturn(List.of(artifactPath));

            StreamedRecordingFile result = manager.streamArtifact("session-1", "art-1");

            assertEquals("artifact.hprof", result.fileName());
            assertEquals(artifactPath, result.path());
        }

        @Test
        void throwsIllegalArgument_whenArtifactNotFound() {
            when(repositoryStorage.artifacts("session-1", List.of("art-1")))
                    .thenReturn(List.of());

            assertThrows(IllegalArgumentException.class,
                    () -> manager.streamArtifact("session-1", "art-1"));
        }
    }

    @Nested
    class MergeAndStreamRecordings {

        @Test
        void returnsMergedFile_withCloseCallback() {
            Path mergedPath = Path.of("/tmp/merged.jfr.lz4");
            MergedRecording merged = new MergedRecording(mergedPath);
            when(repositoryStorage.mergeRecordings("session-1", List.of("r1", "r2")))
                    .thenReturn(merged);

            StreamedRecordingFile result = manager.mergeAndStreamRecordings("session-1", List.of("r1", "r2"));

            assertEquals("merged.jfr.lz4", result.fileName());
            assertEquals(mergedPath, result.path());
            assertNotNull(result.cleanup());
        }
    }

    @Nested
    class DeleteRecordingSession {

        @Test
        void publishesSessionDeletedEvent_andTriggersSynchronizer() {
            manager.deleteRecordingSession("session-1", WorkspaceEventCreator.MANUAL);

            verify(workspaceEventQueue).appendBatch(eq("ws-1"), any());
            verify(projectsSynchronizerTrigger).execute();
        }
    }
}
