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

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.provider.platform.repository.JdbcAlertRepository;
import pbouda.jeffrey.provider.platform.repository.JdbcMessageRepository;
import pbouda.jeffrey.shared.common.model.ImportantMessage;
import pbouda.jeffrey.shared.common.model.Severity;
import pbouda.jeffrey.shared.common.model.time.AbsoluteTimeRange;
import pbouda.jeffrey.shared.common.model.time.RelativeTimeRange;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
class MessagesManagerImplTest {

    private static final Instant T1 = Instant.parse("2025-06-01T10:00:00Z");
    private static final Instant T2 = Instant.parse("2025-06-01T11:00:00Z");
    private static final Instant T3 = Instant.parse("2025-06-01T12:00:00Z");

    private static final Instant NOW = Instant.parse("2025-06-01T15:00:00Z");

    private static ImportantMessage message(String type, String sessionId, Instant createdAt) {
        return new ImportantMessage(type, "Title", "Message text", Severity.MEDIUM, "PERFORMANCE", "test", false, sessionId, createdAt);
    }

    private static ImportantMessage alert(String type, String sessionId, Instant createdAt) {
        return new ImportantMessage(type, "Alert Title", "Alert text", Severity.HIGH, "RESOURCE", "monitor", true, sessionId, createdAt);
    }

    @Nested
    class GetMessages {

        @Test
        void returnsMessages_forAbsoluteTimeRange(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var messageRepo = new JdbcMessageRepository("proj-001", provider);
            var alertRepo = new JdbcAlertRepository("proj-001", provider);
            var clock = Clock.fixed(NOW, ZoneOffset.UTC);
            var manager = new MessagesManagerImpl(clock, messageRepo, alertRepo);

            messageRepo.insert(message("TYPE_A", "session-1", T1));
            messageRepo.insert(message("TYPE_B", "session-1", T2));
            messageRepo.insert(message("TYPE_C", "session-1", T3));

            List<ImportantMessage> result = manager.getMessages(new AbsoluteTimeRange(T1, T3));

            assertEquals(3, result.size());
            assertEquals("TYPE_C", result.get(0).type());
            assertEquals("TYPE_B", result.get(1).type());
            assertEquals("TYPE_A", result.get(2).type());
        }

        @Test
        void returnsMessages_forRelativeTimeRange(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var messageRepo = new JdbcMessageRepository("proj-001", provider);
            var alertRepo = new JdbcAlertRepository("proj-001", provider);
            var clock = Clock.fixed(NOW, ZoneOffset.UTC);
            var manager = new MessagesManagerImpl(clock, messageRepo, alertRepo);

            // NOW = 2025-06-01T15:00:00Z
            // Messages at NOW-2h, NOW-1h, NOW-30min
            Instant msgAt2hAgo = NOW.minus(Duration.ofHours(2));   // 13:00
            Instant msgAt1hAgo = NOW.minus(Duration.ofHours(1));   // 14:00
            Instant msgAt30mAgo = NOW.minus(Duration.ofMinutes(30)); // 14:30

            messageRepo.insert(message("TYPE_A", "session-1", msgAt2hAgo));
            messageRepo.insert(message("TYPE_B", "session-1", msgAt1hAgo));
            messageRepo.insert(message("TYPE_C", "session-1", msgAt30mAgo));

            // RelativeTimeRange(ZERO, 3h) => duration = 3h => range = [NOW-3h, NOW]
            List<ImportantMessage> result = manager.getMessages(
                    new RelativeTimeRange(Duration.ZERO, Duration.ofHours(3)));

            assertEquals(3, result.size());
        }

        @Test
        void relativeTimeRange_usesClockNotSystemTime(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var messageRepo = new JdbcMessageRepository("proj-001", provider);
            var alertRepo = new JdbcAlertRepository("proj-001", provider);

            // Fix clock at a specific time far from real system time (year 2020)
            Instant fixedTime = Instant.parse("2020-01-15T12:00:00Z");
            var clock = Clock.fixed(fixedTime, ZoneOffset.UTC);
            var manager = new MessagesManagerImpl(clock, messageRepo, alertRepo);

            // Insert messages near the fixed clock time
            Instant nearFixedTime = fixedTime.minus(Duration.ofMinutes(30));
            messageRepo.insert(message("TYPE_NEAR", "session-1", nearFixedTime));

            // Insert message near the real system time (should NOT be found)
            messageRepo.insert(message("TYPE_FAR", "session-2", Instant.now()));

            // Query with relative range of 1h from clock time
            // Should resolve to [fixedTime-1h, fixedTime] = [2020-01-15T11:00:00Z, 2020-01-15T12:00:00Z]
            List<ImportantMessage> result = manager.getMessages(
                    new RelativeTimeRange(Duration.ZERO, Duration.ofHours(1)));

            assertEquals(1, result.size());
            assertEquals("TYPE_NEAR", result.getFirst().type());
        }

        @Test
        void returnsEmpty_whenNoMessages(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var messageRepo = new JdbcMessageRepository("proj-001", provider);
            var alertRepo = new JdbcAlertRepository("proj-001", provider);
            var clock = Clock.fixed(NOW, ZoneOffset.UTC);
            var manager = new MessagesManagerImpl(clock, messageRepo, alertRepo);

            List<ImportantMessage> result = manager.getMessages(new AbsoluteTimeRange(T1, T3));

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    class GetAlerts {

        @Test
        void returnsOnlyAlerts_notMessages(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var messageRepo = new JdbcMessageRepository("proj-001", provider);
            var alertRepo = new JdbcAlertRepository("proj-001", provider);
            var clock = Clock.fixed(NOW, ZoneOffset.UTC);
            var manager = new MessagesManagerImpl(clock, messageRepo, alertRepo);

            // Insert messages via messageRepo
            messageRepo.insert(message("MSG_TYPE", "session-1", T1));
            messageRepo.insert(message("MSG_TYPE_2", "session-1", T2));

            // Insert alerts via alertRepo
            alertRepo.insert(alert("ALERT_TYPE", "session-1", T1));
            alertRepo.insert(alert("ALERT_TYPE_2", "session-1", T2));

            List<ImportantMessage> alerts = manager.getAlerts(new AbsoluteTimeRange(T1, T3));

            assertEquals(2, alerts.size());
            assertTrue(alerts.stream().allMatch(ImportantMessage::isAlert));
            assertEquals("ALERT_TYPE_2", alerts.get(0).type());
            assertEquals("ALERT_TYPE", alerts.get(1).type());
        }

        @Test
        void returnsAlerts_forRelativeTimeRange(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/messages/insert-project-for-messages.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var messageRepo = new JdbcMessageRepository("proj-001", provider);
            var alertRepo = new JdbcAlertRepository("proj-001", provider);
            var clock = Clock.fixed(NOW, ZoneOffset.UTC);
            var manager = new MessagesManagerImpl(clock, messageRepo, alertRepo);

            // NOW = 2025-06-01T15:00:00Z
            Instant alertAt1hAgo = NOW.minus(Duration.ofHours(1));   // 14:00
            Instant alertAt30mAgo = NOW.minus(Duration.ofMinutes(30)); // 14:30

            alertRepo.insert(alert("ALERT_A", "session-1", alertAt1hAgo));
            alertRepo.insert(alert("ALERT_B", "session-1", alertAt30mAgo));

            // RelativeTimeRange(ZERO, 2h) => duration = 2h => range = [NOW-2h, NOW]
            List<ImportantMessage> result = manager.getAlerts(
                    new RelativeTimeRange(Duration.ZERO, Duration.ofHours(2)));

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(ImportantMessage::isAlert));
            assertEquals("ALERT_B", result.get(0).type());
            assertEquals("ALERT_A", result.get(1).type());
        }
    }
}
