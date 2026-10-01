/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.hub.core.project.session;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.hub.persistence.jdbc.JdbcHubPlatformRepositories;
import cafe.jeffrey.hub.persistence.jdbc.JdbcProjectRepositoryRepository;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.model.ProjectInfo;
import cafe.jeffrey.hub.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.MutableClock;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DuckDBTest(migration = "classpath:db/migration/hub")
@ExtendWith(MockitoExtension.class)
class SessionFinisherIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final String SESSION_ID = "session-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");

    private static final Duration HEARTBEAT_THRESHOLD = Duration.ofMinutes(5);
    private static final Path SESSION_PATH = Path.of("/workspaces/ws-001/proj-001/session-2025-06-15");

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, null, "Test Project", null,
            WORKSPACE_ID,
            Instant.parse("2025-01-01T11:00:00Z"), null, Map.of(), null);

    private static JdbcProjectRepositoryRepository createRepository(MutableClock clock, DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        return new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);
    }

    private static HubPlatformRepositories createHubPlatformRepositories(MutableClock clock, DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        return new JdbcHubPlatformRepositories(provider, clock);
    }

    private static SessionFinisher createFinisher(
            MutableClock clock, FileHeartbeatReader fileHeartbeatReader, DataSource dataSource) {
        return new SessionFinisher(clock, fileHeartbeatReader, createHubPlatformRepositories(clock, dataSource));
    }

    @Nested
    class MarkFinished {

        @Mock
        FileHeartbeatReader fileHeartbeatReader;

        @Test
        void setsFinishedAt(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
            var clock = new MutableClock(NOW);
            var repository = createRepository(clock, dataSource);
            var finisher = createFinisher(clock, fileHeartbeatReader, dataSource);

            ProjectInstanceSessionInfo sessionInfo = repository.findSessionById(SESSION_ID).orElseThrow();
            Instant finishedAt = Instant.parse("2025-06-15T11:50:00Z");

            finisher.markFinished(new SessionRef(PROJECT_INFO, sessionInfo, SESSION_PATH), finishedAt);

            ProjectInstanceSessionInfo updated = repository.findSessionById(SESSION_ID).orElseThrow();
            assertEquals(finishedAt, updated.finishedAt());
        }
    }

    @Nested
    class ForceFinish {

        @Mock
        FileHeartbeatReader fileHeartbeatReader;

        @Test
        void usesHeartbeatTimestamp_whenHeartbeatPresent(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
            var clock = new MutableClock(NOW);
            var repository = createRepository(clock, dataSource);
            var finisher = createFinisher(clock, fileHeartbeatReader, dataSource);

            Instant heartbeatTimestamp = Instant.parse("2025-06-15T11:30:00Z");
            when(fileHeartbeatReader.readFinishedMarker(SESSION_PATH))
                    .thenReturn(LivenessRead.absent());
            when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                    .thenReturn(LivenessRead.reported(heartbeatTimestamp));

            ProjectInstanceSessionInfo sessionInfo = repository.findSessionById(SESSION_ID).orElseThrow();
            Instant fallback = Instant.parse("2025-06-15T11:00:00Z");

            finisher.forceFinish(new SessionRef(PROJECT_INFO, sessionInfo, SESSION_PATH), fallback);

            ProjectInstanceSessionInfo updated = repository.findSessionById(SESSION_ID).orElseThrow();
            assertEquals(heartbeatTimestamp, updated.finishedAt());
        }

        @Test
        void prefersFinishedMarker_overHeartbeat(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
            var clock = new MutableClock(NOW);
            var repository = createRepository(clock, dataSource);
            var finisher = createFinisher(clock, fileHeartbeatReader, dataSource);

            Instant markerTimestamp = Instant.parse("2025-06-15T11:35:00Z");
            when(fileHeartbeatReader.readFinishedMarker(SESSION_PATH))
                    .thenReturn(LivenessRead.reported(markerTimestamp));

            ProjectInstanceSessionInfo sessionInfo = repository.findSessionById(SESSION_ID).orElseThrow();
            Instant fallback = Instant.parse("2025-06-15T11:00:00Z");

            finisher.forceFinish(new SessionRef(PROJECT_INFO, sessionInfo, SESSION_PATH), fallback);

            ProjectInstanceSessionInfo updated = repository.findSessionById(SESSION_ID).orElseThrow();
            assertEquals(markerTimestamp, updated.finishedAt());
        }

        @Test
        void usesFallbackTimestamp_whenNoHeartbeat(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
            var clock = new MutableClock(NOW);
            var repository = createRepository(clock, dataSource);
            var finisher = createFinisher(clock, fileHeartbeatReader, dataSource);

            when(fileHeartbeatReader.readFinishedMarker(SESSION_PATH))
                    .thenReturn(LivenessRead.absent());
            when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                    .thenReturn(LivenessRead.absent());

            ProjectInstanceSessionInfo sessionInfo = repository.findSessionById(SESSION_ID).orElseThrow();
            Instant fallback = Instant.parse("2025-06-15T11:00:00Z");

            finisher.forceFinish(new SessionRef(PROJECT_INFO, sessionInfo, SESSION_PATH), fallback);

            ProjectInstanceSessionInfo updated = repository.findSessionById(SESSION_ID).orElseThrow();
            assertEquals(fallback, updated.finishedAt());
        }
    }

    @Nested
    class TryFinishFromHeartbeat {

        @Mock
        FileHeartbeatReader fileHeartbeatReader;

        /** The fixture session, as the hub materialized it. */
        private static ProjectInstanceSessionInfo fixtureSession(JdbcProjectRepositoryRepository repository) {
            return repository.findSessionById(SESSION_ID).orElseThrow();
        }

        @Nested
        class CleanExitMarker {

            @Test
            void marksFinished_atMarkerTimestamp(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
                var clock = new MutableClock(NOW);
                var repository = createRepository(clock, dataSource);
                var finisher = createFinisher(clock, fileHeartbeatReader, dataSource);

                // A clean exit is a presence check, so even a fresh heartbeat does not hold the
                // session open — the shutdown hook wrote the marker after its last beat
                Instant markerTimestamp = NOW.minus(Duration.ofMinutes(1));
                when(fileHeartbeatReader.readFinishedMarker(SESSION_PATH))
                        .thenReturn(LivenessRead.reported(markerTimestamp));

                boolean result = finisher.tryFinishFromHeartbeat(new SessionRef(PROJECT_INFO, fixtureSession(repository), SESSION_PATH), HEARTBEAT_THRESHOLD);

                assertTrue(result);
                assertEquals(markerTimestamp, repository.findSessionById(SESSION_ID).orElseThrow().finishedAt());
            }
        }

        @Nested
        class HeartbeatFileExists {

            @Test
            void marksFinished_whenHeartbeatStale(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
                var clock = new MutableClock(NOW);
                var repository = createRepository(clock, dataSource);
                var finisher = createFinisher(clock, fileHeartbeatReader, dataSource);

                // Heartbeat file returns a stale heartbeat: 10 minutes before NOW, threshold is 5 minutes
                Instant staleHeartbeat = NOW.minus(Duration.ofMinutes(10));
                when(fileHeartbeatReader.readFinishedMarker(SESSION_PATH))
                    .thenReturn(LivenessRead.absent());
            when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                        .thenReturn(LivenessRead.reported(staleHeartbeat));

                boolean result = finisher.tryFinishFromHeartbeat(new SessionRef(PROJECT_INFO, fixtureSession(repository), SESSION_PATH), HEARTBEAT_THRESHOLD);

                assertTrue(result);

                ProjectInstanceSessionInfo updated = repository.findSessionById(SESSION_ID).orElseThrow();
                assertEquals(staleHeartbeat, updated.finishedAt());
            }

            @Test
            void doesNotFinish_whenHeartbeatFresh(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
                var clock = new MutableClock(NOW);
                var repository = createRepository(clock, dataSource);
                var finisher = createFinisher(clock, fileHeartbeatReader, dataSource);

                // Heartbeat file returns a fresh heartbeat: 2 minutes before NOW, threshold is 5 minutes
                Instant freshHeartbeat = NOW.minus(Duration.ofMinutes(2));
                when(fileHeartbeatReader.readFinishedMarker(SESSION_PATH))
                    .thenReturn(LivenessRead.absent());
            when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                        .thenReturn(LivenessRead.reported(freshHeartbeat));

                boolean result = finisher.tryFinishFromHeartbeat(new SessionRef(PROJECT_INFO, fixtureSession(repository), SESSION_PATH), HEARTBEAT_THRESHOLD);

                assertFalse(result);

                ProjectInstanceSessionInfo updated = repository.findSessionById(SESSION_ID).orElseThrow();
                assertNull(updated.finishedAt());
            }
        }

        /**
         * Nothing outside the application declares whether it reports liveness, so a session
         * that never wrote a liveness file is either still starting up or carries no library —
         * the two look the same, and finishing the second kind would end a session the profiler
         * is still writing. It is closed by the instance's next session instead.
         */
        @Nested
        class NoHeartbeatFile {

            @Test
            void doesNotFinish_whenNothingEverReported(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
                var clock = new MutableClock(NOW);
                var repository = createRepository(clock, dataSource);
                var finisher = createFinisher(clock, fileHeartbeatReader, dataSource);

                // Hours past any startup window, and still nothing written
                when(fileHeartbeatReader.readFinishedMarker(SESSION_PATH))
                        .thenReturn(LivenessRead.absent());
                when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                        .thenReturn(LivenessRead.absent());

                boolean result = finisher.tryFinishFromHeartbeat(
                        new SessionRef(PROJECT_INFO, fixtureSession(repository), SESSION_PATH), HEARTBEAT_THRESHOLD);

                assertFalse(result);
                assertNull(repository.findSessionById(SESSION_ID).orElseThrow().finishedAt());
            }

            @Test
            void isHeldToTheDeadline_onceTheFirstHeartbeatArrives(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
                var clock = new MutableClock(NOW);
                var repository = createRepository(clock, dataSource);
                var finisher = createFinisher(clock, fileHeartbeatReader, dataSource);

                Instant staleHeartbeat = NOW.minus(Duration.ofMinutes(10));
                when(fileHeartbeatReader.readFinishedMarker(SESSION_PATH))
                        .thenReturn(LivenessRead.absent());
                when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                        .thenReturn(LivenessRead.absent())
                        .thenReturn(LivenessRead.reported(staleHeartbeat));

                ProjectInstanceSessionInfo sessionInfo = fixtureSession(repository);

                assertFalse(finisher.tryFinishFromHeartbeat(new SessionRef(PROJECT_INFO, sessionInfo, SESSION_PATH), HEARTBEAT_THRESHOLD));
                assertTrue(finisher.tryFinishFromHeartbeat(new SessionRef(PROJECT_INFO, sessionInfo, SESSION_PATH), HEARTBEAT_THRESHOLD));

                assertEquals(staleHeartbeat, repository.findSessionById(SESSION_ID).orElseThrow().finishedAt());
            }
        }

        /**
         * A file that is there and cannot be read says nothing about whether the JVM is running,
         * so the session is left alone rather than finished at a guessed timestamp.
         */
        @Nested
        class UnreadableLivenessFiles {

            @Test
            void doesNotFinish_whenTheHeartbeatCannotBeRead(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
                var clock = new MutableClock(NOW);
                var repository = createRepository(clock, dataSource);
                var finisher = createFinisher(clock, fileHeartbeatReader, dataSource);

                when(fileHeartbeatReader.readFinishedMarker(SESSION_PATH))
                        .thenReturn(LivenessRead.absent());
                when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                        .thenReturn(LivenessRead.unreadable("the file could not be read"));

                boolean result = finisher.tryFinishFromHeartbeat(new SessionRef(PROJECT_INFO, fixtureSession(repository), SESSION_PATH), HEARTBEAT_THRESHOLD);

                assertFalse(result);
                assertNull(repository.findSessionById(SESSION_ID).orElseThrow().finishedAt(),
                        "An unreadable heartbeat must not be read as a session that never reported");
            }

            @Test
            void doesNotFinish_whenTheMarkerCannotBeRead(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
                var clock = new MutableClock(NOW);
                var repository = createRepository(clock, dataSource);
                var finisher = createFinisher(clock, fileHeartbeatReader, dataSource);

                // The clean-exit marker is there — the session did end — but its content is not a
                // timestamp this hub can use. Guessing one is worse than waiting for the reconciler
                when(fileHeartbeatReader.readFinishedMarker(SESSION_PATH))
                        .thenReturn(LivenessRead.unreadable("the content is not epoch millis"));
                when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                        .thenReturn(LivenessRead.absent());

                boolean result = finisher.tryFinishFromHeartbeat(new SessionRef(PROJECT_INFO, fixtureSession(repository), SESSION_PATH), HEARTBEAT_THRESHOLD);

                assertFalse(result);
                assertNull(repository.findSessionById(SESSION_ID).orElseThrow().finishedAt());
            }

            @Test
            void stillFinishes_onceTheFileBecomesReadable(DataSource dataSource) throws SQLException {
                TestUtils.executeSql(dataSource, "sql/session-finisher/insert-project-with-unfinished-session.sql");
                var clock = new MutableClock(NOW);
                var repository = createRepository(clock, dataSource);
                var finisher = createFinisher(clock, fileHeartbeatReader, dataSource);

                Instant staleHeartbeat = NOW.minus(Duration.ofMinutes(10));
                when(fileHeartbeatReader.readFinishedMarker(SESSION_PATH))
                        .thenReturn(LivenessRead.absent());
                when(fileHeartbeatReader.readLastHeartbeat(SESSION_PATH))
                        .thenReturn(LivenessRead.unreadable("the file could not be read"))
                        .thenReturn(LivenessRead.reported(staleHeartbeat));

                ProjectInstanceSessionInfo sessionInfo = fixtureSession(repository);

                assertFalse(finisher.tryFinishFromHeartbeat(new SessionRef(PROJECT_INFO, sessionInfo, SESSION_PATH), HEARTBEAT_THRESHOLD));
                assertTrue(finisher.tryFinishFromHeartbeat(new SessionRef(PROJECT_INFO, sessionInfo, SESSION_PATH), HEARTBEAT_THRESHOLD));

                assertEquals(staleHeartbeat, repository.findSessionById(SESSION_ID).orElseThrow().finishedAt());
            }
        }
    }
}
