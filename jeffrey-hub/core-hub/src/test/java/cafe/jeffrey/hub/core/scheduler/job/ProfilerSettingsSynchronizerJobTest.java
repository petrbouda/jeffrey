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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import cafe.jeffrey.hub.core.manager.workspace.LiveWorkspacesManager;
import cafe.jeffrey.hub.core.manager.workspace.WorkspaceManager;
import cafe.jeffrey.hub.core.repository.RemoteWorkspaceRepository;
import cafe.jeffrey.hub.core.scheduler.JobContext;
import cafe.jeffrey.hub.core.scheduler.job.descriptor.ProfilerSettingsSynchronizerJobDescriptor;
import cafe.jeffrey.hub.persistence.api.HubPlatformRepositories;
import cafe.jeffrey.hub.persistence.api.ProfilerRepository;
import cafe.jeffrey.hub.persistence.api.ProjectRepository;
import cafe.jeffrey.shared.common.model.ProfilerInfo;
import cafe.jeffrey.shared.common.model.ProjectInfo;
import cafe.jeffrey.shared.common.model.repository.ProfilerSettings;
import cafe.jeffrey.shared.common.model.repository.RemoteWorkspaceSettings;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceLocation;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProfilerSettingsSynchronizerJobTest {

    private static final String WORKSPACE_ID = "ws-internal-001";
    private static final String PROJECT_ID = "proj-001";
    private static final String ORIGIN_PROJECT_ID = "origin-proj-001";
    private static final String PROJECT_NAME = "project-alpha";
    private static final Instant NOW = Instant.parse("2026-02-20T12:00:00Z");

    private static final ProfilerInfo GLOBAL_SETTINGS = new ProfilerInfo(null, null, "global-cmd");
    private static final ProfilerInfo WORKSPACE_SETTINGS = new ProfilerInfo(WORKSPACE_ID, null, "workspace-cmd");
    private static final ProfilerInfo PROJECT_SETTINGS = new ProfilerInfo(WORKSPACE_ID, PROJECT_ID, "project-cmd");

    @Mock
    ProfilerRepository profilerRepository;

    @Mock
    LiveWorkspacesManager workspacesManager;

    @Mock
    WorkspaceManager workspaceManager;

    @Mock
    RemoteWorkspaceRepository remoteWorkspaceRepository;

    @Mock
    HubPlatformRepositories platformRepositories;

    @Mock
    ProjectRepository projectRepository;

    @TempDir
    Path workspaceDir;

    private ProfilerSettingsSynchronizerJob job;

    @BeforeEach
    void setUp() {
        WorkspaceInfo workspaceInfo = new WorkspaceInfo(
                WORKSPACE_ID, "ws-ref", "repo-001", "Workspace",
                WorkspaceLocation.of(workspaceDir), null, NOW, WorkspaceStatus.UNKNOWN, 0);

        doReturn(List.of(workspaceManager)).when(workspacesManager).findAll();
        when(workspaceManager.resolveInfo()).thenReturn(workspaceInfo);
        when(workspaceManager.remoteWorkspaceRepository()).thenReturn(remoteWorkspaceRepository);

        job = new ProfilerSettingsSynchronizerJob(
                Duration.ofMinutes(5),
                profilerRepository,
                workspacesManager,
                ProfilerSettingsSynchronizerJobDescriptor.of(Map.of("max-versions", "5")),
                platformRepositories);
    }

    private ProfilerSettings uploadedSettings() {
        ArgumentCaptor<RemoteWorkspaceSettings> captor = ArgumentCaptor.forClass(RemoteWorkspaceSettings.class);
        verify(remoteWorkspaceRepository).uploadSettings(captor.capture());
        return captor.getValue().profiler();
    }

    private static ProjectInfo projectInfo(String originId, String name) {
        return new ProjectInfo(PROJECT_ID, originId, name, "Label", null, WORKSPACE_ID, NOW, null, Map.of(), null);
    }

    @Nested
    class ProjectSettingsKeying {

        @Test
        void projectSettings_publishedUnderBothNameAndOriginId() {
            when(profilerRepository.findWorkspaceSettings(WORKSPACE_ID))
                    .thenReturn(List.of(GLOBAL_SETTINGS, PROJECT_SETTINGS));
            when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepository);
            when(projectRepository.find()).thenReturn(Optional.of(projectInfo(ORIGIN_PROJECT_ID, PROJECT_NAME)));

            job.execute(JobContext.EMPTY);

            ProfilerSettings settings = uploadedSettings();
            assertEquals("project-cmd", settings.projectSettings().get(PROJECT_NAME));
            assertEquals("project-cmd", settings.projectSettingsById().get(ORIGIN_PROJECT_ID));
        }

        @Test
        void projectWithoutOriginId_publishedByNameOnly() {
            when(profilerRepository.findWorkspaceSettings(WORKSPACE_ID))
                    .thenReturn(List.of(PROJECT_SETTINGS));
            when(platformRepositories.newProjectRepository(PROJECT_ID)).thenReturn(projectRepository);
            when(projectRepository.find()).thenReturn(Optional.of(projectInfo(null, PROJECT_NAME)));

            job.execute(JobContext.EMPTY);

            ProfilerSettings settings = uploadedSettings();
            assertEquals("project-cmd", settings.projectSettings().get(PROJECT_NAME));
            assertTrue(settings.projectSettingsById().isEmpty());
        }
    }

    @Nested
    class DefaultSettingsResolution {

        @Test
        void workspaceSettings_beatGlobalSettings() {
            when(profilerRepository.findWorkspaceSettings(WORKSPACE_ID))
                    .thenReturn(List.of(GLOBAL_SETTINGS, WORKSPACE_SETTINGS));

            job.execute(JobContext.EMPTY);

            ProfilerSettings settings = uploadedSettings();
            assertEquals("workspace-cmd", settings.defaultSettings());
            assertEquals("WORKSPACE", settings.defaultSettingsLevel());
        }

        @Test
        void globalSettings_usedWhenNoWorkspaceLevel() {
            when(profilerRepository.findWorkspaceSettings(WORKSPACE_ID))
                    .thenReturn(List.of(GLOBAL_SETTINGS));

            job.execute(JobContext.EMPTY);

            ProfilerSettings settings = uploadedSettings();
            assertEquals("global-cmd", settings.defaultSettings());
            assertEquals("GLOBAL", settings.defaultSettingsLevel());
        }
    }
}
