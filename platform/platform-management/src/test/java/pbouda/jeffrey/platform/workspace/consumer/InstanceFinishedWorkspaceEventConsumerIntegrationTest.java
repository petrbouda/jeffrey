/*
 * Jeffrey
 * Copyright (C) 2025 Petr Bouda
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

package pbouda.jeffrey.platform.workspace.consumer;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.provider.platform.JdbcPlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import pbouda.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DuckDBTest(migration = "classpath:db/migration/platform")
@ExtendWith(MockitoExtension.class)
class InstanceFinishedWorkspaceEventConsumerIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final String INSTANCE_ID = "inst-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, ORIGIN_PROJECT_ID, "Test Project", "Label 1", null,
            WORKSPACE_ID, WorkspaceType.LIVE, Instant.parse("2025-01-01T11:00:00Z"), null, Map.of());

    private static final ProjectsSynchronizerJobDescriptor JOB_DESCRIPTOR =
            new ProjectsSynchronizerJobDescriptor("test-template");

    private static WorkspaceEvent instanceFinishedEvent(String instanceId, Instant originCreatedAt) {
        return new WorkspaceEvent(null, instanceId, ORIGIN_PROJECT_ID, WORKSPACE_ID,
                WorkspaceEventType.PROJECT_INSTANCE_FINISHED, Json.EMPTY,
                originCreatedAt, NOW, "test");
    }

    @Nested
    class ClosesUnfinishedSessions {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Test
        void closesUnfinishedSessions_instanceDerivesFinished(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-instance-and-sessions.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);

            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);

            Instant finishedAt = Instant.parse("2025-06-15T11:30:00Z");

            var consumer = new InstanceFinishedWorkspaceEventConsumer(projectsManager, platformRepositories);
            consumer.on(instanceFinishedEvent(INSTANCE_ID, finishedAt), JOB_DESCRIPTOR);

            // Verify sessions are closed
            var repoRepo = platformRepositories.newProjectRepositoryRepository(PROJECT_ID);
            Optional<ProjectInstanceSessionInfo> session1 = repoRepo.findSessionById("session-001");
            assertTrue(session1.isPresent());
            assertEquals(finishedAt, session1.get().finishedAt());

            Optional<ProjectInstanceSessionInfo> session2 = repoRepo.findSessionById("session-002");
            assertTrue(session2.isPresent());
            assertEquals(finishedAt, session2.get().finishedAt());

            // Verify instance status is derived as FINISHED (all sessions closed)
            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            Optional<ProjectInstanceInfo> instance = instanceRepo.find(INSTANCE_ID);
            assertTrue(instance.isPresent());
            assertAll(
                    () -> assertEquals(ProjectInstanceStatus.FINISHED, instance.get().status()),
                    () -> assertNotNull(instance.get().finishedAt())
            );
        }
    }

    @Nested
    class StatusTransition {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Test
        void activeToFinished_viaSessionClosure(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-instance-and-sessions.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var platformRepositories = new JdbcPlatformRepositories(provider, FIXED_CLOCK);

            // Verify instance is ACTIVE before (has unfinished sessions)
            var instanceRepo = platformRepositories.newProjectInstanceRepository(PROJECT_ID);
            Optional<ProjectInstanceInfo> before = instanceRepo.find(INSTANCE_ID);
            assertTrue(before.isPresent());
            assertEquals(ProjectInstanceStatus.ACTIVE, before.get().status());
            assertNull(before.get().finishedAt());

            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);

            Instant finishedAt = Instant.parse("2025-06-15T11:30:00Z");
            var consumer = new InstanceFinishedWorkspaceEventConsumer(projectsManager, platformRepositories);
            consumer.on(instanceFinishedEvent(INSTANCE_ID, finishedAt), JOB_DESCRIPTOR);

            // Verify instance derives to FINISHED after sessions are closed
            Optional<ProjectInstanceInfo> after = instanceRepo.find(INSTANCE_ID);
            assertTrue(after.isPresent());
            assertEquals(ProjectInstanceStatus.FINISHED, after.get().status());
            assertNotNull(after.get().finishedAt());
        }
    }

    @Nested
    class ProjectNotFound {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        PlatformRepositories platformRepositories;

        @Test
        void projectNotFound_noOp(DataSource dataSource) {
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.empty());

            var consumer = new InstanceFinishedWorkspaceEventConsumer(projectsManager, platformRepositories);

            assertDoesNotThrow(() ->
                    consumer.on(instanceFinishedEvent(INSTANCE_ID, NOW), JOB_DESCRIPTOR));
        }
    }
}
