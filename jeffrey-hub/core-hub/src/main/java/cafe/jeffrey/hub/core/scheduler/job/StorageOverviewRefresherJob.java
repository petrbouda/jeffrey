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

import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.manager.storage.StorageOverview;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.hub.core.scheduler.ManuallyTriggerable;
import cafe.jeffrey.shared.common.BytesUtils;
import cafe.jeffrey.hub.model.job.JobType;

import java.time.Duration;

/**
 * Periodically recomputes the hub's storage overview into {@link StorageOverviewCache},
 * so the storage endpoint serves a cached snapshot instead of walking every project
 * repository on each request. The scheduler fires the first tick immediately at startup,
 * which populates the cache before the dashboard is typically opened.
 */
public class StorageOverviewRefresherJob implements Job, ManuallyTriggerable {

    private final StorageOverviewCache storageOverviewCache;
    private final Duration period;

    public StorageOverviewRefresherJob(StorageOverviewCache storageOverviewCache, JobConfig config) {
        this.storageOverviewCache = storageOverviewCache;
        this.period = config.period();
    }

    @Override
    public void execute() {
        storageOverviewCache.refresh();
    }

    /**
     * Recomputes the overview immediately rather than waiting out the period — the figures are a
     * filesystem walk, so they go stale as soon as anything is written or reclaimed.
     */
    @Override
    public String runManually() {
        StorageOverview overview = storageOverviewCache.refresh().overview();

        int projects = overview.projects().size();
        long usedBytes = overview.projects().stream()
                .mapToLong(StorageOverview.ProjectStorage::totalSizeBytes)
                .sum();

        return BytesUtils.format(usedBytes)
                + " across " + projects + (projects == 1 ? " project" : " projects");
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.STORAGE_OVERVIEW_REFRESHER;
    }
}
