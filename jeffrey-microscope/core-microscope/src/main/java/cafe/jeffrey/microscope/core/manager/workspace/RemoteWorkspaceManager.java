/*
 * Jeffrey
 * Copyright (C) 2026 Petr Bouda
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cafe.jeffrey.microscope.core.manager.workspace;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import cafe.jeffrey.microscope.core.MicroscopeJeffreyDirs;
import cafe.jeffrey.microscope.remote.client.RemoteClients;
import cafe.jeffrey.microscope.remote.client.DiscoveryClient;
import cafe.jeffrey.microscope.core.manager.project.ProjectsManager;
import cafe.jeffrey.microscope.core.manager.project.RemoteProjectManagerFactory;
import cafe.jeffrey.microscope.model.remote.RemoteInfo;
import cafe.jeffrey.microscope.persistence.api.WorkspaceRepository;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;

import java.util.List;
import cafe.jeffrey.shared.notification.NotificationType;
import cafe.jeffrey.shared.notification.Notifications;

public class RemoteWorkspaceManager implements WorkspaceManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteWorkspaceManager.class);

    private final MicroscopeJeffreyDirs jeffreyDirs;
    private final RemoteInfo remote;
    private final WorkspaceInfo workspaceInfo;
    private final WorkspaceRepository workspaceRepository;
    private final RemoteClients clients;
    private final RemoteProjectManagerFactory projectManagerFactory;

    public RemoteWorkspaceManager(
            MicroscopeJeffreyDirs jeffreyDirs,
            RemoteInfo remote,
            WorkspaceInfo workspaceInfo,
            WorkspaceRepository workspaceRepository,
            RemoteClients clients,
            RemoteProjectManagerFactory projectManagerFactory) {

        this.jeffreyDirs = jeffreyDirs;
        this.remote = remote;
        this.workspaceInfo = workspaceInfo;
        this.workspaceRepository = workspaceRepository;
        this.clients = clients;
        this.projectManagerFactory = projectManagerFactory;
    }

    @Override
    public WorkspaceInfo localInfo() {
        return workspaceInfo;
    }

    @Override
    public WorkspaceInfo resolveInfo() {
        try {
            DiscoveryClient.WorkspaceResult result = clients.discovery().workspace(workspaceInfo.id());
            return switch (result.status()) {
                case AVAILABLE -> result.info();
                case UNAVAILABLE -> workspaceInfo.withStatus(WorkspaceStatus.UNAVAILABLE);
                case OFFLINE -> workspaceInfo.withStatus(WorkspaceStatus.OFFLINE);
                case UNKNOWN -> throw new IllegalStateException("Unknown remote workspace status");
            };
        } catch (Exception e) {
            LOG.warn("Cannot reach the remote, marking as offline: workspaceId={}", workspaceInfo.id());
            return workspaceInfo.withStatus(WorkspaceStatus.OFFLINE);
        }
    }

    @Override
    public ProjectsManager projectsManager() {
        return new RemoteProjectsManager(remote, workspaceInfo, clients, projectManagerFactory);
    }

    @Override
    public void delete() {
        // Refused before anything local goes: the remote delete below is best-effort, so a
        // refusal there would still wipe this workspace's profiles and answer success
        if (remote.address().readOnly()) {
            throw Exceptions.invalidRequest("Cannot delete workspace " + workspaceInfo.id()
                    + ": the remote is declared read-only");
        }

        // Best-effort: drop the workspace on the remote.
        try {
            clients.discovery().deleteWorkspace(workspaceInfo.id());
        } catch (Exception e) {
            LOG.warn("Remote DeleteWorkspace failed; continuing with local cleanup: workspaceId={}",
                    workspaceInfo.id(), e);

            // The caller gets 204 either way, so from the outside this delete looks complete while
            // the remote still holds the workspace. The next listing shows it again.
            Notifications.of(NotificationType.WORKSPACE_DELETE_PARTIAL)
                    .attribute("workspaceId", workspaceInfo.id())
                    .errorType(e)
                    .emit();
        }

        // Local cleanup: profiles and recordings tied to this workspace.
        List<String> profileIds = workspaceRepository.delete();
        for (String profileId : profileIds) {
            try {
                FileSystemUtils.removeDirectory(jeffreyDirs.profileDir(profileId));
            } catch (Exception e) {
                LOG.warn("Failed to delete profile directory: profileId={}", profileId, e);

                // The row is already gone, so nothing points at this directory any more: it will sit
                // on disk taking space until somebody goes looking for it by hand.
                Notifications.of(NotificationType.PROFILE_DIR_ORPHANED)
                        .attribute("profileId", profileId)
                        .attribute("workspaceId", workspaceInfo.id())
                        .errorType(e)
                        .emit();
            }
        }

        LOG.info("Deleted workspace: workspaceId={} deletedProfiles={}", workspaceInfo.id(), profileIds.size());
    }
}
