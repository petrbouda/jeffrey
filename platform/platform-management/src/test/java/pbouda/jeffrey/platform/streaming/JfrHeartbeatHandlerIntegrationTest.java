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
import pbouda.jeffrey.provider.platform.repository.JdbcProjectRepositoryRepository;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.MutableClock;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
@ExtendWith(MockitoExtension.class)
class JfrHeartbeatHandlerIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String SESSION_ID = "session-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");

    @Nested
    class HeartbeatPersistence {

        @Mock
        RecordedEvent mockEvent;

        @Test
        void onEvent_persistsHeartbeatTimestampToDb(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/heartbeat/insert-project-with-session.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var clock = new MutableClock(NOW);
            var repository = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

            var handler = new JfrHeartbeatHandler(SESSION_ID, repository, clock, Duration.ofMinutes(5), false);

            Instant heartbeatTime = Instant.parse("2025-06-15T11:50:00Z");
            when(mockEvent.getStartTime()).thenReturn(heartbeatTime);

            handler.onEvent(mockEvent);

            Optional<ProjectInstanceSessionInfo> session = repository.findSessionById(SESSION_ID);
            assertTrue(session.isPresent());
            assertEquals(heartbeatTime, session.get().lastHeartbeatAt());
        }

        @Test
        void onEvent_updatesHeartbeatOnSubsequentEvents(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/heartbeat/insert-project-with-session.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var clock = new MutableClock(NOW);
            var repository = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

            var handler = new JfrHeartbeatHandler(SESSION_ID, repository, clock, Duration.ofMinutes(5), false);

            Instant first = Instant.parse("2025-06-15T11:50:00Z");
            Instant second = Instant.parse("2025-06-15T11:55:00Z");

            when(mockEvent.getStartTime()).thenReturn(first);
            handler.onEvent(mockEvent);

            when(mockEvent.getStartTime()).thenReturn(second);
            handler.onEvent(mockEvent);

            Optional<ProjectInstanceSessionInfo> session = repository.findSessionById(SESSION_ID);
            assertTrue(session.isPresent());
            assertEquals(second, session.get().lastHeartbeatAt());
        }
    }

    @Nested
    class WatchdogTimeout {

        @Mock
        RecordedEvent mockEvent;

        @Test
        void watchdog_callsStreamCloser_whenHeartbeatTimesOut(DataSource dataSource) throws Exception {
            TestUtils.executeSql(dataSource, "sql/heartbeat/insert-project-with-session.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var clock = new MutableClock(NOW);
            var repository = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

            // Use a short timeout so the watchdog fires quickly
            var handler = new JfrHeartbeatHandler(SESSION_ID, repository, clock, Duration.ofMillis(50), false);

            // Send one heartbeat
            when(mockEvent.getStartTime()).thenReturn(NOW);
            handler.onEvent(mockEvent);

            // Advance clock well past the timeout
            clock.advance(Duration.ofSeconds(1));

            CountDownLatch closedLatch = new CountDownLatch(1);
            handler.initialize(closedLatch::countDown);

            // Wait for the watchdog to fire
            assertTrue(closedLatch.await(2, TimeUnit.SECONDS), "Stream closer should have been called");
            handler.close();
        }

        @Test
        void watchdog_doesNotCallStreamCloser_whenHeartbeatsArriveRegularly(DataSource dataSource) throws Exception {
            TestUtils.executeSql(dataSource, "sql/heartbeat/insert-project-with-session.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var clock = new MutableClock(NOW);
            var repository = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

            var handler = new JfrHeartbeatHandler(SESSION_ID, repository, clock, Duration.ofMillis(200), false);

            // Send heartbeat right at clock time
            when(mockEvent.getStartTime()).thenReturn(NOW);
            handler.onEvent(mockEvent);

            CountDownLatch closedLatch = new CountDownLatch(1);
            handler.initialize(closedLatch::countDown);

            // Don't advance clock, so heartbeat is still fresh
            assertFalse(closedLatch.await(300, TimeUnit.MILLISECONDS), "Stream closer should NOT have been called");
            handler.close();
        }
    }

    @Nested
    class RequireInitialHeartbeat {

        @Test
        void watchdog_closesStream_whenNoInitialHeartbeatReceived(DataSource dataSource) throws Exception {
            TestUtils.executeSql(dataSource, "sql/heartbeat/insert-project-with-session.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var clock = new MutableClock(NOW);
            var repository = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

            // requireInitialHeartbeat=true, no heartbeat ever sent
            var handler = new JfrHeartbeatHandler(SESSION_ID, repository, clock, Duration.ofMillis(50), true);

            CountDownLatch closedLatch = new CountDownLatch(1);
            handler.initialize(closedLatch::countDown);

            assertTrue(closedLatch.await(2, TimeUnit.SECONDS), "Stream closer should have been called for missing initial heartbeat");
            handler.close();
        }

        @Test
        void watchdog_doesNotClose_whenInitialHeartbeatNotRequired(DataSource dataSource) throws Exception {
            TestUtils.executeSql(dataSource, "sql/heartbeat/insert-project-with-session.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var clock = new MutableClock(NOW);
            var repository = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

            // requireInitialHeartbeat=false, no heartbeat sent
            var handler = new JfrHeartbeatHandler(SESSION_ID, repository, clock, Duration.ofMillis(100), false);

            CountDownLatch closedLatch = new CountDownLatch(1);
            handler.initialize(closedLatch::countDown);

            assertFalse(closedLatch.await(300, TimeUnit.MILLISECONDS), "Stream closer should NOT have been called");
            handler.close();
        }
    }

    @Nested
    class HandlerLifecycle {

        @Test
        void close_cancelsWatchdogTimer(DataSource dataSource) throws Exception {
            TestUtils.executeSql(dataSource, "sql/heartbeat/insert-project-with-session.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var clock = new MutableClock(NOW);
            var repository = new JdbcProjectRepositoryRepository(clock, PROJECT_ID, provider);

            var handler = new JfrHeartbeatHandler(SESSION_ID, repository, clock, Duration.ofMillis(50), true);

            CountDownLatch closedLatch = new CountDownLatch(1);
            handler.initialize(closedLatch::countDown);

            // Close immediately before the watchdog fires
            handler.close();

            // The watchdog should not fire after close
            assertFalse(closedLatch.await(300, TimeUnit.MILLISECONDS), "Watchdog should not fire after close");
        }
    }
}
