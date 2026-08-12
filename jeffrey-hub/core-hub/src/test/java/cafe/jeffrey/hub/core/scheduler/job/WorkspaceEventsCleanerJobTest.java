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

import org.junit.jupiter.api.Test;
import cafe.jeffrey.hub.core.configuration.properties.SchedulerJobsProperties;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository;
import cafe.jeffrey.hub.persistence.api.WorkspaceEventLogRepository.WorkspaceEventQuery;
import cafe.jeffrey.hub.persistence.jdbc.JdbcWorkspaceEventLogRepository;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.test.DuckDBTest;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DuckDBTest(migration = "classpath:db/migration/server")
class WorkspaceEventsCleanerJobTest {

    private static final String WORKSPACE_ID = "ws-001";
    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration RETENTION = Duration.ofDays(31);

    private static WorkspaceEventLogRepository eventLog(DataSource dataSource) {
        return new JdbcWorkspaceEventLogRepository(new DatabaseClientProvider(dataSource), FIXED_CLOCK);
    }

    private static WorkspaceEventsCleanerJob job(WorkspaceEventLogRepository eventLog) {
        var config = new SchedulerJobsProperties.JobConfig(
                true, Duration.ofMinutes(5), Map.of("queue-events-retention", "31d"));
        return new WorkspaceEventsCleanerJob(eventLog, FIXED_CLOCK, config);
    }

    private static WorkspaceEvent eventAt(String originEventId, Instant createdAt) {
        return new WorkspaceEvent(null, originEventId, "proj-1", "ref-1",
                WorkspaceEventType.PROJECT_CREATED, "{}", createdAt, createdAt, "TEST");
    }

    @Test
    void deletesOnlyEventsPastRetention(DataSource dataSource) {
        WorkspaceEventLogRepository eventLog = eventLog(dataSource);
        eventLog.append(WORKSPACE_ID, eventAt("old", NOW.minus(RETENTION).minus(Duration.ofDays(1))), null);
        eventLog.append(WORKSPACE_ID, eventAt("recent", NOW.minus(Duration.ofDays(1))), null);

        job(eventLog).execute(JobContext.EMPTY);

        var remaining = eventLog.findLatest(new WorkspaceEventQuery(WORKSPACE_ID, null, Set.of(), 10));
        assertEquals(1, remaining.size());
        assertEquals("recent", remaining.getFirst().originEventId());
    }

    @Test
    void emptyLogIsANoOp(DataSource dataSource) {
        WorkspaceEventLogRepository eventLog = eventLog(dataSource);

        job(eventLog).execute(JobContext.EMPTY);

        assertEquals(0, eventLog.count(WORKSPACE_ID));
    }
}
