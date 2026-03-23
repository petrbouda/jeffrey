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

package pbouda.jeffrey.local.core.manager.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.local.core.LocalJeffreyDirs;
import pbouda.jeffrey.local.core.client.RemoteClients;
import pbouda.jeffrey.local.core.client.RemoteDiscoveryClient;
import pbouda.jeffrey.local.core.client.RemoteProfilerClient;
import pbouda.jeffrey.local.core.manager.ProfilesManager;
import pbouda.jeffrey.local.core.manager.project.ProjectsManager;
import pbouda.jeffrey.local.persistence.repository.WorkspaceRepository;
import pbouda.jeffrey.local.core.recording.ProjectRecordingInitializer;
import pbouda.jeffrey.local.persistence.model.RemoteWorkspaceInfo;
import pbouda.jeffrey.local.persistence.repository.LocalCoreRepositories;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;

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
            LOG.warn("Failed to resolve remote workspace status, marking as unavailable: workspaceId={}", workspaceInfo.id());
            return workspaceInfo.withStatus(WorkspaceStatus.UNAVAILABLE);
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
    public Optional<RemoteProfilerClient> profilerClient() {
        return Optional.of(remoteClients.profiler());
    }

    @Override
    public void delete() {
        workspaceRepository.delete();
    }

}
