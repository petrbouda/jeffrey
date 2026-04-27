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

package cafe.jeffrey.local.core.manager.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.local.core.LocalJeffreyDirs;
import cafe.jeffrey.local.core.client.RemoteClients;
import cafe.jeffrey.local.core.client.RemoteDiscoveryClient;
import cafe.jeffrey.local.core.client.RemoteProfilerClient;
import cafe.jeffrey.local.core.manager.ProfilesManager;
import cafe.jeffrey.local.core.manager.project.ProjectsManager;
import cafe.jeffrey.local.core.resources.response.WorkspaceEventResponse;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.local.persistence.api.WorkspaceRepository;
import cafe.jeffrey.local.core.recording.ProjectRecordingInitializer;
import cafe.jeffrey.local.persistence.api.RemoteWorkspaceInfo;
import cafe.jeffrey.local.persistence.api.LocalCoreRepositories;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.util.List;
import java.util.Optional;

public class RemoteWorkspaceManager implements WorkspaceManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteWorkspaceManager.class);

    private final LocalJeffreyDirs jeffreyDirs;
    private final RemoteWorkspaceInfo workspaceInfo;
    private final WorkspaceRepository workspaceRepository;
    private final RemoteClients remoteClients;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final ProjectRecordingInitializer.Factory recordingInitializerFactory;
    private final LocalCoreRepositories localCoreRepositories;

    public RemoteWorkspaceManager(
            LocalJeffreyDirs jeffreyDirs,
            RemoteWorkspaceInfo workspaceInfo,
            WorkspaceRepository workspaceRepository,
            RemoteClients remoteClients,
            ProfilesManager.Factory profilesManagerFactory,
            ProjectRecordingInitializer.Factory recordingInitializerFactory,
            LocalCoreRepositories localCoreRepositories) {

        this.jeffreyDirs = jeffreyDirs;
        this.workspaceInfo = workspaceInfo;
        this.workspaceRepository = workspaceRepository;
        this.remoteClients = remoteClients;
        this.profilesManagerFactory = profilesManagerFactory;
        this.recordingInitializerFactory = recordingInitializerFactory;
        this.localCoreRepositories = localCoreRepositories;
    }

    @Override
    public RemoteWorkspaceInfo localInfo() {
        return workspaceInfo;
    }

    @Override
    public RemoteWorkspaceInfo resolveInfo() {
        try {
            RemoteDiscoveryClient.WorkspaceResult result = remoteClients.discovery().workspace(workspaceInfo.id());
            return switch (result.status()) {
                case AVAILABLE -> result.info();
                case UNAVAILABLE -> workspaceInfo.withStatus(WorkspaceStatus.UNAVAILABLE);
                case OFFLINE -> workspaceInfo.withStatus(WorkspaceStatus.OFFLINE);
                case UNKNOWN -> throw new IllegalStateException("Unknown remote workspace status");
            };
        } catch (Exception e) {
            LOG.warn("Cannot reach remote server, marking as offline: workspaceId={}", workspaceInfo.id());
            return workspaceInfo.withStatus(WorkspaceStatus.OFFLINE);
        }
    }

    @Override
    public ProjectsManager projectsManager() {
        return new RemoteProjectsManager(
                jeffreyDirs,
                workspaceInfo,
                remoteClients,
                profilesManagerFactory,
                recordingInitializerFactory,
                localCoreRepositories);
    }

    @Override
    public List<WorkspaceEventResponse> events() {
        return remoteClients.workspaceEvents().getEvents(workspaceInfo.id());
    }

    @Override
    public Optional<RemoteProfilerClient> profilerClient() {
        return Optional.of(remoteClients.profiler());
    }

    @Override
    public void delete() {
        List<String> profileIds = workspaceRepository.delete();

        // Clean up per-profile database directories from filesystem
        for (String profileId : profileIds) {
            try {
                FileSystemUtils.removeDirectory(jeffreyDirs.profileDir(profileId));
            } catch (Exception e) {
                LOG.warn("Failed to delete profile directory: profileId={}", profileId, e);
            }
        }

        LOG.info("Deleted workspace: workspaceId={} deletedProfiles={}", workspaceInfo.id(), profileIds.size());
    }

}
