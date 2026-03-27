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

package pbouda.jeffrey.server.core.scheduler.job;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.server.core.manager.SchedulerManager;
import pbouda.jeffrey.server.core.manager.project.ProjectManager;
import pbouda.jeffrey.server.core.scheduler.JobContext;
import pbouda.jeffrey.server.core.scheduler.job.descriptor.ExpiredInstanceCleanerJobDescriptor;
import pbouda.jeffrey.server.persistence.JdbcServerPlatformRepositories;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DuckDBTest(migration = "classpath:db/migration/server")
@ExtendWith(MockitoExtension.class)
class ExpiredInstanceCleanerJobIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String WORKSPACE_ID = "ws-001";

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, null, "Test Project", "Label 1", null,
            WORKSPACE_ID,
            Instant.parse("2025-01-01T11:00:00Z"), null, Map.of(), false, null);

    // 7-day retention
    private static final ExpiredInstanceCleanerJobDescriptor JOB_DESCRIPTOR =
            new ExpiredInstanceCleanerJobDescriptor(7, ChronoUnit.DAYS);

    @Nested
    class DeletesOldExpiredInstances {

        @Mock
        ProjectManager projectManager;

        @Test
        void deletesExpiredInstance_pastRetentionPeriod(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/expired-cleaner/insert-project-with-expired-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);

            // Clock set to 2025-06-20: inst-expired-old (expired_at=May 9) is 42 days past → delete
            // inst-expired-recent (expired_at=Jun 14) is 6 days past → keep
            Instant now = Instant.parse("2025-06-20T12:00:00Z");
            Clock clock = Clock.fixed(now, ZoneOffset.UTC);
            var platformRepositories = new JdbcServerPlatformRepositories(provider, clock);

            when(projectManager.info()).thenReturn(PROJECT_INFO);

            var job = new ExpiredInstanceCleanerJob(null, null, Duration.ofHours(1), clock, platformRepositories);
            job.execute(projectManager, JOB_DESCRIPTOR, JobContext.EMPTY);

            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            // inst-expired-old should be deleted
            assertTrue(instanceRepo.find("inst-expired-old").isEmpty());
            // inst-expired-recent should still exist (only 6 days past, retention is 7)
            assertTrue(instanceRepo.find("inst-expired-recent").isPresent());
            // Non-expired instances untouched
            assertTrue(instanceRepo.find("inst-active").isPresent());
            assertTrue(instanceRepo.find("inst-finished").isPresent());
        }

        @Test
        void deletesAllExpired_whenAllPastRetention(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/expired-cleaner/insert-project-with-expired-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);

            // Clock set far in the future: both expired instances are past retention
            Instant now = Instant.parse("2025-07-15T12:00:00Z");
            Clock clock = Clock.fixed(now, ZoneOffset.UTC);
            var platformRepositories = new JdbcServerPlatformRepositories(provider, clock);

            when(projectManager.info()).thenReturn(PROJECT_INFO);

            var job = new ExpiredInstanceCleanerJob(null, null, Duration.ofHours(1), clock, platformRepositories);
            job.execute(projectManager, JOB_DESCRIPTOR, JobContext.EMPTY);

            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            assertTrue(instanceRepo.find("inst-expired-old").isEmpty());
            assertTrue(instanceRepo.find("inst-expired-recent").isEmpty());
            // Non-expired instances still exist
            assertEquals(2, instanceRepo.findAll().size());
        }
    }

    @Nested
    class KeepsRecentExpiredInstances {

        @Mock
        ProjectManager projectManager;

        @Test
        void keepsAll_whenNoneExpiredPastRetention(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/expired-cleaner/insert-project-with-expired-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);

            // Clock set to Jun 15: inst-expired-recent (expired_at=Jun 14) is 1 day past → keep
            // inst-expired-old (expired_at=May 9) is 37 days → delete
            // To keep ALL: set clock before May 16 (May 9 + 7 days)
            Instant now = Instant.parse("2025-05-15T12:00:00Z");
            Clock clock = Clock.fixed(now, ZoneOffset.UTC);
            var platformRepositories = new JdbcServerPlatformRepositories(provider, clock);

            when(projectManager.info()).thenReturn(PROJECT_INFO);

            var job = new ExpiredInstanceCleanerJob(null, null, Duration.ofHours(1), clock, platformRepositories);
            job.execute(projectManager, JOB_DESCRIPTOR, JobContext.EMPTY);

            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            // All 4 instances should still exist
            assertEquals(4, instanceRepo.findAll().size());
        }
    }

    @Nested
    class NoExpiredInstances {

        @Mock
        ProjectManager projectManager;

        @Test
        void noOp_whenNoExpiredInstances(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/expired-cleaner/insert-project-with-expired-instances.sql");
            var provider = new DatabaseClientProvider(dataSource);

            Instant now = Instant.parse("2025-06-20T12:00:00Z");
            Clock clock = Clock.fixed(now, ZoneOffset.UTC);
            var platformRepositories = new JdbcServerPlatformRepositories(provider, clock);

            when(projectManager.info()).thenReturn(PROJECT_INFO);

            // Delete both expired instances first
            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            instanceRepo.delete("inst-expired-old");
            instanceRepo.delete("inst-expired-recent");
            assertEquals(2, instanceRepo.findAll().size());

            var job = new ExpiredInstanceCleanerJob(null, null, Duration.ofHours(1), clock, platformRepositories);
            job.execute(projectManager, JOB_DESCRIPTOR, JobContext.EMPTY);

            // Still 2 non-expired instances
            assertEquals(2, instanceRepo.findAll().size());
        }
    }
}
