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

package cafe.jeffrey.hub.core.scheduler.job;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.hub.model.job.JobType;

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
        job.execute();

        verify(storageOverviewCache).refresh();
    }

    @Test
    void exposesConfiguredPeriodAndType() {
        assertThat(job.period()).isEqualTo(PERIOD);
        assertThat(job.jobType()).isEqualTo(JobType.STORAGE_OVERVIEW_REFRESHER);
        assertThat(job.executorGroup()).isEqualTo(Job.ExecutorGroup.GLOBAL);
    }
}
