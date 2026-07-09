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
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.workspace.WorkspaceEventSerializer;
import cafe.jeffrey.hub.core.workspace.consumer.WorkspaceEventConsumer;
import cafe.jeffrey.hub.persistence.jdbc.JdbcProjectInstanceRepository;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceLocation;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import cafe.jeffrey.shared.persistence.client.DatabaseClientProvider;
import cafe.jeffrey.shared.persistentqueue.DuckDBPersistentQueue;
import cafe.jeffrey.test.DuckDBTest;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * End-to-end proof of the per-event transaction: a consumer failure rolls back all of its
 * database writes AND the offset acknowledge, so the event is redelivered and no partial
 * state survives. Runs against a real DuckDB with a real transaction manager — the mocks
 * cover only the workspace fan-out.
 */
@DuckDBTest(migration = "classpath:db/migration/server")
class ProjectsSynchronizerJobTransactionIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String WORKSPACE_ID = "ws-internal-001";
    private static final String PROJECT_ID = "proj-001";
    private static final String CONSUMER_ID = "PROJECT_SYNCHRONIZER_CONSUMER";

    @TempDir
    Path tempDir;

    private WorkspacesManager workspacesManager;

    @BeforeEach
    void setUp() {
        WorkspaceInfo workspaceInfo = new WorkspaceInfo(
                WORKSPACE_ID, "ws-ref-001", null, "Test Workspace",
                WorkspaceLocation.of(tempDir), null, NOW, WorkspaceStatus.UNKNOWN, 0);

        WorkspaceManager workspaceManager = mock(WorkspaceManager.class);
        when(workspaceManager.resolveInfo()).thenReturn(workspaceInfo);
        when(workspaceManager.projectsManager()).thenReturn(mock(ProjectsManager.class));

        workspacesManager = mock(WorkspacesManager.class);
        doReturn(List.of(workspaceManager)).when(workspacesManager).findAll();
    }

    private ProjectsSynchronizerJob createJob(DataSource dataSource, WorkspaceEventConsumer consumer) {
        var provider = new DatabaseClientProvider(dataSource);
        var queue = new DuckDBPersistentQueue<>(
                provider, "workspace-events", new WorkspaceEventSerializer(), FIXED_CLOCK);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

        queue.append(WORKSPACE_ID, new WorkspaceEvent(
                null, "origin-1", null, "ws-ref-001",
                WorkspaceEventType.PROJECT_CREATED, "{}", NOW, NOW, "test"));

        return new ProjectsSynchronizerJob(
                List.of(consumer), queue, transactions, workspacesManager, Duration.ofSeconds(30));
    }

    private static WorkspaceEventConsumer instanceWritingConsumer(DataSource dataSource, Runnable afterWrite) {
        var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, new DatabaseClientProvider(dataSource));
        return new WorkspaceEventConsumer() {
            @Override
            public void on(WorkspaceEvent event, ProjectsManager projectsManager) {
                instanceRepo.insert(new ProjectInstanceInfo(
                        "inst-001", PROJECT_ID, "host-1", ProjectInstanceStatus.PENDING,
                        NOW, null, null, null, 0, null));
                afterWrite.run();
            }

            @Override
            public boolean isApplicable(WorkspaceEvent event) {
                return true;
            }
        };
    }

    @Test
    void consumerFailure_rollsBackWrites_andEventIsRedelivered(DataSource dataSource) {
        var consumer = instanceWritingConsumer(dataSource, () -> {
            throw new RuntimeException("boom after the insert");
        });
        var job = createJob(dataSource, consumer);

        job.execute(JobContext.EMPTY);

        // The insert committed BEFORE the failure point must be rolled back with the event
        var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, new DatabaseClientProvider(dataSource));
        assertTrue(instanceRepo.find("inst-001").isEmpty(),
                "The consumer's write must be rolled back together with the failed event");

        // And the offset must not have advanced — the event stays at the head of the queue
        var queue = new DuckDBPersistentQueue<>(
                new DatabaseClientProvider(dataSource), "workspace-events",
                new WorkspaceEventSerializer(), FIXED_CLOCK);
        assertEquals(1, queue.poll(WORKSPACE_ID, CONSUMER_ID).size(),
                "The failed event must be redelivered on the next poll");
    }

    @Test
    void successfulConsumer_commitsWrites_andAcknowledgesTheEvent(DataSource dataSource) {
        var consumer = instanceWritingConsumer(dataSource, () -> {});
        var job = createJob(dataSource, consumer);

        job.execute(JobContext.EMPTY);

        var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, new DatabaseClientProvider(dataSource));
        assertTrue(instanceRepo.find("inst-001").isPresent(), "The consumer's write must be committed");

        var queue = new DuckDBPersistentQueue<>(
                new DatabaseClientProvider(dataSource), "workspace-events",
                new WorkspaceEventSerializer(), FIXED_CLOCK);
        assertTrue(queue.poll(WORKSPACE_ID, CONSUMER_ID).isEmpty(),
                "The processed event must be acknowledged in the same transaction");
    }
}
