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

package pbouda.jeffrey.provider.platform.repository;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import pbouda.jeffrey.shared.common.model.job.JobInfo;
import pbouda.jeffrey.shared.common.model.job.JobType;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DuckDBTest(migration = "classpath:db/migration/platform")
class JdbcProjectSchedulerRepositoryTest {

    @Nested
    class AllMethod {

        @Test
        void returnsEmptyList_whenNoJobs(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectSchedulerRepository repository = new JdbcProjectSchedulerRepository("proj-001", provider);

            List<JobInfo> result = repository.all();

            assertTrue(result.isEmpty());
        }

        @Test
        void returnsProjectJobs(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/scheduler/insert-project-jobs.sql");
            JdbcProjectSchedulerRepository repository = new JdbcProjectSchedulerRepository("proj-001", provider);

            List<JobInfo> result = repository.all();

            assertEquals(2, result.size());
        }

        @Test
        void filtersJobsByProject(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/scheduler/insert-project-jobs.sql");
            JdbcProjectSchedulerRepository repository = new JdbcProjectSchedulerRepository("proj-other", provider);

            List<JobInfo> result = repository.all();

            assertTrue(result.isEmpty()); // No jobs for proj-other
        }
    }

    @Nested
    class InsertMethod {

        @Test
        void insertsJob(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/projects/insert-workspace-with-projects.sql");
            JdbcProjectSchedulerRepository repository = new JdbcProjectSchedulerRepository("proj-001", provider);

            JobInfo jobInfo = new JobInfo("new-job-001", "proj-001", JobType.REPOSITORY_SESSION_CLEANER, Map.of("keepLast", "5"), true);
            repository.insert(jobInfo);

            List<JobInfo> result = repository.all();
            assertEquals(1, result.size());
            assertEquals("new-job-001", result.get(0).id());
        }
    }

    @Nested
    class UpdateEnabledMethod {

        @Test
        void updatesEnabledFlag(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/scheduler/insert-project-jobs.sql");
            JdbcProjectSchedulerRepository repository = new JdbcProjectSchedulerRepository("proj-001", provider);

            repository.updateEnabled("job-002", true);

            List<JobInfo> result = repository.all();
            JobInfo job = result.stream()
                    .filter(j -> "job-002".equals(j.id()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(job.enabled());
        }
    }

    @Nested
    class DeleteMethod {

        @Test
        void deletesJob(DataSource dataSource) throws SQLException {
            var provider = new DatabaseClientProvider(dataSource);
            TestUtils.executeSql(dataSource, "sql/scheduler/insert-project-jobs.sql");
            JdbcProjectSchedulerRepository repository = new JdbcProjectSchedulerRepository("proj-001", provider);

            repository.delete("job-001");

            List<JobInfo> result = repository.all();
            assertEquals(1, result.size());
            assertEquals("job-002", result.get(0).id());
        }
    }
}
