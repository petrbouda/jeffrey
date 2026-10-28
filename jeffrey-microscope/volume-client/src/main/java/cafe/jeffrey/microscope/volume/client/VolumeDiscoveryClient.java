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

package cafe.jeffrey.microscope.volume.client;

import cafe.jeffrey.microscope.remote.client.DiscoveryClient;
import cafe.jeffrey.microscope.remote.client.ProjectScope;
import cafe.jeffrey.microscope.remote.client.dto.RemoteProjectResponse;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceReferenceId;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;
import cafe.jeffrey.shared.common.exception.Exceptions;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.WritableVolume;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.VolumeChanges;
import cafe.jeffrey.shared.volume.layout.VolumeWorkspace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * The workspaces and projects at the volume's root. Every visible directory there is a workspace,
 * named by its id — the reference id the provisioner writes into; nothing in it is read.
 */
final class VolumeDiscoveryClient implements DiscoveryClient {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeDiscoveryClient.class);

    private static final String ID_TAKEN = "Workspace with reference ID '%s' already exists";

    private final VolumeSource source;

    VolumeDiscoveryClient(VolumeSource source) {
        this.source = source;
    }

    /** Reachable when its root can be listed; a listing that fails says why. */
    @Override
    public PublicApiInfo info() {
        source.read(() -> source.volume().list(VolumePath.root()));
        return new PublicApiInfo(source.description());
    }

    /** One listing of the volume's root: each workspace by its id. */
    @Override
    public List<WorkspaceInfo> allWorkspaces() {
        return source.read(() -> VolumeWorkspace.all(source.volume()).stream()
                .map(VolumeDiscoveryClient::workspace)
                .toList());
    }

    @Override
    public WorkspaceResult workspace(String workspaceId) {
        try {
            return workspaceOrThrow(workspaceId)
                    .map(WorkspaceResult::of)
                    .orElseGet(() -> WorkspaceResult.of(WorkspaceStatus.UNAVAILABLE));
        } catch (JeffreyException e) {
            LOG.warn("Cannot read a workspace off the recording volume: workspace_id={} source={} reason={}",
                    workspaceId, source.description(), e.getMessage());
            return WorkspaceResult.of(WorkspaceStatus.OFFLINE);
        }
    }

    @Override
    public Optional<WorkspaceInfo> workspaceOrThrow(String workspaceId) {
        return source.read(() -> VolumeWorkspace.at(source.volume(), workspaceId).map(VolumeDiscoveryClient::workspace));
    }

    /** Creates a workspace: its directory; refused for an id whose directory is there already. */
    @Override
    public WorkspaceInfo createWorkspace(String referenceId) {
        WritableVolume volume = source.writable("Cannot create workspace " + referenceId);
        if (!WorkspaceReferenceId.isValid(referenceId)) {
            throw Exceptions.invalidRequest("Invalid workspace reference ID: " + WorkspaceReferenceId.DESCRIPTION);
        }
        return source.read(() -> {
            if (!VolumeChanges.createWorkspace(volume, referenceId)) {
                throw Exceptions.invalidRequest(ID_TAKEN.formatted(referenceId));
            }
            LOG.info("Created workspace on the recording volume: workspace_id={} source={}",
                    referenceId, source.description());
            return VolumeWorkspace.at(volume, referenceId)
                    .map(VolumeDiscoveryClient::workspace)
                    .orElseThrow(() -> Exceptions.workspaceNotFound(referenceId));
        });
    }

    @Override
    public void deleteWorkspace(String workspaceId) {
        WritableVolume volume = source.writable("Cannot delete workspace " + workspaceId);
        source.read(() -> {
            VolumeWorkspace workspace = VolumeWorkspace.at(volume, workspaceId)
                    .orElseThrow(() -> Exceptions.workspaceNotFound(workspaceId));
            volume.delete(workspace.path());
            LOG.info("Deleted workspace on the recording volume: workspace_id={} source={}",
                    workspaceId, source.description());
        });
    }

    /** One listing of the workspace: each project by its directory, nothing below it read. */
    @Override
    public List<RemoteProjectResponse> allProjects(String workspaceId) {
        return source.read(() -> VolumePath.root().visibleChild(workspaceId)
                .map(workspace -> source.projects(workspace).stream()
                        .map(project -> project(project, workspaceId))
                        .toList())
                .orElse(List.of()));
    }

    @Override
    public Optional<RemoteProjectResponse> project(String workspaceId, String projectId) {
        return source.read(() -> source.project(new ProjectScope(workspaceId, projectId))
                .map(project -> project(project, workspaceId)));
    }

    /** Off its directory alone: nothing below it is read. */
    private RemoteProjectResponse project(ProjectTree project, String workspaceId) {
        return VolumeResponses.project(project, workspaceId);
    }

    private static WorkspaceInfo workspace(VolumeWorkspace workspace) {
        return VolumeResponses.workspace(workspace.id(), workspace.createdAt().orElse(null));
    }
}
