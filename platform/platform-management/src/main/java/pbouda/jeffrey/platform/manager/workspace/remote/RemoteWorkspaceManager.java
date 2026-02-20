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

package pbouda.jeffrey.platform.manager.workspace.remote;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pbouda.jeffrey.shared.common.filesystem.JeffreyDirs;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceInfo;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceStatus;
import pbouda.jeffrey.shared.common.model.workspace.WorkspaceType;
import pbouda.jeffrey.platform.manager.project.ProjectsManager;
import pbouda.jeffrey.platform.manager.workspace.WorkspaceManager;
import pbouda.jeffrey.platform.scheduler.job.descriptor.JobDescriptorFactory;
import pbouda.jeffrey.provider.platform.repository.PlatformRepositories;
import pbouda.jeffrey.provider.platform.repository.WorkspaceRepository;
import pbouda.jeffrey.platform.repository.RemoteWorkspaceRepository;

public class RemoteWorkspaceManager implements WorkspaceManager {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteWorkspaceManager.class);

    private final JeffreyDirs jeffreyDirs;
    private final WorkspaceInfo workspaceInfo;
    private final WorkspaceRepository workspaceRepository;
    private final RemoteClients remoteClients;
    private final ProjectsManager.Factory commonProjectsManagerFactory;
    private final PlatformRepositories platformRepositories;
    private final JobDescriptorFactory jobDescriptorFactory;

    public RemoteWorkspaceManager(
            JeffreyDirs jeffreyDirs,
            WorkspaceInfo workspaceInfo,
            WorkspaceRepository workspaceRepository,
            RemoteClients remoteClients,
            ProjectsManager.Factory commonProjectsManagerFactory,
            PlatformRepositories platformRepositories,
            JobDescriptorFactory jobDescriptorFactory) {

        this.jeffreyDirs = jeffreyDirs;
        this.workspaceInfo = workspaceInfo;
        this.workspaceRepository = workspaceRepository;
        this.remoteClients = remoteClients;
        this.commonProjectsManagerFactory = commonProjectsManagerFactory;
        this.platformRepositories = platformRepositories;
        this.jobDescriptorFactory = jobDescriptorFactory;
    }

    @Override
    public WorkspaceInfo resolveInfo() {
        try {
            RemoteDiscoveryClient.WorkspaceResult result = remoteClients.discovery().workspace(workspaceInfo.originId());
            return switch (result.status()) {
                case AVAILABLE -> result.info().withId(workspaceInfo.id());
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
                commonProjectsManagerFactory.apply(workspaceInfo),
                platformRepositories,
                jobDescriptorFactory);
    }

    @Override
    public WorkspaceType type() {
        return WorkspaceType.REMOTE;
    }

    @Override
    public void delete() {
        workspaceRepository.delete();
    }

    @Override
    public RemoteWorkspaceRepository remoteWorkspaceRepository() {
        throw new UnsupportedOperationException("Remote workspace does not support remote repository.");
    }
}
