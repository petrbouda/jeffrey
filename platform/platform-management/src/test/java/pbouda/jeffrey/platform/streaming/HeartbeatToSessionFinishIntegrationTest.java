/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
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

package pbouda.jeffrey.platform.streaming;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.project.repository.SessionFinishEventEmitter;
import pbouda.jeffrey.platform.queue.DuckDBPersistentQueue;
import pbouda.jeffrey.platform.queue.QueueEntry;
import pbouda.jeffrey.platform.workspace.WorkspaceEventSerializer;
import pbouda.jeffrey.provider.platform.repository.JdbcProjectInstanceRepository;
import pbouda.jeffrey.provider.platform.repository.JdbcProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.HeartbeatConstants;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.MutableClock;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
@ExtendWith(MockitoExtension.class)
class HeartbeatToSessionFinishIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final String SESSION_ID = "session-001";
    private static final String SESSION_ID_2 = "session-002";
    private static final String INSTANCE_ID = "inst-001";

    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");
    private static final Duration HEARTBEAT_THRESHOLD = Duration.ofMinutes(5);

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, null, "Test Project", "Label 1", null,
            WORKSPACE_ID, WorkspaceType.LIVE,
            Instant.parse("2025-01-01T11:00:00Z"), null, Map.of());

    private static JdbcProjectRepositoryRepository createRepoRepository(MutableClock clock, DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        return new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);
    }

    private static JdbcProjectInstanceRepository createInstanceRepository(DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        return new JdbcProjectInstanceRepository(PROJECT_ID, provider);
    }

    private static DuckDBPersistentQueue<WorkspaceEvent> createQueue(MutableClock clock, DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        return new DuckDBPersistentQueue<>(provider, "workspace-events", new WorkspaceEventSerializer(), clock);
    }

    private static void writeHeartbeatFile(Path sessionDir, Instant timestamp) throws IOException {
        Path heartbeatDir = sessionDir.resolve(HeartbeatConstants.HEARTBEAT_DIR);
        Files.createDirectories(heartbeatDir);
        Files.writeString(heartbeatDir.resolve(HeartbeatConstants.HEARTBEAT_FILE),
                String.valueOf(timestamp.toEpochMilli()));
    }

    @Nested
    class GracefulShutdownScenario {

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Test
        void heartbeatsArrive_thenStop_sessionFinishedByPolling(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException, IOException {

            TestUtils.executeSql(dataSource, "sql/e2e/insert-project-for-e2e.sql");

            var clock = new MutableClock(NOW);
            var repoRepo = createRepoRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var fileHeartbeatReader = new FileHeartbeatReader();
            var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

            // Step 1: Write a heartbeat file (simulates agent writing heartbeats)
            Path sessionDir = tempDir.resolve("session-2025-06-15");
            Instant heartbeatTime = Instant.parse("2025-06-15T11:50:00Z");
            writeHeartbeatFile(sessionDir, heartbeatTime);

            // Step 2: Advance clock past threshold (heartbeat is now stale)
            clock.advance(Duration.ofMinutes(10));

            // Step 3: Read the session from DB
            ProjectInstanceSessionInfo sessionInfo = repoRepo.findSessionById(SESSION_ID).orElseThrow();

            // Step 4: SessionFinisher detects stale heartbeat file and marks session finished
            boolean finished = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, sessionInfo, sessionDir, HEARTBEAT_THRESHOLD, clock.instant());

            assertTrue(finished);

            // Step 5: Verify session is finished with heartbeat timestamp
            ProjectInstanceSessionInfo finishedSession = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertEquals(heartbeatTime, finishedSession.finishedAt());

            // Step 6: Verify SESSION_FINISHED event in queue
            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(WORKSPACE_ID, "test-consumer");
            assertEquals(1, entries.size());

            WorkspaceEvent event = entries.getFirst().payload();
            assertAll(
                    () -> assertEquals(PROJECT_ID, event.projectId()),
                    () -> assertEquals(WORKSPACE_ID, event.workspaceId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED, event.eventType()),
                    () -> assertEquals(SESSION_ID, event.originEventId())
            );
        }
    }

    @Nested
    class MultiSessionInstanceScenario {

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Test
        void firstSession_finishes_instanceStaysActive_secondSessionStillRunning(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException, IOException {

            TestUtils.executeSql(dataSource, "sql/e2e/insert-project-for-e2e-multi-session.sql");

            var clock = new MutableClock(NOW);
            var repoRepo = createRepoRepository(clock, dataSource);
            var instanceRepo = createInstanceRepository(dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var fileHeartbeatReader = new FileHeartbeatReader();
            var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

            // Write heartbeat file for session-001
            Path session1Dir = tempDir.resolve("session-001");
            Instant heartbeatTime = Instant.parse("2025-06-15T11:50:00Z");
            writeHeartbeatFile(session1Dir, heartbeatTime);

            // Advance clock past threshold
            clock.advance(Duration.ofMinutes(10));

            // Finish session-001 only
            ProjectInstanceSessionInfo session1 = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            boolean finished = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, session1, session1Dir, HEARTBEAT_THRESHOLD, clock.instant());

            assertTrue(finished);

            // Verify session-001 is finished
            ProjectInstanceSessionInfo finishedSession1 = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertNotNull(finishedSession1.finishedAt());

            // Verify session-002 is still unfinished
            ProjectInstanceSessionInfo session2 = repoRepo.findSessionById(SESSION_ID_2).orElseThrow();
            assertNull(session2.finishedAt());

            // Verify instance is still ACTIVE because session-002 is still running
            var instance = instanceRepo.find(INSTANCE_ID).orElseThrow();
            assertEquals(ProjectInstanceStatus.ACTIVE, instance.status());

            // Verify there are still unfinished sessions
            List<ProjectInstanceSessionInfo> unfinished = repoRepo.findUnfinishedSessions();
            assertEquals(1, unfinished.size());
            assertEquals(SESSION_ID_2, unfinished.getFirst().sessionId());
        }

        @Test
        void bothSessions_finish_instanceAutoFinishes(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException, IOException {

            TestUtils.executeSql(dataSource, "sql/e2e/insert-project-for-e2e-multi-session.sql");

            var clock = new MutableClock(NOW);
            var repoRepo = createRepoRepository(clock, dataSource);
            var instanceRepo = createInstanceRepository(dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var fileHeartbeatReader = new FileHeartbeatReader();
            var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

            // Write heartbeat files for both sessions
            Path session1Dir = tempDir.resolve("session-001");
            Path session2Dir = tempDir.resolve("session-002");
            Instant heartbeat1 = Instant.parse("2025-06-15T11:50:00Z");
            Instant heartbeat2 = Instant.parse("2025-06-15T11:51:00Z");
            writeHeartbeatFile(session1Dir, heartbeat1);
            writeHeartbeatFile(session2Dir, heartbeat2);

            // Advance clock past threshold for both
            clock.advance(Duration.ofMinutes(10));

            // Finish session-001
            ProjectInstanceSessionInfo session1 = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            boolean finished1 = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, session1, session1Dir, HEARTBEAT_THRESHOLD, clock.instant());
            assertTrue(finished1);

            // Finish session-002
            ProjectInstanceSessionInfo session2 = repoRepo.findSessionById(SESSION_ID_2).orElseThrow();
            boolean finished2 = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, session2, session2Dir, HEARTBEAT_THRESHOLD, clock.instant());
            assertTrue(finished2);

            // Verify both sessions are finished
            assertNotNull(repoRepo.findSessionById(SESSION_ID).orElseThrow().finishedAt());
            assertNotNull(repoRepo.findSessionById(SESSION_ID_2).orElseThrow().finishedAt());

            // Verify no unfinished sessions remain
            List<ProjectInstanceSessionInfo> unfinished = repoRepo.findUnfinishedSessions();
            assertTrue(unfinished.isEmpty());

            // Verify instance derives to FINISHED (all sessions are now finished)
            var instance = instanceRepo.find(INSTANCE_ID).orElseThrow();
            assertEquals(ProjectInstanceStatus.FINISHED, instance.status());
        }
    }

    @Nested
    class JeffreyRestartRecoveryScenario {

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Test
        void unfinishedSession_withHeartbeatFile_finishedOnRestart(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException, IOException {

            TestUtils.executeSql(dataSource, "sql/e2e/insert-project-for-e2e.sql");

            var clock = new MutableClock(NOW);
            var repoRepo = createRepoRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var fileHeartbeatReader = new FileHeartbeatReader();
            var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

            // Write a stale heartbeat file (simulates agent that stopped long ago)
            Path sessionDir = tempDir.resolve("session-2025-06-15");
            Instant staleHeartbeat = NOW.minus(Duration.ofMinutes(10));
            writeHeartbeatFile(sessionDir, staleHeartbeat);

            // Read session from DB
            ProjectInstanceSessionInfo sessionInfo = repoRepo.findSessionById(SESSION_ID).orElseThrow();

            // tryFinishFromHeartbeat detects stale heartbeat file and finishes session
            boolean finished = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, sessionInfo, sessionDir, HEARTBEAT_THRESHOLD, NOW);

            assertTrue(finished);

            ProjectInstanceSessionInfo updated = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertEquals(staleHeartbeat, updated.finishedAt());
        }

        @Test
        void unfinishedSession_noHeartbeatFile_finishedWithFallback(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException, IOException {

            TestUtils.executeSql(dataSource, "sql/e2e/insert-project-for-e2e.sql");

            var clock = new MutableClock(NOW);
            var repoRepo = createRepoRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var fileHeartbeatReader = new FileHeartbeatReader();
            var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

            // No heartbeat file written - directory is empty
            Path sessionDir = tempDir.resolve("session-2025-06-15");
            Files.createDirectories(sessionDir);

            ProjectInstanceSessionInfo sessionInfo = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertNull(sessionInfo.finishedAt());

            Instant fallback = Instant.parse("2025-06-15T11:00:00Z");
            boolean finished = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, sessionInfo, sessionDir, HEARTBEAT_THRESHOLD, fallback);

            assertTrue(finished);

            ProjectInstanceSessionInfo updated = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertEquals(fallback, updated.finishedAt());
        }
    }

    @Nested
    class FullPipeline {

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Test
        void heartbeatFileWritten_thenDetectedStale_thenSessionFinished(
                DataSource dataSource, @TempDir Path tempDir) throws SQLException, IOException {

            TestUtils.executeSql(dataSource, "sql/e2e/insert-project-for-e2e.sql");

            var clock = new MutableClock(NOW);
            var repoRepo = createRepoRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var fileHeartbeatReader = new FileHeartbeatReader();
            var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

            // Step 1: Write heartbeat file (simulates agent writing heartbeat)
            Path sessionDir = tempDir.resolve("session-2025-06-15");
            Instant heartbeatTime = Instant.parse("2025-06-15T11:55:00Z");
            writeHeartbeatFile(sessionDir, heartbeatTime);

            // Step 2: Verify session is not yet finished (heartbeat is fresh)
            ProjectInstanceSessionInfo beforeFinish = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertNull(beforeFinish.finishedAt());

            boolean notFinishedYet = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, beforeFinish, sessionDir, HEARTBEAT_THRESHOLD, clock.instant());
            assertFalse(notFinishedYet);

            // Step 3: Advance clock past threshold
            clock.advance(Duration.ofMinutes(10));

            // Step 4: Re-read session from DB
            ProjectInstanceSessionInfo sessionWithStaleHeartbeat = repoRepo.findSessionById(SESSION_ID).orElseThrow();

            // Step 5: SessionFinisher.tryFinishFromHeartbeat() detects stale heartbeat file
            boolean finished = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, sessionWithStaleHeartbeat, sessionDir, HEARTBEAT_THRESHOLD, clock.instant());

            assertTrue(finished);

            // Step 6: Session marked finished with heartbeat timestamp
            ProjectInstanceSessionInfo finishedSession = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertEquals(heartbeatTime, finishedSession.finishedAt());

            // Step 7: Poll queue and verify SESSION_FINISHED event
            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(WORKSPACE_ID, "test-consumer");
            assertEquals(1, entries.size());

            WorkspaceEvent event = entries.getFirst().payload();
            assertAll(
                    () -> assertEquals(PROJECT_ID, event.projectId()),
                    () -> assertEquals(WORKSPACE_ID, event.workspaceId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED, event.eventType()),
                    () -> assertEquals(SESSION_ID, event.originEventId())
            );
        }
    }
}
