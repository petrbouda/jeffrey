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

package cafe.jeffrey.microscope.core.manager.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.core.client.RemoteClients;
import cafe.jeffrey.microscope.core.client.RemoteDiscoveryClient;
import cafe.jeffrey.microscope.core.client.RemoteProfilerClient;
import cafe.jeffrey.microscope.core.manager.ProfilesManager;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.recordings.RecordingsManager;
import cafe.jeffrey.microscope.core.recording.ProjectRecordingInitializer;
import cafe.jeffrey.microscope.core.client.RemoteWorkspaceEventsClient;
import cafe.jeffrey.microscope.core.resources.response.WorkspaceEventsResponse;
import cafe.jeffrey.microscope.persistence.api.MicroscopeCoreRepositories;
import cafe.jeffrey.microscope.persistence.api.RemoteServerInfo;
import cafe.jeffrey.microscope.persistence.api.WorkspaceRepository;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import cafe.jeffrey.shared.common.model.workspace.WorkspaceStatus;

import java.util.List;
import java.util.Optional;

public class RemoteWorkspaceManager implements WorkspaceManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteWorkspaceManager.class);

    private final MicroscopeJeffreyDirs jeffreyDirs;
    private final RemoteServerInfo serverInfo;
    private final WorkspaceInfo workspaceInfo;
    private final WorkspaceRepository workspaceRepository;
    private final RemoteClients remoteClients;
    private final ProfilesManager.Factory profilesManagerFactory;
    private final ProjectRecordingInitializer.Factory recordingInitializerFactory;
    private final MicroscopeCoreRepositories localCoreRepositories;
    private final RecordingsManager recordingsManager;

    public RemoteWorkspaceManager(
            MicroscopeJeffreyDirs jeffreyDirs,
            RemoteServerInfo serverInfo,
            WorkspaceInfo workspaceInfo,
            WorkspaceRepository workspaceRepository,
            RemoteClients remoteClients,
            ProfilesManager.Factory profilesManagerFactory,
            ProjectRecordingInitializer.Factory recordingInitializerFactory,
            MicroscopeCoreRepositories localCoreRepositories,
            RecordingsManager recordingsManager) {

        this.jeffreyDirs = jeffreyDirs;
        this.serverInfo = serverInfo;
        this.workspaceInfo = workspaceInfo;
        this.workspaceRepository = workspaceRepository;
        this.remoteClients = remoteClients;
        this.profilesManagerFactory = profilesManagerFactory;
        this.recordingInitializerFactory = recordingInitializerFactory;
        this.localCoreRepositories = localCoreRepositories;
        this.recordingsManager = recordingsManager;
    }

    @Override
    public WorkspaceInfo localInfo() {
        return workspaceInfo;
    }

    @Override
    public WorkspaceInfo resolveInfo() {
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
                serverInfo,
                workspaceInfo,
                remoteClients,
                profilesManagerFactory,
                recordingInitializerFactory,
                localCoreRepositories,
                recordingsManager);
    }

    @Override
    public WorkspaceEventsResponse events(int limit) {
        RemoteWorkspaceEventsClient.WorkspaceEventsResult result =
                remoteClients.workspaceEvents().getEvents(workspaceInfo.id(), limit);
        return new WorkspaceEventsResponse(result.events(), result.totalCount(), limit);
    }

    @Override
    public Optional<RemoteProfilerClient> profilerClient() {
        return Optional.of(remoteClients.profiler());
    }

    @Override
    public void upsertProfilerSettings(String agentSettings) {
        remoteClients.profiler().upsertSettingsAtLevel(workspaceInfo.id(), "", agentSettings);
        LOG.debug("Upserted workspace-level profiler settings: workspaceId={}", workspaceInfo.id());
    }

    @Override
    public RemoteProfilerClient.WorkspaceProfilerLevels fetchEffectiveProfilerSettings() {
        return remoteClients.profiler().getWorkspaceEffectiveSettings(workspaceInfo.id());
    }

    @Override
    public void deleteProfilerSettings() {
        remoteClients.profiler().deleteSettingsAtLevel(workspaceInfo.id(), "");
        LOG.debug("Deleted workspace-level profiler settings: workspaceId={}", workspaceInfo.id());
    }

    @Override
    public void delete() {
        // Best-effort: drop the workspace on the remote server via gRPC.
        try {
            remoteClients.discovery().deleteWorkspace(workspaceInfo.id());
        } catch (Exception e) {
            LOG.warn("Remote DeleteWorkspace failed; continuing with local cleanup: workspaceId={}",
                    workspaceInfo.id(), e);
        }

        // Local cleanup: profiles/recordings/profiler_settings tied to this workspace.
        List<String> profileIds = workspaceRepository.delete();
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
