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

package cafe.jeffrey.hub.core.manager.workspace;

import cafe.jeffrey.hub.core.volume.VolumeMarkers;
import cafe.jeffrey.hub.core.volume.WorkspaceDirectory;
import cafe.jeffrey.hub.core.volume.WorkspaceMarker;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceReferenceId;
import cafe.jeffrey.hub.model.workspace.WorkspaceStatus;
import cafe.jeffrey.shared.common.JeffreyLayout;
import cafe.jeffrey.shared.common.filesystem.FileSystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * The workspaces under the hub's workspaces root. A workspace is a directory named by its id with
 * the hub's {@code .workspace-info.json} in it. A directory the provisioner wrote into without one
 * is a workspace too when {@code autoCreate} is on, named by its id; otherwise it is left out
 * until someone creates the workspace.
 */
public final class HubWorkspacesManager implements WorkspacesManager {

    private static final Logger LOG = LoggerFactory.getLogger(HubWorkspacesManager.class);

    private final Path workspacesRoot;
    private final Clock clock;
    private final boolean autoCreate;
    private final WorkspaceManager.Factory workspaceManagerFactory;

    public HubWorkspacesManager(
            Path workspacesRoot,
            Clock clock,
            boolean autoCreate,
            WorkspaceManager.Factory workspaceManagerFactory) {

        this.workspacesRoot = workspacesRoot;
        this.clock = clock;
        this.autoCreate = autoCreate;
        this.workspaceManagerFactory = workspaceManagerFactory;
    }

    @Override
    public WorkspaceInfo create(CreateWorkspaceRequest request) {
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Workspace Name cannot be null or empty");
        }
        String id = request.referenceId().trim();
        String name = request.name().trim();

        Path directory = workspacesRoot.resolve(id);
        if (VolumeMarkers.read(directory, WorkspaceMarker.FILE_NAME, WorkspaceMarker.class).isPresent()) {
            throw new WorkspaceAlreadyExistsException("Workspace with reference ID '" + id + "' already exists");
        }
        boolean nameTaken = findAll().stream()
                .anyMatch(workspace -> workspace.info().name().equals(name));
        if (nameTaken) {
            throw new WorkspaceAlreadyExistsException("Workspace with name '" + name + "' already exists");
        }

        FileSystemUtils.createDirectories(directory);
        VolumeMarkers.write(directory, WorkspaceMarker.FILE_NAME, new WorkspaceMarker(name, clock.millis()));
        LOG.info("Created workspace: workspace_id={} name={}", id, name);
        return workspace(directory).orElseThrow().info();
    }

    @Override
    public List<WorkspaceManager> findAll() {
        return VolumeMarkers.childDirectories(workspacesRoot).stream()
                .map(this::workspace)
                .flatMap(Optional::stream)
                .map(workspaceManagerFactory)
                .toList();
    }

    @Override
    public Optional<WorkspaceManager> findById(String workspaceId) {
        if (!WorkspaceReferenceId.isValid(workspaceId)) {
            return Optional.empty();
        }
        Path directory = workspacesRoot.resolve(workspaceId);
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        return workspace(directory).map(workspaceManagerFactory);
    }

    private Optional<WorkspaceDirectory> workspace(Path directory) {
        String id = directory.getFileName().toString();
        if (!WorkspaceReferenceId.isValid(id)) {
            return Optional.empty();
        }
        Optional<WorkspaceMarker> marker = VolumeMarkers.read(directory, WorkspaceMarker.FILE_NAME, WorkspaceMarker.class);
        if (marker.isEmpty() && !autoCreate) {
            return Optional.empty();
        }
        String name = marker.map(WorkspaceMarker::name).orElse(id);
        Instant createdAt = marker.map(found -> Instant.ofEpochMilli(found.createdAt()))
                .orElseGet(() -> FileSystemUtils.createdAt(directory));
        WorkspaceInfo info = new WorkspaceInfo(id, name, createdAt, WorkspaceStatus.AVAILABLE, projectCount(directory));
        return Optional.of(new WorkspaceDirectory(directory, info));
    }

    private static int projectCount(Path directory) {
        return (int) VolumeMarkers.childDirectories(directory).stream()
                .filter(project -> Files.isRegularFile(project.resolve(JeffreyLayout.PROJECT_INFO_FILE)))
                .count();
    }
}
