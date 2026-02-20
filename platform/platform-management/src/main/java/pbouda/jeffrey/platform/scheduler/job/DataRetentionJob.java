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

package pbouda.jeffrey.platform.scheduler.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.platform.queue.PersistentQueue;
import pbouda.jeffrey.platform.scheduler.Job;
import pbouda.jeffrey.platform.scheduler.JobContext;
import pbouda.jeffrey.provider.platform.repository.AlertRepository;
import pbouda.jeffrey.provider.platform.repository.MessageRepository;
import pbouda.jeffrey.shared.common.model.job.JobType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class DataRetentionJob implements Job {

    private static final Logger LOG = LoggerFactory.getLogger(DataRetentionJob.class);

    private final MessageRepository messageRepository;
    private final AlertRepository alertRepository;
    private final PersistentQueue<?> persistentQueue;
    private final Clock clock;
    private final Duration period;
    private final Duration queueEventsRetention;
    private final Duration messagesRetention;
    private final Duration alertsRetention;

    public DataRetentionJob(
            MessageRepository messageRepository,
            AlertRepository alertRepository,
            PersistentQueue<?> persistentQueue,
            Clock clock,
            Duration period,
            Duration queueEventsRetention,
            Duration messagesRetention,
            Duration alertsRetention) {

        this.messageRepository = messageRepository;
        this.alertRepository = alertRepository;
        this.persistentQueue = persistentQueue;
        this.clock = clock;
        this.period = period;
        this.queueEventsRetention = queueEventsRetention;
        this.messagesRetention = messagesRetention;
        this.alertsRetention = alertsRetention;
    }

    @Override
    public void execute(JobContext context) {
        Instant now = clock.instant();
        deleteOldQueueEvents(now);
        deleteOldMessages(now);
        deleteOldAlerts(now);
    }

    private void deleteOldQueueEvents(Instant now) {
        int deleted = persistentQueue.deleteEventsOlderThan(now.minus(queueEventsRetention));
        if (deleted > 0) {
            LOG.info("Deleted old queue events: count={} retention={}", deleted, queueEventsRetention);
        }
    }

    private void deleteOldMessages(Instant now) {
        int deleted = messageRepository.deleteOlderThan(now.minus(messagesRetention));
        if (deleted > 0) {
            LOG.info("Deleted old messages: count={} retention={}", deleted, messagesRetention);
        }
    }

    private void deleteOldAlerts(Instant now) {
        int deleted = alertRepository.deleteOlderThan(now.minus(alertsRetention));
        if (deleted > 0) {
            LOG.info("Deleted old alerts: count={} retention={}", deleted, alertsRetention);
        }
    }

    @Override
    public Duration period() {
        return period;
    }

    @Override
    public JobType jobType() {
        return JobType.DATA_RETENTION;
    }
}
