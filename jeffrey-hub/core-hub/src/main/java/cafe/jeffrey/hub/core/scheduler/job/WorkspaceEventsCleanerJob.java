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
import cafe.jeffrey.hub.core.scheduler.Job;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository;
import cafe.jeffrey.shared.common.model.job.JobType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Trims the workspace event log (the append-only audit table behind the Activity feed) by
 * age. Nothing consumes the log, so there is no consumer lag to protect — rows past the
 * retention window are simply removed.
 *
 * <p>The retention parameter keeps its historical name {@code queue-events-retention} so
 * existing configuration overrides continue to apply.</p>
 */
public class WorkspaceEventsCleanerJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceEventsCleanerJob.class);

    private static final String PARAM_QUEUE_EVENTS_RETENTION = "queue-events-retention";

    private final WorkspaceEventLogRepository workspaceEventLog;
    private final Clock clock;
    private final Duration period;
    private final Duration eventsRetention;

    public WorkspaceEventsCleanerJob(
            WorkspaceEventLogRepository workspaceEventLog,
            Clock clock,
            JobConfig config) {

        this.workspaceEventLog = workspaceEventLog;
        this.clock = clock;
        this.period = config.period();
        this.eventsRetention = config.durationParam(PARAM_QUEUE_EVENTS_RETENTION);
    }

    @Override
    public void execute(JobContext context) {
        Instant now = clock.instant();
        int deleted = workspaceEventLog.deleteOlderThan(now.minus(eventsRetention));
        if (deleted > 0) {
            LOG.info("Deleted old workspace events: count={} retention={}", deleted, eventsRetention);
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
