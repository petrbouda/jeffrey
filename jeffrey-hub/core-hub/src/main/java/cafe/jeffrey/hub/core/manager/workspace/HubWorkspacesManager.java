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

import cafe.jeffrey.hub.core.volume.WorkspaceDirectory;
import cafe.jeffrey.hub.model.workspace.WorkspaceInfo;
import cafe.jeffrey.hub.model.workspace.WorkspaceReferenceId;
import cafe.jeffrey.shared.volume.FilesystemVolume;
import cafe.jeffrey.shared.volume.VolumePath;
import cafe.jeffrey.shared.volume.layout.VolumeChanges;
import cafe.jeffrey.shared.volume.layout.VolumeMarkers;
import cafe.jeffrey.shared.volume.layout.VolumeWorkspace;
import cafe.jeffrey.shared.volume.layout.WorkspaceCreation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UncheckedIOException;
import java.nio.file.NoSuchFileException;
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

    private final FilesystemVolume volume;
    private final Clock clock;
    private final boolean autoCreate;
    private final WorkspaceManager.Factory workspaceManagerFactory;

    public HubWorkspacesManager(
            FilesystemVolume volume,
            Clock clock,
            boolean autoCreate,
            WorkspaceManager.Factory workspaceManagerFactory) {

        this.volume = volume;
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

        WorkspaceCreation creation = VolumeChanges.createWorkspace(volume, id, name, clock.millis());
        if (creation == WorkspaceCreation.ID_TAKEN) {
            throw new WorkspaceAlreadyExistsException("Workspace with reference ID '" + id + "' already exists");
        }
        if (creation == WorkspaceCreation.NAME_TAKEN) {
            throw new WorkspaceAlreadyExistsException("Workspace with name '" + name + "' already exists");
        }
        LOG.info("Created workspace: workspace_id={} name={}", id, name);
        return workspace(VolumePath.root().resolve(id)).orElseThrow().info();
    }

    @Override
    public List<WorkspaceManager> findAll() {
        return workspaceDirectories().stream()
                .map(workspaceManagerFactory)
                .toList();
    }

    private List<WorkspaceDirectory> workspaceDirectories() {
        return VolumeMarkers.childDirectories(volume, VolumePath.root()).stream()
                .map(this::workspace)
                .flatMap(Optional::stream)
                .toList();
    }

    @Override
    public boolean isDescribed(String workspaceId) {
        return WorkspaceReferenceId.isValid(workspaceId)
                && VolumeChanges.isDescribedWorkspace(volume, VolumePath.root().resolve(workspaceId));
    }

    private Optional<WorkspaceDirectory> workspace(VolumePath directory) {
        if (!WorkspaceReferenceId.isValid(directory.name())) {
            return Optional.empty();
        }
        VolumeWorkspace workspace = VolumeWorkspace.at(volume, directory);
        if (!workspace.described() && !autoCreate) {
            return Optional.empty();
        }
        Instant createdAt = workspace.createdAt().orElseThrow(() -> new UncheckedIOException(
                "Workspace directory vanished while it was read: path=" + directory,
                new NoSuchFileException(directory.toString())));
        return Optional.of(new WorkspaceDirectory(volume, directory, workspace.name(), createdAt));
    }
}
