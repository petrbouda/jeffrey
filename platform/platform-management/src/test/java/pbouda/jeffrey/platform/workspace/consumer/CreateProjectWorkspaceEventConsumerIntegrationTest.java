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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pbouda.jeffrey.platform.manager.RepositoryManager;
import pbouda.jeffrey.platform.manager.project.ProjectManager;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.scheduler.job.descriptor.ProjectsSynchronizerJobDescriptor;
import pbouda.jeffrey.shared.common.model.workspace.event.ProjectCreatedEventContent;
import pbouda.jeffrey.profile.manager.model.CreateProject;
import pbouda.jeffrey.shared.common.Json;
import pbouda.jeffrey.shared.common.model.ProjectInfo;
import pbouda.jeffrey.shared.common.model.RepositoryInfo;
import pbouda.jeffrey.shared.common.model.RepositoryType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEvent;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceEventType;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CreateProjectWorkspaceEventConsumerIntegrationTest {

    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";
    private static final String WORKSPACE_ID = "ws-001";
    private static final Instant NOW = Instant.parse("2025-06-15T12:00:00Z");

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, ORIGIN_PROJECT_ID, "Test Project", "Label 1", null,
            WORKSPACE_ID, WorkspaceType.LIVE, Instant.parse("2025-01-01T11:00:00Z"), null, Map.of());

    private static final RepositoryInfo REPO_INFO = new RepositoryInfo(
            "repo-001", RepositoryType.ASYNC_PROFILER, "/workspaces", "ws-001", "proj-001");

    private static final ProjectsSynchronizerJobDescriptor JOB_DESCRIPTOR =
            new ProjectsSynchronizerJobDescriptor("test-template");

    private static WorkspaceEvent projectCreatedEvent() {
        ProjectCreatedEventContent content = new ProjectCreatedEventContent(
                "project-alpha", "Alpha Label", "/workspaces", "ws-001", "proj-001",
                RepositoryType.ASYNC_PROFILER, Map.of("env", "prod"));
        return new WorkspaceEvent(null, ORIGIN_PROJECT_ID, ORIGIN_PROJECT_ID, WORKSPACE_ID,
                WorkspaceEventType.PROJECT_CREATED, Json.toString(content),
                Instant.parse("2025-06-15T10:00:00Z"), NOW, "test");
    }

    @Nested
    class HappyPath {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryManager repositoryManager;

        @Test
        void projectCreated_withCorrectFields() {
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.empty());
            when(projectsManager.create(any())).thenReturn(projectManager);
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.repositoryManager()).thenReturn(repositoryManager);
            when(repositoryManager.info()).thenReturn(Optional.empty());

            var consumer = new CreateProjectWorkspaceEventConsumer(projectsManager);
            consumer.on(projectCreatedEvent(), JOB_DESCRIPTOR);

            ArgumentCaptor<CreateProject> captor = ArgumentCaptor.forClass(CreateProject.class);
            verify(projectsManager).create(captor.capture());
            CreateProject createProject = captor.getValue();

            assertAll(
                    () -> assertEquals(ORIGIN_PROJECT_ID, createProject.originProjectId()),
                    () -> assertEquals("project-alpha", createProject.projectName()),
                    () -> assertEquals("Alpha Label", createProject.projectLabel()),
                    () -> assertNull(createProject.namespace()),
                    () -> assertEquals("test-template", createProject.templateId()),
                    () -> assertEquals(Instant.parse("2025-06-15T10:00:00Z"), createProject.originCreatedAt()),
                    () -> assertEquals(Map.of("env", "prod"), createProject.attributes())
            );

            // Repository should also be created
            ArgumentCaptor<RepositoryInfo> repoCaptor = ArgumentCaptor.forClass(RepositoryInfo.class);
            verify(repositoryManager).create(repoCaptor.capture());
            RepositoryInfo repoInfo = repoCaptor.getValue();
            assertAll(
                    () -> assertEquals(RepositoryType.ASYNC_PROFILER, repoInfo.repositoryType()),
                    () -> assertEquals("/workspaces", repoInfo.workspacesPath()),
                    () -> assertEquals("ws-001", repoInfo.relativeWorkspacePath()),
                    () -> assertEquals("proj-001", repoInfo.relativeProjectPath())
            );
        }
    }

    @Nested
    class Idempotency {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryManager repositoryManager;

        @Test
        void projectAlreadyExists_skipsCreation_stillCreatesRepositoryIfMissing() {
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.repositoryManager()).thenReturn(repositoryManager);
            when(repositoryManager.info()).thenReturn(Optional.empty());

            var consumer = new CreateProjectWorkspaceEventConsumer(projectsManager);
            consumer.on(projectCreatedEvent(), JOB_DESCRIPTOR);

            // Project creation should NOT be called
            verify(projectsManager, never()).create(any());
            // Repository should still be created
            verify(repositoryManager).create(any());
        }

        @Test
        void projectAndRepositoryAlreadyExist_noCreation() {
            when(projectsManager.findByOriginProjectId(ORIGIN_PROJECT_ID)).thenReturn(Optional.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.repositoryManager()).thenReturn(repositoryManager);
            when(repositoryManager.info()).thenReturn(Optional.of(REPO_INFO));

            var consumer = new CreateProjectWorkspaceEventConsumer(projectsManager);
            consumer.on(projectCreatedEvent(), JOB_DESCRIPTOR);

            verify(projectsManager, never()).create(any());
            verify(repositoryManager, never()).create(any());
        }
    }

    @Nested
    class ContentDeserialization {

        @Mock
        ProjectsManager projectsManager;

        @Mock
        ProjectManager projectManager;

        @Mock
        RepositoryManager repositoryManager;

        @Test
        void allFieldsDeserialized_fromEventContent() {
            ProjectCreatedEventContent content = new ProjectCreatedEventContent(
                    "my-project", "My Label", "/data/workspaces", "workspace-dir", "project-dir",
                    RepositoryType.ASYNC_PROFILER, Map.of("key1", "value1", "key2", "value2"));

            WorkspaceEvent event = new WorkspaceEvent(null, "new-proj-001", "new-proj-001", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_CREATED, Json.toString(content),
                    Instant.parse("2025-06-15T08:00:00Z"), NOW, "test");

            when(projectsManager.findByOriginProjectId("new-proj-001")).thenReturn(Optional.empty());
            when(projectsManager.create(any())).thenReturn(projectManager);
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(projectManager.repositoryManager()).thenReturn(repositoryManager);
            when(repositoryManager.info()).thenReturn(Optional.empty());

            var consumer = new CreateProjectWorkspaceEventConsumer(projectsManager);
            consumer.on(event, JOB_DESCRIPTOR);

            ArgumentCaptor<CreateProject> captor = ArgumentCaptor.forClass(CreateProject.class);
            verify(projectsManager).create(captor.capture());

            assertEquals("my-project", captor.getValue().projectName());
            assertEquals("My Label", captor.getValue().projectLabel());
            assertEquals(Map.of("key1", "value1", "key2", "value2"), captor.getValue().attributes());

            ArgumentCaptor<RepositoryInfo> repoCaptor = ArgumentCaptor.forClass(RepositoryInfo.class);
            verify(repositoryManager).create(repoCaptor.capture());
            assertEquals("/data/workspaces", repoCaptor.getValue().workspacesPath());
            assertEquals("workspace-dir", repoCaptor.getValue().relativeWorkspacePath());
            assertEquals("project-dir", repoCaptor.getValue().relativeProjectPath());
        }
    }

    @Nested
    class IsApplicable {

        @Mock
        ProjectsManager projectsManager;

        @Test
        void onlyApplicable_forProjectCreatedEvents() {
            var consumer = new CreateProjectWorkspaceEventConsumer(projectsManager);

            WorkspaceEvent projectCreated = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_CREATED, null, NOW, NOW, "test");
            WorkspaceEvent instanceCreated = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_INSTANCE_CREATED, null, NOW, NOW, "test");
            WorkspaceEvent sessionCreated = new WorkspaceEvent(null, "id", "proj", WORKSPACE_ID,
                    WorkspaceEventType.PROJECT_INSTANCE_SESSION_CREATED, null, NOW, NOW, "test");

            assertTrue(consumer.isApplicable(projectCreated));
            assertFalse(consumer.isApplicable(instanceCreated));
            assertFalse(consumer.isApplicable(sessionCreated));
        }
    }
}
