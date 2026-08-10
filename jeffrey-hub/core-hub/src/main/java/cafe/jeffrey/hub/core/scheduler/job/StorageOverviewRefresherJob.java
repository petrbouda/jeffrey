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

import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.hub.core.manager.storage.StorageOverviewCache;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Duration;

/**
 * Periodically recomputes the hub's storage overview into {@link StorageOverviewCache},
 * so the storage endpoint serves a cached snapshot instead of walking every project
 * repository on each request. The scheduler fires the first tick immediately at startup,
 * which populates the cache before the dashboard is typically opened.
 */
public class StorageOverviewRefresherJob implements Job {

    private final StorageOverviewCache storageOverviewCache;
    private final Duration period;

    public StorageOverviewRefresherJob(StorageOverviewCache storageOverviewCache, JobConfig config) {
        this.storageOverviewCache = storageOverviewCache;
        this.period = config.period();
    }

    @Override
    public void execute(JobContext context) {
        storageOverviewCache.refresh();
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
