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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StorageOverviewRefresherJobTest {

    private static final Duration PERIOD = Duration.ofMinutes(5);

    @Mock
    StorageOverviewCache storageOverviewCache;

    StorageOverviewRefresherJob job;

    @BeforeEach
    void setUp() {
        job = new StorageOverviewRefresherJob(
                storageOverviewCache,
                new JobConfig(true, PERIOD, Map.of()));
    }

    @Test
    void executeRefreshesTheCache() {
        job.execute(JobContext.EMPTY);

        verify(storageOverviewCache).refresh();
    }

    @Test
    void exposesConfiguredPeriodAndType() {
        assertThat(job.period()).isEqualTo(PERIOD);
        assertThat(job.jobType()).isEqualTo(JobType.STORAGE_OVERVIEW_REFRESHER);
        assertThat(job.executorGroup()).isEqualTo(Job.ExecutorGroup.GLOBAL);
    }
}
