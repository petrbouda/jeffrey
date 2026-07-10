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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties.JobConfig;
import cafe.jeffrey.shared.folderqueue.FolderQueue;
import cafe.jeffrey.shared.persistentqueue.PersistentQueue;
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Trims the workspace-events pipeline storage: entries in the persistent DB queue older
 * than the retention window, and CLI event files already replicated into the DB queue
 * (moved to the folder queue's {@code .processed/} directory) — without this second
 * step every event file ever processed would accumulate on disk forever.
 */
public class WorkspaceEventsCleanerJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventsCleanerJob.class);

    private static final String PARAM_QUEUE_EVENTS_RETENTION = "queue-events-retention";
    private static final String PARAM_PROCESSED_FILES_RETENTION = "processed-files-retention";

    private final PersistentQueue<?> persistentQueue;
    private final FolderQueue folderQueue;
    private final Clock clock;
    private final Duration period;
    private final Duration queueEventsRetention;
    private final Duration processedFilesRetention;

    public WorkspaceEventsCleanerJob(
            PersistentQueue<?> persistentQueue,
            FolderQueue folderQueue,
            Clock clock,
            JobConfig config) {

        this.persistentQueue = persistentQueue;
        this.folderQueue = folderQueue;
        this.clock = clock;
        this.period = config.period();
        this.queueEventsRetention = config.durationParam(PARAM_QUEUE_EVENTS_RETENTION);
        this.processedFilesRetention = config.durationParam(PARAM_PROCESSED_FILES_RETENTION);
    }

    @Override
    public void execute(JobContext context) {
        Instant now = clock.instant();
        deleteOldQueueEvents(now, context);
        folderQueue.cleanup(processedFilesRetention);
    }

    private void deleteOldQueueEvents(Instant now, JobContext context) {
        int deleted = persistentQueue.deleteEventsOlderThan(now.minus(queueEventsRetention));
        if (deleted > 0) {
            LOG.info("Deleted old queue events: count={} retention={}", deleted, queueEventsRetention);
            context.report().summary("Deleted " + deleted + " old queue events");
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
