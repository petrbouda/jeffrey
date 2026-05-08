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

package cafe.jeffrey.server.core.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.shared.persistentqueue.PersistentQueue;
import cafe.jeffrey.server.core.scheduler.Job;
import cafe.jeffrey.server.core.scheduler.JobContext;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Trims the persistent workspace-events queue, deleting entries older than the
 * configured retention window so storage stays bounded.
 */
public class WorkspaceEventsCleanerJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventsCleanerJob.class);

    private final PersistentQueue<?> persistentQueue;
    private final Clock clock;
    private final Duration period;
    private final Duration queueEventsRetention;

    public WorkspaceEventsCleanerJob(
            PersistentQueue<?> persistentQueue,
            Clock clock,
            Duration period,
            Duration queueEventsRetention) {

        this.persistentQueue = persistentQueue;
        this.clock = clock;
        this.period = period;
        this.queueEventsRetention = queueEventsRetention;
    }

    @Override
    public void execute(JobContext context) {
        Instant now = clock.instant();
        deleteOldQueueEvents(now);
    }

    private void deleteOldQueueEvents(Instant now) {
        int deleted = persistentQueue.deleteEventsOlderThan(now.minus(queueEventsRetention));
        if (deleted > 0) {
            LOG.info("Deleted old queue events: count={} retention={}", deleted, queueEventsRetention);
        }
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.WORKSPACE_EVENTS_CLEANER;
    }
}
