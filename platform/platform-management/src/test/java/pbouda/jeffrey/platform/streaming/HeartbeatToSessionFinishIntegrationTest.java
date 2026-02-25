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

package pbouda.jeffrey.platform.streaming;

import jdk.jfr.consumer.RecordedEvent;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.project.repository.SessionFinishEventEmitter;
import pbouda.jeffrey.platform.queue.DuckDBPersistentQueue;
import pbouda.jeffrey.platform.queue.QueueEntry;
import pbouda.jeffrey.platform.workspace.WorkspaceEventSerializer;
import pbouda.jeffrey.provider.platform.repository.JdbcProjectInstanceRepository;
import pbouda.jeffrey.provider.platform.repository.JdbcProjectRepositoryRepository;
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
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
    private static final Path SESSION_PATH = Path.of("/workspaces/ws-001/proj-001/session-2025-06-15");

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

    @Nested
    class GracefulShutdownScenario {

        @Mock
        RecordedEvent mockEvent;

        @Mock
        HeartbeatReplayReader heartbeatReplayReader;

        @Test
        void heartbeatsArrive_thenStop_sessionFinishedByPolling(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/e2e/insert-project-for-e2e.sql");

            var clock = new MutableClock(NOW);
            var repoRepo = createRepoRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var finisher = new SessionFinisher(clock, emitter, heartbeatReplayReader);

            // Step 1: Send a heartbeat via JfrHeartbeatHandler
            var handler = new JfrHeartbeatHandler(SESSION_ID, repoRepo, clock, Duration.ofMinutes(5), Duration.ofMinutes(5), false);

            Instant heartbeatTime = Instant.parse("2025-06-15T11:50:00Z");
            when(mockEvent.getStartTime()).thenReturn(heartbeatTime);
            handler.onEvent(mockEvent);

            // Step 2: Verify heartbeat persisted to DB
            ProjectInstanceSessionInfo sessionAfterHeartbeat = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertEquals(heartbeatTime, sessionAfterHeartbeat.lastHeartbeatAt());

            // Step 3: Advance clock past threshold (heartbeat is now stale)
            clock.advance(Duration.ofMinutes(10));

            // Step 4: Re-read the session from DB to get the updated session info with heartbeat
            ProjectInstanceSessionInfo sessionWithHeartbeat = repoRepo.findSessionById(SESSION_ID).orElseThrow();

            // Step 5: SessionFinisher detects stale heartbeat and marks session finished
            boolean finished = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, sessionWithHeartbeat, SESSION_PATH, HEARTBEAT_THRESHOLD, clock.instant());

            assertTrue(finished);

            // Step 6: Verify session is finished with heartbeat timestamp
            ProjectInstanceSessionInfo finishedSession = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertAll(
                    () -> assertEquals(heartbeatTime, finishedSession.finishedAt()),
                    () -> assertEquals(heartbeatTime, finishedSession.lastHeartbeatAt())
            );

            // Step 7: Verify SESSION_FINISHED event in queue
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
        RecordedEvent mockEvent;

        @Mock
        HeartbeatReplayReader heartbeatReplayReader;

        @Test
        void firstSession_finishes_instanceStaysActive_secondSessionStillRunning(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/e2e/insert-project-for-e2e-multi-session.sql");

            var clock = new MutableClock(NOW);
            var repoRepo = createRepoRepository(clock, dataSource);
            var instanceRepo = createInstanceRepository(dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var finisher = new SessionFinisher(clock, emitter, heartbeatReplayReader);

            // Send a heartbeat to session-001, making it stale
            var handler = new JfrHeartbeatHandler(SESSION_ID, repoRepo, clock, Duration.ofMinutes(5), Duration.ofMinutes(5), false);

            Instant heartbeatTime = Instant.parse("2025-06-15T11:50:00Z");
            when(mockEvent.getStartTime()).thenReturn(heartbeatTime);
            handler.onEvent(mockEvent);

            // Advance clock past threshold
            clock.advance(Duration.ofMinutes(10));

            // Finish session-001 only
            ProjectInstanceSessionInfo session1 = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            boolean finished = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, session1, SESSION_PATH, HEARTBEAT_THRESHOLD, clock.instant());

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
        void bothSessions_finish_instanceAutoFinishes(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/e2e/insert-project-for-e2e-multi-session.sql");

            var clock = new MutableClock(NOW);
            var repoRepo = createRepoRepository(clock, dataSource);
            var instanceRepo = createInstanceRepository(dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var finisher = new SessionFinisher(clock, emitter, heartbeatReplayReader);

            // Send heartbeats to both sessions
            Instant heartbeat1 = Instant.parse("2025-06-15T11:50:00Z");
            Instant heartbeat2 = Instant.parse("2025-06-15T11:51:00Z");

            var handler1 = new JfrHeartbeatHandler(SESSION_ID, repoRepo, clock, Duration.ofMinutes(5), Duration.ofMinutes(5), false);
            when(mockEvent.getStartTime()).thenReturn(heartbeat1);
            handler1.onEvent(mockEvent);

            var handler2 = new JfrHeartbeatHandler(SESSION_ID_2, repoRepo, clock, Duration.ofMinutes(5), Duration.ofMinutes(5), false);
            when(mockEvent.getStartTime()).thenReturn(heartbeat2);
            handler2.onEvent(mockEvent);

            // Advance clock past threshold for both
            clock.advance(Duration.ofMinutes(10));

            // Finish session-001
            ProjectInstanceSessionInfo session1 = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            boolean finished1 = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, session1, SESSION_PATH, HEARTBEAT_THRESHOLD, clock.instant());
            assertTrue(finished1);

            // Finish session-002
            Path session2Path = Path.of("/workspaces/ws-001/proj-001/session-002");
            ProjectInstanceSessionInfo session2 = repoRepo.findSessionById(SESSION_ID_2).orElseThrow();
            boolean finished2 = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, session2, session2Path, HEARTBEAT_THRESHOLD, clock.instant());
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
        HeartbeatReplayReader heartbeatReplayReader;

        @Test
        void unfinishedSession_withStaleHeartbeat_finishedOnRestart(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/e2e/insert-project-for-e2e.sql");

            var clock = new MutableClock(NOW);
            var repoRepo = createRepoRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var finisher = new SessionFinisher(clock, emitter, heartbeatReplayReader);

            // Simulate: session has a heartbeat in DB (set directly), but it is stale
            Instant staleHeartbeat = NOW.minus(Duration.ofMinutes(10));
            repoRepo.updateLastHeartbeat(SESSION_ID, staleHeartbeat);

            // Read session with the stale heartbeat
            ProjectInstanceSessionInfo sessionInfo = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertEquals(staleHeartbeat, sessionInfo.lastHeartbeatAt());

            // tryFinishFromHeartbeat detects stale heartbeat and finishes session
            boolean finished = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, sessionInfo, SESSION_PATH, HEARTBEAT_THRESHOLD, NOW);

            assertTrue(finished);

            ProjectInstanceSessionInfo updated = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertAll(
                    () -> assertEquals(staleHeartbeat, updated.finishedAt()),
                    () -> assertEquals(staleHeartbeat, updated.lastHeartbeatAt())
            );
        }

        @Test
        void unfinishedSession_replayFindsHeartbeat_finishedFromReplay(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/e2e/insert-project-for-e2e.sql");

            var clock = new MutableClock(NOW);
            var repoRepo = createRepoRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var finisher = new SessionFinisher(clock, emitter, heartbeatReplayReader);

            // No heartbeat in DB (fixture default). Replay reader finds one.
            Instant replayedHeartbeat = NOW.minus(Duration.ofMinutes(10));
            when(heartbeatReplayReader.readLastHeartbeat(any(), any()))
                    .thenReturn(Optional.of(replayedHeartbeat));

            ProjectInstanceSessionInfo sessionInfo = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertNull(sessionInfo.lastHeartbeatAt());

            boolean finished = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, sessionInfo, SESSION_PATH, HEARTBEAT_THRESHOLD, NOW);

            assertTrue(finished);

            ProjectInstanceSessionInfo updated = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertAll(
                    () -> assertEquals(replayedHeartbeat, updated.finishedAt()),
                    () -> assertEquals(replayedHeartbeat, updated.lastHeartbeatAt())
            );
        }

        @Test
        void unfinishedSession_noHeartbeatAnywhere_finishedWithFallback(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/e2e/insert-project-for-e2e.sql");

            var clock = new MutableClock(NOW);
            var repoRepo = createRepoRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var finisher = new SessionFinisher(clock, emitter, heartbeatReplayReader);

            // No heartbeat in DB. Replay returns empty.
            when(heartbeatReplayReader.readLastHeartbeat(any(), any()))
                    .thenReturn(Optional.empty());

            ProjectInstanceSessionInfo sessionInfo = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertNull(sessionInfo.lastHeartbeatAt());

            Instant fallback = Instant.parse("2025-06-15T11:00:00Z");
            boolean finished = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, sessionInfo, SESSION_PATH, HEARTBEAT_THRESHOLD, fallback);

            assertTrue(finished);

            ProjectInstanceSessionInfo updated = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertAll(
                    () -> assertEquals(fallback, updated.finishedAt()),
                    () -> assertNull(updated.lastHeartbeatAt())
            );
        }
    }

    @Nested
    class FullPipeline_HeartbeatToFinish {

        @Mock
        RecordedEvent mockEvent;

        @Mock
        HeartbeatReplayReader heartbeatReplayReader;

        @Test
        void singleHeartbeat_persisted_thenDetectedStale_thenSessionFinished(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/e2e/insert-project-for-e2e.sql");

            var clock = new MutableClock(NOW);
            var repoRepo = createRepoRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var finisher = new SessionFinisher(clock, emitter, heartbeatReplayReader);

            // Step 1: JfrHeartbeatHandler.onEvent() persists heartbeat
            var handler = new JfrHeartbeatHandler(SESSION_ID, repoRepo, clock, Duration.ofMinutes(5), Duration.ofMinutes(5), false);

            Instant heartbeatTime = Instant.parse("2025-06-15T11:55:00Z");
            when(mockEvent.getStartTime()).thenReturn(heartbeatTime);
            handler.onEvent(mockEvent);

            // Verify heartbeat was persisted
            ProjectInstanceSessionInfo afterHeartbeat = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertAll(
                    () -> assertEquals(heartbeatTime, afterHeartbeat.lastHeartbeatAt()),
                    () -> assertNull(afterHeartbeat.finishedAt())
            );

            // Step 2: Advance clock past threshold
            clock.advance(Duration.ofMinutes(10));

            // Step 3: Re-read session from DB to get updated session info with heartbeat
            ProjectInstanceSessionInfo sessionWithHeartbeat = repoRepo.findSessionById(SESSION_ID).orElseThrow();

            // Step 4: SessionFinisher.tryFinishFromHeartbeat() detects stale
            boolean finished = finisher.tryFinishFromHeartbeat(
                    repoRepo, PROJECT_INFO, sessionWithHeartbeat, SESSION_PATH, HEARTBEAT_THRESHOLD, clock.instant());

            assertTrue(finished);

            // Step 5: Session marked finished
            ProjectInstanceSessionInfo finishedSession = repoRepo.findSessionById(SESSION_ID).orElseThrow();
            assertAll(
                    () -> assertEquals(heartbeatTime, finishedSession.finishedAt()),
                    () -> assertEquals(heartbeatTime, finishedSession.lastHeartbeatAt())
            );

            // Step 6: Poll queue and verify SESSION_FINISHED event
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
