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

import cafe.jeffrey.hub.client.DiscoveryClient;
import cafe.jeffrey.hub.client.ProjectScope;
import cafe.jeffrey.hub.client.dto.RemoteProjectResponse;
import cafe.jeffrey.microscope.model.workspace.WorkspaceInfo;
import cafe.jeffrey.microscope.model.workspace.WorkspaceStatus;
import cafe.jeffrey.shared.common.exception.JeffreyException;
import cafe.jeffrey.shared.volume.VolumeEntry;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.layout.ProjectTree;
import cafe.jeffrey.shared.volume.layout.VolumeMarkers;
import cafe.jeffrey.shared.volume.layout.WorkspaceMarker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The workspaces and projects at the volume's root. Every visible directory there is a workspace:
 * named by the hub's {@code .workspace-info.json} when a hub created it, by its id otherwise — a
 * volume read without a hub may never have had one to describe it.
 */
final class VolumeDiscoveryClient implements DiscoveryClient {

    private static final Logger LOG = LoggerFactory.getLogger(VolumeDiscoveryClient.class);

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

    @Override
    public WorkspaceInfo createWorkspace(String referenceId, String name) {
        throw VolumeSource.readOnly("Cannot create workspace " + referenceId);
    }

    @Override
    public void deleteWorkspace(String workspaceId) {
        throw VolumeSource.readOnly("Cannot delete workspace " + workspaceId);
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
        Optional<WorkspaceMarker> marker =
                VolumeMarkers.read(source.volume(), directory, WorkspaceMarker.FILE_NAME, WorkspaceMarker.class);
        String id = directory.name();
        String name = marker.map(WorkspaceMarker::name).orElse(id);
        Instant createdAt = marker.map(found -> Instant.ofEpochMilli(found.createdAt()))
                .or(() -> source.volume().stat(directory).flatMap(VolumeEntry::createdAt))
                .orElse(null);
        return VolumeResponses.workspace(id, name, createdAt, source.projects(directory).size());
    }
}
