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
import cafe.jeffrey.shared.volume.layout.WorkspaceCreation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * The workspaces and projects at the volume's root. Every visible directory there is a workspace:
 * named by its {@code .workspace-info.json} when someone created it, by its id otherwise — the
 * provisioner writes into a workspace before anyone describes it.
 */
final class VolumeDiscoveryClient implements DiscoveryClient {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeDiscoveryClient.class);

    private static final String ID_TAKEN = "Workspace with reference ID '%s' already exists";
    private static final String NAME_TAKEN = "Workspace with name '%s' already exists";

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

    @Override
    public List<WorkspaceInfo> allWorkspaces() {
        return source.read(() -> source.workspaceDirectories().stream()
                .map(this::workspace)
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
        return source.read(() -> source.workspaceDirectory(workspaceId).map(this::workspace));
    }

    /**
     * Creates a workspace: its directory, adopting one the provisioner already wrote into,
     * described by its marker; refused for an id already described or a name another workspace
     * goes by.
     */
    @Override
    public WorkspaceInfo createWorkspace(String referenceId, String name) {
        WritableVolume volume = source.writable("Cannot create workspace " + referenceId);
        if (!WorkspaceReferenceId.isValid(referenceId) || name == null || name.isBlank()) {
            throw Exceptions.invalidRequest("Invalid workspace reference ID: " + WorkspaceReferenceId.DESCRIPTION);
        }
        String trimmedName = name.trim();
        return source.read(() -> {
            WorkspaceCreation creation =
                    VolumeChanges.createWorkspace(volume, referenceId, trimmedName, source.clock().millis());
            if (creation == WorkspaceCreation.ID_TAKEN) {
                throw Exceptions.invalidRequest(ID_TAKEN.formatted(referenceId));
            }
            if (creation == WorkspaceCreation.NAME_TAKEN) {
                throw Exceptions.invalidRequest(NAME_TAKEN.formatted(trimmedName));
            }
            LOG.info("Created workspace on the recording volume: workspace_id={} source={}",
                    referenceId, source.description());
            return workspace(VolumePath.root().resolve(referenceId));
        });
    }

    @Override
    public void deleteWorkspace(String workspaceId) {
        WritableVolume volume = source.writable("Cannot delete workspace " + workspaceId);
        source.read(() -> {
            VolumePath directory = source.workspaceDirectory(workspaceId)
                    .orElseThrow(() -> Exceptions.workspaceNotFound(workspaceId));
            volume.delete(directory);
            LOG.info("Deleted workspace on the recording volume: workspace_id={} source={}",
                    workspaceId, source.description());
        });
    }

    @Override
    public List<RemoteProjectResponse> allProjects(String workspaceId) {
        return source.read(() -> source.workspaceDirectory(workspaceId)
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

    private RemoteProjectResponse project(ProjectTree project, String workspaceId) {
        return VolumeResponses.project(project, workspaceId, source.sessions(project).instances());
    }

    private WorkspaceInfo workspace(VolumePath directory) {
        VolumeWorkspace workspace = VolumeWorkspace.at(source.volume(), directory);
        return VolumeResponses.workspace(workspace.id(), workspace.name(), workspace.createdAt().orElse(null),
                source.projects(directory).size());
    }
}
