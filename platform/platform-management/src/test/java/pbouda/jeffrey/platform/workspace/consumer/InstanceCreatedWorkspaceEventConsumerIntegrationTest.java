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
import pbouda.jeffrey.shared.common.model.workspace.event.InstanceCreatedEventContent;
import pbouda.jeffrey.provider.platform.repository.JdbcProjectInstanceRepository;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo;
import pbouda.jeffrey.shared.common.model.ProjectInstanceInfo.ProjectInstanceStatus;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.shared.persistence.client.DatabaseClientProvider;
import pbouda.jeffrey.test.DuckDBTest;
import pbouda.jeffrey.test.TestUtils;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@DuckDBTest(migration = "classpath:db/migration/platform")
@ExtendWith(MockitoExtension.class)
class InstanceCreatedWorkspaceEventConsumerIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, ORIGIN_PROJECT_ID, "Test Project", "Label 1", null,
            WORKSPACE_ID, WorkspaceType.LIVE, Instant.parse("2025-01-01T11:00:00Z"), null, Map.of());

    private static final ProjectsSynchronizerJobDescriptor JOB_DESCRIPTOR =
            new ProjectsSynchronizerJobDescriptor("test-template");

    private static WorkspaceEvent instanceCreatedEvent(String instanceId) {
        InstanceCreatedEventContent content = new InstanceCreatedEventContent("inst-dir-001");
        return new WorkspaceEvent(null, instanceId, ORIGIN_PROJECT_ID, WORKSPACE_ID,
                WorkspaceEventType.PROJECT_INSTANCE_CREATED, Json.toString(content),
                Instant.parse("2025-06-15T10:00:00Z"), NOW, "test");
    }

    @Nested
    class HappyPath {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Test
        void instanceCreated_withCorrectFields(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-and-project.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.projectInstanceRepository()).thenReturn(instanceRepo);

            var consumer = new InstanceCreatedWorkspaceEventConsumer(projectsManager);
            consumer.on(instanceCreatedEvent("inst-new-001"), JOB_DESCRIPTOR);

            Optional<ProjectInstanceInfo> instance = instanceRepo.find("inst-new-001");
            assertTrue(instance.isPresent());
            assertAll(
                    () -> assertEquals("inst-new-001", instance.get().id()),
                    () -> assertEquals(PROJECT_ID, instance.get().projectId()),
                    () -> assertEquals("inst-new-001", instance.get().hostname()),
                    () -> assertEquals(ProjectInstanceStatus.ACTIVE, instance.get().status()),
                    () -> assertEquals(Instant.parse("2025-06-15T10:00:00Z"), instance.get().startedAt()),
                    () -> assertNull(instance.get().finishedAt())
            );
        }
    }

    @Nested
    class Idempotency {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Test
        void instanceAlreadyExists_skipsCreation(DataSource dataSource) throws SQLException {
            TestUtils.executeSql(dataSource, "sql/consumer/insert-workspace-project-and-instance.sql");
            var provider = new DatabaseClientProvider(dataSource);
            var instanceRepo = new JdbcProjectInstanceRepository(PROJECT_ID, provider);

            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.projectInstanceRepository()).thenReturn(instanceRepo);

            // inst-001 already exists from SQL fixture
            var consumer = new InstanceCreatedWorkspaceEventConsumer(projectsManager);
            consumer.on(instanceCreatedEvent("inst-001"), JOB_DESCRIPTOR);

            // Should still have exactly one instance (not duplicated)
            var allInstances = instanceRepo.findAll();
            assertEquals(1, allInstances.size());
            assertEquals("inst-001", allInstances.getFirst().id());
        }
    }

    @Nested
    class ProjectNotFound {

        @Mock
        ProjectsManager projectsManager;

        @Test
        void projectNotFound_noOp(DataSource dataSource) {
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.empty());

            var consumer = new InstanceCreatedWorkspaceEventConsumer(projectsManager);

            // Should not throw
            assertDoesNotThrow(() ->
                    consumer.on(instanceCreatedEvent("inst-001"), JOB_DESCRIPTOR));
        }
    }
}
