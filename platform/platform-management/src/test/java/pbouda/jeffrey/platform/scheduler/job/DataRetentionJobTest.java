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

package pbouda.jeffrey.platform.scheduler.job;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import pbouda.jeffrey.platform.queue.DuckDBPersistentQueue;
import pbouda.jeffrey.platform.queue.EventSerializer;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.provider.platform.JdbcPlatformRepositories;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DuckDBTest(migration = "classpath:db/migration/platform")
class DataRetentionJobTest {

    // Fixed time: 2025-06-30. Old data is from 2025-05-01/05-05 (>31 days), recent from 2025-06-25 (<31 days)
    private static final Instant FIXED_TIME = Instant.parse("2025-06-30T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
    private static final Duration RETENTION = Duration.ofDays(31);
    private static final Duration PERIOD = Duration.ofDays(1);

    private static DataRetentionJob createJob(DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);
        var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);

        var queue = new DuckDBPersistentQueue<>(provider, "test-queue",
                new EventSerializer<String>() {
                    @Override
                    public String serialize(String event) {
                        return event;
                    }

                    @Override
                    public String deserialize(String payload) {
                        return payload;
                    }

                    @Override
                    public String dedupKey(String event) {
                        return null;
                    }
                }, FIXED_CLOCK);

        return new DataRetentionJob(
                platformRepositories.newMessageRetentionCleanup(),
                platformRepositories.newAlertRetentionCleanup(),
                queue,
                FIXED_CLOCK,
                PERIOD,
                RETENTION,
                RETENTION,
                RETENTION);
    }

    private static long countRows(DataSource dataSource, String table) {
        var jdbc = new NamedParameterJdbcTemplate(dataSource);
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Map.of(), Long.class);
        return count != null ? count : 0;
    }

    @Nested
    class DeletesOldQueueEvents {

        @Test
        void deletesEventsOlderThanRetentionPeriod(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/retention/insert-test-data.sql");

            assertEquals(3, countRows(dataSource, "persistent_queue_events"));

            createJob(dataSource).execute(JobContext.EMPTY);

            assertEquals(1, countRows(dataSource, "persistent_queue_events"));
        }
    }

    @Nested
    class DeletesOldMessages {

        @Test
        void deletesMessagesOlderThanRetentionPeriod(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/retention/insert-test-data.sql");

            assertEquals(3, countRows(dataSource, "messages"));

            createJob(dataSource).execute(JobContext.EMPTY);

            assertEquals(1, countRows(dataSource, "messages"));
        }
    }

    @Nested
    class DeletesOldAlerts {

        @Test
        void deletesAlertsOlderThanRetentionPeriod(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/retention/insert-test-data.sql");

            assertEquals(3, countRows(dataSource, "alerts"));

            createJob(dataSource).execute(JobContext.EMPTY);

            assertEquals(1, countRows(dataSource, "alerts"));
        }
    }

    @Nested
    class KeepsRecentData {

        @Test
        void keepsAllDataWithinRetentionPeriod(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/retention/insert-recent-data-only.sql");

            assertEquals(2, countRows(dataSource, "persistent_queue_events"));
            assertEquals(2, countRows(dataSource, "messages"));
            assertEquals(2, countRows(dataSource, "alerts"));

            createJob(dataSource).execute(JobContext.EMPTY);

            assertEquals(2, countRows(dataSource, "persistent_queue_events"));
            assertEquals(2, countRows(dataSource, "messages"));
            assertEquals(2, countRows(dataSource, "alerts"));
        }
    }

    @Nested
    class EmptyTables {

        @Test
        void handlesEmptyTablesWithoutErrors(DataSource dataSource) {
            createJob(dataSource).execute(JobContext.EMPTY);

            assertEquals(0, countRows(dataSource, "persistent_queue_events"));
            assertEquals(0, countRows(dataSource, "messages"));
            assertEquals(0, countRows(dataSource, "alerts"));
        }
    }
}
