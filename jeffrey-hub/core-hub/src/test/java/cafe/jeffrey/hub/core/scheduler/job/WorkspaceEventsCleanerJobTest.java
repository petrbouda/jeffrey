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

package cafe.jeffrey.hub.core.scheduler.job;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import cafe.jeffrey.shared.folderqueue.FolderQueue;
import cafe.jeffrey.shared.persistentqueue.DuckDBPersistentQueue;
import cafe.jeffrey.shared.persistentqueue.EventSerializer;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;
import cafe.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DuckDBTest(migration = "classpath:db/migration/server")
class WorkspaceEventsCleanerJobTest {

    // Fixed time: 2025-06-30. Old data is from 2025-05-01/05-05 (>31 days), recent from 2025-06-25 (<31 days)
    private static final Instant FIXED_TIME = Instant.parse("2025-06-30T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
    private static final Duration RETENTION = Duration.ofDays(31);
    private static final Duration PERIOD = Duration.ofDays(1);

    @TempDir
    Path queueDir;

    private WorkspaceEventsCleanerJob createJob(DataSource dataSource) {
        var provider = new DatabaseClientProvider(dataSource);

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

        return new WorkspaceEventsCleanerJob(
                queue,
                new FolderQueue(queueDir, FIXED_CLOCK),
                FIXED_CLOCK,
                PERIOD,
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
    class KeepsRecentData {

        @Test
        void keepsAllDataWithinRetentionPeriod(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/retention/insert-recent-data-only.sql");

            assertEquals(2, countRows(dataSource, "persistent_queue_events"));

            createJob(dataSource).execute(JobContext.EMPTY);

            assertEquals(2, countRows(dataSource, "persistent_queue_events"));
        }
    }

    @Nested
    class EmptyTables {

        @Test
        void handlesEmptyTablesWithoutErrors(DataSource dataSource) {
            createJob(dataSource).execute(JobContext.EMPTY);

            assertEquals(0, countRows(dataSource, "persistent_queue_events"));
        }
    }

    @Nested
    class CleansProcessedFolderQueueFiles {

        @Test
        void deletesProcessedFilesOlderThanRetention(DataSource dataSource) throws IOException {
            Path processedDir = Files.createDirectories(queueDir.resolve(".processed"));
            // Timestamps encoded in the filenames: 2025-05-01 is beyond the 31-day retention
            // (relative to the fixed 2025-06-30 clock), 2025-06-25 is within it
            Path oldFile = Files.createFile(processedDir.resolve("20250501100000000_aaaaaaaa.json"));
            Path recentFile = Files.createFile(processedDir.resolve("20250625100000000_bbbbbbbb.json"));

            createJob(dataSource).execute(JobContext.EMPTY);

            assertFalse(Files.exists(oldFile), "Replicated event file beyond retention should be deleted");
            assertTrue(Files.exists(recentFile), "Recent processed file must be kept");
        }
    }
}
