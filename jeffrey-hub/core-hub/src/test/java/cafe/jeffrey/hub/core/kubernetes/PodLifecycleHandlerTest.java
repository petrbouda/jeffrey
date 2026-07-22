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

package cafe.jeffrey.hub.core.kubernetes;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.hub.core.HubJeffreyDirs;
import cafe.jeffrey.hub.core.manager.project.ProjectManager;
import cafe.jeffrey.hub.core.manager.project.ProjectsManager;
import cafe.jeffrey.hub.core.manager.workspace.LiveWorkspacesManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspacesManager;
import cafe.jeffrey.hub.core.streaming.SessionFinisher;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProjectRepositoryRepository;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.ProjectInstanceSessionInfo;
import cafe.jeffrey.shared.common.model.RepositoryInfo;
import cafe.jeffrey.shared.common.model.RepositoryType;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

@ExtendWith(MockitoExtension.class)
class PodLifecycleHandlerTest {

    private static final Instant NOW = Instant.parse("2026-03-01T12:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static final String POD_NAME = "my-service-7d9f6c-abcde";
    private static final String WORKSPACE_REF_ID = "production";
    private static final String PROJECT_ID = "proj-001";
    private static final String SESSION_ID = "session-001";

    private static final ProjectInfo PROJECT_INFO = new ProjectInfo(
            PROJECT_ID, "origin-proj-001", "my-service", "My Service", null,
            "ws-001", NOW.minusSeconds(3600), null, Map.of(), null);

    private static final RepositoryInfo REPOSITORY_INFO = new RepositoryInfo(
            "repo-001", RepositoryType.ASYNC_PROFILER, null, "production", "my-service");

    private static final ProjectInstanceSessionInfo UNFINISHED_SESSION = new ProjectInstanceSessionInfo(
            SESSION_ID, "repo-001", POD_NAME, 1,
            Path.of(POD_NAME, SESSION_ID), NOW.minusSeconds(600), NOW.minusSeconds(600), null);

    @Mock
    LiveWorkspacesManager workspacesManager;

    @Mock
    WorkspaceManager workspaceManager;

    @Mock
    ProjectsManager projectsManager;

    @Mock
    ProjectManager projectManager;

    @Mock
    HubPlatformRepositories platformRepositories;

    @Mock
    ProjectRepositoryRepository repositoryRepository;

    @Mock
    SessionFinisher sessionFinisher;

    private final HubJeffreyDirs jeffreyDirs = new HubJeffreyDirs(Path.of("/jeffrey-home"));

    private static Pod pod(Map<String, String> labels, String phase) {
        PodBuilder builder = new PodBuilder()
                .withNewMetadata()
                .withName(POD_NAME)
                .withLabels(labels)
                .endMetadata();
        if (phase != null) {
            builder = builder.withNewStatus().withPhase(phase).endStatus();
        }
        return builder.build();
    }

    private PodLifecycleHandler handler(boolean autoCreateWorkspaces) {
        return new PodLifecycleHandler(
                workspacesManager, platformRepositories, sessionFinisher,
                jeffreyDirs, FIXED_CLOCK, autoCreateWorkspaces);
    }

    @Nested
    class WorkspaceAutoCreate {

        @Test
        void podWithWorkspaceLabel_missingWorkspace_created() {
            when(workspacesManager.findByReferenceId(WORKSPACE_REF_ID)).thenReturn(Optional.empty());
            when(workspacesManager.create(any())).thenReturn(new WorkspaceInfo(
                    "ws-001", WORKSPACE_REF_ID, "repo-001", WORKSPACE_REF_ID,
                    null, null, NOW, WorkspaceStatus.UNKNOWN, 0));

            handler(true).onAdd(pod(Map.of(
                    KubernetesConventions.LABEL_ENABLED, "true",
                    KubernetesConventions.LABEL_WORKSPACE, WORKSPACE_REF_ID), "Running"));

            ArgumentCaptor<WorkspacesManager.CreateWorkspaceRequest> captor =
                    ArgumentCaptor.forClass(WorkspacesManager.CreateWorkspaceRequest.class);
            verify(workspacesManager).create(captor.capture());
            assertEquals(WORKSPACE_REF_ID, captor.getValue().referenceId());
            assertEquals(WORKSPACE_REF_ID, captor.getValue().name());
        }

        @Test
        void podWithWorkspaceLabel_existingWorkspace_notCreatedAgain() {
            when(workspacesManager.findByReferenceId(WORKSPACE_REF_ID)).thenReturn(Optional.of(workspaceManager));

            handler(true).onAdd(pod(Map.of(
                    KubernetesConventions.LABEL_ENABLED, "true",
                    KubernetesConventions.LABEL_WORKSPACE, WORKSPACE_REF_ID), "Running"));

            verify(workspacesManager, never()).create(any());
        }

        @Test
        void podWithoutWorkspaceLabel_nothingCreated() {
            handler(true).onAdd(pod(Map.of(KubernetesConventions.LABEL_ENABLED, "true"), "Running"));

            verify(workspacesManager, never()).create(any());
        }

        @Test
        void autoCreateDisabled_nothingCreated() {
            handler(false).onAdd(pod(Map.of(
                    KubernetesConventions.LABEL_ENABLED, "true",
                    KubernetesConventions.LABEL_WORKSPACE, WORKSPACE_REF_ID), "Running"));

            verify(workspacesManager, never()).create(any());
        }
    }

    @Nested
    class SessionFinishOnTermination {

        private void wireProjectWalk() {
            doReturn(List.of(workspaceManager)).when(workspacesManager).findAll();
            when(workspaceManager.projectsManager()).thenReturn(projectsManager);
            when(projectsManager.findAll()).thenReturn(List.of(projectManager));
            when(projectManager.info()).thenReturn(PROJECT_INFO);
            when(platformRepositories.newProjectRepositoryRepository(PROJECT_ID)).thenReturn(repositoryRepository);
        }

        @Test
        void podDeleted_unfinishedSessionsOfInstance_forceFinished() {
            wireProjectWalk();
            when(repositoryRepository.findUnfinishedSessionsByInstanceId(POD_NAME))
                    .thenReturn(List.of(UNFINISHED_SESSION));
            when(repositoryRepository.getAll()).thenReturn(List.of(REPOSITORY_INFO));

            handler(true).onDelete(pod(Map.of(KubernetesConventions.LABEL_ENABLED, "true"), "Running"), false);

            Path expectedSessionPath = jeffreyDirs.workspaces()
                    .resolve("production").resolve("my-service").resolve(POD_NAME).resolve(SESSION_ID);
            verify(sessionFinisher).forceFinish(
                    eq(repositoryRepository), eq(PROJECT_INFO), eq(UNFINISHED_SESSION),
                    eq(expectedSessionPath), eq(NOW));
        }

        @Test
        void podDeleted_noUnfinishedSessions_nothingFinished() {
            wireProjectWalk();
            when(repositoryRepository.findUnfinishedSessionsByInstanceId(POD_NAME)).thenReturn(List.of());

            handler(true).onDelete(pod(Map.of(KubernetesConventions.LABEL_ENABLED, "true"), "Running"), false);

            verify(sessionFinisher, never()).forceFinish(any(), any(), any(), any(), any());
        }

        @Test
        void podTransitionsToFailedPhase_sessionsFinished() {
            wireProjectWalk();
            when(repositoryRepository.findUnfinishedSessionsByInstanceId(POD_NAME))
                    .thenReturn(List.of(UNFINISHED_SESSION));
            when(repositoryRepository.getAll()).thenReturn(List.of(REPOSITORY_INFO));

            Pod running = pod(Map.of(KubernetesConventions.LABEL_ENABLED, "true"), "Running");
            Pod failed = pod(Map.of(KubernetesConventions.LABEL_ENABLED, "true"), "Failed");

            handler(true).onUpdate(running, failed);

            verify(sessionFinisher).forceFinish(any(), any(), eq(UNFINISHED_SESSION), any(), eq(NOW));
        }

        @Test
        void podStaysRunning_onUpdate_nothingFinished() {
            Pod running = pod(Map.of(KubernetesConventions.LABEL_ENABLED, "true"), "Running");
            Pod stillRunning = pod(Map.of(KubernetesConventions.LABEL_ENABLED, "true"), "Running");

            handler(true).onUpdate(running, stillRunning);

            verify(sessionFinisher, never()).forceFinish(any(), any(), any(), any(), any());
        }

        @Test
        void podAlreadyTerminal_onUpdate_notFinishedTwice() {
            Pod failed = pod(Map.of(KubernetesConventions.LABEL_ENABLED, "true"), "Failed");
            Pod stillFailed = pod(Map.of(KubernetesConventions.LABEL_ENABLED, "true"), "Failed");

            handler(true).onUpdate(failed, stillFailed);

            verify(sessionFinisher, never()).forceFinish(any(), any(), any(), any(), any());
        }
    }
}
