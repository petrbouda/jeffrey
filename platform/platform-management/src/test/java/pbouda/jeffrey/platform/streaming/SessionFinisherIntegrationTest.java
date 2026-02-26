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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.project.repository.SessionFinishEventEmitter;
import pbouda.jeffrey.platform.queue.DuckDBPersistentQueue;
import pbouda.jeffrey.platform.queue.QueueEntry;
import pbouda.jeffrey.platform.workspace.WorkspaceEventSerializer;
import pbouda.jeffrey.provider.platform.repository.JdbcProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
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
import static org.mockito.Mockito.when;

@DuckDBTest(migration = "classpath:db/migration/platform")
@ExtendWith(MockitoExtension.class)
class SessionFinisherIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final String SESSION_ID = "session-001";
    private static final String INSTANCE_ID = "inst-001";

    private static final Instant SESSION_ORIGIN_CREATED_AT = Instant.parse("2025-06-15T08:00:00Z");
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");

    private static final Duration HEARTBEAT_THRESHOLD = Duration.ofMinutes(5);
    private static final Path SESSION_PATH = Path.of("/workspaces/ws-001/proj-001/session-2025-06-15");

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, null, "Test Project", "Label 1", null,
            WORKSPACE_ID, WorkspaceType.LIVE,
            Instant.parse("2025-01-01T11:00:00Z"), null, Map.of());

    private static JdbcProjectRepositoryRepository createRepository(MutableClock clock, DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        return new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);
    }

    private static DuckDBPersistentQueue<WorkspaceEvent> createQueue(MutableClock clock, DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        return new DuckDBPersistentQueue<>(provider, "workspace-events", new WorkspaceEventSerializer(), clock);
    }

    @Nested
    class MarkFinished {

        @Mock
        FileHeartbeatReader fileHeartbeatReader;

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Test
        void setsFinishedAt(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
            var clock = new MutableClock(NOW);
            var repository = createRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

            ProjectInstanceSessionInfo sessionInfo = repository.findSessionById(SESSION_ID).orElseThrow();
            Instant finishedAt = Instant.parse("2025-06-15T11:50:00Z");

            finisher.markFinished(repository, PROJECT_INFO, sessionInfo, finishedAt);

            ProjectInstanceSessionInfo updated = repository.findSessionById(SESSION_ID).orElseThrow();
            assertEquals(finishedAt, updated.finishedAt());
        }

        @Test
        void emitsSessionFinishedEvent_toQueue(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
            var clock = new MutableClock(NOW);
            var repository = createRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

            ProjectInstanceSessionInfo sessionInfo = repository.findSessionById(SESSION_ID).orElseThrow();
            Instant finishedAt = Instant.parse("2025-06-15T11:50:00Z");

            finisher.markFinished(repository, PROJECT_INFO, sessionInfo, finishedAt);

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
    class ForceFinish {

        @Mock
        FileHeartbeatReader fileHeartbeatReader;

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Test
        void usesHeartbeatTimestamp_whenHeartbeatPresent(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
            var clock = new MutableClock(NOW);
            var repository = createRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

            Instant heartbeatTimestamp = Instant.parse("2025-06-15T11:30:00Z");
            when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                    .thenReturn(Optional.of(heartbeatTimestamp));

            ProjectInstanceSessionInfo sessionInfo = repository.findSessionById(SESSION_ID).orElseThrow();
            Instant fallback = Instant.parse("2025-06-15T11:00:00Z");

            finisher.forceFinish(repository, PROJECT_INFO, sessionInfo, SESSION_PATH, fallback);

            ProjectInstanceSessionInfo updated = repository.findSessionById(SESSION_ID).orElseThrow();
            assertEquals(heartbeatTimestamp, updated.finishedAt());
        }

        @Test
        void usesFallbackTimestamp_whenNoHeartbeat(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
            var clock = new MutableClock(NOW);
            var repository = createRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

            when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                    .thenReturn(Optional.empty());

            ProjectInstanceSessionInfo sessionInfo = repository.findSessionById(SESSION_ID).orElseThrow();
            Instant fallback = Instant.parse("2025-06-15T11:00:00Z");

            finisher.forceFinish(repository, PROJECT_INFO, sessionInfo, SESSION_PATH, fallback);

            ProjectInstanceSessionInfo updated = repository.findSessionById(SESSION_ID).orElseThrow();
            assertEquals(fallback, updated.finishedAt());
        }
    }

    @Nested
    class TryFinishFromHeartbeat {

        @Mock
        FileHeartbeatReader fileHeartbeatReader;

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Nested
        class HeartbeatFileExists {

            @Test
            void marksFinished_whenHeartbeatStale(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
                var clock = new MutableClock(NOW);
                var repository = createRepository(clock, dataSource);
                var queue = createQueue(clock, dataSource);
                var emitter = new SessionFinishEventEmitter(clock, queue);
                var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

                // Heartbeat file returns a stale heartbeat: 10 minutes before NOW, threshold is 5 minutes
                Instant staleHeartbeat = NOW.minus(Duration.ofMinutes(10));
                when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                        .thenReturn(Optional.of(staleHeartbeat));

                ProjectInstanceSessionInfo sessionInfo = repository.findSessionById(SESSION_ID).orElseThrow();

                boolean result = finisher.tryFinishFromHeartbeat(
                        repository, PROJECT_INFO, sessionInfo, SESSION_PATH, HEARTBEAT_THRESHOLD, NOW);

                assertTrue(result);

                ProjectInstanceSessionInfo updated = repository.findSessionById(SESSION_ID).orElseThrow();
                assertEquals(staleHeartbeat, updated.finishedAt());
            }

            @Test
            void doesNotFinish_whenHeartbeatFresh(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
                var clock = new MutableClock(NOW);
                var repository = createRepository(clock, dataSource);
                var queue = createQueue(clock, dataSource);
                var emitter = new SessionFinishEventEmitter(clock, queue);
                var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

                // Heartbeat file returns a fresh heartbeat: 2 minutes before NOW, threshold is 5 minutes
                Instant freshHeartbeat = NOW.minus(Duration.ofMinutes(2));
                when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                        .thenReturn(Optional.of(freshHeartbeat));

                ProjectInstanceSessionInfo sessionInfo = repository.findSessionById(SESSION_ID).orElseThrow();

                boolean result = finisher.tryFinishFromHeartbeat(
                        repository, PROJECT_INFO, sessionInfo, SESSION_PATH, HEARTBEAT_THRESHOLD, NOW);

                assertFalse(result);

                ProjectInstanceSessionInfo updated = repository.findSessionById(SESSION_ID).orElseThrow();
                assertNull(updated.finishedAt());
            }
        }

        @Nested
        class NoHeartbeatFile {

            @Test
            void marksFinished_withFallbackTimestamp(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
                var clock = new MutableClock(NOW);
                var repository = createRepository(clock, dataSource);
                var queue = createQueue(clock, dataSource);
                var emitter = new SessionFinishEventEmitter(clock, queue);
                var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

                // Heartbeat file does not exist
                when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                        .thenReturn(Optional.empty());

                ProjectInstanceSessionInfo sessionInfo = repository.findSessionById(SESSION_ID).orElseThrow();
                Instant fallback = Instant.parse("2025-06-15T11:00:00Z");

                boolean result = finisher.tryFinishFromHeartbeat(
                        repository, PROJECT_INFO, sessionInfo, SESSION_PATH, HEARTBEAT_THRESHOLD, fallback);

                assertTrue(result);

                ProjectInstanceSessionInfo updated = repository.findSessionById(SESSION_ID).orElseThrow();
                assertEquals(fallback, updated.finishedAt());
            }
        }

        @Nested
        class SessionTooYoung {

            @Test
            void doesNotFinish_whenCreatedWithinThreshold(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
                // Set clock close to session origin time so it falls within the threshold.
                // Session originCreatedAt is 2025-06-15T08:00:00Z.
                // Use a large threshold (5 hours) so that NOW (08:00 + 4h = 12:00) is within the window.
                var clock = new MutableClock(NOW);
                var repository = createRepository(clock, dataSource);
                var queue = createQueue(clock, dataSource);
                var emitter = new SessionFinishEventEmitter(clock, queue);
                var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

                // No heartbeat file
                when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                        .thenReturn(Optional.empty());

                ProjectInstanceSessionInfo sessionInfo = repository.findSessionById(SESSION_ID).orElseThrow();

                // originCreatedAt = 08:00, NOW = 12:00, threshold = 5h
                // originCreatedAt.isAfter(NOW - 5h) => 08:00.isAfter(07:00) => true => session too young
                boolean result = finisher.tryFinishFromHeartbeat(
                        repository, PROJECT_INFO, sessionInfo, SESSION_PATH,
                        Duration.ofHours(5), NOW);

                assertFalse(result);

                ProjectInstanceSessionInfo updated = repository.findSessionById(SESSION_ID).orElseThrow();
                assertNull(updated.finishedAt());
            }
        }
    }

    @Nested
    class EventEmission {

        @Mock
        FileHeartbeatReader fileHeartbeatReader;

        @Mock
        JfrStreamingConsumerManager streamingConsumerManager;

        @Test
        void emitsSessionFinished_toWorkspaceQueue(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
            var clock = new MutableClock(NOW);
            var repository = createRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

            // Heartbeat file returns a stale heartbeat so tryFinishFromHeartbeat marks it finished and emits event
            Instant staleHeartbeat = NOW.minus(Duration.ofMinutes(10));
            when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                    .thenReturn(Optional.of(staleHeartbeat));

            ProjectInstanceSessionInfo sessionInfo = repository.findSessionById(SESSION_ID).orElseThrow();

            boolean result = finisher.tryFinishFromHeartbeat(
                    repository, PROJECT_INFO, sessionInfo, SESSION_PATH, HEARTBEAT_THRESHOLD, NOW);

            assertTrue(result);

            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(WORKSPACE_ID, "test-consumer");
            assertEquals(1, entries.size());
            assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED, entries.getFirst().payload().eventType());
        }

        @Test
        void eventContains_correctProjectAndSessionIds(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
            var clock = new MutableClock(NOW);
            var repository = createRepository(clock, dataSource);
            var queue = createQueue(clock, dataSource);
            var emitter = new SessionFinishEventEmitter(clock, queue);
            var finisher = new SessionFinisher(clock, emitter, fileHeartbeatReader, streamingConsumerManager);

            ProjectInstanceSessionInfo sessionInfo = repository.findSessionById(SESSION_ID).orElseThrow();
            Instant finishedAt = Instant.parse("2025-06-15T11:50:00Z");

            finisher.markFinished(repository, PROJECT_INFO, sessionInfo, finishedAt);

            List<QueueEntry<WorkspaceEvent>> entries = queue.poll(WORKSPACE_ID, "test-consumer");
            assertEquals(1, entries.size());

            WorkspaceEvent event = entries.getFirst().payload();
            assertAll(
                    () -> assertEquals(PROJECT_ID, event.projectId()),
                    () -> assertEquals(WORKSPACE_ID, event.workspaceId()),
                    () -> assertEquals(SESSION_ID, event.originEventId()),
                    () -> assertEquals(WorkspaceEventType.PROJECT_INSTANCE_SESSION_FINISHED, event.eventType()),
                    () -> assertEquals(NOW, event.originCreatedAt()),
                    () -> assertEquals(NOW, event.createdAt())
            );
        }
    }
}
